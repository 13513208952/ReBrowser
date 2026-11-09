// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// 单机怀旧模式：从自建服务端分页拉取完整目录。
// 推荐画像/排序全部在客户端本地算(服务端只吐资源不参与)，所以引擎需要
// 完整候选集：这里按页循环拉全量并在内存缓一份，短TTL过期后重拉。
// 私有归档的量级(几百~几千条)对1~3个使用者完全扛得住。
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models/offline/offline_video_item.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';

abstract final class OfflineCatalogHttp {
  static List<OfflineVideoItemModel>? _cache;
  static DateTime? _cacheAt;
  static const _cacheTtl = Duration(minutes: 5);

  static void invalidate() {
    _cache = null;
    _cacheAt = null;
  }

  static Future<LoadingState<List<OfflineVideoItemModel>>> fullCatalog({
    bool forceRefresh = false,
  }) async {
    if (!forceRefresh &&
        _cache != null &&
        _cacheAt != null &&
        DateTime.now().difference(_cacheAt!) < _cacheTtl) {
      return Success(_cache!);
    }

    try {
      await OfflineConfig.ensureResolved();
      const pageSize = 200;
      final all = <OfflineVideoItemModel>[];
      for (var page = 1; ; page++) {
        final res = await Request().get(
          OfflineConfig.apiUri('/api/v1/videos', {
            'page': '$page',
            'pageSize': '$pageSize',
          }).toString(),
        );
        if (res.data is! List) {
          return const Error('单机怀旧模式：服务端目录返回格式异常');
        }
        final items = (res.data as List)
            .map((e) => OfflineVideoItemModel.fromJson(e))
            .toList();
        all.addAll(items);
        if (items.length < pageSize) break;
      }
      _cache = all;
      _cacheAt = DateTime.now();
      return Success(all);
    } catch (e) {
      OfflineConfig.invalidate(); // 下次请求前重新探测USB/主/备地址
      return Error('单机怀旧模式连接失败: $e');
    }
  }
}
// === OFFLINE-NOSTALGIA-MODE END ===
