# 开发参考

本页面向构建、维护和贡献代码的开发者。普通用户请从[项目首页](../README.md)与[使用指南](user/REBROWSER.md)开始。当前发布验收状态见[项目状态](PROJECT_STATUS.md)，历史记录不自动代表本轮重新验证。

## 仓库与身份

主仓库：[13513208952/ReBrowser](https://github.com/13513208952/ReBrowser)。原 ReOutlook 仓库原地更名；历史提交、标签和 Releases 保留。ReBrowser 是软件主名称，ReOutlook 是同 APK 的邮件模式，不是插件或独立安装。

Gradle 项目名与显示名为 ReBrowser；内部 Java package/applicationId 保留 `io.github.reoutlook`，数据库、命名 Profile 和原签名保持连续。不要为更名修改安装身份、删除旧数据或更换签名。

## 构建

需要 JDK 17 和 Android SDK 36；最低 Android API 29。开发树已拆分套件与背景两轴，合法组合是 `minimalLite`、`minimalBackground`、`fullBackground`，相同安装身份，不能并存；`fullLite` 不生成。正式 v1.0.2 的历史两风味产物不因此改变。v1.0.3-beta 使用极简 code 101／全量 code 1,000,101；普通安装器拒绝新版全量回退极简或旧稳定版。套件剔除、版本区间与升级注意事项见[音乐开发记录](NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)。

```bash
git clone https://github.com/13513208952/ReBrowser.git
cd ReBrowser
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
export TMPDIR=/home/zhao/temp/re_outlook/build-jvm
mkdir -p "$TMPDIR"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon testMinimalLiteDebugUnitTest testMinimalBackgroundDebugUnitTest testFullBackgroundDebugUnitTest
./gradlew --no-daemon assembleMinimalLiteDebug assembleMinimalBackgroundDebug assembleFullBackgroundDebug
./gradlew --no-daemon assembleMinimalLiteRelease assembleMinimalBackgroundRelease assembleFullBackgroundRelease
./gradlew --no-daemon lintMinimalLiteDebug lintMinimalBackgroundDebug lintFullBackgroundDebug
```

在本维护机器上临时文件统一使用 `/home/zhao/temp/re_outlook/`，不使用内存盘 `/tmp`；其他开发机器请自行指定可用磁盘目录。

产物位于 `app/build/outputs/apk/{minimalLite,minimalBackground,fullBackground}/{debug,release}/`，文件名分别包含 `minimal-lite`、`minimal-background`、`full-background`。Release 为未签名产物，已启用完整 R8 优化／混淆、资源剪枝与压缩 DEX。公开包继续使用历史证书以覆盖升级，Release 本身不含 `DEBUGGABLE`；历史 Android Debug 签名不是新的生产签名方案。签名私钥和管理员私钥绝不提交。

## 测试与发布

- [回归检查](../tools/tests/README.md)：JVM、实际权限脚本的 Node 检查、固定 CLI 请求、打包固件测试。
- [构建工具](../tools/build/README.md)：未签名 APK 的无损 ZIP 候选压缩、zipalign、现代签名与最终 ZIP 体积统计。
- [项目状态](PROJECT_STATUS.md)：本轮通过／待验证项及设备覆盖边界。
- [历次发布说明](releases/)：对应版本的验证、包体与校验值，不替代当前版本验收。

发布前逐条比较压缩前后的解压内容、签名、对齐、网络政策、非调试 Manifest、套件剔除和三种组合的照片有无；保留本地 R8 报告。展示最终签名 APK 的体积分布再推送／发布。只发布源码、测试、文档和批准的 APK／校验资产，不发布密钥、设备日志、截图、原始大图或临时网络例外。

真机升级只能覆盖安装（例如 `adb install -r`），不可卸载或清数据；先提醒锁定重要临时环境。保持用户最新默认浏览器选择，不自动改角色、不绕过锁屏或验证码。`.1` 文件与安装器操作仍由机主自行测试；测试 YouTube 前需告知机主准备代理。

## 实现与协议

| 主题 | 参考文档 |
| --- | --- |
| 音乐独立运行时、官方登录、套件及延期验收 | [音乐开发记录](NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)、[机主原话](MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md) |
| 当前设置、风味默认、升级入口和原生能力回退 | [设置与应用管理](REBROWSER_SETTINGS.md) |
| 总／子标签页、命名 Profile 与状态所有者 | [浏览器设计与沿革](REBROWSER_EXPLORATION.md) |
| 邮件桥、账号、缓存、回填与数据库 | [邮件架构](REOUTLOOK_ARCHITECTURE.md) |
| 管理员三级授权、双根、固定命令和数据边界 | [管理协议](REBROWSER_ADMIN_CONTROL.md)、[CLI](../tools/admin/README.md) |
| 网站授权与后台策略 | [网站权限](REBROWSER_SITE_PERMISSIONS.md) |
| Android 浏览器、默认应用、外部链接和 APK 交接 | [Android 交接](REBROWSER_ANDROID_HANDOFF.md) |
| 历史保留与实现 | [历史记录](REBROWSER_HISTORY_PLAN.md) |
| YouTube 窄兼容策略及验证范围 | [播放兼容](YOUTUBE_PLAYER_COMPATIBILITY.md) |
| 本地图片与第三方品牌来源 | [资源来源](ASSET_CREDITS.md) |

管理员能力必须保持固定白名单和有界状态，不增加任意 Intent、文件／偏好／数据库读取、脚本执行、安装命令或网站能力启用／授权。ReBrowser 根不能授权邮件导出。

## 历史与讨论

[开发历史](DEVELOPMENT_HISTORY.md)与[新标签页讨论](REBROWSER_NEW_TAB_PLAN.md)保存决策背景；过往默认值、页面名称和候选方向可能已被后续需求替换。实际行为以当前源码、使用指南、设置说明和对应发布验收为准。不要把讨论稿作为已实现功能或不可更改的要求。
