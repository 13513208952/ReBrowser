# 应用本体目标代码总树 · R10首版

> O27范围：只逐文件展开应用自身运行代码。测试/验证/构建/维护/文档不混入主树；在树后按关联概括并引用目标位置。
> 总树同时区分规划节点和已准入源码。`[HOST]`仅指该文件已编写并通过所列主机测试，不是Android集成/产品完成；`[?]`仍保留未决运行证据。`[API/WIP]`为Android绑定源码实现中，可能已有合成HOST子集，不等于仅有编译或完整文件验证；逐项看准入记录。固定Gecko隔离样机的局部Android结果不自动覆盖产品文件。

当前已创建本体源码：**39文件**；其中HOST测试切片 **25文件**，API编译/实现中 **14文件**；其余 **139文件**仍为规划。依据：[逐文件实现记录](records/implementation-readiness.json)。

当前目标：`io.github.rebrowser` · 本体代码 **178文件** · 18职责分组。源码增加/更名必须先改权威清单，再重新生成；不能用省略号隐藏源码。

符号：`[J]`原生代码，`[S]`运行脚本/HTML，`[C]`运行样式，`[Q]`运行SQL，`[?]`需要Android/网站实证；`↪`为树外引用，不算本体代码叶节点。

## 本体代码树

```text
└── app/
    └── src/
        └── main/
            ├── assets/
            │   ├── bridge/
            │   │   ├── background.js [S][PLAN][?] — 固定内建扩展后台和Native路由
            │   │   ├── content/
            │   │   │   ├── music.js [S][PLAN][?] — 隔离世界音乐页面握手与文档路由
            │   │   │   └── outlook.js [S][PLAN][?] — 隔离世界邮箱握手/账户/DTO流
            │   │   ├── page/
            │   │   │   ├── music-api.js [S][PLAN][?] — 官网MAIN世界受控SDK命令和真实回执
            │   │   │   └── outlook-api.js [S][PLAN][?] — 官网MAIN世界只读账号/邮件采集与明确用户动作
            │   │   ├── protocol.js [S][PLAN][?] — 严格消息/序号/信用/版本
            │   │   └── styles/
            │   │       ├── music-desktop.css [C][PLAN][?] — 完整平板横屏/显式桌面扫码的边界
            │   │       ├── music-phone.css [C][PLAN][?] — 批准手机布局适配，不重建播放器
            │   │       └── music-window.css [C][PLAN][?] — 非完整平板窗口原约定适配
            │   ├── offline-mail/
            │   │   ├── reader.css [C][PLAN][?] — 离线正文可读排版
            │   │   ├── reader.html [S][PLAN][?] — 只读离线安全阅读文档
            │   │   └── reader.js [S][PLAN][?] — 无脚本邮件执行的本机资源交互
            │   └── sql/
            │       ├── browser-v1.sql [Q][PLAN][?] — 环境/标签/历史/书签原生库
            │       ├── control-v1.sql [Q][PLAN][?] — nonce/任务/幂等/审计元数据原生库
            │       └── mail-v1.sql [Q][PLAN][?] — 新账户实体/引用/修订/事务约束
            └── java/
                └── io/
                    └── github/
                        └── rebrowser/
                            ├── ReBrowserApplication.java [J][API/WIP][?] — 只在正确进程建立应用所有者，不启动被来源闸门锁住的业务
                            ├── authority/
                            │   ├── AdminAudit.java [J][PLAN][?] — 脱敏审计与受控私有结果
                            │   ├── AdminProofVerifier.java [J][HOST][?] — 来源中立私钥证明，不以辅助包签名额外否决
                            │   ├── AuthorityPolicy.java [J][PLAN][?] — 同作用域>=、通道/介入floor、机主授权激活
                            │   ├── AuthorityScale.java [J][HOST][?] — H/普通最高/Root与不可变风险下限
                            │   ├── CompiledPrincipals.java [J][HOST][?] — 预编译全部高级身份与角色
                            │   ├── DeviceWhitelist.java [J][PLAN][?] — Root联合/各最高自有/专有名单隔离
                            │   ├── DisableLedger.java [J][PLAN][?] — 更高禁更低、Root不可禁、恢复和签名快照
                            │   ├── EnrollmentProposal.java [J][PLAN][?] — 仅已明确设备/持钥证据的编译名单提案
                            │   ├── LowDelegationVerifier.java [J][PLAN][?] — 低于H的有界委托链和撤销
                            │   ├── OwnerGrantStore.java [J][PLAN][?] — 精确范围/期限/epoch的本人授权
                            │   └── TargetPossessionProof.java [J][PLAN][?] — 实际目标安装私钥challenge验证
                            ├── bootstrap/
                            │   ├── ComponentGate.java [J][HOST][?] — Activity/Provider/Service/后台入口共同闸门
                            │   ├── ProcessRole.java [J][HOST][?] — 识别主/Gecko内部子进程，避免每进程重复初始化
                            │   └── StartupCoordinator.java [J][API/WIP][?] — 来源检查→原生壳→按需内核的启动顺序
                            ├── bridge/
                            │   ├── BridgeBudget.java [J][PLAN][?] — 64KiB信封/32KiB原始块和有界信用
                            │   ├── BridgeMessages.java [J][PLAN][?] — HELLO/READY/命令/结果/观测/错误类型
                            │   ├── BridgeStreamReceiver.java [J][PLAN][?] — 分块/长度/摘要/临时对象提交
                            │   ├── BuiltinConnectorManager.java [J][PLAN][?] — 固定APK扩展安装/版本/注册先于业务数据泵
                            │   ├── ConnectorBroker.java [J][PLAN][?] — 真实sender/session/port路由与允许动作
                            │   ├── DocumentLease.java [J][PLAN][?] — 页面/port/epoch代次握手与失效
                            │   ├── MusicConnector.java [J][PLAN][?] — 官方同context账号/业务SDK类型化命令
                            │   └── OutlookConnector.java [J][PLAN][?] — 官方邮箱账号/邮件DTO校验与有界流
                            ├── browser/
                            │   ├── BackCoordinator.java [J][HOST][?] — IME/模态/全屏/浮层/子屏/Popup/history/环境单次返回
                            │   ├── BookmarkStore.java [J][PLAN][?] — 收藏/书签/搜索设置
                            │   ├── BrowserCoordinator.java [J][API/WIP][?] — 环境/标签/导航/原生页面共同领域动作
                            │   ├── BrowserModels.java [J][HOST][?] — 浏览器句柄/状态/不可变快照
                            │   ├── BrowserStore.java [J][PLAN][?] — 新v2原生环境/标签持久化，非v1迁移
                            │   ├── ExternalNavigation.java [J][API/WIP][?] — 拒绝无效/userinfo/控制字符URL，重建私有Intent丢外部extras/selector/ClipData，合法链接入新临时隔离环境
                            │   ├── HistoryStore.java [J][PLAN][?] — 近期访问/期限/维持/暂停写入
                            │   ├── SearchEngines.java [J][HOST][?] — 原生搜索提供者与明确目标加载
                            │   ├── TabCatalog.java [J][HOST][?] — 环境子标签/选择/父Popup/关闭
                            │   └── WorkspaceCatalog.java [J][HOST][?] — 双层环境类型/保留策略/名称
                            ├── continuity/
                            │   ├── CompiledSourceIdentity.java [J][HOST][?] — APK编译根公钥/策略身份
                            │   ├── ContinuityCoordinator.java [J][HOST][?] — 四步/抢救/销毁前锁定/提交新epoch的状态归约
                            │   ├── ContinuityState.java [J][HOST][?] — 来源与确认步骤、失败和不可逆点数据类型
                            │   ├── EpochCommitter.java [J][PLAN][?] — 确认清空后原子提交新epoch/source
                            │   ├── EpochErasureCoordinator.java [J][PLAN][?] — 专项旧私有数据销毁/核对，不是搁置的子应用清理
                            │   ├── LastGoodSourceStore.java [J][HOST][?] — 最后正常来源与数据epoch记录，异常包不覆盖
                            │   ├── OwnerAuthentication.java [J][API/WIP][?] — 每步新Android验证及确无锁判断
                            │   ├── PrivateEpochManifest.java [J][PLAN][?] — 仅v2自有旧epoch文件/密钥归属清单
                            │   └── SigningIdentity.java [J][API/WIP][?] — APK真实签名锚与合法轮换验证
                            ├── control/
                            │   ├── ActionRegistry.java [J][PLAN][?] — 178逐动作定义/处理器/门槛单一注册
                            │   ├── ActionSchemas.java [J][HOST][?] — 类型/范围/长度/目标/副作用参数
                            │   ├── ControlChallengeStore.java [J][HOST][?] — 目标/epoch/服务实例/策略revision/动作参数绑定的30秒monotonic挑战；并发一次消费在授权后原子准入
                            │   ├── ControlDecoder.java [J][HOST][?] — 严格外包络/重复键/Unicode/Base64；不可变参数JSON语法树，不等于ActionSchemas/验签/准入
                            │   ├── ControlDispatcher.java [J][PLAN][?] — 授权→同领域服务→真实结果，不复制特权路径
                            │   ├── ControlEnvelope.java [J][HOST][?] — 17个长度前缀签名字节字段/固定域版本/目标和原参数，明确DER签名与外包络边界
                            │   ├── ControlResultStream.java [J][PLAN][?] — 私有大结果有界传输
                            │   ├── IdempotencyStore.java [J][API/WIP][?] — 同principal/target/epoch/requestId同内容用新证明返回原任务，不同内容冲突；旧nonce重放不再授权
                            │   └── PrivateDataController.java [J][PLAN][?] — 受授权应用自有对象检查/导出/重置，不访问无权名单；非子应用清理功能
                            ├── core/
                            │   ├── ActorContext.java [J][PLAN][?] — 正常用户/系统媒体/已验证管理员的明确身份
                            │   ├── Cancellation.java [J][HOST][?] — 请求取消和真实停止证据
                            │   ├── DomainResult.java [J][PLAN][?] — 结果/错误/等待条件/证据等级
                            │   ├── ResourceBudgets.java [J][PLAN][?] — 队列/块/缓存/优先级和低内存预算
                            │   ├── TargetReferences.java [J][PLAN][?] — 原生目标句柄与epoch/partition/session绑定
                            │   ├── TaskCoordinator.java [J][PLAN][?] — auth/effect/read/safety有界通道及真实工作集
                            │   ├── TaskJournal.java [J][API/WIP][?] — 副作用/幂等/持久化阶段日志
                            │   └── TaskSnapshot.java [J][HOST][?] — 客户端等待与实际执行/效果/持久化分轴
                            ├── crypto/
                            │   ├── CryptoEncoding.java [J][HOST][?] — 严格SPKI/DER/长度和摘要编码
                            │   ├── DataKeyVault.java [J][PLAN][?] — epoch包装密钥/账户用途DEK与版本
                            │   ├── InstallKeyStore.java [J][PLAN][?] — 安装设备EC密钥持有与目标challenge，不用序列号作凭据
                            │   ├── KeyPurpose.java [J][PLAN][?] — 发行/管理员/设备/数据密钥域定义
                            │   ├── ObjectAead.java [J][HOST][?] — HKDF派生不可变对象AES-GCM分帧
                            │   └── PrivateRecordSealer.java [J][PLAN][?] — 原生敏感快照/策略记录封装
                            ├── data/
                            │   └── OwnedDataCatalog.java [J][PLAN][?] — 来源保护/私有对象访问共享的精确v2拥有集合，不接任意文件路径
                            ├── download/
                            │   ├── DownloadCoordinator.java [J][PLAN][?] — 真实WebResponse流/用户批准/取消/发布
                            │   ├── DownloadHandoff.java [J][PLAN][?] — 明确系统安装器/文件打开，非自动安装
                            │   ├── DownloadStore.java [J][PLAN][?] — 任务/元数据/保存目标的私有记录
                            │   ├── ResponseStreamWorker.java [J][HOST][?] — 有界IO/EOF/关闭流/失败/临时对象
                            │   └── SaveTargetRegistry.java [J][PLAN][?] — 原生拥有或用户授权URI，拒绝任意私有路径
                            ├── engine/
                            │   ├── ContentDelegate.java [J][PLAN][?] — 崩溃/终止/全屏/外部response
                            │   ├── DiagnosticCoordinator.java [J][PLAN][?] — 作用域内页面/网络/会话诊断和renderer脚本，不开无认证全局调试端口
                            │   ├── GeckoHistoryDelegate.java [J][PLAN][?] — 已提交历史与认证瞬态访问区分
                            │   ├── GeckoMediaBridge.java [J][PLAN][?] — 实际媒体激活/feature/状态与秒单位
                            │   ├── GeckoRuntimeOwner.java [J][API/WIP][?] — 主进程唯一runtime，原生子进程由SDK管理
                            │   ├── GeckoSessionOwner.java [J][API/WIP][?] — session强引用/创建/关闭与代次
                            │   ├── NavigationDelegate.java [J][PLAN][?] — 加载/外链/新窗与正确父来源
                            │   ├── PartitionCatalog.java [J][HOST][?] — 稳定非null context和环境/子应用归属
                            │   ├── PermissionDelegate.java [J][PLAN][?] — context+origin网站许可与OS许可分层
                            │   ├── ProgressDelegate.java [J][API/WIP][?] — 提交导航/加载/安全状态/快照事件
                            │   ├── PromptDelegate.java [J][PLAN][?] — 网站提示/文件选择的Native/系统等待
                            │   ├── SelectionDelegate.java [J][PLAN][?] — 网页选择/复制与用户动作
                            │   ├── SessionCheckpointStore.java [J][PLAN][?] — 异步sessionState与敏感快照提交/恢复
                            │   ├── SessionDisplayBinding.java [J][API/WIP][?] — View附着/分离/可见/focus与session生命分离
                            │   └── ViewportPolicy.java [J][PLAN][?] — 手机/完整平板/小窗与批准布局策略
                            ├── entrypoints/
                            │   ├── AdminControlActivity.java [J][API/WIP][?] — 应用内/隐藏控制台，隐藏不是授权
                            │   └── AdminControlProvider.java [J][PLAN][?] — 普通持钥辅助包/ADB的公开受认证Provider入口
                            ├── lyrics/
                            │   ├── LyricOverlayController.java [J][PLAN][?] — 关闭/可拖动/锁定穿透的唯一状态
                            │   ├── LyricOverlayWindow.java [J][PLAN][?] — 实际浮层视图/触摸/穿透
                            │   ├── LyricPositionStore.java [J][PLAN][?] — 位置恢复与窗口边界
                            │   └── OverlayPermissionFlow.java [J][PLAN][?] — 系统浮层权限请求和结果
                            ├── music/
                            │   ├── MusicActivity.java [J][PLAN][?] — 批准布局及同一个真实播放器session显示
                            │   ├── MusicArtwork.java [J][PLAN][?] — 最新请求/有界像素解码/缓存
                            │   ├── MusicAudioFocus.java [J][PLAN][?] — 有效焦点租约，pause后gain不复活
                            │   ├── MusicAuthentication.java [J][PLAN][?] — 正常手机UA/fresh官方检查/未知不等于退出
                            │   ├── MusicCoordinator.java [J][PLAN][?] — 认证/意图/唯一执行路线/资源/真状态归约
                            │   ├── MusicLyrics.java [J][PLAN][?] — 官方歌词有界读取/逐字时间映射
                            │   ├── MusicMediaSession.java [J][PLAN][?] — Android系统动作与Gecko/官网语义对照
                            │   ├── MusicNotification.java [J][PLAN][?] — 真实状态/可用动作，不伪造进度
                            │   ├── MusicPlaybackAuthorization.java [J][PLAN][?] — 冷启暂停、明确起播、旧意图失效
                            │   ├── MusicPreferences.java [J][PLAN][?] — 音源/恢复意图/位置的明确本机配置
                            │   ├── MusicQueue.java [J][PLAN][?] — 官网队列/喜欢/歌单域，不提取URL重建播放器
                            │   ├── MusicService.java [J][PLAN][?] — 合法前台媒体服务和Native任务/通知所有者
                            │   ├── MusicSessionOwner.java [J][PLAN][?] — UI与服务共享单个music context/session
                            │   └── MusicState.java [J][PLAN][?] — 账号/播放/资源/意图/焦点代次
                            ├── outlook/
                            │   ├── AttachmentCoordinator.java [J][PLAN][?] — 有界获取/取消/原生对象/明确保存
                            │   ├── MailAccountCatalog.java [J][PLAN][?] — 原生账号归属和绑定revision
                            │   ├── MailAuthCoordinator.java [J][PLAN][?] — fresh官网证据/网络未知/错误分类/恢复
                            │   ├── MailCaptureValidator.java [J][PLAN][?] — 真实官方来源/代次/账户/DTO/流校验
                            │   ├── MailDatabase.java [J][PLAN][?] — 执行新账户SQL/事务/约束，源闸门先于打开
                            │   ├── MailExportCoordinator.java [J][PLAN][?] — 明确范围/高风险授权的正文附件导出
                            │   ├── MailObjectCodec.java [J][HOST][?] — RAW/zlib与受约束解压、长度/摘要
                            │   ├── MailObjectFormat.java [J][PLAN][?] — 124B头/不可变对象/分帧AEAD
                            │   ├── MailObjectStore.java [J][PLAN][?] — 小payload内联/较大不可变文件/同账户去重
                            │   ├── MailReferenceReconciler.java [J][PLAN][?] — 内部引用/孤儿完整性回收，非子应用清理功能
                            │   ├── MailRetentionPolicy.java [J][PLAN][?] — 原始唯一内容与可重建缓存区分
                            │   ├── MailSearch.java [J][PLAN][?] — 分页元数据与可取消中文正文跨块检索
                            │   ├── MailSyncCoordinator.java [J][PLAN][?] — 账户有序批次/引用/游标同事务
                            │   ├── OfficialMailActions.java [J][PLAN][?] — 仅已授权官网发信/移动/删除等命令与真实回执；不与本机同步事务混淆
                            │   ├── OfficialMailEntry.java [J][PLAN][?] — 个人/工作学校/最近成功入口，禁止猜测账户域
                            │   ├── OfflineMailActivity.java [J][PLAN][?] — 原生离线列表/正文/搜索恢复位置
                            │   ├── OfflineMailRenderer.java [J][PLAN][?] — 隔离只读HTML，无脚本/邮箱Cookie/自动远程内容
                            │   ├── OutlookActivity.java [J][PLAN][?] — 官方邮箱显示和原生离线入口
                            │   └── OutlookSessionOwner.java [J][PLAN][?] — 同context邮箱/官方认证Popup生命
                            ├── permissions/
                            │   ├── ClipboardCoordinator.java [J][PLAN][?] — 网页单次原生输入信用/遮挡与代次取消，不由JS或持久grant生成；管理员证明与OS前台条件另行校验；Native clipboard read走SelectionActionDelegate，不把screenPoint当手势证明
                            │   ├── FilePickerCoordinator.java [J][PLAN][?] — 选择器/ContentResolver/拒绝与重建
                            │   ├── PendingPromptRegistry.java [J][PLAN][?] — session/document/request绑定且一次结果
                            │   ├── PermissionCoordinator.java [J][PLAN][?] — Native content/OS/media权限分层与真实prompt；Gecko永久权限缓存和有限native grant时限差异尚待验证，不用定时器冒充回收
                            │   └── SiteGrantStore.java [J][PLAN][?] — origin/partition/能力/期限记录
                            ├── settings/
                            │   ├── PersonalizationPolicy.java [J][PLAN][?] — 批准背景与原生外观资源选择/授权URI归属
                            │   └── SettingsRepository.java [J][PLAN][?] — 本机通用/背景/搜索/子设置读写，与Activity分离
                            ├── subapps/
                            │   ├── SubapplicationCoordinator.java [J][PLAN][?] — NativeUI和管理员共享启用/打开/关闭/子设置路由
                            │   └── SubapplicationRegistry.java [J][PLAN][?] — 固定子应用身份/启用/入口元数据，不包含已搁置容量/清理
                            ├── system/
                            │   ├── ApkHandoffActivity.java [J][PLAN][?] — 仅已授权本地.1/APK交接入口与Native生命周期，不转发外部管理员参数
                            │   ├── ApkHandoffCoordinator.java [J][PLAN][?] — UI/管理员共同的来源验证、受限stage与明确系统安装器交接；已交接不冒充安装完成
                            │   ├── ApkStagingProvider.java [J][PLAN][?] — 不导出只读随机单文件URI，核对授权/归属/期限/规范路径，绝不暴露业务资料
                            │   ├── ApkStagingStore.java [J][PLAN][?] — 仅自有随机APK暂存，输入流/队列/实际任务/空间/保留预算及取消结算；不清其他缓存
                            │   ├── DefaultBrowserFlow.java [J][PLAN][?] — 用户/已授权动作打开系统推荐默认应用页，已持有角色也可打开；只读角色不直接设置默认，不交第三方/合成Resolver
                            │   ├── DeviceWindowState.java [J][PLAN][?] — 窗口/平板/横竖屏事实，不以型号硬门控
                            │   ├── ForegroundPolicy.java [J][PLAN][?] — Android前台服务/通知许可按实际版本处理
                            │   ├── MemoryPressurePolicy.java [J][PLAN][?] — 仅本软件缓存/可恢复session回收，不动其他软件
                            │   └── PlatformResultRouter.java [J][PLAN][?] — 系统选择器/权限/安装器返回归属
                            └── ui/
                                ├── ApplicationsActivity.java [J][PLAN][?] — 子应用启用与48dp设置齿轮
                                ├── BookmarkView.java [J][PLAN][?] — 收藏和书签操作
                                ├── BrowserActivity.java [J][API/WIP][?] — 主窗口和Native/Gecko显示协调
                                ├── BrowserToolbar.java [J][PLAN][?] — 原生地址/返回/刷新/输入建议
                                ├── DownloadsActivity.java [J][PLAN][?] — 批准/取消/任务/文件交接
                                ├── DownloadsAdapter.java [J][PLAN][?] — 下载列表与实际阶段显示
                                ├── HistoryActivity.java [J][PLAN][?] — 原生历史查询/期限/清理入口
                                ├── HistoryAdapter.java [J][PLAN][?] — 有界近期/历史列表
                                ├── LoadingProgressView.java [J][PLAN][?] — Gecko会话/文档代际绑定的单调加载条、批准渐变、动画取消与解绑
                                ├── NativeDialogs.java [J][PLAN][?] — 取消/危险确认/平台等待，不默认为同意
                                ├── NewTabView.java [J][PLAN][?] — 批准背景/搜索/收藏布局
                                ├── OverviewSelection.java [J][PLAN][?] — 每窗口环境/子标签多选和面板返回清除状态，不冒充领域数据或跨窗口持久化
                                ├── SettingsActivity.java [J][PLAN][?] — 通用/权限/应用管理入口
                                ├── SettingsSections.java [J][PLAN][?] — Native设置项与结果绑定
                                ├── SitePermissionsActivity.java [J][PLAN][?] — 网站范围与系统许可状态
                                ├── SourceWarningActivity.java [J][PLAN][?] — 仅原生四步/救援，不创建锁定旧业务
                                ├── SubapplicationSettingsActivity.java [J][PLAN][?] — 子设置/音源/入口；占用清理按O26搁置
                                ├── TabPanel.java [J][PLAN][?] — 环境内标签选择/新建/关闭
                                ├── WindowPresentation.java [J][PLAN][?] — 状态栏/全屏/窗口布局，不改用户全局设置
                                └── WorkspacePanel.java [J][PLAN][?] — 环境列表和双层交互
```

