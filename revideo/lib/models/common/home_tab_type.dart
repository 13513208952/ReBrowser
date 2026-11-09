import 'package:PiliPlus/models/common/enum_with_label.dart';
import 'package:PiliPlus/pages/common/common_controller.dart';
import 'package:PiliPlus/pages/hot/controller.dart';
import 'package:PiliPlus/pages/hot/view.dart';
import 'package:PiliPlus/pages/live/controller.dart';
import 'package:PiliPlus/pages/live/view.dart';
import 'package:PiliPlus/pages/pgc/controller.dart';
import 'package:PiliPlus/pages/pgc/view.dart';
import 'package:PiliPlus/pages/rank/controller.dart';
import 'package:PiliPlus/pages/rank/view.dart';
// === OFFLINE-NOSTALGIA-MODE BEGIN ===
import 'package:PiliPlus/pages/offline_rcmd/controller.dart';
import 'package:PiliPlus/pages/offline_rcmd/view.dart';
// === OFFLINE-NOSTALGIA-MODE END ===
import 'package:PiliPlus/pages/online_nostalgia/controller.dart';
import 'package:PiliPlus/pages/online_nostalgia/view.dart';
import 'package:PiliPlus/pages/rcmd/controller.dart';
import 'package:PiliPlus/pages/rcmd/view.dart';
import 'package:PiliPlus/utils/nostalgia/nostalgia_config.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

enum HomeTabType implements EnumWithLabel {
  live('直播'),
  rcmd('推荐'),
  hot('热门'),
  rank('分区'),
  bangumi('番剧'),
  cinema('影视'),
  // === OFFLINE-NOSTALGIA-MODE BEGIN ===
  // 必须追加在末尾：tabBarSort 按枚举下标持久化，插中间会错位已有配置
  offlineRcmd('怀旧推荐'),
  // === OFFLINE-NOSTALGIA-MODE END ===
  ;

  @override
  final String label;
  const HomeTabType(this.label);

  ScrollOrRefreshMixin Function() get ctr => switch (this) {
    HomeTabType.live => Get.find<LiveController>,
    HomeTabType.rcmd => Get.find<RcmdController>,
    HomeTabType.hot => Get.find<HotController>,
    HomeTabType.rank => Get.find<RankController>,
    HomeTabType.bangumi ||
    HomeTabType.cinema => () => Get.find<PgcController>(tag: name),
    // === OFFLINE-NOSTALGIA-MODE BEGIN ===
    HomeTabType.offlineRcmd =>
      NostalgiaConfig.online
          ? Get.find<OnlineNostalgiaController>
          : Get.find<OfflineRcmdController>,
    // === OFFLINE-NOSTALGIA-MODE END ===
  };

  Widget get page => switch (this) {
    HomeTabType.live => const LivePage(),
    HomeTabType.rcmd => const RcmdPage(),
    HomeTabType.hot => const HotPage(),
    HomeTabType.rank => const RankPage(),
    HomeTabType.bangumi => const PgcPage(tabType: HomeTabType.bangumi),
    HomeTabType.cinema => const PgcPage(tabType: HomeTabType.cinema),
    // === OFFLINE-NOSTALGIA-MODE BEGIN ===
    HomeTabType.offlineRcmd =>
      NostalgiaConfig.online
          ? const OnlineNostalgiaPage()
          : const OfflineRcmdPage(),
    // === OFFLINE-NOSTALGIA-MODE END ===
  };
}
