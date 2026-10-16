# 网易云音乐：接口研究草案

**历史研究，不是现行接口规范或新的调查授权。** 以下服务/音频分析来自正式开发前的限定研究，各处“本轮/待验证”均指该研究时点。后续 [v1.0.3 Android 实现](NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)已接入媒体服务/固定控制，播放引擎始终为真正官方 WebPlayer，不是自建原生播放器；当前状态见[正式版验收](NETEASE_MUSIC_RELEASE_ACCEPTANCE.md)。已确认的[非横屏布局](NETEASE_MUSIC_PORTRAIT_LAYOUT.md)保留原网页控件与业务；本页不批准任意模块/API 桥，不自动恢复停止的第三方调查，也不把宿主 View 缩放称为 page zoom。

目标入口：<https://music.163.com/st/webplayer>。第一阶段只研究网易云，其他提供方仍未支持。

## 1. 当前证据及限制

重新以匿名桌面 UA 获取官方页面，HTTP 200；其三个脚本 URL 与前次研究一致，并重新下载逐字节核对：

| 公开脚本 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `vendors-rudio.pc-new.production.js` | 257,484 | 见本地源文件校验记录 |
| `vendors~app.31d15e2d.js` | 2,521,813 | 见本地源文件校验记录 |
| `app.4c9df986.js` | 5,609,816 | `baa76cd9cefd3b638b4ea8b6c55d42d8b6ebd0a14027818a465ed3e1b6fa4a44` |

公开源地址基路径：`https://s5.music.126.net/static_public/68aea63daca57500bb3fb4b6_68aea63daca57500bb3fb4b7/`，主应用位于 `hybrid/app.4c9df986.js`，通用依赖位于 `hybrid/vendors~app.31d15e2d.js`。

对当前包做静态 AST 分析得到约 412 个不同 `/api/...` 字符串。**这不是 412 个网页端可用接口的证明**：脚本同时含 PC 客户端、上传、下载、当地文件和空实现分支。

在保留的桌面测试 Profile／标签页里进行有界模块能力观察：账号、歌单、歌曲详情、播放地址、搜索及歌词入口确实存在。随后只调用一次账号状态读取，业务返回码 200，存在 account/profile/userId/nickname/avatar 字段。记录仅含返回码和字段存在性，不记录其值、Cookie、令牌或已登录 DOM。

后续只读验证了当前歌曲搜索的两页（offset 0/3，每页 3 条）、对应的一首歌曲详情，以及搜索所得公开歌单的少量曲目详情（业务码 200）。报告仅保存字段名、类型、计数和匹配性，不保存账号值、歌曲／歌单内容或音源 URL。

另对提取的公开音频实现进行隔离模拟：7 项断言通过，音频是替身，不发生网络或播放。这是**源代码行为模拟，不是实际网页／WebView 播放验收**。

没有调用登录、退出、令牌刷新、收藏、播放或歌单写操作，没有改变手机、关闭测试浏览器或替换 Profile。歌词、个人歌单、播放地址和完整播放仍未完成服务／设备验证。

## 2. 先分清三类接口

1. **服务业务接口**：账号、歌单、歌曲、搜索、歌词、权益和播放地址。它们是官方网页正在使用的私有业务接口，不是承诺稳定的公开开发者 API。
2. **页面播放器接口**：load/play/pause/seek、音量、事件和状态；不等于 HTTP 接口。暂停不应请求一个想象中的服务器“pause URL”。
3. **Android 应用接口**：未来为我们自己的 UI／系统控制定义的有界方法。不能把整个网页 SDK、任意 URL 请求、任意 Redux action、任意模块 ID 或 JS 字符串暴露出去。

若后续增加自有原生 UI／系统媒体控制，应调用第三层，由提供方适配器处理前两层；已确认的网页布局适配直接保留原页面组件与事件，不重新实现整套业务接口。下面的包内模块编号只用于研究定位，**不属于稳定 SDK 或对外协议**。

## 3. 请求、登录和 Profile

当前主包模块 15 定义业务函数，模块 12 根据 `isWeb` 选择网页请求路径；网页请求默认 POST，并设置 `credentials: include` 和 `x-music-web-os: web3`。依赖包的加密中间件会将 `/api/` 转为 `/weapi/`，构造表单 `params/encSecKey`，并在页面内部使用 CSRF 值。请求管线还包含域名处理和风控检查。

