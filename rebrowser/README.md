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

R52继续创建6个实质Java文件：NativeRecordCodec、OwnedDataCatalog、BookmarkStore、HistoryStore、SettingsRepository、SubapplicationCoordinator，另联动4个既有接口；Java114→120、PLAN66→61。真实实现固定purpose3/4/5的现有私有记录AEAD/CAS、书签树有界CRUD、历史保留/暂停与显式过期、四搜索引擎/双内置子应用desired设置，以及一次性同步原生交接fence；启动器只懒建规范owner，不读取默认值、不创建密钥/目录、不自动保存/恢复。实际120源码/613class/1034HOST，58纯切片552HOST＋SDKbootclasspath，新增49项模型/codec/真实HOST文件JCA/CAS回归。没有把desired-disabled/回调返回当实际停止；真实Gecko历史捕获、私密会话排除、Android IO/组件执行和管理员持久head适配尚未完成。详见[契约](../planning/geckoview-v2/records/native-browser-metadata-contract.json)。

历史R51依O90/O91转向尚未创建的正式核心：新增10个实质Java文件（ActorContext、AuthorityPolicy、DisableLedger、LowDelegationVerifier、DeviceWhitelist、TargetPossessionProof、OwnerGrantStore、AdminAudit、ActionRegistry、ControlDispatcher）并联动4既有接口，Java104→114、PLAN76→66。真实实现签名历史/有界委托/设备名单隔离与目标挑战、仅在已消费挑战预留回调内有效的Actor、当前策略和任务预留桥；不把语法/持钥/历史有效当最新授权。实际114源码/590class/985HOST，57纯切片542HOST＋实际SDKbootclasspath，新增41JCA/内核回归。正式Root/设备表仍空，持久当前head/原生目标适配器、效果分发和Android系统授权尚未实测/激活，见[新核心契约](../planning/geckoview-v2/records/current-authority-kernel-contract.json)。

历史R50在O88通知后主审 `dsh/result/d03/`：25最新源哈希与6份R49依赖独立核对、107原始非build文件先原样归档，只合入25个指定Java扩展，不复制其余15Java/6资产/context/build脚本。主审修正重绘帧失效、旧回调重定向、设置拆分提交、推断文件发布、未提供的媒体能力、图像额度ABA、私有View清理、未映射词字符高亮等真实问题，共26实质runtime文件。保留82主线基线测试类，另列28交付相关类123测试及43项主审新增回归；实际AGP104源码/560class/944HOST，49纯切片501HOST＋实际SDKbootclasspath。交付自述180测试/探针不当独立执行证据；Android View/Bitmap/Notification/MotionEvent/Gecko/网站仍未实测，后台、Actor准入、恢复和正式Root仍未完成。D03无剩余保留目标，不把25扩展算25新文件/模块完成，详见[主审记录](../planning/geckoview-v2/reviews/R50-D03-reviewed-code-extension.json)。

历史R49先按O83/O85/O86分配D03：L49–L73共25个既有Java文件的实质扩展，源码/算法任务为主，不计为25新增；开始前上下文全同步，无额外交接或依赖副本，`dsh/result-D03/`隔离交付后待owner通知主审。主agent避开该25范围，跨11核心文件实现epoch私有pin→保留目录一致捕获→4MiB严格二进制→既有私有AEAD/CAS→原生单次IO检查。临时/真实private context不落保留记录；原生壳仅在信任来源下显式检查数量，不恢复、不自动写。实际AGP104源码/502class/778HOST，49纯切片427HOST＋实际SDKbootclasspath；新增50项使用合成来源/public toy key与真实HOST文件，不是Android Handler/View/Keystore/Gecko资格。当前184叶节点、108份源码/资产有限准入、76个PLAN；真实checkpoint的SDK全量JSON物化、persistent cookie context与正确恢复仍明确待办，Root未配置。详见[核心契约](../planning/geckoview-v2/records/native-retained-browser-store-contract.json)。

