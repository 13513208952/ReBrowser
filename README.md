# ReBrowser

轻量的 Android 浏览器，用独立的浏览环境分开不同网站的登录状态，并提供搜索、标签页、收藏、历史记录和下载管理。

应用还内置 **ReOutlook** 邮件模式：使用 Outlook 网页收发邮件，将已同步的邮件保存在本机供离线阅读。它是同一应用内的子软件，不需要另外安装。

## 下载与安装

前往 **[GitHub Releases](https://github.com/13513208952/ReBrowser/releases)** 下载 APK。

当前正式版本：**[v1.0.2](https://github.com/13513208952/ReBrowser/releases/tag/v1.0.2)**。

本版修复已授权网站直接调用剪贴板桥时的用户手势旁路，并纳入设备类型自动识别／手动指定、平板完整窗口跟随系统四方向及设置固定竖屏。旧版用户建议覆盖升级；验证范围见[发布记录](docs/releases/v1.0.2.md)。

需要 **Android 10 或更高版本**。浏览器功能还需要系统 WebView 支持独立 Profile 和网页权限管理；仅 Android 版本满足不保证所有厂商设备都支持。能力不足时仍可打开应用管理，进入可用的邮件模式，不会降低浏览器隔离要求。

| 版本 | 默认新标签页背景 | 适合谁 |
| --- | --- | --- |
| **[Lite 轻量版](https://github.com/13513208952/ReBrowser/releases/download/v1.0.2/ReBrowser-v1.0.2-lite.apk)** | 纯白，不包含背景照片 | 更看重安装包大小，约 250 KB |
| **[Background 背景版](https://github.com/13513208952/ReBrowser/releases/download/v1.0.2/ReBrowser-v1.0.2-background.apk)** | 专属蓝紫背景 | 喜欢照片背景，约 1.10 MB |

两版默认显示近期访问，历史为空时不显示。升级保留已有背景和近期访问选择；轻量版不含照片，即使记住照片偏好也显示纯白。没有单独的个性化设置页。

两版是同一个应用，**不能同时安装**。换版本或升级请直接覆盖安装，**不要先卸载或清除应用数据**。重要的临时浏览环境请先上锁，避免升级重启后丢失。

## 开始使用

- 打开 **ReBrowser**，在新标签页输入网址或搜索内容；设置中可选择 Bing、百度、Google 或 DuckDuckGo。
- **总标签页**是独立浏览环境；同一总标签页内的**子标签页**共享登录状态。新建的总标签页默认是临时环境，需要长期保留时请主动上锁。
- 设置分为 **常规、隔离与权限、应用管理** 三页。默认浏览器只由你在系统设置中选择，应用不会自动替你更改。
- 使用邮件时，打开 **设置 → 应用管理 → ReOutlook → 进入**。也可在那里开启独立桌面入口；新安装默认隐藏邮件入口，升级保留原有入口状态。

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

本项目采用 [GPL-3.0](LICENSE)，与 Microsoft、Outlook 或搜索引擎厂商没有隶属或官方合作关系。第三方名称与商标归各自权利人；图形资源说明见 [资源来源](docs/ASSET_CREDITS.md)。
