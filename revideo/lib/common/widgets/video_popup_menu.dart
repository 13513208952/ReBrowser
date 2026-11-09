import 'package:PiliPlus/common/widgets/custom_icon.dart';
import 'package:PiliPlus/http/user.dart';
import 'package:PiliPlus/http/video.dart';
import 'package:PiliPlus/models/home/rcmd/result.dart';
import 'package:PiliPlus/models/model_video.dart';
import 'package:PiliPlus/models_new/space/space_archive/item.dart';
import 'package:PiliPlus/pages/mine/controller.dart';
import 'package:PiliPlus/pages/search/widgets/search_text.dart';
import 'package:PiliPlus/pages/video/ai_conclusion/view.dart';
import 'package:PiliPlus/pages/video/introduction/ugc/controller.dart';
import 'package:PiliPlus/utils/accounts.dart';
// === OFFLINE-NOSTALGIA-MODE BEGIN ===
import 'package:PiliPlus/utils/offline/local_interactions.dart';
import 'package:PiliPlus/utils/offline/offline_config.dart';
// === OFFLINE-NOSTALGIA-MODE END ===
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:PiliPlus/utils/utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:material_design_icons_flutter/material_design_icons_flutter.dart';

class _VideoCustomAction {
  final String title;
  final Widget icon;
  final VoidCallback onTap;
  const _VideoCustomAction(this.title, this.icon, this.onTap);
}

class VideoPopupMenu extends StatelessWidget {
  final double? iconSize;
  final double menuItemHeight;
  final BaseSimpleVideoItemModel videoItem;
  final VoidCallback? onRemove;

