# 待办（TODO）

> **纪律（必须遵守）**
> 1. 本文件**只放当前待完成**的事项。已完成、已放弃、已过期的条目**当次提交内立即删除**，不打勾保留、不挪到“已完成”区。
> 2. 完成历史靠 git 记录（conventional commit 信息 + tag），不在这里留痕。
> 3. 每条必须写清：**要做什么 / 验收标准 / 依据或位置**。写不清楚的先别入表。
> 4. 新发现的事项当天加入；做完一项，在**同一个提交**里删掉对应条目。
> 5. 按优先级排序（P1 > P2 > P3），同级按紧迫程度。每次编辑后检查是否有已过期条目。
> 6. 流程与背景说明不放这里（放 README 或 docs/ 下对应文档）；这里只放“还没做的事”。

最后整理：2026-11-10（基线 v0.1.4，versionCode 见 gradle.properties）

## P1

### 1. 真机冒烟：11 项 UI/UX 改造（0673adf..451c31f）
- 做什么：装新版后验证：单机直达、设备页简化/底部照片传输入口/删除二次确认、直达连接、图标顶栏与方形角标、预览页直接下载与时间副标题、退出应用（划走任务/根页返回）只结束 Wi-Fi，BLE/GPS 与有连接需求的前台服务保持、缩略图分批渐进显示（`adb logcat -s AlphaThumb` 看 batch 行）。
- 验收：逐项无异常；退出/后台不得主动断 BLE，设备页用户主动断开才断 BLE；无下载退后台即结束 Wi-Fi，有用户单张/批量下载则保留至结束。重开应用不自动重建已结束的 Wi-Fi，需用户再次启动照片传输。
- 依据：sonnet 五提交（ad8626b/8f542f6/acea142/b7226b2/451c31f）自报需真机验证清单。

### 9. 真机冒烟：第三轮 UI/UX（ac52215..E 提交）
- 做什么：装新版后验证：①打开相册先显示"正在读取照片目录"，随后日期列表（按日倒序、未知日期最后，张数按 capture 计），进入某日显示该日整日网格（0.2.0 起日期内不分页），Back：网格→日期→退出；切日/返回日期不清选择、不丢"已选 N 张（含其它日期）"、不清已完成汇总；②缩略图只加载可见+前后余量（`adb logcat -s AlphaThumb` 看 batch 行，快速滑动不应整页扫描，换日/刷新/断开立即停）；③网格长按拖动范围选取（长按格子变选中/取消、拖过范围批量、反向拖回恢复、边缘低速自动滚动、checkbox/放大镜仍独立）；④角标 JPG/RAW/J&R/HEIF/H&R 只按文件格式；⑤JPG-only：RAW-only/HEIF-only 跳过并汇总"无 JPEG"，全跳过显示"下载 0/跳过 N"且不发起下载；Original：单张 JPG+RAW 一次下两个无格式选择器；⑥设备页仅 Ⓘ：对话框含重命名/删除二次确认/保持活跃/自动时间区域只读/MTU 与断开连接（断开已并入 Ⓘ 弹窗，连接按钮只有 连接/连接中…）；断开后相机真实 BLE 断开（相机屏幕连接图标熄灭）、再开设备页仍保持断开、CDM 重新出现/重启不自动重连，点连接才恢复；多相机只断目标；Wi-Fi 会话中断开先关 Wi-Fi；⑦"保持活跃"开关：关（默认）时相机离开范围且无连接后服务应自行停止，开则保活；退出应用（划走/根页返回）不再断 BLE/GPS，仅立即取消下载并结束 Wi-Fi；Home/锁屏/切应用有用户下载时保留 Wi-Fi 至下载结束，无下载立即结束。
- 验收：逐项无异常；特别核对 ⑥ 用户主动断开后 CDM 重新出现不重连、多设备只断目标；⑦ 退出/后台仍继续 GPS 写入且前台服务不被 UI 退出停止，离开范围后的 presence 退休规则不变。
- 依据：本轮 5 提交自报需真机验证（日期/视口/拖选/planner/Info 连接状态机均含无法在 JVM 验证的 Android 传输与手势部分）。
- 子项（权限请求改造，fix(permissions)）：Android 12（API 31/32）全新安装，首次授权定位应一次弹出 FINE+COARSE 同请求（系统显示"精确/大致位置"二选一）；选"精确位置"后走后台定位分步授权（先前台全部通过再弹 ACCESS_BACKGROUND_LOCATION），选"仅大致位置"不放行、仍提示需要精确位置。API 33+ 同步确认 FINE+COARSE+通知一次请求。依据：sol 审阅 03dc59ae（Android 12 要求 FINE 与 COARSE 同请求，`PermissionBox.kt` `foregroundRuntimePermissions`）。

