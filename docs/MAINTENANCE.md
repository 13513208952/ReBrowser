# 仓库维护与清理指南

本页规定文件归属、保留规则与低成本检查，不批准新功能、密钥迁移或额外设备测试。当前状态见[项目状态](PROJECT_STATUS.md)，构建见[开发参考](DEVELOPMENT.md)。

## 文件归属

| 位置 | 职责/是否可清理 |
| --- | --- |
| 根 README、PRIVACY、SECURITY | 用户入口、隐私和安全边界；不能用历史 Beta 状态描述当前正式版 |
| `app/src/main` | 共享浏览器、邮件、管理、数据和音乐状态/策略；邮件在极简由 R8 剔除，不可仅凭位于 main 就判定泄漏 |
| `app/src/full` | 真正音乐实现、固定 provider/layout assets、服务/权限；极简不得混入 |
| `app/src/minimal` | 极简 Manifest 裁剪和音乐编译门面；看似空实现也不能直接删除 |
| `app/src/lite` / `background` | 背景能力、照片/品牌覆盖；同名资源是有意风味差异，不按文件名合并 |
| `app/src/debug` / `androidTest` | DUMP 保护探针及固定 instrumentation，不进生产 APK；音乐自目标辅助包不重启 Release 进程，manifest 的回归运行器必须在音乐运行器之前，防 AGP 覆写 |
| `app/src/test` / `testFull` | 共享/全量 JVM；断言已否决缩放命令被拒绝，不是残留功能 |
| `tools/admin` | 浏览器固定命令、邮件独立授权导出及离线解密；不是重复管理员工具，不合并信任根/权限 |
| `tools/build` | 无损 APK ZIP、体积统计、依赖说明及显式测试内存 init script；**这是源码，不能随 build 目录批量删除** |
| `tools/music` | 不可变批准参考与 Android 生成器；只改获准生成逻辑，不改黄金参考 |
| `tools/tests` / `maintenance` | 离线回归与只读检查/严格白名单生成物清理；无设备自动操作 |
| `docs/user` | 用户真实操作：浏览器、邮件、音乐 |
| `docs/releases` | 逐版本来源、包体/哈希、证据和限制；保留历史措辞的时点，不用后来结果改写旧验收 |
| `docs/assets/showcase`（批准后使用） | 仅机主已审核原图／隐私处理对照并明确批准的最终展示图，不放原图、对照、诊断或可还原图层 |
| 其他专题 docs | 当前机制/边界；历史提案必须有醒目时点标记，现状不能同时写在多处相互冲突 |
| `gradle/wrapper` | 必需 wrapper JAR/配置，不是应删除的普通二进制构建输出 |
| `app/build`、根 `build`、项目 `.gradle` | 可再生输出/本地元数据；确认构建空闲、保存必要报告后才清理 |

不因目录名 `re_outlook`、内部包名、类名前缀或数据库旧名批量更名。安装/数据/签名连续性优先于命名美观。

## 文档权威与历史

1. 机主最新明确决定优先；保留[机主音乐原话](MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md)不改写。历史讨论不自动成为实施授权。
2. 当前产品说明：用户指南、专题机制、当前源码；验证必须区分实现、fake/JVM、设备状态、可听输出、最终 APK。
3. 当前验收：对应[版本记录](releases/README.md)和[音乐验收表](NETEASE_MUSIC_RELEASE_ACCEPTANCE.md)。短时 PSS/PLAYING/唤醒锁不等于长期无泄漏或独立声音证明。
4. 过往报告放[开发历史](DEVELOPMENT_HISTORY.md)或链接 Git 快照，不在“当前状态”不断追加完整历史。原发布标签/资产不移动或覆盖。

发布变更至少同步：首页/用户指南、隐私安全（若边界变化）、当前状态、相关专题、版本索引和版本记录；历史版本只保留其当时证据。运行只读一致性检查，再人工核对语义。

## 不可变与私有资料

