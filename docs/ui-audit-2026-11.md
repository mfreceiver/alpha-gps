# UI 玻璃化与文字/颜色规范排查报告（2026-11）

> 性质：只读排查，不含实现代码。基线：分支 `main`，v0.2.0 之后（排查开始时工作区干净，提交 `d2dc6f3`）。
> 证据格式：`文件:行号`。无法由代码确认的一律标 **待确认**（多数需真机截图/实机验证）。
> 路径简写：
> - `SN/` = `sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/`
> - `APP/` = `app/src/main/java/com/saschl/cameragps/`
> - `RES-SN/` = `sharednew/src/commonMain/composeResources/`，`RES-APP/` = `app/src/main/res/`
> - 未带前缀的 `docs/…`、`AGENTS.md`、`app/src/androidTest/…` 为仓库根相对路径。
> 行号均为排查时的行号，后续批次改动会使其漂移。

---

## 0 摘要（结论 + 优先级）

1. **“玻璃没有落地”的最大根因在背景而不在控件**：页面里卡片下面除 G0 底光（纯色 + 3 个径向光晕，`SN/ui/components/glass/GlassBackground.kt:62-69`）外没有任何内容，Haze 模糊的是近乎均匀的色块，肉眼等于“半透明填充”；加上 `noiseFactor(0f)`（`SN/ui/components/glass/GlassSurface.kt:71`）、亮色卡片 fill 仅 `White@0.58` 叠在 `#F3F6FC` 上（`SN/ui/theme/Glass.kt:47-53`）、暗色卡片仅 `White@0.07`（`:68-74`），层次只靠 1dp 边框。真正有可见模糊的只有：对话框（压在页面内容之上，`SN/ui/components/glass/CapsuleDialog.kt:198-220`）和照片预览页（对照片取样，`SN/ui/remote/WifiPhotoViewer.kt:287,303,320`）。**照片网格/日期列表在 `GlassBackground(contentIsBackdropSource=false)` 内（`SN/ui/remote/WifiRemoteScreen.kt:676`），悬浮条永远只取样到 G0，没有模糊。**
2. **原生/非玻璃控件仍大量存在**（§1，约 70 处）：原生 `Switch`×3（`SN/ui/settings/SharedSettingsCard.kt:111`、`SN/ui/device/DeviceInfoDialogs.kt:277`、`SN/ui/remote/WifiPhotoTopBar.kt:267`）、`RadioButton`、`OutlinedTextField`×4、M3 顶栏×6、M3 `Button`/`TextButton`/`Card`（权限页、警告卡、欢迎页、排障页、配对准备页）、`Toast`×5、原生进度条×14、`Popup` 新窗口菜单。**`CapsuleButton` 本身不是玻璃**：Primary/Tonal/Danger 为不透明填充，仅 Secondary 半透明（`SN/ui/components/glass/CapsuleButton.kt:89-127`）。
3. **悬浮下载按钮“粗深色外圈”根因**：`GlassFloatingBar(Float 级, 6dp)` 包着 `CapsuleButton(Large, Primary)`（`SN/ui/remote/WifiPhotoBrowser.kt:409-425`）——浮层深色 fill + 边框 + 阴影，叠按钮自带 `Black@.5` 辉光（`CapsuleButton.kt:109`）与底部 scrim 渐变（`WifiPhotoBrowser.kt:353-357`），且无模糊内容可透。
4. **“添加相机”按钮比卡片宽**根因：`ScrollbarLazyColumn` 给列表右侧加了 `padding(end = 8.dp)` 滚动条槽（`SN/ui/components/ScrollbarLazyColumn.kt:30-34`），按钮在列表外、只有水平 16dp（`SN/ui/devicelist/DeviceListLayout.kt:118-131`）→ 差 8dp（≈16px，与 706 vs 690 吻合）。
5. **预览页确实没有任何“打开详情”的入口**：`ViewerDetailsPanel`（`WifiPhotoViewer.kt:661`）只在 B/C 宽屏出现（`:429`）；A 档把 EXIF/镜头/直方图内联进信息条（`:365-369`），三个设置开关默认全关（`APP/utils/PreferencesManager.kt:236-256`）→ 默认什么都看不到。
6. **保护/评分**：评分仅剩无引用的死代码 `RatingColors`（`SN/ui/theme/Color.kt:226-235`）；保护贯穿偏好键→网格选项→ObjectInfo 解析→`PhotoMetadata.isProtected`→网格/预览标签→3 条字符串→测试，完整引用见附录 A。
7. **文字**：自定义 9 档 + 覆盖 1 个 M3 档，但 UI 同时直接用 11 个 M3 档，192 处 `Text(` 中 57 处无 `style`，另有 9 处硬编码 `fontSize`。建议收敛为 **6 档文字 + 8 个颜色角色**（§3）。
8. **多语言**：键集一致（仅 `app_name/app_name_ui` 只在基线）；波及 key 见 §6.3。`AGENTS.md` 的“通用帮助只在设备清单提供入口”与新要求冲突，须先改约定。
9. **优先级建议**：P1＝B0 地基（档位 + 玻璃 token + 新组件）与“移除保护评分 / 去相对日期 / 帮助日志入设置”（需求明确、风险低）；P1＝悬浮下载按钮、预览页下载按钮与信息条（用户最先看到）；P2＝设备详情页整合布局、网格 Fit/锚点、其余原生控件全面替换；P3＝Toast/通知。
10. **关键待决策**（§6.2）：玻璃是否允许背景增加取样内容、网格 Fit 呈现方式、尺寸标签口径、预览详情入口形态、设备详情页两案选一。

---

## 1 玻璃与原生控件清单（按页面）

图例：✅ 已用 glass 组件库；⚠ 部分；❌ 原生/M3 默认外观。“换成”中 **【新增】** 表示组件库没有对应组件，规格见 §4。

### 1.0 组件库自身的“不够玻璃”点（影响全部页面）

| 位置 | 现状 | 问题/建议 |
|---|---|---|
| `SN/ui/components/glass/CapsuleButton.kt:89-127` | Primary/Tonal/Danger 不透明渐变/纯色；仅 Secondary 半透明（`onSurface@.07/.13`） | 按钮是“实心糖果”而非玻璃；raised（Large/Hero 的 Primary）带下阴影辉光（`:153-155`）。需 **【新增】玻璃变体**。 |
| `SN/ui/components/glass/GlassSurface.kt:222-237` `GlassInsetBlock` | 纯 `onSurface@5%/7%` 平涂（`:240`） | 非玻璃；Ⓘ 弹窗“保持活跃”块在用（`SN/ui/device/DeviceInfoDialogs.kt:263-279`）。 |
| `SN/ui/components/glass/InfoTag.kt:29-36` | OnPhoto=`Black@.60`+白字；OnSurface=`onSurface@.08/.14` | 放在玻璃条内形成“二层深色底”（`WifiPhotoViewer.kt:577`）。需 **【新增】GlassTag**。 |
| `SN/ui/components/glass/GlassIconButton.kt`（`TooltipBox`/`PlainTooltip`） | contentDescription 非空即包 M3 Tooltip | Tooltip 是系统 Popup 窗口，非玻璃（视觉待确认）。 |
| `SN/ui/components/glass/CapsuleDialog.kt:222` | 无 `LocalGlassOverlayHost` 时退化为平台 `Dialog(`，并关掉取样（`:231`） | 仅测试/预览会走到；正常路径是页内 overlay（`:198-220`）。 |
| `SN/ui/components/page/GlassPageScaffold.kt:53-55` | 每个二级页**再嵌一层** `GlassBackground`（新的 HazeState + 新 overlay host） | 与 `APP/MainActivity.kt:57-67` 外层叠加：内层 G0 盖住外层；对话框挂在内层 host；外层“内容作为取样源”在二级页失效；重复绘制。 |

### 1.1 设备清单页（`APP/ui/device/DevicesScreen.kt` + `SN/ui/devicelist/*` + `SN/ui/device/SharedDevicesScreen.kt`）

| 位置 | 控件/当前实现 | 换成 |
|---|---|---|
| `SN/ui/device/SharedDevicesScreen.kt:26-49` | ❌ M3 `Scaffold` + `CenterAlignedTopAppBar`（居中标题 `topBarTitle`），与 `GlassPageScaffold` 的左对齐自绘顶栏不一致 | **【新增】`GlassTopBar`** |
| `APP/ui/device/DevicesScreen.kt:99-113` | ✅ 三个 `GlassIconButton`：帮助 `:99-103`、日志 `:104-108`、设置 `:109-113` | 帮助、日志移入设置（§2-3、§5 B1） |
| `SN/ui/devicelist/DeviceListLayout.kt:51,100-113` | ❌ M3 `TextButton("需要帮助？")`，`AlphaTextStyles.button` + `primary` | 删除，或 **【新增】**`CapsuleRole.Link`（决策 D3） |
| `SN/ui/devicelist/DeviceListLayout.kt:118-131` | ⚠ `AdaptiveAction` + `CapsuleButton(Primary)`（不透明） | 宽度对齐修复 + 玻璃主按钮（N12） |
| `SN/ui/devicelist/SharedDeviceList.kt:257-290` | ⚠ M3 `SwipeToDismissBox`，背景 `errorContainer/surfaceContainerHigh` 平涂（`:266-269`） | 保留手势，滑出背景改玻璃红色 tint |
| `SN/ui/devicelist/SharedDeviceList.kt:315-340` | ⚠ 44dp 图标圆底用 `errorContainer/primaryContainer/surfaceContainerHigh` 平涂 | **【新增】`GlassIconTile`**（N16） |
| `SN/ui/devicelist/SharedDeviceList.kt:391-400` | ❌ M3 `TextButton("打开指南")` | `CapsuleButton(Secondary)` 小号 |
| `SN/ui/devicelist/SharedDeviceList.kt:227-234,364-390` | `statusLabel.copy(fontSize=13.sp)` ×4 | 档位化（§3） |
| `APP/ui/device/ScanForDevicesMenu.kt:169-173` | ❌ 裸 `Text(color=error)`，无 style（默认 16sp/0.5ls） | `Caption`+`danger` |
| `APP/ui/device/ScanForDevicesMenu.kt:178,181` | `bodySmall` 提示 + ❌ 原生 `TextButton` | `CapsuleButton(Secondary)` |
| `APP/ui/device/ScanForDevicesMenu.kt:143,147` → `APP/ui/SystemStateWarnings.kt:37-83,89-135` | ❌ M3 `Card(errorContainer)` + M3 `Button`（蓝牙/定位警告卡），`titleSmall`/`bodySmall` | **【新增】`GlassWarningCard`**（N15）+ `CapsuleButton` |
| `APP/ui/device/DevicesScreen.kt:185-216` / `SN/ui/devicelist/SharedDeviceList.kt:181-224` | ✅ `MissingPermissionsCard` / `EmptyStateCard` | 无需改（仅文字档位） |

### 1.2 权限页（`APP/ui/PermissionBox.kt`，设备清单前的拦截页）

| 位置 | 当前 | 换成 |
|---|---|---|
| `:197` | `titleLarge`（M3 默认） | `Title` 档 |
| `:203-211, :266` | ❌ M3 `Card(containerColor = primaryContainer/surface)`（步骤卡） | `GlassCard`（完成态 Success tone） |
| `:241, :298, :327, :447` | ❌ M3 `Button` | `CapsuleButton` |
| `:338, :460` | ❌ M3 `TextButton` | `CapsuleButton(Secondary)` |
| 全文件 | 20 个 `Text(`（6 个无 style）；`MaterialTheme.colorScheme.*` 11 处 | 档位化 |
| `:346, :365` | ✅ `CapsuleDialog` | — |

### 1.3 设备详情页（`APP/ui/device/DeviceDetailScreen.kt` + `SN/ui/device/*`）

