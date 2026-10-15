# 文档目录

文档按用户说明、开发参考和历史资料分开。主 [README](../README.md) 面向用户；构建和实现细节从本页进入。

## 用户说明

| 文档 | 内容 |
| --- | --- |
| [ReBrowser 使用指南](user/REBROWSER.md) | 搜索、独立浏览环境、收藏／书签、历史、设置、下载和兼容性 |
| [ReOutlook 邮件指南](user/REOUTLOOK.md) | 内置邮件模式的进入、登录、离线阅读与数据保留 |
| [隐私说明](../PRIVACY.md) | 本地数据、网络、备份和删除 |
| [安全策略](../SECURITY.md) | 对外安全边界与漏洞报告 |

## 开发参考

| 文档 | 内容 |
| --- | --- |
| [开发入口](DEVELOPMENT.md) | 构建、安装身份、测试、发布流程与参考索引 |
| [项目状态](PROJECT_STATUS.md) | 本轮通过／待验证项、已知限制；历史记录按版本区分 |
| [设置与应用管理](REBROWSER_SETTINGS.md) | 三页设置、子软件入口、迁移、风味默认与原生回退 |
| [浏览器设计与沿革](REBROWSER_EXPLORATION.md) | 双层标签、Profile、状态与交互背景 |
| [邮件架构与同步](REOUTLOOK_ARCHITECTURE.md) | 桥、缓存、账号、回填、检查点与迁移 |
| [音乐子应用完整设想原文](MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md) | 机主整段原话，完整逐字保存，不作整理或改写；压缩交接必读 |
| [音乐登录前置／UA 硬性要求](NETEASE_MUSIC_LOGIN_REQUIREMENTS.md) | 压缩交接必读；未登录先原手机 UA 登录，仅平板完整窗口横屏可主动切电脑扫码；已恢复全面开发，源码门控已接入，真实 Android 登录待验收 |
| [音乐 Android 开发](NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)、[非横屏布局](NETEASE_MUSIC_PORTRAIT_LAYOUT.md) | 首轮源码与已安装预览、已确认布局及未验收范围 |
| [管理协议](REBROWSER_ADMIN_CONTROL.md)、[管理员 CLI](../tools/admin/README.md) | 挑战、签名、三级授权、固定命令与数据边界 |
| [网站权限](REBROWSER_SITE_PERMISSIONS.md) | 单站授权、后台策略及永久禁止能力 |
| [剪贴板手势修复](CLIPBOARD_GESTURE_SECURITY.md) | v1.0.2 原生输入边界、确认问题与测试限制 |
| [旧宿主缩放移除记录](REBROWSER_LAYOUT_ZOOM.md) | 已否决试验及协议／CLI 从源码移除；正常浏览器缩放与网易布局适配保留 |
| [Android 交接](REBROWSER_ANDROID_HANDOFF.md) | 浏览器注册、默认应用、外部链接和 `.1` APK 交接 |
| [历史记录](REBROWSER_HISTORY_PLAN.md) | 生命周期保留、维持／暂停及验证范围 |
| [YouTube 兼容](YOUTUBE_PLAYER_COMPATIBILITY.md) | 窄兼容策略与未覆盖场景 |
| [回归检查](../tools/tests/README.md) | JVM、Node、CLI、打包测试 |
| [Release 构建工具](../tools/build/README.md) | R8、无损 ZIP、对齐、签名与体积统计 |
| [资源来源](ASSET_CREDITS.md) | 照片与搜索品牌；来源不等于任意品牌使用授权 |

## 当前正式版

[v1.0.3-beta 测试版记录](releases/v1.0.3-beta.md)：三种套件包、官方网易云音乐、有限观察与延期至下一 release 的验收；最新稳定版仍为 v1.0.2。

[v1.0.2 发布记录](releases/v1.0.2.md)：剪贴板原生手势修复，纳入设备与窗口方向功能；升级、签名包体积、验证范围与限制。

## 历史发布与讨论

- [v1.0.1-beta](releases/v1.0.1-beta.md)：设备与窗口方向测试版，已由正式版纳入；原包仍含剪贴板手势问题。
- [v1.0.0](releases/v1.0.0.md)：首个正式版的实现与当时验证。

- [开发历史与验证资料](DEVELOPMENT_HISTORY.md)：版本演进和历史测试，不代表本轮重新执行。
- [新标签页与个性化讨论](REBROWSER_NEW_TAB_PLAN.md)：保留讨论背景，其中个性化页等设计已被后续需求替换。
- [v0.4.5-alpha](releases/v0.4.5-alpha.md)：Android 浏览器／安装器交接和无损打包。
- [v0.4.4-alpha](releases/v0.4.4-alpha.md)：原生新标签页、双风味与当时的三页设置。
- [v0.4.3-alpha](releases/v0.4.3-alpha.md)：历史记录与邮件入口显隐。
- [v0.4.2-alpha](releases/v0.4.2-alpha.md)：加载进度条。
- [v0.4.1-alpha](releases/v0.4.1-alpha.md)：Popup 返回与 YouTube 修正。

历史发布、设计讨论和未来候选方向不是现行功能承诺，也不是不可更改的要求。当前默认值和界面以当前使用指南、设置说明、源码与对应发布记录为准。

## 维护资料

本维护机器临时文件统一使用 `/home/zhao/temp/re_outlook/`，不使用内存盘 `/tmp`。历史设备截图、日志、私钥、原始照片及 R8 报告保留在仓库外，不作为公开文档资产上传。
