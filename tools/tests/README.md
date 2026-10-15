# 网页权限脚本回归检查

`test_rebrowser_permission_script.cjs` 使用 Node.js 内置 `vm` 和模拟 DOM 检查文档启动策略，读取实际编译输出，不维护第二份权限脚本。它覆盖 YouTube 播放器兼容的来源、顶层文档、对象范围、方法借用、元素迁移、事件名称转换及捕获原生方法等情况。

模拟 DOM 测试不能代替真实 WebView 验证，也不是独立安全审计。Java 测试另检查兼容选择的默认值、Profile／origin 隔离、清理和脚本来源白名单。

在项目根目录运行（需要 JDK、Android SDK 和 Node.js）：

```bash
export TMPDIR=/home/zhao/temp/re_outlook/permission-policy-tests
mkdir -p "$TMPDIR/classes"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
./gradlew testMinimalLiteDebugUnitTest testMinimalBackgroundDebugUnitTest testFullBackgroundDebugUnitTest assembleFullBackgroundDebug

"$JAVA_HOME/bin/javac" \
  -cp app/build/intermediates/javac/fullBackgroundDebug/compileFullBackgroundDebugJavaWithJavac/classes \
  -d "$TMPDIR/classes" tools/tests/DumpPermissionScript.java
"$JAVA_HOME/bin/java" -cp "$TMPDIR/classes" io.github.reoutlook.DumpPermissionScript \
  | node tools/tests/test_rebrowser_permission_script.cjs
```

`DumpPermissionScript` 仅输出编译后的固定脚本常量；它不是应用入口，不打包到 APK，不读取设备、账号或认证数据。

## 剪贴板原生入口检查

```bash
./gradlew testMinimalLiteDebugUnitTest \
  --tests io.github.reoutlook.ReBrowserClipboardGestureTest \
  --tests io.github.reoutlook.ReBrowserClipboardBridgeTest \
  --tests io.github.reoutlook.ReBrowserSitePermissionsTest
```

当前 24 项检查直接调用生产桥处理函数，包括 15 天授权下无输入／伪造字段、单次输入、原生键回调、过期、导航、失焦、后台／切页、待批准及过期批准；Robolectric 夹具使用固定测试文本，不读取真实剪贴板。脚本另有 47 项编译输出检查。旧处理函数的仓库外对照两项无输入测试应失败；正常构建不得携带该临时 Gradle source 替换。

`ReBrowserClipboardProbeActivity` 仅在 Debug 源集，由 DUMP 保护；只拦截固定 HTTPS 离线测试页，不接受外部 URL、脚本或 Profile 参数。`ReBrowserClipboardInstrumentation` 使用独立 Profile、隔离授权记录、随机剪贴板样本和原生注入输入。执行前需机主允许、设备解锁，并明确指定设备；同签名覆盖安装 Debug 和对应 androidTest 包，不卸载或清除主应用数据。某些系统会拦截运行器后台启动，需显式前台启动固定 Probe，不能绕过锁屏或授权。

测试页仅在前台保持亮屏，不写系统锁屏设置。系统剪贴板浮层可能造成部分遮挡，测试等待其消失，生产防护仍拒绝；部分后续步骤用原生 Enter。结束前恢复本轮原剪贴板并清除隔离偏好；`ReBrowserClipboardCleanupInstrumentation` 在新进程中只清理私有登记表内的测试 Profile，避免 Chromium 的 in-use 限制，不扫描删除用户环境。早期未登记空样本不作猜测性删除。

最后必须覆盖回原签名非调试 Release 候选，并禁用测试辅助包。本轮真实 WebView 的 13 项断言在 Debug 夹具通过，最终 R8 安装包另核对字节；不能混称最终 Release 全部攻击复测。具体范围见[修复记录](../../docs/CLIPBOARD_GESTURE_SECURITY.md)。

## 音乐生命周期与内存回归

```bash
node tools/tests/test_rebrowser_music_provider.cjs
node tools/tests/test_rebrowser_music_layout.cjs
node --expose-gc --max-old-space-size=96 tools/tests/test_rebrowser_music_memory.cjs
python3 -B tools/tests/test_rebrowser_music_layout.py
```

`music_memory_fixture.cjs` 模拟 DOM 及 Webpack 4 保留 parent packet 的行为，不加载官网代码、账号或网络。回归覆盖 1,000 次页面替换/布局启停、4,800 次正常/永久故障模拟轮询、最多一个待执行登记、有限重试、延迟启动/取消及清理后旧节点 GC。生成一致性另外验证 Android CSS 与不可变批准参考逐字相同。旧 Beta 的仓库外脚本作为负对照必须失败；这些不是实机 PSS/真实播放测试。

全量专属 JVM 的 `ReBrowserMusicLatestTaskTest`、`ReBrowserMusicArtworkTest` 和 `ReBrowserMusicEvaluationTest` 检查阻塞线程下 4,096 次替换仍只有一个排队任务、封面总时限断连与网络/大小边界，以及 JS 超时不放开真实未完成请求。网络与连接均为假对象，不进行真实图片解码验证。

构建前检查可用内存，使用 `--no-daemon --max-workers=1`，测试 fork=1/heap=512MiB；JVM 测试、R8 与 Lint 分批，不把下面历史多任务示例当作可以在桌面满载时同时执行的推荐命令。

## 主软件与原生应用管理 JVM 检查

`ReBrowserApplicationsTest` / `ReBrowserSettingsTest` 覆盖软件名称、三个并列设置页、个性化控件移除、风味默认与显式偏好保留、旧 alias DEFAULT／显式选择和迁移标记、无 provider 的浏览器与邮件直接入口／生命周期，以及固定管理员模式。provider 存在但缺少浏览器特性的状态为模拟检查，不是完整设备兼容验证；不增加公开版特性覆写入口。

## Android 交接 JVM 检查

`ReBrowserPlatformTest` 覆盖 API 29／35 的浏览器与 `.1` 路径注册、外部 Intent 参数隔离、已持有默认浏览器角色时按钮仍可点击、推荐原生默认应用入口／拒绝第三方假冒处理器／旧系统入口兼容、独立 Profile 初始化、原样复制与有界缓存、只读随机 URI／过期／路径拒绝及系统安装组件选择。模拟测试不实际设置系统默认应用、不安装软件，不能替代 QQ 和厂商安装器真机验证。

## 无损 APK 打包检查

`test_optimize_apk.py` 使用临时 ZIP 固件，验证解压内容与顺序、未压缩资源表／图片保留、签名前处理限制、Unicode 文件名与 ZIP 注释。需安装 [构建工具](../build/README.md) 的仓库外 Zopfli 依赖；只运行 `PYTHONDONTWRITEBYTECODE=1 python3 -B tools/tests/test_optimize_apk.py`，不执行 ADB 或读取密钥。最终候选还逐条比对实际 APK 解压字节、签名证书与对齐。

## 管理员 CLI 请求构建检查

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -B tools/tests/test_rebrowser_admin_cli.py
```

此检查构造协议请求并模拟 ADB 调用，覆盖设置三页入口、只读状态、偏好类型、越权参数拒绝、独立管理员任务标志与传输期限；需要工具已有的 `cryptography` 依赖，但不会执行 ADB、读取密钥或改动设备。