| 位置 | 当前 | 换成 |
|---|---|---|
| `APP/ui/device/DeviceDetailScreen.kt:135-167` | ❌ `Scaffold` + `CenterAlignedTopAppBar`（Ⓘ 在 `:156-160`） | `GlassTopBar` |
| `SN/ui/device/DeviceDetailScreen.kt:282-362` `ConnectionCard` | ✅ GlassCard；右侧 trailing：`Disconnect` → `GlassIconButton(m2_bluetooth_disabled_24px, Subtle)`（`:327-332`）；❌ `CancelConnecting` → M3 `TextButton`（`:334-343`）；`Connect` → `CapsuleButton(Tonal)`（`:345-350`）；`None` → ❌ M3 `CircularProgressIndicator`（`:352-356`） | **【新增】`StatusBadge`**（N10）；`TextButton`→`CapsuleButton(Secondary)`；进度→`GlassProgress` |
| `SN/ui/device/DeviceDetailScreen.kt:365-395` `LocationCard` | ✅ GlassCard + `StatusRow` | 图标按含义着色（§2-4） |
| `SN/ui/device/DeviceDetailScreen.kt:398-450` `PhotoTransferEntryBlock` | ⚠ `CapsuleButton(Primary, Hero)` 全宽（`:439-448`），提示文字 `statusLabel.copy(fontSize=dialogBody.fontSize)`（`:425,434`） | 降 Large(56) 或并入主卡 |
| `SN/ui/device/DeviceInfoDialogs.kt:277` | ❌ 原生 `Switch`（“保持活跃”） | **【新增】`GlassSwitch`**（N1） |
| `SN/ui/device/DeviceInfoDialogs.kt:347` | ❌ 原生 `OutlinedTextField`（重命名） | **【新增】`GlassTextField`**（N4） |
| `SN/ui/device/DeviceInfoDialogs.kt:315` | `InfoValue` 硬编码 `14.sp` | 档位化 |

### 1.4 Wi-Fi 连接 / 叠层 / 实验提示

| 位置 | 当前 | 换成 |
|---|---|---|
| `SN/ui/remote/WifiRemoteScreen.kt:738-856` `WifiConnectionControls` | ❌ `Scaffold`（`:755`）+ M3 `TopAppBar`（`:758-769`，默认标题样式）；❌ `OutlinedTextField`（`:805`）；14 个 `Text(` **全部无 style** | `GlassTopBar`、`GlassTextField`、档位化 |
| `SN/ui/remote/WifiManualJoin.kt:30,38,46,47` | ❌ 2×`OutlinedTextField`、`OutlinedButton`、`Button`；5 个 `Text(` 全无 style | `GlassTextField`、`CapsuleButton` |
| `SN/ui/remote/WifiRemoteExperimentalNotice.kt:31-57` | ✅ `CapsuleDialog`；2 个 `Text(` 无 style | 档位化 |
| `SN/ui/remote/WifiConnectionOverlay.kt:158` | ✅ 页内 `glass(Modal, dialog)`；3 个 `Text(` | — |

### 1.5 相机照片日期列表（`SN/ui/remote/WifiPhotoDates.kt`）

| 位置 | 当前 | 换成 |
|---|---|---|
| `:124` | ❌ M3 `CircularProgressIndicator` | `GlassProgress` |
| `:83-93` | 副标题 = 周几 + “今天/昨天” | 去相对词（§2-5） |
| `:113-116,125,133-136` | 6 个 `Text(` 中 3 个无 style | 档位化 |
| `:162-187` `DateRow` | 标题 `titleMedium`、副标题 `bodySmall`、计数 `bodyMedium` | 档位化 |

### 1.6 整日网格页（`SN/ui/remote/WifiPhotoBrowser.kt` + `WifiPhotoTopBar.kt`）

| 位置 | 当前 | 换成 |
|---|---|---|
| `WifiPhotoTopBar.kt:97-101` | ❌ M3 `TopAppBar`（标题默认样式 `:104-115`，副标题 `bodySmall` `:121-122`） | `GlassTopBar` |
| `WifiPhotoTopBar.kt:159-161` | ❌ `CircularProgressIndicator` | `GlassProgress`（N8） |
| `WifiPhotoTopBar.kt:191-196` | ❌ **平台 `Popup`（新窗口）**作视图选项菜单，不是 DropdownMenu | **【新增】`GlassPopover`**（N6，页内 overlay） |
| `WifiPhotoTopBar.kt:201-232` | ⚠ 自绘分段控件（`onSurface@.06` 胶囊 + 选中 primary 实心，`labelLarge`） | **【新增】`GlassSegmented`**（N7） |
| `WifiPhotoTopBar.kt:249-268` `OptionSwitch` | ❌ 原生 `Switch(onCheckedChange=null)`（`:267`；行为 `toggleable` 行） | `GlassSwitch` |
| `WifiPhotoBrowser.kt:248,637,643` | ❌ `LinearProgressIndicator`×3 | `GlassProgress`（线性） |
| `WifiPhotoBrowser.kt:615` | ❌ `CircularProgressIndicator`（缩略图加载） | `GlassProgress` 小号 |
| `WifiPhotoBrowser.kt:409-425` | ⚠ `GlassFloatingBar(6dp)` 包 `CapsuleButton(Large, Primary)` | 去外圈（§2-7） |
| `WifiPhotoBrowser.kt:363-380` | ✅ 传输状态 `GlassFloatingBar` | — |
| `WifiPhotoBrowser.kt:522-541` | ⚠ 放大镜：自绘 22dp 方块 `Black@.55` + 白 15dp 图标（`:533,538`） | **【新增】`GlassCheckbox/GlassTileAction`**（N3） |
| `WifiPhotoBrowser.kt:589-603` `SelectionCheck` | ⚠ 自绘：未选 `Black@.25`+2dp 白框（`:594`），选中 primary 实心 | `GlassCheckbox`（预览页复用） |
| `WifiPhotoBrowser.kt:488-506` | ✅ `InfoTag(OnPhoto)`（格式/大小/保护） | 保护移除；大小移右上（§2-6） |
| `WifiPhotoBrowser.kt` 全文件 | 14 个 `Text(`，9 个无 style | 档位化 |

### 1.7 单张预览页（`SN/ui/remote/WifiPhotoViewer.kt`）

| 位置 | 当前 | 换成 |
|---|---|---|
| `:747-752` | ❌ 底部下载：`CapsuleButton` 默认 Primary + Regular = 暗色主题 primary `#A8C7FA` 不透明实心 | 玻璃变体（N12，Viewer 级） |
| `:556-565` | ⚠ 信息条 `GlassSurface(Viewer)`（`101418@.66`，blur24） | 保留，内部去二层底 |
| `:577` | ⚠ 格式标签 `InfoTag(OnPhoto)`（`Black@.60`）放在玻璃条内 = 二层深底 | **【新增】`GlassTag`**（N13） |
| `:471-482` | `InfoTag(OnPhoto)`：保护 + “已选 N 张” | 保护移除 |
| `:822-837` | ⚠ 选择方框（同 `SelectionCheck`，48dp 点击区） | `GlassCheckbox` |
| `:841-880` | ⚠ 胶片条：平涂 `White@.08`（`:863`）、选中 2dp 白框（`:864`）；`captures.size ≥ 2` 才显示（`:850`） | **【新增】`GlassFilmstrip`**（N14） |
| `:777-818` | ⚠ 下载进度胶囊 `GlassFloatingBar(Viewer)` + ❌ 原生 `CircularProgressIndicator`（`:786,791`） | `GlassProgress` |
| `:531,541` | ❌ `CircularProgressIndicator` | `GlassProgress` |
| 全文件 | `Color.White`/`WhiteSoft`/`White@.x` 硬编码 20 处（`:157,454-865`） | `onPhoto` 色角色 |

### 1.8 设置页（`APP/ui/settings/*` + `SN/ui/settings/*`）

| 位置 | 当前 | 换成 |
|---|---|---|
| `SN/ui/settings/SharedSettingsCard.kt:111` | ❌ 原生 `Switch(onCheckedChange=null)`（`SharedToggleRow`，所有设置页共用） | `GlassSwitch` |
| `SN/ui/settings/SharedRadioOption.kt:37` | ❌ 原生 `RadioButton` | **【新增】`GlassRadio`**（N2） |
| `APP/ui/settings/SettingsScreen.kt:83-113` | ✅ GlassPageScaffold；**无帮助/日志入口** | 新增两行 `SharedSettingsNavRow`（`SharedSettingsCard.kt:121-162`） |
| `APP/ui/settings/AppControlsCard.kt:83` | ❌ `Toast` | **【新增】`GlassSnackbar`**（N9） |
| `APP/ui/settings/BatteryOptimizationCard.kt:144,193,221` | ❌ `Toast`×3；`MaterialTheme.colorScheme.*`×4 | `GlassSnackbar`、色角色 |
| `APP/ui/settings/PhotoTransferSettingsCard.kt:97-138` | ✅ 但 3 个预览开关含义需重新设计（§2-8） | — |
| `APP/ui/settings/LogLevelSettingsCard.kt:80` / `EventSoundsSettingsCard.kt:315` / `SN/ui/settings/SharedLanguageSettingsCard.kt:60` | ✅ `CapsuleDialog` | — |

### 1.9 帮助 / 排障 / 日志 / 关于 / 欢迎 / 配对

| 位置 | 当前 | 换成 |
|---|---|---|
| `APP/ui/TroubleshootingScreen.kt:26-46` | ❌ **整页原生**：`Scaffold` + `CenterAlignedTopAppBar`（`containerColor = surface` 不透明 `:44`）+ `IconButton` | `GlassPageScaffold` |
| `SN/ui/help/TroubleshootingGuideScreen.kt:189-192,275` | ❌ M3 `Card(surfaceVariant)`、`Surface`；`MaterialTheme.colorScheme.*` 11 处 | `GlassCard`/`GlassSurface` |
| `APP/ui/HelpScreen.kt:168` | ✅ `GlassPageScaffold` | — |
| `SN/ui/logs/SharedLogViewerScreen.kt:98,181` | ✅；`:158` 硬编码 `12.sp`（日志等宽，合理） | 记入档位表 `Mono` 例外 |
| `APP/ui/about/*` | ✅ `GlassPageScaffold`；`AboutComponents.kt:110` ❌ `Toast`（仅 <Android 13） | `GlassSnackbar` |
| `SN/ui/welcome/SharedWelcomeScreen.kt:109` | ❌ `Scaffold {}` 默认容器色 | `GlassPageScaffold` / 透明 |
| `SN/ui/welcome/SharedWelcomeScreen.kt:125,167,174` | ❌ `TextButton`×2、`Button`；`MaterialTheme.colorScheme.*` 6 处 | `CapsuleButton` |
| `SN/ui/pairing/SharedPairingPreparationScreen.kt:58-154` | ❌ **整页原生**（Scaffold/TopAppBar/IconButton/Surface/Button/Progress/TextButton/Card）。**生产代码无引用**：仅 `app/src/androidTest/java/com/saschl/cameragps/AdaptiveLayoutTest.kt:204` 调用 → **疑似死代码（待确认）** | 删除（优先）或整页重做 |
| `APP/ui/pairing/PairingDialog.kt:89` | ❌ `CircularProgressIndicator`（在 `CapsuleDialog` 内） | `GlassProgress` |
| `APP/ui/device/CameraDeviceManager.kt:195` | 回退 `Text("No Companion device manager found...")` 硬编码英文、无主题 | 走字符串资源 + 档位 |

### 1.10 系统界面（无法玻璃化，只能品牌化）

| 位置 | 说明 |
|---|---|
| `APP/notification/NotificationsHelper.kt:92,127,154` | 3 个 `NotificationCompat.Builder`（`setOngoing`/`setSmallIcon(ic_gps_fixed)`），系统通知样式，**不可玻璃**；建议列“不做”。 |
| `Toast.makeText`×5 | 见 §1.8/1.9，可由 `GlassSnackbar` 替代。 |
| `Popup`×1、`Dialog`×1（回退）、`PlainTooltip` | 新窗口，见 §2-9。 |

> **Checkbox/Slider/FilterChip/ListItem/ModalBottomSheet/Snackbar/Divider**：在 `app/src/main`、`sharednew/commonMain` 的 `ui/` 与 `MainActivity` 范围内**均无使用**（扫描脚本匹配 `Name(`）。网格/预览的“复选框”是自绘 `SelectionCheck`。

---

## 2 用户截图问题根因对照表

### 2-1 设备详情页 Ⓘ 弹窗：左上角 ✕ 与设备名/编辑笔重叠

