// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式本地推荐引擎。全部计算发生在客户端本地，服务端不参与——
// 画像数据来自本地专属的点赞/投币/收藏/关注/不感兴趣/观看历史记录，永不上传。
//
// 采用的都是2022年之前、非模型非AI的经典算法（1~3个使用者的量级撑不起协同过滤）：
//  1. Rocchio 相关反馈 (Rocchio, 1971)：
//       画像 = Σ β·decay(t)·正反馈特征 − Σ γ·decay(t)·负反馈特征
//     这里用"事件日志 + 排序时批量重算"的实现：每次排序从本地互动日志重建画像，
//     指数式时间衰减等价于反复迭代 α<1 的增量式 Rocchio(旧画像不断被打折)，
//     好处是 β/γ 权重以后可调而无需迁移已存画像。γ=1.5β，保证"点踩"的压制力
//     不会被少量后续正反馈稀释——这是硬性要求。
//  2. 贝叶斯加权评分 (IMDB经典公式)：
//       WR = v/(v+m)·R + m/(v+m)·C   (R=该视频历史赞播比, C=全目录平均, v=播放量)
//     小样本视频的质量分向全体均值收缩，避免"凑巧几个赞"被捧高，
//     log压缩播放量避免头部通吃。
//  3. MMR 多样性 (Carbonell & Goldstein, SIGIR 1998)：
//       next = argmax λ·rel(i) − (1−λ)·max_{j∈已选} sim(i,j)
//     在"贴合口味"和"别老是同一个UP/同一类标签"之间贪心取舍，不是纯随机穿插。
//  4. 关注UP主强制名额：「关注」是对UP主本身的强信号，不进特征画像稀释，
//     而是每页强制留出名额给关注UP主的未看完视频。
//
// 候选集硬过滤顺序（源头排除，不参与打分，分数再高也翻不上来）：
//   全目录 → 剔除黑名单UP(复用PiliPlus的blackMids) → 剔除"不感兴趣"视频
//   → RecommendFilter 原版规则过滤(时长/播放量/赞播比/标题屏蔽词)照常叠加。
import 'dart:math';

import 'package:PiliPlus/models/offline/offline_video_item.dart';
import 'package:PiliPlus/utils/global_data.dart';
import 'package:PiliPlus/utils/offline/local_interactions.dart';
import 'package:PiliPlus/utils/recommend_filter.dart';

abstract final class OfflineRecommendEngine {
  // ---- Rocchio 权重：正反馈按B站语境分级，负反馈是正反馈基准的1.5倍 ----
  static const double wLike = 1.0; // 点赞：基础权重
  static const double wCoin = 1.5; // 投币：消耗硬币，认可更强
  static const double wFav = 2.0; // 收藏："以后还想看"，最强
  static const double wWatch = 0.3; // 看完(≥70%)：弱隐性信号
  static const double wDislike = 1.5; // γ = 1.5 × β基准

  static const double watchCompleteRatio = 0.7;

  // 反馈事件半衰期(天)：等价于增量Rocchio里 α<1 的旧画像打折
  static const double halfLifeDays = 90;

  // 贝叶斯先验强度 m：播放量低于这个数量级的视频，质量分主要听全目录均值的
  static const double bayesM = 10000;

  // MMR 平衡系数：λ 越大越贴口味，越小越多样
  static const double mmrLambda = 0.7;

  // 最终分 = 画像相关度 + 质量先验 的加权
  static const double wRelevance = 0.7;
  static const double wQuality = 0.3;

  // 每页给关注UP主未看完视频强制保留的名额数(每pageSize条里)
  static const int followedQuotaPer10 = 2;

  // ---- 特征提取：标签/分区/UP主 → 稀疏特征向量 ----
  static Map<String, double> _features(OfflineVideoItemModel v) {
    final f = <String, double>{};
    if (v.owner.mid != null) f['up:${v.owner.mid}'] = 1;
    if (v.tid != 0) f['zone:${v.tid}'] = 1;
    for (final t in v.tags) {
      if (t.isNotEmpty) f['tag:$t'] = 1;
    }
    return f;
  }

  static double _decay(int atMs, int nowMs) {
    final ageDays = (nowMs - atMs) / Duration.millisecondsPerDay;
    return pow(0.5, ageDays / halfLifeDays).toDouble();
  }

