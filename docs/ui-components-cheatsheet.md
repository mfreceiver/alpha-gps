# UI 组件速查（0.3.0 起，给页面迁移批次用）

所有组件在 `sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/`（包 `com.sasch.cameragps.sharednew.ui.components.glass`），主题 token 在同模块 `ui/theme/`。材质参数与取值依据见 `docs/glass-material-notes.md`；规范来源 `docs/ui-audit-2026-11.md` §3–§4。

## 0. 硬规则

1. **字号只用 `AlphaType` 6 档**（+ Mono），颜色只用 `AlphaTheme.colors` 的角色色。UI 代码里不得出现 `fontSize =`、`MaterialTheme.typography`、`Color.White`、`Color(0x`（token 目录 `ui/theme/` 除外）——`scripts/ui_style_guard.sh` 守卫，见 §6。
2. **不开系统窗口**：不用 `Popup` / `Dialog` / `DropdownMenu` / `ModalBottomSheet` / `AlertDialog` / M3 `TooltipBox` / `Snackbar`。一律用本库页内叠层组件（通过 `GlassBackground` 内置的 `GlassOverlayHost` 渲染）。因此页面根必须是 `GlassBackground { … }`，否则 `GlassPopover`/`GlassMenu`/`GlassSnackbarHost`/`GlassBottomSheet` 不渲染（`CapsuleDialog` 例外：无 host 时回退平台 Dialog）。
3. 触控目标 ≥ 48 dp（密集行 ≥ 40 dp）；纯图标必须有 `contentDescription`；行级开关/单选把交互放在整行（`Modifier.toggleable` / `selectable`），控件本身传 `onXxx = null` 只做显示。
4. 不用分隔线；卡片间距 `AlphaDimens.cardGap`，屏边距 `AlphaDimens.screenMargin`。
5. 新文案只追加到 `values/` 与 `values-zh-rCN/` 末尾。

## 1. 文字：`AlphaType`

| 档 | 规格 | 用途 |
|---|---|---|
| `AlphaType.Title` | 22/28 SemiBold | 顶栏标题、页面大标题（唯一的 WCAG"大字"） |
| `AlphaType.Heading` | 18/24 SemiBold | 卡片标题、对话框标题、分组标题 |
| `AlphaType.Body` | 15/22 Normal | 正文、行主文字、对话框正文、输入框文字 |
| `AlphaType.Label` | 15/20 SemiBold | 按钮文字、数值、分段控件、徽章 |
| `AlphaType.Caption` | 13/18 Normal | 次要说明、行副文字、输入框 label、时间 |
| `AlphaType.Tag` | 11/14 SemiBold | InfoTag / 角标 |
| `AlphaType.Mono` | 12/16 Monospace | 日志、许可证全文 |

- letterSpacing 全部 0。`MaterialTheme.typography` 的槽已映射到同 6 档（display*/headline*/titleLarge→Title，titleMedium→Heading，titleSmall/labelLarge→Label，bodyLarge/bodyMedium→Body，bodySmall/labelMedium→Caption，labelSmall→Tag），所以未迁移/第三方组件不会"长回"M3 默认大小——但**新代码不要再写 `MaterialTheme.typography`**。
- 过渡别名 `AlphaTextStyles` 已删除（0.3.0）；旧调用对应关系：topBarTitle→Title、dialogTitle→Heading、dialogBody→Body、button*→Label、statusValue→Label、statusLabel→Caption、tag→Tag。
- 旧 → 新对照：`headlineSmall/titleLarge` → Title；`titleMedium` → Heading；`bodyLarge/bodyMedium` → Body；`labelLarge` → Label；`bodySmall/labelMedium` → Caption；`labelSmall` → Tag；`fontSize = 12.sp, fontFamily = Monospace` → Mono。

## 2. 颜色角色：`AlphaTheme.colors`