因此，不能只把下面的路径拼成普通 GET，也不能为简化调用删除 CSRF／风控。主包中存在 `/eapi/`、设备签名、原生 storage.fetch 等 PC 代码，不代表网页端需要伪装成 PC 客户端。源码中的 `interface.music.163.com` 域名配置也意味着 **同 Profile 不等于同 Origin**；具体域名改写、CORS 和 Cookie 范围仍须跟踪。

优先评估在真正加载的官方网页运行时内，以固定调用复用其正常请求管线；这样不用把 Cookie 交给新 UI、Java 网络客户端或外部代理。它仍是依赖私有网页实现的适配方案，版本变化必须可检测、可拒绝，不能静默调用错误函数。

账号状态实测的是模块 15 的当前导出 `Ah`。正式实现保留官方登录流程；不自制密码／短信／验证码采集、不自动刷新凭据或绕过 CAPTCHA。

首轮 Android 实现使用新的固定音乐专属 Profile；现有已登录浏览器 Profile 保留，不自动迁移登录。更换 UI 不自动继承新 Profile 的登录，不能复制 Cookie/token。新环境登录和真实隔离性仍需设备验收。

## 4. UI 所需业务接口清单

参数和数据形状来自当前官方源码；账号、歌曲搜索分页、单曲详情、公开歌单搜索及详情已有只读服务响应证据，其他行仍不能视为已验证可用。

| 功能 | 当前逻辑路径 | 源码可确认内容／注意事项 |
| --- | --- | --- |
| 账号状态 | `/api/w/nuser/account/get` | 当前导出 `Ah`；只读实测业务码 200，存在 account/profile 基本字段 |
| 我的歌单 | `/api/user/playlist` | 当前导出 `rg`；官方调用 `uid, offset, limit`；消费 `playlist, more`，按创建者区分自建和收藏 |
| 置顶歌单补充 | `/api/user/playlist/getTop` | 当前导出 `vg`；调用 `userId, offset, limit`，与歌单结果合并、去重 |
| 歌单详情 | `/api/playlist/v4/detail` | 当前 `pe` 返回原始对象、`oe` 格式化为曲目数组，不能混用；调用 `id, n, s`。公开歌单实测 `n=3,s=0` 返回 3 个 tracks、29 个 trackIds，且没有 privileges 字段；还需读动态信息／补充曲目权益 |
| 歌单动态信息 | `/api/playlist/detail/dynamic` | 当前导出 `te`；收藏状态、计数等，不应把初始缓存值当实时值 |
| 歌单详情另一版本 | `/api/v6/playlist/detail` | 当前导出 `bh`；条件分支使用，不能不加区分混用响应 |
| 批量歌曲详情 | `/api/v3/song/detail` | 当前导出 `Yg`；官方构造 `c=JSON.stringify([{id,v:0},...])`，按 500 条分批，并合并 songs 与 privileges。单曲实测返回已归一化数组，ID 与搜索结果匹配 |
| 综合／旧版搜索候选 | `/api/cloudsearch/pc` | 当前导出 `V`；只确认定义，具体网页调用参数尚待跟踪 |
| 当前歌曲搜索 | `/api/search/song/list/page` | 延迟模块 1897→2201→当前导出 `tf`；参数 `keyword,scene,limit,offset,needCorrect`，默认 scene=NORMAL；两页只读调用成功，归一化结果 `list,more,total,searchQcReminder,trackId` |
| 歌单／歌手／专辑搜索 | `/api/v1/search/playlist/get`、`/api/v1/search/artist/get`、`/api/v1/search/album/get` | 当前分别 `Rg/Qg/Pg`；歌单搜索实测使用 `s,limit,offset,queryCorrect`，返回 `list,total` 等；歌手／专辑未实测，参数不能照搬歌曲搜索 |
| 播放地址与等级 | `/api/song/enhance/player/url/v1` | 当前导出 `Of`；官方调用涉及 ids/level/encodeType/immerseType/trialMode，并检查业务结果、读取 data[0] |
| 播放权限补充 | `/api/song/enhance/privilege`、`/api/privilege/song/message/get` | 显示拒绝原因，不修改返回的权益字段 |
| 歌词 | `/api/song/lyric/v1` | 当前导出 `Rf`，有专用格式化器；逐句／翻译／罗马音／逐字数据的形状和时间单位待确认 |
| 首页内容候选 | `/api/homepage/block/page` | 当前导出 `lc`；使用 cursor JSON／blockCodeOrderList，混合业务块，不适合一开始照抄整页 |
| 喜欢／取消喜欢 | `/api/song/like` | 当前导出 `Qf`；官方调用 trackId/userid/like 等，**写操作，本轮未调用** |
| 歌单收藏与编辑 | subscribe/unsubscribe/manipulate/tracks 等 | 写操作，后置；需要明确用户意图、成功回报和失败处理 |