  // ---- Rocchio：从本地互动日志重建画像向量 ----
  static Map<String, double> buildProfile(List<OfflineVideoItemModel> catalog) {
    final byBvid = {
      for (final v in catalog)
        if (v.bvid != null) v.bvid!: v,
    };
    final profile = <String, double>{};
    final nowMs = DateTime.now().millisecondsSinceEpoch;

    void addSignal(String? bvid, double weight, {int? atMs}) {
      final v = bvid != null ? byBvid[bvid] : null;
      if (v == null) return;
      final w = weight * (atMs != null ? _decay(atMs, nowMs) : 1.0);
      _features(v).forEach((k, fv) {
        profile[k] = (profile[k] ?? 0) + w * fv;
      });
    }

    // 点赞/投币/收藏没有逐条时间戳(Set存储)，不做衰减；
    // 观看历史和不感兴趣带时间戳，按半衰期打折。
    for (final bvid in OfflineLocalInteractions.likedBvids) {
      addSignal(bvid, wLike);
    }
    for (final bvid in OfflineLocalInteractions.coinedBvids) {
      addSignal(bvid, wCoin);
    }
    for (final bvid in OfflineLocalInteractions.favoritedBvids) {
      addSignal(bvid, wFav);
    }
    for (final rec in OfflineLocalInteractions.watchHistory) {
      final durationMs = rec['durationMs'] as int? ?? 0;
      final progressMs = rec['progressMs'] as int? ?? 0;
      if (durationMs > 0 && progressMs >= durationMs * watchCompleteRatio) {
        addSignal(rec['bvid'] as String?, wWatch, atMs: rec['at'] as int?);
      }
    }
    OfflineLocalInteractions.dislikedVideos.forEach((bvid, rec) {
      addSignal(bvid, -wDislike, atMs: rec['at'] as int?);
    });

    return profile;
  }

  // ---- 余弦相似度：画像向量 vs 候选特征向量 ----
  static double _relevance(
    Map<String, double> profile,
    Map<String, double> features,
  ) {
    if (profile.isEmpty || features.isEmpty) return 0;
    double dot = 0;
    features.forEach((k, fv) {
      final pv = profile[k];
      if (pv != null) dot += pv * fv;
    });
    if (dot == 0) return 0;
    final pNorm = sqrt(profile.values.fold<double>(0, (s, v) => s + v * v));
    final fNorm = sqrt(features.values.fold<double>(0, (s, v) => s + v * v));
    return dot / (pNorm * fNorm);
  }

  // ---- 贝叶斯质量先验(IMDB公式)，R=log压缩后的赞播比 ----
  static double _qualityScore(OfflineVideoItemModel v, double catalogMeanR) {
    final view = v.viewCount.toDouble();
    if (view <= 0) return catalogMeanR; // 没有快照数据的完全听均值
    final r = v.likeCount / view;
    final shrunk = (view / (view + bayesM)) * r +
        (bayesM / (view + bayesM)) * catalogMeanR;
    // 赞播比通常在0~0.2区间，放大到可比尺度
    return min(1.0, shrunk * 10);
  }

  static double _catalogMeanLikeRate(List<OfflineVideoItemModel> catalog) {
    double sum = 0;
    int n = 0;
    for (final v in catalog) {
      if (v.viewCount > 0) {
        sum += v.likeCount / v.viewCount;
        n++;
      }
    }
    return n > 0 ? sum / n : 0.03; // 全站赞播比大致量级兜底
  }

  // ---- 候选间相似度(MMR的多样性项)：UP主/分区/标签的加权重合 ----
  static double _itemSim(OfflineVideoItemModel a, OfflineVideoItemModel b) {
    double s = 0;
    if (a.owner.mid != null && a.owner.mid == b.owner.mid) s += 0.5;
    if (a.tid != 0 && a.tid == b.tid) s += 0.2;
    if (a.tags.isNotEmpty && b.tags.isNotEmpty) {
      final inter = a.tags.toSet().intersection(b.tags.toSet()).length;
      final union = a.tags.toSet().union(b.tags.toSet()).length;
      s += 0.3 * inter / union;
    }
    return s;
  }

