# Release APK 无损压缩与统计

应用 Release 开启完整模式 R8 优化／剪枝／混淆、资源裁剪和 DEX ZIP 压缩。当前 Java 应用没有 kotlin-reflect，R8 最终没有保留 Kotlin 运行时类；只排除不再需要的 `*.kotlin_builtins` 编译元数据，不移除依赖版本、许可证、服务描述或现有功能。如果未来引入 Kotlin 运行时反射，必须重新评估该排除项。

`optimize_apk.py` 仅处理**未签名 APK**，对原本 DEFLATE 的条目比较原压缩流、zlib level 9 和 Zopfli（100 次迭代），采用最小者。所有原条目和解压内容保持不变；原本 STORED 的资源不改为压缩，尤其 `resources.arsc`，以保留 Android 的直接访问约束。图片不重新编码，也不降低画质。现有 WebP 再作 ZIP 压缩反而更大。

在项目指定临时目录安装 Python 压缩依赖，不打包进应用或提交环境：

```bash
export TMPDIR=/home/zhao/temp/re_outlook/release-v0.4.5-alpha/jvm
export PYTHONPATH=/home/zhao/temp/re_outlook/release-v0.4.5-alpha/compression-deps
mkdir -p "$TMPDIR" "$PYTHONPATH"
python3 -m pip install --target "$PYTHONPATH" --no-cache-dir -r tools/build/requirements.txt
PYTHONDONTWRITEBYTECODE=1 python3 -B tools/tests/test_optimize_apk.py
python3 -B tools/build/optimize_apk.py \
  app/build/outputs/apk/lite/release/app-lite-release-unsigned.apk \
  /home/zhao/temp/re_outlook/release-v0.4.5-alpha/lite-optimized-unsigned.apk \
  --iterations 100 --report /home/zhao/temp/re_outlook/release-v0.4.5-alpha/lite-compression.json
```

然后依次 `zipalign -f -p 4`、`apksigner sign`、`apksigner verify --verbose --print-certs` 和 `zipalign -c -p 4`。**签名后禁止重新压缩或修改 APK。**

最低 Android 10／API 29，无需旧 JAR v1 签名；签名显式使用 `--v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true`，原证书不变。不要因排除旧签名而更换密钥。工具默认验证可能只报告所选平台的最强方案，额外可用 `verify --min-sdk-version 24 --max-sdk-version 27` 验证 v2（这是验证工具参数，不降低应用 minSdk）。

`apk_size.py LABEL=SIGNED_APK --output REPORT.json` 按 ZIP 条目的压缩大小分组，并用内容 SHA-256 识别 R8 改名的照片和品牌图形；剩余总字节归到 ZIP 结构、对齐和 APK 签名块。它不报告安装后的数据、缓存或内存，不读取设备或私钥。

每次最终构建需保留 R8 报告，逐条核对打包前后的解压内容、两版照片有无、签名证书、对齐、非调试 Manifest 与无明文例外的网络配置，再统计**签名后的** APK。对于算法输出，不宣称数学意义的全局最优；实际逐条选用本轮候选中的最小流。
