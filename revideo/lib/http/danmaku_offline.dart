// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式：弹幕走 protobuf 透传，服务端直接吐 DmSegMobileReply 的原始字节，
// 客户端复用现成的 fromBuffer 解码器，不用另写解析逻辑。
// 注：Phase 1 walking skeleton 只有几秒钟的测试视频，服务端一次性把全部弹幕都发回来，
// 没有做官方API那种6分钟一段的segment分页——真实长视频的分段策略留到 Phase 2 再看要不要补。
import 'package:PiliPlus/grpc/bilibili/community/service/dm/v1.pb.dart';
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';
import 'package:dio/dio.dart' show Options, ResponseType;

abstract final class OfflineDanmakuHttp {
  static Future<LoadingState<DmSegMobileReply>> dmSegMobile({
    required int cid,
  }) async {
    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/danmaku/$cid').toString(),
        options: Options(responseType: ResponseType.bytes),
      );
      final bytes = res.data as List<int>;
      return Success(DmSegMobileReply.fromBuffer(bytes));
    } catch (e) {
      OfflineConfig.invalidate();
      return Error('单机怀旧模式弹幕获取失败: $e');
    }
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