| 项 | 内容 |
|---|---|
| 实现 | `DeviceInfoDialog` 用 `CapsuleDialog(onClose=…)`，**没有 `title`**（`SN/ui/device/DeviceInfoDialogs.kt:188-194`）；设备名行 `padding(end = 40.dp)`，名称 `topBarTitle`(22sp)，旁边 44dp 笔图标（`:197-216`）。✕ 在 `CapsuleDialogPanel` 内以 `Modifier.align(Alignment.TopEnd).padding(8.dp)` **绝对定位**（`SN/ui/components/glass/CapsuleDialog.kt:312-318`），不参与布局。 |
| 静态几何 | ✕ 占 x∈[W−56, W−8]、y∈[8, 56]；名称行右缘 = W−24（dialogPadding）−40 = W−64 → **水平仅 8dp 间隙，且 ✕ 与名称第一行同高（y 24–52）**。默认字号下无像素重叠，但视觉上挤在同一行；放大字体/长名称时更接近。 |
| 与用户描述的出入 | 代码里 ✕ 在**右上角（TopEnd）**，用户说“左上角”。**待确认**（需截图；本项目无 RTL 适配代码）。 |
| 根因判断 | ✕ 是浮在内容之上的绝对定位叠层，内容区顶部没有为它预留一整行（只在 `title != null` 时给标题右侧让 36dp，`CapsuleDialog.kt:301`）；Ⓘ 弹窗恰好无 title，靠 `padding(end=40.dp)` 手工避让，余量不足。 |
| 建议 | `CapsuleDialog` 增加头部行布局（标题/自定义头部 weight 1 + ✕ 同一 Row），或内容顶部统一预留 48dp；Ⓘ 弹窗把名称+笔放入头部行。 |

### 2-2 同弹窗：蓝牙地址默认隐藏；自动校时/区域用 ✓ + “已启用”

| 项 | 内容 |
|---|---|
| 地址 | `InfoRow(m2_info_address){ InfoValue(deviceId.uppercase()) }` 始终显示（`DeviceInfoDialogs.kt:240`），无“点按显示”。→ 新增 `RevealValueRow`（N17）。 |
| 自动校时/区域 | `InfoValue(settingText(settingDisplay(session, session?.autoTimeCorrection)))`（`:256-261`），`settingText`（`:319-326`）把 `Enabled` 映射到 `Res.string.device_info_setting_enabled`，ZH“已启用（应用会保持开启）”（`RES-SN/values-zh-rCN/strings.xml:402`），EN “On (the app keeps it on)”（`RES-SN/values/strings.xml:431`），**无 ✓ 图标**。 |
| “位置写入”参照 | `writing` 时 18dp ✓（`colors.success`）+ `m2_info_location_active`（“进行中”）（`:241-255`）；蓝牙行同样 ✓ + 成功色（`:219-239`）。 |
| 建议 | 自动校时/区域：`Enabled` → ✓ + 短文案“已启用”（缩短 `device_info_setting_enabled` 或新增短 key）；长句并入“保持活跃”说明（`device_info_keep_active_hint`）。其他状态（off/unsupported/unknown）保持文字、不带 ✓。 |

### 2-3 设备清单页

| 子项 | 根因/证据 |
|---|---|
| 顶部“?”与日志按钮 | `APP/ui/device/DevicesScreen.kt:99-108`；回调 `onHelpClick/onLogsClick` 由 `APP/ui/device/CameraDeviceManager.kt:282-285` 传入，`APP/MainActivity.kt:240-259` 接线；目标页 Help `:217-228`、Logs `:261-273`。设置页无入口：`APP/ui/settings/SettingsScreen.kt:96-111` 只含 AppControls/PhotoTransfer/LogLevel/EventSounds/Battery/Language/WhatsNew；`SettingsScreen(whatsNew, onBackClick)`（`MainActivity.kt:206-215`）无这两个回调，需扩签名。 |
| “添加相机”按钮 30→706px，卡片 30→690px | **根因：滚动条槽 8dp**。`SN/ui/components/ScrollbarLazyColumn.kt:30-34` 给 `LazyColumn` 追加 `.padding(end = 8.dp)`；`SN/ui/devicelist/SharedDeviceList.kt:133-136` 外层又有 `padding(horizontal = screenMargin(16dp))` → 卡片右缘 = W−16−8。`AddCameraButton`（`SN/ui/devicelist/DeviceListLayout.kt:118-131`）在 header 里、列表之外，用 `AdaptiveAction(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))`（`SN/ui/components/AdaptiveContent.kt:31-39`：`BoxWithConstraints.fillMaxWidth`，≥600dp 时 `widthIn(max=360dp)`）→ 右缘 = W−16。差 8dp，与 706−690=16px（≈2× 密度）吻合。 |
| 修复方向 | ① 滚动条改为叠在内容之上（不占布局）；或 ② 页内所有“整行元素”共用同一容器（`DeviceListLayout` 统一 `padding(horizontal=screenMargin)`，header/列表/footer 都放进去）。 |
| 底部“需要帮助？” | `SN/ui/devicelist/DeviceListLayout.kt:100-113`（M3 `TextButton` + `AlphaTextStyles.button` 蓝字，`:106-112`；`helpLabel` 参数 `:51`）；`DevicesScreen.kt:117-160` 传 `onNeedHelp=onTroubleshootingClick`（指向排障页）。`AGENTS.md`“应用内通用帮助只在设备清单提供入口”与新要求冲突（决策 D3）。测试引用：`app/src/androidTest/java/com/saschl/cameragps/DeviceListLayoutTest.kt:16,43,77`、`AdaptiveLayoutTest.kt:36,71`。 |

### 2-4 设备详情页

| 子项 | 根因/证据 | 建议 |
|---|---|---|
| 蓝牙已连接时右侧是“断开蓝牙”动作图标 | `ConnectionAction` 枚举（`SN/ui/device/DevicePagePresentation.kt:29-49`）：`Connected → Disconnect`；`ConnectionCard` 右侧渲染 `GlassIconButton(m2_bluetooth_disabled_24px, Subtle)`（`SN/ui/device/DeviceDetailScreen.kt:327-332`）；点击走二次确认 `CapsuleDialog`（`:263-278`）。文件头注释（`:122-127`）写明“small disconnect icon”。 | 右侧改只读 `StatusBadge`（✓ + “已连接”，success）；“断开”下沉到 Ⓘ 弹窗（与“移除”并列）或卡片溢出动作。 |
| “位置传输”卡三小格图标未按含义着色 | `StatusRow` 仅在 `item.tone != Neutral` 时着色图标（`SN/ui/components/glass/StatusRow.kt:126`）；`LocationCard` 三格 tone：信号仅 Poor→Warning（`SN/ui/device/DeviceDetailScreen.kt:380`），已写入/刚刚无 tone（`:382-391`）→ 全灰（`onSurfaceVariant`）。 | `StatusItem` 增 `iconTone`（与值文字 tone 分离）：信号强→success/中→warning/弱或无→danger；已写入→accent；刚刚→success，陈旧→neutral。注意亮色 `warning #F9A825` 在卡片上仅 1.9:1（§3.3）。 |
| 底部 Hero“照片传输”按钮过大 | `CapsuleButton(Primary, size=Hero)`（`DeviceDetailScreen.kt:439-448`）：68dp 高/17sp（`CapsuleButton.kt:64-68`；`SN/ui/theme/Dimens.kt` `heroButtonHeight`），A/B 档贴底常驻（`:236-259`），上方还有一行提示（`:425,434`）。 | 降 Large(56) 或并入主卡。 |
| 两张卡观感割裂 | 状态卡 `ConnectionCard` 与位置卡 `LocationCard` 是两张等权 GlassCard，“照片传输”是第三个块钉在底部（A/B 档 `:236-259`，C 档 `:219-235`）。 | 见下两个方案。 |

**方案 1：“一张主卡 + 内置分区”（保守，改动小）**
```
顶栏:  ←   设备名                          ⓘ
┌ GlassCard ──────────────────────────────────┐
│ ✓ 已连接 (StatusBadge)             🔋 85%   │
│                                              │
│ 位置传输                                     │
│ [📶 强]   [✎ 已写入]   [⏱ 刚刚]  (图标按含义着色) │
│                                              │
│ [        📷  照片传输        ›        ]       │  ← CapsuleButton Large(56) 玻璃主色
└──────────────────────────────────────────────┘
(下方留白，无贴底 Hero)
```
- 一张卡承载“连接 → 位置 → 照片传输”的阅读顺序；按钮在卡内、与卡内容同宽。
- C 档（横屏）：左列“状态 + 位置”，右列“照片传输”。

**方案 2：“状态头 + 双磁贴”（视觉更强）**
```
顶栏:  ←   设备名                          ⓘ
 [相机图标]  设备名
 ✓ 已连接 · 🔋85%                                   ← 状态头，直接在背景上，无卡
┌ 位置传输 (磁贴, 左) ─────────┐ ┌ 照片传输 (磁贴, 右) ┐
│ 📶 强   ✎ 已写入              │ │      🖼️            │
│ ⏱ 刚刚                       │ │  浏览 / 下载    ›  │  ← 整块可点，无单独按钮
└──────────────────────────────┘ └────────────────────┘
```
- 取消“按钮 vs 卡”的二元，“照片传输”是与位置同级的可点磁贴；A 档磁贴上下排。缺点：需新增磁贴组件，“传输中/需重连”状态要在磁贴内表达。

### 2-5 相机照片日期列表：副标题含“昨天/今天”

| 项 | 内容 |
|---|---|
| 位置 | `SN/ui/remote/WifiPhotoDates.kt:83-93` `daySubtitle(key, today)`：`weekdayLabel` + 相对词（`mw_dates_today/mw_dates_yesterday`，`:87-91`）以 ` · ` 连接；`DateRow`（`:162-187`）标题为原始日期键 `yyyy-MM-dd`（`:174`）。 |
| 模型 | `SN/ui/remote/WifiBrowserModel.kt:150-166` `DayRelation{Today,Yesterday,Other}` + `dayRelation()`；`:170` `dateKeyWeekday`；`today` 来自 `Clock.System.todayIn(…)`（`WifiPhotoDates.kt:140`，导入 `:55-60`）；测试 `sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiBrowserModelTest.kt:125-130`（相对日）、`:135-137`（周几）。 |
| 字符串 | `RES-SN/values/strings.xml:576-577`、`values-zh-rCN/strings.xml:547-548`；周几 `mw_weekday_1..7`（EN `:578-584`，ZH `:549-555`）**保留**。 |
| 改法 | 副标题只留周几：删 `DayRelation`/`dayRelation`/`today` 参数与导入/测试/两条字符串。 |

### 2-6 整日网格页

**(a) 锚点是否随图片绘制区域，以及 Fit/Crop**
- **代码里锚点不跟随图片区域**：`PhotoGridTile`（`SN/ui/remote/WifiPhotoBrowser.kt:438-543`）外框是 `Box.fillMaxWidth().aspectRatio(1f).clip(shape)`（`:455-458`），所有锚点都是该方形框的子元素：格式/大小 `BottomStart`（`:488-496`）、保护 `TopEnd`（`:497-506`）、复选框 `BottomEnd` 40dp（`:511-521`）、放大镜 `TopStart` 40dp（`:522-541`）。`PhotoTile` 的 `Image` 是 `fillMaxSize` + **`ContentScale.Crop`**（`:609-621`，Crop 在 `:613`）→ 图片铺满方框，竖/横图不会造成锚点错位。
- 用户观察到的“竖图/横图位置不齐”：**待确认**（需截图）。可疑：缩略图未加载（占位/spinner `:615`）、缩略图本身带黑边（缩略图来源=ARW 优先否则 preview，`SN/remote/wifi/WifiCameraCapture.kt:19-21`）、或网格尺寸取整。
- **格子是 Crop 而非 Fit**（`:613`，文档注释 `:432-436,605-608` 写明“有意裁剪”）。用户要完整显示 → 改 `ContentScale.Fit`：方框与锚点位置不变，留白由 `onSurface@.08` 底色（`:458`）呈现；若要“框随图比例”需换 `LazyVerticalStaggeredGrid`，会改变锚点语义（决策 D4）。

**(b) 视图选项菜单（右上角网格图标）**
- 不是 `DropdownMenu`，而是**平台 `Popup`**（`SN/ui/remote/WifiPhotoTopBar.kt:191-196`，`alignment=TopEnd`，`PopupProperties(focusable=true)`）；内容 `Column(Modifier.width(232.dp).glass(GlassLevel.Modal, AlphaShapes.popover)…)`（`:198`）：尺寸分段（`:201-232`，自绘）+ 3 个 `OptionSwitch`（格式/大小/保护，`:233-244`）；`OptionSwitch`（`:249-268`）= 行 `toggleable` + **原生 M3 `Switch(onCheckedChange = null)`**（`:267`，仅展示状态）。
- `Popup` 是独立窗口，Haze 取样源在主窗口；`CapsuleDialog` 回退路径会显式 `LocalGlassBackdrop provides null`（`CapsuleDialog.kt:231`），而此 Popup 内**没有**这一处理 → 模糊是否渲染/是否错位 **待确认**。

