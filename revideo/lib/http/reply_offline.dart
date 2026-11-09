// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式：评论走自建服务端，服务端已经把 BiliDanmuComment_cs 归档的
// comments.7z 重整形成 ReplyData/ReplyItemModel 期望的JSON形状了，这里直接
// fromJson 解析，不用再写一套新的评论模型/渲染逻辑。
import 'package:PiliPlus/grpc/bilibili/main/community/reply/v1.pb.dart'
    show
        MainListReply,
        DetailListReply,
        ReplyInfo,
        Content,
        Member,
        ReplyControl,
        CursorReply;
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models_new/reply/data.dart';
import 'package:PiliPlus/models_new/reply2reply/data.dart';
import 'package:PiliPlus/utils/offline/local_interactions.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';
import 'package:fixnum/fixnum.dart';

abstract final class OfflineReplyHttp {
  static int _asInt(dynamic v) => switch (v) {
    int i => i,
    num n => n.toInt(),
    String s => int.tryParse(s) ?? 0,
    _ => 0,
  };

  // 服务端归档评论JSON → grpc ReplyInfo(视频页评论区UI只认这个protobuf类型)。
  // 递归处理楼中楼(replies字段)。评论者头像是B站CDN直链，手机直接加载。
  static ReplyInfo _toReplyInfo(Map j) {
    final info = ReplyInfo()
      ..id = Int64(_asInt(j['rpid']))
      ..oid = Int64(_asInt(j['oid']))
      ..mid = Int64(_asInt(j['mid']))
      ..root = Int64(_asInt(j['root']))
      ..parent = Int64(_asInt(j['parent']))
      ..like = Int64(_asInt(j['like']))
      ..ctime = Int64(_asInt(j['ctime']))
      ..count = Int64(_asInt(j['rcount']))
      ..content = (Content()..message = (j['content']?['message'] ?? '').toString());
    if (j['member'] is Map) {
      final m = j['member'] as Map;
      info.member = Member()
        ..mid = Int64(_asInt(m['mid']))
        ..name = (m['uname'] ?? '').toString()
        ..sex = (m['sex'] ?? '').toString()
        ..face = (m['avatar'] ?? '').toString()
        ..level = Int64(_asInt((m['level_info'] as Map?)?['current_level']));
    }
    if (j['reply_control'] is Map && (j['reply_control'] as Map)['location'] != null) {
      info.replyControl = ReplyControl()
        ..location = (j['reply_control'] as Map)['location'].toString();
    }
    if (j['replies'] is List) {
      info.replies.addAll((j['replies'] as List).whereType<Map>().map(_toReplyInfo));
    }
    return info;
  }

  // 本地单机评论(仅 message/sentAt) → 合成一条置顶ReplyInfo，作者标注"仅本机"。
  static ReplyInfo _localToReplyInfo(Map j, int oid) {
    final sentAt = _asInt(j['sentAt']);
    return ReplyInfo()
      ..id = Int64(-(sentAt == 0 ? 1 : sentAt)) // 负id避免与真实rpid冲突
      ..oid = Int64(oid)
      ..mid = Int64.ZERO
      ..like = Int64.ZERO
      ..ctime = Int64(sentAt ~/ 1000)
      ..count = Int64.ZERO
      ..content = (Content()..message = (j['message'] ?? '').toString())
      ..member = (Member()
        ..name = '我 (仅本机)'
        ..face = '');
  }

  // 视频页评论区(grpc主列表)的离线实现：本地单机评论置顶 + 服务端归档评论。
  // 归档是一次性快照、无真分页，全量一次给完。
  static Future<LoadingState<MainListReply>> grpcMainList({
    required int oid,
  }) async {
    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/replies/$oid').toString(),
      );
      if (res.data is! Map) {
        return const Error('单机怀旧模式：服务端返回格式异常');
      }
      final reply = MainListReply()
        ..cursor = (CursorReply()
          ..isBegin = true
          ..isEnd = true);
      for (final lr in OfflineLocalInteractions.localRepliesFor(oid)) {
        reply.replies.add(_localToReplyInfo(lr, oid));
      }
      final archived = (res.data as Map)['replies'];
      if (archived is List) {
        reply.replies.addAll(archived.whereType<Map>().map(_toReplyInfo));
      }
      return Success(reply);
    } catch (e) {
      OfflineConfig.invalidate();
      return Error('单机怀旧模式评论获取失败: $e');
    }
  }

  // 楼中楼"查看全部回复"的离线实现(grpc DetailListReply)。
  static Future<LoadingState<DetailListReply>> grpcDetailList({
    required int oid,
    required int root,
  }) async {
    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/replies/$oid').toString(),
      );
      if (res.data is! Map) {
        return const Error('单机怀旧模式：服务端返回格式异常');
      }
      final archived = (res.data as Map)['replies'];
      if (archived is List) {
        for (final r in archived.whereType<Map>()) {
          if (_asInt(r['rpid']) == root) {
            // root 自身经 _toReplyInfo 已带上其楼中楼(replies)，DetailListReply
            // 本身无独立 replies 字段，UI 从 root.replies 读取。
            final detail = DetailListReply()
              ..cursor = (CursorReply()..isEnd = true)
              ..root = _toReplyInfo(r);
            return Success(detail);
          }
        }
      }
      return const Error('单机怀旧模式：归档里找不到这条评论');
    } catch (e) {
      OfflineConfig.invalidate();
      return Error('单机怀旧模式评论获取失败: $e');
    }
  }

  static Future<LoadingState<ReplyData>> replyList({
    required int oid,
  }) async {
    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/replies/$oid').toString(),
      );
      if (res.data is Map) {
        return Success(ReplyData.fromJson(res.data as Map<String, dynamic>));
      }
      return const Error('单机怀旧模式：服务端返回格式异常');
    } catch (e) {
      OfflineConfig.invalidate();
      return Error('单机怀旧模式评论获取失败: $e');
    }
  }

  // 楼中楼"查看全部回复"页：归档评论的楼中楼在服务端返回的JSON里本来就是
  // 全量内嵌的(replies字段)，这里重新拉一次整页、按rpid找到根评论、在JSON层
  // 组装成 ReplyReplyData 期望的形状(root就是一条评论对象，字段同构)。
  // 归档是一次性快照，没有真分页——第一页给全量，后续页给空表示到底。
  static Future<LoadingState<ReplyReplyData>> replyReplyList({
    required int oid,
    required int root,
    required int pageNum,
  }) async {
    if (pageNum > 1) {
      return Success(ReplyReplyData(replies: []));
    }
    try {
      await OfflineConfig.ensureResolved();
      final res = await Request().get(
        OfflineConfig.apiUri('/api/v1/replies/$oid').toString(),
      );
      if (res.data is! Map) {
        return const Error('单机怀旧模式：服务端返回格式异常');
      }
      final replies = (res.data as Map)['replies'];
      if (replies is List) {
        for (final r in replies) {
          if (r is Map && r['rpid'] == root) {
            final subReplies = r['replies'] is List ? r['replies'] as List : const [];
            return Success(
              ReplyReplyData.fromJson({
                'page': {
                  'num': 1,
                  'size': subReplies.length,
                  'count': subReplies.length,
                },
                'replies': subReplies,
                'root': r,
              }),
            );
          }
        }
      }
      return const Error('单机怀旧模式：归档里找不到这条评论');
    } catch (e) {
      return Error('单机怀旧模式评论获取失败: $e');
    }
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
