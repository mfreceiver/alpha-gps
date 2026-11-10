# 玻璃材质说明（0.3.0 B0）

本文记录 0.3.0「全面样式重塑」第一批（B0 地基）落地的玻璃材质与背景参数、每个取值的依据、Haze 2.1.0 行为的验证结论，以及**只能真机确认**的点，供真机调参使用。依据：`docs/ui-audit-2026-11.md` §2-9(c)、§3.4、§4。

代码位置（`SN` = `sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew`）：

| 内容 | 位置 |
|---|---|
| 玻璃 token（四级 × 亮/暗） | `SN/ui/theme/Glass.kt` → `GlassTokens` |
| 按钮/控件玻璃着色 | `SN/ui/theme/Glass.kt` → `GlassTints`、`GlassControlColors` |
| 背景光斑（orb） | `SN/ui/theme/Color.kt` → `AlphaColors.orbs`（`BackdropOrb`） |
| 背景绘制 / 取样 | `SN/ui/components/glass/GlassBackground.kt` → `drawGlassBackdrop`、`orbIntensity`、`backdropColorAt` |
| 材质绘制 | `SN/ui/components/glass/GlassSurface.kt` → `Modifier.glass(level, shape, shadow, tint)`、`resolveGlassPaint` |
| 对比度矩阵 | `sharednew/src/commonTest/.../ui/theme/PaletteContrastTest.kt`（`textContrastMatrix` 打印全表） |

> 调参守则：改任何颜色/alpha 后跑 `:sharednew:testAndroidHostTest`，`PaletteContrastTest` 会在不达标时指出具体组合。**不允许放宽阈值**（正文 ≥ 4.5:1，大字/图标/textMuted ≥ 3:1）。

## 1. 层级（GlassLevel）

| 级别 | 用途 | 模糊半径 | 说明 |
|---|---|---|---|
| `Card` (G1) | 页面卡片、行 | 20 dp | 最通透；亮色带极淡蓝调 |
| `Float` (G2) | 浮动条、顶栏滚动态、浮层按钮、输入框、徽章 | 28 dp | 比卡片更实 |
| `Modal` (G3) | 对话框、菜单、Snackbar、底部面板 | 32 dp | 最实，保证长文可读 |
| `Viewer` (GV) | 照片查看器上的控件 | 24 dp | 暗色玻璃 + onPhoto 白字，亮暗主题共用 |

模糊半径沿用设计 v3（`GlassTokensTest.blurRadiiFollowDesign` 锁定），B0 未改。

## 2. 六项观感参数（audit §2-9(c) 1–6）及取值依据

每个 `GlassToken` 现在带齐下列字段（`GlassComponentLogicTest.glassMaterialTokensCarryAllSixEffects` 断言都存在且在合理区间）。

### 2.1 边缘光（rim）——`rimTop` / `rimBottom` / `rimShade` / `rimWidth`
- 画法：`glassRimBrush(token)`，**左上→右下**对角线渐变：0 处白 `rimTop`，0.45 处白 `rimBottom`，1 处 `rimShade`（右下暗边）。宽度 `rimWidth = 1.5 dp`。
- 依据：audit 指出原 1 dp 纯竖向白边在暗色只有 .22→.06，几乎看不见；真实玻璃是"迎光边亮、背光边暗"的双向边缘。
- 取值：亮色 rimTop .95–1.0（白底上本来就弱，需要接近满白才看得出），rimShade = 海军蓝 `#0A1F44` @ .08–.10；暗色 rimTop 提到 .34/.38/.40（Card/Float/Modal，原 .22/.26/.30），rimShade = 黑 @ .30–.40。
- 真机调：如果暗色边缘"发光过头"像描边按钮，先降 rimTop（不低于 .30，测试下限）；亮色若嫌脏，先降 rimShade alpha。

