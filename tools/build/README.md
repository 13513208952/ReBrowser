# Release APK 无损压缩与统计

Release 开启完整模式 R8 优化/剪枝/混淆、资源裁剪和 DEX ZIP 压缩。当前 Java 应用不保留 Kotlin 反射运行时，只排除无用 `*.kotlin_builtins` 元数据；未来引入反射必须重评估，不移除许可证、服务描述或现有功能。

## 输入与资源预算

当前三种输入必须来自本次构建，不从历史 `lite/`、`background/` 残留目录取包：

| variant | 未签名 APK |
| --- | --- |
| `minimalLite` | `app/build/outputs/apk/minimalLite/release/app-minimal-lite-release-unsigned.apk` |
| `minimalBackground` | `app/build/outputs/apk/minimalBackground/release/app-minimal-background-release-unsigned.apk` |
| `fullBackground` | `app/build/outputs/apk/fullBackground/release/app-full-background-release-unsigned.apk` |

按[开发参考](../../docs/DEVELOPMENT.md#有界串行构建)分批构建，`--no-daemon --max-workers=1`、Gradle1536MiB、test fork1/512MiB；`bounded-tests.gradle` 是显式 `-I` 使用的测试限制，不负责 Gradle 堆或系统内存。不要重叠 JVM/R8/Lint 高峰。

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