必须保留 `trackIds` 和真实总数；“详情返回 tracks 的数量”不一定等于完整歌单。分页／批量请求要限流、取消过时请求，不一次性抓取整个用户库。ID 采用字符串，避免无意精度损失。

当前搜索调用链定位在公开 `hybrid/114.29dcda5f.js`（模型／服务）与 `hybrid/23.a8f598ea.js`（页面）；因此只查初始 app 包会漏掉真正调用者。综合搜索使用 `/api/search/pc/complex/page`，包含初始 cursor JSON 和混合 blocks，不与歌曲列表响应共用一种解析器。

搜索与单曲详情 SDK 的归一化 Track 实测使用嵌套 `artists` 和 `album`，而不是顶层 artistIds/albumId；权益对象包括 maxPlayLevel/maxFreeLevel/freeTrialPrivilege 等。不能将原始服务 DTO（ar/al/dt）与 SDK DTO（artists/album/duration）混为一谈。模块 21 的曲目格式化器直接取 dt 为 duration，没有转换成秒；低层 Howler 的 current/duration 则是秒。适配器必须按来源换算。

歌词格式化器同在模块 21：保留 lrc/yrc/tlyric/romalrc/yromalrc/ytlrc，过滤没有 lyric 的条目，把 offset 除以 1000，并推导 noLyric。这是静态证据；实际请求参数、正文解析和时间边界仍待验证，不能把 noLyric 当网络错误。

喜欢、收藏、歌单编辑、播放记录上报的行为不能在页面初始化、自动重试或测试中偷偷发生。直接索取下载地址也不应成为播放失败时的备用路径。

2026-10-16 开发补充：[歌词只读基础](MUSIC_EXPANDED_PLAYER_AND_LYRICS.md)已固定 `15.Rf` 与官方解析器 `519.a`；一次无登录公共 GET 返回 code 200，原解析器生成 49 个有时间戳的行，确认该次响应及秒／毫秒换算。随后同签名临时只读辅助程序在本应用当前登录音乐 Profile 得到一次 `ready`／身份匹配／4 个定时条目的结果，未导出正文或账号并已停用；不是有歌词歌曲逐句／seek／试听或切歌矩阵。云盘歌词、权益／试听和系统／蓝牙输出仍不视为完成。后续[原生桌面歌词三态入口](MUSIC_DESKTOP_LYRICS.md)已经接通，当前实机／未完成范围在那里单列。

## 5. 当前网页播放器实际能力

主包模块 4 有网页兼容音频实现：Howler `html5: true`，即 HTML 媒体元素路径。不能因为依赖名带 rudio 或出现 AudioContext 就断言当前实际使用何种后台机制。

低层音频对象可见：

- `load(playId, playInfo)`，需要 `musicurl`，卸载前一音源后加载新源。
- play/pause/stop/seek、音量与 playback rate。
- `playstate`、`end`、`playprogress`、`buffering`、`seek` 等事件；内部进度定时器为 100ms。
- 状态枚举低层 playing=1、pause=2；seek/current/duration 使用 Howler 的秒。current 是 toFixed(2) 字符串，需要转换为统一毫秒；cacheProgress 则是 toFixed(4) 的缓冲比例，不是时间，不能直接当毫秒。
- `setMediaSession` 写曲名、歌手、专辑和封面；这不证明 Android 锁屏、后台、音频焦点或耳机控制已完整可用。

