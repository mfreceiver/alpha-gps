# AlphaGPS 关于页 / 开源许可页标准文本块

> 供前端接入使用（W1 产出，不改 strings）。每个文本块给出 EN 与简体中文。名称规范：产品名
> **AlphaGPS**；保持小写的只有包名 `cn.vectory.alphagps`、仓库名 `alphagps`、文件名
> `alpha-gps-*`。

## 1. Fork 声明

**EN**

> AlphaGPS is a fork of [Saschl/alpha-gps](https://github.com/Saschl/alpha-gps), modified by
> mfreceiver since November 2026. It is an open-source application licensed under the
> GNU General Public License v3.0.

**zh-CN**

> AlphaGPS 是 [Saschl/alpha-gps](https://github.com/Saschl/alpha-gps) 的分支，由 mfreceiver
> 自 2026 年 11 月起修改。本应用为开源软件，采用 GNU 通用公共许可证第 3 版（GPL-3.0）授权。

## 2. GPL-3.0 说明

**EN**

> This program is free software: you can redistribute it and/or modify it under the terms of the
> GNU General Public License as published by the Free Software Foundation, version 3. The complete
> license text is included with the source code.

**zh-CN**

> 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证（第 3 版）条款重新
> 发布或修改它。完整许可证文本随源码提供。

## 3. 修改声明

**EN**

> Modifications by mfreceiver, starting November 2026: personal customization — no app store
> distribution, no remote capture, no crash reporting, no data collection. All changes are
> recorded in the public repository history.

**zh-CN**

> 修改者 mfreceiver（2026 年 11 月起）：个人定制版——不通过应用市场分发，无遥控拍摄、无崩溃
> 上报、不收集任何数据。全部改动记录在公开仓库历史中。

## 4. 源码获取

**EN**

> Source code (GPL-3.0):
> - GitHub: https://github.com/mfreceiver/alpha-gps (issues: https://github.com/mfreceiver/alpha-gps/issues)

**zh-CN**

> 源码（GPL-3.0）：
> - GitHub：https://github.com/mfreceiver/alpha-gps（反馈：https://github.com/mfreceiver/alpha-gps/issues）

## 5. 第三方组件许可清单

**EN**

> AlphaGPS is built on the following third-party components:

**zh-CN**

> AlphaGPS 构建使用了以下第三方组件：

| 组件 | 许可证 | 说明 |
|---|---|---|
| libheif / libde265 | LGPL-3.0-or-later | HEIF 解码，以 git 子模块源码方式随仓库构建（heif/） |
| Kotlin / Kotlin 标准库、kotlinx.serialization、kotlinx.coroutines、kotlinx-datetime | Apache-2.0 | |
| Jetpack Compose（BOM、Material3 多平台版）、Compose Multiplatform | Apache-2.0 | |
| AndroidX：core-ktx、activity-compose、lifecycle、navigation3、navigationevent、appcompat、room、sqlite、exifinterface、core-splashscreen | Apache-2.0 | |
| Material Components for Android（com.google.android.material） | Apache-2.0 | |
| Material Icons / Material Symbols | Apache-2.0 | 应用内功能图标 |
| Haze（dev.chrisbanes.haze:haze / haze-blur 2.1.0，传递依赖 haze-utils 与 androidx.tracing 2.0.5，均为 Apache-2.0） | Apache-2.0 | 玻璃背景模糊，仅 sharednew 模块依赖（ui/components/glass） |
| Timber | Apache-2.0 | 本机日志 |
| Accompanist（permissions） | Apache-2.0 | |
| diamondedge:logging（KmLogging） | Apache-2.0 | 多平台日志门面 |
| Google Play services location、Play Core review（review / review-ktx） | — | 已随 gplay 变体删除（flavor 合并为单一 foss 行为），不再出现在任何构建中 |

> 上游使用的 Sentry（崩溃上报）相关组件已随 Sentry 移除（W2）从依赖中删除，不再列于此清单。

## 6. 商标免责声明

**EN**

> AlphaGPS is not affiliated with, endorsed by, or sponsored by Sony. “Sony” and “Alpha” are
> trademarks of Sony Group Corporation. All other trademarks are the property of their respective
> owners.

**zh-CN**

> AlphaGPS 与 Sony 无关联，未获得 Sony 的认可或赞助。“Sony”与“Alpha”是 Sony Group
> Corporation 的商标。其它商标均为其各自所有者的财产。

## 7. 支持上游作者（全应用仅此一处）

**EN**

> AlphaGPS exists thanks to the original upstream project by Saschl. If you find it useful,
> consider supporting the **original upstream author (Saschl)**:
> [Buy Me A Coffee](https://buymeacoffee.com/wj8tism4dq)

**zh-CN**

> AlphaGPS 得益于 Saschl 的原上游项目。如果你觉得有用，可以考虑支持**原上游作者
> Saschl**：[Buy Me A Coffee](https://buymeacoffee.com/wj8tism4dq)

## 8. 图标素材来源

**EN**

> The app icon is original artwork redrawn for this version (sources in the project's
> `docs/brand/`), released under GPL-3.0. The About page states this credit.

**zh-CN**

> 应用图标为本定制版原创重绘（源文件见项目 `docs/brand/`），采用 GPL-3.0。应用内“关于”页注明该来源。
> `docs/brand/` 为本项目自有品牌图标的 SVG 源。

## 9. 应用内展示位置

- 关于页：fork 声明、修改声明、GPL-3.0 全文、开源许可入口（与第 5 节清单一致，`LicenseCatalog`）、
  源码与反馈（仅 GitHub）、隐私入口、支持上游作者（第 7 节）、商标免责声明（第 6 节）、图标来源（第 8 节）。
- 帮助页不放赞助链接；第 7 节的链接全应用仅在关于页出现。
