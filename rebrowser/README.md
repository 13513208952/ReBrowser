# ReBrowser v2 主程序

独立 Android 项目，入口 `io.github.rebrowser.ReBrowserApplication`；主代码仍位于总树的 [`app/src/main/java/io/github/rebrowser`](../app/src/main/java/io/github/rebrowser)。不引入旧 `io.github.reoutlook` / WebView 运行实现或数据迁移；既有产品体验和已批准的界面资源不是应当丢弃的旧实现。

## 当前所有者进度检查点：R71 尚未完成整轮真机收尾

[远端分析入口与未完成项](../planning/geckoview-v2/CURRENT_CHECKPOINT.md)。当前代码1468HOST／163测试类、170主Java实际SDK934class／22资源，已有开发包；R71恢复物理DPI七英寸设备分类及自动／手机／平板呈现纠正、显式RBSETT05及互不夹带保存。真机已完成若干实际策略／同DOM-Cookie／常规／冷启阶段，但最终默认恢复和回归尚未跑完，**不写成整轮通过**。[当前精确源码检查点](../planning/geckoview-v2/evidence/native-device-dpi-R71-checkpoint.json)；最近完整执行的限定轮次仍是下文R70，旧证据未改。设备呈现纠正不是信任、白名单或管理员身份。

## R70自身窗口方向操作（历史完整限定轮次）

已恢复旧版菜单的点击锁定相反横／竖屏、长按解除锁定，使用真实配置判断当前方向；仅请求本应用窗口，**不写系统旋转设置**。方向通过同一 canonical IO、原配置窄 CAS 和独立精确回读后交给原窗口。已有 RBSETT03 读取、普通常规编辑保持原字节／格式，只有明确方向意图写 RBSETT04；常规与方向写入均不能夹带对方或子应用状态。

真机实际横屏／竖屏往返，原网页“已启用离页保护”的 DOM 保留、无额外 HTTP 重载，Cookie 未丢；重绑提示可取消／继续。横屏菜单裁切已改为原项目原顺序滚动可达，实际两次常规主页保存仍保留横屏，再还原主页。长按实测自身窗口 UNSPECIFIED，保存横屏后强停冷启仍实际横屏，最后经菜单恢复 PORTRAIT。常规无改动返回密文相同；表单重发、连续提示、全屏、收藏回归通过。1452HOST／161类、170主Java实际SDK931class／22资源，见[R70限定证据](../planning/geckoview-v2/evidence/native-window-direction-R70-limited.json)。这是方向偏好持久化，**不是临时 Profile／页面冷恢复**；平板／小窗／反向传感器、视频全屏独立方向及 PiP 尚未真机验收。

## R69网页离开／表单重发确认（历史检查）

真实 SDK `beforeunload` 与 POST 重发确认已接入同一原会话／来源／弱原生窗口、一次决定与实际弹窗关闭／焦点回收链路。取消、Back 或真正停止到后台均为负向响应；只有原窗口中的明确确认才允许引擎继续。SDK 的 `confirm(null)` 实际表示允许，代码不用它取消。

真机验证取消／Back 留在原已设离页保护的网页、确认后实际显示目标 B；固定假表单 POST 的取消／Back／真正后台停止均不产生新 POST，明确确认后服务端计数才从4到5。首轮复现了菜单关闭返回后过早执行刷新导致后续确认不显示，现等待原菜单实际关闭及窗口焦点再一次派发，已完整重跑；测试也改为观察 Activity 真正 STOPPED，而非把 Home 按键返回当作已停止。设置密文／角色、收藏、连续提示和全屏回归通过。R69当时1439HOST／159类、170主Java实际SDK930class／21资源，见[R69限定证据](../planning/geckoview-v2/evidence/native-form-navigation-R69-limited.json)。未使用真实交易／凭据；**关闭标签的未保存表单保护、持久表单恢复及完整浏览器仍未完成**。

## R68系统默认浏览器入口（历史检查）

