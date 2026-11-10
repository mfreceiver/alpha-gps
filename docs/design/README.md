# 界面设计（docs/design）

AlphaGPS 的 UI/UX 设计方案。**纯设计交付**：这里的内容不改动应用代码，只描述“要做成什么样”和“与现有实现差在哪”。

## 当前生效版本：v3

| 版本 | PDF | 页数 | 范围 | 状态 |
|---|---|---|---|---|
| **v3**（第三轮修订 · 定稿） | [`ui-v3/ui-design-v3.pdf`](ui-v3/ui-design-v3.pdf) | 34 | 全应用界面重设计（玻璃风格、固定蓝色、亮/暗；仅 Android 12+） | **当前生效** |
| v2 | [`history/ui-status-enhancement-v2.pdf`](history/ui-status-enhancement-v2.pdf) | 17 | 相机/照片界面改版 | 历史，已被 v3 取代 |
| v1 | [`history/ui-status-enhancement.pdf`](history/ui-status-enhancement.pdf) | 14 | 状态信息增强 | 历史；其数据层规格仍有效 |

- v3 说明与每页摘要：[`ui-v3/README.md`](ui-v3/README.md)
- v1/v2 说明：[`history/ui-status-enhancement/README.md`](history/ui-status-enhancement/README.md)；v1 数据层规格（字段、阈值、写入位置）：[`history/ui-status-enhancement/SPEC.md`](history/ui-status-enhancement/SPEC.md)，v3 第 31 页沿用。

v3 经过三轮评审，均为原地修订：第一轮 27 页（提交 `47038fe`），第二轮 30 页（`38e365e`），第三轮定稿 34 页；修订对照在 PDF 第 2、3 页。
唯一仍待用户确认的是第 27 页“素材来源清单与处置”。应用图标源文件在 [`../brand/`](../brand/)。

## 重建

需要 `python3`、`google-chrome`（无头打印 PDF）与中文字体（Noto / 思源黑体）；装有 `pymupdf` 时会顺带做一次无损压缩。

```bash
cd docs/design/ui-v3 && python3 pages3.py                          # v3 → ui-design-v3.pdf
cd docs/design/history/ui-status-enhancement && python3 pages2.py  # v2 → ../ui-status-enhancement-v2.pdf
cd docs/design/history/ui-status-enhancement && python3 pages.py   # v1 → ../ui-status-enhancement.pdf
```

脚本生成 `mockups*/*.svg` 与 `index*.html`，再打印成 PDF；这些中间产物一并入库，便于不重建就查看单张 mockup。
入库的 v1/v2 PDF 经过无损结构压缩，重建出的文件会更大，内容相同。

## 与 docs/todo.md 的关系

- 设计里的“待确认问题”（Q 编号）和“开发拆分”（W1 文档素材线 / W2 代码线，含 M 编号）**不复制进** `docs/todo.md`。
- 决定开发某个里程碑时，再按 `AGENTS.md` 的待办纪律把当期条目写入 `docs/todo.md`（要做什么 / 验收标准 / 依据位置），完成即删。
- 设计与代码不一致时，以“v3 PDF 第 32 页：与现有实现的差异”与“第 33 页：清理与接入清单”为对照；第 32 页行号基于 HEAD `587f510`（v0.1.5），第 33 页行号来自盘点、开工前复核。

## 约定

- 新版本新建目录（如 `ui-v4/`），把被取代的版本移入 `history/`，并更新本页表格。
- mockup 为手机竖屏 390×844；文档为横版 1400×1000。

## 组件库在代码中的位置

- 主题与令牌：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/theme/`（`Color.kt` 固定蓝色调色板、`Theme.kt` 统一入口 `CameraGpsTheme` + `AlphaTheme`、`Glass.kt` G1/G2/G3/GV 令牌、`Dimens.kt` 圆角与尺寸、`Type.kt` 的 `AlphaTextStyles`、`Contrast.kt` 对比度计算、`WindowClass.kt` 窗口三档 A/B/C）。
- 玻璃组件：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/`（`GlassBackground`、`GlassSurface`/`GlassCard`/`GlassFloatingBar`/`GlassInsetBlock`、`CapsuleButton`、`GlassIconButton`、`InfoTag`、`StatusLine`/`StatusRow`、`CapsuleDialog`）。
- 预览：`app/src/debug/java/com/saschl/cameragps/ui/preview/GlassComponentsPreview.kt`（亮 / 暗 / 无模糊回退 / 对话框）。
- 对比度用例（第 4 页表）：`sharednew/src/commonTest/.../ui/theme/PaletteContrastTest.kt`。