网页 SDK 当前实际存在的控制方法：`AudioPlayer.play/resumePlay/pause/stop/seek/setVolume/setPlaybackRate`。其事件接口为 **`subscribePlayStatus({type,callback})`**，并不是猜测的 subscribePlayState/subscribePlayProgress/subscribeEnd；后面三个名称在当前实例里不存在。

### 控制参数与兼容问题

网页 SDK 的 play 接收一个对象（含 songId/musicurl），内部再调用低层 load(playId,playInfo)；resumePlay/pause 接收 playId/resumeOrPauseId，seek 接收 playId/seekId/value，而 stop 接收 playId 字符串。不能把低层方法签名误当 SDK 的公开签名。

隔离源码＋假音频模拟已确认：

1. **返回值不是完成凭据**：错误 playId 的 SDK resumePlay 仍返回 true，但没有播放；正确 playId 的低层 play 返回 false，却执行了音频 play。不能据此更新 UI／系统状态。
2. **取消订阅不匹配**：subscribePlayStatus 注册包装函数，unSubscribePlayStatus 只在 PlayState 上尝试移除原函数。模拟中取消后回调仍触发。应避免随 UI 重建反复注册，先确定可靠解绑或运行时级单次订阅方案。
3. **请求 ID 丢失**：PlayState 的 resumeOrPauseId 和 Seek 的 seekId 被赋成 playId；模拟 seek 回报没有保留传入的 seekId，不能拿它作精确请求确认。SDK seek 还会等待下一个未按请求过滤的 Seek 事件，缺少超时。
4. **停止缺少回报**：这份 createAudio 没有 onstop 映射；假音频执行 stop 时，没有 PlayState 回报。不能只订阅 playstate 后期待覆盖所有生命周期。
5. **Buffering 不完整**：存在枚举、订阅与转换，但该兼容实现未找到实际 emit Buffering 的触发点。不能据方法存在就声称具备可靠缓冲状态。

上述是源码和替身模拟证据，**不是这些问题在真实音频／Android 上的复现**。SDK playId 来自 songId；同歌重播、过期事件和快速切歌还需独立代次保护，不能只匹配歌曲 ID。

## 6. 不能绕过业务层直接拿 URL 播放

低层音频实现接收 URL 后播放，不足以执行完整权益／试听／队列逻辑。当前应用的 `playing` 模型还维护：

- resourceTrackId/playId、当前歌曲／声音、资源时长、加载和恢复状态；
- freeTrialInfo/freeTrialPrivilege、播放等级、音源错误及权限更新；
- 队列、播放模式、切歌、预取、歌词和声音类型；
- 对播放完成、seek、播放／暂停、网络失败的处理。

源码可见高层操作 `playing/resume`、`playing/pause`、`playing/stop`、`playing/setPlayingPosition`、`playing/setVolume`、`playing/switchQuality`、`playing/switchPlayingMode`，以及 `playingList/replaceCurPlayingList`、`playingList/jump2Track` 等队列动作。

这些不是我们将公开的任意 dispatch 接口。需要先确定每个允许动作的合法参数与前置状态，再包成固定提供方操作。尤其上一首／下一首不是低层 AudioPlayer 的单一 HTTP 调用，必须结合当前队列和模式；不能随意猜 action 名或强行跳过权益检查。

试听边界、会员／购买、区域版权、DRM、质量降级、地址到期和能力不足必须按服务规则处理。不能找镜像、解锁下载源、修改 privileges/freeTrialInfo，或以“只是自定义 UI”为由播放本来不允许的部分。出现空地址不能当普通加载成功。

模块 136 的原进度条明确将播放 current 加上 freeTrialInfo.start 来显示歌曲绝对位置，并在 seek 时减去 start，还会拒绝试听范围外的定位。因此**片段音源位置与全曲时间轴不是同一个坐标系**。自定义 UI／系统 seekTo 必须明确范围与映射，不能把片段时长误当全曲时长。

模块 4 的低层 Howler 创建参数没有直接应用 freeTrialInfo/sprite；源模拟也只确认了这一点，不证明服务是否已经裁剪音源。不能假定复用低层甚至高层入口就自动保留所有试听约束，必须核查服务、业务层和音频层的分工。

