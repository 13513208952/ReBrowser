// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式：从自建局域网服务端取 playurl，构造出和官方接口一样的
// PlayUrlModel 形状，下游 media_kit 播放器完全不用改。
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models/video/play/url.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';

abstract final class OfflineVideoHttp {
  static Future<LoadingState<PlayUrlModel>> videoUrl({
    int? avid,
    String? bvid,
    required int cid,
  }) async {
    final id = bvid ?? avid?.toString();
    if (id == null) {
      return const Error('单机怀旧模式：缺少视频ID');
    }

    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/videos/$id/playurl').toString(),
      );
      if (res.data is Map) {
        return Success(PlayUrlModel.fromJson(res.data as Map<String, dynamic>));
      }
      return const Error('单机怀旧模式：服务端返回格式异常');
    } catch (e) {
      OfflineConfig.invalidate();
      return Error('单机怀旧模式连接失败: $e');
    }
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
