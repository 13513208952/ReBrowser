// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式本地互动记录：点赞/投币/收藏/关注/不感兴趣/观看历史/本地弹幕/本地评论，
// 仿照 Pref.blackMids 的 "Set/Map 存 GStorage.localCache + 每次直接读写 Hive" 模式，
// 不新开 Hive box，复用现成的 localCache。这些数据也是本地推荐引擎
// (recommend_engine.dart) 的画像输入，永不上传。
//
// ⚠️ 关键教训(真机bug 2026-07-18)：Hive 落盘再读回的容器是 Map<dynamic,dynamic>/
// List<dynamic>，用 .cast<...>() 是懒视图，重启后首次遍历会抛
// "type '_Map<dynamic,dynamic>' is not a subtype ..."。所以这里全部用
// 深拷贝转换(Map.from/List.from逐层重建)，绝不用 cast。
import 'package:PiliPlus/utils/storage.dart';
import 'package:PiliPlus/utils/storage_key.dart';

abstract final class OfflineLocalInteractions {
  // ---- 防御性深转换工具：任何来自Hive的容器都经这里重建为强类型 ----

  static Set<String> _readStringSet(String key) {
    final raw = GStorage.localCache.get(key);
    if (raw is Iterable) return raw.map((e) => e.toString()).toSet();
    return <String>{};
  }

  static Set<int> _readIntSet(String key) {
    final raw = GStorage.localCache.get(key);
    if (raw is Iterable) {
      return raw.whereType<num>().map((e) => e.toInt()).toSet();
    }
    return <int>{};
  }

  static Map<String, dynamic> _deepMap(Map raw) => {
    for (final e in raw.entries) e.key.toString(): _deepValue(e.value),
  };

  static dynamic _deepValue(dynamic v) {
    if (v is Map) return _deepMap(v);
    if (v is List) return v.map(_deepValue).toList();
    return v;
  }

  static List<Map<String, dynamic>> _readMapList(String key) {
    final raw = GStorage.localCache.get(key);
    if (raw is List) {
      return raw.whereType<Map>().map(_deepMap).toList();
    }
    return <Map<String, dynamic>>[];
  }

  static Map<String, List<Map<String, dynamic>>> _readMapOfMapLists(String key) {
    final raw = GStorage.localCache.get(key);
    final out = <String, List<Map<String, dynamic>>>{};
    if (raw is Map) {
      for (final e in raw.entries) {
        final v = e.value;
        if (v is List) {
          out[e.key.toString()] = v.whereType<Map>().map(_deepMap).toList();
        }
      }
    }
    return out;
  }

  // ---- 点赞/投币/收藏：Set<bvid> ----

  static Set<String> get likedBvids =>
      _readStringSet(LocalCacheKey.offlineLikedBvids);

  static bool isLiked(String bvid) => likedBvids.contains(bvid);

  static void toggleLike(String bvid) {
    final set = likedBvids;
    if (!set.add(bvid)) set.remove(bvid);
    GStorage.localCache.put(LocalCacheKey.offlineLikedBvids, set);
  }

  static Set<String> get coinedBvids =>
      _readStringSet(LocalCacheKey.offlineCoinedBvids);

  static Set<String> get favoritedBvids =>
      _readStringSet(LocalCacheKey.offlineFavoritedBvids);

  static bool isCoined(String bvid) => coinedBvids.contains(bvid);

  static void setCoined(String bvid) {
    final set = coinedBvids..add(bvid);
    GStorage.localCache.put(LocalCacheKey.offlineCoinedBvids, set);
  }

  static bool isFavorited(String bvid) => favoritedBvids.contains(bvid);

  static bool toggleFavorite(String bvid) {
    final set = favoritedBvids;
    final nowFav = set.add(bvid);
    if (!nowFav) set.remove(bvid);
    GStorage.localCache.put(LocalCacheKey.offlineFavoritedBvids, set);
    return nowFav;
  }

  // ---- 关注UP主：Set<mid>。推荐引擎里"关注"不进画像向量，
  //      单独作为强信号定期强制留名额，见 recommend_engine.dart ----

  static Set<int> get followedMids =>
      _readIntSet(LocalCacheKey.offlineFollowedMids);

  static bool isFollowed(int mid) => followedMids.contains(mid);

