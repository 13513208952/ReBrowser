# YouTube 播放器窄兼容

## 原因与用途

在 Google Android WebView 156.0.8078.25 上复现：YouTube 视频标题等内容正常，但播放器黑屏、没有视频元素。播放器脚本在初始化时将其 API（包括播放器事件订阅方法）赋给播放器 DIV，遇到 `Cannot assign to read only property 'addEventListener'` 异常。

原策略为过滤运动／方向事件，把 `EventTarget.prototype.addEventListener` 设为不可写。这也阻止了普通赋值在实例上建立同名播放器 API。补丁兼容这一局部行为，不将全局方法改为可写，不授予新的原生能力。

## 当前行为与代码接口

常规浏览默认开启，无菜单按钮、确认弹窗或额外网站授权步骤。只有 `https://m.youtube.com` 与 `https://www.youtube.com` 的顶层页面进入窄兼容路径，其他页面仍使用原事件策略。

- `ReBrowserPlayerCompatibility()`：常规模式默认开启。
- `ReBrowserPlayerCompatibility(boolean defaultEnabled)`：为以后不同模式提供默认值选择；当前尚未实现无痕模式。
- `ReBrowserActivity.setYoutubePlayerCompatibilityEnabled(workspace, enabled)`：UI 线程上的内部启停入口，供未来模式／防护选择调用，不暴露给网页或管理员协议。按指定工作区 Profile 覆写策略；改变当前运行时策略时保存标签元数据并重建页面，让旧脚本引用失效。
- `ReBrowserPlayerCompatibility.setEnabled(profileName, enabled)`：底层策略选择，影响此 Profile 中允许列表内的两个站点，不改变来源白名单。

运行时覆写不写入持久化存储，重启恢复模式默认值（目前为开启）；删除 Profile 时清除覆写。重建页面不删除网站 Profile，但可能中断表单状态与依赖页面的 Blob 下载。未来无痕模式拟默认关闭，届时应在页面创建前选择策略；本轮没有提前实现无痕功能。

初始调试实现曾采用菜单手动开启，仅用于早期真机验证；经项目所有者澄清后已移除该交互。

## 实现范围

- 仅列出的两个 HTTPS origin（默认端口），拒绝其他子域、非默认端口和带 userinfo 的地址。
- 文档启动时再次检查 origin，并要求顶层文档；不为 iframe 安装兼容接口。
- 全局 `EventTarget.prototype.addEventListener` 保持不可写、不可配置。
- 使用 DIV 原型上的受控访问器，允许当前文档中已连接的 `movie_player` DIV 安装函数型实例封装；Window、Document、原型本身、普通 DIV 和非 DIV 不获得此赋值兼容。
- 实例封装检查接收对象，不能借用到 Window、Document 或其他元素；脱离或迁出文档后拒绝调用。
- 封装入口和原生注册路径继续过滤三类运动／方向事件。事件名只转换一次；原生调用使用预先捕获的 `Reflect.apply`，不依赖网站可修改的 `Function.prototype.call`。
- 摄像头、麦克风、定位、剪贴板、通知、推送和新窗口控制不随开关开放；管理员协议没有新增启用命令。

## 不是安全沙箱

`movie_player` ID 是兼容定位条件，不是可信身份标记。获准来源的脚本可以创建满足条件的 DIV，也能让自己的封装执行普通网页脚本。它仍可能干扰交互、运行广告代码或耗费资源；本补丁不是广告拦截器，也不证明网站脚本无恶意。

原有 JavaScript 策略不是内核级 Content Setting。实例定义、其他执行上下文等网页机制仍需按具体威胁分析，不能宣称所有绕过路径已被排除。这里描述的是新增赋值兼容的范围，不是对网页任意脚本执行的完全限制。

## 验证状态

- 6 个 JVM 测试检查来源、常规默认开启、替代默认关闭、Profile 覆写隔离、清理及生成脚本的允许列表。
- 39 项 Node.js 模拟 DOM 检查覆盖开关、不同来源、iframe、对象范围、方法借用、元素迁移、重定义、名称转换和原生调用路径；运行方式见 [脚本测试说明](../tools/tests/README.md)。
- 初始手动开关版真机调试包：在保持原页面的情况下确认未开启时无视频元素、只读赋值异常；通过菜单开启后视频元素创建，640×360 画面、`readyState=4`、`paused=false`，时间由约 22 秒继续增加至约 53 秒，观察窗口内无初始化异常。
- 真机 12 项 DOM 检查通过，包含全局锁定、局部封装、传感器事件过滤、借用拒绝、普通元素／Window／Document 赋值拒绝、重定义拒绝、能力构造器隐藏和脱离文档拒绝；检查后视频继续播放。
- 默认开启的非调试 R8 Release 包已同签名原地升级安装，无需菜单开启即恢复移动站播放；截图确认画面、字幕推进至 0:12／0:19，播放器控件正常出现。菜单按钮已移除，系统包标志无 DEBUGGABLE。必应新开 Wikipedia 子标签页后的系统返回也再次验证通过。
- 尚待验证：桌面站播放、真实 iframe／来源矩阵、内部关闭接口重建的端到端操作及更广泛的视频兼容性。不把 JVM 或模拟 DOM 结果等同于这些真机结果。

开发日志、截图与诊断记录保存在 `/home/zhao/temp/re_outlook/device-validation/`。初次播放成功后曾暂停安装以避免打断用户，随后在机主确认设备可用后完成了非调试 Release 包替换。