**(c) 保护与评分的全部引用点**：见附录 A。

**(d) 文件大小标签：位置、单位、口径**
- 位置：与格式标签同一 `Row`，`BottomStart`，`padding(end = TileActionSize)`（`WifiPhotoBrowser.kt:488-496`）；右上 `TopEnd` 现为保护标签（`:497-506`），移除保护后可让给大小。
- 单位：`formatSizeTag`（`SN/ui/remote/WifiBrowserModel.kt:129-134`）总是输出 `"x.y MB"`（二进制 MB，1 位小数，**带 “ MB” 后缀**）→ 已统一 MB 但带单位；下载按钮用 `formatByteSize`（`SN/remote/wifi/WifiFormatPolicy.kt:138-151`）是**自适应 KB/MB/GB**，两处口径不同。
- 口径：`J&R 66.4` 是**两个标签**：`J&R` = `formatBadge`（`WifiFormatPolicy.kt:33-46`，多格式取首字母以 `&` 连接，JPG+RAW→`J&R`）+ `66.4 MB` = `capture.files.sumOf{it.size}`（该 capture **全部文件之和**即 JPG+RAW，`WifiPhotoBrowser.kt:488-496`）。
- 下载按钮“下载 1 张 JPG（6.6 MB）”：`viewModel.batchPlan(policy, state)` → `planDownloads`（`WifiFormatPolicy.kt:85-110`）：`JpegOnly` 策略只取每个 capture 的 JPEG（跳过已保存/过大）；`formatByteSize(plan.totalBytes)`（`WifiPhotoBrowser.kt:409-425`，字符串 `wifi_remote_batch_download_jpg`）。→ **口径不一致**：格内=全部文件，按钮=默认下载格式的实际下载量。预览页信息条用的是后者口径（`SN/ui/remote/WifiPhotoViewerModel.kt:126-127`：先 `planDownloads(policy).totalBytes`，为 0 才退回全部文件之和）。
- 建议：格内大小改“按当前下载策略将下载的大小”（与按钮、预览一致），统一 MB 且不带单位时，按钮/预览处保留单位以免歧义（决策 D6）。

### 2-7 悬浮下载按钮外圈粗深色描边

| 层 | 证据 | 效果 |
|---|---|---|
| ① 浮层自身 | `GlassFloatingBar(contentPadding = PaddingValues(6.dp))`，默认 `GlassLevel.Float`（`SN/ui/components/glass/GlassSurface.kt:200-215`；`WifiPhotoBrowser.kt:409-425`）：暗色 fill `#20252B@.74` + 边框 `.26→.08` + 阴影 `(6dp,20dp,Black@.42)`（`SN/ui/theme/Glass.kt:75-81`）；亮色 fill `F9FAFE@.78` + 阴影 `Navy@.18`（`:54-60`） | 按钮外一圈 6dp 深色胶囊 |
| ② 按钮辉光 | `CapsuleButton` raised（Large Primary）：`dropShadow(radius 16dp, offset y 6dp, color=glow)`（`CapsuleButton.kt:153-155,109`；暗色 `Black@.5`、亮色 `0B57D0@.34`） | 外圈再叠一层深晕 |
| ③ 底部 scrim | `Brush.verticalGradient(Transparent → scrim@0.28)`（`WifiPhotoBrowser.kt:353-357`）；暗色 scrim `Black@.52`（`SN/ui/theme/Color.kt:165`） | 底部整体变暗 |
| ④ 无模糊 | 网格在 `GlassBackground(contentIsBackdropSource=false)`（`WifiRemoteScreen.kt:676`），浮层只取样 G0 | 玻璃“透”不出缩略图，只剩深色填充 |
- 根因：**“玻璃浮层 + 带阴影的实心按钮”双重包装**，且玻璃没有可模糊的内容。
- 建议：去掉外层 `GlassFloatingBar`，改单一“玻璃主按钮”（半透明主色 tint + 高光边 + 极轻阴影）直接浮在 scrim 上，scrim 保留但更淡。

### 2-8 单张预览页

| 子项 | 结论/证据 |
|---|---|
| 底部下载按钮：不透明浅蓝实心 | `ViewerDownloadArea`（`SN/ui/remote/WifiPhotoViewer.kt:718-755`）默认分支 `CapsuleButton(text=wifi_remote_download…)` 无 `role/size` → Primary + Regular（`:747-752`）；预览页强制暗色主题（`CameraGpsTheme(darkTheme=true)` `:197`）→ primary=`#A8C7FA` 实心。 |
| 信息条（格式·大小）深色胶囊底 / “JPG + RAW”二层深色底 | `ViewerInfoStripBar`：`GlassSurface(Viewer)`（`101418@.66`）`:550-565`；内部 `ViewerBasicLine`（`:567-591`）的格式用 `InfoTag(OnPhoto)`（`Black@.60`，`SN/ui/components/glass/InfoTag.kt:31`）→ 玻璃条里再压一层黑底。“JPG + RAW” 由 `viewerFormatText`（`WifiPhotoViewerModel.kt:52-57`，顺序 JPG,HEIF,RAW，` + ` 连接）生成。 |
| 右下方形复选框 | `ViewerSelectionToggle`（`:822-837`）：48dp 点击区 + 22dp `SelectionCheck`（未选 `Black@.25` + 2dp 白框；选中 primary 实心，同网格 `WifiPhotoBrowser.kt:589-603`）。 |
| 胶片条 | `ViewerFilmstrip`（`:841-880`）：40dp 单元、6dp 圆角、`White@.08` 底（`:863`）、当前项 2dp 白框（`:864`）、非当前 alpha .62（`:874`）、缩略图 `Crop`（`:873`）；容器无玻璃；`captures.size < 2` 时不显示（`:850`）。 |
| 详情入口 | **确认：没有任何入口**。竖屏 A 档 `PortraitViewer`（`:354-381`）不含 `ViewerDetailsPanel`；它把额外信息内联到信息条（`strip(..., true)` `:365-369`）。`ViewerDetailsPanel`（`:661-680`）**仅在 `WideViewer`（B/C）调用**（`:429`）。详情内容=参数/镜头/直方图（`ViewerPanelExtras` `:683-706`），**无点击/手势/按钮入口**。 |
| 三个设置开关控制什么 | `APP/ui/settings/PhotoTransferSettingsCard.kt:97-130` → `PreferencesManager.kt:231-256`（键 `viewer_show_histogram/viewer_show_exif_params/viewer_show_lens`，**默认 false**）→ `APP/ui/device/AndroidWifiRemoteScreen.kt:102-103,144-148`（ON_RESUME 重读）→ `ViewerInfoOptions`（`WifiPhotoViewerModel.kt:20-23`）→ `WifiRemoteScreen.kt:601,702` → `WifiPhotoViewer.kt:173`。`showExifParams/showLens` 触发扩展元数据读取（`viewerNeedsExtendedMetadata` `:31-32`；`WifiPhotoViewer.kt:193-196` → `showViewerMetadata` `WifiRemoteScreen.kt:574`）；直方图仅开启时计算（`WifiPhotoViewer.kt:251-258`）。 |
| 默认全关时用户能看到什么 | 只有“基础行”：格式 · 分辨率 · 大小（`WifiPhotoViewerModel.kt:17` 注释“basic line always shown”）；B/C 详情面板滚动区为空（只剩下载按钮与复选框）。**没有 EXIF/镜头/直方图，也没有入口**。 |
| 建议 | 提供显式入口：点击信息条（或 ⓘ）→ 底部玻璃抽屉（A 档，N20）/ 右侧面板（B/C 常驻）；三个开关改为“详情默认展开哪些块”（决策 D7）。 |

### 2-9 玻璃效果本身（Haze）

**(a) hazeSource / hazeEffect 配对**

| 范围 | 取样源（hazeSource） | 玻璃（hazeEffect） | 实际可见模糊？ |
|---|---|---|---|
| 全 App | `APP/MainActivity.kt:57-67` → `GlassBackground`：Canvas（G0, zIndex 0，`GlassBackground.kt:45`）+ 页面内容（zIndex 1，`:48-53`） | 页内所有 `Modifier.glass(...)`（`GlassSurface.kt:57-115`，`hazeBlur` `:90-94`）：GlassSurface/GlassCard/GlassFloatingBar/GlassIconButton(container) | 卡片下无内容，**只模糊 G0（近似纯色）→ 不可见**；“内容作为 zIndex1 源时自己取样自己”的行为 **待确认**（Haze 2.1.0 仅有 aar，未验证 zIndex 语义）。 |
| 对话框 | 同上（页内 overlay：`CapsuleDialog.kt:198-220` 挂在 `GlassBackground` 的 `GlassOverlayLayer`，`GlassBackground.kt:56`） | `CapsuleDialogPanel` → `GlassSurface(Modal)`（`:239-320`） | **有可见模糊**（背后是页面内容，Modal blur 32dp，亮色 fill .86） |
| 二级页（设置/帮助/关于/日志/许可/隐私） | `GlassPageScaffold` 自带内层 `GlassBackground`（`SN/ui/components/page/GlassPageScaffold.kt:53-55`） | 页内卡片、行 | 同“全 App”：不可见 |
| Wi-Fi 屏（日期/网格/连接控件） | `WifiRemoteScreen.kt:676` `GlassBackground(contentIsBackdropSource = false)`：只有 G0 | 浮层、顶栏按钮、传输状态条、连接叠层 `WifiConnectionOverlay.kt:158` | **不可见**（设计如此：`MainActivity.kt:59-63` 注释） |
| 预览页 | `WifiPhotoViewer.kt:287` `rememberGlassBackdrop()` + `:303` 照片 pager `glassSource(backdrop)` + `:320` 提供 `LocalGlassBackdrop` | 信息条（`:556-565`）、顶栏按钮 container、下载进度胶囊（`GlassLevel.Viewer`） | **有可见模糊**（对照片取样，Viewer blur 24dp） |
| 视图选项菜单 | 主窗口取样源；菜单在 `Popup` 新窗口（`WifiPhotoTopBar.kt:191-198`） | `glass(Modal, popover)` | **待确认**（跨窗口 Haze 取样/坐标） |

**(b) 开新窗口导致无法取样背景的情形**
1. `Popup`：视图选项菜单（`WifiPhotoTopBar.kt:191`）——唯一的生产 `Popup`。
2. `Dialog(`：仅 `CapsuleDialog` 回退（无 overlay host，`CapsuleDialog.kt:222`），显式关取样（`:231`）→ 实心玻璃。生产路径不会走到（`MainActivity` 根已有 host），测试/预览会走。
3. `PlainTooltip`（`GlassIconButton` 的 `TooltipBox`）、`Toast`×5、系统通知：系统窗口/样式，无法玻璃。
4. 约束：任何新增 `DropdownMenu`/`ModalBottomSheet`/`Dialog` 都是新窗口——**新组件必须走 `GlassOverlayHost`（页内）**。

