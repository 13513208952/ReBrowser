// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// "怀旧推荐"信息流控制器：继承和原版推荐tab同一个 CommonListController 基类，
// 只替换拉数据逻辑——下拉刷新时拉全量目录、本地跑一遍推荐引擎排序，
// 之后的无限滚动只是在排好的整份列表上翻页切片，不再重复请求服务端。
import 'dart:math';

import 'package:PiliPlus/http/catalog_offline.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:flutter/foundation.dart' show kDebugMode, debugPrint;
import 'package:PiliPlus/models/offline/offline_video_item.dart';
import 'package:PiliPlus/pages/common/common_list_controller.dart';
import 'package:PiliPlus/utils/offline/recommend_engine.dart';

class OfflineRcmdController
    extends
        CommonListController<List<OfflineVideoItemModel>?, OfflineVideoItemModel> {
  static const int pageSize = 20;

  List<OfflineVideoItemModel> _ranked = const [];
  bool _forceRefreshCatalog = false;

  @override
  void onInit() {
    super.onInit();
    queryData();
  }

  @override
  Future<LoadingState<List<OfflineVideoItemModel>?>> customGetData() async {
    if (page == 1) {
      final res = await OfflineCatalogHttp.fullCatalog(
        forceRefresh: _forceRefreshCatalog,
      );
      _forceRefreshCatalog = false;
      if (res case Success(:final response)) {
        // 画像/排序全在本地：刷新时重算一次，反馈(点赞/点踩/不感兴趣)
        // 的效果在下一次刷新立即体现。
        // 兜底：引擎出任何异常(比如本地存储数据形状损坏)都不能杀死feed，
        // 降级为原始目录顺序照常展示——真机上曾因Hive类型问题炸过这里。
        try {
          _ranked = OfflineRecommendEngine.rank(response);
        } catch (e) {
          if (kDebugMode) debugPrint('recommend engine failed: $e');
          _ranked = response;
        }
      } else {
        return Error(res is Error ? res.errMsg : '单机怀旧模式：目录加载失败');
      }
    }
    final start = (page - 1) * pageSize;
    if (start >= _ranked.length) {
      return const Success([]);
    }
    return Success(_ranked.sublist(start, min(start + pageSize, _ranked.length)));
  }

  @override
  Future<void> onRefresh() {
    _forceRefreshCatalog = true;
    return super.onRefresh();
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