- 子项（M1 后台/退出常驻）：真机分别在单张、跨文件批量下载中按 Home、锁屏、切换应用：确认 Wi-Fi 网络请求与下载不断，最后一项成功/失败/用户取消后仍在后台则释放 Wi-Fi（不留半文件，已提交文件保留），期间回前台则完成后继续保留会话。无下载、只有缩略图/大图预览时退后台立即释放；返回/旋转/活动重建不自动重连。下载中划卡、根页 Back 均立即取消并释放 Wi-Fi，但多台相机 BLE/GPS 与 FGS 保持；设备页主动断开仍仅断目标且保留既有持久化意图。验收还需覆盖后台超过 30 分钟的超时日志/清理、相机或网络丢失、OEM 电池策略。依据：`BackgroundWifiPolicy`、`WifiRemoteController` 的后台保留与 `AppExitCoordinator` 的 Wi-Fi-only 退出；宿主测试不能验证 Android 锁屏调度/系统网络许可窗口。

### 16. 真机冒烟：0.2.0 Wi-Fi 屏（连接叠层 / 日期列表 / 整日网格 / 选择与悬浮下载）
- 做什么：装新版后验证：①设备页点“照片传输”→ 全屏叠层 4 段环依次走 启动相机 Wi-Fi / 允许连接 / 连接相机 / 读取照片目录，卡片尺寸与按钮位置全程不跳变，取消即结束会话回设备页；成功短暂绿环后自动进日期列表；失败红色图标无底色、无帮助/重试，轻触/返回/5 秒自动回设备页；拒绝附近设备权限时出现“去授权”且不自动关闭；②日期列表：倒序、未知日期最后、副标题只有星期（无今天/昨天）、未知日期无副标题、张数；刷新图标读取中转圈；返回=结束 Wi-Fi 回设备页（蓝牙不断）；横屏两列、近方形限宽；③整日网格：无分页栏，长日（>60 张）一次连续滚动，每格为固定正方形深色近黑框（主题 token，亮暗主题一致），照片按原比例完整缩放居中（**不裁剪**：相机 160×120 缩略图可能自带黑边——此为推断、未经真机确认；黑边与近黑框融为一体，无需核对无黑边；前 3 张缩略图的 AlphaThumb 日志含解码后宽高与近黑边缘行列数，供真机确认黑边是否在相机缩略图像素里），四角：左上放大镜、右上文件大小（无单位 MB：<10 一位小数、≥10 取整，均从原始字节只做一次 half-up 舍入，<0.1 显示“<0.1”；JPG-only 为 JPG 大小、Original 为各文件之和，RAW-only 在 JPG-only 下显示 RAW 大小并带警示图标，但该 RAW 已被手动保存时显示“已保存”；候选文件全部已保存才显示“已保存”，存在超限且无可下载文件时显示“超过大小上限，将跳过”（超限优先，与文件顺序无关），均不显示大小）、左下格式、右下复选框，放大镜/复选框不触发点格子，`adb logcat -s AlphaThumb` 只见可见+两行余量的 batch，快速滑动不扫整日、静置不全量下载；视图选项（grid_view 图标）小/中/大=4/3/2 列，菜单为玻璃弹层+玻璃开关，仅 大小/文件格式/文件大小 三项，默认关、重启后保持；从 0.2.0 升级（存过保护开关）不崩溃；④长按进选择，长按拖动连续范围、反向拖回恢复；顶栏“已选 N 张”+✕；悬浮“下载 N 张 JPG（x MB）”为玻璃主按钮（无深色粗外圈），按钮金额与各格标签按同一 saved-aware 计划计算（JVM 测试只保证字节口径一致；可见数字各自舍入，不保证逐项相加等于已舍入的总数；真机上仍需目视复核），按设置格式直接下载（JPG-only 跳过无 JPG 并汇总、Original 全部原始文件）；跨日选择保留并一次下载；下载中底部玻璃进度条可取消，完成显示“已下载 N、跳过 M”；下载中按 Home 不断 Wi-Fi（M1 例外）；⑤预览（放大镜）不改选择，返回保留选择；整日内左右翻到首/末张为止。
- 验收：逐项无异常；叠层不跳变；长日滚动不卡顿、内存稳定（缩略图缓存上限 300）；退后台/划卡语义与 M1 一致。
- 依据：wave2/wifi 包（M4/M5/M6）自报需真机验证（Compose 手势/弹层/窗口适配与真实相机目录无法在 JVM 验证）。

