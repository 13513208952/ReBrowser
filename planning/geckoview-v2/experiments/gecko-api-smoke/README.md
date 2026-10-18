# 隔离Gecko API／R8／本地运行验证（辅助代码）

新包名`io.github.rebrowser.validation`，不是产品v2或旧v1，不属于应用本体总树。无正式签名配置，不读旧keystore，不自动安装/登录/播放/清理。

## 已实际验证与未测范围

- 第一轮：官方Gecko158 AAR完整摘要、真实API编译、AGP9.4.0＋Gradle9.6.0＋SDK37.2＋JDK21的离线R8/资源收缩/LintVital三ABI无签名Release包。
- 第二轮：O29授权手机上临时签名arm64 Release，Native初始化、builtin后台/内容端口、MAIN世界`executeScript`、两唯一contextId的Cookie/localStorage隔离及A被B操作后保持、releaseSession不关闭与重绑、flush请求后状态回调。详见[结构化证据](../../evidence/validation-android-local-synthetic.json)。
- **未测**：官网登录/真实播放/本人验证/Keystore、Popup、SessionState恢复、BFCache/崩溃重建及长时/性能矩阵。一次本地通过不是网站或产品通过。flush为void，不把一次状态回调视为磁盘持久化回执。

当前fixture只绑定`127.0.0.1`临时端口，页面仅合成标记；扩展host匹配仅loopback。每轮新建contextId，不使用清除API。结果输出新验证包自身app-specific external files与`RBV`日志，不导出真实页面或账号。显式启动按钮，或经授权启动Activity并传`RUN_SYNTHETIC=true`；不自动播放。临时签名由隔离工作区外部完成，不把私钥/口令加入源码。

## 隔离复现

将本目录复制到非tmpfs独立工作区；准备固定官方AAR/POM及准确的Guava Android变体镜像、SDK37.2和Gradle9.6。用私有`ANDROID_USER_HOME`、`GRADLE_USER_HOME`、project cache，可用`GRADLE_RO_DEP_CACHE`只读复用既有依赖缓存；不删全局锁，不终止无关进程。

构建前至少3GiB MemAvailable及足够磁盘；JVM1536MiB、单worker、不常驻daemon、临时目录显式非tmpfs。新依赖预备可联网；实际Release离线，不关闭Lint或SDK兼容检查。

```sh
# VROOT/JAVA_HOME/ANDROID_HOME/TMPDIR/GRADLE指向已准备的隔离目录。
"$GRADLE" -p "$VROOT/project" --project-cache-dir "$VROOT/project-cache" \
  :app:prepareValidationDependencies --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1536m -Djava.io.tmpdir=$TMPDIR" \
  -PvalidationMozillaRepo="file://$VROOT/maven"
"$GRADLE" -p "$VROOT/project" --project-cache-dir "$VROOT/project-cache" \
  :app:assembleRelease --offline --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1536m -Djava.io.tmpdir=$TMPDIR" \
  -PvalidationMozillaRepo="file://$VROOT/maven"
```

本fixture现为arm64（约194MB临时签名APK），第一轮三ABI约528MB是历史打包事实，不是正式产品体积。上游native库未做NDK strip，宿主/安装占用不等于APK大小。原三ABI临时包已按O33回收，仅保留来源/摘要/验证记录；当前arm64临时包仍保留。磁盘预备仅移除自己可再生的验证native中间产物；不清产品或无关缓存。

构建教训：AGP9.4要求Gradle≥9.6；minor SDK用`compileSdk { version = release(37) { minorApiLevel = 2 } }`；localhost network config须显式`includeSubdomains=false`，Lint抓到问题后修复，不加baseline绕过。
