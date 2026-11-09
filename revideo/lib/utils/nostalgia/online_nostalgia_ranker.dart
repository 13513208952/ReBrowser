import 'dart:math';

import 'package:PiliPlus/models/online_nostalgia/online_nostalgia_video.dart';

/// 在线怀旧的轻量排序器。候选获取与B站业务完全分离：
/// SQLite负责探索/复用配额，本类只做质量与多样性重排。
abstract final class OnlineNostalgiaRanker {
  static const _lambda = .72;
  static const _bayesM = 10000.0;

  static List<OnlineNostalgiaVideo> rank(
    List<OnlineNostalgiaVideo> input,
    Map<String, double> profile,
  ) {
    if (input.length < 2) return input;
    final rng = Random();
    var rateSum = 0.0;
    var rateCount = 0;
    for (final v in input) {
      if (v.viewCount > 0) {
        rateSum += v.likeCount / v.viewCount;
        rateCount++;
      }
    }
    final mean = rateCount == 0 ? .03 : rateSum / rateCount;
    final scores = <OnlineNostalgiaVideo, double>{};
    for (final v in input) {
      final views = v.viewCount.toDouble();
      final rate = views <= 0 ? mean : v.likeCount / views;
      final quality = views <= 0
          ? mean
          : (views / (views + _bayesM)) * rate +
                (_bayesM / (views + _bayesM)) * mean;
      // 复用时优先很久没出现且出现次数少的内容。
      final freshness = v.lastRecommendedAt == 0
          ? 1.0
          : min(
              1.0,
              DateTime.now()
                      .difference(
                        DateTime.fromMillisecondsSinceEpoch(
                          v.lastRecommendedAt,
                        ),
                      )
                      .inDays /
                  30,
            );
      final frequencyPenalty = 1 / (1 + v.recommendCount * .2);
      final relevance = _relevance(v, profile);
      scores[v] =
          relevance * .55 +
          quality * 10 * .25 +
          freshness * .15 +
          frequencyPenalty * .05 +
          rng.nextDouble() * .015;
    }

    final remaining = input.toList();
    final selected = <OnlineNostalgiaVideo>[];
    final maxSimilarity = <OnlineNostalgiaVideo, double>{};
    while (remaining.isNotEmpty) {
      OnlineNostalgiaVideo? best;
      var bestScore = double.negativeInfinity;
      for (final item in remaining) {
        final score =
            _lambda * scores[item]! -
            (1 - _lambda) * (maxSimilarity[item] ?? 0);
        if (score > bestScore) {
          bestScore = score;
          best = item;
        }
      }
      selected.add(best!);
      remaining.remove(best);
      for (final item in remaining) {
        maxSimilarity[item] = max(
          maxSimilarity[item] ?? 0,
          _similarity(item, best),
        );
      }
    }
    return selected;
  }

  static double _similarity(
    OnlineNostalgiaVideo a,
    OnlineNostalgiaVideo b,
  ) {
    var similarity = 0.0;
    if (a.owner.mid != null && a.owner.mid == b.owner.mid) similarity += .5;
    if (a.tid != 0 && a.tid == b.tid) similarity += .2;
    if (a.tags.isNotEmpty && b.tags.isNotEmpty) {
      final aTags = a.tags.toSet();
      final bTags = b.tags.toSet();
      similarity +=
          .3 * aTags.intersection(bTags).length / aTags.union(bTags).length;
    }
    return similarity;
  }

  static double _relevance(
    OnlineNostalgiaVideo video,
    Map<String, double> profile,
  ) {
    if (profile.isEmpty) return 0;
    final features = <String>{
      if (video.owner.mid case final mid?) 'up:$mid',
      if (video.tid != 0) 'zone:${video.tid}',
      for (final tag in video.tags) 'tag:$tag',
    };
    if (features.isEmpty) return 0;
    var dot = 0.0;
    for (final feature in features) {
      dot += profile[feature] ?? 0;
    }
    if (dot == 0) return 0;
    final profileNorm = sqrt(
      profile.values.fold<double>(0, (sum, v) => sum + v * v),
    );
    return dot / (profileNorm * sqrt(features.length));
  }
}
