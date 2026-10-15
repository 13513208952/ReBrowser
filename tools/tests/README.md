# 自动回归与证据边界

所有命令从仓库根目录运行。日常文档/工具维护优先离线检查，不自动构建、联网安装依赖、调用 ADB 或恢复已取消设备专项。当前发布证据见[v1.0.3](../../docs/releases/v1.0.3.md)，如何串行限资源构建见[开发参考](../../docs/DEVELOPMENT.md)。

## 第一层：离线、无设备检查

先设置仓库外 `TMPDIR`（本维护机器不使用 `/tmp`）；Python依赖为现有 `cryptography` 和[Zopfli](../build/README.md)，不在测试中自动安装。Node 使用小堆，顺序执行：

```bash
export TMPDIR=/home/zhao/temp/re_outlook/host-checks
mkdir -p "$TMPDIR"
export PYTHONDONTWRITEBYTECODE=1
# PYTHONPATH must include your existing external compression-deps if not installed globally.
python3 -B tools/maintenance/check_repository.py
python3 -B tools/tests/test_repository_maintenance.py
node --max-old-space-size=96 tools/tests/test_rebrowser_music_provider.cjs
node --max-old-space-size=96 tools/tests/test_rebrowser_music_layout.cjs
node --expose-gc --max-old-space-size=96 tools/tests/test_rebrowser_music_memory.cjs
python3 -B tools/tests/test_rebrowser_music_layout.py
python3 -B tools/tests/test_rebrowser_admin_cli.py
python3 -B tools/tests/test_optimize_apk.py
```

| 检查 | 实际覆盖 | 不代表 |
| --- | --- | --- |
| repository maintenance，18 项夹具 | 本地链接/锚点、不可变参考、版本入口/源集否定；工作树根/清理白名单/跟踪文件/软链接、默认不删/忙碌拒绝，apply只验证临时夹具 | 内容语义审计、秘密扫描、真实仓库删除或设备测试 |
| provider fake SDK/DOM/audio | 固定官方动作/回调关联、登录/播放门禁、seek/喜欢/层级可见性 | 实际官网、音频可听输出、账号/权益 |
| layout fake DOM，6 场景 | 宿主适配/撤销、方向范围、固定路由及失败关闭 | 所有真实 DOM/窗口/旋转 |
| memory fake，7 场景 | 1,000 次页面替换/启停；4,800 次健康/故障轮询；单待执行登记、有限重试、延迟取消/旧节点GC | 实机 PSS、官网全部缓存或长期无泄漏 |
| generation，3 项 | Android生成一致性、批准CSS逐字相同、固定宿主范围且不 dispatch 播放 | 生产播放器/布局全验收 |
| admin CLI，11 项 | 固定请求/偏好类型/越权拒绝/独立任务/期限，mock ADB | 实际管理员授权；不读密钥不调设备 |
| optimizer，3 项 | 解压内容/顺序/STORED/Unicode/注释及拒绝签名/原地处理 | 实际最终APK签名/对齐/裁剪，需要另外检查 |

旧 Beta 的仓库外布局/provider 作为内存负对照曾失败；正常测试不自动获取/加载这些旧脚本或真实登录环境。

## 第二层：JVM 与编译后的权限脚本

按开发参考的 `gradle_one` 函数分别执行三套件测试，不能并行各套 JVM/R8/Lint。仓库 `tools/build/bounded-tests.gradle` 显式限制 fork1/512MiB，Gradle堆1536MiB/worker1；本机每批前至少3GiB可用内存。

v1.0.3 发布时全量217、两种极简各183 JVM，零失败/错误/跳过；这是对应版本证据，不是文档维护时又执行过全矩阵。

- `ReBrowserApplicationsTest`/`ReBrowserSettingsTest`：三页/草稿、主软件名称、默认/显式偏好、alias迁移、无provider原生回退、固定模式以及指定音乐说明移除。缺能力状态为模拟，不是全厂商验证。
- `ReBrowserMusicLatestTaskTest`/`ArtworkTest`/`EvaluationTest`/`PlaybackAuthorizationTest`：阻塞线程4,096次替换仍最多一个排队任务、封面时限断连/网络尺寸边界、JS超时不释放实际请求、明确原生播放授权。假连接不是真实图片解码/设备请求。
- `ReBrowserPlatformTest`：API29/35注册/原生默认应用/外部Intent/`.1`固定交接与有界缓存；不设置真实系统角色或安装文件。
- 剪贴板针对性：发布修复时24项覆盖原生入口五秒单次输入、无输入/伪造/遮挡/过期、导航/失焦/后台及待批准；夹具使用固定文本，不读真实剪贴板。

权限脚本只读取**实际编译常量**，不能直接空输入运行Node或维护第二份JS快照。清理build后先重建Full Debug类：

```bash
# gradle_one is defined in docs/DEVELOPMENT.md; one bounded batch only.
gradle_one compileFullBackgroundDebugJavaWithJavac
set -o pipefail
mkdir -p "$TMPDIR/policy-classes"
"$JAVA_HOME/bin/javac" \
  -cp app/build/intermediates/javac/fullBackgroundDebug/compileFullBackgroundDebugJavaWithJavac/classes \
  -d "$TMPDIR/policy-classes" tools/tests/DumpPermissionScript.java
"$JAVA_HOME/bin/java" -cp "$TMPDIR/policy-classes" io.github.reoutlook.DumpPermissionScript \
  | node --max-old-space-size=96 tools/tests/test_rebrowser_permission_script.cjs
```

47项检查含origin/顶层文档/对象范围/方法借用/元素迁移/事件转换及原生方法捕获。`DumpPermissionScript`不是应用入口，不打包，不读设备、账号或认证数据。

## 第三层：设备测试，仅获机主明确授权后

**本轮手机/Android16专项已取消，高成本矩阵后期。** 此处说明保留夹具的职责，不是执行指令或清理旧账号环境的授权。登录、验证码、解锁、USB安装允许和系统确认由机主处理。

- Debug `ReBrowserClipboardProbeActivity`由DUMP保护，只拦截固定离线HTTPS页，无任意URL/脚本/Profile参数。Clipboard instrumentation用隔离Profile/授权、随机样本与原生输入，结束恢复剪贴板/清理登记的测试偏好；Cleanup instrumentation仅删除其私有登记内的测试Profile，不扫描用户环境。
- 历史设备剪贴板13项断言在Debug夹具通过，不是最终R8全部攻击复验；系统浮层遮挡仍拒绝，不绕过锁屏或授权。测试页保持亮屏仅属于Debug夹具，不是生产策略。
- `ReBrowserMusicSystemInstrumentation`自目标辅助包只采用 `MEDIA_CONTENT_CONTROL`，固定包/tag及播放/暂停/前后曲/seek/喜欢/旧控制器检查，输出哈希身份和数值状态，不取账号内容。manifest先列主回归运行器、再音乐运行器，避免AGP覆写。旧MediaController通过不等于旧通知PendingIntent完整矩阵。
- 必须显式设备序列号、原签名覆盖，不卸载/清数据/强降来制造基线；测试后恢复非调试Release并停用辅助包。无关历史Profile、真实账号、锁屏和最新默认浏览器选择不动。

真机状态/进度、fake音频、JVM、短时PSS、最终包字节和独立可听输出是不同证据。[完整限定范围](../../docs/NETEASE_MUSIC_RELEASE_ACCEPTANCE.md)、[剪贴板修复记录](../../docs/CLIPBOARD_GESTURE_SECURITY.md)。
