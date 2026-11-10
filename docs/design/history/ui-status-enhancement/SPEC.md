# 界面改版 v2 · 开发规格（待用户确认设计及 Q1–Q16 后使用）

详见 `docs/design/history/ui-status-enhancement-v2.pdf`（历史版本；界面以 v3 `docs/design/ui-v3/` 为准）。v1 的数据层规格见文末（仍有效，仅 UI 承载位置按 v2 调整）。

## 0. 主题（M0）
- `sharednew/.../ui/theme/Theme.kt`：删 `dynamicColor` 参数与注释分支（:36, :43-46）；iOS 调用 `StoreScreenshotViewController.kt:28` 同步；亮/暗各给**完整显式** `lightColorScheme/darkColorScheme`，不用动态色。删 `Color.kt` Purple/PurpleGrey/Pink 常量与笔误色 `0xADD8E6FF`。
- 主色：亮 primary `#0B57D0`/onPrimary `#FFFFFF`/primaryContainer `#D3E3FD`/onPrimaryContainer `#041E49`；暗 primary `#A8C7FA`/onPrimary `#062E6F`/primaryContainer `#0842A0`/onPrimaryContainer `#D3E3FD`。surface/容器阶梯、error、inverse 等**全表见 PDF 第 13 页**（亮 background `#F9FAFE`，暗 `#101418`）。
- 扩展语义色（CompositionLocal `CameraGpsExtendedColors`）：success 亮 `#1B6E3A`/暗 `#7DDB93`；warning 亮 `#F9A825`、warningText 亮 `#7A5200`/暗 `#FFC857`；successContainer/warningContainer 见 PDF。替换硬编码：`DeviceDetailScreen.kt:245-246`、`TransmissionDot.kt:36,62`。
- 评分色（亮暗通用）：1 `#90A4AE` 2 `#4FC3F7` 3 `#66BB6A` 4 `#FFB300` 5 `#FF7043`，数字必显示；预览页恒黑背景，`PhotoViewerColors` primary 用暗色 primary。

## 1. 按钮规则（M1）
- 纯图标：导航（←/✕）、顶栏操作（≤2 个：刷新/视图选项/全选/Ⓘ）、就地编辑（名称旁 ✎）；均带 contentDescription。
- 文字按钮：页面主操作（设备页「连接」、预览页「下载」、选择态「下载 N 张」）、危险操作（「移除相机」描边红，仅 Ⓘ 危险区，必二次确认）、对话框决定、叠层「取消/重试/关闭/帮助」。
- 入口：整块卡片（Wi-Fi 照片传输）。无任何独立「断开」按钮；子页顶栏不放 link_off。
- 标题统一左对齐。

## 2. 设备页（M2）
- 自上而下：状态卡（相机图标+名称+连接状态点；**未连接时**右侧填充「连接」按钮；下方 3 格：校时 ✓/⚠/转圈、信号 4 格、电量图示+%，仅图示/数字+一字标签）→ Wi-Fi 照片传输卡（未连接置灰，副文「连接相机后可用」）→ GPS 卡（已传输/已写入/最近写入 + 状态胶囊：传输中绿 / 延迟琥珀 / 已停止灰）。底部不再放固定按钮，已连接时无断开按钮。
- 顶栏：← + 左对齐标题 + Ⓘ。**返回 = 断开蓝牙**（Q1/Q3：建议“软断开”，不持久化 disabled、不停 presence 观察）；进入设备页且未连接 → 自动连接（Q5）。

## 3. Ⓘ（M2）
- 内容：右上 ✕（取代 OK）；名称 + ✎（→ 重命名对话框，说明小字删）；蓝牙地址；保持活跃（Switch，无说明）；自动校时/自动区域调整（✓ 绿=已开启；「—」灰=不支持/未知；⚠ 琥珀=关闭，点按重试）；蓝牙 MTU；固件、序列号（仅 Wi-Fi 会话读到后出现，序列号遮罩点按 10 s 显示）；分隔线 + 底部整宽描边红「移除相机」（图标+文字）→ 现有 ConfirmDelete。
- 删除：三行说明、「已启用（应用会保持开启）」、「帮助」、OK、底部一排 TextButton；GPS 明细/重置计数/实时状态不进 Ⓘ。

