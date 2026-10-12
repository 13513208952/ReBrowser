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
| [管理协议](REBROWSER_ADMIN_CONTROL.md)、[管理员 CLI](../tools/admin/README.md) | 挑战、签名、三级授权、固定命令与数据边界 |
| [网站权限](REBROWSER_SITE_PERMISSIONS.md) | 单站授权、后台策略及永久禁止能力 |
| [Android 交接](REBROWSER_ANDROID_HANDOFF.md) | 浏览器注册、默认应用、外部链接和 `.1` APK 交接 |
| [历史记录](REBROWSER_HISTORY_PLAN.md) | 生命周期保留、维持／暂停及验证范围 |
| [YouTube 兼容](YOUTUBE_PLAYER_COMPATIBILITY.md) | 窄兼容策略与未覆盖场景 |
| [回归检查](../tools/tests/README.md) | JVM、Node、CLI、打包测试 |
| [Release 构建工具](../tools/build/README.md) | R8、无损 ZIP、对齐、签名与体积统计 |
| [资源来源](ASSET_CREDITS.md) | 照片与搜索品牌；来源不等于任意品牌使用授权 |

## 当前正式版

[v1.0.0 发布记录](releases/v1.0.0.md)：首个正式版的变化、签名 APK 体积与校验、已完成检查和未覆盖范围。

## 历史发布与讨论

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