## ↪ 测试需求与目标位置（辅助代码独立存放，不展开为主树）

| 职责 | 测试要求摘要 | 目标文件引用，实施/证据另看准入记录 |
| --- | --- | --- |
| 入口与进程 | 进程角色/幂等启动/所有组件先闸门/无系统WebView条件；API30新安装冷启动及Gecko子进程不重复建主runtime | `app/src/test/java/io/github/rebrowser/entry/EntryContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/entry/EntryRuntimeTest.java` |
| 来源连续性 | lastGood而非lastSeen、四步计数/取消、精确私有清单与原子epoch；合成A→异常B→正常C、版本/签名限制、两次新系统验证与无锁/取消；不清日常资料；actualunconfiguredRoot/strictfingerprints/noDynamicRegistrationAPI;3HOSTtestsnottrustunlock；4HOST publicTOYoctet hash/history/bounds/immutability;notcertificates/AndroidOS/privatekey custody；3HOST exact4O16warninghashes andForce2/3freshauthpositions;notOwnerAuthorTrust；3HOST epochcopy/steprestriction/freshchallenge/privateTicketconstructor, NOownerprompt/noLockproof/ticketmintconsume/controlflowexecution；17 actual HOST metadata IO/reopen/A-B-abort-A and explicit-empty-B, lock/CAS, corrupt/pending/symlink/directory identity rejection; synthetic fences, not owner authentication or erasure；24 HOST synthetic-native outcome cases, exact order/revision/window/context/clock/consent and retained unknown native work; not OS bridge/authentication；28 actual OwnerAuthentication controller/private-ticket/bridge cases using private reflected fake platform; no AndroidPlatform/OS auth/real signer calls | `app/src/test/java/io/github/rebrowser/continuity/ContinuityContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/continuity/ContinuityRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/CompiledSourceIdentityTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/SigningIdentityTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/ContinuityStateTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/OwnerAuthenticationBindingTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/LastGoodSourceStoreTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/ContinuityCoordinatorTest.java`<br>`app/src/test/java/io/github/rebrowser/continuity/OwnerAuthenticationControlTest.java` |
| 领域基础与任务 | 真实槽/安全通道/超时不释放/旧拥有者只结算/持久化前后fence；线程/进程死亡/背压/取消失败与长任务并发，实际资源上限；112phase/work/effect combinations,14valid; timeout/cancel/stale neverendwork; observed outcome/workend/durability independent；235HOST SQLite constraints/196transitionpairs/recovery/pairedtransaction rollback,reopen/2writeronce;NOTAndroidDAO | `app/src/test/java/io/github/rebrowser/core/CoreContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/core/CoreRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/core/TaskSnapshotTest.java`<br>`planning/geckoview-v2/experiments/task_journal_sql.py` |
| 本机加密与密钥 | 公开RFC/NIST向量、帧边界/篡改/截断/错epoch/account/object/key版本；Android JCA/Keystore生命周期、同包更新保留、无生物硬件单项退路；fresh one-shot immutable object IDs/catalog scope/RAW digest/GCM frame tamper/tainted late prefix/cancel/4MiB generated source bounded private staging; ZLIB bytes need separate codec verification | `app/src/test/java/io/github/rebrowser/crypto/CryptoContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/crypto/CryptoRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/crypto/ObjectAeadTest.java` |
| 管理员政策 | >=与H、静态高级、Root不可禁、撤销恢复、多名单所有权、同钥同身份；实际持钥IPC/目标设备证明/机主范围授权期限；不得包名来源否决 | `app/src/test/java/io/github/rebrowser/authority/AuthorityContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/authority/AuthorityRuntimeTest.java` |
| 统一控制入口与协议 | 严格字段/长度/签名字节/nonce/重复请求/未知副作用不重放；跨语言17字段帧/DER/P256/目标代次/新proof取旧job，签名变体不改身份或绕nonce；ADB/普通辅助包同协议，断线重连/大结果流/平台等待而非权限假报；跨语言17字段帧/DER/P256/目标代次/新proof取旧job，签名变体不改身份或绕nonce；外包络必填/未知/重复与类型，原字节/不可变树、代理摘要、深度/节点/数值预算、UTF8/surrogate/数字语法/不把UnverifiedProof当身份；不覆盖逐动作schema/准入；30s exact-frame/context/nonce, replay/expiry, authorize failure/unknown reservation burn,16concurrent/reentrant attempts,1MiB retained-frame cap/clock rollback; no actual effects/durable journal；fresh nonce/policy/grant same content identity; action/scope/raw args change conflicts, immutable digest/schema arrays; Android SQLite methods separately pending；fixed4actions/unknownfailclosed/strictrefs/exactsafeinteger/permissionpurpose/installerpair/rawargspreservation;NOTauthority orAndroidexecution | `app/src/test/java/io/github/rebrowser/control/ControlContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/control/ControlRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/control/ControlDecoderTest.java`<br>`app/src/test/java/io/github/rebrowser/control/ControlChallengeStoreTest.java`<br>`app/src/test/java/io/github/rebrowser/control/IdempotencyIdentityTest.java`<br>`app/src/test/java/io/github/rebrowser/control/ActionSchemasTest.java` |
| Gecko原生宿主 | partition与session身份/Popup父来源/快照revision/显示与关闭分离；实际context cookies隔离、NativePopup、崩溃/回收恢复、无WebView门控；6HOST actual owner-domain/context/epoch/fence/tombstone/capacity/gate/thread tests; notcookie/storage erasure/runtime | `app/src/test/java/io/github/rebrowser/engine/EngineContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/engine/EngineRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/engine/PartitionCatalogTest.java` |
| APK内建连接器 | 一HELLO/版本、原生sender绑定、序号/信用、旧代次与实际结算；Gecko内建权限、content/MAIN世界、SPA/BFCache、port断开与真实未完成任务 | `app/src/test/java/io/github/rebrowser/bridge/BridgeContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/bridge/BridgeRuntimeTest.java` |
| 浏览器领域与本机数据 | 双层环境/标签、搜索/收藏/历史期限维持、原生返回优先级；多环境切换/临时环境、官方认证新窗、旋转小窗/进程恢复不串区；14HOST actual primary/secondary/temp/shelf/pin/quota/tab/Popup/generation/Unicode/foreignhandle/thread tests, notGecko orBrowserStore；11HOST queries4providers/strictHTTP/userinfo/UTF16/UnicodeIDNpreservation/IPv6/ports/noSecretSearchFallback/URLbounds+fixedpoint;notGecko/DNS orOSIntent；12HOST real reducer/synthetic layer priority/revision/pending/unknown/stalepublication/thread/reentrancy/sourceLockedcancel, notAndroidactualBack | `app/src/test/java/io/github/rebrowser/browser/BrowserContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/browser/BrowserRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/browser/BrowserCatalogTest.java`<br>`app/src/test/java/io/github/rebrowser/browser/SearchEnginesTest.java`<br>`app/src/test/java/io/github/rebrowser/browser/BackCoordinatorTest.java` |
| 原生浏览器界面 | 每Native控件到领域动作、Back只消费一次、无障碍焦点/48dp齿轮；加载条按session/document代际单调/取消旧fade；每窗口多选与Back清除，旧ID不可影响新面板；手机/平板/完整与非完整窗口、IME/模态/全屏/列表恢复与批准外观对照；加载条按session/document代际单调/取消旧fade；每窗口多选与Back清除，旧ID不可影响新面板 | `app/src/test/java/io/github/rebrowser/ui/UiContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/ui/UiRuntimeTest.java` |
| 下载与目标文件 | 所有拒绝/失败路径close流、目标URI/文件名边界、事务发布；真实Gecko response流、断网/磁盘不足/系统选择器取消；不导Cookie重发；10 HOST bulk-only EOF, byte length/SHA, all close/error paths, tainted prefix, cancel and interrupt-ignoring worker; not Gecko runtime | `app/src/test/java/io/github/rebrowser/download/DownloadContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/download/DownloadRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/download/ResponseStreamWorkerTest.java` |
| 网站与系统许可 | origin+context范围、prompt一次结果、手势/文档代次与拒绝；Android许可/剪贴板前台资格、文件选择器/旋转/取消和API差异 | `app/src/test/java/io/github/rebrowser/permissions/PermissionsContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/permissions/PermissionsRuntimeTest.java` |
| Outlook与离线邮件 | 账户绑定/修订/SQL约束/引用/损坏/搜索跨块/导出范围/加密完整性；个人/工作学校官方流程与历史错误、同contextPopup、中文/HTML/离线读取与中断；无真实云端变更；RAW/zlib选择包含AEAD帧开销，输出/摘要/EOF/字典/资源/取消，prepare->seal/open->decode私有暂存全原文；非DB/官网/Android性能 | `app/src/test/java/io/github/rebrowser/outlook/OutlookContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/outlook/OutlookRuntimeTest.java`<br>`app/src/test/java/io/github/rebrowser/outlook/MailObjectCodecTest.java` |
| 官方音乐与后台 | fresh准入与安全停止分开、单路线/冷启暂停/seek单位、旧焦点gain失效；合成媒体事件后再明确授权官网验证、服务/通知/焦点/任务重连；不擅自播放 | `app/src/test/java/io/github/rebrowser/music/MusicContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/music/MusicRuntimeTest.java` |
| 桌面歌词 | 关闭/拖动/锁定穿透、位置边界、歌词时间与资源代次；系统浮层许可/锁屏/小窗/方向变化/触摸穿透及恢复 | `app/src/test/java/io/github/rebrowser/lyrics/LyricsContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/lyrics/LyricsRuntimeTest.java` |
| 平台集成 | 版本分支/系统等待分类、仅本软件内存回收、不得改全局用户设置；.1输入/自有provider反读拒绝、字节一致/真实IO预算/取消、系统安装器vs商店、只读grant/过期、交接不等于安装成功；API30+Role/安装器/前台服务/通知许可、无GMS/无生物/无相机局部退路；.1输入/自有provider反读拒绝、字节一致/真实IO预算/取消、系统安装器vs商店、只读grant/过期、交接不等于安装成功 | `app/src/test/java/io/github/rebrowser/system/SystemContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/system/SystemRuntimeTest.java` |
| 子应用启用与路由 | NativeUI和管理员同一设置/启用服务，幂等/来源闸门/取消/不创建无关网页；入口/设置恢复、Activity与领域生命分离，设备窗口变化和系统返回 | `app/src/test/java/io/github/rebrowser/subapps/SubappsContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/subapps/SubappsRuntimeTest.java` |
| 本机设置领域 | NativeUI和管理员同一设置/启用服务，幂等/来源闸门/取消/不创建无关网页；入口/设置恢复、Activity与领域生命分离，设备窗口变化和系统返回 | `app/src/test/java/io/github/rebrowser/settings/SettingsContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/settings/SettingsRuntimeTest.java` |