- `docs/MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md` SHA256：`974ba8c6a8d287f41f46a32de8fdd57b1aaf409a99ddf877861d78751fbb56f4`。
- `tools/music/netease_portrait_reference.js` SHA256：`4ee8ce6f44c9fd26f009babfe996746c86c440e384890b8b808c270a9e13e0ec`。
- Android 变换在生成器中，生成资源是 APK 源输入，**不是可以删除的临时构建文件**；生成一致性/CSS 回归约束外观。
- `/home/zhao/temp/re_outlook/` 是受限工作/发布/证据根，不按“temp”或版本新旧推定可删。原图、密钥、账号/登录环境、媒体、设备诊断、R8/发布证据与共享依赖默认保留。
- 保留的测试 Firefox 曾被宿主 OOM 杀死；不得重启其启动器、删锁、覆盖恢复文件、修改 Profile 或关闭普通 Firefox。不要为清理修改 swap/zram/OOM 或结束桌面程序。
- 不发布Cookie／token、私钥、认证DOM、未处理的账户关联媒体／截图、音频URL、真实数据库／堆或原始设备日志；诊断公开只匿名摘要。
- 实际界面展示是机主新授权的单独流程：先在仓库外制作同色覆盖优先、必要时高斯模糊的处理版与原图对照，明确批准最终文件后才可放入`docs/assets/showcase/`。批准一张处理图不授权上传原图／对照，也不授权其他图片；[逐张审查规则](SCREENSHOT_REVIEW.md)。

`.gitignore` 防止常见包/密钥/数据库/日志/堆误入库，不是充分的秘密扫描或安全审计。不要因被忽略就认为本地文件没有保留价值。

## 安全清理流程

1. `git status --short` 确认源码状态，盘点目录及磁盘；禁止 `git clean -fdx`、按 `**/build` 删除、任意根目录递归清理或自动删除仓库外旧版本。
2. 确认 IDE/Gradle/R8/Lint/测试没有运行；不结束进程来制造空闲。
3. 本次输出仍承担发布/回归证据时，先复制 reports、test-results、mapping 和大小/哈希清单到仓库外私有根。保留公开最终资产原件；检查副本内容/哈希成功后再删。
4. 使用[白名单清理工具](../tools/maintenance/README.md)，先默认 dry-run，再审阅并显式 `--apply`。只清 `app/build`、根 `build`；项目 `.gradle` 需额外选择，用户级 Gradle/Maven/SDK 缓存不动。
5. 清理后再次检查源码、黄金摘要、本地链接与 diff。编译输出缺失是预期结果，不伪造 `NO-SOURCE` 测试成功；后续需要编译权限策略时按开发参考先重建 Full Debug。

清理不会回收手机应用数据，也不清浏览器登录、重装应用、升级 WebView 或触发功能测试。用途不明、被其他版本引用或唯一证据先保留待人工判断。

## 本轮整理范围

v1.0.3 发布后进行的维护不改变 Android 生产代码、版本号、签名或已发布 APK：

- 当前状态从历史长报告中分离，以 Git 历史保留旧报告，不再复制维护第二份。
- 更新三套件/音乐用户说明、正式版隐私安全边界；标记早期布局/接口/个性化讨论为历史。
- 统一构建示例到现行 variant，加入有界测试 init script，替换多高峰旧示例。
- 加入只读链接/摘要/源集检查及默认 dry-run 的严格清理工具，配离线夹具回归。
- 已清理 `app/build`、根 `build` 和项目 `.gradle` 约 **180.8 MiB** 可再生输出/缓存；先保留 **285 个**报告/映射/测试证据文件（内容约11.1 MiB）到仓库外受限维护根，逐文件SHA256核对成功后才删除原生成目录。不清私有历史发布或账号资料。
- 维护工具18项夹具、权限编译脚本47项、provider/layout/memory/generation/CLI/打包离线回归通过；这些不是新增设备或最终APK验收。

本轮**没有重跑设备专项、昂贵三套件 R8/Lint/JVM 矩阵或触发发布**。手机/Android16专项仍取消，高成本矩阵仍延期；维护脚本结果不取代 v1.0.3 原有发布证据。