### 19. 真机冒烟：0.2.0 照片预览页（M7 全屏查看器）
- 做什么：装新版后验证：①顶栏仅 返回/文件名/拍摄时间（第二行）/“k / N”（该日内）；②底部单层玻璃信息条“格式 · 大小”（无叠加深色标签），大小只出现一次且与网格标签同口径（JPG-only 为 JPG 大小，Original 为全部文件之和），J+R 显示“JPG + RAW”，RAW-only 在 JPG-only 下显示“仅 RAW”；③详情抽屉：竖屏从照片下部约 35% 区域（含信息条）上滑或点按信息条呼出玻璃底部抽屉，下拉把手/点遮罩/✕/返回键关闭（返回键先关抽屉），左右翻页不误触发；抽屉含文件名、拍摄时间、格式、各文件各自大小、分辨率、光圈/快门/ISO/曝光补偿/焦距/镜头/相机、直方图，读不到的项不出现；抽屉关闭时 `adb logcat` 无元数据部分读取，打开后约 250ms 读原片头部，开着抽屉快速横滑不堆积请求，250ms 内关闭不发起读取；B/C 宽屏右侧面板内容与抽屉一致；设置页已无直方图/拍摄参数/镜头三开关；④单张“下载”为玻璃主按钮（非不透明浅蓝），按默认格式直接下载，下载中玻璃进度叠层可取消、横滑与胶片条锁定，完成后按钮变灰“已保存”；JPG-only 下 RAW-only 照片标“仅 RAW”、提示“此照片没有 JPG”并可手动“下载 RAW”；批量下载进行中进入预览按钮禁用并提示；下载中按 Home 不断 Wi-Fi（M1 例外）；⑤右下方形复选框与网格选择共享；胶片条缩略图完整显示不裁剪，返回网格保留选择；⑥胶片条当前项高亮、点击跳转，长日（>100 张）快速拖动 `adb logcat -s AlphaThumb` 只见可见±4 的 batch；⑦窗口三档：竖屏（A）、折叠屏展开/平板近方形（B，右侧 360dp 详情面板）、横屏（C，顶栏叠在图上、底部单行），信息条不拉伸；旋转后停留在同一张；⑧TalkBack：详情抽屉打开时焦点进入抽屉并朗读标题、背后照片/胶片条不可达，关闭（✕/遮罩/返回键）后焦点回到信息条（sharednew 没有 Compose UI 测试依赖，焦点/语义只做了 JVM 层的异常守卫测试，需 TalkBack 真机验证）。
- 验收：逐项无异常；预览不重复旋转（EXIF 方向正确）；读取/下载/直方图不阻塞主线程；退后台/划卡语义与 M1 一致。
- 依据：wave3/m7 实现与 sol 对 0c9b324..ef196de 的复审（Compose 手势/窗口适配/真实相机部分读取无法在 JVM 验证）；`docs/wifi-architecture.md` “Live status and photo metadata” 的 Viewer use 段。

## P2

### 13. 恢复遥控拍摄功能（后续计划，暂不实现）
- 做什么：遥控与"照片传输"做平级入口。**协议层整体保留**（BLE FF01/FF02 + RemoteControlCoordinator + `triggerShutterSequence`、Wi-Fi capture/liveview、DB `remoteControlEnabled`），UI 入口已移除。研究与设计见 `docs/design/remote-control/`（`research.md`、`remote-control-design.pdf`）。拆分：R1 蓝牙单次拍摄恢复（入口/状态/防重复/离页释放/与位置联动互斥提示）；R2 Wi-Fi 取景+单拍（按用途建会话，当前 Android `AppServices.kt:72` 固定 `browseOnly=true` 是取景被关的原因，并非 v4 不支持 HTTP 取景）；R3 参数面板（缺 access/form 完整属性模型与写入读回链，先单机型单属性白名单）；R4 独立对焦。后台语义沿用 v3：后台/划卡结束 Wi-Fi，蓝牙+GPS 常驻（有用户指定下载时保留 Wi-Fi 至完成）。
- 验收：设计评审后分阶段实施；恢复入口不得影响 GPS 写入/时间校正/照片下载；保留的协议层测试继续通过；需实机矩阵（型号/固件/协议版本/GPS 并用/单拍释放/取景首帧/浏览切换）。
- 待用户决策/实机核实：①第一版做到 R1 还是 R1+R2；②ILCE-7M4 上蓝牙遥控是否与位置联动冲突（官方只明确 α6400 互斥）；③v4 取景能否稳定出帧；④参数面板与独立对焦是否后置。
- 依据：用户 2026-11-10 决定；astra 研究报告。

