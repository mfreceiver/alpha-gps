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
- 做什么：装新版后验证：单机直达、设备页简化/底部照片传输入口/删除二次确认、直达连接、图标顶栏与方形角标、预览页直接下载与时间副标题、自适应列数与不裁剪缩略图、图标分页栏+跳页、退出应用（划走任务/根页返回）后相机 BLE 断开、缩略图分批渐进显示（`adb logcat -s AlphaThumb` 看 batch 行）。
- 验收：逐项无异常；特别注意退出断开与重开应用秒开不冲突。
- 依据：sonnet 五提交（ad8626b/8f542f6/acea142/b7226b2/451c31f）自报需真机验证清单。

### 9. 真机冒烟：第三轮 UI/UX（ac52215..E 提交）
- 做什么：装新版后验证：①打开相册先显示"正在读取照片目录"，随后日期列表（按日倒序、未知日期最后，张数按 capture 计），进入某日只翻该日页，Back：网格→日期→退出；切日/翻页/返回日期不清选择、不丢"已选 N 张（含其它日期）"、不清已完成汇总；②缩略图只加载可见+前后余量（`adb logcat -s AlphaThumb` 看 batch 行，快速滑动不应整页扫描，翻页/刷新/断开立即停）；③网格长按拖动范围选取（长按格子变选中/取消、拖过范围批量、反向拖回恢复、边缘低速自动滚动且不翻页、checkbox/放大镜仍独立）；④角标 JPG/RAW/J&R/HEIF/H&R 只按文件格式；⑤JPG-only：RAW-only/HEIF-only 跳过并汇总"无 JPEG"，全跳过显示"下载 0/跳过 N"且不发起下载；Original：单张 JPG+RAW 一次下两个无格式选择器；⑥设备页仅 Ⓘ：对话框含重命名/删除二次确认/保持活跃/自动时间区域只读/MTU；连接按钮 连接/连接中…/断开连接/断开中…；断开后相机真实 BLE 断开（相机屏幕连接图标熄灭）、再开设备页仍保持断开、CDM 重新出现/重启不自动重连，点连接才恢复；多相机只断目标；Wi-Fi 会话中断开先关 Wi-Fi；⑦"保持活跃"开关：关（默认）时相机离开范围且无连接后服务应自行停止，开则保活；退出应用（划走/根页返回）仍断开。
- 验收：逐项无异常；特别核对 ⑥ 断开后 CDM 重新出现不重连、⑦ 默认行为变化无意外掉线。
- 依据：本轮 5 提交自报需真机验证（日期/视口/拖选/planner/Info 连接状态机均含无法在 JVM 验证的 Android 传输与手势部分）。
- 子项（权限请求改造，fix(permissions)）：Android 12（API 31/32）全新安装，首次授权定位应一次弹出 FINE+COARSE 同请求（系统显示"精确/大致位置"二选一）；选"精确位置"后走后台定位分步授权（先前台全部通过再弹 ACCESS_BACKGROUND_LOCATION），选"仅大致位置"不放行、仍提示需要精确位置。API 33+ 同步确认 FINE+COARSE+通知一次请求。依据：sol 审阅 03dc59ae（Android 12 要求 FINE 与 COARSE 同请求，`PermissionBox.kt` `foregroundRuntimePermissions`）。

## P2

### 3. 补强回归测试：Original 批量“已锁存旧汇总”场景
- 做什么：`originalBatchDropsASettledJpegOnlyBatchSummary`（`sharednew/src/commonTest/.../WifiRemoteControllerTest.kt` 约 :723-760）在提交 Original 批量 B 之前，先让 A Saved 后 `previewPhoto("photo:0")` → `closePhoto()`，断言 transfer=Idle 且 `batchSummarySettled()==true`，再提交 B；并在传入的 callback 内 `assertNull(batchSummary)` 证明清理早于提交。
- 验收：断言在 `closeAndJoin` 之前；删掉 `downloadOriginalBatch` 里的 `recordBatchSummary(null)` 时该测试必须失败（mutation 自验）。
- 依据：sol 对 910aa05 的复核（测试覆盖真实性缺口，非生产 bug）。

## P3

### 6. 补测：队列 Closed → download 报 Protocol
- 做什么：在 `SonyImageTransferTest.kt` 增加用例：openBrowser 后 `queue.close()`，再 `download`，断言 `failureReason == Protocol`。
- 验收：用例通过；去掉 `ensureResponded` 对 Closed 的映射时该用例必须失败。
- 依据：sol 对 a4f756f 的复核（实现分支直接，仅覆盖缺口）；`PtpIpCommandQueueTest.kt:57-67` 现只验证 Closed 解挂。

### 8. 残余风险：任务移除的首次 onTaskRemoved 迟到投递可落在重连后的新服务上
- 做什么：评估在 AppExitGate/LocationSenderService 增加对“重连后立刻到达的 unowned removal”的启发式甄别，。窗口条件：Android 15 AOSP 中 ActivityTaskSupervisor.cleanUpRemovedTask → ActiveServices.cleanUpServices 只把 taskRemoved 的枚举投到 system-server Handler，执行时才枚举当时运行的服务；同一 removal 的**首次**投递可晚于旧服务 teardown 完成与 Connect 重启，落到新服务实例上。若旧 flow 在退休时已完成（completedAtRetire=true），Gate 按状态机会放行该迟到投递（AppExitGate.kt scheduleExit），disconnectAll 会清掉新 GATT、stopSelf 停掉新 FGS。根页 Back 已改 finishAndRemoveTask()，"Back 完成后卡片仍在→真实划卡" 主路径已消除；该残余窗口与既有 unowned-entered flow 的同类窗口同级，本地无法区分（事件序列与"真实新划卡"完全相同）。AOSP 依据：ActivityTaskSupervisor.cleanUpRemovedTask/ActiveServices.cleanUpServices 的异步 Handler 投递（sol 源码分析，2026-11-09）。
- 验收：若处置，需与 AppExitGateTest 全部既有反例测试兼容；若评估后决定接受风险，把依据迁到 docs/ 风险文档并在决策提交里删除本条。
- 依据：sol 对 9b436d9 的复核（P2-2 设计处置结论）。

### 10. Info 里的电池电量
- 做什么：设计要求"仅 Wi-Fi 会话查 1 次电池并标注获取时间"，但 sharednew 内没有任何电池查询协议（grep battery 仅帮助文案），本轮已砍掉。需先确认 Sony PTP/Camera Remote 是否提供电池属性，再接入 Info（缓存 {value,obtainedAt,cameraId}，失败显示未知）。
- 验收：有协议依据；Info 不建 Wi-Fi 会话；无会话时显示未知。
- 依据：本轮 E 提交实施时核实源码无电池协议。

### 11. "连接中…" 无取消
- 做什么：设备页连接按钮 Connecting 态目前双禁用（不可重复点也不可取消）；评估加"取消连接"（enabled=false→close GATT 的反向路径）并处理超时（CONNECT_SETTLE 8s 仅解除 UI pending，不中止系统 autoConnect）。
- 验收：Connecting 可取消且不留残余 GATT；与 disconnectDevice 的 retiredGatts 机制兼容。
- 依据：本轮 E 设计状态机（Connecting→禁用）与 AndroidBleTransport.connect 使用 autoConnect。
