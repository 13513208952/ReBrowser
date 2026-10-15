# 音乐固定适配与布局参考

`netease_portrait_reference.js` 保存机主已确认的官方 WebPlayer 非横屏演示布局。对应[方案与验收边界](../../docs/NETEASE_MUSIC_PORTRAIT_LAYOUT.md)。

- 参考源码不是 APK 运行时，也不会自动随方向切换；`python3 -B tools/music/build_android_layout.py` 生成独立的 Android 布局 host 资源。首轮集成状态见[开发记录](../../docs/NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)。
- 仅针对 `https://music.163.com/st/webplayer` 的当前原页面结构；保留原播放器／搜索／导航处理，不导出 Cookie、账号、媒体地址或任意业务调用接口。
- 当前演示入口为 `window.__rbMusicMobileDemo`，含 `status()`、`toggle()`、`cleanup()`、`handleBack()`；清理恢复其拥有的样式、标记及视口元信息。Firefox 演示配置的偏好修改不由页面清理函数恢复。
- 依赖当前网页私有类名和导航模块；后续生产化需要版本校验、失配退回原页面、适用文档与方向范围控制以及设备测试。不要对现有登录 Profile 重跑浏览器启动器。
- 校验语法：`node --check tools/music/netease_portrait_reference.js`；host 模拟：`node tools/tests/test_rebrowser_music_layout.cjs`。这不等于实际 DOM、WebView 功能／播放验收。

## 当前 Android 适配

生成输出为 `app/src/full/assets/rebrowser_netease_layout.js`；仅平板完整窗口横屏不启用适配，其余方向／窗口均适配。`app/src/full/assets/rebrowser_netease_provider.js` 是固定版本、固定状态／动作的原 WebPlayer facade，不是通用模块或 JS 桥；不提供自制音源播放。

```sh
python3 -B tools/music/build_android_layout.py
node tools/tests/test_rebrowser_music_provider.cjs
node tools/tests/test_rebrowser_music_layout.cjs
python3 -B tools/tests/test_rebrowser_music_layout.py
```

Provider 回归使用 fake SDK／audio，不能证明登录、真实播放、锁屏或系统媒体接管。完整状态及三组合构建见[开发记录](../../docs/NETEASE_MUSIC_ANDROID_DEVELOPMENT.md)。

私人页面截图、录屏和 WebDriver 会话资料仍仅保留在本地临时目录，不随此源码提交。
