# 子应用独立设置与存储清理（开发中）

本页描述当前开发实现，**公开v1.0.4尚未包含这些入口**。未更改安装身份、版本码、签名或已发布APK；也未在机主手机上执行数据清理。README机主原话不因本轮功能开发而改写。

## 入口与设置归属

应用管理中，ReOutlook与音乐各自保留原有启用／桌面入口和“进入”控件，增加独立齿轮按钮。齿轮打开原生二级设置页，即使子应用未启用或WebView不可用，也不需要先启动网页来查看设置。

- ReOutlook设置：查看已知本机资料体积、刷新摘要、清除缓存、清除数据。
- ReMusic设置：相同存储操作，并将原应用管理页的音乐源选择移入这里。保留选择控件及固定音源白名单，不添加音源计划说明，也不通过设置页自动开启或播放音乐。
- 二级Activity仅在全量包注册，`exported=false`；极简包没有该Activity或清理实现。请求目标只允许`outlook`／`music`，不接受包名、Profile名、文件路径或任意Intent。

## 统计口径

已知本机资料体积只统计固定归属的数据库、SQLite日志及设置文件，不打开数据库、不读取账号或邮件内容，也不创建WebView／Profile。

系统WebView没有公开完整的按Profile独立容量接口。因此网页缓存与站点数据容量明确标为不可独立统计；协议返回`null`与`completeSizeAvailable=false`，**不以全应用缓存或数字0冒充子应用完整容量**。统计不是安卓系统应用存储页的替代品。

## 清缓存与清数据不能混用

| 操作 | 范围 | 不做的事 |
| --- | --- | --- |
| 清缓存 | 关闭对应子应用，向其固定Profile绑定的WebView提交资源缓存清理 | 不主动移除Cookie、持久化站点存储、本机邮件或恢复资料；不调用清数据API冒充清缓存 |
| 清数据 | 等待该Profile浏览数据清理回调，再清理固定归属的本机资料和独立设置 | 不删除官网账号／云端内容，不清其他子应用或浏览环境，不重置应用启用／桌面入口、系统授权、管理员密钥或全局浏览器设置 |

关闭窗口会丢失未保存编辑及临时页面会话，是否仍需重新认证由网站决定。WebView的`clearCache`没有磁盘完成回调，界面只报告“已提交清理请求”，不谎报磁盘清理已完成。

操作使用既有独立Profile：邮件为Default，音乐为固定音乐Profile；先核对Profile绑定，不接受外部输入的Profile名。数据清理使用该Profile自己的`WebStorage`及AndroidX `DELETE_BROWSING_DATA`能力，没有全局Cookie／WebStorage删除回退，也不按WebView内部目录命名猜测归属。功能能力不足时拒绝操作，不先删除一半本机资料。

本机清理白名单：

- 邮件：`mail_cache.db`及SQLite配套日志；`account_preferences`、`ui_preferences`。
- 音乐：`rb_music_sources_v1`、`rb_music_resume_netease_v1`、`rb_music_lyric_position_v1`。

原生资料清理仅在操作栅栏有效、对应资源全部退出后执行。它不是任意目录遍历／删除接口。

## 生命周期与中断恢复

1. 用户须在对应原生设置页明确确认，弹窗显示对象和影响。清理触摸控件过滤遮挡触摸，Android12及以上使用既有隐藏覆盖窗口权限。
2. 栅栏禁止新增该子应用资源；关闭邮件窗口或停止音乐任务／服务。邮件已接收写入先排空，再关闭数据库并释放租约；关闭的helper不能因旧回调重新打开数据库。
3. 不强制结束受保护的邮件管理员导出。若写入／导出未在有界等待内结束，尚未提交的新清理会失败，不删除数据。
4. 清数据意图在停止运行前持久化。提交异步删除后不因任意超时就重新放行；失败、进程中断或异常日志保持该子应用受保护，原生设置页可再次确认继续清理。
5. 只有网页清理回调、本机白名单清理和状态保存成功后才重新放行；其他浏览环境及子应用不受此栅栏影响。清理协调器持应用Context，界面回调弱引用，不以Activity销毁伪装取消已提交的清理。

