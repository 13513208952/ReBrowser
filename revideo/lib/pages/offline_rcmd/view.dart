// === OFFLINE-NOSTALGIA-MODE BEGIN ===
// "怀旧推荐"页面：网格布局/下拉刷新/无限滚动/卡片UI(VideoCardV含⋮弹出菜单)
// 与原版推荐tab(lib/pages/rcmd/view.dart)完全同一套，只是控制器换成
// OfflineRcmdController，去掉了原版"上次看到这里"的续看提示逻辑。
import 'package:PiliPlus/common/skeleton/video_card_v.dart';
import 'package:PiliPlus/common/style.dart';
import 'package:PiliPlus/common/widgets/flutter/refresh_indicator.dart';
import 'package:PiliPlus/common/widgets/loading_widget/http_error.dart';
import 'package:PiliPlus/common/widgets/video_card/video_card_v.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models/offline/offline_video_item.dart';
import 'package:PiliPlus/pages/offline_rcmd/controller.dart';
import 'package:PiliPlus/utils/grid.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class OfflineRcmdPage extends StatefulWidget {
  const OfflineRcmdPage({super.key});

  @override
  State<OfflineRcmdPage> createState() => _OfflineRcmdPageState();
}

class _OfflineRcmdPageState extends State<OfflineRcmdPage>
    with AutomaticKeepAliveClientMixin {
  final OfflineRcmdController controller = Get.put(OfflineRcmdController());

  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Container(
      clipBehavior: .hardEdge,
      margin: const .symmetric(horizontal: Style.safeSpace),
      decoration: const BoxDecoration(borderRadius: Style.mdRadius),
      child: refreshIndicator(
        onRefresh: controller.onRefresh,
        child: CustomScrollView(
          controller: controller.scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverPadding(
              padding: const .only(top: Style.cardSpace, bottom: 100),
              sliver: Obx(() => _buildBody(controller.loadingState.value)),
            ),
          ],
        ),
      ),
    );
  }

  late final gridDelegate = SliverGridDelegateWithExtentAndRatio(
    mainAxisSpacing: Style.cardSpace,
    crossAxisSpacing: Style.cardSpace,
    maxCrossAxisExtent: Pref.recommendCardWidth,
    childAspectRatio: Style.aspectRatio,
    mainAxisExtent: MediaQuery.textScalerOf(context).scale(90),
  );

  Widget _buildBody(LoadingState<List<OfflineVideoItemModel>?> loadingState) {
    return switch (loadingState) {
      Loading() => _buildSkeleton,
      Success(:final response) =>
        response != null && response.isNotEmpty
            ? SliverGrid.builder(
                gridDelegate: gridDelegate,
                itemBuilder: (context, index) {
                  if (index == response.length - 1) {
                    controller.onLoadMore();
                  }
                  return VideoCardV(
                    videoItem: response[index],
                    onRemove: () => controller.loadingState
                      ..value.data!.removeAt(index)
                      ..refresh(),
                  );
                },
                itemCount: response.length,
              )
            : HttpError(onReload: controller.onReload),
      Error(:final errMsg) => HttpError(
        errMsg: errMsg,
        onReload: controller.onReload,
      ),
    };
  }

  Widget get _buildSkeleton => SliverGrid.builder(
    gridDelegate: gridDelegate,
    itemBuilder: (context, index) => const VideoCardVSkeleton(),
    itemCount: 10,
  );
}
// === OFFLINE-NOSTALGIA-MODE END ===