## P3

### 20. 缩略图黑边成因：待真机日志
- 做什么：网格“相机缩略图自带黑边”仍只是推断（暗景/夜景也可能命中近黑边缘检测，因此日志里的 `nearBlackBars` 非零不等于相机像素里有黑边）。真机用 `adb logcat -s AlphaThumb` 取前 3 张缩略图的 `decoded=WxH nearBlackBars=top/bottom/left/right`，对照同一张照片在相机上的实际画面（明亮场景与暗景各取几张）后下结论；确认前不要据此改网格呈现。
- 验收：明确“黑边在相机缩略图像素里 / 只是暗景”之一，并在 `ThumbnailLetterboxDiagnostics.kt` 的注释或删除诊断中落实。
- 依据：`WifiRemoteController.kt` `logLetterboxDiagnostics`；本表 #16 ③。

### 21. 非自动连接路径未经 phone preflight（说明，非回归）
- 做什么：手机 Wi-Fi/定位前置检查（`WifiAutomaticConnector.phonePreconditions`）只在 `connectAutomatically` 走；`connect(identifier, host)`（`AndroidWifiRemoteScreen` 的 `connect(host)` 分支）和 `prepareManualConnection` 不经它。Android 工厂 `createAndroidWifiRemoteController` 没有配置 `manualSetupConnector`，所以后者在生产上不可达；`connect(host)` 面向已入网的相机，仍会先 claim BLE 再因无 Wi-Fi 失败（JoinCameraWifi 等）。评估是否让 `connect(host)` 也走同一受管 preflight（需先确认其用户场景与期望失败码）。
- 验收：要么 `connect(host)` 与自动路径一致地在 claim 前失败并有测试，要么在设计文档中确认其保持现状的理由后删除本条。
- 依据：sol 对 0c9b324..ef196de 的复审 P2-02；`WifiRemoteController.kt` `connect`/`prepareManualConnection`。

### 6. 补测：队列 Closed → download 报 Protocol
- 做什么：在 `SonyImageTransferTest.kt` 增加用例：openBrowser 后 `queue.close()`，再 `download`，断言 `failureReason == Protocol`。
- 验收：用例通过；去掉 `ensureResponded` 对 Closed 的映射时该用例必须失败。
- 依据：sol 对 a4f756f 的复核（实现分支直接，仅覆盖缺口）；`PtpIpCommandQueueTest.kt:57-67` 现只验证 Closed 解挂。

### 10. 电池电量：实机核实
- 做什么：数据层已实现（best-effort）：Wi-Fi/PTP 会话就绪后读 1 次，标准 `0x5001`（DeviceInfo 的 DevicePropertiesSupported 列出时走 GetDevicePropValue 0x1015）否则 Sony 扩展 `0xD218`（ExtDeviceInfo 列出时走 0x9209），结果经 `CameraLiveStatus.battery`（{percent, obtainedAtMillis, cameraId}，`freshness`：≤30min 新鲜 / ≤24h 灰显带时间 / 更久显示"—"）暴露。依据仅来自 libgphoto2 的 USB Sony PTP 属性表（0x5001 UINT8 0..100；0xD218 INT8 −1=未知），**Wi-Fi 传输模式下相机是否列出/允许读取尚未验证**。待办：①实机（至少 ILCE-7M4）抓 `Wi-Fi PTP` 日志确认 DeviceInfo 的 DevicePropertiesSupported 与 ExtDeviceInfo 是否含 0x5001/0xD218、读数是否与相机屏幕一致；②若两者都不可读，删除 `SonyBatteryReader` 并在本条记录结论（设备页已按缓存显示：无缓存显示未知，缓存 >30 分钟带获取时间，不建 Wi-Fi 会话）。
- 验收：实机读数与相机一致或确认不可得。
- 依据：`sharednew/.../remote/wifi/SonyBatteryReader.kt`；libgphoto2 `camlibs/ptp2/ptp.h`（`PTP_DPC_BatteryLevel`、`PTP_DPC_SONY_BatteryLevel`）；ui-v3 设计"待实机核实"。

