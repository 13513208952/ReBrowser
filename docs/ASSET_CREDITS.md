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

## 专属蓝紫背景

机主提供由本地 AI 从模糊缩略图重新生成的高分辨率照片，用作 ReBrowser 背景特色。仓库仅包含背景版的 3324×2493 压缩 WebP 派生母图；约 1.93 亿像素原图、处理脚本、设备截图及临时预览保存在仓库外，不作为应用资源发布。