## 4. 连接叠层（M3）
- 删除 `WifiRemoteScreen.kt` 照片库未打开时的整页 pre-browser `Scaffold`（含「返回」TextButton 与「断开连接」OutlinedButton）；由设备页宿主按 `WifiRemoteState.phase` 渲染 `ConnectOverlay`（蓝牙自动连接同组件）。
- 卡片 286×252 dp 固定尺寸（五帧不跳动）、圆角 28、surfaceContainerHigh、scrim 亮 38%/暗 60%，不因点空白关闭；返回键=取消。圆环 60 dp/线宽 4/1.2 s 每圈；入场 200 ms/出场 150 ms；减少动态时圆环改静态不确定态；`liveRegion=Polite`。
- 帧：PreparingCamera「正在启动相机 Wi-Fi」→ AwaitingNetworkApproval「请允许连接相机 Wi-Fi / 请在系统弹窗中点「连接」」→ JoiningNetwork「正在连接相机」→ OpeningSession+browseDates「正在读取照片目录」（无进度数字）→ Ready ✓ 0.5 s 后推入日期列表。仅「取消」：取消本次尝试，回设备页，蓝牙不变。失败：红图标+一行原因+取消/重试（+ 帮助，Q11）；重试回帧 ①。
- 文案仅 `values/` + `values-zh-rCN/`。

## 5. 日期列表 / 网格（M4）
- 日期列表：顶栏仅刷新；信息条（卡容量/剩余/固件，v1 数据）只在此页。网格页：顶栏 刷新 + 视图选项(tune)；标题日期 + 副标题「N 张」；右侧快速滚动条；无页码栏。
- 视图选项面板（锚定图标下方，实时生效，偏好全局 DataStore 持久化）：大小 小/中/大 = `GridCells.Adaptive(minSize)` 80/104/150 dp（360 dp 屏 ≈4/3/2 列，小=现状）；开关：文件格式（默认开）、文件大小（默认关，「小」档隐藏并置灰）、保护与评分（默认关；相机不提供时置灰+「此相机不提供」）。顶栏星标开关删除。
- 选择态：顶栏 ✕ + 全选本日；批量下载为底部整宽主按钮「下载 N 张」，顶栏下载图标删除。
- **取消分页（待 Q6）**：删 `WifiPhotoPagerBar`/`WifiPhotoPager.kt`/`photoPageCount…`/跳页对话框与页码字符串；日期内一次取整日（`CatalogSnapshot` 已全量在内存）；`PAGE_SIZE`(SonyImageTransfer.kt:456) 仅留协议层；`RangeSelectionSession.PageIdentity` 去 offset；预览 Pager 直接覆盖整日，删边界占位页/`changingPage`/跨页 `browseDate`；缩略图继续按可见窗口加载，新增 LRU 上限（建议 ≈300 张）；测试 `WifiCameraCaptureTest.kt:70` 等同步改，补整日连续/选择/拖选/预览用例。非日期分组的平铺路径建议同改。

## 6. 预览页（M5）
- 顶栏：← ；两行：第一行 文件名 + 文件大小（同行，次要色）；第二行 时间（无「已拍摄」）；右侧 位置「6/227」+ 选择框。
- 底部仅一个整宽按钮，文案即状态：「下载」/「已保存」(置灰)/「无 JPEG」/「文件过大」；去掉 ‹ ›、格式与大小文案、按钮下原因行、「已将…保存到手机相册」。
- 下载中：画面中央叠层（进度环+百分比+「取消」；Converting 为不定态），按钮置灰；失败叠层「保存失败 / 关闭 / 重试」；下载期间锁定滑动与选择框，返回=取消（Q12）。
- **滑动已确认支持**（`WifiPhotoViewer.kt:203-206` HorizontalPager `userScrollEnabled = !changingPage`），保留为唯一翻页手势。

