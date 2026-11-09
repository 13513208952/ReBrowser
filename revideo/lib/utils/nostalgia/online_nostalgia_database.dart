// ignore_for_file: cascade_invocations, use_null_aware_elements

import 'dart:isolate';
import 'dart:math' show pow;

import 'package:PiliPlus/models/online_nostalgia/online_nostalgia_video.dart';
import 'package:PiliPlus/utils/id_utils.dart';
import 'package:PiliPlus/utils/path_utils.dart';
import 'package:path/path.dart' as p;
import 'package:sqlite3/sqlite3.dart';
import 'package:synchronized/synchronized.dart';

typedef ImportSummary = ({
  int total,
  int added,
  int duplicate,
  int invalid,
});

typedef PoolStats = ({
  int total,
  int unknown,
  int available,
  int unavailable,
});

abstract final class OnlineNostalgiaDatabase {
  static final _operationLock = Lock();

  static String get path =>
      p.join(appSupportDirPath, 'online_nostalgia.sqlite3');

  static Future<T> _run<T>(T Function(String dbPath) operation) {
    // appSupportDirPath 是主 isolate 初始化的 late final；必须在切换 isolate
    // 之前解析成普通字符串并随消息传入。
    final dbPath = path;
    // 网络验证可以并发，但SQLite连接初始化和写事务必须短暂串行，
    // 避免多个isolate同时切换WAL/更新状态时触发SQLITE_BUSY。
    return _operationLock.synchronized(
      () => Isolate.run(() => operation(dbPath)),
    );
  }

  static Database _open(String dbPath) {
    final db = sqlite3.open(dbPath);
    db.execute('PRAGMA busy_timeout=15000');
    db.execute('PRAGMA journal_mode=WAL');
    db.execute('PRAGMA synchronous=NORMAL');
    db.execute('PRAGMA foreign_keys=ON');
    db.execute('''
      CREATE TABLE IF NOT EXISTS nostalgia_videos (
        aid INTEGER PRIMARY KEY,
        bvid TEXT NOT NULL UNIQUE,
        source_token TEXT NOT NULL,
        metadata_status INTEGER NOT NULL DEFAULT 0,
        availability_status INTEGER NOT NULL DEFAULT 0,
        title TEXT,
        cover TEXT,
        duration INTEGER,
        pubdate INTEGER,
        cid INTEGER,
        owner_mid INTEGER,
        owner_name TEXT,
        owner_face TEXT,
        tid INTEGER,
        tname TEXT,
        tags TEXT,
        view_count INTEGER,
        like_count INTEGER,
        danmaku_count INTEGER,
        redirect_url TEXT,
        checked_at INTEGER NOT NULL DEFAULT 0,
        retry_at INTEGER NOT NULL DEFAULT 0,
        failure_count INTEGER NOT NULL DEFAULT 0,
        failure_reason TEXT,
        last_recommended_at INTEGER NOT NULL DEFAULT 0,
        recommend_count INTEGER NOT NULL DEFAULT 0,
        session_id INTEGER NOT NULL DEFAULT 0,
        random_key INTEGER NOT NULL
      )
    ''');
    _ensureColumn(
      db,
      table: 'nostalgia_videos',
      column: 'redirect_url',
      definition: 'TEXT',
    );
    _ensureColumn(
      db,
      table: 'nostalgia_videos',
      column: 'session_id',
      definition: 'INTEGER NOT NULL DEFAULT 0',
    );
    db.execute('''
      CREATE TABLE IF NOT EXISTS nostalgia_imports (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        imported_at INTEGER NOT NULL,
        source_name TEXT NOT NULL,
        content_hash TEXT,
        total_count INTEGER NOT NULL,
        added_count INTEGER NOT NULL,
        duplicate_count INTEGER NOT NULL,
        invalid_count INTEGER NOT NULL
      )
    ''');
    db.execute('''
      CREATE TABLE IF NOT EXISTS nostalgia_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        aid INTEGER NOT NULL,
        event_type INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        FOREIGN KEY(aid) REFERENCES nostalgia_videos(aid) ON DELETE CASCADE
      )
    ''');
    db.execute(
      'CREATE INDEX IF NOT EXISTS idx_nostalgia_pool '
      'ON nostalgia_videos(availability_status, retry_at, random_key)',
    );
    db.execute(
      'CREATE INDEX IF NOT EXISTS idx_nostalgia_recent '
      'ON nostalgia_videos(availability_status, last_recommended_at, recommend_count)',
    );
    db.execute(
      'CREATE INDEX IF NOT EXISTS idx_nostalgia_events '
      'ON nostalgia_events(event_type, created_at DESC)',
    );
    return db;
  }

