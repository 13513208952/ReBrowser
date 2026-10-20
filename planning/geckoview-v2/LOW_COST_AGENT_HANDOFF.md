# 历史低成本agent交接 · D01（仅原6目标）

> O75/O78后此文不再是当前任务入口，也不要求worker阅读额外交接。当前D02一次42个新目标以[任务记录](records/low-cost-agent-assignments.json)和[剩余全量风险](records/remaining-file-risk-assessment.json)为准；下文只保留原6交付的历史边界。

## 先读这些

- 实现基线：`dd41e3e7b1fb0aa6225837a309d9ac8d9a15f485`（R46）。
- 权威任务范围：[low-cost-agent-assignments.json](records/low-cost-agent-assignments.json)。**先完整阅读其中sharedRules及领取任务的条目。**
- [总树](APPLICATION_CODE_TREE.md)的`[LOW:L01]`至`[LOW:L06]`是预留任务，仍为`PLAN`，不是已经实现或模块已经完成。
- 仅领取下表文件，不接管来源/权限/密钥/持久化/Gecko/管理员/connector主链。主agent保留集成与安全边界决策。

| ID | 唯一运行代码目标（包根`app/src/main/java/io/github/rebrowser/`） | 限定的低难度工作 |
| --- | --- | --- |
| L01 | `subapps/SubapplicationRegistry.java` | Outlook/音乐的不可变固定目录与精确查找，不读写启用状态、不打开组件 |
| L02 | `ui/SettingsSections.java` | 设置分组与行的只读展示描述，未知配置显示未读取，不造设置默认值/策略 |
| L03 | `ui/HistoryAdapter.java` | 最多100行的原生历史列表、复用/失效与用户意图回调，不查询/修改历史库 |
| L04 | `ui/DownloadsAdapter.java` | 原生下载列表、忠实显示`TaskSnapshot`各轴，不下载、取消执行、发布文件或发Intent |
| L05 | `ui/BookmarkView.java` | 原生收藏/文件夹组件与操作意图，不保存/删除记录或执行URL导航 |
| L06 | `system/DeviceWindowState.java` | 复用`ViewportPolicy`组合本窗口只读事实，不改系统状态或按机型门控 |

测试只新增各条目指定的`testPath`。不要修改已有运行代码；不要另造基础类、服务桩或Activity来填依赖。

## 可只读参考的现有实现

- `app/src/main/java/io/github/rebrowser/ui/BrowserToolbar.java`：`Binding`的源/窗口/线程及退役检查。
- `app/src/main/java/io/github/rebrowser/ui/TabPanel.java`、`WorkspacePanel.java`：有界行、捕获精确frame、旧回调失效。
- `app/src/main/java/io/github/rebrowser/core/TaskSnapshot.java`：任务14合法组合；客户端超时、取消请求、外部效果、实际结束、持久化分轴。
- `app/src/main/java/io/github/rebrowser/engine/ViewportPolicy.java`：本窗口几何和既有尺寸分类。
- `app/src/test/java/io/github/rebrowser/ui/NativeWindowModelsTest.java`：真实生产模型HOST测试与Android视图实测的区别。

这些参考文件由主agent维护，**不要为方便修改它们**。若确有必要，提交建议与最小接口需求，由主agent协调。

## 不越过的边界

1. 低难度是**指定切片**：渲染、静态目录、只读事实。不是“整个历史/下载/设置/书签模块简单”。不得伪造已保存、已发布、已授权、已登录或任务已结束。
2. UI只回传精确行token、原revision与用户意图；`Binding`不是管理员权限，展示DTO不是领域凭据。主域稍后仍要重新授权、检查来源/epoch/目标和当前状态。
3. 缺失backend不应补空实现，也不要硬编码`true`、伪数据或给当前未配置Root开调试后门。可实现本文件展示Row/Frame，但不能冒充未来Store的权威DTO。
4. 禁止迁移/删除旧版数据、访问凭据、创建正式Root/签名、登录、播放、设备操作、系统权限提示和子应用占用/清理功能。
5. 不增加依赖、WebView/Compose/AndroidX、不改共享Gradle/Manifest/准入清单/生成树/公共协议。不要覆盖其他agent的文件或统一格式化仓库。

## 工作区、验证与交付

- 使用独立工作区，以基线提交为代码输入；本轮D01文档可由owner一起提供。不要在共享`main`上reset/clean/stash或覆盖别人的更改。
- 交付**指定路径的未提交diff/patch**、公共API说明、实际命令/测试结果、尚未执行的Android范围、遇到的真实接口缺口。**不要自行commit/push。**
- O64仍有效：每commit至少10个实质runtime代码文件。这次仅分配6个，不应硬凑额外文件；主agent会与核心变更合并成合规提交。测试、文档和生成树不计运行代码数。
- 每条bash命令以`date -Iseconds`开头，`umask 077`，Python用`python3 -B`且不生成`__pycache__`。
- `TMPDIR=/home/zhao/temp/re_outlook/geckoview-reimplementation/jvm`，不可把大量输出放tmpfs `/tmp`。
- Java/manifest/JUnit只能在新鲜`MemAvailable>=3GiB`且磁盘能容纳预计32MiB新增＋1GiB保留时串行离线运行；不能删全局缓存、停其他进程、下载依赖或扩展成APK/JNI/dex/R8/设备验证。
- 主agent会把完成的文件登记到`implementation-readiness.json`并运行实际SDK编译/汇总测试；worker不要给所有当前source-bound证据换hash或虚报“全量通过”。未登记的新源文件在隔离工作区中暂不能跑整体树一致性审查，是待集成状态，不应改校验器绕过。
- 模型测试通过、API编译通过、实际View渲染、真实Android/Gecko行为分别报告；不因单元测试全绿就声明完整模块或产品可用。

若资源不足或缺接口，保留已有可验证成果并准确报告阻碍；不反复重试或扩大权限/文件范围。