  static bool toggleFollow(int mid) {
    final set = followedMids;
    final nowFollowed = set.add(mid);
    if (!nowFollowed) set.remove(mid);
    GStorage.localCache.put(LocalCacheKey.offlineFollowedMids, set);
    return nowFollowed;
  }

  // ---- 视频级"不感兴趣"：硬性永久排除出推荐候选，除非手动移除。
  //      Map<bvid, {reason, at}>，reason 用于"已排除列表"里回显 ----

  static Map<String, Map<String, dynamic>> get dislikedVideos {
    final raw = GStorage.localCache.get(LocalCacheKey.offlineDislikedVideos);
    final out = <String, Map<String, dynamic>>{};
    if (raw is Map) {
      for (final e in raw.entries) {
        if (e.value is Map) out[e.key.toString()] = _deepMap(e.value as Map);
      }
    }
    return out;
  }

  static bool isDisliked(String bvid) => dislikedVideos.containsKey(bvid);

  static void addDislike({required String bvid, required String reason}) {
    final all = dislikedVideos;
    all[bvid] = {
      'reason': reason,
      'at': DateTime.now().millisecondsSinceEpoch,
    };
    GStorage.localCache.put(LocalCacheKey.offlineDislikedVideos, all);
  }

  static void removeDislike(String bvid) {
    final all = dislikedVideos..remove(bvid);
    GStorage.localCache.put(LocalCacheKey.offlineDislikedVideos, all);
  }

  // ---- 观看历史日志：watchProgress 只按 cid 覆盖存"最后位置"，
  //      这里补一份带时间戳的追加日志，供画像的弱隐性信号用
  //      (完成度超过阈值才算兴趣，见 recommend_engine.dart) ----

  static const int _watchHistoryCap = 2000;

  static List<Map<String, dynamic>> get watchHistory =>
      _readMapList(LocalCacheKey.offlineWatchHistory);

  static void recordWatch({
    required String bvid,
    required int cid,
    required int progressMs,
    required int durationMs,
  }) {
    final list = watchHistory;
    // 同一次观看(同bvid+cid的最后一条)只更新进度，不重复追加，
    // 隔了新条目再看才算一次新观看。
    if (list.isNotEmpty &&
        list.last['bvid'] == bvid &&
        list.last['cid'] == cid) {
      list.last['progressMs'] = progressMs;
      list.last['durationMs'] = durationMs;
    } else {
      list.add({
        'bvid': bvid,
        'cid': cid,
        'at': DateTime.now().millisecondsSinceEpoch,
        'progressMs': progressMs,
        'durationMs': durationMs,
      });
      if (list.length > _watchHistoryCap) {
        list.removeRange(0, list.length - _watchHistoryCap);
      }
    }
    GStorage.localCache.put(LocalCacheKey.offlineWatchHistory, list);
  }

  // ---- 本地弹幕/本地评论 ----

  static List<Map<String, dynamic>> localDanmakuFor(int cid) =>
      _readMapOfMapLists(LocalCacheKey.offlineLocalDanmaku)[cid.toString()] ??
      const [];

  static void addLocalDanmaku({
    required int cid,
    required String content,
    required int progressMs,
    int mode = 1,
    int color = 16777215,
  }) {
    final all = _readMapOfMapLists(LocalCacheKey.offlineLocalDanmaku);
    final list = all[cid.toString()] ?? <Map<String, dynamic>>[];
    list.add({
      'content': content,
      'progress': progressMs,
      'mode': mode,
      'color': color,
      'sentAt': DateTime.now().millisecondsSinceEpoch,
    });
    all[cid.toString()] = list;
    GStorage.localCache.put(LocalCacheKey.offlineLocalDanmaku, all);
  }

  static List<Map<String, dynamic>> localRepliesFor(int aid) =>
      _readMapOfMapLists(LocalCacheKey.offlineLocalReplies)[aid.toString()] ??
      const [];

  static void addLocalReply({required int aid, required String message}) {
    final all = _readMapOfMapLists(LocalCacheKey.offlineLocalReplies);
    final list = all[aid.toString()] ?? <Map<String, dynamic>>[];
    list.add({
      'message': message,
      'sentAt': DateTime.now().millisecondsSinceEpoch,
    });
    all[aid.toString()] = list;
    GStorage.localCache.put(LocalCacheKey.offlineLocalReplies, all);
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
