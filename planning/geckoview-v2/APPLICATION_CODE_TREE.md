# 应用本体目标代码总树 · R10首版

> O27范围：只逐文件展开应用自身运行代码。测试/验证/构建/维护/文档不混入主树；在树后按关联概括并引用目标位置。
> 这是目标设计，不是已创建的产品源码。已先完成固定Gecko二进制/API编译与隔离Release打包；Android运行/网站兼容尚未验证，`[?]`不得理解为通过。

当前目标：`io.github.rebrowser` · 本体代码 **164文件** · 16职责分组。源码增加/更名必须先改权威清单，再重新生成；不能用省略号隐藏源码。

符号：`[J]`原生代码，`[S]`运行脚本/HTML，`[C]`运行样式，`[Q]`运行SQL，`[?]`需要Android/网站实证；`↪`为树外引用，不算本体代码叶节点。

## 本体代码树

```text
└── app/
    └── src/
        └── main/
            ├── assets/
            │   ├── bridge/
            │   │   ├── background.js [S][?] — 固定内建扩展后台和Native路由
            │   │   ├── content/
            │   │   │   ├── music.js [S][?] — 隔离世界音乐页面握手与文档路由
            │   │   │   └── outlook.js [S][?] — 隔离世界邮箱握手/账户/DTO流
            │   │   ├── page/
            │   │   │   ├── music-api.js [S][?] — 官网MAIN世界受控SDK命令和真实回执
            │   │   │   └── outlook-api.js [S][?] — 官网MAIN世界只读账号/邮件采集与明确用户动作
            │   │   ├── protocol.js [S][?] — 严格消息/序号/信用/版本
            │   │   └── styles/
            │   │       ├── music-desktop.css [C][?] — 完整平板横屏/显式桌面扫码的边界
            │   │       ├── music-phone.css [C][?] — 批准手机布局适配，不重建播放器
            │   │       └── music-window.css [C][?] — 非完整平板窗口原约定适配
            │   ├── offline-mail/
            │   │   ├── reader.css [C][?] — 离线正文可读排版
            │   │   ├── reader.html [S][?] — 只读离线安全阅读文档
            │   │   └── reader.js [S][?] — 无脚本邮件执行的本机资源交互
            │   └── sql/
            │       ├── browser-v1.sql [Q][?] — 环境/标签/历史/书签原生库
            │       ├── control-v1.sql [Q][?] — nonce/任务/幂等/审计元数据原生库
            │       └── mail-v1.sql [Q][?] — 新账户实体/引用/修订/事务约束
            └── java/
                └── io/
                    └── github/
                        └── rebrowser/
                            ├── ReBrowserApplication.java [J][?] — 只在正确进程建立应用所有者，不启动被来源闸门锁住的业务
                            ├── authority/
                            │   ├── AdminAudit.java [J][?] — 脱敏审计与受控私有结果
                            │   ├── AdminProofVerifier.java [J][?] — 来源中立私钥证明，不以辅助包签名额外否决
                            │   ├── AuthorityPolicy.java [J][?] — 同作用域>=、通道/介入floor、机主授权激活
                            │   ├── AuthorityScale.java [J][?] — H/普通最高/Root与不可变风险下限
                            │   ├── CompiledPrincipals.java [J][?] — 预编译全部高级身份与角色
                            │   ├── DeviceWhitelist.java [J][?] — Root联合/各最高自有/专有名单隔离
                            │   ├── DisableLedger.java [J][?] — 更高禁更低、Root不可禁、恢复和签名快照
                            │   ├── EnrollmentProposal.java [J][?] — 仅已明确设备/持钥证据的编译名单提案
                            │   ├── LowDelegationVerifier.java [J][?] — 低于H的有界委托链和撤销
                            │   ├── OwnerGrantStore.java [J][?] — 精确范围/期限/epoch的本人授权
                            │   └── TargetPossessionProof.java [J][?] — 实际目标安装私钥challenge验证
                            ├── bootstrap/
                            │   ├── ComponentGate.java [J][?] — Activity/Provider/Service/后台入口共同闸门
                            │   ├── ProcessRole.java [J][?] — 识别主/Gecko内部子进程，避免每进程重复初始化
                            │   └── StartupCoordinator.java [J][?] — 来源检查→原生壳→按需内核的启动顺序
                            ├── bridge/
                            │   ├── BridgeBudget.java [J][?] — 64KiB信封/32KiB原始块和有界信用
                            │   ├── BridgeMessages.java [J][?] — HELLO/READY/命令/结果/观测/错误类型
                            │   ├── BridgeStreamReceiver.java [J][?] — 分块/长度/摘要/临时对象提交
                            │   ├── BuiltinConnectorManager.java [J][?] — 固定APK扩展安装/版本/注册先于业务数据泵
                            │   ├── ConnectorBroker.java [J][?] — 真实sender/session/port路由与允许动作
                            │   ├── DocumentLease.java [J][?] — 页面/port/epoch代次握手与失效
                            │   ├── MusicConnector.java [J][?] — 官方同context账号/业务SDK类型化命令
                            │   └── OutlookConnector.java [J][?] — 官方邮箱账号/邮件DTO校验与有界流
                            ├── browser/
                            │   ├── BackCoordinator.java [J][?] — IME/模态/全屏/浮层/子屏/Popup/history/环境单次返回
                            │   ├── BookmarkStore.java [J][?] — 收藏/书签/搜索设置
                            │   ├── BrowserCoordinator.java [J][?] — 环境/标签/导航/原生页面共同领域动作
                            │   ├── BrowserModels.java [J][?] — 浏览器句柄/状态/不可变快照
                            │   ├── BrowserStore.java [J][?] — 新v2原生环境/标签持久化，非v1迁移
                            │   ├── ExternalNavigation.java [J][?] — 外部HTTPS临时隔离/非HTTPS用户交接
                            │   ├── HistoryStore.java [J][?] — 近期访问/期限/维持/暂停写入
                            │   ├── SearchEngines.java [J][?] — 原生搜索提供者与明确目标加载
                            │   ├── TabCatalog.java [J][?] — 环境子标签/选择/父Popup/关闭
                            │   └── WorkspaceCatalog.java [J][?] — 双层环境类型/保留策略/名称
                            ├── continuity/
                            │   ├── CompiledSourceIdentity.java [J][?] — APK编译根公钥/策略身份
                            │   ├── ContinuityCoordinator.java [J][?] — 四步/抢救/销毁前锁定/提交新epoch的状态归约
                            │   ├── ContinuityState.java [J][?] — 来源与确认步骤、失败和不可逆点数据类型
                            │   ├── EpochCommitter.java [J][?] — 确认清空后原子提交新epoch/source
                            │   ├── EpochErasureCoordinator.java [J][?] — 专项旧私有数据销毁/核对，不是搁置的子应用清理
                            │   ├── LastGoodSourceStore.java [J][?] — 最后正常来源与数据epoch记录，异常包不覆盖
                            │   ├── OwnerAuthentication.java [J][?] — 每步新Android验证及确无锁判断
                            │   ├── PrivateEpochManifest.java [J][?] — 仅v2自有旧epoch文件/密钥归属清单
                            │   └── SigningIdentity.java [J][?] — APK真实签名锚与合法轮换验证
                            ├── control/
                            │   ├── ActionRegistry.java [J][?] — 178逐动作定义/处理器/门槛单一注册
                            │   ├── ActionSchemas.java [J][?] — 类型/范围/长度/目标/副作用参数
                            │   ├── ControlChallengeStore.java [J][?] — 目标绑定nonce/新鲜度/一次消费
                            │   ├── ControlDecoder.java [J][?] — 严格解码/重复字段拒绝/数据量上限
                            │   ├── ControlDispatcher.java [J][?] — 授权→同领域服务→真实结果，不复制特权路径
                            │   ├── ControlEnvelope.java [J][?] — 新控制协议签名字节与请求结构
                            │   ├── ControlResultStream.java [J][?] — 私有大结果有界传输
                            │   └── IdempotencyStore.java [J][?] — 同请求只执行一次与未知效果查询
                            ├── core/
                            │   ├── ActorContext.java [J][?] — 正常用户/系统媒体/已验证管理员的明确身份
                            │   ├── Cancellation.java [J][?] — 请求取消和真实停止证据
                            │   ├── DomainResult.java [J][?] — 结果/错误/等待条件/证据等级
                            │   ├── ResourceBudgets.java [J][?] — 队列/块/缓存/优先级和低内存预算
                            │   ├── TargetReferences.java [J][?] — 原生目标句柄与epoch/partition/session绑定
                            │   ├── TaskCoordinator.java [J][?] — auth/effect/read/safety有界通道及真实工作集
                            │   ├── TaskJournal.java [J][?] — 副作用/幂等/持久化阶段日志
                            │   └── TaskSnapshot.java [J][?] — 客户端等待与实际执行/效果/持久化分轴
                            ├── crypto/
                            │   ├── CryptoEncoding.java [J][?] — 严格SPKI/DER/长度和摘要编码
                            │   ├── DataKeyVault.java [J][?] — epoch包装密钥/账户用途DEK与版本
                            │   ├── InstallKeyStore.java [J][?] — 安装设备EC密钥持有与目标challenge，不用序列号作凭据
                            │   ├── KeyPurpose.java [J][?] — 发行/管理员/设备/数据密钥域定义
                            │   ├── ObjectAead.java [J][?] — HKDF派生不可变对象AES-GCM分帧
                            │   └── PrivateRecordSealer.java [J][?] — 原生敏感快照/策略记录封装
                            ├── download/
                            │   ├── DownloadCoordinator.java [J][?] — 真实WebResponse流/用户批准/取消/发布
                            │   ├── DownloadHandoff.java [J][?] — 明确系统安装器/文件打开，非自动安装
                            │   ├── DownloadStore.java [J][?] — 任务/元数据/保存目标的私有记录
                            │   ├── ResponseStreamWorker.java [J][?] — 有界IO/EOF/关闭流/失败/临时对象
                            │   └── SaveTargetRegistry.java [J][?] — 原生拥有或用户授权URI，拒绝任意私有路径
                            ├── engine/
                            │   ├── ContentDelegate.java [J][?] — 崩溃/终止/全屏/外部response
                            │   ├── GeckoHistoryDelegate.java [J][?] — 已提交历史与认证瞬态访问区分
                            │   ├── GeckoMediaBridge.java [J][?] — 实际媒体激活/feature/状态与秒单位
                            │   ├── GeckoRuntimeOwner.java [J][?] — 主进程唯一runtime，原生子进程由SDK管理
                            │   ├── GeckoSessionOwner.java [J][?] — session强引用/创建/关闭与代次
                            │   ├── NavigationDelegate.java [J][?] — 加载/外链/新窗与正确父来源
                            │   ├── PartitionCatalog.java [J][?] — 稳定非null context和环境/子应用归属
                            │   ├── PermissionDelegate.java [J][?] — context+origin网站许可与OS许可分层
                            │   ├── ProgressDelegate.java [J][?] — 提交导航/加载/安全状态/快照事件
                            │   ├── PromptDelegate.java [J][?] — 网站提示/文件选择的Native/系统等待
                            │   ├── SelectionDelegate.java [J][?] — 网页选择/复制与用户动作
                            │   ├── SessionCheckpointStore.java [J][?] — 异步sessionState与敏感快照提交/恢复
                            │   ├── SessionDisplayBinding.java [J][?] — View附着/分离/可见/focus与session生命分离
                            │   └── ViewportPolicy.java [J][?] — 手机/完整平板/小窗与批准布局策略
                            ├── entrypoints/
                            │   ├── AdminControlActivity.java [J][?] — 应用内/隐藏控制台，隐藏不是授权
                            │   └── AdminControlProvider.java [J][?] — 普通持钥辅助包/ADB的公开受认证Provider入口
                            ├── lyrics/
                            │   ├── LyricOverlayController.java [J][?] — 关闭/可拖动/锁定穿透的唯一状态
                            │   ├── LyricOverlayWindow.java [J][?] — 实际浮层视图/触摸/穿透
                            │   ├── LyricPositionStore.java [J][?] — 位置恢复与窗口边界
                            │   └── OverlayPermissionFlow.java [J][?] — 系统浮层权限请求和结果
                            ├── music/
                            │   ├── MusicActivity.java [J][?] — 批准布局及同一个真实播放器session显示
                            │   ├── MusicArtwork.java [J][?] — 最新请求/有界像素解码/缓存
                            │   ├── MusicAudioFocus.java [J][?] — 有效焦点租约，pause后gain不复活
                            │   ├── MusicAuthentication.java [J][?] — 正常手机UA/fresh官方检查/未知不等于退出
                            │   ├── MusicCoordinator.java [J][?] — 认证/意图/唯一执行路线/资源/真状态归约
                            │   ├── MusicLyrics.java [J][?] — 官方歌词有界读取/逐字时间映射
                            │   ├── MusicMediaSession.java [J][?] — Android系统动作与Gecko/官网语义对照
                            │   ├── MusicNotification.java [J][?] — 真实状态/可用动作，不伪造进度
                            │   ├── MusicPlaybackAuthorization.java [J][?] — 冷启暂停、明确起播、旧意图失效
                            │   ├── MusicPreferences.java [J][?] — 音源/恢复意图/位置的明确本机配置
                            │   ├── MusicQueue.java [J][?] — 官网队列/喜欢/歌单域，不提取URL重建播放器
                            │   ├── MusicService.java [J][?] — 合法前台媒体服务和Native任务/通知所有者
                            │   ├── MusicSessionOwner.java [J][?] — UI与服务共享单个music context/session
                            │   └── MusicState.java [J][?] — 账号/播放/资源/意图/焦点代次
                            ├── outlook/
                            │   ├── AttachmentCoordinator.java [J][?] — 有界获取/取消/原生对象/明确保存
                            │   ├── MailAccountCatalog.java [J][?] — 原生账号归属和绑定revision
                            │   ├── MailAuthCoordinator.java [J][?] — fresh官网证据/网络未知/错误分类/恢复
                            │   ├── MailCaptureValidator.java [J][?] — 真实官方来源/代次/账户/DTO/流校验
                            │   ├── MailDatabase.java [J][?] — 执行新账户SQL/事务/约束，源闸门先于打开
                            │   ├── MailExportCoordinator.java [J][?] — 明确范围/高风险授权的正文附件导出
                            │   ├── MailObjectCodec.java [J][?] — RAW/zlib与受约束解压、长度/摘要
                            │   ├── MailObjectFormat.java [J][?] — 124B头/不可变对象/分帧AEAD
                            │   ├── MailObjectStore.java [J][?] — 小payload内联/较大不可变文件/同账户去重
                            │   ├── MailReferenceReconciler.java [J][?] — 内部引用/孤儿完整性回收，非子应用清理功能
                            │   ├── MailRetentionPolicy.java [J][?] — 原始唯一内容与可重建缓存区分
                            │   ├── MailSearch.java [J][?] — 分页元数据与可取消中文正文跨块检索
                            │   ├── MailSyncCoordinator.java [J][?] — 账户有序批次/引用/游标同事务
                            │   ├── OfficialMailEntry.java [J][?] — 个人/工作学校/最近成功入口，禁止猜测账户域
                            │   ├── OfflineMailActivity.java [J][?] — 原生离线列表/正文/搜索恢复位置
                            │   ├── OfflineMailRenderer.java [J][?] — 隔离只读HTML，无脚本/邮箱Cookie/自动远程内容
                            │   ├── OutlookActivity.java [J][?] — 官方邮箱显示和原生离线入口
                            │   └── OutlookSessionOwner.java [J][?] — 同context邮箱/官方认证Popup生命
                            ├── permissions/
                            │   ├── ClipboardCoordinator.java [J][?] — 明确用户/授权动作与真实OS前台资格
                            │   ├── FilePickerCoordinator.java [J][?] — 选择器/ContentResolver/拒绝与重建
                            │   ├── PendingPromptRegistry.java [J][?] — session/document/request绑定且一次结果
                            │   ├── PermissionCoordinator.java [J][?] — 原生网站问询与OS分层流程
                            │   └── SiteGrantStore.java [J][?] — origin/partition/能力/期限记录
                            ├── system/
                            │   ├── DefaultBrowserFlow.java [J][?] — 用户发起Role系统流程，结果如实
                            │   ├── DeviceWindowState.java [J][?] — 窗口/平板/横竖屏事实，不以型号硬门控
                            │   ├── ForegroundPolicy.java [J][?] — Android前台服务/通知许可按实际版本处理
                            │   ├── MemoryPressurePolicy.java [J][?] — 仅本软件缓存/可恢复session回收，不动其他软件
                            │   └── PlatformResultRouter.java [J][?] — 系统选择器/权限/安装器返回归属
                            └── ui/
                                ├── ApplicationsActivity.java [J][?] — 子应用启用与48dp设置齿轮
                                ├── BookmarkView.java [J][?] — 收藏和书签操作
                                ├── BrowserActivity.java [J][?] — 主窗口和Native/Gecko显示协调
                                ├── BrowserToolbar.java [J][?] — 原生地址/返回/刷新/输入建议
                                ├── DownloadsActivity.java [J][?] — 批准/取消/任务/文件交接
                                ├── DownloadsAdapter.java [J][?] — 下载列表与实际阶段显示
                                ├── HistoryActivity.java [J][?] — 原生历史查询/期限/清理入口
                                ├── HistoryAdapter.java [J][?] — 有界近期/历史列表
                                ├── NativeDialogs.java [J][?] — 取消/危险确认/平台等待，不默认为同意
                                ├── NewTabView.java [J][?] — 批准背景/搜索/收藏布局
                                ├── SettingsActivity.java [J][?] — 通用/权限/应用管理入口
                                ├── SettingsSections.java [J][?] — Native设置项与结果绑定
                                ├── SitePermissionsActivity.java [J][?] — 网站范围与系统许可状态
                                ├── SourceWarningActivity.java [J][?] — 仅原生四步/救援，不创建锁定旧业务
                                ├── SubapplicationSettingsActivity.java [J][?] — 子设置/音源/入口；占用清理按O26搁置
                                ├── TabPanel.java [J][?] — 环境内标签选择/新建/关闭
                                ├── WindowPresentation.java [J][?] — 状态栏/全屏/窗口布局，不改用户全局设置
                                └── WorkspacePanel.java [J][?] — 环境列表和双层交互
```