## 管理员工具增强

协议保持v2、DUMP保护、双永久根、一次性精确挑战、授权级别和审计边界，新增两个一级操作：

- `GET_APPLICATION_STORAGE`：固定子应用的已知容量、未知容量标记、能力与清理状态摘要。无账号、邮件、Cookie、曲目、歌单名、数据库内容或任意文件读取。
- `OPEN_APPLICATION_SETTINGS`：打开该子应用的原生二级设置。音乐仅允许进入原生设置，不允许管理员启用音乐、进入业务播放器或选择任意音源。

`applicationId`必须是精确白名单字符串，纳入签名请求、机主确认目标和审计。不增加后台`CLEAR_APPLICATION_DATA`／`CLEAR_APPLICATION_CACHE`或应用启用操作；清理仍由机主在二级设置确认。极简包不宣告或接受这些子应用操作。

```bash
python3 tools/admin/rebrowser_admin.py application-storage outlook
python3 tools/admin/rebrowser_admin.py application-storage music
python3 tools/admin/rebrowser_admin.py application-settings outlook
python3 tools/admin/rebrowser_admin.py application-settings music
```

密钥或本机凭据选择沿用[管理员协议](REBROWSER_ADMIN_CONTROL.md)。邮件导出仍是独立RSA签名加前台系统凭据流程，未放宽。

CLI同时支持`--serial SERIAL`，或沿用`ANDROID_SERIAL`。查询、执行、结果轮询与挑战清理使用同一设备目标；包括失败后的挑战清理在内，每次ADB调用均有30秒传输限时。超时不自动重发状态修改命令，清理失败不覆盖原始错误；显式`clear`失败会返回非成功状态。不修改宿主的持久环境或ADB配置。

## R8报告分析与源码清理

新增只读工具：

```bash
python3 tools/maintenance/analyze_r8_usage.py
python3 tools/tests/test_r8_usage_analysis.py
```

同时读取三个套件的`usage.txt`，记录报告哈希，区分字段、方法、构造器与被移除／合并的类；合并重复类段，防止丢失早先的内联字段记录。工具明确标为`triage-only`，不删除文件，不把跨套件同时被移除的符号当成可删源码。

本轮先审查2026-10-16的v1.0.4基线报告，并保存在仓库外。常量内联、私有构造器消除、类合并、极简排除和仅测试使用的入口都不是直接删源码的依据。例如`CaptureScript`与音乐封面工作器的外层类可以在报告中消失，但其代码仍承担实际功能。

实际清理仅删除`ReBrowserAdminAuthorizer`中九个旧操作名转发常量：全仓源码／测试／工具已无引用，真实白名单和常量仍由`ReBrowserAdminProtocol`维护。不移除授权根、生成音乐资源、套件边界或测试观察接口；这些旧常量本来就被R8消除，不据此声称APK变小。

## 开发快照的离线验证

2026-10-17：全量295项、两个极简各184项，合计**663项JVM测试，零失败／错误／跳过**。三套件Lint均为零错误，保留警告数为48／37／37；三个Release变体的R8任务通过，并核对二级设置及邮件／音乐实现未进入极简输出。管理员CLI14项、R8分析器5项、仓库维护18项及现有音乐离线回归通过。

这些是源码开发与离线验证结果，不是新APK发布，也不是机主账号上的真实清理验收。

## 验证边界

- 单元回归覆盖原生状态机、固定目标、文件白名单、等待写入、未知容量、异步回调、重复请求、失败重试与中断日志；使用Robolectric隔离数据和fake WebView后端，不读取／清理机主数据。
- 管理员CLI和R8分析器有无设备的离线请求／报告测试。
- 编译、JVM、Lint与R8结果不替代真实WebView的多Profile缓存／Cookie保留和清数据隔离验收。真实验证须用独立测试身份／Profile与明确授权，不用机主邮件或音乐账号做破坏性样本。
- 本轮没有覆盖安装、正式签名打包或发布APK；公开v1.0.4及README中的现行界面图保持原样。