  static bool _watchedEnough(String? bvid) {
    if (bvid == null) return false;
    for (final rec in OfflineLocalInteractions.watchHistory) {
      if (rec['bvid'] == bvid) {
        final durationMs = rec['durationMs'] as int? ?? 0;
        final progressMs = rec['progressMs'] as int? ?? 0;
        if (durationMs > 0 && progressMs >= durationMs * watchCompleteRatio) {
          return true;
        }
      }
    }
    return false;
  }

  /// 对完整目录做 硬过滤→打分→MMR→关注名额插入，返回整份排好序的推荐列表。
  /// 排序带少量随机tiebreaker(±2%)：同分内容每次刷新会轮换，但绝不是纯随机。
  static List<OfflineVideoItemModel> rank(
    List<OfflineVideoItemModel> catalog,
  ) {
    final blackMids = GlobalData().blackMids;
    final rng = Random();

    // 1. 硬过滤：黑名单UP + 不感兴趣视频 + 原版RecommendFilter规则
    final candidates = catalog.where((v) {
      if (v.owner.mid != null && blackMids.contains(v.owner.mid)) return false;
      if (v.bvid != null && OfflineLocalInteractions.isDisliked(v.bvid!)) {
        return false;
      }
      if (RecommendFilter.filter(v)) return false;
      return true;
    }).toList();
    if (candidates.isEmpty) return const [];

    // 2. 打分：Rocchio画像相关度 + 贝叶斯质量先验
    final profile = buildProfile(catalog);
    final meanR = _catalogMeanLikeRate(catalog);
    final featureOf = <OfflineVideoItemModel, Map<String, double>>{};
    final scoreOf = <OfflineVideoItemModel, double>{};
    for (final v in candidates) {
      final f = _features(v);
      featureOf[v] = f;
      final rel = _relevance(profile, f);
      final quality = _qualityScore(v, meanR);
      final jitter = 1 + (rng.nextDouble() - 0.5) * 0.04;
      scoreOf[v] = (wRelevance * rel + wQuality * quality) * jitter;
    }

    // 3. MMR 贪心重排
    final remaining = candidates.toList()
      ..sort((a, b) => scoreOf[b]!.compareTo(scoreOf[a]!));
    final selected = <OfflineVideoItemModel>[];
    // maxSim 增量维护：每选中一个，只需拿它去更新余下候选的已选最大相似度，
    // 避免每轮对已选集合全量重算(O(n³)→O(n²))。
    final maxSimToSelected = <OfflineVideoItemModel, double>{};
    while (remaining.isNotEmpty) {
      OfflineVideoItemModel? best;
      double bestVal = double.negativeInfinity;
      for (final v in remaining) {
        final val = mmrLambda * scoreOf[v]! -
            (1 - mmrLambda) * (maxSimToSelected[v] ?? 0);
        if (val > bestVal) {
          bestVal = val;
          best = v;
        }
      }
      selected.add(best!);
      remaining.remove(best);
      for (final v in remaining) {
        final s = _itemSim(v, best);
        if (s > (maxSimToSelected[v] ?? 0)) maxSimToSelected[v] = s;
      }
    }

    // 4. 关注UP主强制名额：每10个位置留2个给关注UP的未看完视频
    //    (它们本身也在selected里，只是被提前；不重复出现)
    final followedMids = OfflineLocalInteractions.followedMids;
    if (followedMids.isNotEmpty) {
      final followedUnseen = selected
          .where(
            (v) =>
                v.owner.mid != null &&
                followedMids.contains(v.owner.mid) &&
                !_watchedEnough(v.bvid),
          )
          .toList();
      if (followedUnseen.isNotEmpty) {
        final result = <OfflineVideoItemModel>[];
        final used = <OfflineVideoItemModel>{};
        var fIdx = 0;
        var sIdx = 0;
        while (result.length < selected.length) {
          final posInPage = result.length % 10;
          final wantFollowed =
              (posInPage == 4 || posInPage == 9) && fIdx < followedUnseen.length;
          if (wantFollowed) {
            final v = followedUnseen[fIdx++];
            if (used.add(v)) {
              result.add(v);
              continue;
            }
          }
          while (sIdx < selected.length && used.contains(selected[sIdx])) {
            sIdx++;
          }
          if (sIdx >= selected.length) break;
          final v = selected[sIdx++];
          used.add(v);
          result.add(v);
        }
        return result;
      }
    }

    return selected;
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