常规设置现接入本应用 UID 的实际 `RoleManager` 只读观察和“前往系统选择默认浏览器”。原生按钮受原窗口 Binding、来源、焦点和一次尝试约束；仅从系统查询选择已启用且导出的 SYSTEM 默认应用页面，显式组件、独立任务，不把第三方／合成 Resolver 当系统页。只新增只读路由，四项常规偏好及存储格式未变；页面返回或调用成功均不代表已获角色。

已安装开发包真机观察到实际 PermissionController 默认应用页面，任务与浏览器分开；未对系统页面做手势、界面抓取或默认项选择，只返回本应用。前后及强停冷启均实测“不是默认浏览器”；未保存的主页草稿保留并还原，canonical 设置密文始终相同。外部完整 URL、收藏遮挡／返回、连续原生确认、网页全屏／Back 回归通过。R68当时1436HOST／158测试类、170主Java实际SDK929class／21资源，见[R68限定证据](../planning/geckoview-v2/evidence/native-default-browser-R68-limited.json)。**未更改系统默认项**；角色实际授予、全部 OEM、物理旋转、完整权限／下载／持久环境仍未验收。

## R67外部HTTP(S)网页入口（历史检查）

外部VIEW链接已接入独立来源闸门后的真实原生窗口：只读取有界action／HTTP(S)数据，不把caller extras、selector、ClipData、URI grants或伪造私有action当管理员／来源／目标账户权限。每次新建隔离临时环境；首次冷入口只使用刚由本机创建的初始环境，不复用既有登录子页。私有原生非配置数据锁保存“一次尝试”状态，不从Intent／Bundle重建权限，后台重开不会自动再发。

真机完成首次冷VIEW完整query／fragment、暖入口原环境有Cookie而新环境无Cookie、非法scheme／凭据URL无导航或新环境、设置页之上的原主窗口交接与单实例、伪造额外字段／私有action仅作网址数据、后台不重放及收藏／提示回归。修正了新建重复窗口先重排再结束会让Android选中自身的时序错误；现在先结束重复窗口再重排原窗口，保留设置Activity。R67当时1428HOST／157类、170主Java实际SDK928class／21资源，精确绑定已安装开发包，见[R67限定证据](../planning/geckoview-v2/evidence/native-external-web-R67-limited.json)。安装后的HTTP(S)候选入口已解析，但**没有改系统默认浏览器或宣称默认设置页完成**；物理旋转、真实来源丢失等仍未因此验收。

## R66原生选择与剪贴板（历史检查）

已接入窗口拥有的SDK浮动选择菜单，实际复制／剪切／粘贴走规范一次性原SDK选择＋原生菜单动作，不直接读Android剪贴板。来源／导航／私有面板及窗口解绑撤销旧报价；返回等原ActionMode实际关闭，另向原选择发送负向取消。真机修正了“只关工具条却未退出选择”的问题，32K选择／30秒报价是显式界限，过期只负向撤销，不冒充后台任务结束。

已用全新固定测试文本完成原生浮动复制→粘贴、剪切→粘贴、原页面选择返回、30秒过期、后台菜单撤销／新绑定，以及提示／全屏／规范收藏回归。**没有宣称网页Clipboard API全面阻断**：固定Gecko允许同来源读回自身刚复制的文本快路，另一个实际来源的请求被拒绝，有限授权政策仍待完成。后台DOM选区也可能保留，不能把旧菜单撤销冒充数据抹除。R66当时1422HOST／156类、170主Java实际SDK926class／21资源，精确绑定已安装开发APK，见[R66限定证据](../planning/geckoview-v2/evidence/native-selection-clipboard-R66-limited.json)。外部文字处理、其他系统权限／多机型／丰富剪贴板与持久环境继续。

## R65标准网页提示（历史检查）

标准JS alert／confirm／文本提示已接入实际所选Gecko会话和弱原生窗口。提示明确标注“网页提示（非系统授权）”；只响应原SDK请求，后台／来源／窗口失效不换路确认，导航事件仅撤销旧请求、不授予文档权限。界面实际关闭与请求决定分开记账，确认后还等原窗口真实恢复焦点再向网页发布一次性响应。