| 角色 | 字段 | 用于 | 对比要求 |
|---|---|---|---|
| 主文字 | `textPrimary`（= onSurface） | 标题、正文、数值 | 4.5 |
| 次文字 | `textSecondary`（= onSurfaceVariant） | 说明、标签、未强调图标 | 4.5 |
| 弱文字 | `textMuted`（亮 #696E75 / 暗 #A3A8B0，不透明） | 占位符、禁用说明、次要时间戳、Subtle 图标 | 3 |
| 强调 | `accent`（= primary） | 链接、选中、主操作图标 | 4.5 |
| 成功 | `success` | 已连接、已写入 | 4.5 |
| 警告 | `warningText`（文字）/ `warningIcon`（图标/圆点/边条） | 警告 | 4.5 / 3 |
| 危险 | `danger`（= error） | 错误、删除 | 4.5 |
| 照片上 | `onPhoto` / `onPhotoSecondary`(.85) / `onPhotoMuted`(.72) | Viewer 与照片上的文字/图标 | 4.5 / 4.5 / 3 |
| 照片上警示/错误 | `onPhotoWarning`(#FFE08A) / `onPhotoDanger`(#FFDAD6)，亮暗主题同值 | Viewer 与照片上的警示、错误文字与图标（如"仅 RAW"、下载失败提示、Viewer 上 Danger 图标按钮）；Viewer 上**不用** `warningText`/`danger`/`success` | 4.5（文字）/ 3（图标） |

- `warning`（原浅琥珀 #F9A825）只作装饰，不能当文字或图标色。
- Container 类颜色（primaryContainer、errorContainer…）不作为文字底色；需要色块用玻璃 tint（`GlassSurface(tint = …)`）。
- 状态 → 颜色统一走 `statusToneColor(tone, colors, forText)` / `statusIconColor(tone, colors)`（`StatusTone { Neutral, Active, Success, Warning, Error, Accent }`）。

## 3. 容器与材质

```kotlin
GlassBackground { /* 页面根：G0 背景光斑 + 取样源 + 叠层宿主 */ }
GlassCard(modifier, onClick = null, onClickLabel = null, contentPadding, verticalArrangement) { … }         // G1
GlassSurface(modifier, level = GlassLevel.Card, shape = AlphaShapes.card, onClick = null, enabled, onClickLabel,
             contentPadding, contentAlignment, tint = Color.Transparent) { … }
GlassFloatingBar(modifier, level = GlassLevel.Float, shape = AlphaShapes.capsule, contentPadding, horizontalArrangement) { … }
GlassInsetBlock(modifier, contentPadding, contentAlignment) { … }   // 卡片内的平面凹块
Modifier.glass(level, shape, shadow = true, tint = Color.Transparent)  // 自定义形状的玻璃
```

- `GlassLevel`：`Card`(G1) / `Float`(G2) / `Modal`(G3) / `Viewer`(GV)。
- 嵌套：卡片里的玻璃自动降级为"不模糊的半透明亮片"（`LocalGlassDepth`），叠层组件始终是顶层玻璃。
- 照片网格不作取样源：`GlassBackground(contentIsBackdropSource = false)`。

## 4. 组件清单

### 按钮
```kotlin
CapsuleButton(text, onClick, modifier = Modifier, role = CapsuleRole.Primary, size = CapsuleSize.Regular,
              icon: Painter? = null, enabled = true, loading = false,
              glass = false, glassLevel = GlassLevel.Float)
// CapsuleRole { Primary, Secondary, Tonal, Danger, DangerOutlined, GlassPrimary }
// CapsuleSize { Small(32), Regular(48), Medium(52), Large(56), Hero(68) } —— 只管高度/圆角/内边距，文字永远 AlphaType.Label
CapsuleButton("下载", onClick, role = CapsuleRole.GlassPrimary, glassLevel = GlassLevel.Viewer) // 照片上（onPhoto 文字）
CapsuleButton("连接", onClick, role = CapsuleRole.Tonal, glass = true)                            // N12 玻璃变体

GlassIconButton(icon: Painter, contentDescription: String?, onClick, modifier = Modifier, enabled = true,
                emphasis = IconEmphasis.Standard, touchTarget = 48.dp, container: GlassLevel? = null, tint = Unspecified)
// IconEmphasis { Standard, Primary, Subtle, Danger }；长按显示页内 tooltip（不开窗口）
```

### 选择控件（N1–N3、N7）
```kotlin
GlassSwitch(checked, onCheckedChange: ((Boolean) -> Unit)?, modifier = Modifier, enabled = true)          // 52×32 轨道
GlassRadio(selected, onClick: (() -> Unit)?, modifier = Modifier, enabled = true)                         // 20 dp
GlassCheckbox(checked, onCheckedChange: ((Boolean) -> Unit)?, modifier = Modifier, enabled = true, onPhoto = false)
GlassCheckbox(state: ToggleableState, onStateChange: ((ToggleableState) -> Unit)?, modifier, enabled, onPhoto) // 三态
GlassTileAction(icon, contentDescription, onClick, modifier = Modifier)   // 照片格角上的放大镜等 22 dp 小操作
GlassSegmented(options: List<String>, selectedIndex, onSelect: (Int) -> Unit, modifier = Modifier, enabled = true, height = 48.dp)

// 整行可点的设置行
Row(Modifier.fillMaxWidth().heightIn(min = AlphaDimens.rowMinHeight)
        .toggleable(value = on, role = Role.Switch, onValueChange = { on = it })) {
    Text(label, style = AlphaType.Body, color = AlphaTheme.colors.textPrimary, modifier = Modifier.weight(1f))
    GlassSwitch(on, onCheckedChange = null)
}
```

### 输入（N4）
```kotlin
GlassTextField(value, onValueChange, modifier = Modifier, label: String? = null, placeholder: String? = null,
               supportingText: String? = null, isError = false, enabled = true, readOnly = false, singleLine = true,
               keyboardOptions, keyboardActions, visualTransformation, trailing: (@Composable () -> Unit)? = null)
```
- 无障碍：编辑框节点本身带名称（contentDescription = `label`，无 label 时用 `placeholder`）、描述（空值时 placeholder 提示 + 非错误的 supportingText）与错误（`isError` 时 supportingText，缺省"输入无效"）；可见的 label/placeholder/supportingText 不再单独朗读，trailing 保持独立节点。**必须给 `label` 或 `placeholder`**，否则读屏得到无名编辑框。映射见 `glassTextFieldA11y`。

### 顶栏（N5）
```kotlin
val listState = rememberLazyListState()
GlassTopBar(title, modifier = Modifier, subtitle: String? = null,
            navigationIcon = { GlassIconButton(painterResource(Res.drawable.arrow_back_24px), back, onBack) },
            actions = { GlassIconButton(…) },
            scrolled = listState.isScrolledUnderTopBar,     // 也有 ScrollState.isScrolledUnderTopBar
            windowInsets = WindowInsets.statusBars)
```
标题左对齐；`scrolled = true` 时淡入 Float 玻璃底。让列表内容"真的经过"顶栏玻璃需要把顶栏放到内容取样区之外（见 glass-material-notes §5.1），属页面结构改动。

二级页面（设置/帮助/关于/许可证/隐私/日志）直接用页面骨架 `GlassPageScaffold`（`ui/components/page/`）：`GlassBackground` + 带返回键的 `GlassTopBar` + `ScreenContent` 限宽。
```kotlin
GlassPageScaffold(title, onBackClick, modifier = Modifier, actions = { … },
                  scrolled = scrollState.isScrolledUnderTopBar) { padding -> … }   // scrolled 默认 false
```

### 弹出菜单 / 气泡（N6）
```kotlin
val anchor = rememberGlassAnchor()
GlassIconButton(icon, "更多", onClick = { open = true }, modifier = Modifier.glassAnchor(anchor))
GlassMenu(expanded = open, onDismissRequest = { open = false }, anchor = anchor,
          items = listOf(GlassMenuItem("网格", onClick = { … }, selected = true),
                         GlassMenuItem("删除全部", onClick = { … }, destructive = true)),
          placement = PopoverPlacement.BelowEnd)
GlassPopover(expanded, onDismissRequest, anchor, modifier, placement = PopoverPlacement.BelowEnd) { /* ColumnScope */ }
// GlassMenuItem(label, onClick, icon: Painter? = null, enabled = true, destructive = false, selected: Boolean? = null)
// PopoverPlacement { BelowEnd, BelowStart, AboveEnd, AboveStart }；放不下自动翻转并夹在屏内
```

### 进度（N8）
```kotlin
GlassProgressCircle(modifier = Modifier, progress: Float? = null /* null = 不确定 */, color = Unspecified,
                    onPhoto = false, size = GlassProgressSize.Medium /* Small 16 / Medium 24 / Large 36 */, strokeWidth = 2.5.dp)
GlassProgressLinear(modifier = Modifier, progress: Float? = null, onPhoto = false)   // 4 dp 胶囊
```

### Snackbar（N9）
```kotlin
val snackbar = rememberGlassSnackbarHostState()
GlassBackground {
    …页面…
    GlassSnackbarHost(snackbar)          // 放在 GlassBackground 内任意位置；底部叠层，live region
}
snackbar.show(message, actionLabel = null, onAction = null, duration = GlassSnackbarDuration.Short)
// Short 3 s（带操作 5 s）/ Long 6 s / Indefinite；同文案排队去重；snackbar.dismiss(data) / clear()
```

### 状态（N10、N11、N15–N17）
```kotlin
StatusLine(title, tone, modifier = Modifier, supportingText: String? = null)
StatusRow(items: List<StatusItem>, modifier = Modifier)
StatusItem(label, value, icon: Painter? = null, tone = StatusTone.Neutral,
           iconTone: StatusTone? = null,   // 只给图标着色（null = 跟随 tone）
           valueTone: StatusTone? = null)  // N11：只给数值着色（null = 跟随 tone；Neutral → textPrimary）
// 例："信号 强"——图标绿、数值保持主文字色：StatusItem("信号", "强", icon, iconTone = StatusTone.Success)
StatusBadge(text, tone, modifier = Modifier, showCheck = tone == StatusTone.Success)   // 只读胶囊徽章
StatusDot(tone, modifier = Modifier)
GlassWarningCard(title, message: String?, modifier = Modifier, tone = StatusTone.Warning,
                 icon: Painter? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null)
GlassIconTile(icon, modifier = Modifier, tone = StatusTone.Active, size = 44.dp, contentDescription: String? = null)
RevealValueRow(label, value, revealed, onRevealedChange: (Boolean) -> Unit, modifier = Modifier,
               masked = maskValue(value))   // maskValue("AA:BB:CC:DD:EE:FF", visibleSuffix = 2) == "••:••:••:••:••:FF"
InfoTag(text, modifier = Modifier, style = InfoTagStyle.OnSurface /* OnPhoto */, contentColor = Unspecified, icon = null)
```

### 对话框 / 面板（N18、N20）
```kotlin
CapsuleDialog(onDismissRequest, title, message, confirm = DialogAction("删除", onDelete), dismiss = DialogAction("取消", onDismiss),
              destructive = true, icon = painterResource(…))
CapsuleDialog(onDismissRequest, confirm, dismiss, title = "…", onClose = { … } /* ✕ 与标题同一行 */) { /* 自定义内容 */ }
GlassBottomSheet(visible, onDismissRequest, modifier = Modifier, title: String? = null) { /* ColumnScope */ }
// 下拉超过 35% 高度或快速下滑关闭；点遮罩 / 返回键关闭
```

### 未提供
- N14 `GlassFilmstrip`（可选）B0 未做：Viewer 胶片条继续用现有 `ViewerFilmstrip`，迁移 Viewer 的批次如需要再按 audit N14 规格（`GlassFloatingBar(level = Viewer)`、格 40 dp 圆角 8、当前项 2 dp onPhoto 环、其余 alpha .7）实现。

## 5. 旧原生控件 → 新组件

| 旧（M3 / 自写） | 新 |
|---|---|
| `Switch` | `GlassSwitch`（行级 `toggleable` + `onCheckedChange = null`） |
| `RadioButton`、`SharedRadioOption` 里的 RadioButton | `GlassRadio`（行级 `selectable`，外层 `selectableGroup`） |
| `Checkbox`、`TriStateCheckbox`、照片格选择圆点 | `GlassCheckbox`（照片上 `onPhoto = true`） |
| `TextField` / `OutlinedTextField` | `GlassTextField` |
| `TopAppBar` / `CenterAlignedTopAppBar` / 自写顶栏 Row | `GlassTopBar` |
| `DropdownMenu` / `DropdownMenuItem` | `GlassMenu`（或 `GlassPopover` 放自定义内容） |
| `SegmentedButton`、`TabRow`、`FilterChip` 组 | `GlassSegmented` |
| `CircularProgressIndicator` / `LinearProgressIndicator` | `GlassProgressCircle` / `GlassProgressLinear` |
| `Snackbar` / `SnackbarHost` / `Toast` | `GlassSnackbarHost` + `show()` |
| `AlertDialog` / `Dialog` | `CapsuleDialog` |
| `ModalBottomSheet` | `GlassBottomSheet` |
| `Button` / `FilledTonalButton` / `OutlinedButton` / `TextButton` | `CapsuleButton`（role Primary / Tonal / Secondary / DangerOutlined…） |
| `IconButton`、`TooltipBox` | `GlassIconButton`（自带页内 tooltip） |
| `AssistChip` / `Badge` / 自写彩色小标签 | `InfoTag`（信息）/ `StatusBadge`（状态） |
| `Card` / `ElevatedCard` / `Surface` | `GlassCard` / `GlassSurface` |
| 带色底的警告块（errorContainer 底） | `GlassWarningCard` |
| 圆形图标底座 | `GlassIconTile` |
| 自写"显示/隐藏"地址行 | `RevealValueRow` |
| `HorizontalDivider` | 删除（用间距） |

## 6. 样式守卫

- `scripts/ui_style_guard.sh`：扫描 `sharednew/…/ui/` 与 `app/src/main/java/com/saschl/cameragps/ui/` 下 `*.kt`（豁免 `sharednew/…/ui/theme/`），模式 `fontSize =`、`MaterialTheme.typography`、`Color.White`、`Color(0x`。
- 基线 `docs/ui-style-guard-baseline.txt`（`<违规行数> <路径>`）：不在基线的文件必须 0；基线文件只能下降。**迁移完一个页面后运行 `scripts/ui_style_guard.sh --update-baseline`**（只接受下降）并提交基线变化。最后一批清空基线后改用 `--strict`。
- 扫描必须真实发生：仓库根或任一扫描目录缺失、受管 `*.kt` 为 0 个、grep 出错（如文件不可读）均 rc=2 失败，不当作"无违规"；成功输出报告"扫描了 N 个文件"。
- 自测：`scripts/ui_style_guard_test.sh`。
