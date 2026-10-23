# ReBrowser v2 主程序

独立 Android 项目，入口 `io.github.rebrowser.ReBrowserApplication`；主代码仍位于总树的 [`app/src/main/java/io/github/rebrowser`](../app/src/main/java/io/github/rebrowser)。不引入旧 `io.github.reoutlook` / WebView 运行实现或数据迁移；既有产品体验和已批准的界面资源不是应当丢弃的旧实现。

## 当前主程序接线（R57，未安装Demo）

`BrowserActivity`现在确实消费同一浏览器域、新标签页原生数据绑定和GeckoView／SessionDisplayBinding，不再只是开发状态展示。已接入原有单行顶栏、全窗背景与白色前景、地址导航、实际主页配置、新建总／子标签、双层概览和临时环境切换；原生会话观测经弱窗口订阅合并刷新，不用网页轮询，不把显示URI当文档授权。返回等待原会话新的位置观测，不把SDK调用返回称作已返回；会话退出也不会自动重开或堵住其他标签的目录操作。

这些路径仍只在独立来源检查已接受时启用。**新安装的首次初始化／密钥与初始记录创建尚未接通，所以目前仍不能给出可操作的已安装Demo**；没有为展示效果绕过来源闸门。当前原生工厂仅临时环境／8个活会话；持久恢复、Popup、多选、完整权限、历史OPEN、收藏和三同级设置等后续逐项闭合。概览列表也尚不是全部批准卡片外观的最终恢复。

实际有限检查：1338项HOST／146测试类通过（R57新增22项），170主Java以Java8语言级和实际SDK37.2／固定Gecko／生成R类编译为908class；18项资源实际链接，仍无Android／官网运行资格。见[源绑定检查](../planning/geckoview-v2/evidence/native-browser-window-R57-host.json)。

## 推进顺序与管理员配置

先打通ReBrowser主程序，再依次完成ReMusic、ReOutlook；相关管理员控制调试能力随对应真实业务服务一起实现，不再独立扩展脱离产品的框架。

机主已授权生成初始Root，公钥已固定编入身份表；私钥加密留仓库外，离线备份交接仍待完成。唯一连接设备已被动识别，但尚未安装正式v2，缺少真实安装公钥，仍是待登记候选而不是已激活名单。名单不是全局准入条件：最高危操作无名单例外时，必须保留有效管理员密钥＋真实机主授权路径。公钥配置不等于来源接受、机主认证或业务解锁，详见[密钥与设备登记](../docs/ADMINISTRATOR_PROVISIONING.md)。R56实际1316项HOST及全部170主Java实际SDK编译通过，未安装、启动或唤醒设备。

## R55产品体验修正：新标签页

`NewTabView`已改回批准的品牌Logo、圆角共用搜索／地址输入栏和横向最近访问栏；没有独立引擎Spinner、四个引擎按钮、收藏管理或空卡片。`bindNative`在有界后台线程读取同一规范设置／历史拥有者：只用实际默认引擎，最多8个最新不同完整URL；点击重新查找原记录及到期状态，通用显示token不能当原生历史记录打开，网址输入不被一律当搜索词。

13份背景／品牌／提交按钮资源逐字节继承v1.0.4已批准资源，独立置于本工程`app/src/main/res/`，并已通过实际AAPT2链接；没有全量引入旧资源或旧实现。[来源与hash](../planning/geckoview-v2/records/native-new-tab-resource-provenance.json)保留背景版Bing／DuckDuckGo文字白色处理、Google 75%透明画布等既有调整，不宣称新增品牌授权。3324×2493母图后台解码为水平居中／顶部取景，输出像素有限；使用自身Activity的WindowMetrics区分实际窗口与键盘裁剪，不据键盘尺寸推断设备。没有回收已显示Bitmap来冒充渲染器已释放。

`SettingsRepository`补入背景／最近访问两项持久配置，修改搜索引擎或子应用不重置它们。未发行的`RBSETT02`明确拒绝不完整旧开发格式，不猜测迁移或缺失默认值；完整三同级设置页仍待恢复。