真机发现连续两个confirm的第二个被过早恢复的JS抢到旧对话框焦点而取消，已保留失败并修正／重测：alert继续、confirm正反、文本实际输入／null取消、连续两次确认、模态返回不导航、过长拒绝、后台取消及重开新绑定均通过；全屏里第一次返回只关提示、第二次才退全屏。R65当时1419HOST／155类、170主Java实际SDK927class／21资源，精确绑定已安装开发包，见[R65限定证据](../planning/geckoview-v2/evidence/native-standard-prompts-R65-limited.json)。8K消息／4K输入及每会话128原请求登记是显式界限，不冒称无限规模。文件、密码认证、其他提示／有限权限和持久环境仍待接通；网页确认不是系统、管理员或机主授权。

## R64原生网页全屏与返回（历史检查）

已把真实所选Gecko会话全屏回调接入本窗口顶栏／系统栏显示。全局返回向**原实际会话**发送退出，再等该会话新的原生`fullScreen(false)`观测；不把SDK返回、进度或退休状态冒充退出。网页自行退出会恢复界面，后台／重开按原生当前状态恢复；不关闭会话或更换Profile。

一加Android15静态页面实测进入、返回退出且不导航、DOM自行退出、后台保留全屏后重开／返回、之后规范收藏和回到网页。实际视口由`[0,282][1080,2364]`扩大到`[0,96][1080,2412]`并恢复，保留缺口安全边距。R64当时1411HOST／154类、170主Java实际SDK924class／21资源，精确对应已安装开发APK，见[R64限定证据](../planning/geckoview-v2/evidence/native-web-fullscreen-R64-limited.json)。**视频媒体／全局及视频方向策略、PiP、平板／多窗口／物理旋转和真实来源丢失仍未因此验收**；没有修改系统全局方向。

## R63启动边界与持久环境推进（历史检查）

R63当时1406HOST／153类、170主Java实际SDK924class、21项资源，对应已安装arm64开发包。检查持久环境所需真实API时，发现固定Gecko在可调试应用中默认会读取外部启动YAML并合并参数／偏好；已明确关闭该默认路径和console／devtools，在实际runtime设置回读符合后才发布。实际冷启动Gecko网页与规范收藏／返回仍可用；没有创建外部注入文件。见[R63限定证据](../planning/geckoview-v2/evidence/native-runtime-startup-R63-limited.json)。

持久环境仍未冒充完成：当前私有会话没有公开无损转常规模式API；按context清理无完成回执，SessionState字符串／Parcel会先整体物化，不能事后截断冒称有界。继续按[真实能力与准入条件](../planning/geckoview-v2/records/native-persistent-browser-admission-status.json)处理，不能为保存而切换Profile或丢失登录。下列R62是已实现的URL收藏业务范围，与整个环境书签不同。

## R62全局网址收藏

菜单星标、收藏夹列表、标题／完整网址编辑、取消／删除及原子页打开已接通同一规范加密存储与单IO通道；写入关闭并精确回读后才反馈保存。按原记录版本、ID和网址重新核对，来源或原窗口改变不换路；重复添加不写盘，非法scheme保存和取消也不改变原密文。已移除错误的未启用文件夹树：**全局URL收藏不等于副级整个环境书签／收起恢复**，最近访问仍按完整URL去重。

一加Android15实际完成星标增删、完整query编辑／真实Gecko打开、冷启动保留、新总标签中的全局收藏与Cookie隔离、重复／非法／取消不写盘、确认删除。真机发现编辑错误浮层遮挡按钮、原生概览下底层网页仍可检查的问题，已改为行内错误及实际隐藏页面显示父层并重测；不关闭后台会话。当前1404HOST／152类、170主Java实际SDK924class、21项资源，源／清单精确对应已安装arm64开发APK，见[R62限定证据](../planning/geckoview-v2/evidence/native-url-favorites-R62-limited.json)。临时工厂8活会话、持久Profile／环境层级／收起恢复、完整权限与下载等继续，正式签名／登记／最高危认证仍待完成。

