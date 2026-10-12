# 网页权限脚本回归检查

`test_rebrowser_permission_script.cjs` 使用 Node.js 内置 `vm` 和模拟 DOM 检查文档启动策略，读取实际编译输出，不维护第二份权限脚本。它覆盖 YouTube 播放器兼容的来源、顶层文档、对象范围、方法借用、元素迁移、事件名称转换及捕获原生方法等情况。

模拟 DOM 测试不能代替真实 WebView 验证，也不是独立安全审计。Java 测试另检查兼容选择的默认值、Profile／origin 隔离、清理和脚本来源白名单。

在项目根目录运行（需要 JDK、Android SDK 和 Node.js）：

```bash
export TMPDIR=/home/zhao/temp/re_outlook/permission-policy-tests
mkdir -p "$TMPDIR/classes"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
./gradlew testLiteDebugUnitTest testBackgroundDebugUnitTest assembleLiteDebug

"$JAVA_HOME/bin/javac" \
  -cp app/build/intermediates/javac/liteDebug/compileLiteDebugJavaWithJavac/classes \
  -d "$TMPDIR/classes" tools/tests/DumpPermissionScript.java
"$JAVA_HOME/bin/java" -cp "$TMPDIR/classes" io.github.reoutlook.DumpPermissionScript \
  | node tools/tests/test_rebrowser_permission_script.cjs
```

`DumpPermissionScript` 仅输出编译后的固定脚本常量；它不是应用入口，不打包到 APK，不读取设备、账号或认证数据。

## Android 交接 JVM 检查

`ReBrowserPlatformTest` 覆盖 API 29／35 的浏览器与 `.1` 路径注册、外部 Intent 参数隔离、已持有默认浏览器角色时按钮仍可点击、推荐原生默认应用入口／拒绝第三方假冒处理器／旧系统入口兼容、独立 Profile 初始化、原样复制与有界缓存、只读随机 URI／过期／路径拒绝及系统安装组件选择。模拟测试不实际设置系统默认应用、不安装软件，不能替代 QQ 和厂商安装器真机验证。

## 无损 APK 打包检查

`test_optimize_apk.py` 使用临时 ZIP 固件，验证解压内容与顺序、未压缩资源表／图片保留、签名前处理限制、Unicode 文件名与 ZIP 注释。需安装 [构建工具](../build/README.md) 的仓库外 Zopfli 依赖；只运行 `PYTHONDONTWRITEBYTECODE=1 python3 -B tools/tests/test_optimize_apk.py`，不执行 ADB 或读取密钥。最终候选还逐条比对实际 APK 解压字节、签名证书与对齐。

## 管理员 CLI 请求构建检查

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -B tools/tests/test_rebrowser_admin_cli.py
```

此检查仅构造协议请求，覆盖设置三页入口、只读状态、偏好类型及越权参数拒绝；需要工具已有的 `cryptography` 依赖，但不会执行 ADB、读取密钥或改动设备。
