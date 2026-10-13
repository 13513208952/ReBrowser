# ReBrowser 设置与应用管理（v1.0.0 / v1.0.1-beta）

设置为三个同级页面，顺序固定：**常规 → 隔离与权限 → 应用管理**。顶部页签、独立滚动位置、公共“保存并返回”、主页／搜索引擎草稿与恢复机制保留。v0.4.4 的个性化页已删除，背景与近期访问控件不搬到其他页。

| 页面 | 范围 |
| --- | --- |
| 常规 | 主页、默认搜索引擎、桌面版网站、全局／视频方向、用户手动打开系统默认应用设置 |
| 隔离与权限 | JavaScript、第三方 Cookie、强行提升主标签页、嵌入的网站能力、后台策略、有效授权与撤销 |
| 应用管理 | 同 APK 的子软件；目前为 ReOutlook 名称、桌面入口开关、直接进入按钮；显示运行环境是否满足要求 |

## Beta 设备与方向

v1.0.1-beta 常规页增加自动／手机／平板持久选择。自动使用最大显示范围与物理 DPI，以 7 英寸为暂定分界；数据不可用默认手机，不禁用手动平板选择。完整窗口平板使用 FULL_USER 并遵守系统方向锁定；多窗口／画中画不强制方向，不比较窗口边界。手机原偏好仍保存，平板下相应方向控件不生效。设置固定竖屏，API 36 兼容属性仅限定设置 Activity；系统仍有最终决定权。

管理员只允许固定 `deviceType` 枚举及只读诊断。新增部分以 35 JVM、10 CLI 和一台平板的窗口／管理员检查为范围，不沿用下面 v1.0.0 完整测试数字作为 Beta 覆盖。详见[设备策略](REBROWSER_DEVICE_MODE.md)。

## 默认背景与保留偏好

- 没有显式背景偏好时：lite 纯白；background 使用专属蓝紫照片。两版默认开启近期访问，列表为空仍隐藏。
- 升级不删除或覆盖已保存的背景／近期访问选择。已有纯白或关闭近期访问仍生效。
- 风味互换保留偏好；lite 不含照片，记住 `exclusive` 时实际仍显示纯白。
- 照片和品牌图形未重新编码或降质，不联网获取近期图标、新闻。
- 管理协议 v2 的有界 `newTabBackground` / `newTabRecent` setter 及 `settings.personalization` 诊断字段保留用于兼容；该字段不代表仍有个性化设置页。

## 子软件与升级入口

子软件是同一安装包内的模式，不是插件或独立安装。新安装仅默认显示主软件 ReBrowser 的入口；ReOutlook 桌面入口默认隐藏，但可从应用管理进入。其开关仅控制 launcher alias，MainActivity 不禁用。

升级迁移由 Application 初始化和 `MY_PACKAGE_REPLACED` 接收器执行一次：无迁移标记时通过安装／更新时间识别覆盖安装，旧 alias 的 DEFAULT 状态转为显式 ENABLED，保留旧版本默认可见状态；显式 ENABLED／DISABLED 不改动。成功后写入持久迁移标记，后续升级不重新应用旧默认。PackageManager 使用 `DONT_KILL_APP`；启动器图标刷新时机与排列由系统启动器决定。

ReOutlook 开关从常规页迁至本页，浏览器菜单内重复的邮件切换按钮删除。启用的 ReOutlook 桌面入口仍可直接进入邮件。

## WebView 能力不足

原生应用管理不创建浏览器工作区、命名 Profile 或 WebView。浏览器需要 provider、`MULTI_PROFILE`、`WEB_MESSAGE_LISTENER`、`DOCUMENT_START_SCRIPT` 全部满足；启动入口与外部链接入口在浏览器初始化前检查，不满足时转到应用管理。

ReOutlook 不要求 Multi-Profile，仅使用 Default Profile；provider 存在时可以尝试进入。不把邮件 Profile 借给浏览器。若连 provider 都没有，邮件进入按钮禁用，独立邮件入口也回到原生管理。检测可用不保证 Outlook 网站、账号登录或每家厂商兼容。Android 最低要求仍是 API 29；系统版本满足不等于 WebView 能力满足。

## 管理员与授权

`OPEN_SETTINGS.section` 仅接受 `general` / `isolation` / `applications`；无参数默认为常规。旧个性化 Activity 草稿会安全回到常规；新的管理员 `personalization` 请求不再接受。

新增一级固定操作：`GET_APPLICATION_STATUS`、`GET_RUNTIME_STATUS`、`OPEN_APPLICATION_MANAGEMENT`、`OPEN_MODE`（仅 `browser` / `outlook`）。原生状态、能力和设置入口在浏览器不可用时仍能查询／打开；需要浏览器的操作明确返回 `browser-runtime-unavailable`。不增加入口 setter、任意组件／Intent、网站能力启用／授权、Cookie 或安装命令。

原生设置独立于管理员临时任务；重用已有设置页时重新选择请求页签并保留草稿，不通过 CLEAR_TOP 结束其上方的浏览器。CLI 为每次授权启动独立临时任务，并给 ADB 调用设置 30 秒传输期限，避免把旧任务带到前台却未执行新请求。

权限禁用仍清除对应网站授权；重新启用不恢复授权，不替用户申请 Android 权限。真正无痕、主题和用户图片均未实现。

## v1.0.0 验证边界

两版各 146 项 JVM 回归通过，覆盖三页布局、草稿／恢复／重复路由、背景默认与显式偏好保留、旧入口 DEFAULT／显式状态迁移、无 provider 原生路由及固定管理员模式。WebView 缺能力回退为模拟验证，不能外推到所有厂商。

真机从 v0.4.5-alpha 同签名覆盖升级，最终两风味均已安装验证；固定偏好、入口与最新默认浏览器选择保留。独立持久测试环境的 Profile／标签标识和测试 Cookie 跨升级及风味互换保留。隐藏邮件桌面入口后仍可点击进入 MainActivity；测试后恢复入口状态，只清理测试环境及其历史，原有零历史状态恢复。系统默认应用入口打开原生页面，不替用户选择。`.1` 文件测试仍由机主自行执行；完整邮件账号／数据库迁移、平板／多窗口和完整网页矩阵未覆盖。
