import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/http/user.dart';
import 'package:PiliPlus/http/video.dart';
import 'package:PiliPlus/models/common/account_type.dart';
import 'package:PiliPlus/models/common/video/video_type.dart';
import 'package:PiliPlus/models/online_nostalgia/online_nostalgia_video.dart';
import 'package:PiliPlus/utils/accounts.dart';
import 'package:PiliPlus/utils/id_utils.dart';
import 'package:PiliPlus/utils/nostalgia/online_nostalgia_database.dart';

abstract final class OnlineNostalgiaHttp {
  static Future<bool> validate(
    int aid, {
    OnlineNostalgiaVideo? cached,
  }) async {
    final bvid = IdUtils.av2bv(aid);
    try {
      if (cached?.cid case final cid?) {
        final play = await _playUrl(
          aid: aid,
          bvid: cached!.bvid ?? bvid,
          cid: cid,
          redirectUrl: cached.redirectUrl,
        );
        if (play is Success) {
          await OnlineNostalgiaDatabase.touchAvailable(aid);
          return true;
        }
        await _saveError(aid, play.toString());
        return false;
      }

      final metadata = await VideoHttp.videoIntro(bvid: bvid);
      if (metadata case Success(:final response)) {
        final cid =
            response.cid ??
            (response.pages?.isNotEmpty == true
                ? response.pages!.first.cid
                : null);
        if (cid == null) {
          await OnlineNostalgiaDatabase.saveFailure(
            aid,
            reason: '元数据缺少cid',
            permanent: true,
          );
          return false;
        }

        // 真正请求playurl但不下载媒体字节，确认当前账号/网络下可播放。
        final play = await _playUrl(
          aid: aid,
          bvid: response.bvid ?? bvid,
          cid: cid,
          redirectUrl: response.redirectUrl,
        );
        if (play is! Success) {
          await _saveError(aid, play.toString());
          return false;
        }

        final tagsResult = await UserHttp.videoTags(
          bvid: response.bvid ?? bvid,
          cid: cid,
        );
        final tags =
            tagsResult.dataOrNull
                ?.map((e) => e.tagName ?? '')
                .where((e) => e.isNotEmpty)
                .join('\u001f') ??
            '';
        await OnlineNostalgiaDatabase.saveAvailable({
          'aid': response.aid ?? aid,
          'bvid': response.bvid ?? bvid,
          'title': response.title ?? '',
          'cover': response.pic,
          'duration': response.duration ?? -1,
          'pubdate': response.pubdate,
          'cid': cid,
          'owner_mid': response.owner?.mid,
          'owner_name': response.owner?.name,
          'owner_face': response.owner?.face,
          'tid': response.tid ?? 0,
          'tname': response.tname ?? '',
          'tags': tags,
          'view_count': response.stat?.view ?? 0,
          'like_count': response.stat?.like ?? 0,
          'danmaku_count': response.stat?.danmaku ?? 0,
          'redirect_url': response.redirectUrl,
        });
        return true;
      }
      await _saveError(aid, metadata.toString());
    } catch (e) {
      await OnlineNostalgiaDatabase.saveFailure(
        aid,
        reason: e.toString(),
        permanent: false,
      );
    }
    return false;
  }

  static Future<void> _saveError(int aid, String reason) {
    final lower = reason.toLowerCase();
    final permanent =
        lower.contains('-404') ||
        reason.contains('不存在') ||
        reason.contains('已删除') ||
        reason.contains('不可见');
    return OnlineNostalgiaDatabase.saveFailure(
      aid,
      reason: reason,
      permanent: permanent,
    );
  }

  static Future<LoadingState<dynamic>> _playUrl({
    required int aid,
    required String bvid,
    required int cid,
    String? redirectUrl,
  }) {
    final pgc = redirectUrl == null
        ? null
        : RegExp(r'(ep|ss)(\d+)').firstMatch(redirectUrl);
    final isSeason = pgc?.group(1) == 'ss';
    final id = pgc?.group(2);
    return VideoHttp.videoUrl(
      avid: aid,
      bvid: bvid,
      cid: cid,
      epid: pgc != null && !isSeason ? id : null,
      seasonId: isSeason ? id : null,
      tryLook: !Accounts.get(AccountType.video).isLogin,
      videoType: pgc == null ? VideoType.ugc : VideoType.pgc,
    );
  }
}
