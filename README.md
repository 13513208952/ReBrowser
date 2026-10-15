# ReBrowser

轻量的 Android 浏览器，用独立的浏览环境分开不同网站的登录状态，并提供搜索、标签页、收藏、历史记录和下载管理。

全量包还内置 **ReOutlook** 邮件模式：使用 Outlook 网页收发邮件，将已同步的邮件保存在本机供离线阅读。它是同一应用内的子软件，不需要另外安装。

## 下载与安装

前往 **[GitHub Releases](https://github.com/13513208952/ReBrowser/releases)** 下载 APK。

当前正式版本：**[v1.0.3](https://github.com/13513208952/ReBrowser/releases/tag/v1.0.3)**。

本版提供三种套件，修复音乐旧页面引用、模块登记与封面等待队列的无界保留，增加网页请求背压，并删除指定的过时管理说明。平板核心控制与短时内存观察已完成；高成本完整矩阵移至后期，手机及 Android 16 专项由机主明确暂不进行，不能宣称已通过。[发布记录与验证边界](docs/releases/v1.0.3.md)。

### 套件选择

| 安装包 | 内容 |
| --- | --- |
| [极简无背景](https://github.com/13513208952/ReBrowser/releases/download/v1.0.3/ReBrowser-v1.0.3-minimal-lite.apk) | 仅 ReBrowser，无背景照片，约 220 KB |
| [极简有背景](https://github.com/13513208952/ReBrowser/releases/download/v1.0.3/ReBrowser-v1.0.3-minimal-background.apk) | 仅 ReBrowser，有背景照片，约 1.07 MB |
| [全量有背景](https://github.com/13513208952/ReBrowser/releases/download/v1.0.3/ReBrowser-v1.0.3-full-background.apk) | ReBrowser、ReOutlook、音乐（网易云），约 1.12 MB |

音乐在 **设置 → 应用管理**由机主启用，使用独立任务／Profile 和真正的官方 WebPlayer，保留官方登录、验证码与权益规则。系统媒体控制、后台播放和新启动暂停已有实现及平板有限验证；完整返回、恢复、窗口及长期锁屏等仍未完成验收。

**使用邮件或音乐请选择全量包。三包同身份，不能并装；只覆盖安装，不卸载／清数据。安装新版全量后，普通安装器不能回退极简，也不能回退旧 v1.0.2；极简→全量可覆盖升级，但完整迁移矩阵未验收。** [校验文件](https://github.com/13513208952/ReBrowser/releases/download/v1.0.3/SHA256SUMS.txt)。

需要 **Android 10 或更高版本**。浏览器功能还需要系统 WebView 支持独立 Profile 和网页权限管理；仅 Android 版本满足不保证所有厂商设备都支持。能力不足时，全量包仍可打开原生应用管理、进入可用的邮件模式；极简包不含子应用。不会降低浏览器隔离要求。

三包默认显示近期访问，历史为空时不显示。升级保留已有背景和近期访问选择；无背景包不含照片，即使记住照片偏好也显示纯白。重要的临时浏览环境请先上锁，避免升级重启后丢失。

历史 [v1.0.3-beta](https://github.com/13513208952/ReBrowser/releases/tag/v1.0.3-beta) 与 [v1.0.2](https://github.com/13513208952/ReBrowser/releases/tag/v1.0.2) 标签及资产保持不变；不要通过卸载来强行回退。

## 开始使用

- 打开 **ReBrowser**，在新标签页输入网址或搜索内容；设置中可选择 Bing、百度、Google 或 DuckDuckGo。
- **总标签页**是独立浏览环境；同一总标签页内的**子标签页**共享登录状态。新建的总标签页默认是临时环境，需要长期保留时请主动上锁。
- 设置分为 **常规、隔离与权限、应用管理** 三页。默认浏览器只由你在系统设置中选择，应用不会自动替你更改。
- 使用邮件时（全量包），打开 **设置 → 应用管理 → ReOutlook → 进入**。也可在那里开启独立桌面入口；新安装默认隐藏邮件入口，升级保留原有入口状态。

详细操作见 **[ReBrowser 使用指南](docs/user/REBROWSER.md)** 和 **[ReOutlook 邮件指南](docs/user/REOUTLOOK.md)**。

## 数据与安全

- 邮件缓存、收藏和历史记录保存在本机；没有开发者服务器、广告或遥测服务。
- 不同总标签页分开保存网站状态，浏览器不与 ReOutlook 共用登录环境。
- 网站权限仍需要你的授权；网站通知、后台推送和运动传感器保持禁用。
- 暂停历史记录**不是无痕模式**；卸载应用或清除数据会移除本地资料。应用不提供 Android 自动备份。

项目尚未接受独立安全审计，不能保证每家 WebView 或每个网站完全兼容。为兼容旧版覆盖升级，公开包沿用原 Android Debug 证书，APK 本身是非调试 Release 构建；这不等于已更换为正式生产签名。详见 [隐私说明](PRIVACY.md)、[安全策略](SECURITY.md) 和 [已知限制](docs/PROJECT_STATUS.md)。

## 反馈与更多资料

遇到问题可在 **[Issues](https://github.com/13513208952/ReBrowser/issues)** 反馈，请描述设备、Android／WebView 版本和复现步骤；不要公开真实邮件、密码、登录令牌或私钥。

[文档目录](docs/README.md) · [开发参考](docs/DEVELOPMENT.md)

本项目采用 [GPL-3.0](LICENSE)，与 Microsoft、Outlook、网易云音乐或搜索引擎厂商没有隶属或官方合作关系。第三方名称与商标归各自权利人；图形资源说明见 [资源来源](docs/ASSET_CREDITS.md)。
