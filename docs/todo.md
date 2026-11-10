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
- 做什么：装新版后验证：单机直达、设备页简化/底部照片传输入口/删除二次确认、直达连接、图标顶栏与方形角标、预览页直接下载与时间副标题、自适应列数与不裁剪缩略图、图标分页栏+跳页、退出应用（划走任务/根页返回）只结束 Wi-Fi，BLE/GPS 与有连接需求的前台服务保持、缩略图分批渐进显示（`adb logcat -s AlphaThumb` 看 batch 行）。
- 验收：逐项无异常；退出/后台不得主动断 BLE，设备页用户主动断开才断 BLE；无下载退后台即结束 Wi-Fi，有用户单张/批量下载则保留至结束。重开应用不自动重建已结束的 Wi-Fi，需用户再次启动照片传输。
- 依据：sonnet 五提交（ad8626b/8f542f6/acea142/b7226b2/451c31f）自报需真机验证清单。

### 9. 真机冒烟：第三轮 UI/UX（ac52215..E 提交）
- 做什么：装新版后验证：①打开相册先显示"正在读取照片目录"，随后日期列表（按日倒序、未知日期最后，张数按 capture 计），进入某日只翻该日页，Back：网格→日期→退出；切日/翻页/返回日期不清选择、不丢"已选 N 张（含其它日期）"、不清已完成汇总；②缩略图只加载可见+前后余量（`adb logcat -s AlphaThumb` 看 batch 行，快速滑动不应整页扫描，翻页/刷新/断开立即停）；③网格长按拖动范围选取（长按格子变选中/取消、拖过范围批量、反向拖回恢复、边缘低速自动滚动且不翻页、checkbox/放大镜仍独立）；④角标 JPG/RAW/J&R/HEIF/H&R 只按文件格式；⑤JPG-only：RAW-only/HEIF-only 跳过并汇总"无 JPEG"，全跳过显示"下载 0/跳过 N"且不发起下载；Original：单张 JPG+RAW 一次下两个无格式选择器；⑥设备页仅 Ⓘ：对话框含重命名/删除二次确认/保持活跃/自动时间区域只读/MTU；连接按钮 连接/连接中…/断开连接/断开中…；断开后相机真实 BLE 断开（相机屏幕连接图标熄灭）、再开设备页仍保持断开、CDM 重新出现/重启不自动重连，点连接才恢复；多相机只断目标；Wi-Fi 会话中断开先关 Wi-Fi；⑦"保持活跃"开关：关（默认）时相机离开范围且无连接后服务应自行停止，开则保活；退出应用（划走/根页返回）不再断 BLE/GPS，仅立即取消下载并结束 Wi-Fi；Home/锁屏/切应用有用户下载时保留 Wi-Fi 至下载结束，无下载立即结束。
- 验收：逐项无异常；特别核对 ⑥ 用户主动断开后 CDM 重新出现不重连、多设备只断目标；⑦ 退出/后台仍继续 GPS 写入且前台服务不被 UI 退出停止，离开范围后的 presence 退休规则不变。
- 依据：本轮 5 提交自报需真机验证（日期/视口/拖选/planner/Info 连接状态机均含无法在 JVM 验证的 Android 传输与手势部分）。
- 子项（权限请求改造，fix(permissions)）：Android 12（API 31/32）全新安装，首次授权定位应一次弹出 FINE+COARSE 同请求（系统显示"精确/大致位置"二选一）；选"精确位置"后走后台定位分步授权（先前台全部通过再弹 ACCESS_BACKGROUND_LOCATION），选"仅大致位置"不放行、仍提示需要精确位置。API 33+ 同步确认 FINE+COARSE+通知一次请求。依据：sol 审阅 03dc59ae（Android 12 要求 FINE 与 COARSE 同请求，`PermissionBox.kt` `foregroundRuntimePermissions`）。

- 子项（M1 后台/退出常驻）：真机分别在单张、跨文件批量下载中按 Home、锁屏、切换应用：确认 Wi-Fi 网络请求与下载不断，最后一项成功/失败/用户取消后仍在后台则释放 Wi-Fi（不留半文件，已提交文件保留），期间回前台则完成后继续保留会话。无下载、只有缩略图/大图预览时退后台立即释放；返回/旋转/活动重建不自动重连。下载中划卡、根页 Back 均立即取消并释放 Wi-Fi，但多台相机 BLE/GPS 与 FGS 保持；设备页主动断开仍仅断目标且保留既有持久化意图。验收还需覆盖后台超过 30 分钟的超时日志/清理、相机或网络丢失、OEM 电池策略。依据：`BackgroundWifiPolicy`、`WifiRemoteController` 的后台保留与 `AppExitCoordinator` 的 Wi-Fi-only 退出；宿主测试不能验证 Android 锁屏调度/系统网络许可窗口。

## P2

### 13. 恢复遥控拍摄功能（后续计划，暂不实现）
- 做什么：遥控与"照片传输"做平级入口。**协议层整体保留**（BLE FF01/FF02 + RemoteControlCoordinator + `triggerShutterSequence`、Wi-Fi capture/liveview、DB `remoteControlEnabled`），UI 入口已移除。研究与设计见 `docs/design/remote-control/`（`research.md`、`remote-control-design.pdf`）。拆分：R1 蓝牙单次拍摄恢复（入口/状态/防重复/离页释放/与位置联动互斥提示）；R2 Wi-Fi 取景+单拍（按用途建会话，当前 Android `AppServices.kt:72` 固定 `browseOnly=true` 是取景被关的原因，并非 v4 不支持 HTTP 取景）；R3 参数面板（缺 access/form 完整属性模型与写入读回链，先单机型单属性白名单）；R4 独立对焦。后台语义沿用 v3：后台/划卡结束 Wi-Fi，蓝牙+GPS 常驻（有用户指定下载时保留 Wi-Fi 至完成）。
- 验收：设计评审后分阶段实施；恢复入口不得影响 GPS 写入/时间校正/照片下载；保留的协议层测试继续通过；需实机矩阵（型号/固件/协议版本/GPS 并用/单拍释放/取景首帧/浏览切换）。
- 待用户决策/实机核实：①第一版做到 R1 还是 R1+R2；②ILCE-7M4 上蓝牙遥控是否与位置联动冲突（官方只明确 α6400 互斥）；③v4 取景能否稳定出帧；④参数面板与独立对焦是否后置。
- 依据：用户 2026-11-10 决定；astra 研究报告。