播放地址 helper 在当前包为模块 82 的 a：按真实运行时选择 encodeType（默认 mp3，特定 A/B 分组改 aac），传默认 trialMode 并解析 data[0]。全曲试听相关参数不得为研究而强制开启。获取地址是否会分配／消耗试听权益尚待确认，所以本轮未获取媒体地址或开始播放。

源码中对播放 URL 有五分钟本地缓存及 flag 检查，但该缓存期限不是普遍有效的 URL 生命周期保证。完整 URL 可能带凭据性参数，不能写入管理员诊断、公共日志或文档。播放器应使用内部不透明音源引用；源地址更新和过期恢复待验证。

## 7. 历史 UI／系统控制接口提案（不代表当前规范）

建议把 UI 与服务细节隔离，至少分为：

- 数据：AccountSummary、PlaylistSummary、Track、TrackRights、PageResult、Lyrics；
- 播放：PlayerSnapshot、QueueSnapshot、PlaybackError；
- 命令：读取账号／分页歌单／歌单曲目／搜索／歌词，playTrack、play、pause、seekMs、previous、next、setQueue、setMode；
- 事件：当前曲目、播放状态、位置／时长／缓冲、队列变更、错误／需要重新登录。

PlayerSnapshot 应包含提供方、会话／曲目／播放代次、状态（idle/loading/playing/paused/ended/error）、毫秒位置与时长、可执行操作和试听范围。UI、通知和系统媒体按钮消费同一个状态源，而不是各自猜播放状态。

这里是当时的接口建模建议，不用于描述当前后台服务/任务是否实现；正式版采用独立服务持有真正网页播放器和固定有界 facade，未把本提案全部实现或公开。UI 和系统命令的 play/pause 应语义明确、尽量幂等，不简单复用所有动作都返回 play_pause 的切换逻辑。

## 8. 登录／数据／权限边界

- 原浏览器数据和登录 Profile 保留，不复制 Cookie/token，不伪造 music.163.com 来源来承载本地自定义 HTML。
- 自定义 native UI 与真正官方运行时可以评估分离，但其 Profile 归属、运行时控制和可靠性尚未决定；file:// 的跨域放开不是方案。
- 不能为适配 UI 实现 PC SDK 的任意 Cookie、文件、下载、网络或 OS 命令桥。仅暴露音乐所需的固定有界操作。
- 页面数据是外部输入；标题、歌词和搜索高亮按数据处理，不能当任意 HTML/脚本执行。封面、音源需校验协议／边界；公开版本继续拒绝 cleartext。
- 网站会员和内容权益不是 Android 权限。音乐播放操作也不应替网站授予剪贴板、相机、麦克风等资格。
- 私有运行时报告和账号信息不进入仓库。管理员诊断只报告能力、计数、状态和错误分类，不公开音源 URL、账号内容或凭据。

## 9. 后续接口／系统控制的验证顺序

1. 已完成：当前公开包一致性、业务入口／音频方法存在性、一次只读账号状态查询；追到实际歌曲搜索延迟模块，并只读验证搜索两页、单曲详情、公开歌单搜索／详情。
2. 待验证：个人歌单只读分页、歌词请求和解析；搜索空结果／错误／过时返回／分页去重，歌曲和歌单权益补充。
3. 待验证：正常授权播放地址返回的质量、时效和试听字段，以及获取地址对试听权益的影响；失败／未登录／无权益情形，不能用“返回 URL”替代权益测试。
4. 已完成 7 项源代码替身模拟；仍待真实验证高层播放动作、事件订阅／解绑、单位、代次与快速切歌竞态。这一步需要明确选定可播放测试内容，模拟结果不能替代设备验收。
5. 原 WebPlayer 非横屏布局已有机主确认的 Firefox 原型；接下来按 Android 接入和系统媒体控制实际需要，确定有界接口契约并验证后台行为。UI 方案确认不代表上述接口和真实设备矩阵已完成。

在前四步没有完成前，不能宣称“接口全部可用”或开始维护整套原站功能。优先最小播放闭环，再逐项增加歌单编辑、推荐等功能。
