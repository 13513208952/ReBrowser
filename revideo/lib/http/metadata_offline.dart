// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式：视频详情元数据从自建服务端取。服务端 /api/v1/videos/{id}
// 的响应字段名就是按B站 view 接口原生形状吐的(aid/bvid/cid/title/desc/
// pubdate/owner/stat/pages...)，这里直接喂给上游现成的 VideoDetailData.fromJson，
// 不需要客户端侧适配层。
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models_new/video/video_detail/data.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';

abstract final class OfflineMetadataHttp {
  static Future<LoadingState<VideoDetailData>> videoIntro({
    required String bvid,
  }) async {
    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/videos/$bvid').toString(),
      );
      if (res.data is Map) {
        return Success(
          VideoDetailData.fromJson(res.data as Map<String, dynamic>),
        );
      }
      return const Error('单机怀旧模式：该视频不在本地归档目录中');
    } catch (e) {
      OfflineConfig.invalidate();
      return Error('单机怀旧模式连接失败: $e');
    }
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