以下各轮为当时的历史范围与证据，旧“未接通／未安装”不覆盖上面的当前实测，也不修改旧源码哈希来冒充重新准入。

## R61已显示页面的访问记录

已修正R60发现的“等待整个页面加载完成才记录”缺口：同一原生会话的实际内容绘制＋顶层位置可记录已显示访问，成功加载仍作为未绘制后台页的补充；不把绘制当完整渲染、任务结束或文档权限。受控本机页面刻意保持HTTP正文未结束时，历史已出现该实际网址；离开中断后保留访问，302只记最终地址，绘制前TLS错误不记。1404HOST／170主Java实际SDK923class，R61当时开发APK与源／清单精确绑定，见[R61限定证据](../planning/geckoview-v2/evidence/native-visible-history-R61-limited.json)。其余主程序功能继续，不因此宣称历史或全产品已全面验收。

## R60主页按钮与历史打开

按机主明确许可，仅把左上主页文字字形换为24dp圆角线条矢量／原生图像按钮；原46×50dp点击区域、位置、配置主页路由及其他布局不变。照片下白色、普通网页下深色，使用原生触摸反馈；这是新绘制资源，不冒称旧版原样复制。

历史标题／网址点击已接通：在同一规范加密存储重新核对原条目ID、URL及期限，经原生私有一次性回执回到**原主窗口和原子标签**；来源、目标改变或回执过期不换路打开。真实本机静态页A→B→历史A已完成导航，配置主页和两种图标颜色也已限定真机检查。1396HOST／152类、170主Java实际SDK923class／20资源，开发APK与源快照绑定，见[R60证据](../planning/geckoview-v2/evidence/native-home-history-open-R60-limited.json)。

R60当时发现一项历史语义缺口：临时Cookie生产者等待原生加载完成，快速离开已显示但未完成加载的页面可能漏记；此项已由上节R61限定修正，旧检查保持历史范围。其余主程序及子应用缺口仍按下文推进，不以图标或历史点击作为整体完成。

## R59设置闭环（仍非完整设置）

设置入口已恢复为批准的 **常规／隔离与权限／应用管理** 三同级页，常规页的主页、搜索引擎、新标签背景、最近访问四项走同一个规范加密存储和单IO通道。草稿跨页及后台／重开保持，保存前重读原私有记录版本；领域写入还独立拒绝夹带子应用开关／错误版本。仅完整写入、关闭和精确回读后返回主窗口。实际保存已改变主界面的默认品牌、背景／最近栏以及主页和新子页导航，随后也通过真实设置恢复了批准默认值。无效主页禁用保存；回到原选项不写磁盘、不保留隐藏旧选择。

限定一加Android15实测包含来源接受后的设置读取／保存、应用切换草稿保留、只保留一个主窗口和设置窗口。真实Android发现Spinner禁止传入空item-click监听器的清理崩溃，按实际控件修正后重测通过；HOST测试不是View行为保证。R59当时1390HOST／151类，170主Java实际SDK Java8编译920class，19项批准资源链接，与当前已安装开发APK源码／清单精确匹配。见[R59限定证据](../planning/geckoview-v2/evidence/native-general-settings-R59-limited.json)。

权限页及应用管理仍是明确的状态入口，完整常规策略项、有限授权／原生提示、实际子应用管理等还要继续闭合；未以可点击开关假装后端完成。旋转实现有私有原生保留状态／epoch检查，实际物理旋转尚未验证。

## R58主程序演示（限定Android15实测）

已在机主连接的一加Android15设备安装独立 `.development`／arm64开发演示包。不是只展示界面：实际完成冷首次目录／安装密钥清点、原生确认、新epoch／AndroidKeystore安装及包装密钥、三份初始加密记录写入／回读，随后提交来源基线，再由独立正常来源检查接受。未知、非空或有旧密钥的状态不会被当作首次安装；失败不重置、覆盖或自动重试。AndroidX编译状态24字节标记经固定依赖字节码与实际包更新时间核验，非放行任意缓存／私有文件。

