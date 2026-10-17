# 系统与体验 · R01–R10 · 设计草案

```mermaid
flowchart TB
  USER[用户交互] --> UI[原生宿主与子应用界面]
  ADMIN[认证后的调试接入] --> POLICY[身份／等级／作用域／机主授权]
  UI --> DOMAIN[统一领域动作与查询]
  POLICY --> DOMAIN
  DOMAIN --> B[ReBrowser]
  DOMAIN --> O[ReOutlook]
  DOMAIN --> M[ReMusic]
  DOMAIN --> S[存储／任务／审计／配置]
  B --> SESSION[Gecko会话管理]
  O --> SESSION
  M --> SESSION
  SESSION --> GV[自带 GeckoView stable]
  SESSION --> EXT[APK内受信扩展／类型化消息]
  EXT --> SITE[真实网站与官方登录／播放器]
  O --> MAIL[压缩离线邮件存储]
  M --> MEDIA[Android媒体会话／服务／歌词]
  S --> OWN[数据归属目录与引擎清理协调]
  GV -. 不是依赖 .-> WV[系统WebView：不作运行门槛]
```

```mermaid
stateDiagram-v2
  [*] --> NewInstall
  NewInstall --> Ready: Android11+且自带内核成功启动
  Ready --> BrowserSession
  Ready --> OutlookSession
  Ready --> MusicSession
  BrowserSession --> Ready: 退出／关闭，按策略保存
  OutlookSession --> Ready: 保留正常会话，不误清登录
  MusicSession --> BackgroundMusic: 合法播放与服务生命周期
  BackgroundMusic --> MusicSession: 原任务重连
  BackgroundMusic --> Ready: 明确停止并释放
  Ready --> LocalDegradation: 个别设备能力不可用
  LocalDegradation --> Ready: 不牵连其他功能
```

| 设计对象 | 新实现原则 | 不能偷换的验收 |
| --- | --- | --- |
| 会话／隔离 | 用Gecko原生runtime/session/context建模；实际隔离拓扑待P1验证 | 不复用任意站点／另一子应用的登录环境 |
| 功能承接 | 对照[功能矩阵](../matrices/feature-parity.csv)，不复制旧类结构 | 双层标签、书签／历史、下载、返回／Popup、权限、离线邮件、音乐系统控制等逐项确认 |
| 浏览器与网站能力 | Native delegates优先，扩展只做必要受控集成 | 不以任意JS桥或裸远程调试端口替代架构 |
| 音乐 | 官方认证→同一音乐隔离上下文→真正播放器；重新评估旧引擎补丁 | 手机UA登录；桌面扫码仅机主主动且平板＋全窗口＋横屏；未知认证不放行业务 |
| Android11兼容 | 不查系统WebView包／版本；验证ABI、GPU、内存、权限、编解码器 | 不能宣传已覆盖全部Android11设备；缺单项能力尽量局部降级 |
| 新包 | io.github.rebrowser；v2.0.0；正式新签名；含背景全量 | 无旧数据迁移；不自动删除旧应用／旧数据 |
| 体积 | 自带内核不可避免变大；先测再解释 | 不承诺保持旧版219KB／1MB包体 |

**未决连接：** runtime/context与物理存储、后台音乐、清理回执的选择共同影响架构，见Q09／Q10；不能先画成“每个context独立目录”再把假设当事实。
