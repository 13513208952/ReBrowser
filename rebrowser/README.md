# ReBrowser v2 主程序

独立 Android 项目，入口 `io.github.rebrowser.ReBrowserApplication`；主代码仍位于总树的 [`app/src/main/java/io/github/rebrowser`](../app/src/main/java/io/github/rebrowser)。不引入旧 `io.github.reoutlook`、WebView、旧资产或数据迁移。

- 正式身份 `io.github.rebrowser` / 2.0.0 / API30+，仅全量含背景版。
- 原生主窗口和内部管理员控制台已开始编写；工作区/标签模型、分区及 Gecko Session/View 所有权、浏览器协调器也已有实际源码。导航目标/外部Intent清理、选中Tab后的SDK加载和进度/敏感快照回调已有源码（回调尚未实测）；Native导航委托已有保守web/blank加载策略和12项HOST测试，Popup/未支持的schemes仍拒绝；网页主窗口、委托实际初始化、持久化/正确恢复和权限协调仍未接通，尚不是完整可用浏览器；编译身份验签不是动作准入。
- 下载响应的有界私有暂存流复制/关闭已有实际代码和10项HOST测试，按固定Gecko源码发现只用bulk读取与EOF探测；这不代表下载UI/目标选择/发布或真实Gecko流已接通。
- 自身签名身份读取/编译 Root 元数据、单层 Back、精确来源确认文案和 Android 机主验证封装已有源码（未执行真实OS认证；控制器/私有票据/桥接已有28项合成平台HOST测试）；LastGood signer/Root/epoch小账本已有实际原子文件存储及17项HOST文件系统测试；四步reducer和私有OS票据桥接已有源码（24项合成验证信号HOST测reducer，另28项实际控制器/桥接方法HOST使用合成平台，非真实Android认证）；正式根、实际来源UI/本人验证/擦除/完整来源连续性及命令准入尚未接通，业务保持关闭；不以 Debug、首次点击或有效签名跳过来源/本人授权。
- Debug 使用独立 `.development` 包名，且默认无签名；不自动创建全局调试密钥。正式 Release 打包当前主动阻断，不代表已经可交付。
- 每次仅选择一个 ABI：`-PrebrowserAbi=arm64-v8a`（默认）、`armeabi-v7a` 或 `x86_64`。正式分别执行三次并校验三个独立 APK；临时手机测试只选 arm64，不产出 universal APK。

复用已验证的 AGP9.4 / Gradle9.6 / JDK21 / SDK37.2；先通过内存和临时存储预检。编译主程序（不做 JNI/R8/dex/APK）：

```sh
$GRADLE_96/bin/gradle -p rebrowser :app:compileDebugJavaWithJavac \
  :app:processDebugMainManifest --offline --no-daemon --max-workers=1 \
  -PrebrowserAbi=arm64-v8a -PrebrowserMozillaRepo=file://$ISOLATED_MAVEN
```

SDK、Gradle 用户目录、项目缓存及 `java.io.tmpdir` 使用既有隔离环境；不要改动全局 SDK/缓存。代码/清单编译通过不等于 APK、Android运行或网站验证。

R44按模块推进：入口与进程→浏览器核心→Gecko宿主→管理员协议→内建连接器→原生UI，依赖按需协作，协议/边界允许随能力需求调整。本批跨12个runtime文件收口入口/来源生命周期：实际own-process路由、唯一startup工厂、incarnation/代次绑定许可，旧catalog/runtime/task/target/DB不能用新许可重定向。实际AGP编译51源码/244class，386HOST（纯切片230），当前编译DDL实际HOST SQL39+235通过。Root仍未配置，真实source接受/Android进程/SQLite DAO/Gecko及整模块运行未完成；下一依赖是原生continuity接受回执提供器，不用元数据或布尔值解锁。提交最少10个实质runtime代码文件，不设上限。

历史R43按O64完成至少10个实质runtime代码文件后再提交：新增8个原生工具栏/双层标签面板/多选/进度/Dialog/窗口样式/Viewport文件，实质修改BrowserActivity与BrowserCoordinator；测试和文档不凑数，不设10个上限。本轮[实际离线AGP检查](../planning/geckoview-v2/evidence/production-main-agp-R43-checks.json)编译51份本体源码/238class，367项HOST测试通过（新增37项界面模型）；manifest任务UP-TO-DATE并核对当前产物。未执行Android View/Window/Dialog/Gecko运行或JNI/dex/R8/APK。入口仍只构建锁定原生壳，完整浏览器/已核验epoch域工厂/有限权限/恢复尚未接通，不能把组件代码当可用产品。

R42独立限额Java检查及R38历史AGP记录保留为分级检查点。TaskCoordinator/TargetReferences/资源预算与事务fence已有源码和限定HOST证据；Android SQLite、实际窗口/内存工厂、完整ActorContext/命令派发器和全域静默仍未完成，引用/槽位/编译均不授予权限。
