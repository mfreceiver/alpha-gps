# 待办（TODO）

> **纪律（必须遵守）**
> 1. 本文件**只放当前待完成**的事项。已完成、已放弃、已过期的条目**当次提交内立即删除**，不打勾保留、不挪到“已完成”区。
> 2. 完成历史靠 git 记录（conventional commit 信息 + tag），不在这里留痕。
> 3. 每条必须写清：**要做什么 / 验收标准 / 依据或位置**。写不清楚的先别入表。
> 4. 新发现的事项当天加入；做完一项，在**同一个提交**里删掉对应条目。
> 5. 按优先级排序（P1 > P2 > P3），同级按紧迫程度。每次编辑后检查是否有已过期条目。
> 6. 流程与背景说明不放这里（放 README 或 docs/ 下对应文档）；这里只放“还没做的事”。

最后整理：2026-11-08（基线 v0.1.2，versionCode 3）

## P1

### 1. 真机冒烟 + 缩略图实测数据
- 做什么：用 0.1.2 覆盖安装 0.1.0/0.1.1，连真实索尼相机，翻若干页（含 RAW+JPG 的页）、跨页多选、批量下载 JPG、详情页单张下载、多选下预览按钮；`adb logcat -s AlphaThumb` 抓每张/每页日志。
- 验收：得到每页 60 张的 `reqTotalMs`、`reqMaxMs`、`bytesTotal`、`bitmapEstBytes`；记录安装升级、竖拍方向、触控是否有异常。
- 依据：日志格式见 `sharednew/.../remote/wifi/ThumbnailDiagnostics.kt`；决策依赖这些数据（见第 2 项）。

### 2. 依据实测数据决定“每页张数 / 缩略图解码缩小”
- 前置：第 1 项数据。
- 做什么：评估 Android 解码缩小（`inSampleSize` / RGB565）与每页 90 或 120 张；PAGE_SIZE 在 `sharednew/.../remote/wifi/SonyImageTransfer.kt:355`（当前 60）。无限滚动方案已决定不做。
- 验收：有数据支撑的结论（保持 60 或改值），改动需通过全量测试并经 sol 审阅。

## P2

### 3. 补强回归测试：Original 批量“已锁存旧汇总”场景
- 做什么：`originalBatchDropsASettledJpegOnlyBatchSummary`（`sharednew/src/commonTest/.../WifiRemoteControllerTest.kt` 约 :723-760）在提交 Original 批量 B 之前，先让 A Saved 后 `previewPhoto("photo:0")` → `closePhoto()`，断言 transfer=Idle 且 `batchSummarySettled()==true`，再提交 B；并在传入的 callback 内 `assertNull(batchSummary)` 证明清理早于提交。
- 验收：断言在 `closeAndJoin` 之前；删掉 `downloadOriginalBatch` 里的 `recordBatchSummary(null)` 时该测试必须失败（mutation 自验）。
- 依据：sol 对 910aa05 的复核（测试覆盖真实性缺口，非生产 bug）。

## P3

### 5. browse-only 下 busy 时 Back 无反馈
- 做什么：传输进行中按返回无任何反馈（取消按钮可用）。加提示或确认“结束连接并取消传输？”。
- 验收：忙时 Back 有明确反馈；补一条 Controller/VM 层测试。
- 依据：v0.1.0 发版知情清单。
