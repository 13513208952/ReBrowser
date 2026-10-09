# 网页权限脚本回归检查

`test_rebrowser_permission_script.cjs` 使用 Node.js 内置 `vm` 和模拟 DOM 检查文档启动策略，读取实际编译输出，不维护第二份权限脚本。它覆盖 YouTube 播放器兼容的来源、顶层文档、对象范围、方法借用、元素迁移、事件名称转换及捕获原生方法等情况。

模拟 DOM 测试不能代替真实 WebView 验证，也不是独立安全审计。Java 测试另检查兼容选择的默认值、Profile／origin 隔离、清理和脚本来源白名单。

在项目根目录运行（需要 JDK、Android SDK 和 Node.js）：

```bash
export TMPDIR=/home/zhao/temp/re_outlook/permission-policy-tests
mkdir -p "$TMPDIR/classes"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
./gradlew testDebugUnitTest assembleDebug

"$JAVA_HOME/bin/javac" \
  -cp app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes \
  -d "$TMPDIR/classes" tools/tests/DumpPermissionScript.java
"$JAVA_HOME/bin/java" -cp "$TMPDIR/classes" io.github.reoutlook.DumpPermissionScript \
  | node tools/tests/test_rebrowser_permission_script.cjs
```

`DumpPermissionScript` 仅输出编译后的固定脚本常量；它不是应用入口，不打包到 APK，不读取设备、账号或认证数据。