### 2.2 内高光 / 内阴影——`sheen` / `innerHighlight` / `innerShadow` / `bandHeight`
- 画法：`drawWithCache` 在形状内画三层：整体自上而下 `sheen` 白色渐隐（到 55% 高度消失）；顶部 `bandHeight`（6 dp，Modal 8 dp）内 `innerHighlight` → 透明；底部同高 `innerShadow` → 透明。
- 依据：audit 第 2 条"顶部 1–2 dp 亮带 + 底部暗带"让玻璃有厚度。实现用 6–8 dp 渐变带而不是 1–2 dp 实线：1–2 dp 实线在 xxhdpi 上像描边、与 rim 叠在一起分不开；渐变带在边缘最亮（≈ rim 宽度处）往内迅速衰减，视觉上就是"1–2 dp 高光 + 柔和过渡"。
- 取值：亮色 innerHighlight 白 @ .70/.80/.85，innerShadow 海军蓝 @ .05–.06（亮色底部暗带一重就脏）；暗色 innerHighlight 白 @ .14–.18，innerShadow 黑 @ .22–.28。sheen 亮 .26/.20/.16、暗 .06–.08（暗色 sheen 太高会把 textSecondary 压到 4.5 以下——`PaletteContrastTest` 的 sheen 变体覆盖了这一点）。

### 2.3 双层阴影——`ambientShadow` / `keyShadow`
- 画法：`Modifier.glass` 依次叠两个 `dropShadow`：ambient（大半径、低 alpha、略下移）+ key（小半径、更贴边、小偏移）。
- 依据：单层 16 dp 阴影在亮色下显得"悬浮的灰块"；双层能同时表达"环境遮蔽"和"接触阴影"。亮色 Card 的 ambient 用 primary 蓝 `#0B57D0` @ .09（audit：亮色阴影带主色调更通透），其余亮色用海军蓝，暗色用黑。
- 取值（offsetY / blur / alpha）：亮 Card 8/24/.09 + 1/4/.08；亮 Float 10/28/.14 + 2/6/.10；亮 Modal 16/40/.20 + 3/8/.12；暗 Card 8/24/.34 + 1/4/.30；暗 Float 10/28/.42 + 2/6/.30；暗 Modal 16/40/.55 + 3/8/.30；Viewer 6/20/.40 + 1/4/.30。
- 嵌套玻璃（见 §4）不画阴影。

### 2.4 噪点——`noise`
- 画法：`HazeBlurStyle.noiseFactor(token.noise)`。
- 取值：Card .04，Float/Modal .05，暗色 .05，Viewer .03（照片上噪点会被看成图像噪声，取下限）。区间来自 audit 建议的 0.03–0.06。
- 性能：见 §5.2——API 33+ 噪点与 tint 合成一次 AGSL pass，几乎零额外开销；API 31/32 多一次 softlight 混合。

### 2.5 填充反差——`fill` / `solidFill`
- 亮 Card：`#FBFDFF` @ .60（原纯白 @ .58）。依据：audit 指出纯白 .58 叠在 `#F3F6FC` 上几乎与背景同色；略带蓝/青的冷白在 orb 上能读出"玻璃片"边界，同时不至于变成蓝色卡片。测试断言亮 Card fill 蓝通道 > 红通道。
- 亮 Float `#F9FAFE` @ .74（原 .78，略降让模糊更透）；亮 Modal 白 @ .84（原 .86）。
- 暗 Card：`#DDE6F5` @ .08（原白 @ .07，冷调 + 略增）；暗 Float `#20252B` @ .70；暗 Modal `#272C33` @ .84。
- Viewer：`#101418` @ .70（原 .66）。依据：照片可能是全白，`onPhotoSecondary`（白 .85）在 .66 上只有 4.27:1；.70 时 onPhoto 5.92 / onPhotoSecondary 4.82 / onPhotoMuted 3.98。
- `solidFill`（Solid 模式或无取样源时的回退）保持 ≥ .94 不透明，对比度另测（`solidFallbackKeepsTextReadable`）。