## ↪ 测试需求与目标位置（辅助代码独立存放，不展开为主树）

| 职责 | 测试要求摘要 | 目标文件引用，均为规划路径 |
| --- | --- | --- |
| 入口与进程 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/entry/EntryContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/entry/EntryRuntimeTest.java` |
| 来源连续性 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/continuity/ContinuityContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/continuity/ContinuityRuntimeTest.java` |
| 领域基础与任务 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/core/CoreContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/core/CoreRuntimeTest.java` |
| 本机加密与密钥 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/crypto/CryptoContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/crypto/CryptoRuntimeTest.java` |
| 管理员政策 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/authority/AuthorityContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/authority/AuthorityRuntimeTest.java` |
| 统一控制入口与协议 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/control/ControlContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/control/ControlRuntimeTest.java` |
| Gecko原生宿主 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/engine/EngineContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/engine/EngineRuntimeTest.java` |
| APK内建连接器 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/bridge/BridgeContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/bridge/BridgeRuntimeTest.java` |
| 浏览器领域与本机数据 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/browser/BrowserContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/browser/BrowserRuntimeTest.java` |
| 原生浏览器界面 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/ui/UiContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/ui/UiRuntimeTest.java` |
| 下载与目标文件 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/download/DownloadContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/download/DownloadRuntimeTest.java` |
| 网站与系统许可 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/permissions/PermissionsContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/permissions/PermissionsRuntimeTest.java` |
| Outlook与离线邮件 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/outlook/OutlookContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/outlook/OutlookRuntimeTest.java` |
| 官方音乐与后台 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/music/MusicContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/music/MusicRuntimeTest.java` |
| 桌面歌词 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/lyrics/LyricsContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/lyrics/LyricsRuntimeTest.java` |
| 平台集成 | 正常/拒绝/取消/过期/代次/失败与有界资源；真实平台/进程/窗口/Gecko或服务生命周期，禁止以主机替代 | `app/src/test/java/io/github/rebrowser/system/SystemContractsTest.java`<br>`app/src/androidTest/java/io/github/rebrowser/system/SystemRuntimeTest.java` |

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

### 当前边界

- 子应用占用查看/缓存与数据清理按O26搁置，不添加当前运行依赖。专项来源异常旧私有数据保护、邮箱内部引用完整性不是该延期功能。
- 测试/构建/辅助脚本/公开维护资料由独立引用与清单管理；Gecko等第三方内部源码不冒充本项目应用源文件。
- 资源/组件声明/编译政策也是软件必要内容，但按O27以引用表达；本体JS/CSS/HTML/SQL实际参与运行，不能误当“辅助代码”漏掉。
- 权威清单：[application-code-files.json](records/application-code-files.json)。技术证据：[technology-validation.json](records/technology-validation.json)。
- `python3 -B planning/geckoview-v2/render_application_tree.py --check`逐叶核对总树，拒绝混入辅助代码、重复路径、通配省略或失去关联。