**(c) 背景实现与 token 现状；“不改背景”时怎样让质感可见**
- 背景 `GlassBackground`：`drawGlassBackdrop`（`GlassBackground.kt:62-69`）= `drawRect(colors.background)` + 3 个径向光晕（蓝 (0.12w,0.10h) r0.78w；紫 (1.02w,0.46h) r0.70w；青 (0.20w,0.95h) r0.75w），alpha 亮色 `[.60,.42,.36]`（`Color.kt:118-121`）/暗色 `[.42,.30,.26]`（`:166-169`）。**无纹理/噪点/高频细节**。
- 玻璃 token（`SN/ui/theme/Glass.kt`）：Card 亮 blur20 fill `White@.58` rim `.95→.35` sheen `.30` shadow `(4,16,0B57D0@.10)`（`:47-53`）；暗 fill `White@.07` rim `.22→.06` sheen `.05` shadow `Black@.30`（`:68-74`）；Float/Modal/Viewer 见 `:54-67,75-95`；`noiseFactor(0f)`（`GlassSurface.kt:71`）；`HazeColorEffect.tint(fill)`（`:72`）；sheen 为单向线性 0→0.55（`:99-107`）；边框 1dp 竖向渐变（`:108-114`）。
- 不改背景时的候选手段（按成本排序，**均需真机对比，待确认**）：
  1. **边缘光**：1dp 渐变边框改“左上亮/右下暗”双向或 1.5dp；暗色 rim 仅 `.22→.06`，在 `#0E1217` 上几乎不可见。
  2. **内高光/内阴影**：顶部 1–2dp 内亮带 + 底部内暗带，给卡片“厚度”。
  3. **阴影分层**：ambient（大半径低 alpha）+ key（小半径偏移）双层；亮色阴影 primary tint 加深。
  4. **噪点**：`noiseFactor` 0→0.03–0.06，在平坦背景上给玻璃颗粒（Haze 2.1.0 对应 API/性能待确认）。
  5. **填充反差**：亮色 `White@.58` 叠 `#F3F6FC` 亮度差极小；fill 加极淡 primary/青 tint，或略降低底色亮度/提高 `glowAlphas`。
  6. **叠层**：卡片内再放 G2 玻璃（输入框、磁贴），形成“玻璃叠玻璃”（需确认 Haze zIndex 语义，待确认）。
  7. **让内容真的经过玻璃**：顶栏/底栏做成 edge-to-edge 浮层，列表在其下滚动——不改背景时唯一能出现“真模糊”的结构（预览页已是如此，列表/网格页不是）。
  8. 备选（用户已声明“不改背景”）：背景加低对比大尺度纹理/彩色 orb 供取样。

---

## 3 文字与颜色规范

### 3.1 集中定义处现状

| 位置 | 内容 |
|---|---|
| `SN/ui/theme/Type.kt:10-17` | `Typography(bodyLarge = 16/24/ls0.5)`，其余 M3 档沿用 M3 默认（注释掉）；`:36-50` `AlphaTextStyles`：`topBarTitle 22/28 SB`、`dialogTitle 20/26 SB`、`dialogBody 13.5/20 N`、`button 15/20 SB`、`buttonLarge 16/22 SB`、`buttonHero 17/22 SB`、`statusValue 15/20 SB`、`statusLabel 11.5/15 N`、`tag 11/14 SB`；无 fontFamily/letterSpacing；样式不含颜色。 |
| `SN/ui/theme/Color.kt:20-73` | `AlphaColors`（亮/暗两套，含 `success/warning/warningText/…container`、`glow*`、`scrim`），`toColorScheme()`（`:218-`）映射 M3；`RatingColors`（`:226-235`，**无引用死代码**）。 |
| `SN/ui/theme/Theme.kt:30-46` | `CameraGpsTheme(darkTheme, glassRenderMode)`、`AlphaTheme.colors`；`MaterialTheme(colorScheme, typography=Typography)`。 |
| `SN/ui/theme/Glass.kt`、`Dimens.kt`、`Contrast.kt` | 玻璃 token、形状/间距、WCAG 对比度工具；测试 `sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/ui/theme/PaletteContrastTest.kt`。 |
| app 资源 | `RES-APP/values/colors.xml`、`values-night/colors.xml`、`themes.xml`（`Theme.Material3.DayNight.NoActionBar`，启动页/系统栏用）。 |
- 结论：**已有集中处（Type.kt/Color.kt/Glass.kt），但档位只有 9 个且未覆盖大多数 UI；M3 档与 Alpha 档混用，颜色有 `MaterialTheme.colorScheme.*`（50 处）与 `AlphaTheme.colors.*`（190 处）两个入口。**

### 3.2 现状分布

**(a) 使用次数（93 个 UI 文件：`app/src/main`、`sharednew/commonMain` 的 `ui/`、`MainActivity`、`notification`）**

| 来源 | 档/项 | 次数 |
|---|---|---|
| `MaterialTheme.typography.*` | bodySmall 26 · bodyMedium 26 · titleMedium 14 · bodyLarge 11 · labelLarge 7 · labelSmall 5 · titleSmall 3 · titleLarge 3 · labelMedium 2 · headlineMedium 2 · headlineSmall 1 | 共 100 |
| `AlphaTextStyles.*` | statusLabel 11 · dialogBody 10 · topBarTitle 4 · button 4 · statusValue 3 · buttonLarge 3 · dialogTitle 2 · tag 1 · buttonHero 1 | 共 39 |
| `FontWeight.*` | SemiBold 16 · Medium 11 · Bold 7 · Normal 5 | 39 |
| 无 `style=` 的 `Text(` | 192 处中 57 处 | 30% |
| 硬编码 `fontSize`（Type.kt 外） | `APP/ui/about/LicensesScreen.kt:97`（12）；`SN/ui/logs/SharedLogViewerScreen.kt:158`（12，等宽）；`SN/ui/device/DeviceInfoDialogs.kt:315`（14）；`SN/ui/devicelist/SharedDeviceList.kt:230,374,381,388`（13）；`SN/ui/device/DeviceDetailScreen.kt:425,434`（=13.5） | 9 |
| 无 style 的 `Text(` 分布（总/无 style） | `WifiRemoteScreen` 14/14 · `WifiPhotoBrowser` 14/9 · `PermissionBox` 20/6 · `WifiManualJoin` 5/5 · `WifiPhotoViewer` 18/5 · `WifiPhotoDates` 6/3 · `ScanForDevicesMenu` 3/2 · `SystemStateWarnings` 6/2 · `SharedPairingPreparationScreen` 6/2 · `SharedWelcomeScreen` 7/2 · `WifiRemoteExperimentalNotice` 2/2 · `WifiPhotoTopBar` 5/1 · `DeviceInfoDialogs` 7/1 · `CameraDeviceManager` 2/1 · `TroubleshootingScreen` 1/1 · `GlassIconButton` 1/1 | 57 |

**(b) 语义 × 实际用法（同一语义出现的不同样式）**

| 语义 | 实际用法（次数/代表行号） | 种数 |
|---|---|---|
| 页面标题 | `topBarTitle`：`SN/ui/device/SharedDevicesScreen.kt:31-45`、`APP/ui/device/DeviceDetailScreen.kt:138-147`、`GlassPageScaffold.kt`；M3 默认（无 style）：`WifiPhotoTopBar.kt:104-115`、`WifiRemoteScreen.kt:758-769`、`APP/ui/TroubleshootingScreen.kt:30-33`（仅 `SemiBold`）；`headlineMedium`：`SharedPairingPreparationScreen.kt:77`；`titleLarge`：`PermissionBox.kt:197` | 5 |
| 弹窗标题 | `dialogTitle 20`（`CapsuleDialog.kt:301`）；Ⓘ 弹窗名称 `topBarTitle 22`（`DeviceInfoDialogs.kt:201-208`） | 2 |
| 卡片/分组标题 | `titleMedium+SemiBold`：`SharedSettingsCard.kt:46-49`；`buttonLarge`：`SharedDeviceList.kt:345-351`、`DevicesScreen.kt:185-216`；`dialogTitle`：`EmptyStateCard`（`SharedDeviceList.kt:181-224`）；`titleSmall+Medium`：`SystemStateWarnings.kt:63-65`；`bodyLarge+Medium`（行标题）：`SharedSettingsCard.kt:94-95,143-144` | 5 |
| 正文 | `dialogBody 13.5`（10）；`bodyMedium 14`（26，含描述）；`bodyLarge 16`（11）；**无 style 默认 16/0.5ls**（57）；`InfoValue 14sp` | 5 |
| 说明/次要 | `statusLabel 11.5`（11）；`bodySmall 12`（26）；`statusLabel.copy(13)`（4）；`bodyMedium+onSurfaceVariant`（设置描述） | 4 |
| 标签/标记 | `tag 11 SB`；`labelSmall 11 Medium`（5）；`labelMedium 12`（2）；`labelLarge 14`（7，预览页） | 4 |
| 数值强调 | `statusValue 15 SB`（3）；`InfoValue 14`；预览页 `labelLarge`+白（`WifiPhotoViewer.kt:587`） | 3 |
| 按钮文字 | `CapsuleButton` 内置 `button 15/buttonLarge 16/buttonHero 17`；原生 `Button`/`TextButton` 默认 labelLarge 14（`PermissionBox.kt:241-460`、`SharedWelcomeScreen.kt:125-174`、`SystemStateWarnings.kt:79,131`） | 4 |
| 错误/成功文字 | `Text(color=error)` 无 style（`ScanForDevicesMenu.kt:169-173`、`WifiRemoteScreen.kt:101`）；`statusLabel 13`+error（`SharedDeviceList.kt:378-390`）；`bodySmall`+error（`WifiPhotoViewer.kt:730-731`）；成功色 `InfoValue`（`DeviceInfoDialogs.kt:219-239`） | 4 |

**(c) 颜色硬编码与入口**
- `Color(0x…)`（主题文件外）：仅 `SN/ui/components/glass/CapsuleButton.kt:109`（`0xFF0B57D0` 辉光）；`SN/ui/theme/Glass.kt` 内 12 处（玻璃 token，归属合理）。
- `Color.White/Black`：`WifiPhotoViewer.kt` 20 处（`:157,321,454-865`）、`WifiPhotoBrowser.kt` 3（`:533,538,594`）、`CapsuleButton.kt` 3、`GlassIconButton.kt` 3（`:44-46`）、`GlassSurface.kt` 3、`InfoTag.kt:31`、`Color.kt` scrim 4、`Glass.kt` 8。
- `.copy(alpha = …)`：玻璃组件内部 + `WifiPhotoBrowser.kt` 5、`WifiPhotoViewer.kt` 5、`WifiPhotoTopBar.kt` 2、`WifiConnectionOverlay.kt` 1、`SharedWelcomeScreen.kt` 1。
- `MaterialTheme.colorScheme.*` 50 处：`PermissionBox` 11、`TroubleshootingGuideScreen` 11、`SystemStateWarnings` 8、`SharedWelcomeScreen` 6、`BatteryOptimizationCard` 4、其余各 1–2；与 `AlphaTheme.colors.*` 190 处并存（`onSurfaceVariant` 43、`onSurface` 31、`error` 10、`primary` 9）。

### 3.3 不一致清单 & 对比度可疑点

**不一致**
1. 页面标题三套：Alpha `topBarTitle 22`、M3 默认标题、`headlineMedium/titleLarge`。
2. 弹窗内设备名 22sp（`topBarTitle`）与弹窗标题 20sp（`dialogTitle`）并存。
3. “说明文字”：设备清单 13sp，详情页 13.5sp，设置页 `bodyMedium 14`，预览页 `bodySmall 12`/`labelSmall 11`。
4. “行标题”：设置页 `bodyLarge 16 Medium`，设备卡 `buttonLarge 16 SB`，详情卡 `StatusLine` `titleMedium 16 SB`（`StatusRow.kt:80`）。
5. 57 个 `Text` 无 style → 默认 16/0.5ls，与周围 13.5/14 文字不协调（Wi-Fi 连接页最明显）。
6. 按钮文字：`CapsuleButton` 内置 15/16/17 与原生 Button 14 并存；`CapsuleSize` 三档（`CapsuleButton.kt:64-68`）与 `AlphaTextStyles.button*` 重复定义。
7. 预览页白/半白文字有 `Color.White`/`WhiteSoft(.72)`/`White@.7/.14/.2/.08` 多种；`glassContentColor(Viewer)=White`（`GlassSurface.kt:119-120`）存在但未被统一使用。
8. 颜色入口两套，导致 `errorContainer/secondaryContainer/surfaceVariant` 这类玻璃体系中没有语义的角色漏入（权限页/排障页/欢迎页/警告卡）。

**对比度（按 `Contrast.kt` 同公式估算，背景取卡片 fill 叠在 `background` 上；均需真机核对）**

| 组合 | 估算比值 | 判定 |
|---|---|---|
| 亮色 `warning #F9A825` 作图标/点在亮色卡片 | **1.9:1** | ❌ 低于 3:1（非文本）；文字应用 `warningText #7A5200`（6.7:1）。`StatusDot`/`StatusRow` 图标用 `forText=false`（`StatusRow.kt:37-43,126`）取 `warning` → 亮色主题下警告图标偏淡。 |
| 亮色 `onSurfaceVariant #42474E` 在卡片 | 9.0:1 | ✅ |
| 亮色 `success #1B6E3A` / `primary #0B57D0` / `error #BA1A1A` 在卡片 | 6.1 / 6.2 / 6.2 | ✅ |
| 暗色 `onSurfaceVariant #C2C7CF` / `success #7DDB93` 在暗色卡片 | 9.3 / 9.4 | ✅ |
| 预览页 `WhiteSoft(.72)` 文字在信息条（`101418@.66`）上、照片为纯白时 | **约 4.0:1** | ⚠ <4.5（照片高亮区）；中灰 6.9、黑 10.1 |
| 11.5sp `statusLabel` | 对比度足够但字号偏小 | ⚠ 小于 12sp |
| 暗色 `InfoTag(OnSurface)` | 注释已处理 AA（`InfoTag.kt:35-36`） | ✅ |
| `OnPhoto`：白字 + `Black@.60` | ≥5.7:1 | ✅ 但“深底”观感差（§1.7） |