### 2.6 玻璃叠玻璃（嵌套）
- 已验证 Haze 的 `Behind` 选择规则（§5.1）：卡片内部的玻璃**取不到卡片本身**，只能取到背景层；若仍做模糊，会在卡片上"挖洞"显示背后的 orb，观感错误。
- 因此 `LocalGlassDepth`：`GlassSurface` 向子树提供 depth+1；`resolveGlassPaint(depth > 0)` → 不模糊、不画阴影、直接用 `token.fill` 半透明叠加（仍画 rim 与内高光），即"玻璃上的一片更亮的玻璃"。叠层（对话框/菜单/Snackbar/面板）在 `GlassOverlayLayer` 中重置 depth = 0，所以它们始终是顶层玻璃、模糊整个页面。
- 对比度测试为每个级别加了"嵌套在 Card 上"的背景变体。

## 3. 背景光斑（orb，D1）

- 5 个径向渐变光斑，位置/半径按画布比例（`BackdropOrb(x, y, radius, color, alpha)`，radius 为短边的倍数，横竖屏形状一致）。
- 亮：浅蓝 `#A8C7FA`@.55（左上，r .80）、淡紫 `#CDBDF7`@.40（右中，r .66）、薄荷 `#A6E6D6`@.36（左下，r .72）、天蓝 `#BEE3FF`@.38（右上，r .42）、粉 `#F6D3E4`@.24（右下，r .46）。
- 暗：同几何，`#0B57D0`@.40、`#5B3FA8`@.30、`#0E6B5C`@.26、`#1A6FA8`@.26、`#7A3466`@.18。
- 剖面：`orbIntensity(d)`：中心 1 → 0.45 半径处 0.55 → 边缘 0（`ORB_BODY_STOP` / `ORB_BODY_ALPHA`），比原来"线性渐隐"多一段平台，模糊后更像一团有体积的光。
- **低对比**依据：`backdropOrbsAreLowContrast` 在 4 种画布（1080×2400、1600×1600、2400×1080、1600×2560）上 17×17 采样，要求任何点与纯背景色对比 < 1.5（实测亮 1.28、暗 1.40），且最大值 > 1.1（保证真的看得出来）。亮色背景亮度 .709–.920，暗色 .0059–.0285。
- 新增的第 4、5 个光斑（右上天蓝、右下粉/梅）用于让顶栏和底部浮动条下面也有颜色可被模糊——只有背后有色彩变化，模糊玻璃才"看得出是玻璃"。
- 真机调：光斑太抢眼时降 alpha（每次 .05），不要缩半径（缩半径会出现明显的圆形边界）。

## 4. 控件与按钮的玻璃着色

- `GlassTints.of(role)`（`CapsuleButton(glass = true)` 与 `GlassPrimary`）：
  - `Accent`（主按钮）：亮 primary @ .30 + 文字 `onPrimaryContainer`（深海军蓝）；暗 `#0B57D0` @ .46 + textPrimary。依据：亮色玻璃上 primary 蓝字只有 2.28:1，必须用深色字；暗色浅蓝 tint 会让白字掉到 4.2。
  - `AccentSoft`（Tonal）：亮 primary @ .14 + onPrimaryContainer；暗 深蓝 @ .24 叠背景 @ .30。
  - `Danger`：亮 error @ .18 + onErrorContainer；暗 `#93000A` @ .46 + onErrorContainer。
  - `Neutral`（Secondary）：亮透明；暗 背景色 @ .30 面纱（否则暗玻璃标签在白照片上 3.80:1）。
  - 测试 `glassCapsulesAreTranslucentAndReadableEverywhere`：所有角色的玻璃按钮叠在 Float 玻璃上，背景取全部 orb 采样点 + 白/黑/灰照片，标签 ≥ 4.5:1。