## ↪ 其他部分的概括引用（不作为源码分支展开）

| 关联职责 | 部分 | 位置/说明 |
| --- | --- | --- |
| 入口与进程 | runtime-declarations | 应用标识/Activity/Service/Provider/权限/备份边界<br>`app/src/main/AndroidManifest.xml`<br>`app/src/main/res/xml/backup_rules.xml`<br>`app/src/main/res/xml/data_extraction_rules.xml`<br>`app/src/main/res/xml/network_security_config.xml` |
| 管理员政策 | compiled-policy | APK不可变的管理员/设备公钥与动作政策；私钥不入包<br>`app/src/main/assets/policy/principals.json`<br>`app/src/main/assets/policy/devices.json`<br>`app/src/main/assets/policy/actions.json` |
| APK内建连接器 | builtin-extension-declaration | 扩展版本/host权限/固定连接器定义，非测试入口<br>`app/src/main/assets/bridge/manifest.json` |
| 原生浏览器界面 | resources | 图标/主题/文案/批准背景等资产独立清单，不当辅助源码展开<br>`app/src/main/res/`<br>`app/src/main/assets/background/` |
| 入口与进程 | build-tooling | 构建/依赖/正式R8/签名公共模板；独立辅助目录<br>`build.gradle.kts`<br>`settings.gradle.kts`<br>`gradle.properties`<br>`gradle/wrapper/gradle-wrapper.properties`<br>`app/build.gradle.kts`<br>`app/proguard-rules.pro`<br>`tools/build/` |
| 统一控制入口与协议 | auxiliary-clients | 辅助CLI/协议文档，调用同控制服务，不是APK本体<br>`tools/admin/`<br>`docs/admin/` |
| 入口与进程 | legacy-retention | 旧包/发布/密钥维护资料独立保留，不挤入新应用源码<br>`docs/releases/`<br>`tools/maintenance/`<br>`planning/geckoview-v2/evidence/legacy-public-trust.json` |
| 原生浏览器界面 | deferred-feature | O26：子应用占用/缓存与数据清理搁置，不创建当前实现依赖<br>`planning/geckoview-v2/records/validation-scope.json` |
| 来源连续性 | minimal-control-record | lastGood/source/epoch控制记录独立于被锁业务Vault，闸门仅能读最小锚；不为解密账号资料先开旧数据库<br>`planning/geckoview-v2/records/root-continuity.json`<br>`planning/geckoview-v2/records/key-and-enrollment-design.json` |
| 原生浏览器界面 | legacy-behavior-review | 旧加载条/概览选择仅作保留交互输入；新目标文件不是已完成替代，未将旧源码归档<br>`planning/geckoview-v2/records/legacy-archive-review.json`<br>`planning/geckoview-v2/LEGACY_ARCHIVE_REVIEW.md` |
| Gecko原生宿主 | abi-delivery-policy-and-auxiliary-check | 正式三ABI独立APK，手机临时测试仅arm64；辅助静态ABI/ELF/ZIP对齐检查不是产品源码叶节点<br>`planning/geckoview-v2/records/abi-packaging-policy.json`<br>`planning/geckoview-v2/verify_abi_apks.py`<br>`planning/geckoview-v2/test_abi_packages.py` |
| 平台集成 | legacy-installer-contract-retained | 旧.1安装器/默认应用文档仍是功能边界参考，新版不是任意文件转发或静默安装；不沿用旧2GiB缓存作为不受约束预算<br>`docs/REBROWSER_ANDROID_HANDOFF.md`<br>`planning/geckoview-v2/records/legacy-archive-review.json`<br>`planning/geckoview-v2/TEMPORARY_STORAGE_POLICY.md` |
| 统一控制入口与协议 | control-proof-byte-contract-and-host-evidence | 请求证明17字段已有Java/Python一致与OpenSSL验签；公开标量1测试非真实凭据；Android IPC/JSON实现和收据格式仍待验证<br>`planning/geckoview-v2/records/control-proof-format.json`<br>`planning/geckoview-v2/experiments/ControlProofVectors.java`<br>`planning/geckoview-v2/review_control_proof.py`<br>`planning/geckoview-v2/evidence/control-proof-cross-implementation.json` |
| 网站与系统许可 | native-permission-api-source-and-compile-gap | 实际158 Native签名已编译，pinned引擎普通权限EXPIRE_NEVER；有限grant期限/精确粗略定位/停止既有使用未实测，不盲搬v1 navigator猴子补丁<br>`planning/geckoview-v2/records/gecko-permission-design.json`<br>`planning/geckoview-v2/evidence/gecko-permission-api-and-lifetime.json`<br>`planning/geckoview-v2/experiments/GeckoPermissionApiSmoke.java` |
| 平台集成 | concrete-platform-action-contracts | 四个动作DTO/生命周期与共享WindowPlatformService，交接不等于OS成功，程序Back只作用自身窗口<br>`planning/geckoview-v2/records/platform-action-contracts.json`<br>`planning/geckoview-v2/review_platform_actions.py` |
| 网站与系统许可 | exact-pinned-engine-module-host-exercise | 固定官方Gecko权限/存储模块实际JS在受限Node VM中运行28断言；Services/actors完全合成，非Android；旧设置后台策略只影响拥有的浏览器页面，不应shutdown共享Runtime<br>`planning/geckoview-v2/evidence/permission-module-host-probe.json`<br>`planning/geckoview-v2/experiments/permission_module_host_probe.cjs` |
| 浏览器领域与本机数据 | scoped-browser-background-lifecycle | 浏览器后台关闭只收敛自身页面/任务，不shutdown共享GeckoRuntime，不把关闭页当全部IO停止或零内存<br>`planning/geckoview-v2/records/browser-background-ownership.json` |
| 统一控制入口与协议 | admitted-production-source-host-tests | O41逐文件准入：ControlEnvelope已实现，真实生产类经独立历史帧/签名负例及不可变性测试；未含JSON/授权/Android<br>`planning/geckoview-v2/records/implementation-readiness.json`<br>`planning/geckoview-v2/run_production_unit_tests.py`<br>`planning/geckoview-v2/evidence/production-unit-tests.json` |
| 本机加密与密钥 | canonical-public-key-and-proof-primitives | 精确P256 SPKI/曲线上点及JCA参数、严格正DER标量、规范有界Base64url和SHA；接受s/n-s；两生产文件合跑22HOST JUnit，不生成钥匙、不等于验签授权<br>`planning/geckoview-v2/records/crypto-encoding-contract.json`<br>`planning/geckoview-v2/evidence/production-unit-tests.json` |
| 网站与系统许可 | pinned-geolocation-source-method-host-study | 抽取固定GeckoAppShell四方法体，用纯合成LocationManager16项主机断言；COARSE provider hint不等于站点坐标隐私，global pause非单站撤权回执；无真实位置访问<br>`planning/geckoview-v2/experiments/geolocation_source_host_probe.py`<br>`planning/geckoview-v2/evidence/geolocation-source-host-probe.json` |
| 领域基础与任务 | production-source-actual-Android-API-compile | 已准入的六生产文件实际SDK37.2 bootclasspath编译；runner --android-jar复现。不是API30或Android运行<br>`planning/geckoview-v2/evidence/production-android-api-compile.json` |
| 统一控制入口与协议 | strict-unverified-json-and-independent-parser-crosscheck | ControlDecoder生产源码；三文件42JUnit、独立Python JSON/BE编码对Java1033条（1004树/29拒绝）；未知动作/参数schema与真实证明准入仍外部阻断<br>`planning/geckoview-v2/records/control-decoder-contract.json`<br>`planning/geckoview-v2/experiments/ControlJsonDifferential.java`<br>`planning/geckoview-v2/experiments/control_json_differential.py`<br>`planning/geckoview-v2/evidence/control-json-differential.json` |
| 领域基础与任务 | actual-task-cancellation-versus-end | 生产Cancellation区分Requester/Reporter，不重置/不释放槽；256轮并发及真实HOST忽略interrupt的Future用例。四文件51JUnit+SDK API编译，非Gecko停止证据<br>`planning/geckoview-v2/records/core-cancellation-contract.json`<br>`planning/geckoview-v2/evidence/production-unit-tests.json` |
| 本机加密与密钥 | streamed-payload-authentication-not-publication | RBMOBJ1生产流式封装，64JUnit含4MiB生成源/私有暂存；R9旧原型与新实现双向/逐字节36项+64历史RFC/NIST重跑。ZLIB仅打包字节认证，原文/事务仍外部验证<br>`planning/geckoview-v2/records/object-aead-implementation-contract.json`<br>`planning/geckoview-v2/evidence/object-aead-production-interop.json`<br>`planning/geckoview-v2/experiments/object_aead_interop.py`<br>`planning/geckoview-v2/experiments/ObjectAeadInterop.java` |
| Outlook与离线邮件 | exact-original-bounded-codec-pipeline | 六生产文件75JUnit；流式RAW/zlib候选封顶回退、完整长度/摘要验证和公用虚构key的私有AEAD管线。不是源/epoch/fence/DB提交证据<br>`planning/geckoview-v2/records/mail-codec-implementation-contract.json`<br>`planning/geckoview-v2/evidence/production-unit-tests.json` |
| 入口与进程 | standalone-v2-main-program-and-admin-console | O45收敛调研转主程序/管理员代码；独立rebrowser项目，Application/进程/共享来源闸门/单Gecko owner/原生窗口与控制台实际编译，89HOST测试。正式身份未配置保持锁定，不以验签当命令准入<br>`rebrowser/README.md`<br>`rebrowser/settings.gradle`<br>`rebrowser/app/build.gradle`<br>`rebrowser/app/src/main/AndroidManifest.xml`<br>`planning/geckoview-v2/evidence/production-main-app-compile.json` |
| 统一控制入口与协议 | native-challenge-and-process-local-admission | 实际17字段服务器challenge/30s/1MiB内存界限/每身份容量，授权重查后一次消费并调用原生job reservation；异常未知不重放。101HOST测试，真实持久化/效果调度仍待接入<br>`app/src/main/java/io/github/rebrowser/control/ControlChallengeStore.java`<br>`planning/geckoview-v2/evidence/production-main-app-compile.json` |
| 统一控制入口与协议 | actual-native-idempotency-ledger | SQLite请求->job不可变绑定、SourcePermit/原生target-epoch检查；同内容新证明复用原job，内容变更冲突。实际类DDL在HOST SQLite39检查/磁盘重开，非Android事务或效果执行<br>`app/src/main/java/io/github/rebrowser/control/IdempotencyStore.java`<br>`planning/geckoview-v2/evidence/admin-idempotency-host-sql.json`<br>`planning/geckoview-v2/experiments/admin_journal_sql.py` |
| 领域基础与任务 | actual-task-journal-and-projection | 原生work/intent/outcome/end分轴，私有原执行者ticket、跨进程未知不重放；request/task共一SQLite提交。同步CommitFence在事务内重查当前权限/期限，不能后台逃逸。<br>`app/src/main/java/io/github/rebrowser/core/TaskJournal.java`<br>`app/src/main/java/io/github/rebrowser/core/TaskSnapshot.java`<br>`planning/geckoview-v2/evidence/task-journal-host-sql.json` |
| 统一控制入口与协议 | four-actual-action-schemas-console-integration | 四项已理解原生窗口参数正式源码接入内部控制台：仅typedclaims，不验证真实window/source/presentation/URI权限，不执行动作；其他174项schema未完成并拒绝<br>`app/src/main/java/io/github/rebrowser/control/ActionSchemas.java`<br>`app/src/main/java/io/github/rebrowser/entrypoints/AdminControlActivity.java`<br>`planning/geckoview-v2/evidence/production-unit-tests.json` |
| 浏览器领域与本机数据 | actual-browser-domain-and-Gecko-ownership | R30按总树编写7个缺失本体文件：工作区/标签/不可变模型及分区/Session/View所有权和Native协调器。真实模型测试与GeckoAPI编译分层；主窗口尚未接通、永久存储/正确恢复/A04未完成，不以外壳冒充可用浏览器。<br>`app/src/main/java/io/github/rebrowser/browser/BrowserCoordinator.java`<br>`app/src/main/java/io/github/rebrowser/engine/GeckoSessionOwner.java`<br>`app/src/main/java/io/github/rebrowser/engine/SessionDisplayBinding.java`<br>`planning/geckoview-v2/evidence/production-main-app-compile.json` |
| 浏览器领域与本机数据 | actual-navigation-input-and-Android-link-boundary | SearchEngines/ExternalNavigation正式源码，选定环境/标签/revision检查后NativeCoordinator.loadUri调用；Nativeinitializer在open前拿真实Tabowner。无系统receiver激活/Source解锁/正确Checkpoint/Android实机动作；加载提交不等于网页成功，不回滚代次重放。<br>`app/src/main/java/io/github/rebrowser/browser/SearchEngines.java`<br>`app/src/main/java/io/github/rebrowser/browser/ExternalNavigation.java`<br>`app/src/main/java/io/github/rebrowser/browser/BrowserCoordinator.java` |
| 来源连续性 | actual-source-metadata-and-owned-back | R32按总树补BackCoordinator/SigningIdentity/CompiledSourceIdentity。原生Back只一层，未知不落下一层，原执行者迟到结算不发布新代；来源只读自身OS签名和APK编译Root元数据，Startup仍UNCONFIGURED/LOCKED，不写lastGood/不解锁。<br>`app/src/main/java/io/github/rebrowser/browser/BackCoordinator.java`<br>`app/src/main/java/io/github/rebrowser/continuity/SigningIdentity.java`<br>`app/src/main/java/io/github/rebrowser/continuity/CompiledSourceIdentity.java`<br>`app/src/main/java/io/github/rebrowser/bootstrap/StartupCoordinator.java` |
| 来源连续性 | actual-fresh-system-owner-authentication-source | R32push后继续补确认数据和Android机主验证源码，严格Force2/3分次，只有系统明确无锁才免验证，取消/未知不产成功票据。35源码174HOST，OwnerAuthentication仅API-WIP；OS验证/四步归约/LastGood/精确销毁/业务解锁均未完成。<br>`app/src/main/java/io/github/rebrowser/continuity/OwnerAuthentication.java`<br>`app/src/main/java/io/github/rebrowser/continuity/ContinuityState.java`<br>`planning/geckoview-v2/evidence/production-main-app-compile.json` |
| Gecko原生宿主 | actual-Gecko-progress-checkpoint-observer | R33push后继续写ProgressDelegate：实际SDK回调、同源session/partition准入、类型化进度/安全观测与单占位敏感快照转交。Native仅API编译，无新增HOST数/无Gecko实测，不把加载停止/未知接收/失效当实际work结束。<br>`app/src/main/java/io/github/rebrowser/engine/ProgressDelegate.java`<br>`planning/geckoview-v2/evidence/production-main-app-compile.json` |

### 当前边界

- 子应用占用查看/缓存与数据清理按O26搁置，不添加当前运行依赖。专项来源异常旧私有数据保护、邮箱内部引用完整性不是该延期功能。
- 测试/构建/辅助脚本/公开维护资料由独立引用与清单管理；Gecko等第三方内部源码不冒充本项目应用源文件。
- 资源/组件声明/编译政策也是软件必要内容，但按O27以引用表达；本体JS/CSS/HTML/SQL实际参与运行，不能误当“辅助代码”漏掉。
- 权威清单：[application-code-files.json](records/application-code-files.json)。技术证据：[technology-validation.json](records/technology-validation.json)。
- `python3 -B planning/geckoview-v2/render_application_tree.py --check`逐叶核对总树，拒绝混入辅助代码、重复路径、通配省略或失去关联。