### 3.4 建议的“固定档位”方案

> 目标：UI 层**不再直接引用** `MaterialTheme.typography.*`、不再出现 `fontSize =`/`.copy(fontSize…)`；字号只从 `AlphaType` 取。

**文字 6 档**

| 档名 | sp / 行高 | 字重 | 用途 |
|---|---|---|---|
| `Title` | 22 / 28 | SemiBold | 页面标题（顶栏）、设备名（Ⓘ 弹窗头部）、权限/欢迎页主标题 |
| `Heading` | 18 / 24 | SemiBold | 弹窗标题、卡片/分组标题、设置分组标题、设备卡名称、空状态标题 |
| `Body` | 15 / 22 | Normal | 正文、弹窗消息、行描述（主说明）、输入框文字、列表主行文字 |
| `Label` | 15 / 20 | SemiBold | 按钮文字（所有尺寸/role，含 Hero）、数值强调（状态值/大小/位置）、行内强调、行标题 |
| `Caption` | 13 / 18 | Normal | 次要说明、状态标签、分组小标题、日期副标题、预览页分辨率/参数文字、提示/错误提示行 |
| `Tag` | 11 / 14 | SemiBold | `InfoTag`/角标；不得用于可点击或关键信息 |

- 例外：日志视图等宽 12sp（`SharedLogViewerScreen.kt:158`）、许可证文本 12sp（`LicensesScreen.kt:97`）登记为 `Mono`（不计入 6 档，或并入 `Caption` + `FontFamily.Monospace`）。
- 字号单位 `sp`（随系统缩放）；`letterSpacing` 统一 0（现仅 `bodyLarge` 有 0.5）。
- 按钮高度（48/56/68dp）与字号解耦：`CapsuleSize` 只管高度/圆角/内边距，文字永远 `Label`。

**颜色 8 个角色（映射到 `AlphaColors`，新增处标注）**

| 角色 | 取值 | 用途 |
|---|---|---|
| `textPrimary` | `onSurface` | 主文字、标题 |
| `textSecondary` | `onSurfaceVariant` | 次文字、说明、图标默认 |
| `textMuted` | `onSurfaceVariant @ ≈0.6`（**新增**；亮/暗各自校验 ≥3:1） | 弱化文字：禁用、占位、辅助计数 |
| `accent` | `primary` | 强调、链接、选中、主按钮 |
| `success` | `success` | 成功/已连接/已写入 |
| `warning` | 文字 `warningText`；图标/点 `warning`（亮色需加深至 ≥3:1，**新 token 待选**） | 警告 |
| `danger` | `error` | 错误/危险 |
| `onPhoto` | 白；弱化 `White@.72`（弱化文字需满足 ≥4.5:1 的场景改用 `.85`） | Viewer 级玻璃与照片之上的反色文字/图标 |

- 规则：容器色（`errorContainer/secondaryContainer/primaryContainer/surfaceVariant`）不再用作文字场景底色；需要 tone 的卡片用“玻璃 + tone 描边/淡 tint”，文字色仍取上述 8 角色。

### 3.5 现有硬编码 → 档位对照表（按文件聚合）

| 文件 | 现有 | → 文字档 | → 颜色角色 |
|---|---|---|---|
| `SN/ui/components/glass/CapsuleButton.kt` | `CapsuleSize` 内置 20/22/24 + `AlphaTextStyles.button*`；`Color.White` rim、`0xFF0B57D0`/`Black@.5` 辉光 | `Label` | 辉光取 `accent`；rim 用 token |
| `.../glass/CapsuleDialog.kt` | `dialogTitle`、`dialogBody` | `Heading`、`Body` | `textPrimary/textSecondary` |
| `.../glass/StatusRow.kt` | `titleMedium+SB`（`:80`）、`bodySmall`（`:84`）、`statusValue`（`:132`）、`statusLabel`（`:140`） | `Heading`/`Caption`/`Label`/`Caption` | tone→success/warning/danger/accent |
| `.../glass/InfoTag.kt` | `tag` | `Tag` | OnSurface→`textSecondary`；OnPhoto→`onPhoto`（去 `Black@.6` 底，N13） |
| `.../glass/GlassIconButton.kt` | `Color.White`（Viewer）等 | — | `onPhoto` / `textSecondary` / `accent` |
| `SN/ui/device/DeviceDetailScreen.kt` | `statusLabel`、`.copy(fontSize=dialogBody.fontSize)`（`:425,434`）、`StatusLine` | `Caption`、`Heading` | `textSecondary` |
| `SN/ui/device/DeviceInfoDialogs.kt` | `topBarTitle`（名称）、`dialogBody`、`InfoValue 14sp`（`:315`）、`statusLabel` | `Heading`（名称）、`Body`、`Label`、`Caption` | `success`/`textSecondary` |
| `SN/ui/devicelist/SharedDeviceList.kt` | `statusLabel.copy(13)`×4、`buttonLarge`、`dialogTitle`、`dialogBody`、`button` | `Caption`、`Heading`、`Heading`、`Body`、`Label` | `textSecondary/success/danger/accent` |
| `SN/ui/devicelist/DeviceListLayout.kt` | `button`+`primary` | `Label` | `accent` |
| `SN/ui/remote/WifiPhotoDates.kt` | `titleMedium`、`bodySmall`、`bodyMedium`、3 个无 style | `Heading`、`Caption`、`Body` | `textPrimary/textSecondary` |
| `SN/ui/remote/WifiPhotoBrowser.kt` | `bodyMedium`、`bodySmall`、9 个无 style、`Black@.55/.25`、`White`、scrim `.28` | `Body`、`Caption` | `onPhoto`（标签/复选框）；scrim 走 token |
| `SN/ui/remote/WifiPhotoTopBar.kt` | 默认标题、`bodySmall`、`labelLarge`、`bodyLarge`、`bodySmall` | `Title`、`Caption`、`Label`、`Body`、`Caption` | `textPrimary/textSecondary` |
| `SN/ui/remote/WifiPhotoViewer.kt` | `titleMedium`、`bodySmall`、`labelLarge/Medium/Small`、`bodyMedium`、`statusValue/Label`、20 处白色 | `Heading`、`Caption`、`Label`、`Caption`、`Label`、`Caption` | `onPhoto`（含弱化） |
| `SN/ui/remote/WifiRemoteScreen.kt` / `WifiManualJoin.kt` / `WifiRemoteExperimentalNotice.kt` | 21 个无 style、默认标题 | `Title`、`Body`、`Caption` | `textPrimary/textSecondary`、`danger` |
| `SN/ui/settings/SharedSettingsCard.kt` / `SharedRadioOption.kt` | `titleMedium+SB`、`bodyLarge+Medium`、`bodyMedium` | `Heading`、`Label`（行标题）、`Caption` | `textPrimary/textSecondary` |
| `APP/ui/settings/*` | `bodyLarge+Medium`、`bodyMedium`、`bodySmall`、`colorScheme.*`（Battery 4） | `Label`/`Body`/`Caption` | 同上 + `warning/danger` |
| `APP/ui/PermissionBox.kt` | `titleLarge`、`titleMedium`、默认、`colorScheme.*` 11 | `Title`、`Heading`、`Body` | `accent`（完成）、`danger` |
| `APP/ui/SystemStateWarnings.kt` | `titleSmall+Medium`、`bodySmall`、`onErrorContainer`、`errorContainer` | `Heading`、`Caption` | `danger` |
| `SN/ui/help/TroubleshootingGuideScreen.kt` / `APP/ui/HelpScreen.kt` / `APP/ui/TroubleshootingScreen.kt` | 多档 M3 + `colorScheme.*` 11 | `Heading/Body/Caption` | `textPrimary/textSecondary` |
| `SN/ui/welcome/SharedWelcomeScreen.kt` | `titleMedium`、M3 Button/TextButton、`colorScheme.*` 6 | `Title/Body/Label` | `accent` |
| `SN/ui/logs/SharedLogViewerScreen.kt` / `APP/ui/about/LicensesScreen.kt` | 12sp 等宽 | `Mono`（例外） | `textSecondary` |
| `APP/ui/about/*` | `bodyMedium` 等 | `Body/Caption` | `textPrimary/textSecondary/accent` |

### 3.6 集中到哪里
- 扩展 `SN/ui/theme/Type.kt`：新增 `object AlphaType { Title, Heading, Body, Label, Caption, Tag }`（`AlphaTextStyles` 过渡期保留别名，已于 0.3.0 删除），并把 `MaterialTheme.typography` 的常用槽映射到同一 6 档（避免遗漏的原生/第三方组件“长回”M3 默认）。
- 扩展 `SN/ui/theme/Color.kt`：在 `AlphaColors` 增加 `textMuted`、`onPhoto`、`onPhotoMuted`、（可选）`warningIcon`；以别名属性暴露 8 角色。
- 玻璃观感 token（边缘光/内高光/噪点/双层阴影）放 `SN/ui/theme/Glass.kt`，不再散落到组件内。
- 测试：在 `PaletteContrastTest.kt` 基础上扩展 6 档×8 色×（4 个玻璃 level × 亮/暗）的对比度；再加一个“`ui/` 内除 token 文件外不得出现 `fontSize =`/`MaterialTheme.typography`/`Color.White`/`Color(0x`”的 grep 守卫（`scripts/*_test.sh` 风格）。

---

## 4 需新增/改造的组件清单及规格

均放 `SN/ui/components/glass/`；全部走页内 `GlassOverlayHost`（不开新窗口）、`AlphaType`/颜色角色、≥48dp 触控、提供 `semantics`。