历史R48按O80审查D02的42个候选，原样归档输入、独立校验42份源哈希及9份R47依赖后，并入36 Java＋4个未激活资产；两份未经批准的窗口/桌面CSS继续HOLD。修复32KiB桥分块、邮件搜索Reader所有权/取消/UTF16/片段上限、旧seek手势/能力猜测、回收视图私有内容、伪APK验证DTO等问题；KeyPurpose接入私有记录格式，共38个实质Java文件。本轮实际AGP101源码/487class/728HOST；48纯切片398HOST＋实际SDK bootclasspath，另20项合成DOM断言/5项资产静态测试。当前105份源码/资产有限准入、77个PLAN；未注册新Activity、打包新资产、执行实际Android/Gecko/CSP/网站或完成后端，Root仍未配置。详见[主审记录](../planning/geckoview-v2/reviews/R48-D02-reviewed-source-integration.json)。

历史R47跨11个实质runtime文件接入原生既有epoch的私有记录依赖：仅既有Android AES wrapping key、独立非mail的AEAD metadata格式、固定目录下有界CAS/0600 stage/atomic move/force/readback；不自动创建目录/密钥/seed，未防同epoch回滚。旧6个低成本agent交付修正后以有限模型/API范围准入，当时D02新42文件预留未触碰（后续主审见R48）。实际AGP65源码/307class/577HOST；独立29纯切片293HOST与SDKbootclasspath；49私有记录流水线用明确合成来源/公开toykey/真实HOST文件，67交付模型测试不是真实Android View/Keystore/FS或完整backend验证。当前Root仍未配置。

历史R46跨10个实质runtime文件连接原生accepted epoch→临时浏览器域→private context/Gecko privateMode→open前permission/content/navigation/progress delegates。Selection精确校验workspace/tab双revision拒绝ABA；session级title/location不能伪造真实document。修复源发布未知提交/确认前回调不得信任的缺口。实际AGP56源码/264class/456HOST；独立26纯切片260HOST与SDKbootclasspath；新增17模型/13私有adapter＋拒绝/body-close HOST，不是Gecko/Android实测。Root仍无配置，未激活完整UI、持久化恢复、finiteGrant、Popup或下载发布。

历史R45继续闭合入口的原生来源依赖：新增SourceBootstrap稳定精确来源回执，并将epoch16接到入口/Gate/数据库/分区/目标/runtime，跨12个实质runtime文件。实际AGP编译52源码/253class，413HOST；独立纯切片234，HOST SQL39+235通过。23项回执流水线测试使用私有合成OS来源和真实HOST metadata文件，不是Android签名/文件系统/Handler实测。当前Root仍未配置；基准缺失/非stable/不匹配/仅lineage轮换仍锁定，不自动seed或擦除。first-fresh清单、恢复/销毁提交与完整浏览器仍需完成。

历史R44按模块推进：入口与进程→浏览器核心→Gecko宿主→管理员协议→内建连接器→原生UI，依赖按需协作，协议/边界允许随能力需求调整。本批跨12个runtime文件收口入口/来源生命周期：实际own-process路由、唯一startup工厂、incarnation/代次绑定许可，旧catalog/runtime/task/target/DB不能用新许可重定向。实际AGP编译51源码/244class，386HOST（纯切片230），当前编译DDL实际HOST SQL39+235通过。Root仍未配置，真实source接受/Android进程/SQLite DAO/Gecko及整模块运行未完成；下一依赖是原生continuity接受回执提供器，不用元数据或布尔值解锁。提交最少10个实质runtime代码文件，不设上限。

历史R43按O64完成至少10个实质runtime代码文件后再提交：新增8个原生工具栏/双层标签面板/多选/进度/Dialog/窗口样式/Viewport文件，实质修改BrowserActivity与BrowserCoordinator；测试和文档不凑数，不设10个上限。本轮[实际离线AGP检查](../planning/geckoview-v2/evidence/production-main-agp-R43-checks.json)编译51份本体源码/238class，367项HOST测试通过（新增37项界面模型）；manifest任务UP-TO-DATE并核对当前产物。未执行Android View/Window/Dialog/Gecko运行或JNI/dex/R8/APK。入口仍只构建锁定原生壳，完整浏览器/已核验epoch域工厂/有限权限/恢复尚未接通，不能把组件代码当可用产品。

R42独立限额Java检查及R38历史AGP记录保留为分级检查点。TaskCoordinator/TargetReferences/资源预算与事务fence已有源码和限定HOST证据；Android SQLite、实际窗口/内存工厂、完整ActorContext/命令派发器和全域静默仍未完成，引用/槽位/编译均不授予权限。
