// === OFFLINE-NOSTALGIA-MODE BEGIN ===
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/utils/nostalgia/nostalgia_config.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:dio/dio.dart' show Options;

// 单机怀旧模式的配置门面 + 连接解析器。
//
// 候选地址体系(按设置里的USB直连档位排序，谁先探通用谁)：
//   - USB直连: http://127.0.0.1:port —— 服务端电脑上的 adb reverse 隧道，
//     数据线插上即通(需USB调试授权)，与WiFi/热点/防火墙全部无关
//   - 主地址/备用地址: 内网IP和公网IP各填一个，在家走内网、在外走公网
// 探测结果缓存60秒；任何一次请求连不上时调 invalidate() 触发下次重新探测。
abstract final class OfflineConfig {
  static bool get enabled => NostalgiaConfig.offline;

  static String? _activeBase;
  static DateTime? _resolvedAt;

  static List<String> get candidateBases {
    final port = Pref.offlineServerPort;
    final usb = 'http://127.0.0.1:$port';
    final hosts = <String>[
      if (Pref.offlineServerHost.isNotEmpty)
        'http://${Pref.offlineServerHost}:$port',
      if (Pref.offlineServerHostBackup.isNotEmpty)
        'http://${Pref.offlineServerHostBackup}:$port',
    ];
    return switch (Pref.offlineUsbLinkMode) {
      1 => [usb, ...hosts],
      2 => [...hosts, usb],
      // 禁用USB：只用配置的地址；一个都没配时保底给usb免得baseUrl畸形
      _ => hosts.isEmpty ? [usb] : hosts,
    };
  }

  /// 当前生效的服务端基地址。未探测过时返回首选项(不阻塞UI同步读取)。
  static String get baseUrl => _activeBase ?? candidateBases.first;

  /// 按候选顺序探测 /health，第一个通的设为生效地址。
  /// offline的http辅助类在每次请求前调用；有60秒缓存，正常使用几乎无感。
  static Future<void> ensureResolved({bool force = false}) async {
    if (!enabled) return;
    if (!force &&
        _activeBase != null &&
        _resolvedAt != null &&
        DateTime.now().difference(_resolvedAt!) < const Duration(seconds: 60)) {
      return;
    }
    final cands = candidateBases;
    for (final base in cands) {
      if (await _probe(base)) {
        _activeBase = base;
        _resolvedAt = DateTime.now();
        return;
      }
    }
    // 全部探测失败：保持首选让上层请求自然报错提示用户，
    // 缓存时间戳往回拨，10秒后就允许重新探测(而不是干等60秒)。
    _activeBase = cands.first;
    _resolvedAt = DateTime.now().subtract(const Duration(seconds: 50));
  }

  static Future<bool> _probe(String base) async {
    try {
      final res = await Request().get(
        '$base/health',
        options: Options(
          sendTimeout: const Duration(milliseconds: 1500),
          receiveTimeout: const Duration(milliseconds: 1500),
        ),
      );
      return res.data is Map && res.data['status'] == 'ok';
    } catch (_) {
      return false;
    }
  }

  /// 请求失败时调用：清掉生效地址，下一次请求前重新全量探测。
  static void invalidate() {
    _activeBase = null;
    _resolvedAt = null;
  }

  static Uri apiUri(String path, [Map<String, dynamic>? queryParameters]) {
    return Uri.parse('$baseUrl$path').replace(
      queryParameters: queryParameters?.map(
        (key, value) => MapEntry(key, value?.toString()),
      ),
    );
  }
}

// === OFFLINE-NOSTALGIA-MODE END ===