### 15. 实机核实：M3 状态数据层与 M8 照片元数据（M8 已由预览页接入）
- 做什么：①RSSI：`readRemoteRssi` 每 10s 一次在 Transmitting 时是否影响 GPS 写入/耗电，Android 回调 status 是否恒为 0；②EXIF：`GetPartialObject(0x101b)`/`0x9211`/catalog `0x923d` 读原片前 64KB 在 ILCE-7M4/其它机型是否稳定，EXIF 是否在首个 APP1 且 ≤64KB（否则退化为 Unavailable，需评估读 preview 字节）；③v4 目录记录 `take(8)`（评分/保护标志，保护/评分显示功能已移除，仅跳过）后的布局未知，v4 路径目前不暴露分辨率（`PhotoMetadata.width/height` 为 null），待抓包确定字节布局后再解析；v3 路径取自 PTP ObjectInfo（ImagePixWidth@26、ImagePixHeight@30）；④GPS 统计：「已写入」= GATT 写确认（相机 ATT 应答）次数，「入队」= 已入队发送次数，两者在断连/超时时可能不等。
- 验收：逐项在 ≥1 台实机上确认或记录例外；不稳定项在数据层降级而非报错。
- 依据：本批 M3/M8 实现自报风险；`docs/wifi-architecture.md` 新增"Live status and photo metadata"一节。

### 11. 实机核实：连接中"取消"不留残余 GATT
- 做什么：设备页连接中已提供"取消"（`DeviceDetailViewModel.cancelConnecting` = 持久化的手动断开：deviceEnabled=false、停 presence 观察、`ACTION_DISCONNECT_DEVICE` → `AndroidBleTransport.disconnectDevice` 关闭含 autoConnect 等待中的 GATT）。需实机确认：相机不在范围/未开机时点"连接"再"取消"，之后相机开机靠近不会自动连上；日志无残余 GATT 回调。
- 验收：≥1 台实机确认取消后无自动重连、无残余 GATT；与 retiredGatts 机制兼容。
- 依据：M2 设备页实现；AndroidBleTransport.connect 使用 autoConnect。

### 17. 日期列表封面缩略图（设计 v3 有，0.2.0 Wi-Fi 屏未做）
- 做什么：日期列表每行左侧显示该日最新一张的缩略图（可见行惰性、每日 1 次请求、中性占位）。需放宽 `SonyImageTransfer.thumbnail()` 只服务“当前显示日”的门控（或为封面单独开放每日首张），并接入 `WifiRemoteController` 的串行队列与代次失效。
- 验收：静置不批量请求；滚动只请求可见行；刷新/断开立即停；不压垮串行 PTP 队列。
- 依据：`docs/design/ui-v3/content3a.py` 日期列表；wave2/wifi 报告“对设计稿的偏离”。

### 18. 清理平铺分页遗留路径
- 做什么：Android UI 只走按日分组浏览（`browseDates`/`browseDate`）；`WifiRemoteController.browsePhotos(offset)`、`WifiRemoteConnection.openPhotoBrowser/photoPage`、`WifiRemoteViewModel.viewPhotoAt` 的平铺翻页分支、`WifiPhotoViewer` 的平铺边界页（加载/失败/传输等待）与 `SonyImageTransfer.page()`/`PAGE_SIZE` 仍保留（大量测试以其为夹具）。评估迁移测试夹具到按日路径后删除平铺分页。
- 验收：全仓无 UI 可达的平铺翻页代码；测试改用按日路径且覆盖不降；全量验证通过。
- 依据：wave2/wifi 取消日期内分页时为控制风险保留（见 `docs/wifi-architecture.md` “Day list, day grid, and thumbnails”）。

### 22. 复审遗留的测试强度与展示边界（0.2.1 复审 P3，不阻断）
- 前置检查"晚返回"测试：现有测试用可取消的 `CompletableDeferred.await()`，取消后旧检查已退出，没有制造"忽略取消后仍返回"的非协作检查；建议补一个先取消、再放行旧结果、断言零 claim/open 并可重连的用例。
- 诊断取消测试只覆盖 disconnect；建议补"同一 Ready 会话内换日/刷新后，排队扫描不再执行"的断言。
- JPG-only 下"无 JPG 且 RAW/HEIF 原件全部超限"的照片：格子仍显示原件大小+无 JPG 警示（NoJpeg），预览页回退原件计划为空显示"超限"并禁用下载；不会误下载也不误报已保存，属 JPG-only 批量与手动 Original 动作的语义区别，待决定是否对齐提示。