| # | 组件 | 取代 | 规格 |
|---|---|---|---|
| N1 | `GlassSwitch` | 原生 `Switch`（`SharedSettingsCard.kt:111`、`DeviceInfoDialogs.kt:277`、`WifiPhotoTopBar.kt:267`） | 52×32 胶囊轨道：关=`textSecondary@.12`+细边；开=`accent` 半透明玻璃 tint + 高光；滑块 24→28dp 白圆、柔阴影；`role=Switch`；点击区由父行承担（保持现 `toggleable` 行语义，`onCheckedChange=null` 仅展示）；支持 `enabled`。 |
| N2 | `GlassRadio` | `RadioButton`（`SharedRadioOption.kt:37`） | 20dp 圆环，选中内点 `accent`；整行 `selectable`。 |
| N3 | `GlassCheckbox`（含 `GlassTileAction`） | 自绘 `SelectionCheck`（`WifiPhotoBrowser.kt:589-603`、`WifiPhotoViewer.kt:822-837`）、放大镜（`WifiPhotoBrowser.kt:522-541`） | 22dp 圆角方块（圆角≥6dp）：未选=Viewer 级玻璃（半透明 + 1dp 白边），选中=`accent` + 勾；外圈 48dp 点击区；放大镜同风格 22dp 玻璃方块（图标 15dp）。 |
| N4 | `GlassTextField` | `OutlinedTextField`（`DeviceInfoDialogs.kt:347`、`WifiRemoteScreen.kt:805`、`WifiManualJoin.kt:30,38`） | G2 内凹玻璃：圆角 18dp，`Body` 文字，label 用 `Caption`，聚焦 `accent` 边，错误 `danger`；支持 `readOnly`/`singleLine`/trailing 槽。 |
| N5 | `GlassTopBar` | `TopAppBar`/`CenterAlignedTopAppBar`（`SharedDevicesScreen.kt:31`、`APP/ui/device/DeviceDetailScreen.kt:138`、`WifiPhotoTopBar.kt:97`、`WifiRemoteScreen.kt:758`、`TroubleshootingScreen.kt:28`、`SharedPairingPreparationScreen.kt:61`） | 与 `GlassPageScaffold` 顶栏一致：左对齐 `Title`、可选副标题 `Caption`、导航/动作槽用 `GlassIconButton`；背景透明，或滚动时出现 Float 级玻璃（内容在其下滚动，取样可见）。 |
| N6 | `GlassPopover`/`GlassMenu` | 平台 `Popup`（`WifiPhotoTopBar.kt:191`） | 页内 overlay，锚点定位（右上），G3 玻璃，点击空白/返回关闭；淡入 + 位移；内含 N1/N7。 |
| N7 | `GlassSegmented` | 自绘分段（`WifiPhotoTopBar.kt:201-232`） | 胶囊容器（`textPrimary@.06`）+ 选中滑块（`accent` tint 玻璃）；每段 `Label`；高 ≥40dp。 |
| N8 | `GlassProgress`（圆/线） | 14 处原生进度条（§1） | 圆：2.5dp 描边，轨道 `textPrimary@.12`，进度 `accent`（Viewer 上用 `onPhoto`）；线：4dp 圆角胶囊；尺寸 16/24/36。 |
| N9 | `GlassSnackbar`（+ host） | `Toast.makeText`×5 | G3 圆角条，底部浮出，3s 自动消失，可带动作；经 `GlassOverlayHost`；a11y live region。 |
| N10 | `StatusBadge` | 设备详情右侧“断开”图标（`DeviceDetailScreen.kt:327-332`） | 只读：✓/点 + `Label` 文字 + tone 着色（success/warning/danger/neutral）；胶囊 G2 底。 |
| N11 | `StatusItem.iconTone`（改造 `StatusRow`） | `StatusRow.kt:126` 仅 tone≠Neutral 才着色 | 值 tone 与图标 tone 分离，默认图标 tone 取语义。 |
| N12 | `CapsuleButton` 玻璃变体（改造） | `CapsuleButton.kt:89-127` 不透明 | 新 role 或 `glass=true`：fill=`accent@.28~.40` + Haze 模糊 + 1dp 渐变高光边 + 极轻阴影；去掉 Large/Hero 的深色辉光（`:153-155`）；Viewer 级用 `onPhoto` 文字。 |
| N13 | `GlassTag`（改造 `InfoTag`） | `InfoTag(OnPhoto)` 的 `Black@.6`（`InfoTag.kt:31`） | 玻璃内：无底，仅 `Tag` 文字 + 1dp 高光描边；照片上：Viewer 级轻玻璃（不再纯黑）。 |
| N14 | `GlassFilmstrip` | `ViewerFilmstrip`（`WifiPhotoViewer.kt:841-880`） | 容器 `GlassFloatingBar(Viewer)`；单元 40dp 圆角 8dp；当前=`onPhoto` 2dp 环；其余 0.7 alpha；`Fit`。 |
| N15 | `GlassWarningCard` | M3 `Card(errorContainer)`（`SystemStateWarnings.kt:37,89`） | `GlassCard` + tone 左缘描边/淡 tint；内含 `Heading`+`Caption`+`CapsuleButton`。 |
| N16 | `GlassIconTile` | 设备卡 44dp 图标底（`SharedDeviceList.kt:315-340`） | 圆形 G2，图标按 tone 着色（paired=accent、未配对=danger、其它=neutral）。 |
| N17 | `RevealValueRow` | 蓝牙地址行（`DeviceInfoDialogs.kt:240`） | 默认遮罩，点按切换；`stateDescription` 播报显隐。 |
| N18 | `CapsuleDialog` 头部行（改造） | ✕ 绝对定位（`CapsuleDialog.kt:312-318`） | 头部 Row（标题/自定义头部 weight 1 + ✕），或内容顶部固定 48dp 预留。 |
| N19 | `AlphaType` 6 档 + 8 色角色 + 玻璃 token（改造 Type/Color/Glass） | §3 | 见 §3.4–3.6。 |
| N20 | `GlassBottomSheet`（页内，可选） | 预览页“详情”抽屉（D7） | A 档用；页内 overlay，拖拽/点击空白关闭。 |

---

## 5 建议的实施批次划分（尽量文件互不重叠）

> 同一“并行组”内批次的文件集合互不重叠。`strings.xml`（EN/ZH，`RES-SN/values*/strings.xml`、`RES-APP/values*/strings.xml`）被多批共用，**建议只由 B7 统一修改**（各批在提交说明里列 `TODO(strings)` 新增/删除项），或把各批新增 key 放到自有的新 `strings_<batch>.xml`（Compose Resources 同目录多文件是否支持 **待确认**）。

| 批次 | 内容 | 会碰到的文件（主要） | 依赖/并行 |
|---|---|---|---|
| **B0 地基**（串行，先做） | `AlphaType` 6 档 + 8 色角色 + 玻璃观感 token + 新组件 N1–N20 + 预览页 | `SN/ui/theme/{Type,Color,Glass,Dimens}.kt`（含删除 `Color.kt` 的 `RatingColors`）；`SN/ui/components/glass/*`（新增 + 改 `CapsuleButton`、`InfoTag`、`StatusRow`、`GlassSurface`、`CapsuleDialog`、`GlassIconButton`、`GlassBackground`）；`SN/ui/components/page/GlassPageScaffold.kt`；`SN/ui/components/ScrollbarLazyColumn.kt`（槽位）；`SN/ui/components/AdaptiveContent.kt`；`app/src/debug/java/com/saschl/cameragps/ui/preview/GlassComponentsPreview.kt`；`sharednew/src/commonTest/.../ui/theme/PaletteContrastTest.kt` | 其它批次依赖 B0；B0 独占 `components/glass/*` 与 `theme/*` |
| **B1 导航重排：帮助/日志入设置 + 设备清单整理** | 设置页加“帮助/日志”入口；设备清单去“?”“日志”“需要帮助？”；添加相机对齐；顶栏换 `GlassTopBar`；警告卡换玻璃 | `APP/MainActivity.kt`；`APP/ui/settings/SettingsScreen.kt`；`APP/ui/device/{DevicesScreen,CameraDeviceManager,ScanForDevicesMenu}.kt`；`APP/ui/{SystemStateWarnings,AssociatedDevicesList}.kt`；`SN/ui/device/SharedDevicesScreen.kt`；`SN/ui/devicelist/{DeviceListLayout,SharedDeviceList}.kt`；`app/src/androidTest/java/com/saschl/cameragps/{DeviceListLayoutTest,AdaptiveLayoutTest}.kt` | B0 后；与 B2–B6 文件不重叠，可并行 |
| **B2 设备详情页 + Ⓘ 弹窗** | 状态徽章、图标着色、整合布局（方案 1/2）、Hero 缩小、地址隐藏、✓ 已启用、✕ 头部行适配、Switch/TextField 换玻璃 | `SN/ui/device/{DeviceDetailScreen,DeviceInfoDialogs,DevicePagePresentation,DeviceDetailLogic}.kt`；`APP/ui/device/DeviceDetailScreen.kt`；对应 commonTest | B0 后；与 B1 同目录不同文件，不重叠 |
| **B3 Wi-Fi 日期/网格/视图菜单 + 移除保护（UI 层）** | 去相对日期；网格 Fit；锚点重排（大小右上、无单位）；视图菜单换 `GlassPopover`+`GlassSwitch`+`GlassSegmented`；悬浮下载按钮去外圈；移除 `showProtection` 与保护标签 | `SN/ui/remote/{WifiPhotoDates,WifiPhotoBrowser,WifiPhotoTopBar,WifiBrowserModel}.kt`；`APP/utils/PreferencesManager.kt`（仅 `KEY_MW_WIFI_GRID_SHOW_PROTECTION` 相关 `:268,277,284`）；`APP/ui/device/AndroidWifiRemoteScreen.kt`（`:99,123-126`）；`WifiBrowserModelTest.kt`；（若改标签口径）`WifiFormatPolicyTest.kt` | B0 后；与 B4 同改 `PreferencesManager.kt` → 分段或同一 agent |
| **B4 预览页** | 下载按钮玻璃、信息条去二层底、复选框/胶片条玻璃化、A 档详情入口、移除预览保护标签、三个设置开关语义调整 | `SN/ui/remote/{WifiPhotoViewer,WifiPhotoViewerModel,ViewerHistogram}.kt`；`APP/ui/settings/PhotoTransferSettingsCard.kt`；`APP/utils/PreferencesManager.kt`（viewer 键 `:231-256`）；`WifiPhotoViewerModelTest.kt` | B0 后；与 B3 在 `PreferencesManager.kt` 同文件 → 串行或同 agent |
| **B5 数据层移除保护** | 删 `isProtected`（`PhotoMetadata`、`WifiCameraPhoto`、`SonyImageInfo`、ObjectInfo 解析、`WifiCaptureDetails`）；注释更新；测试 | `SN/remote/wifi/{PhotoMetadata,WifiRemoteState,SonyImageTransfer,SonyContentCatalog}.kt`；`SN/ui/remote/WifiRemoteScreen.kt`（`:229` 一行）；对应 commonTest；`docs/wifi-architecture.md:336` | **必须在 B3、B4 之后**（它们先停止引用） |
| **B6 其余原生控件清扫** | 权限页、欢迎页、排障页（整页）与指南卡、Wi-Fi 连接控件（含 `WifiManualJoin`）、配对准备页（删除/重做）、`PairingDialog` 进度、`Toast`→`GlassSnackbar`、设置页开关/单选（若 B0 未全局替换） | `APP/ui/PermissionBox.kt`；`APP/ui/TroubleshootingScreen.kt`；`SN/ui/welcome/SharedWelcomeScreen.kt`；`SN/ui/help/TroubleshootingGuideScreen.kt`；`SN/ui/pairing/*`；`APP/ui/pairing/PairingDialog.kt`；`SN/ui/remote/{WifiManualJoin,WifiRemoteExperimentalNotice,WifiConnectionOverlay}.kt`；`SN/ui/remote/WifiRemoteScreen.kt`（连接控件段 `:738-856`，**与 B5 的 `:229` 同文件不同段，需顺序合并**）；`APP/ui/settings/{AppControlsCard,BatteryOptimizationCard,EventSoundsSettingsCard,LogLevelSettingsCard}.kt`；`APP/ui/about/*`；`SN/ui/settings/{SharedSettingsCard,SharedRadioOption,SharedLanguageSettingsCard}.kt` | B0 后；除 `WifiRemoteScreen.kt` 外与 B1–B4 不重叠 |
| **B7 文案与文档收尾（串行最后）** | 字符串增删（EN/ZH）、`AGENTS.md`（帮助入口约定）、`docs/design/README.md`、`docs/todo.md`、`docs/icon-sources.md` 同步、清理 `mw_lock_24px`、无引用字符串（如 `device_info_address`）、跨模块重复 key | `RES-SN/values{,-zh-rCN}/strings.xml`；`RES-APP/values{,-zh-rCN}/strings.xml`；`AGENTS.md`；`docs/todo.md`；`docs/design/README.md`；`docs/icon-sources.md`；`sharednew/src/commonMain/composeResources/drawable/mw_lock_24px.xml` | 最后；依赖 B1–B6 |

**文件所有权冲突提示**：`PreferencesManager.kt`（B3/B4）、`WifiRemoteScreen.kt`（B5/B6）、`strings.xml`（全部）、`SN/ui/components/glass/*` 与 `SN/ui/theme/*`（B0 独占，其它批次不改）。每批提交后跑 `AGENTS.md` 的验证命令（`./gradlew --no-daemon :sharednew:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`）。

---

## 6 风险与待用户决策点

