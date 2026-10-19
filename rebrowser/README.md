# ReBrowser v2 主程序

独立 Android 项目，入口 `io.github.rebrowser.ReBrowserApplication`；主代码仍位于总树的 [`app/src/main/java/io/github/rebrowser`](../app/src/main/java/io/github/rebrowser)。不引入旧 `io.github.reoutlook`、WebView、旧资产或数据迁移。

- 正式身份 `io.github.rebrowser` / 2.0.0 / API30+，仅全量含背景版。
- 原生主窗口和内部管理员控制台已开始编写；工作区/标签模型、分区及 Gecko Session/View 所有权、浏览器协调器也已有实际源码。导航目标/外部Intent清理、选中Tab后的SDK加载和进度/敏感快照回调已有源码（回调尚未实测）；网页主窗口、实际委托、持久化/正确恢复和权限协调仍未接通，尚不是完整可用浏览器；编译身份验签不是动作准入。
- 自身签名身份读取/编译 Root 元数据、单层 Back、精确来源确认文案和 Android 机主验证封装已有源码（尚未执行 OS 认证/票据消费）；正式根、LastGood/epoch/逐次本人验证和完整来源连续性及命令准入尚未接通，业务保持关闭；不以 Debug、首次点击或有效签名跳过来源/本人授权。
- Debug 使用独立 `.development` 包名，且默认无签名；不自动创建全局调试密钥。正式 Release 打包当前主动阻断，不代表已经可交付。
- 每次仅选择一个 ABI：`-PrebrowserAbi=arm64-v8a`（默认）、`armeabi-v7a` 或 `x86_64`。正式分别执行三次并校验三个独立 APK；临时手机测试只选 arm64，不产出 universal APK。

复用已验证的 AGP9.4 / Gradle9.6 / JDK21 / SDK37.2；先通过内存和临时存储预检。编译主程序（不做 JNI/R8/dex/APK）：

```sh
$GRADLE_96/bin/gradle -p rebrowser :app:compileDebugJavaWithJavac \
  :app:processDebugMainManifest --offline --no-daemon --max-workers=1 \
  -PrebrowserAbi=arm64-v8a -PrebrowserMozillaRepo=file://$ISOLATED_MAVEN
```

SDK、Gradle 用户目录、项目缓存及 `java.io.tmpdir` 使用既有隔离环境；不要改动全局 SDK/缓存。代码/清单编译通过不等于 APK、Android运行或网站验证。
