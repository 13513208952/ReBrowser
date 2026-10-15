# 开发参考

用户从[首页](../README.md)和[使用指南](user/REBROWSER.md)开始；当前状态见[项目状态](PROJECT_STATUS.md)，文件归属与清理规则见[维护指南](MAINTENANCE.md)。历史测试数字不自动代表本轮重新验证。

## 身份与结构

主仓库为 [13513208952/ReBrowser](https://github.com/13513208952/ReBrowser)。原仓库原地更名，历史提交、标签和 Releases 保留。显示名为 ReBrowser，安装身份仍是 `io.github.reoutlook`；数据库、Profile、偏好及原签名保持连续，不为整理目录或更名修改它们。

| variant | 内容 | 当前 v1.0.3 code |
| --- | --- | ---: |
| `minimalLite` | 仅浏览器，无照片 | 104 |
| `minimalBackground` | 仅浏览器，有照片 | 104 |
| `fullBackground` | 浏览器、邮件、音乐，有照片 | 1,000,104 |

`fullLite` 禁用；三包不能并存。全量高 code 区间阻止普通全量→极简/旧稳定版降级，完整真实极简→全量迁移矩阵尚未专项验证。不要以卸载、清数据或强制降级制造测试基线。版本以 `app/build.gradle.kts` 为准，历史版本见[发布索引](releases/README.md)。

`src/main` 是共享代码；音乐具体实现和固定 assets 位于 `src/full`，`src/minimal` 的未注册门面不是残留；`src/lite`、`src/background` 决定照片和品牌资源。详细职责见[仓库地图](MAINTENANCE.md#文件归属)。

## 环境

- JDK 17 或兼容 Android Gradle Plugin 的较新 JDK，Java 源码目标 17；Android SDK/build-tools 36。
- Gradle 使用仓库 wrapper；Node.js 18+、Python 3.10+ 用于离线工具和测试。
- Python 的 `cryptography` 用于管理员请求构造；Zopfli 依赖见[打包工具](../tools/build/README.md)。依赖安装在仓库外，不提交虚拟环境或第三方源码。

```bash
git clone https://github.com/13513208952/ReBrowser.git
cd ReBrowser
export JAVA_HOME=/path/to/compatible-jdk
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export WORK=/home/zhao/temp/re_outlook/local-build
mkdir -p "$WORK/jvm"
export TMPDIR="$WORK/jvm"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
export PYTHONDONTWRITEBYTECODE=1
```

本维护机器只在 `/home/zhao/temp/re_outlook/` 放临时文件，不使用 `/tmp`。其他机器自行指定磁盘目录。

## 有界、串行构建

曾因三套件 JVM/R8/Lint 与桌面浏览器重叠造成宿主 OOM。不要重新使用一次启动全部高峰的旧命令。每批开始前检查内存，Linux 本机至少 3 GiB `MemAvailable`；不足就停止，不杀桌面应用、不改 swap/zram/OOM。

以下函数限制 Gradle 堆 1536 MiB、worker 1，仓库 init script 限制测试 fork 1/heap 512 MiB；不是修改系统或运行时应用策略。

```bash
gradle_one() {
  python3 -B -c 'from pathlib import Path; m=int(next(x.split()[1] for x in Path("/proc/meminfo").read_text().splitlines() if x.startswith("MemAvailable:"))); assert m >= 3*1024*1024, "Insufficient MemAvailable; stop this batch"' || return
  ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' \
    -I tools/build/bounded-tests.gradle "$@"
}
# Run one phase at a time; do not background these loops.
for variant in MinimalLite MinimalBackground FullBackground; do
  gradle_one "test${variant}DebugUnitTest" || break
done
# Separate phase, only when a new build is necessary:
for variant in MinimalLite MinimalBackground FullBackground; do
  gradle_one "assemble${variant}Release" || break
done
# Separate phase:
for variant in MinimalLite MinimalBackground FullBackground; do
  gradle_one "lint${variant}Debug" || break
done
```

Debug 需要时单独执行，例如 `gradle_one assembleFullBackgroundDebug`。不能仅为文档变更重跑全部设备/R8矩阵。`--no-daemon` 仍可能启动一次性 Gradle 进程，命令完成后再开始下一批。

产物路径：`app/build/outputs/apk/{minimalLite,minimalBackground,fullBackground}/release/`，文件名对应 `app-minimal-lite-release-unsigned.apk`、`app-minimal-background-release-unsigned.apk`、`app-full-background-release-unsigned.apk`。旧 `lite/`、`background/` 输出是历史残留，不是当前构建。Release 启用完整 R8、资源裁剪和压缩 DEX，但 Gradle 输出尚未签名。

## 测试与发布

1. [仓库检查](../tools/maintenance/README.md)：离线链接、不可变参考、源集与当前版本入口。
2. [自动回归](../tools/tests/README.md)：JVM、实际编译权限脚本、fake DOM/SDK、CLI 和打包测试；各类证据分开。
3. [Release 流程](../tools/build/README.md)：未签名优化→对齐→原证书 v2/v3→验签/对齐/内容/裁剪检查→签名包统计。签名后不修改 APK。
4. 发布独立新标签，不移动旧标签或替换旧资产。草稿下载先核对，发布后再未认证下载逐字节/哈希验证，核对 latest 与旧版本不变。
5. 只发布批准 APK、校验文件和匿名文档；密钥、登录环境、设备日志、截图、原图、原始媒体地址和 R8 私有材料不上传。

历史 Android Debug 证书仅维持覆盖升级连续性；APK 本身必须不可调试，不宣称已换生产签名。最终保留 manifest/网络/套件裁剪/资源质量/签名/对齐报告及 R8 依据。

## 设备操作与验收预算

本轮 v1.0.3 发布已完成：RC2 平板限定短测，最终全量包手机覆盖安装及身份核对；机主取消手机/Android 16 专项，高成本矩阵后期。维护文档不自动恢复这些测试，详见[验收记录](NETEASE_MUSIC_RELEASE_ACCEPTANCE.md)。

后续任何获准真机更新只用 `adb -s SERIAL install -r APK`，不卸载/清数据/强降；先锁定重要临时环境，保留最新默认浏览器选择。登录、验证码、扫码、解锁、USB 安装权限和系统确认由机主处理，不绕过。测试 YouTube 前告知机主准备代理；`.1` 安装器仍由机主自行测试。设备辅助包不应常驻启用。

## 实现与协议索引

| 主题 | 参考 |
| --- | --- |
| 音乐生命周期/固定动作/内存与恢复边界 | [音乐开发](NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)、[机主原话](MUSIC_SUBAPPLICATION_OWNER_VISION_VERBATIM.md)、[登录要求](NETEASE_MUSIC_LOGIN_REQUIREMENTS.md) |
| 三页设置/套件入口/无 provider 原生回退 | [设置](REBROWSER_SETTINGS.md) |
| 双层标签、命名 Profile 与控制器职责 | [浏览器设计](REBROWSER_EXPLORATION.md) |
| 邮件桥/缓存/检查点/迁移 | [邮件架构](REOUTLOOK_ARCHITECTURE.md) |
| 一次性精确挑战、三级授权、双根 | [管理协议](REBROWSER_ADMIN_CONTROL.md)、[CLI](../tools/admin/README.md) |
| 网站授权/后台策略/剪贴板输入边界 | [网站权限](REBROWSER_SITE_PERMISSIONS.md)、[剪贴板修复](CLIPBOARD_GESTURE_SECURITY.md) |
| 默认浏览器/外部链接/安装交接 | [Android 交接](REBROWSER_ANDROID_HANDOFF.md) |
| 历史与资源 | [历史规则](REBROWSER_HISTORY_PLAN.md)、[资源来源](ASSET_CREDITS.md) |
| 窄播放兼容及已否决试验 | [YouTube](YOUTUBE_PLAYER_COMPATIBILITY.md)、[假缩放移除](REBROWSER_LAYOUT_ZOOM.md) |

管理员保持固定白名单和有界状态，不增加任意脚本、模块、网络、文件/数据库/偏好、Intent 或安装命令，不授予网页能力、不启用音乐，浏览器根不能授权邮件导出。历史讨论不等于当前实施授权。