### 3. 补强回归测试：Original 批量“已锁存旧汇总”场景
- 做什么：`originalBatchDropsASettledJpegOnlyBatchSummary`（`sharednew/src/commonTest/.../WifiRemoteControllerTest.kt` 约 :723-760）在提交 Original 批量 B 之前，先让 A Saved 后 `previewPhoto("photo:0")` → `closePhoto()`，断言 transfer=Idle 且 `batchSummarySettled()==true`，再提交 B；并在传入的 callback 内 `assertNull(batchSummary)` 证明清理早于提交。
- 验收：断言在 `closeAndJoin` 之前；删掉 `downloadOriginalBatch` 里的 `recordBatchSummary(null)` 时该测试必须失败（mutation 自验）。
- 依据：sol 对 910aa05 的复核（测试覆盖真实性缺口，非生产 bug）。

## P3

### 6. 补测：队列 Closed → download 报 Protocol
- 做什么：在 `SonyImageTransferTest.kt` 增加用例：openBrowser 后 `queue.close()`，再 `download`，断言 `failureReason == Protocol`。
- 验收：用例通过；去掉 `ensureResponded` 对 Closed 的映射时该用例必须失败。
- 依据：sol 对 a4f756f 的复核（实现分支直接，仅覆盖缺口）；`PtpIpCommandQueueTest.kt:57-67` 现只验证 Closed 解挂。

### 10. Info 里的电池电量：实机核实 + UI 接入
- 做什么：数据层已实现（best-effort）：Wi-Fi/PTP 会话就绪后读 1 次，标准 `0x5001`（DeviceInfo 的 DevicePropertiesSupported 列出时走 GetDevicePropValue 0x1015）否则 Sony 扩展 `0xD218`（ExtDeviceInfo 列出时走 0x9209），结果经 `CameraLiveStatus.battery`（{percent, obtainedAtMillis, cameraId}，`freshness`：≤30min 新鲜 / ≤24h 灰显带时间 / 更久显示"—"）暴露。依据仅来自 libgphoto2 的 USB Sony PTP 属性表（0x5001 UINT8 0..100；0xD218 INT8 −1=未知），**Wi-Fi 传输模式下相机是否列出/允许读取尚未验证**。待办：①实机（至少 ILCE-7M4）抓 `Wi-Fi PTP` 日志确认 DeviceInfo 的 DevicePropertiesSupported 与 ExtDeviceInfo 是否含 0x5001/0xD218、读数是否与相机屏幕一致；②若两者都不可读，删除 `SonyBatteryReader` 并在本条记录结论；③设备页接入（UI 批次）。
- 验收：实机读数与相机一致或确认不可得；设备页不建 Wi-Fi 会话；无缓存时显示未知。
- 依据：`sharednew/.../remote/wifi/SonyBatteryReader.kt`；libgphoto2 `camlibs/ptp2/ptp.h`（`PTP_DPC_BatteryLevel`、`PTP_DPC_SONY_BatteryLevel`）；ui-v3 设计"待实机核实"。

### 15. 实机核实：M3 状态数据层与 M8 照片元数据（UI 接入前）
- 做什么：①RSSI：`readRemoteRssi` 每 10s 一次在 Transmitting 时是否影响 GPS 写入/耗电，Android 回调 status 是否恒为 0；②EXIF：`GetPartialObject(0x101b)`/`0x9211`/catalog `0x923d` 读原片前 64KB 在 ILCE-7M4/其它机型是否稳定，EXIF 是否在首个 APP1 且 ≤64KB（否则退化为 Unavailable，需评估读 preview 字节）；③v4 目录记录 `take(8)`（评分/保护标志）布局未知，v4 路径目前不暴露分辨率与保护状态（`PhotoMetadata.width/height/isProtected` 为 null），待抓包确定字节布局后再解析；v3 路径取自 PTP ObjectInfo（ProtectionStatus@6、ImagePixWidth@26、ImagePixHeight@30）；④GPS 统计：「已写入」= GATT 写确认（相机 ATT 应答）次数，「入队」= 已入队发送次数，两者在断连/超时时可能不等。
- 验收：逐项在 ≥1 台实机上确认或记录例外；不稳定项在数据层降级而非报错。
- 依据：本批 M3/M8 实现自报风险；`docs/wifi-architecture.md` 新增"Live status and photo metadata"一节。

### 11. "连接中…" 无取消
- 做什么：设备页连接按钮 Connecting 态目前双禁用（不可重复点也不可取消）；评估加"取消连接"（enabled=false→close GATT 的反向路径）并处理超时（CONNECT_SETTLE 8s 仅解除 UI pending，不中止系统 autoConnect）。
- 验收：Connecting 可取消且不留残余 GATT；与 disconnectDevice 的 retiredGatts 机制兼容。
- 依据：本轮 E 设计状态机（Connecting→禁用）与 AndroidBleTransport.connect 使用 autoConnect。
