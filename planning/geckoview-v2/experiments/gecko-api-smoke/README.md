# 隔离Gecko API／R8打包验证（辅助代码）

仅技术验证，新包名`io.github.rebrowser.validation`；不属于应用本体总树。没有正式签名配置，不读旧keystore，不自动安装/登录/播放/清理。启动后只显示按钮，手工启动分支仅`about:blank`和固定扩展消息。

已验证：官方Gecko158 AAR完整摘要、Java delegate/port API编译、AGP9.4.0＋Gradle9.6.0＋SDK37.2＋JDK21的R8/资源收缩/LintVital/三ABI无签名Release包。**没有Android运行、JNI回调或官网兼容通过证据。**

## 隔离复现要点

1. 将本目录复制到独立非tmpfs工作目录，构建输出不写产品源码目录。
2. 使用官方固定AAR/POM和已核对SHA；本地Maven目录含`org.mozilla.geckoview:geckoview:158.0.20261007201254`。Guava的Android变体使用官方MavenCentral原件镜像，不能用JRE变体冒充。
3. 指定私有SDK根：官方`platforms;android-37.2`；原有build-tools只读复用。不得篡改AAR的minCompileSdk或抑制不兼容校验。
4. 设置私有`ANDROID_USER_HOME`、`GRADLE_USER_HOME`及project cache；可用`GRADLE_RO_DEP_CACHE`只读复用现有依赖缓存。不删全局Gradle锁，不停止其他工程进程。
5. 新依赖预备阶段可联网；实际构建离线、单worker、不常驻daemon、JVM1536MiB、临时目录显式放非tmpfs。构建前至少3GiB MemAvailable，并有足够磁盘；不要为复现自动清其他工程文件。

```sh
# VROOT/JAVA_HOME/ANDROID_HOME/TMPDIR须由执行者指向已准备的隔离目录。
# 本地SDK位置仅写隔离副本local.properties；源目录无机器私有配置。
"$GRADLE" -p "$VROOT/project" --project-cache-dir "$VROOT/project-cache" \
  :app:prepareValidationDependencies --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1536m -Djava.io.tmpdir=$TMPDIR" \
  -PvalidationMozillaRepo="file://$VROOT/maven"
"$GRADLE" -p "$VROOT/project" --project-cache-dir "$VROOT/project-cache" \
  :app:assembleRelease --offline --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1536m -Djava.io.tmpdir=$TMPDIR" \
  -PvalidationMozillaRepo="file://$VROOT/maven"
```

失败/修正事实：AGP9.4要求Gradle≥9.6；9.5不能强行用。9.2.1对SDK37.2只给旧测试范围警告，最终未采用。minor SDK使用`compileSdk { version = release(37) { minorApiLevel = 2 } }`。缺少依赖应先准备，不把Release切联网或关闭Lint来绕过。

原始完整包约528MB（三ABI、上游native库原样），不是正式产品体积结论；未进行NDK strip或安装占用/性能测量。JNI运行、实际extension/content/MAIN世界、context隔离、媒体/Keystore/本人验证仍须明确授权Android目标上的合成测试。