  static void _ensureColumn(
    Database db, {
    required String table,
    required String column,
    required String definition,
  }) {
    final exists = db
        .select('PRAGMA table_info($table)')
        .any((row) => row['name'] == column);
    if (!exists) {
      db.execute('ALTER TABLE $table ADD COLUMN $column $definition');
    }
  }

  static ({int aid, String bvid, String token})? normalize(String raw) {
    final token = raw.trim();
    if (token.isEmpty) return null;
    try {
      if (IdUtils.avRegexExact.hasMatch(token)) {
        final aid = int.parse(
          IdUtils.avRegexExact.firstMatch(token)!.group(1)!,
        );
        if (aid < 0) return null;
        return (aid: aid, bvid: IdUtils.av2bv(aid), token: 'av$aid');
      }
      if (IdUtils.bvRegexExact.hasMatch(token)) {
        // BV编码区分大小写，不能擅自 upper/lower case。
        final aid = IdUtils.bv2av(token);
        final canonicalBvid = IdUtils.av2bv(aid);
        return (aid: aid, bvid: canonicalBvid, token: canonicalBvid);
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  static Future<ImportSummary> importText(
    String text, {
    required String sourceName,
    String? contentHash,
  }) => _run((dbPath) {
    final db = _open(dbPath);
    var total = 0;
    var added = 0;
    var duplicate = 0;
    var invalid = 0;
    final insert = db.prepare('''
      INSERT OR IGNORE INTO nostalgia_videos
        (aid, bvid, source_token, random_key)
      VALUES (?, ?, ?, ?)
    ''');
    db.execute('BEGIN IMMEDIATE');
    try {
      for (final line in text.split(RegExp(r'[\r\n,]+'))) {
        if (line.trim().isEmpty) continue;
        total++;
        final id = normalize(line);
        if (id == null) {
          invalid++;
          continue;
        }
        insert.execute([
          id.aid,
          id.bvid,
          id.token,
          _stableRandomKey(id.aid),
        ]);
        if (db.updatedRows == 1) {
          added++;
        } else {
          duplicate++;
        }
      }
      db.execute(
        'INSERT INTO nostalgia_imports '
        '(imported_at, source_name, content_hash, total_count, added_count, '
        'duplicate_count, invalid_count) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [
          DateTime.now().millisecondsSinceEpoch,
          sourceName,
          contentHash,
          total,
          added,
          duplicate,
          invalid,
        ],
      );
      db.execute('COMMIT');
    } catch (_) {
      db.execute('ROLLBACK');
      rethrow;
    } finally {
      insert.close();
      db.close();
    }
    return (
      total: total,
      added: added,
      duplicate: duplicate,
      invalid: invalid,
    );
  });

  static Future<bool> hasImportedHash(
    String sourceName,
    String contentHash,
  ) => _run((dbPath) {
    final db = _open(dbPath);
    final found = db.select(
      'SELECT 1 FROM nostalgia_imports '
      'WHERE source_name=? AND content_hash=? LIMIT 1',
      [sourceName, contentHash],
    ).isNotEmpty;
    db.close();
    return found;
  });

  static int _stableRandomKey(int aid) {
    var x = aid ^ 0x5bd1e995;
    x = ((x ^ (x >> 13)) * 0x5bd1e995) & 0x7fffffff;
    return x ^ (x >> 15);
  }

  static Future<bool> get isEmpty => _run((dbPath) {
    final db = _open(dbPath);
    final count =
        db.select('SELECT COUNT(*) AS c FROM nostalgia_videos').first['c']
            as int;
    db.close();
    return count == 0;
  });

  static Future<PoolStats> stats() => _run((dbPath) {
    final db = _open(dbPath);
    final rows = db.select(
      'SELECT availability_status, COUNT(*) AS c '
      'FROM nostalgia_videos GROUP BY availability_status',
    );
    var unknown = 0, available = 0, unavailable = 0;
    for (final row in rows) {
      switch (row['availability_status'] as int) {
        case 1:
          available = row['c'] as int;
        case 2:
          unavailable = row['c'] as int;
        default:
          unknown = row['c'] as int;
      }
    }
    db.close();
    return (
      total: unknown + available + unavailable,
      unknown: unknown,
      available: available,
      unavailable: unavailable,
    );
  });

  static Future<List<int>> selectForValidation({
    required int count,
    required Set<int> excluded,
    required int sessionId,
  }) => _run((dbPath) {
    final db = _open(dbPath);
    final s = _statsSync(db);
    if (s.total == 0 || count <= 0) {
      db.close();
      return <int>[];
    }
    final collectedRate = (s.available + s.unavailable) / s.total;
    final exploreRate = collectedRate < .5
        ? .75
        : s.unknown / (s.unknown + s.available).clamp(1, 1 << 62);
    var exploreCount = (count * exploreRate).round().clamp(0, count).toInt();
    var reuseCount = count - exploreCount;
    final recycleCount = (count * .05).floor().clamp(0, reuseCount).toInt();
    final availableCount = reuseCount - recycleCount;
    final ids = <int>[];
    final excludedList = excluded.toList();
    final now = DateTime.now().millisecondsSinceEpoch;

    void addRows(String where, int limit, String order) {
      if (limit <= 0) return;
      final placeholders = excludedList.isEmpty
          ? ''
          : ' AND aid NOT IN (${List.filled(excludedList.length, '?').join(',')})';
      final rows = db.select(
        'SELECT aid FROM nostalgia_videos WHERE $where '
        'AND session_id != ?$placeholders '
        'ORDER BY $order LIMIT ?',
        [sessionId, ...excludedList, limit],
      );
      ids.addAll(rows.map((e) => e['aid'] as int));
    }

    addRows('availability_status=0', exploreCount, 'random_key');
    exploreCount -= ids.length;
    final beforeAvailable = ids.length;
    addRows(
      'availability_status=1',
      availableCount + exploreCount,
      'last_recommended_at ASC, recommend_count ASC, random_key',
    );
    reuseCount -= ids.length - beforeAvailable;
    addRows(
      'availability_status=2 AND retry_at <= $now',
      recycleCount,
      'checked_at ASC, failure_count ASC, random_key',
    );
    final missing = count - ids.length;
    if (missing > 0) {
      final allExcluded = {...excluded, ...ids}.toList();
      final placeholders = List.filled(allExcluded.length, '?').join(',');
      final rows = db.select(
        'SELECT aid FROM nostalgia_videos WHERE availability_status != 2 '
        'AND session_id != ? '
        '${allExcluded.isEmpty ? '' : 'AND aid NOT IN ($placeholders)'} '
        'ORDER BY last_recommended_at ASC, random_key LIMIT ?',
        [sessionId, ...allExcluded, missing],
      );
      ids.addAll(rows.map((e) => e['aid'] as int));
    }
    db.close();
    return ids;
  });

  static PoolStats _statsSync(Database db) {
    final rows = db.select(
      'SELECT availability_status, COUNT(*) AS c '
      'FROM nostalgia_videos GROUP BY availability_status',
    );
    var unknown = 0, available = 0, unavailable = 0;
    for (final row in rows) {
      final count = row['c'] as int;
      switch (row['availability_status'] as int) {
        case 1:
          available += count;
        case 2:
          unavailable += count;
        default:
          unknown += count;
      }
    }
    return (
      total: unknown + available + unavailable,
      unknown: unknown,
      available: available,
      unavailable: unavailable,
    );
  }

  static Future<void> saveAvailable(Map<String, Object?> data) =>
      _run((dbPath) {
        final db = _open(dbPath);
        db.execute(
          '''
      UPDATE nostalgia_videos SET
        bvid=?, metadata_status=1, availability_status=1,
        title=?, cover=?, duration=?, pubdate=?, cid=?,
        owner_mid=?, owner_name=?, owner_face=?, tid=?, tname=?, tags=?,
        view_count=?, like_count=?, danmaku_count=?, redirect_url=?,
        checked_at=?, retry_at=0, failure_count=0, failure_reason=NULL
      WHERE aid=?
    ''',
          [
            data['bvid'],
            data['title'],
            data['cover'],
            data['duration'],
            data['pubdate'],
            data['cid'],
            data['owner_mid'],
            data['owner_name'],
            data['owner_face'],
            data['tid'],
            data['tname'],
            data['tags'],
            data['view_count'],
            data['like_count'],
            data['danmaku_count'],
            data['redirect_url'],
            DateTime.now().millisecondsSinceEpoch,
            data['aid'],
          ],
        );
        db.close();
      });

  static Future<void> saveFailure(
    int aid, {
    required String reason,
    required bool permanent,
  }) => _run((dbPath) {
    final db = _open(dbPath);
    final now = DateTime.now().millisecondsSinceEpoch;
    final retryAt = permanent
        ? now + const Duration(days: 30).inMilliseconds
        : now + const Duration(hours: 6).inMilliseconds;
    db.execute(
      '''
      UPDATE nostalgia_videos SET
        availability_status=2, checked_at=?, retry_at=?,
        failure_count=failure_count+1, failure_reason=?
      WHERE aid=?
    ''',
      [now, retryAt, reason, aid],
    );
    db.close();
  });

  static Future<void> touchAvailable(int aid) => _run((dbPath) {
    final db = _open(dbPath);
    db.execute(
      'UPDATE nostalgia_videos SET availability_status=1, checked_at=?, '
      'retry_at=0, failure_count=0, failure_reason=NULL WHERE aid=?',
      [DateTime.now().millisecondsSinceEpoch, aid],
    );
    db.close();
  });

  static Future<List<OnlineNostalgiaVideo>> loadVideos(
    Iterable<int> aids,
  ) => _run((dbPath) {
    final ids = aids.toList();
    if (ids.isEmpty) return <OnlineNostalgiaVideo>[];
    final db = _open(dbPath);
    final placeholders = List.filled(ids.length, '?').join(',');
    final rows = db.select(
      'SELECT * FROM nostalgia_videos '
      'WHERE availability_status=1 AND aid IN ($placeholders)',
      ids,
    );
    final byAid = {
      for (final row in rows)
        row['aid'] as int: OnlineNostalgiaVideo.fromDb(row),
    };
    db.close();
    return [
      for (final aid in ids)
        if (byAid[aid] case final v?) v,
    ];
  });

  static Future<void> markRecommended(
    Iterable<int> aids, {
    required int sessionId,
  }) => _run((dbPath) {
    final ids = aids.toList();
    if (ids.isEmpty) return;
    final db = _open(dbPath);
    final statement = db.prepare(
      'UPDATE nostalgia_videos SET last_recommended_at=?, '
      'recommend_count=recommend_count+1, session_id=? WHERE aid=?',
    );
    db.execute('BEGIN');
    final now = DateTime.now().millisecondsSinceEpoch;
    for (final aid in ids) {
      statement.execute([now, sessionId, aid]);
    }
    db.execute('COMMIT');
    statement.close();
    db.close();
  });

  static Future<void> recordClick(int aid) => _run((dbPath) {
    final db = _open(dbPath);
    db.execute(
      'INSERT INTO nostalgia_events(aid, event_type, created_at) '
      'VALUES (?, 1, ?)',
      [aid, DateTime.now().millisecondsSinceEpoch],
    );
    // 画像只需近期信号，限制事件表增长。
    db.execute('''
      DELETE FROM nostalgia_events
      WHERE id NOT IN (
        SELECT id FROM nostalgia_events ORDER BY created_at DESC LIMIT 2000
      )
    ''');
    db.close();
  });

  static Future<Map<String, double>> preferenceProfile() => _run((dbPath) {
    final db = _open(dbPath);
    final rows = db.select('''
      SELECT v.owner_mid, v.tid, v.tags, e.created_at
      FROM nostalgia_events e
      JOIN nostalgia_videos v ON v.aid=e.aid
      WHERE e.event_type=1
      ORDER BY e.created_at DESC
      LIMIT 500
    ''');
    final profile = <String, double>{};
    final now = DateTime.now().millisecondsSinceEpoch;
    for (final row in rows) {
      final ageDays =
          (now - (row['created_at'] as int)) / Duration.millisecondsPerDay;
      final weight = .5 * pow(.5, ageDays / 90).toDouble();
      final mid = row['owner_mid'] as int?;
      final tid = row['tid'] as int?;
      if (mid != null) profile['up:$mid'] = (profile['up:$mid'] ?? 0) + weight;
      if (tid != null && tid != 0) {
        profile['zone:$tid'] = (profile['zone:$tid'] ?? 0) + weight;
      }
      for (final tag in (row['tags'] as String? ?? '').split('\u001f')) {
        if (tag.isNotEmpty) {
          profile['tag:$tag'] = (profile['tag:$tag'] ?? 0) + weight;
        }
      }
    }
    db.close();
    return profile;
  });

  static Future<String> exportText() => _run((dbPath) {
    final db = _open(dbPath);
    final rows = db.select(
      'SELECT aid FROM nostalgia_videos ORDER BY aid',
    );
    final output = rows.map((e) => 'av${e['aid']}').join('\n');
    db.close();
    return '$output\n';
  });
}