## 7. 导航与断开语义（Q1–Q4）
- 解读：不单独设计断开按钮；仅「设备页左上返回」= 真断开（蓝牙）；Wi-Fi 子通道在日期列表返回设备页时结束（沿用现有行为）。若用户仍要一个按钮（Q2），唯一位置为设备页顶栏 Ⓘ 旁。

## 里程碑与验证
M0 主题 → M1 组件/规则 → M2 设备页+Ⓘ（含 v1 BLE 侧状态）→ M3 Wi-Fi 叠层 → M4 日期网格（含取消分页）→ M5 预览页 → M6 v1 PTP 数据（固件/序列号/卡容量/电量/保护评分，先实机抓包）。验证命令见 AGENTS.md；每个里程碑补单测与 en/zh-rCN 文案；不新增其它语言目录。

---
# v1 数据层规格（仍有效；UI 承载位置以 v2 为准）

## 新增状态（均为内存，不落盘）
- `CameraSession.timeSync: TimeSyncStatus` — NotStarted / Syncing / Applied(at) / Rejected(reason) / Unconfirmed / WriteFailed / Unsupported。写入点：`CameraTimeSyncController.sync()`（CameraTimeSyncController.kt:154-215，现仅记日志）。仅 CC0E applied=1 为已校正。
- `CameraSession.rssi: Int?` + `rssiAt`。新增 `BlePeripheralTransport.readRssi()` + `BleTransportEvent.RssiRead`；Android `readRemoteRssi/onReadRemoteRssi`，iOS `readRSSI`。仅页面可见且已连接时首读 1 次，之后每 5 s。
- `CameraSession.gpsStats{sent, written, failed, bytes, lastWriteAt}`：`LocationTransmissionManager.sendToDevice`（入队成功→sent+1）与 `BleSessionCoordinator.onCharacteristicWrite` 位置特征分支（BleSessionCoordinator.kt:116-118，Success→written+1/bytes+=len/lastWriteAt；Failure/Timeout/Cancelled/NotInitiated→failed+1）。连接时清零，断开保留末值灰显（v2 不再提供「重置计数」）。
- `BatteryCache{percent, obtainedAt, cameraId}`（应用级）：Wi-Fi 会话 Ready 后读 1 次（标准 0x5001 / Sony 0xD218，待实机核实）。≤30 min 正常；30 min~24 h 灰色；>24 h/换机「—」。
- `WifiRemoteState.deviceInfo{firmware, serial}`：扩展 `PtpDeviceInfoParser`（PtpDeviceInfo.kt:20-53）解析 DeviceVersion/SerialNumber；仅内存、不入日志。
- `WifiRemoteState.storage{total, free}`：GetStorageInfo 0x1005（卡槽 1），目录首屏返回后再发。
- `WifiCameraPhoto.protected: Boolean?` / `rating: Int?`（null=未知）：v4 解析 `SonyContentCatalog.kt:57 take(8)`（布局待实机核实）；旧路径读 ObjectInfo.ProtectionStatus（偏移 6）/PropList 0xDC03。
- GPS 新鲜度：≤15 s 绿「传输中」，15–60 s 琥珀「写入延迟」，>60 s 红；未连接灰「已停止」。RSSI 分档：≥−60 强 / −60~−75 良好 / −75~−85 一般 / <−85 弱。

## 约束
- 每项独立失败，不阻塞 GPS/浏览/下载；信息类 PTP 请求排在目录首屏之后。
- 序列号默认遮罩，点按 10 s 自动重遮，不入日志/缓存。
- 锁/评分只读，不新增点击目标。