### 6.1 风险
1. **Haze 行为未验证**：`hazeSource` zIndex 语义、“取样源包含效果自身祖先”“Popup 跨窗口取样”“`noiseFactor` 性能”——Haze 2.1.0 源码不在本地（仅有 aar），**需真机/最小用例验证**后再定 B0 方案（§2-9）。
2. **模糊 + 大面积玻璃的性能**：老机型上 20–32dp blur 多层叠加会掉帧；网格不要给每个缩略图格加 blur（现在也不是取样源）。
3. **移除保护**：Sony 保护位真机映射本来就“待确认”（`SN/remote/wifi/PhotoMetadata.kt:94-98` 注释）；删除即丢掉该能力。该数据不落盘（元数据缓存为内存 LRU，见 `docs/wifi-architecture.md` 元数据段），无迁移问题；偏好键 `mw_wifi_grid_show_protection` 成孤儿键（无害）。
4. **帮助入口约定冲突**：`AGENTS.md`“应用内通用帮助只在设备清单提供入口”必须同步修改；排障页入口也在设备清单底部。
5. **字号收敛的视觉回归**：`Body 15` 比现 `dialogBody 13.5/bodyMedium 14` 更大，弹窗/卡片会变高；需在 318dp 弹窗宽度与 200% 字体缩放下核对折行。
6. **网格 Fit**：留白会让竖/横图在方格里出现空边，锚点仍在方框角；若期待“框随图”需换瀑布流（改动面大）。
7. **`SharedPairingPreparationScreen` 疑似死代码**（已决：已删除，0.3.0；下述为审计当时状态）：仅 `androidTest` 引用；删除需同步改 `AdaptiveLayoutTest.kt:204`；`PairingPreparationState` 有 commonTest 在用（`sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/ui/pairing/PairingPreparationStateTest.kt`）。
8. **新增组件一律页内**：任何窗口式组件都会重现“无法取样/无法玻璃”。
9. **`GlassPageScaffold` 嵌套 `GlassBackground`**：若 B0 改成“只在根部一个 `GlassBackground`”，二级页对话框 host 行为会变，需回归 `LogLevel/EventSounds/Language/Logs` 的对话框。
10. **通知/Toast**：系统通知与（Android 13 以下）Toast 无法玻璃化；`GlassSnackbar` 仅覆盖应用内提示。

### 6.2 需用户决策
- **D1** 玻璃优先级：先做“观感 token + 新组件”（不动背景，§2-9(c) 的 1–7），还是允许给背景加低对比纹理/彩色 orb（备选 8）？
- **D2** 文字 6 档/颜色 8 角色是否认可（尤其 `Body 15`、`Heading 18`、`Caption 13`、是否需要 `textMuted`）？
- **D3** 帮助入口：设置页放“帮助”“日志”两行；设备清单底部“需要帮助？”**删除**还是保留为排障快捷？（`AGENTS.md` 同步）
- **D4** 网格 Fit：方格内留白（推荐，改动小）还是瀑布流？留白底色用 `onSurface@.08` 还是玻璃？
- **D5** 视图选项菜单保留哪些项（尺寸/格式/大小，去保护）？
- **D6** 大小标签口径：格内显示“将下载的大小”（随策略）还是“全部文件大小”？按钮/预览是否也统一 MB（现 `formatByteSize` 自适应 KB/MB/GB）？
- **D7** 预览详情入口：A 档“点击信息条 → 底部抽屉”还是显式 ⓘ？三个开关是否改为“默认展开哪些块”（建议默认开参数+镜头、直方图关）？B/C 面板是否常驻？
- **D8** 设备详情页整合布局：方案 1 还是方案 2？“断开蓝牙”下沉到 Ⓘ 弹窗是否可接受？
- **D9** 日期列表标题：保留原始 `yyyy-MM-dd`，还是本地化“11月10日”？
- **D10** `SharedPairingPreparationScreen`（疑似死代码）直接删除？——**已决：已删除（0.3.0）**，连同 `PairingPreparationState`/测试与 8 个 `pairing_preparation_*` 字符串。

### 6.3 任务 C：多语言与资源

**整体健康度**
- 键数：`sharednew` 基线 575 vs 简中 573（简中缺 `app_name`、`app_name_ui`，疑似有意只在基线，**待确认**）；`app` 7 vs 7 一致；EN/ZH 内部均无重复键。
- 跨模块重复键（`app` 与 `sharednew` 都定义）：`app_standby_content`、`app_standby_title`、`disconnect_notification_channel_name`、`foreground_service_notification`、`notification_channel_name`（两个资源系统各一份，易漂移）。
- 格式瑕疵：`RES-SN/values-zh-rCN/strings.xml:303` 一行写了两个 `<string>`（`wifi_remote_connected` 与 `wifi_remote_fullscreen`），不影响功能，批量脚本易漏。
- 无 Kotlin 引用的键：`device_info_address`（`RES-SN/values/strings.xml:222`，只有生成的资源访问器引用；实际用 `m2_info_address`）。

**“移除保护/评分”波及**（EN `values/strings.xml` / ZH `values-zh-rCN/strings.xml`）

| key | EN | ZH | 引用 |
|---|---|---|---|
| `mw_tag_protection` | `:591` | `:562` | `WifiPhotoTopBar.kt:240` |
| `mw_tag_protection_unavailable` | `:592` | `:563` | `WifiPhotoTopBar.kt:241` |
| `mw_protected` | `:593` | `:564` | `WifiPhotoBrowser.kt:499`、`WifiPhotoViewer.kt:475` |
| 资源 `drawable/mw_lock_24px.xml` | — | — | `WifiPhotoBrowser.kt:75,504`、`WifiPhotoViewer.kt:84,476`；`docs/icon-sources.md:52` 许可登记需同步删 |
- 评分：无字符串、无 UI；仅 `RatingColors`（`Color.kt:226-235`）死代码。

**“帮助与日志移入设置”波及**

| key | 现状 | 动作 |
|---|---|---|
| `help_menu_item`（EN `:208`/ZH `:86`） | 仅 `DevicesScreen.kt:101` 作 `contentDescription` | 改作设置页行标题（或新增更合适的 key） |
| `view_logs`（EN `:41`/ZH `:20`）、`logs_title`（EN `:42`；`SharedLogViewerScreen.kt:99`） | `DevicesScreen.kt:106` / 日志页标题 | 同上；设置行副标题需新增 key（EN+ZH） |
| `device_list_need_help`（EN `:4`/ZH `:249`） | `DeviceListLayout.kt:53` + 两个 androidTest | 若删除底部链接则删 key，并改 `DeviceListLayoutTest.kt:16,43,77`、`AdaptiveLayoutTest.kt:36,71` |
| 设置行文案 | — | **新增**：帮助行标题/描述、日志行标题/描述（EN+ZH 两处）；“关于”入口说明 `m9_help_about_entry_desc`（EN `:455`）已存在 |

**“去相对日期”波及**

| key | EN | ZH | 动作 |
|---|---|---|---|
| `mw_dates_today` | `:576` | `:547` | 删除 |
| `mw_dates_yesterday` | `:577` | `:548` | 删除 |
| `mw_weekday_1..7` | `:578-584` | `:549-555` | **保留**（周几） |

**设备 Ⓘ 弹窗/详情页相关（本轮会改的文案）**
- `device_info_setting_enabled`（EN `:431` “On (the app keeps it on)”/ZH `:402` “已启用（应用会保持开启）”）：改为短文案（ZH“已启用”，EN “On”）或新增短 key；长句并入 `device_info_keep_active_hint`（EN `:428`）；`device_info_setting_off/unsupported/unknown`（EN `:432-434`）保持。
- 新增：地址遮罩的 a11y 文案（“显示/隐藏蓝牙地址”）。
- “已连接”徽章：可复用 `connected`（ZH `:164`）或 `m2_bt_connected`（ZH `:475`）。

**其它**：新增文案只写 `values/` 与 `values-zh-rCN/`（`AGENTS.md`“语言资源纪律”）；删减 key 后清理 `sharednew/build` 的 compose 资源生成目录（会残留旧 key 访问器，如已见到的 `device_info_address` 生成项）。

---

## 附录 A：保护 / 评分 全部引用点（移除清单）

**偏好 / 选项**
- `APP/utils/PreferencesManager.kt:268`（读 `KEY_MW_WIFI_GRID_SHOW_PROTECTION`，默认 false）、`:277`（写）、`:284`（常量 `mw_wifi_grid_show_protection`）。
- `APP/ui/device/AndroidWifiRemoteScreen.kt:99,123-126`（`WifiGridOptions` 读写接线；保护字段随 `WifiGridOptions` 变更）。
- `SN/ui/remote/WifiBrowserModel.kt:98`（注释）、`:104` `val showProtection: Boolean = false`（`WifiGridOptions`）、`:115-123` `WifiCameraCapture.isProtected: Boolean?`、`:125-127` `protectionKnown(captures)`、`:142-143` `WifiCaptureDetails.isProtected`。

**UI**
- `SN/ui/remote/WifiPhotoTopBar.kt:49-50`（导入）、`:151-153`（`protectionAvailable`）、`:179-180,185`（文档/参数）、`:239-244`（保护 `OptionSwitch` + “此相机不提供”提示）。
- `SN/ui/remote/WifiPhotoBrowser.kt:75-76`（导入 `mw_lock_24px`/`mw_protected`）、`:434`（注释）、`:497-506`（右上保护 `InfoTag`，条件 `options.showProtection && capture.isProtected==true`）。
- `SN/ui/remote/WifiPhotoViewer.kt:84-85`（导入）、`:274`（`isProtected = info?.isProtected == true`）、`:338`（标签字段）、`:469-476`（状态标签：保护 + “已选 N 张”，`:472` 早退条件含 `!parts.isProtected`）。
- `SN/ui/remote/WifiPhotoViewerModel.kt:92`（`ViewerInfoStrip.isProtected`）、`:143`（`capture.isProtected == true || metadata?.isProtected == true`）。
- `SN/ui/remote/WifiRemoteScreen.kt:229`（`captureDetails` 里 `isProtected = capture.isProtected`）。

**数据层**
- `SN/remote/wifi/PhotoMetadata.kt:94-98`（`val isProtected: Boolean? = null`）、`:118`（透传）。
- `SN/remote/wifi/WifiRemoteState.kt:72-73`（`WifiCameraPhoto.isProtected`）。
- `SN/remote/wifi/SonyImageTransfer.kt:43`（`SonyImageInfo.isProtected`）、`:83-90`（ObjectInfo 保护位：`val protection = number(6, 2).toInt()` → `0→false;1→true;else→null`）、`:192`（`info.width, info.height, info.isProtected` 构造 `PhotoMetadata`；width/height 保留）。
- `SN/remote/wifi/SonyContentCatalog.kt:57`（`take(8) // Rating, protection flag.`：v4 目录跳过评分/保护字节，只改注释，**不涉及解析**）。

**评分**
- 仅 `SN/ui/theme/Color.kt:226-235`（`RatingColors`，全仓无引用）；`docs/design/history/ui-status-enhancement/SPEC.md:59`、`README.md` 为历史设计，不改。

**字符串 / 资源 / 文档**：见 §6.3；`docs/icon-sources.md:52`；`docs/wifi-architecture.md:336`（“ObjectInfo resolution/protection”措辞需改）；`docs/todo.md:59`（EXIF/RSSI 冒烟项是否提到保护 **待确认**）。

**测试**
- `sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/remote/wifi/PhotoMetadataTypesTest.kt:24,27`
- `sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyImageTransferTest.kt:636-660`（`objectInfoExposesProtectionAndPixelSizeWhenTheCameraProvidesThem`，保留像素尺寸断言，删保护断言）
- `sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiBrowserModelTest.kt:84,91-108`（`showProtection`、`protectionIsKnownOnlyWhenTheCatalogReportsIt`）
- `sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiPhotoViewerModelTest.kt:31,146-151`（`protectionComesFromTheCatalogOrTheMetadata`）

## 附录 B：窗口式组件与系统样式一览（Haze 无法取样/无法玻璃）
- `Popup`：`SN/ui/remote/WifiPhotoTopBar.kt:191`
- `Dialog`（回退）：`SN/ui/components/glass/CapsuleDialog.kt:222`
- `PlainTooltip`：`SN/ui/components/glass/GlassIconButton.kt`（`TooltipBox`）
- `Toast.makeText`：`APP/ui/about/AboutComponents.kt:110`、`APP/ui/settings/AppControlsCard.kt:83`、`APP/ui/settings/BatteryOptimizationCard.kt:144,193,221`
- 系统通知：`APP/notification/NotificationsHelper.kt:92,127,154`

## 附录 C：本报告未能确认/未覆盖
- 所有“视觉是否错位/重叠/模糊是否生效”的判断（§2-1、§2-6a、§2-9）需截图或真机；代码只给出布局算术与取样配对。
- Haze 2.1.0 的 API/zIndex 语义；Compose Resources 同目录多 `strings*.xml` 文件的支持。
- 对比度数值为按 `Contrast.kt` 公式的纸面估算（背景取卡片 fill 叠 `background`，未计光晕/照片），以 `PaletteContrastTest.kt` 与真机为准。
- 未逐一阅读 `app/src/androidTest` 与 `sharednew/src/androidDeviceTest` 中对本轮改动的断言（仅列出 `DeviceListLayoutTest`、`AdaptiveLayoutTest`）。