  const VideoPopupMenu({
    super.key,
    required this.iconSize,
    required this.videoItem,
    this.onRemove,
    this.menuItemHeight = 45,
  });

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton(
      padding: EdgeInsets.zero,
      icon: Icon(
        Icons.more_vert_outlined,
        color: Theme.of(context).colorScheme.outline,
        size: iconSize,
      ),
      position: PopupMenuPosition.under,
      itemBuilder: (context) =>
          [
                if (videoItem.bvid?.isNotEmpty == true) ...[
                  _VideoCustomAction(
                    videoItem.bvid!,
                    const Icon(CustomIcons.identifier_circle, size: 16),
                    () => Utils.copyText(videoItem.bvid!),
                  ),
                  // === OFFLINE-NOSTALGIA-MODE BEGIN ===
                  // "稍后再看"是B站云端账号能力，怀旧模式下隐藏入口
                  if (!OfflineConfig.enabled && Accounts.main.isLogin)
                    // === OFFLINE-NOSTALGIA-MODE END ===
                    _VideoCustomAction(
                      '稍后再看',
                      const Icon(MdiIcons.clockTimeEightOutline, size: 16),
                      () => UserHttp.toViewLater(bvid: videoItem.bvid),
                    ),
                  if (videoItem.cid != null && Pref.enableAi)
                    _VideoCustomAction(
                      'AI总结',
                      const Icon(CustomIcons.ai_circle, size: 16),
                      () async {
                        final res = await UgcIntroController.getAiConclusion(
                          videoItem.bvid!,
                          videoItem.cid!,
                          videoItem.owner.mid,
                        );
                        if (res != null && context.mounted) {
                          showDialog(
                            context: context,
                            builder: (context) => Dialog(
                              child: Padding(
                                padding: const .symmetric(vertical: 14),
                                child: AiConclusionPanel.buildContent(
                                  context,
                                  Theme.of(context),
                                  res,
                                  tap: false,
                                ),
                              ),
                            ),
                          );
                        }
                      },
                    ),
                ],
                if (videoItem is! SpaceArchiveItem) ...[
                  _VideoCustomAction(
                    '访问：${videoItem.owner.name}',
                    const Icon(MdiIcons.accountCircleOutline, size: 16),
                    () => Get.toNamed('/member?mid=${videoItem.owner.mid}'),
                  ),
                  _VideoCustomAction(
                    '不感兴趣',
                    const Icon(MdiIcons.thumbDownOutline, size: 16),
                    () {
                      // === OFFLINE-NOSTALGIA-MODE BEGIN ===
                      // 怀旧模式：原因分类弹窗+提交后立即从列表移除的交互原样保留，
                      // 只是提交目标从B站live API换成本地"不感兴趣"记录——
                      // 该视频被硬性排除出本地推荐候选，除非手动移除记录。
                      if (OfflineConfig.enabled) {
                        final bvid = videoItem.bvid;
                        if (bvid == null || bvid.isEmpty) {
                          SmartDialog.showToast('缺少视频ID');
                          return;
                        }
                        showDialog(
                          context: context,
                          builder: (context) => SimpleDialog(
                            contentPadding: const .fromLTRB(24, 16, 24, 24),
                            children: [
                              const Text('我不想看(仅本机生效)'),
                              const SizedBox(height: 5),
                              Wrap(
                                spacing: 8.0,
                                runSpacing: 8.0,
                                children:
                                    [
                                          '不喜欢这个UP主',
                                          '不喜欢此类内容',
                                          '内容质量差',
                                          '就是不想看这个视频',
                                        ]
                                        .map(
                                          (reason) => SearchText(
                                            text: reason,
                                            onTap: (_) {
                                              Get.back();
                                              OfflineLocalInteractions.addDislike(
                                                bvid: bvid,
                                                reason: reason,
                                              );
                                              SmartDialog.showToast(
                                                '已排除出怀旧推荐(仅本机)',
                                              );
                                              onRemove?.call();
                                            },
                                          ),
                                        )
                                        .toList(),
                              ),
                              const Divider(),
                              Center(
                                child: FilledButton.tonal(
                                  onPressed: () {
                                    OfflineLocalInteractions.removeDislike(
                                      bvid,
                                    );
                                    SmartDialog.showToast('已撤销');
                                    Get.back();
                                  },
                                  style: FilledButton.styleFrom(
                                    visualDensity: VisualDensity.compact,
                                  ),
                                  child: const Text('撤销'),
                                ),
                              ),
                            ],
                          ),
                        );
                        return;
                      }
                      // === OFFLINE-NOSTALGIA-MODE END ===
                      final rcmd = Accounts.get(.recommend);
                      if (rcmd.accessKey == null || rcmd.accessKey == "") {
                        SmartDialog.showToast(
                          rcmd.isLogin ? '请退出账号后重新登录' : '账号未登录',
                        );
                        return;
                      }
                      if (videoItem case final RcmdVideoItemAppModel item) {
                        ThreePoint? tp = item.threePoint;
                        if (tp == null) {
                          SmartDialog.showToast("未能获取threePoint");
                          return;
                        }
                        if (tp.dislikeReasons == null && tp.feedbacks == null) {
                          SmartDialog.showToast(
                            "未能获取dislikeReasons或feedbacks",
                          );
                          return;
                        }
                        Widget actionButton(Reason? r, Reason? f) {
                          return SearchText(
                            text: r?.name ?? f?.name ?? '未知',
                            onTap: (_) async {
                              Get.back();
                              SmartDialog.showLoading(msg: '正在提交');
                              final res = await VideoHttp.feedDislike(
                                reasonId: r?.id,
                                feedbackId: f?.id,
                                id: item.param!,
                                goto: item.goto!,
                              );
                              SmartDialog.dismiss();
                              if (res.isSuccess) {
                                SmartDialog.showToast(
                                  r?.toast ?? f!.toast!,
                                );
                                onRemove?.call();
                              } else {
                                res.toast();
                              }
                            },
                          );
                        }

                        showDialog(
                          context: context,
                          builder: (context) {
                            return SimpleDialog(
                              contentPadding: const .fromLTRB(24, 16, 24, 24),
                              children: [
                                if (tp.dislikeReasons != null) ...[
                                  const Text('我不想看'),
                                  const SizedBox(height: 5),
                                  Wrap(
                                    spacing: 8.0,
                                    runSpacing: 8.0,
                                    children: tp.dislikeReasons!
                                        .map((item) => actionButton(item, null))
                                        .toList(),
                                  ),
                                ],
                                if (tp.feedbacks != null) ...[
                                  const SizedBox(height: 5),
                                  const Text('反馈'),
                                  const SizedBox(height: 5),
                                  Wrap(
                                    spacing: 8.0,
                                    runSpacing: 8.0,
                                    children: tp.feedbacks!
                                        .map((item) => actionButton(null, item))
                                        .toList(),
                                  ),
                                ],
                                const Divider(),
                                Center(
                                  child: FilledButton.tonal(
                                    onPressed: () async {
                                      SmartDialog.showLoading(
                                        msg: '正在提交',
                                      );
                                      final res =
                                          await VideoHttp.feedDislikeCancel(
                                            id: item.param!,
                                            goto: item.goto!,
                                          );
                                      SmartDialog.dismiss();
                                      SmartDialog.showToast(
                                        res.isSuccess ? "成功" : res.toString(),
                                      );
                                      Get.back();
                                    },
                                    style: FilledButton.styleFrom(
                                      visualDensity: VisualDensity.compact,
                                    ),
                                    child: const Text("撤销"),
                                  ),
                                ),
                              ],
                            );
                          },
                        );
                      } else {
                        showDialog(
                          context: context,
                          builder: (context) => SimpleDialog(
                            contentPadding: const .all(24),
                            children: [
                              const Center(child: Text("web端暂不支持精细选择")),
                              const SizedBox(height: 5),
                              Wrap(
                                spacing: 5.0,
                                runSpacing: 2.0,
                                alignment: .center,
                                children: [
                                  FilledButton.tonal(
                                    onPressed: () async {
                                      Get.back();
                                      SmartDialog.showLoading(msg: '正在提交');
                                      final res = await VideoHttp.dislikeVideo(
                                        bvid: videoItem.bvid!,
                                        type: true,
                                      );
                                      SmartDialog.dismiss();
                                      if (res.isSuccess) {
                                        SmartDialog.showToast('点踩成功');
                                        onRemove?.call();
                                      } else {
                                        res.toast();
                                      }
                                    },
                                    style: FilledButton.styleFrom(
                                      visualDensity: .compact,
                                    ),
                                    child: const Text("点踩"),
                                  ),
                                  FilledButton.tonal(
                                    onPressed: () async {
                                      Get.back();
                                      SmartDialog.showLoading(msg: '正在提交');
                                      final res = await VideoHttp.dislikeVideo(
                                        bvid: videoItem.bvid!,
                                        type: false,
                                      );
                                      SmartDialog.dismiss();
                                      SmartDialog.showToast(
                                        res.isSuccess ? '取消踩' : res.toString(),
                                      );
                                    },
                                    style: FilledButton.styleFrom(
                                      visualDensity: .compact,
                                    ),
                                    child: const Text("撤销"),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        );
                      }
                    },
                  ),
                  _VideoCustomAction(
                    '拉黑：${videoItem.owner.name}',
                    const Icon(MdiIcons.cancel, size: 16),
                    () => showDialog(
                      context: context,
                      builder: (context) {
                        return AlertDialog(
                          title: const Text('提示'),
                          content: Text(
                            '确定拉黑:${videoItem.owner.name}(${videoItem.owner.mid})?'
                            '\n\n注：被拉黑的Up可以在隐私设置-黑名单管理中解除',
                          ),
                          actions: [
                            TextButton(
                              onPressed: Get.back,
                              child: Text(
                                '点错了',
                                style: TextStyle(
                                  color: ColorScheme.of(context).outline,
                                ),
                              ),
                            ),
                            TextButton(
                              onPressed: () async {
                                Get.back();
                                // === OFFLINE-NOSTALGIA-MODE BEGIN ===
                                // 怀旧模式：拉黑直接写本地黑名单(blackMids本来就是
                                // 客户端本地过滤机制)，不发B站关系接口。
                                if (OfflineConfig.enabled) {
                                  // setBlackMid 会同时更新 GlobalData 内存镜像并落盘
                                  Pref.setBlackMid(videoItem.owner.mid!);
                                  SmartDialog.showToast('已拉黑(仅本机)');
                                  onRemove?.call();
                                  return;
                                }
                                // === OFFLINE-NOSTALGIA-MODE END ===
                                final res = await VideoHttp.relationMod(
                                  mid: videoItem.owner.mid!,
                                  act: 5,
                                  reSrc: 11,
                                );
                                if (res.isSuccess) {
                                  onRemove?.call();
                                } else {
                                  res.toast();
                                }
                              },
                              child: const Text('确认'),
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                ],
                _VideoCustomAction(
                  "${MineController.anonymity.value ? '退出' : '进入'}无痕模式",
                  MineController.anonymity.value
                      ? const Icon(MdiIcons.incognitoOff, size: 16)
                      : const Icon(MdiIcons.incognito, size: 16),
                  MineController.onChangeAnonymity,
                ),
              ]
              .map(
                (e) => PopupMenuItem(
                  height: menuItemHeight,
                  onTap: e.onTap,
                  child: Row(
                    children: [
                      e.icon,
                      const SizedBox(width: 6),
                      Text(e.title, style: const TextStyle(fontSize: 13)),
                    ],
                  ),
                ),
              )
              .toList(),
    );
  }
}
