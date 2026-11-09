# Release APK 无损压缩与统计

Release 开启完整模式 R8 优化/剪枝/混淆、资源裁剪和 DEX ZIP 压缩。完整套件包含原生 Flutter ReVideo 及其插件；保留依赖所需的 Kotlin 元数据、许可证与服务描述，不按旧 Java-only 假设裁剪运行时。

## 输入与资源预算

当前三种输入必须来自本次构建，不从历史 `lite/`、`background/` 残留目录取包：

| variant | 未签名 APK |
| --- | --- |
| `minimalLite` | `app/build/outputs/apk/minimalLite/release/app-minimal-lite-release-unsigned.apk` |
| `minimalBackground` | `app/build/outputs/apk/minimalBackground/release/app-minimal-background-release-unsigned.apk` |
| `fullBackground` | `app/build/outputs/apk/fullBackground/release/app-full-background-release-unsigned.apk` |

按[开发参考](../../docs/DEVELOPMENT.md#有界串行构建)分批构建，`--no-daemon --max-workers=1`、Gradle1536MiB、test fork1/512MiB；`bounded-tests.gradle` 是显式 `-I` 使用的测试限制，不负责 Gradle 堆或系统内存。不要重叠 JVM/R8/Lint 高峰。

## ReVideo 原生模块的有界构建

使用 `tools/build/revideo_native.py` 串行构建原版 Flutter AOT 模块与两种 Android 宿主，不直接启动无空间保护的 Flutter/Gradle 构建。需先为 `revideo/` 完成锁定依赖解析，提供已安装且满足 `pubspec.yaml` 要求的 Flutter SDK、Android SDK 与 JDK。

```sh
TMPDIR=/home/zhao/temp/reoutlook_removeable/agent-runtime python3 -B tools/tests/test_revideo_native_build.py
python3 -B tools/build/revideo_native.py \
  --work-root /run/media/zhao/Windows/tempuseforrebrowser/revideo-native \
  --flutter-sdk /home/zhao/.local/opt/flutter-3.44.6 \
  --stage all
```

SDK 目录名不是版本证据；本机该目录实际为 Flutter 3.44.7 / Dart 3.12.2。`module` 阶段发布 arm/arm64/x64 的 release AOT AAR；`hosts` 阶段消费该产物，构建 compat arm64/x64 与 fullBackground Debug。两种宿主共用同一 release 播放核心，调试宿主不改用 JIT。`--prepare-only` 仅检查空间并配置输出路径，不编译。

- 仅接管空目录，或带本仓库匹配所有权标记的工作区；拒绝逃逸工作区的符号链接。包装器发行包复用既有缓存，其余 Gradle 缓存、构建中间产物、Dart AOT 临时产物与固定摘要的原生库均写入指定工作区。
- 启动前系统盘至少 3GiB、构建盘至少 12GiB 空闲；运行时下限分别为 2GiB / 8GiB，工作区上限 16GiB。触限终止整组构建进程，包括忽略 TERM 的孙进程；收到 TERM 也走关闭与清理路径。
- Gradle 单 worker、堆上限 2048MiB；1024MiB 已在原版插件编译中触发 GC thrashing，不能靠关闭 GC 保护掩盖。不要同时运行模拟器或另一组 JVM 编译高峰。退出时确认所属 Gradle 已停止，再清理可重建中间产物与转换缓存；保留 APK、模块仓库、已校验原生库及依赖。保留工作区超过 8GiB 时额外清除可再下载的依赖缓存。
- AAR 仓库位于 `artifacts/module/host/outputs/repo`，供 `REBROWSER_REVIDEO_MAVEN_DIR` 指向；APK 位于 `artifacts/apk/`。不会删除旧安装包、设备数据、账号数据或其他 Windows 目录内容。
- 本机构建如需 HTTP 代理，可通过常规 JVM `JAVA_TOOL_OPTIONS` 设置 `http.proxyHost` / `https.proxyHost` 等参数；保持证书校验，不加入忽略 TLS 的构建选项。不要把本机代理端口或凭证固化进项目。
- module 阶段在 Android library `finalizeDsl` 将低于 36 的插件 compileSdk 统一到 36，以满足锁定依赖的 AAR metadata；不关闭兼容检查，也不修改宿主 minSdk/targetSdk。宿主通过 JitPack 获取原插件依赖时，仅开放 `com.github.LinXunFeng` 与 `com.github.Yalantis` 两个精确 group。
- 工作目录授权与虚拟机授权分离：VM 镜像、AVD、磁盘、日志及运行临时文件只能位于 `/run/media/zhao/Windows/android15/`。本轮测试环境已迁至其 `revideo-parity/` 子目录，注册文件为 `avd/revideo-pili-parity-35.ini`；原有 `ReBrowserAndroid15` 保持不变。
- 项目工作文件不得写入 `/tmp` 或 `/dev/shm`。构建入口显式设置 `TMPDIR` / `TMP` / `TEMP` 与 JVM `java.io.tmpdir`，并用 `-XX:+PerfDisableSharedMem` 避免 JVM 在系统 `/tmp` 创建性能计数文件。测试命令同样必须显式提供获准的 `TMPDIR`，不能使用 Python 的系统默认临时目录。
- 模拟器 SDK 不完全遵循通用临时变量：必须再设 `ANDROID_TMP=/run/media/zhao/Windows/android15/revideo-parity/tmp`；ADB 使用 `ANDROID_ADB_LOG_PATH` 指向同一 VM 根内的日志文件。已实跑确认崩溃日志转入获准目录，不通过关闭崩溃记录来掩盖路径问题。

上述 9 项资源保护回归已实跑。2026-11-09 原生 AOT AAR、compat arm64/x64 与 fullBackground Debug APK 均构建成功；仅 compat x64 完成一次匿名原生界面与实际视频播放烟测，不代表双宿主、全部画质、账号操作或正式发行验收。功能开发已按机主要求暂停，后续先执行[范围减法](../../docs/REVIDEO_NATIVE_CHECKPOINT.md)。


## 无损 ZIP 优化

`optimize_apk.py` 仅处理**未签名 APK**：逐个 DEFLATE 条目比较原流、zlib9、Zopfli100，保留本轮最小者；不宣称数学全局最优。保留解压字节、顺序、注释和 STORED，尤其 `resources.arsc`。图片不重新编码/降质。拒绝签名、原地写入、重复/加密/不支持/ZIP64 输入。

在仓库外指定目录安装依赖，维护时若已有依赖可复用，不自动下载：

```bash
export PACKAGE_WORK=/home/zhao/temp/re_outlook/package-work
mkdir -p "$PACKAGE_WORK/jvm" "$PACKAGE_WORK/compression-deps"
export TMPDIR="$PACKAGE_WORK/jvm" PYTHONDONTWRITEBYTECODE=1
export PYTHONPATH="$PACKAGE_WORK/compression-deps"
# First-time setup only; not part of routine repository checks:
python3 -m pip install --target "$PYTHONPATH" --no-cache-dir -r tools/build/requirements.txt
python3 -B tools/tests/test_optimize_apk.py
python3 -B tools/build/optimize_apk.py \
  app/build/outputs/apk/fullBackground/release/app-full-background-release-unsigned.apk \
  "$PACKAGE_WORK/full-optimized-unsigned.apk" \
  --iterations 100 --report "$PACKAGE_WORK/full-compression.json"
```

其他两套件分别处理；输出目录不能覆盖原 Beta/已发布目录。

## 对齐、签名与复核

工具来自 SDK build-tools36；设置有效 `JAVA_HOME`/`PATH`，不要将电脑缺少 `java` 误诊为设备故障。原签名身份不能因清理/重打包而更换，私钥/口令不入库；不要自行新建替代 keystore。

```bash
export BT="$ANDROID_HOME/build-tools/36.0.0"
# SIGNING_KEYSTORE and SIGNING_ALIAS refer to the authorized original identity.
"$BT/zipalign" -f -p 4 "$PACKAGE_WORK/full-optimized-unsigned.apk" "$PACKAGE_WORK/full-aligned.apk"
"$BT/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out "$PACKAGE_WORK/full-signed.apk" "$PACKAGE_WORK/full-aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$PACKAGE_WORK/full-signed.apk"
"$BT/apksigner" verify --min-sdk-version 24 --max-sdk-version 27 "$PACKAGE_WORK/full-signed.apk"
"$BT/zipalign" -c -p 4 "$PACKAGE_WORK/full-signed.apk"
python3 -B tools/build/apk_size.py "fullBackground=$PACKAGE_WORK/full-signed.apk" \
  --output "$PACKAGE_WORK/size-breakdown.json"
```

API24–27仅单独核对v2，不降低应用minSdk29。**签名后不得重压缩或修改 APK。** `apk_size.py` 按ZIP压缩占用分组并用内容SHA256识别R8改名的照片/品牌；剩余是ZIP/对齐/签名结构，不是安装数据或RAM。

最终还要核对版本/code、原证书、不可调试/无测试探针、压缩DEX、无备份/明文例外、极简剔除邮件/音乐assets/服务权限与照片有无、全部解压内容不变及素材不降质。保留R8/报告在受限仓库外；统计签名后的APK，再按[发布流程](../../docs/DEVELOPMENT.md#测试与发布)核对公开下载/最新稳定版及旧资产。历史原地升级证据不代表当前所有套件数据迁移已验证。
