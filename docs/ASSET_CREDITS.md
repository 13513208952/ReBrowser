# 界面资源来源

## 搜索引擎图标与 Logo

四个搜索引擎的搜索框图标与上方完整 Logo 都使用打包的品牌图形，不再用设备字体重排名称、用“百”／“D”／“b”占位或手绘近似 G。共用资源位于 `app/src/main/res/drawable-nodpi/rb_brand_*.webp`，背景版的同名样式覆盖位于 `app/src/background/res/drawable-nodpi/`，由 `ReBrowserSearchEngineAssets` 按既有引擎设置选择；不会运行时联网获取。

| 品牌 | 使用资源来源 | 使用规范参考 |
| --- | --- | --- |
| Google | [彩色字标](https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png)、[深色背景白字字标](https://www.google.com/images/branding/googlelogo/2x/googlelogo_light_color_272x92dp.png)、[G 图标](https://www.google.com/images/branding/googleg/2x/googleg_standard_color_64dp.png)，均来自 Google 官网 | [Google Brand Resource Center](https://about.google/brand-resource-center/logos-list/) |
| 百度 | [官网搜索页 2 倍透明 Logo](https://www.baidu.com/img/flexible/logo/pc/result@2.png)（404×132）、[官网 favicon](https://www.baidu.com/favicon.ico) | [百度版权声明](https://www.baidu.com/duty/copyright.html) |
| 必应 | [Bing Fluent 图标](https://commons.wikimedia.org/wiki/File:Bing_Fluent_Logo.svg)、[完整 Fluent Logo](https://commons.wikimedia.org/wiki/File:Bing_Fluent_Logo_Text.svg)，来自 Wikimedia Commons 品牌图形文件；不是此前撤回的 2016 年纯字母轮廓 | [Microsoft Trademark and Brand Guidelines](https://www.microsoft.com/en-us/legal/intellectualproperty/trademarks) |
| DuckDuckGo | [官方新闻页面](https://duckduckgo.com/press)提供的[品牌包](https://staticcdn.duckduckgo.com/press/DuckDuckGo-Brand-Logo.zip)中的 Horizontal 与 Dax Solo PNG | 同一品牌包内的品牌指南；突出显示处增加注册标记 ®，保留完整图形的组成与比例 |

共用资源的处理涉及格式转换、按显示需求缩放及 SVG 光栅化，不重新绘制字形；轻量版保持这些资源不变。WebP 使用无损压缩。四家 Logo 均保留透明通道，直接呈现，不添加白色承载底。

背景版按机主要求增加衍生样式：从 Google 官方白字版本的不透明像素采样 RGB(255,255,255)，仅将 Bing／DuckDuckGo 完整 Logo 的文字分区改为该颜色，保留原透明度，图案分区逐像素不变；百度和所有搜索框图标不变。Google 两种字标缩至原宽高的 75%，居中以透明区域填回原 544×184 画布。所有覆盖图尺寸与共用原图一致，仅影响背景版。这些界面衍生样式不宣称是品牌方提供或授权的新版本。

这些商标／版权仍归各自权利人。公开可下载不等于获得任意用途、再发行或营销授权；上述材料用于核对来源与使用方式，不构成已获授权的声明。界面仅识别用户所选搜索引擎，不表示官方合作、赞助或认可；正式发行前仍需按实际分发场景复核适用规范。

## 经审核的文档展示图

`docs/assets/showcase/rebrowser-new-tab.png`和`docs/assets/showcase/rebrowser-bing.png`分别为v1.0.4手机原生新标签页与必应官网。机主审查原图与裁剪版对照并批准后纳入；仅裁去顶部手机状态栏，其余像素不变，未打码或替换内容。必应官网页面及背景属于第三方网页内容，不是项目打包的网页资源。

`docs/assets/showcase/reoutlook-inbox-example.png`来自v1.0.4实际手机界面截图。机主审查原图与处理版对照并明确同意后，仅纳入最终处理图；联系人、标题、预览、日期、账户缩写及对应头像字母均替换为虚构示例，未修改真实邮箱。字体按原图层级排版；字体文件、原图、处理记录和对照材料不入库。

ReMusic展示图来自机主准备的v1.0.4实际手机界面，逐张对照审查后采用：

| 图片 | 隐私处理 | 保留内容 |
| --- | --- | --- |
| `docs/assets/showcase/remusic-recommendations-example.png` | 替换右上角账号头像，裁去手机状态栏 | 推荐内容、封面与播放控件 |
| `docs/assets/showcase/remusic-playlists-example.png` | 可见私人歌单名替换为“示例歌单1—9”；替换并匹配暗化头像；裁去手机状态栏 | 原有封面、通用菜单、歌单数量与列表布局 |
| `docs/assets/showcase/remusic-player-example.png` | 来源用户名替换为“示例名称”，裁至状态栏与应用的实际边界 | 曲名、专辑、歌手、封面、歌词、来源通用文字与播放控件 |

这些图片仅用于界面展示，不作为真实通信内容或功能验收证据。未修改真实邮箱、歌单或账号头像；保留的曲目、封面、歌词及推荐仍是官网实机内容，不称为虚构数据。微软／Outlook、网易云及其他第三方界面与品牌权利仍归各自权利人，不因本项目代码许可而重新授予，也不表示官方合作或认可。使用图片须保留对应的示例数据说明。

### ReMusic示例头像

前两张ReMusic图片使用[Wikipe-tan face](https://commons.wikimedia.org/wiki/File:Wikipe-tan_face.svg)作为示例头像：原绘[Kasuga](https://ja.wikipedia.org/wiki/User:Kasuga~jawiki)，矢量化[Actam](https://commons.wikimedia.org/wiki/User:Actam)，依据[CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/)使用。

处理包括缩放、圆形裁切和截图合成；歌单侧栏图还按原界面遮罩匹配了暗化效果。头像衍生部分继续按CC BY-SA 3.0提供。该许可不重新授予第三方网页、封面或歌词的权利；示例头像不代表真实账号身份，也不表示角色作者或Wikimedia对项目的认可。原始素材、字体文件、原图和对照材料不入库。

## 专属蓝紫背景

机主提供由本地 AI 从模糊缩略图重新生成的高分辨率照片，用作 ReBrowser 背景特色。应用背景资源仅包含背景版的 3324×2493 压缩 WebP 派生母图；约 1.93 亿像素原图、处理脚本、未处理设备截图及临时预览保存在仓库外，不作为应用资源发布。经审核的文档展示图另见上节。
