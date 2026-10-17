# 文档目录

文档按用户说明、开发参考和历史资料分开。主 [README](../README.md) 面向用户；构建和实现细节从本页进入。

## 用户说明

| 文档 | 内容 |
| --- | --- |
| [ReBrowser 使用指南](user/REBROWSER.md) | 搜索、独立浏览环境、收藏／书签、历史、设置、下载和兼容性 |
| [ReOutlook 邮件指南](user/REOUTLOOK.md) | 全量包邮件进入、登录、离线阅读与数据保留 |
| [ReMusic 音乐指南](user/MUSIC.md) | 全量包启用/登录/系统控制、独立任务停止、数据保留及问题反馈 |
| [隐私说明](../PRIVACY.md) | 本地数据、网络、备份和删除 |
| [安全策略](../SECURITY.md) | 对外安全边界与漏洞报告 |

## 开发参考

| 文档 | 内容 |
| --- | --- |
| [项目定位：重新定义浏览器](PROJECT_VISION.md) | Re的含义、应用环境方向、已实现实践与不能外推的技术边界 |
| [实际界面与隐私审核](SCREENSHOT_REVIEW.md) | 私有原图／裁剪与隐私替换对照、机主先审查、批准后才公开；不改变应用或账号状态 |
| [开发入口](DEVELOPMENT.md) | 构建、安装身份、测试、发布流程与参考索引 |
| [项目状态](PROJECT_STATUS.md) | 当前版本、实现与有限证据、已知限制；不再混入完整历史报告 |
| [仓库维护与清理](MAINTENANCE.md)、[离线维护工具](../tools/maintenance/README.md) | 文件归属、不可变/私有保留、链接检查、生成物 dry-run 清理 |
| [设置与应用管理](REBROWSER_SETTINGS.md) | 三页设置、子软件入口、迁移、风味默认与原生回退 |
| [子应用独立设置（开发中）](SUBAPPLICATION_SETTINGS.md) | 齿轮二级页、音乐源迁移、存储口径与隔离清理、管理员只读扩展及R8审查；公开v1.0.4尚未包含 |
| [浏览器设计与沿革](REBROWSER_EXPLORATION.md) | 双层标签、Profile、状态与交互背景 |
| [邮件架构与同步](REOUTLOOK_ARCHITECTURE.md) | 桥、缓存、账号、回填、检查点与迁移 |
| [音乐子应用完整设想原文](MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md) | 机主整段原话，完整逐字保存，不作整理或改写；压缩交接必读 |
| [音乐登录前置／UA 硬性要求](NETEASE_MUSIC_LOGIN_REQUIREMENTS.md) | 未登录先手机UA；只有平板完整窗口横屏可主动切扫码；自动认证，有限平板证据不等于完整登录矩阵 |
| [音乐 Android 开发](NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)、[非横屏布局](NETEASE_MUSIC_PORTRAIT_LAYOUT.md) | 当前正式架构/内存修复、历史Beta观察、不可变布局与限定验收 |
| [音乐启动优化](MUSIC_STARTUP_OPTIMIZATION.md) | 日常录屏口径、轻量官方认证、等待消除、停止/重开交接与限定证据 |
| [音乐控件外观修复](MUSIC_CONTROL_UI_FIXES.md) | Android 16 自定义动作心形与通用播放按钮尺寸，旧版保留及真实验证边界 |
| [完整播放页与歌词](MUSIC_EXPANDED_PLAYER_AND_LYRICS.md) | 竖屏展开页／心形及自身 Profile 取数的单机观察；顶部／正常滚动的机主限定确认 |
| [桌面歌词三态与系统进度修正](MUSIC_DESKTOP_LYRICS.md) | 同一媒体按钮、拖动边界／记忆、锁定透明且无说明文字；真实引擎等待分支、原元素有界继续及有限手机复验 |
| [v1.0.3 音乐验收记录](NETEASE_MUSIC_RELEASE_ACCEPTANCE.md) | 修复与限定证据、已完成/取消/后期分开；未实测不算通过 |
| [管理协议](REBROWSER_ADMIN_CONTROL.md)、[管理员 CLI](../tools/admin/README.md) | 挑战、签名、三级授权、固定命令与数据边界 |
| [网站权限](REBROWSER_SITE_PERMISSIONS.md) | 单站授权、后台策略及永久禁止能力 |
| [剪贴板手势修复](CLIPBOARD_GESTURE_SECURITY.md) | v1.0.2 原生输入边界、确认问题与测试限制 |
| [旧宿主缩放移除记录](REBROWSER_LAYOUT_ZOOM.md) | 已否决试验及协议／CLI 从源码移除；正常浏览器缩放与网易布局适配保留 |
| [Android 交接](REBROWSER_ANDROID_HANDOFF.md) | 浏览器注册、默认应用、外部链接和 `.1` APK 交接 |
| [历史记录](REBROWSER_HISTORY_PLAN.md) | 生命周期保留、维持／暂停及验证范围 |
| [YouTube 兼容](YOUTUBE_PLAYER_COMPATIBILITY.md) | 窄兼容策略与未覆盖场景 |
| [回归检查](../tools/tests/README.md) | JVM、Node、CLI、打包测试 |
| [Release 构建工具](../tools/build/README.md) | R8、无损 ZIP、对齐、签名与体积统计 |
| [资源来源](ASSET_CREDITS.md) | 背景、展示图、示例头像许可与搜索品牌；来源不等于任意品牌使用授权 |

## 当前正式版

[全部发布记录索引](releases/README.md)；更早研究与讨论仅解释历史，不自动恢复实施或测试。

[v1.0.4 正式版记录](releases/v1.0.4.md)：音乐启动／布局、官方与桌面歌词、系统媒体seek修正、首页双入口卡片；当前手机有限观察和三套件必要验证，未测范围由日常反馈。

[v1.0.3 历史正式版记录](releases/v1.0.3.md)：音乐内存/背压修复、三套件签名与自动回归、限定平板证据；该轮手机/Android16未专项测试，不用后来证据改写历史。

[v1.0.3-beta 测试版记录](releases/v1.0.3-beta.md)：历史三套件音乐测试版；标签/资产保持不变，不以新版结果改写 Beta 证据。

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

本维护机器临时文件统一使用 `/home/zhao/temp/re_outlook/`，不使用内存盘 `/tmp`。历史设备截图、日志、私钥、原始照片及R8报告保留在仓库外，不作为公开文档资产上传，也不因目录名含temp／旧版本就删除。展示图仅接受机主先审查原图／隐私处理对照并批准的最终处理版本；原图和对照材料仍保持私有。[清理边界与流程](MAINTENANCE.md#安全清理流程)。
