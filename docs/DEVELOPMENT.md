# 开发参考

用户从[首页](../README.md)和[使用指南](user/REBROWSER.md)开始；当前状态见[项目状态](PROJECT_STATUS.md)，文件归属与清理规则见[维护指南](MAINTENANCE.md)。历史测试数字不自动代表本轮重新验证。

## 身份与结构

主仓库为 [13513208952/ReBrowser](https://github.com/13513208952/ReBrowser)。原仓库原地更名，历史提交、标签和 Releases 保留。显示名为 ReBrowser，安装身份仍是 `io.github.reoutlook`；数据库、Profile、偏好及原签名保持连续，不为整理目录或更名修改它们。

| variant | 内容 | 当前 v1.0.4 code |
| --- | --- | ---: |
| `minimalLite` | 仅浏览器，无照片 | 105 |
| `minimalBackground` | 仅浏览器，有照片 | 105 |
| `fullBackground` | 浏览器、邮件、音乐，有照片 | 1,000,106 |

`fullLite` 禁用；三包不能并存。全量高 code 区间阻止普通全量→极简/旧稳定版降级，完整真实极简→全量迁移矩阵尚未专项验证。不要以卸载、清数据或强制降级制造测试基线。版本以 `app/build.gradle.kts` 为准，历史版本见[发布索引](releases/README.md)。

`src/main` 是共享代码；音乐具体实现和固定 assets 位于 `src/full`，`src/minimal` 的未注册门面不是残留；`src/lite`、`src/background` 决定照片和品牌资源。详细职责见[仓库地图](MAINTENANCE.md#文件归属)。

### 双内核维护与下一发行版

- 下一正式版须同时发行 WebView 与 GeckoView 两个内核版本，并长期并行维护；GeckoView 不是替代或停止维护 WebView 的迁移终点。
- ReVideo 之前的 GeckoView 工作目标仍有效：功能与质量不低于最新 WebView 基线，不能用新播放器的阶段性结果代替既有功能的对等验收。
- 当前允许先集中校准 GeckoView 的 ReVideo 与画中画实际体验。稳定后按相同功能、交互行为和验证标准推进 WebView，不只同步外观；不为提前抽象双内核而阻塞当前真实效果验证。
- 两个版本均完成各自的功能与运行验证后，才进入下一正式版发布准备。独立 `:compat` 开发 APK、单内核检查通过或浏览器审查通过，都不能标记为双版本正式发行完成。


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

v1.0.3发布轮的取消／延期预算与RC2平板证据保留为[历史验收记录](NETEASE_MUSIC_RELEASE_ACCEPTANCE.md)。v1.0.4新增的手机针对性修复与发布范围见[本版记录](releases/v1.0.4.md)；机主决定其余兼容性由日常使用反馈，OEM歌词输出暂缓、网页引擎缩放已放弃，不自动恢复完整矩阵。

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

## 独立 compat / ReVideo 原生迁移

`:compat` 保持独立安装身份 `io.github.rebrowser.compat`，不覆盖 `io.github.reoutlook`。WebView 完整套件与 GeckoView 宿主共用 `revideo/` 原生 Flutter / media_kit 模块；外层浏览器内核不决定 ReVideo 的页面或播放内核。当前正式版本与旧开发包的验收不能代替本轮原生迁移验收。

2026-11-09 按机主要求暂停功能开发。当前完整导入的原版栏目不是已接受的产品范围；后续应在原生底座上做减法，不再恢复旧混合方案。原定布局、额外定制要求、实际验证边界和恢复工作顺序统一记录于 [ReVideo 暂停检查点](REVIDEO_NATIVE_CHECKPOINT.md)。

### 源码与宿主边界

- 直接维护机主提供的 pili20 原版 Dart、资源、锁定依赖和 Android JNI 接入副本；参考仓库 `/home/zhao/pilito20` 保持不变。历史、我的、收藏、稍后再看、订阅、登录、播放器控件及播放设置使用原版页面、控制器和接口，不用官方网页功能页或 Java 近似控件替代。
- 原版普通推荐与搜索独立取数，只有怀旧推荐使用清单。当前导入仍保留上游栏目、多模式和离线归档入口，尚未完成产品收敛；目标仅保留在线怀旧推荐，普通推荐与综合搜索不能依赖 `av_list`，详见暂停检查点。
- 公共 1080P 的 `tryLook` 随实际视频账号登录状态自动计算；没有播放前二选一或单独的免登录画质开关，不伪造会员权限。删除原版调试/代理路径的忽略 TLS 校验逻辑，不提供证书绕过开关。
- 原版单 P 隐藏分 P 区域，多 P 保留原版横向选集；底栏保持白色且不绘制淡紫色选中底块。禁止新增非原版说明性界面文本；确需新增必须先逐条向机主确认，维护约束见 `revideo/AGENTS.md`。
- `:revideo-platform` 保留 JNI 所需的 `com.example.piliplus` 包名、AndroidHelper、MediaHelper 和原版 AudioServiceActivity；两个宿主通过既有 `.ReVideoLauncher` alias 启动非导出的原生 Activity。Activity、音频服务和原版必要辅助组件运行于 `:revideo`，task affinity 与浏览器分离。
- 有限通道 `io.github.rebrowser.revideo/host` 仅承接关闭。根返回和退出定时器暂停并同步进度、刷新已打开的正确类型 Hive 盒，再关闭所属 Activity/音频服务；不退出整个宿主进程。最终关闭前清除指向旧 Dart isolate 的两个 Java 回调，避免重开后调用失效端口。
- 进程隔离不等于文件隔离：支持目录、临时目录、图片缓存、日志及默认下载均使用 `revideo` 子目录；清缓存只枚举所属目录，不删除浏览器下载临时文件、APK handoff、邮件或音乐缓存。原版图片库通过现有 `cacheDirectoryProvider` 接入，不另外实现图片缓存。
- `:revideo` 的原版验证码等辅助 WebView 在初始化前设置独立 `revideo` data-directory suffix，不共享或清理外层 WebView 的 Cookie/WebStorage。核心功能页与播放器仍为原生 Flutter；不导出旧 Gecko Cookie，也不从 PC 审查账号复制登录。
- 删除上游独立应用更新入口，避免下载另一应用的 PiliPlus 安装包；删除会操作整个浏览器包默认链接设置的独立应用入口。成员固定快捷方式显式指向本宿主的 ReVideo alias，继续交给原版 URI 路由处理。
- 旧 Java/Gecko/dash.js ReVideo 实现、专用扩展脚本、图标码位层及其专属测试已移除，不保留双实现或兼容别名。字体、完整许可和代码归属见 [资源来源](ASSET_CREDITS.md)。

### 数据与验证边界

旧开发版的 `revideo_tasks.db`、私有分片及已发布媒体文件不删除；新原版下载格式不自动导入旧 Java 下载任务。原版账号由本模块自己的登录流程建立，不以另一浏览器容器的登录状态冒充。真实账号修改和会员专项必须分别验证；不把匿名页面、静态源码或旧审查服务当成新登录链路证明。

构建必须使用[原生有界构建入口](../tools/build/README.md#revideo-原生模块的有界构建)。中间产物与缓存写入经机主授权的独占工作区；系统盘启动/运行空闲下限 3/2GiB，构建盘 12/8GiB，工作区上限 16GiB。停止整组进程并确认 Gradle 停止后清理可重建产物，保留安装包与固定摘要原生库，不清扫其他 Windows 内容或用户数据。

当前已执行的针对性证据：

| 检查 | 观察 |
| --- | --- |
| `python3 -B tools/tests/test_revideo_native_build.py` | 9 项通过；包含低空间拒绝启动、运行中终止、忽略 TERM 的孙进程、目录逃逸、保留产物、缓存环境变化和扫描期间目录消失。 |
| 独立 Dart / Hive API 烟测 | 确认类型化 Hive 盒不能用无类型查找；正确类型刷新后立即退出进程，重新打开读回全部 1000 条排队写入。该检查不是 Android 界面验收。 |
| 原生 AAR 与双宿主 APK | release AOT AAR（arm/arm64/x64）构建成功；compat arm64/x64 和 WebView fullBackground Debug 三个 APK 构建成功，构建结束确认无 Gradle daemon。compat x64 签名摘要与旧开发包一致。 |
| Android 原生页面与播放器 | API 35 隔离模拟器安装 compat x64 成功；实际看到原生推荐、我的、设置及播放器设置；`MacBook` 搜索返回真实结果，`BV1NS4t6oEXQ` 在原生播放器呈现视频与弹幕，进度推进。未完成实际轨道 1080P、重开持久化、PiP、多 P、账号历史与 WebView 宿主实跑验收；旧实现证据不得沿用。 |

下一正式版仍须同时通过 WebView 与 GeckoView 的完整质量门槛，长期并行维护。本轮迁移不自动代表正式版就绪；大会员专项按机主要求暂缓，不擅自消耗硬币或执行账号写入。