- 大按钮（Large/Hero）原来的深色辉光已去掉：只有不透明 Primary 保留一圈很淡的 accent 色辉光（暗 .18 / 亮 .24），永不为黑色。
- `GlassControlColors`：开关/复选框选中填充 = accent；勾/滑块 = onPrimary。**偏离**：N1 写"白色 thumb"；暗色主题的 accent 是浅蓝 `#A8C7FA`，白色 thumb 在它上面只有 1.6:1，改用 onPrimary（深海军蓝，7.5:1）。曾尝试中蓝 `#3D7BE3` 填充 + 白 thumb，但它与最亮的暗色 Card 玻璃只有 2.25:1（非文本对比 < 3），满足双约束的亮度窗口只有 L∈[0.29, 0.30]，太脆弱，放弃。

## 5. Haze 2.1.0 行为验证

方法：本机 Gradle 缓存只有 `dev.chrisbanes.haze:{haze,haze-blur,haze-utils}-android:2.1.0` 的 aar（无 sources jar），用 IntelliJ 自带反编译器（fernflower）反编译 classes.jar 阅读实现。

### 5.1 hazeSource 的 zIndex 语义 —— **已验证（读反编译源码）**
- `HazeInput.Sources(state, selection = HazeSourceSelection.Behind, retention = KeepLastFrame)` 默认 `Behind`。
- `Behind` 的实现：从效果节点往上找**最近的同一 `HazeState` 的祖先 `hazeSource` 节点**；若找到，只纳入 `zIndex < 祖先 zIndex` 的取样区；没有祖先则纳入全部。`HazeSourceSelection.All` 无条件纳入全部。
- 推论（与 `GlassBackground` 结构对应：背景 Canvas = source z0，内容 Box = source z1）：
  - 页面内容里的玻璃（卡片、顶栏、浮动条）：祖先是内容 Box（z1）→ 只取到背景 Canvas（z0）。**内容不会被模糊进卡片**——这就是 audit 第 7 条"内容没有真正经过玻璃"的根因。
  - 叠层（对话框/菜单/面板/Snackbar）：在内容 Box 之外，没有祖先 source → 取到背景 + 全部内容。✔ 对话框能模糊页面。
  - 嵌套玻璃：取不到父卡片 → 见 §2.6 决策。
- 后续批次若要让列表内容"经过"顶栏玻璃：把顶栏放到内容 Box **之外**（与叠层同层），或对顶栏用 `HazeSourceSelection.All`。这属于页面结构调整（audit 第 7 条），B0 只提供 `GlassTopBar(scrolled = …)` 的玻璃出现能力。**推断，未在真机验证。**

### 5.2 noiseFactor 的开销与 API 差异 —— **已验证（读源码）/ 性能需真机**
- `noiseFactor > 0` 才启用噪点。噪点纹理 `R.drawable.haze_noise` 解码一次后缓存在静态字段，用 `BitmapShader(REPEAT)` 平铺。
- API 33+：若恰好一个 SrcOver tint 且无渐进/遮罩，噪点与 tint 合成**一个 AGSL RuntimeShader** pass（`createCombinedNoiseTintRenderEffectOrNull`）。`Modifier.glass` 刻意只传一个 tint（层级 fill 与按钮 tint 先在 CPU 侧用 `tintOver` 合成），以命中这条快速路径。
- API 31/32（minSdk 31）：噪点走 softlight `blendForeground` RenderEffect + tint 链，多一次合成。
- API 36+ 另有 `HazeBackdropRenderer` 路径。
- **需真机**：低端机（API 31/32）上大量 Card 同屏滚动时的帧耗时；若掉帧，先把 Card 的 noise 设 0（Float/Modal 保留）。

