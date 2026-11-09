import 'package:PiliPlus/models/model_owner.dart';
import 'package:PiliPlus/models/model_rec_video_item.dart';
import 'package:PiliPlus/models/model_video.dart';

class OnlineNostalgiaVideo extends BaseRcmdVideoItemModel {
  List<String> tags = const [];
  int tid = 0;
  String tname = '';
  int viewCount = 0;
  int likeCount = 0;
  int lastRecommendedAt = 0;
  int recommendCount = 0;
  String? redirectUrl;

  OnlineNostalgiaVideo.fromDb(Map<String, Object?> row) {
    goto = 'av';
    aid = row['aid'] as int;
    bvid = row['bvid'] as String?;
    cid = row['cid'] as int?;
    title = row['title'] as String? ?? '';
    cover = row['cover'] as String?;
    duration = row['duration'] as int? ?? -1;
    pubdate = row['pubdate'] as int?;
    tags = (row['tags'] as String? ?? '')
        .split('\u001f')
        .where((e) => e.isNotEmpty)
        .toList();
    tid = row['tid'] as int? ?? 0;
    tname = row['tname'] as String? ?? '';
    viewCount = row['view_count'] as int? ?? 0;
    likeCount = row['like_count'] as int? ?? 0;
    lastRecommendedAt = row['last_recommended_at'] as int? ?? 0;
    recommendCount = row['recommend_count'] as int? ?? 0;
    redirectUrl = row['redirect_url'] as String?;
    owner = Owner(
      mid: row['owner_mid'] as int?,
      name: row['owner_name'] as String?,
      face: row['owner_face'] as String?,
    );
    stat = Stat.fromJson({
      'view': viewCount,
      'like': likeCount,
      'danmaku': row['danmaku_count'] as int? ?? 0,
    });
  }
}