真实Gecko网页、DOM链接与原生后退、主页／总子标签区别、同总标签两子页共享Cookie与不同总标签隔离、国内百度和`cn.bing.com`、规范加密历史读写、暂停不记录／恢复只同步实际当前页、维持期间禁止单次清理已实际执行。临时Cookie环境下以原生顶层位置＋加载结果记录应用历史，不把Gecko全局访问回调／私有SessionState假定为总能使用；错误访问和重定向源不作为成功访问。加载失败保留请求网址及原会话，原生错误层不伪报空白成功；没有证书绕过。

**仍非完整浏览器／v2发行**：临时工厂8活会话、持久环境恢复、Popup、多选、完整权限／提示、历史OPEN、收藏、三同级设置、下载等仍待闭合，ReMusic／ReOutlook随后依次推进。正式包签名／正式设备名单／最高危真实机主认证也不因开发包生成密钥而完成。手机无代理；只测国内站点，不分享电脑外网。本机USB静态测试页无任何上游代理／互联网转发。

最新有限源检查：1379HOST／150测试类，170主Java实际SDK37.2／Gecko／生成R类以Java8编译为915class；Native检查仅此Android15的明确路径，不是所有版本／机型／断电／恢复／整文件准入。见[当前限定证据](../planning/geckoview-v2/evidence/native-first-setup-R58-limited.json)。没有新提交或推送。

## R57主程序接线（历史检查时未安装Demo）

`BrowserActivity`现在确实消费同一浏览器域、新标签页原生数据绑定和GeckoView／SessionDisplayBinding，不再只是开发状态展示。已接入原有单行顶栏、全窗背景与白色前景、地址导航、实际主页配置、新建总／子标签、双层概览和临时环境切换；原生会话观测经弱窗口订阅合并刷新，不用网页轮询，不把显示URI当文档授权。返回等待原会话新的位置观测，不把SDK调用返回称作已返回；会话退出也不会自动重开或堵住其他标签的目录操作。

这些路径仍只在独立来源检查已接受时启用。**新安装的首次初始化／密钥与初始记录创建尚未接通，所以目前仍不能给出可操作的已安装Demo**；没有为展示效果绕过来源闸门。当前原生工厂仅临时环境／8个活会话；持久恢复、Popup、多选、完整权限、历史OPEN、收藏和三同级设置等后续逐项闭合。概览列表也尚不是全部批准卡片外观的最终恢复。

实际有限检查：1338项HOST／146测试类通过（R57新增22项），170主Java以Java8语言级和实际SDK37.2／固定Gecko／生成R类编译为908class；18项资源实际链接，仍无Android／官网运行资格。见[源绑定检查](../planning/geckoview-v2/evidence/native-browser-window-R57-host.json)。

## 推进顺序与管理员配置

先打通ReBrowser主程序，再依次完成ReMusic、ReOutlook；相关管理员控制调试能力随对应真实业务服务一起实现，不再独立扩展脱离产品的框架。

机主已授权生成初始Root，公钥已固定编入身份表；私钥加密留仓库外，离线备份交接仍待完成。R56时唯一连接设备已被动识别，但尚未安装正式v2，缺少真实安装公钥，仍是待登记候选而不是已激活名单。名单不是全局准入条件：最高危操作无名单例外时，必须保留有效管理员密钥＋真实机主授权路径。公钥配置不等于来源接受、机主认证或业务解锁，详见[密钥与设备登记](../docs/ADMINISTRATOR_PROVISIONING.md)。R56时实际1316项HOST及全部170主Java实际SDK编译通过，当阶段未安装、启动或唤醒设备。R58在后来连接的一加上仅生成开发UID的真实安装密钥，不是正式名单登记；开发APK签名也是既有一次性实验签名，独立于管理员Root。

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


## R53基础记录（历史范围，当前源码以上文R71检查点为准，完整限定实测范围以R70及各历史报告为准）

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
