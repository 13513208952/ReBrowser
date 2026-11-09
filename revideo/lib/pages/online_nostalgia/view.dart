import 'package:PiliPlus/common/skeleton/video_card_v.dart';
import 'package:PiliPlus/common/style.dart';
import 'package:PiliPlus/common/widgets/flutter/refresh_indicator.dart';
import 'package:PiliPlus/common/widgets/loading_widget/http_error.dart';
import 'package:PiliPlus/common/widgets/video_card/video_card_v.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models/online_nostalgia/online_nostalgia_video.dart';
import 'package:PiliPlus/pages/online_nostalgia/controller.dart';
import 'package:PiliPlus/utils/grid.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class OnlineNostalgiaPage extends StatefulWidget {
  const OnlineNostalgiaPage({super.key});

  @override
  State<OnlineNostalgiaPage> createState() => _OnlineNostalgiaPageState();
}

class _OnlineNostalgiaPageState extends State<OnlineNostalgiaPage>
    with AutomaticKeepAliveClientMixin {
  final controller = Get.put(OnlineNostalgiaController());

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
              sliver: Obx(() => _body(controller.loadingState.value)),
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

  Widget _body(LoadingState<List<OnlineNostalgiaVideo>?> state) =>
      switch (state) {
        Loading() => SliverGrid.builder(
          gridDelegate: gridDelegate,
          itemBuilder: (_, _) => const VideoCardVSkeleton(),
          itemCount: 10,
        ),
        Success(:final response) when response?.isNotEmpty == true =>
          SliverGrid.builder(
            gridDelegate: gridDelegate,
            itemCount: response!.length,
            itemBuilder: (_, index) {
              if (index == response.length - 1) controller.onLoadMore();
              return VideoCardV(videoItem: response[index]);
            },
          ),
        Success() => HttpError(onReload: controller.onReload),
        Error(:final errMsg) => HttpError(
          errMsg: errMsg,
          onReload: controller.onReload,
        ),
      };
}