新标签页、历史页和Gecko访问采集现在共用启动器的一条单worker／64排队数据IO通道，避免同一解码快照额度被独立线程争用。原生访问在入队前捕获进程记录开关代次，暂停期间的中间访问、以及跨暂停／恢复周期的旧排队访问都不会在恢复后补写。最近栏刷新保留未改变的已解码背景；解码队列饱和只保留有界弱引用等待者，等实际完成后再尝试固定公共图片，不引入额外线程或任意URI。

R55时实际有限检查：170份主Java源码完成Java8＋实际SDK37.2／固定Gecko／生成R类编译，1307项HOST回归通过（R55新增44项），另8项资源来源检查及12份WebP实际链接包字节核对。**完整主窗口尚未调用`bindNative`，BrowserActivity仍为开发锁定壳；顶栏／全窗背景联动、全局Back、历史OPEN路由和实际Android渲染／IME／解码仍未完成或验证。**本轮未构建APK／装机／登录／配置根，不把组件或测试当作产品已恢复。见[当前工作状态](../planning/geckoview-v2/records/product-experience-correction-status.json)／[源绑定有限证据](../planning/geckoview-v2/evidence/product-experience-correction-R55-host.json)。

## R54历史记录修正

本轮直接修正既有主程序文件，不重画产品界面或推翻总树：

- 历史页恢复维持记录、单次清理有时限记录、面具暂停写入三个独立控件，以及原有标题／网址／时间和来源列表。取消逐条“保留”作为期限规则的替代设计；加载更早记录不再被展示层的100行限制卡住。
- 普通临时总标签页仍记录30分钟历史；打开的副／主来源长期保留，副来源收起／降级从转换时起计时，重复关闭不续期。维持状态落盘，结束后有时限记录重新获得30分钟；暂停仅属于规范历史拥有者的进程状态，不落盘、不暂停已有记录到期。
- 已接线绑定的 Gecko HistoryDelegate 顶层访问 → 浏览器原生归属 → 有界后台历史写入，标题更新不新增访问；环境级别／收起／恢复／删除向同一个存储服务提交期限变化。网站帧、重定向源和错误访问不直接记为成功访问。历史页读取、到期、清理、删除和恢复记录时的当前打开页同步已连接实际私有记录IO，未复制Cookie或重开旧环境。
- 更正尚未发行的本地历史格式为`RBHIST02`（来源／子页／期限／访问顺序／维持状态）。旧开发格式不会被猜测转换或自动覆盖，缺失记录仍不当作首次安装。现有4MiB／5000条元数据后端上限仍需后续存储闭合，达到容量会报错，不自动丢弃长期记录；这不是最终无限历史规模保证。

R54时实际有限离线检查：170份主源码完成Java8＋固定SDK37.2／Gecko类编译，1263项HOST测试通过，含47项R54新增回归；不是Android渲染、真实Gecko回调／私有模式差异、进程恢复或网站验收。最新检查与仍需接通的产品路径见上节。正式根配置和来源保护没有被绕过。


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

历史R52继续创建6个实质Java文件：NativeRecordCodec、OwnedDataCatalog、BookmarkStore、HistoryStore、SettingsRepository、SubapplicationCoordinator，另联动4个既有接口；Java114→120、PLAN66→61。真实实现固定purpose3/4/5的现有私有记录AEAD/CAS、书签树有界CRUD、历史保留/暂停与显式过期、四搜索引擎/双内置子应用desired设置，以及一次性同步原生交接fence；启动器只懒建规范owner，不读取默认值、不创建密钥/目录、不自动保存/恢复。实际120源码/613class/1034HOST，58纯切片552HOST＋SDKbootclasspath，新增49项模型/codec/真实HOST文件JCA/CAS回归。没有把desired-disabled/回调返回当实际停止；真实Gecko历史捕获、私密会话排除、Android IO/组件执行和管理员持久head适配尚未完成。详见[契约](../planning/geckoview-v2/records/native-browser-metadata-contract.json)。

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