### 5.3 Popup / Dialog 跨窗口取样 —— **推断 + 需真机**
- `HazeSourceNode` 用 `positionOnScreen`（屏幕坐标）记录取样区，几何上跨窗口能对上（推断）。
- 但失效/重绘监听：`observesSameWindowSourceSnapshots()` 只在 SDK 31 为真，`invalidateOnHazeAreaPreDraw` 仅 SDK < 32 —— 跨窗口时源内容变化后效果层可能不刷新（未验证）。
- 结论：**新组件一律页内叠层（`GlassOverlayHost`），不开系统窗口**。B0 把 `GlassIconButton` 的 M3 `TooltipBox`（Popup 窗口）替换成页内 `GlassTooltip`（长按显示，`ICON_TOOLTIP_MILLIS` = 1.5 s 后消失）；`GlassPopover`/`GlassMenu`/`GlassSnackbarHost`/`GlassBottomSheet` 全部走叠层。
- 仍使用系统窗口的旧路径：`CapsuleDialog` 在**没有** `GlassBackground`（无 overlay host）时回退平台 `Dialog`，此时 `LocalGlassBackdrop = null` → Solid 渲染，不涉及跨窗口模糊。

## 6. 对比度矩阵（`PaletteContrastTest.textContrastMatrix`）

背景 = 每级玻璃 fill 叠在全部 orb 采样点上（另加 sheen 最亮处、嵌套于 Card 两个变体；Viewer 叠在白/黑/灰照片上），取最差值。阈值：Title（22 sp SemiBold，WCAG 大字）与 textMuted/onPhotoMuted 3:1，其余 4.5:1。6 档字号 × 角色 × 4 级 × 亮/暗 全部通过。最差值（:1）：

| 级别 | textPrimary | textSecondary | textMuted | accent | success | warning(text) | danger |
|---|---|---|---|---|---|---|---|
| 亮 Card | 14.90 | 8.16 | 4.47 | 5.56 | 5.48 | 6.03 | 5.63 |
| 亮 Float | 15.24 | 8.34 | 4.57 | 5.69 | 5.60 | 6.16 | 5.75 |
| 亮 Modal | 16.33 | 8.94 | 4.90 | 6.10 | 6.00 | 6.61 | 6.17 |
| 暗 Card | 6.99 | 5.33 | 3.79 | 5.27 | 5.36 | 5.89 | 5.33 |
| 暗 Float | 9.31 | 7.10 | 5.04 | 7.01 | 7.13 | 7.83 | 7.10 |
| 暗 Modal | 8.46 | 6.45 | 4.58 | 6.37 | 6.48 | 7.12 | 6.45 |
| Viewer | onPhoto 5.95 | onPhotoSecondary 4.84 | onPhotoMuted 4.01 | | | | |

为达标调整的 token（而非放宽阈值）：
- `textMuted` 新增为**不透明色**：亮 `#696E75`、暗 `#A3A8B0`。audit 建议的 onSurfaceVariant @ .6 在暗色 Card 只有 2.59:1。
- `warningIcon` 新增：亮 `#A15C00`（原 `warning #F9A825` 作为图标只有 1.71:1），暗 `#FFC857`。`warning` 保留为纯装饰色。
- Viewer fill .66 → .70（见 §2.5）。
- `IconEmphasis.Subtle` 从 textSecondary @ .62（暗 Card 2.38:1）改为 textMuted。
- 玻璃按钮文字角色见 §4。

## 7. 只能真机确认的点

- 肉眼"毛玻璃/液态玻璃"质感是否明显：边缘光、内高光带宽、噪点强度、orb 浓度——本机无法渲染预览截图，以上取值仅按审计建议和对比度约束推出。
- API 31/32 低端机滚动性能（噪点 + 每卡片一次模糊）。
- 叠层模糊是否随页面滚动实时刷新（`RetentionPolicy.KeepLastFrame` 的表现）。
- 动态字体 200%：6 档字号都是 sp，组件用 `heightIn(min)` 而非固定高度，但 GlassSegmented/StatusBadge/顶栏标题在 200% 下是否截断未验证。
- TalkBack：Snackbar 的 `liveRegion = Polite` 播报、RevealValueRow 的 stateDescription、页内 tooltip 不被朗读（clearAndSetSemantics）等需真机听。
- 暗色开关 navy thumb 的观感是否可接受（§4 偏离）。
