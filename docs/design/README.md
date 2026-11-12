# 界面设计（docs/design）

AlphaGPS 的 UI/UX 设计方案。这里的内容不改动应用代码，只描述“要做成什么样”；v3 已与 0.2.0 的实现决定对齐，不一致处集中列在 PDF 第 35 页。

## 当前生效版本：v3

| 版本 | PDF | 页数 | 范围 | 状态 |
|---|---|---|---|---|
| **v3**（第三轮修订 · 定稿） | [`ui-v3/ui-design-v3.pdf`](ui-v3/ui-design-v3.pdf) | 35 | 全应用界面重设计（玻璃风格、固定蓝色、亮/暗；仅 Android 12+、仅 arm64、仅 en + zh-rCN、单一构建） | **当前生效**（已与 0.2.0 实现对齐） |

- v3 说明与每页摘要：[`ui-v3/README.md`](ui-v3/README.md)
- v1/v2 旧版设计及其源文件已从仓库移除（v1 数据层规格已由 v3 第 31 页沿用；需要旧版时从 git 历史找回，见提交 `daee69a` 之前）。

v3 经过三轮评审，均为原地修订：第一轮 27 页（提交 `47038fe`），第二轮 30 页（`38e365e`），第三轮定稿 34 页；修订对照在 PDF 第 2、3 页。0.2.0 开发完成后再次原地修订：正文与设备页 / Ⓘ / 日期列表 / 设置 / 日志 mockup 按实现改正，并新增第 35 页“0.2.0 实现偏差与决定”（共 35 页）。
唯一仍待用户确认的是第 27 页“素材来源清单与处置”。应用图标源文件在 [`../brand/`](../brand/)。

## 重建

需要 `python3`、`google-chrome`（无头打印 PDF）与中文字体（Noto / 思源黑体）；装有 `pymupdf` 时会顺带做一次无损压缩。

```bash
cd docs/design/ui-v3 && python3 pages3.py  # v3 → ui-design-v3.pdf
```

脚本生成 `mockups*/*.svg` 与 `index*.html`，再打印成 PDF；这些中间产物一并入库，便于不重建就查看单张 mockup。

## 与 docs/todo.md 的关系

- 设计里的“待确认问题”（Q 编号）和“开发拆分”（W1 文档素材线 / W2 代码线，含 M 编号）**不复制进** `docs/todo.md`。
- 决定开发某个里程碑时，再按 `AGENTS.md` 的待办纪律把当期条目写入 `docs/todo.md`（要做什么 / 验收标准 / 依据位置），完成即删。
- 设计与代码不一致时，**以“v3 PDF 第 35 页：0.2.0 实现偏差与决定”为准**（预览页以 M7 实现为准，完成后再对齐）。第 32 页“与现有实现的差异”与第 33 页“清理与接入清单”是开工前的清单，行号基于 v0.1.5（HEAD `587f510`），仅作追溯。

## 约定

- 新版本新建目录（如 `ui-v4/`），把被取代的版本移入 `history/`，并更新本页表格。
- mockup 为手机竖屏 390×844；文档为横版 1400×1000。

## 组件库在代码中的位置

- 主题与令牌：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/theme/`（`Color.kt` 固定蓝色调色板、`Theme.kt` 统一入口 `CameraGpsTheme` + `AlphaTheme`、`Glass.kt` G1/G2/G3/GV 令牌、`Dimens.kt` 圆角与尺寸、`Type.kt` 的 `AlphaType`（6 档 + Mono）、`Contrast.kt` 对比度计算、`WindowClass.kt` 窗口三档 A/B/C）。
- 玻璃组件：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/`（`GlassBackground`、`GlassSurface`/`GlassCard`/`GlassFloatingBar`/`GlassInsetBlock`、`CapsuleButton`、`GlassIconButton`、`InfoTag`、`StatusLine`/`StatusRow`、`CapsuleDialog`）。
- 预览：`app/src/debug/java/com/saschl/cameragps/ui/preview/GlassComponentsPreview.kt`（亮 / 暗 / 无模糊回退 / 对话框）。
- 对比度用例（第 4 页表）：`sharednew/src/commonTest/.../ui/theme/PaletteContrastTest.kt`。
