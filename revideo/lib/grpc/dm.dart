import 'package:PiliPlus/grpc/bilibili/community/service/dm/v1.pb.dart';
import 'package:PiliPlus/grpc/grpc_req.dart';
import 'package:PiliPlus/grpc/url.dart';
import 'package:PiliPlus/http/danmaku_offline.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';
import 'package:fixnum/fixnum.dart';

abstract final class DmGrpc {
  static Future<LoadingState<DmSegMobileReply>> dmSegMobile({
    required int cid,
    required int segmentIndex,
    int type = 1,
  }) {
    // === OFFLINE-NOSTALGIA-MODE BEGIN ===
    if (OfflineConfig.enabled) {
      return OfflineDanmakuHttp.dmSegMobile(cid: cid);
    }
    // === OFFLINE-NOSTALGIA-MODE END ===
    return GrpcReq.request(
      GrpcUrl.dmSegMobile,
      DmSegMobileReq(
        oid: Int64(cid),
        segmentIndex: Int64(segmentIndex),
        type: type,
      ),
      DmSegMobileReply.fromBuffer,
      isolate: true,
    );
  }

  static Future<LoadingState<DmViewReply>> dmView(int aid, int cid) {
    return GrpcReq.request(
      GrpcUrl.dmView,
      DmViewReq(pid: Int64(aid), oid: Int64(cid), type: 1),
      DmViewReply.fromBuffer,
    );
  }
}
