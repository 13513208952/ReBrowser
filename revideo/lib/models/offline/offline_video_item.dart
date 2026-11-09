// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式：自建服务端 /api/v1/videos 目录项。
// 继承 BaseRcmdVideoItemModel，这样 VideoCardV / VideoPopupMenu 这套
// 现成的推荐卡片UI原样可用；额外带上 tags/tid/tname/viewCount 这些
// 推荐画像特征字段，供本地 recommend_engine 打分用。
import 'package:PiliPlus/models/model_owner.dart';
import 'package:PiliPlus/models/model_rec_video_item.dart';
import 'package:PiliPlus/models/model_video.dart';
import 'package:PiliPlus/utils/offline/local_interactions.dart';

class OfflineVideoItemModel extends BaseRcmdVideoItemModel {
  List<String> tags = const [];
  int tid = 0;
  String tname = '';
  int viewCount = 0;
  int likeCount = 0; // 归档时的历史点赞数快照，贝叶斯质量先验用

  OfflineVideoItemModel.fromJson(Map<String, dynamic> json) {
    goto = 'av'; // 服务端目录都是本地归档视频，走av播放路径
    aid = json['aid'];
    bvid = json['bvid'];
    cid = json['cid'];
    title = json['title'] ?? '';
    cover = json['pic'];
    duration = json['duration'] ?? -1;
    pubdate = json['pubdate'];
    tags = List<String>.from(json['tags'] ?? const []);
    tid = json['tid'] ?? 0;
    tname = json['tname'] ?? '';
    viewCount = json['view_count'] ?? 0;
    likeCount = json['like_count'] ?? 0;
    owner = Owner.fromJson(json['owner'] ?? const {});
    stat = Stat.fromJson({'view': viewCount, 'like': likeCount});
    isFollowed =
        owner.mid != null && OfflineLocalInteractions.isFollowed(owner.mid!);
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
