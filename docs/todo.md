# 待办（TODO）

> **纪律（必须遵守）**
> 1. 本文件**只放当前待完成**的事项。已完成、已放弃、已过期的条目**当次提交内立即删除**，不打勾保留、不挪到“已完成”区。
> 2. 完成历史靠 git 记录（conventional commit 信息 + tag），不在这里留痕。
> 3. 每条必须写清：**要做什么 / 验收标准 / 依据或位置**。写不清楚的先别入表。
> 4. 新发现的事项当天加入；做完一项，在**同一个提交**里删掉对应条目。
> 5. 按优先级排序（P1 > P2 > P3），同级按紧迫程度。每次编辑后检查是否有已过期条目。
> 6. 流程与背景说明不放这里（放 README 或 docs/ 下对应文档）；这里只放“还没做的事”。

最后整理：2026-11-09（基线 v0.1.3，versionCode 4）

## P1

### 1. 真机冒烟：11 项 UI/UX 改造（0673adf..451c31f）
- 做什么：装新版后验证：单机直达、设备页简化/底部照片传输入口/删除二次确认、直达连接、图标顶栏与方形角标、预览页直接下载与时间副标题、自适应列数与不裁剪缩略图、图标分页栏+跳页、退出应用（划走任务/根页返回）后相机 BLE 断开、缩略图分批渐进显示（`adb logcat -s AlphaThumb` 看 batch 行）。
- 验收：逐项无异常；特别注意退出断开与重开应用秒开不冲突。
- 依据：sonnet 五提交（ad8626b/8f542f6/acea142/b7226b2/451c31f）自报需真机验证清单。

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

### 6. 补测：队列 Closed → download 报 Protocol
- 做什么：在 `SonyImageTransferTest.kt` 增加用例：openBrowser 后 `queue.close()`，再 `download`，断言 `failureReason == Protocol`。
- 验收：用例通过；去掉 `ensureResponded` 对 Closed 的映射时该用例必须失败。
- 依据：sol 对 a4f756f 的复核（实现分支直接，仅覆盖缺口）；`PtpIpCommandQueueTest.kt:57-67` 现只验证 Closed 解挂。

### 7. README 精度：iOS 保留的德语 string catalog
- 做什么：README:63-66、88-89 改写为“knownRegions 仅 en/Base；保留的 `iosApp/alphagps/InfoPlist.xcstrings` 仍含历史德语翻译（iOS 默认禁用，未清理）”，或直接删除该文件的 de 条目。
- 验收：README 陈述与 `InfoPlist.xcstrings`、`project.pbxproj` 实际一致。
- 依据：sol 对 a4f756f 的复核（InfoPlist.xcstrings:28-54）。
