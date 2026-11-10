# AlphaGPS 遥控恢复：研究与设计依据

> 2026-11-10 · 仅研究与设计，不代表已实现。Android 12+，固定蓝色，承接 UI v3。代码核查是静态阅读，未运行 Gradle、协议测试或相机实测。文中状态分为「现有实现」「拟议设计」「待验证」。

## 1. 结论

1. **蓝牙遥控底层仍在，界面入口缺失**。可恢复快门、半按与 AF-ON；不能据此宣称支持实时取景、曝光参数编辑、录像、变焦或任意自定义按键。
2. **Wi-Fi 不是只有照片传输**。保留 PTP 遥控快门、HTTP 实时取景和模式切换实现；但 Android 注入 `browseOnly = { true }`，当前产品路径主动禁用遥控会话与取景。恢复需引入明确会话用途，不能把全局开关简单改成 false 后宣称完成。
3. **曝光参数是新能力，不是接回旧界面**。现有属性解析丢弃访问标志与枚举/范围信息，没有完整参数写入链。设计可展示目标交互，但必须经能力发现、写入、读回和实机门控逐项启用。
4. **蓝牙遥控与位置同步不是普遍兼容**。Sony 明确说明 α6400 打开蓝牙遥控时不能使用手机位置联动。对于其它型号不按年代推断兼容；用型号/固件验证矩阵与运行时状态共同判断。
5. **共享一个控制所有者**。BLE 快门、Wi-Fi 遥控、传输不能各自独立发命令。切换先释放按键、停取景，再接管会话。拍摄结果不确定时绝不自动重发。

## 2. 基线与入口

设计以 [UI v3 PDF](../ui-v3/ui-design-v3.pdf) 为视觉和导航权威；[v1](../history/ui-status-enhancement.pdf) 保留数据来源，不能用 [v2](../history/ui-status-enhancement-v2.pdf) 覆盖 v3 已确认导航。

v3 规定：G1 卡片、G2 悬浮条、G3 决策层；无分隔线；信息 tag 小方角不可点；操作胶囊触控至少 48dp；对话框左取消右确认。固定蓝色，不用动态色。静态 SVG 以渐变/透明度模拟玻璃，实际 Android 实现使用 Haze 真模糊。

**拟议入口**：设备页保持相机卡、位置传输卡以及唯一底部主按钮「照片传输」；在两卡下方空白区加入一个次级「遥控拍摄」胶囊。进入遥控选择页本身不自动连接。选择「蓝牙快门」或「Wi-Fi 取景」才视为用户主动连接请求。避免同时出现两个同权主按钮，不把遥控藏进信息弹窗，不新增帮助入口。

现有设备页面并不是已落地的 v3：共享 [DeviceDetailScreen.kt:121–174](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/device/DeviceDetailScreen.kt#L121-L174) 仍是连接按钮和 88dp 传输入口；Android [DeviceDetailScreen.kt:91–162](../../../app/src/main/java/com/saschl/cameragps/ui/device/DeviceDetailScreen.kt#L91-L162) 仅打开 Wi-Fi 页。因此本方案是在 v3 目标上增量设计，不应误认现有 UI 截图。

## 3. 能力矩阵

| 能力 | 代码现状 | 产品决策 / 门控 |
|---|---|---|
| BLE 全按/释放 | 已有枚举和写入 | 第一阶段恢复单次拍摄；已入队不是已拍摄 |
| BLE 半按 / AF-ON | 已有命令 | 第二阶段开放按住交互；必须处理取消、移出、后台、断连 |
| BLE 取景 | 未见实现 | 不提供；可提示切 Wi-Fi |
| BLE 曝光参数 | 未见实现 | 不提供；无伪参数按钮 |
| Wi-Fi 单次拍摄 | PTP 能力门控 + 释放 + 事件 | 恢复会话用途及 UI 后实机验证 |
| Wi-Fi 实时取景 | HTTP 端点、解码、流消费已保留 | 非 browseOnly 才启用；失败独立于控制会话 |
| Wi-Fi 独立对焦 | 没有面向界面的独立接口 | 展示为目标，未验证不可点；不能把固定延时称为合焦 |
| ISO / 光圈 / 快门 / EV / WB | 非完整可写属性模型 | 后续逐项读取 access + form + 枚举，并校验读回 |
| 浏览 / 下载 | 当前主要路径 | 独立 ImageTransfer 用途保留，不退化为遥控前置 |
| 录像 / BULB / 连拍 / 变焦 / 触摸对焦 | 未核实完整链路 | 本轮不承诺，不呈现可用按钮 |

## 4. 代码证据索引

### BLE

- [RemoteCommand.kt:15–27](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/coordinator/RemoteCommand.kt#L15-L27)：仅 `ShutterFullPress/Release`、`ShutterHalfPress/Release`、`AfOnPress/Release`。
- [RemoteControlCoordinator.kt:60–74](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/coordinator/RemoteControlCoordinator.kt#L60-L74)：FF02 通知与 FF01 探测；不能以 CC09 位单独判断支持。
- [RemoteControlCoordinator.kt:123–135](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/coordinator/RemoteControlCoordinator.kt#L123-L135)：连接和 active 门控；返回值只说明入队。Wi-Fi 占有期间拒绝新的按下。
- [RemoteControlCoordinator.kt:162–184](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/coordinator/RemoteControlCoordinator.kt#L162-L184)：单次序列防重入；尾部 ready 等待可被新请求替代。UI 仍需禁止重复提交当前未释放序列。
- [RemoteControlCoordinator.kt:318–335](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/coordinator/RemoteControlCoordinator.kt#L318-L335)：`claimWifiControls` 取消并等待探测和快门序列；释放所有权只对已监控设备重启探测。
- [RemoteControlCoordinator.kt:399–407](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/coordinator/RemoteControlCoordinator.kt#L399-L407)：探测初始 500ms / 间隔 3000ms；合焦 2s、曝光 2s、ready 20s 是兜底超时，不等于可靠拍摄确认。
- [CameraSessionOrchestrator.kt:174–211](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/bluetooth/session/CameraSessionOrchestrator.kt#L174-L211)：Wi-Fi 准备依赖 Transmitting / MTU/bootstrap；保留远控发送、序列及监控接口。旧机位置受限时此前置可能阻断 Wi-Fi 启动，不能承诺所有旧机自动切换。
- [CameraDevice.kt:23](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/database/devices/CameraDevice.kt#L23)：`remoteControlEnabled=false`。持久化用户偏好不应等同正在按键或控制所有权；离开遥控页停止监控，不改写主动断连意图。

### Wi-Fi

- [AppServices.kt:69–72](../../../app/src/main/java/com/saschl/cameragps/AppServices.kt#L69-L72)：固定 browseOnly；这解释当前没有取景，而不是协议 v4 不支持 HTTP。
- [SonyWifiSession.kt:107–126](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyWifiSession.kt#L107-L126)：ImageTransfer 与 RemoteShooting 用途；`canCapture` 由用途、0x9207 及半/全按能力共同判断。
- [SonyWifiSession.kt:129–184](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyWifiSession.kt#L129-L184)：分离模式进入浏览替换会话，关闭浏览回遥控；browseOnly 不返回遥控且不启动 images。
- [SonyPtpInitializer.kt:196–210](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyPtpInitializer.kt#L196-L210)：协商用途和模式；v4 combined / v3 separate 是实现分支，不是全型号兼容保证。
- [SonyPtpShutter.kt:11–105](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyPtpShutter.kt#L11-L105)：0xd2c1 半按、0xd2c2 全按，2 按下 / 1 释放；Busy 与不支持门控；500ms 延时不是已合焦；非取消释放；捕获事件才确认，否则 Uncertain。
- [SonyLiveViewStream.kt:58–146](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyLiveViewStream.kt#L58-L146)：非 browseOnly combined 模式仍发控制且走 HTTP；首帧超时 10s，buffer 1 丢旧帧；503 限 3 次 / 15s，不能据此让所有故障无限自动重试。
- [SonyCameraProperties.kt:61–95](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyCameraProperties.kt#L61-L95)：当前解析选定属性，丢弃 access 和 form 内容。EXIF 中的曝光数据是照片元信息，不是相机实时可写属性。
- [WifiRemoteController.kt:245–259](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/WifiRemoteController.kt#L245-L259)：控制者、Ready、浏览状态、photoJob 联合限制 capture。跨机不能抢占，传输占用时不能静默停任务以拍照。
- [AutomaticWifiRemoteConnector.kt:18–65](../../../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/AutomaticWifiRemoteConnector.kt#L18-L65)：先相机准备，再系统网络许可、加入、会话；网络请求在相机会话释放后关闭，连接超时 60s。界面取消必须贯穿所有阶段且避免迟到回调重新打开。

### 生命周期与既有文案差异

- [AndroidWifiRemoteScreen.kt:62–71](../../../app/src/main/java/com/saschl/cameragps/ui/device/AndroidWifiRemoteScreen.kt#L62-L71) 当前 ON_STOP **保留** Wi-Fi，dispose 才 close；v3 目标是离开应用结束 Wi-Fi、继续 BLE/GPS。这是需修改的差异，不是已实现行为。
- [AndroidWifiRemoteScreen.kt:73–123](../../../app/src/main/java/com/saschl/cameragps/ui/device/AndroidWifiRemoteScreen.kt#L73-L123)：按 Android 版本权限和 keepScreenOn；新增遥控不能另建权限体系。系统网络授权引起短暂停顿需与真正离开应用区分，用户点取消始终终止。
- [中文 strings.xml:244–247](../../../sharednew/src/commonMain/composeResources/values-zh-rCN/strings.xml#L244-L247) 仍写不再使用遥控，且概括新机可并用；恢复时英/简中文同步更新，只写已验证型号结论。

## 5. 官方依据与边界

已读取 [Sony α6400 蓝牙遥控指南](https://helpguide.sony.net/ilc/1810/v1/en/contents/TP0002392817.html)（2026-11-10）：固件 2.00 起提供该遥控功能；开启 Bluetooth Rmt Ctrl 时不能手机位置联动、不能红外遥控，也不进入省电模式。该指南描述 RMT-P1BT，不证明 AlphaGPS 协议实现全部兼容。UI 应明确警示 GPS 会暂停、相机耗电可能增加，而不是引导用户默默改菜单。

项目既有官方链接另有 [α6400 位置联动](https://helpguide.sony.net/ilc/1810/v1/en/contents/TP0002273573.html) 和 [ZV-E10 位置联动](https://helpguide.sony.net/ilc/2070/v1/en/contents/TP0001212414.html)，本轮未逐页读取后两者，不能将其内容当新增已核实结论。

## 6. 拟议状态与操作契约

- BLE：Idle → Preparing → Ready → Pressing → Releasing → Ready；Unavailable / Lost 独立呈现。通知可缺失，超时只退出等待，不伪造合焦/成功。离页发送 best-effort release；失联不能保证相机已释放，明确提醒检查相机。
- Wi-Fi：Idle → PreparingCamera → AwaitingNetworkApproval → JoiningNetwork → OpeningSession → Ready。Ready 分成 control、liveView、property 三组能力，不用一个布尔值把整页一起禁掉。
- 取景：Waiting / Streaming / Stale / Failed。冻结最后帧时加明显「画面已暂停」，不可冒充实时；重试只重启取景。设计建议超过 2s 无新帧标陈旧（需测正常低帧率，非当前协议常量）。
- 快门：接收点击立即抑制重复；执行后显示「请求已发送」或「相机已确认拍摄」。Uncertain 显示「结果未确认，请检查相机」，不自动重发。被拒展示可恢复原因，不误标成功。
- 参数：Unsupported 隐藏；Unknown 显示 —；ReadOnly 可见但不响应；Writable 才打开选择面板；Pending 保持旧确认值并显示应用中；失败恢复旧值并提示。相机物理操作改变模式后重新核实能力及值。
- 用户主动断开蓝牙后不自动重连；进入遥控选择页不改变这一意图。点击连接/启动取景属于明确连接请求，使用既有连接策略。

## 7. 与传输和 GPS 的协调

| 用户动作 | 目标语义 |
|---|---|
| 蓝牙页 → Wi-Fi 取景 | 释放全部按键 → 停 BLE 监控 → claim Wi-Fi → 准备会话；失败归还 owner，按页面可见性恢复监控 |
| 取景 → 照片传输 | 快门/属性提交完成或取消 → 停取景 → 确认无下载冲突 → 切用途；分离模式重新打开 PTP，会话 epoch 变化，旧句柄作废 |
| 传输 → 遥控 | 正在下载时不允许静默抢占；明确取消下载并等待释放，或留在传输。退出浏览后重建取景与能力，不用旧参数值 |
| 遥控返回设备页 | 释放控制，结束此遥控 Wi-Fi（若有），保留蓝牙/GPS；不是“断开蓝牙” |
| Home / 锁屏 / 应用后台 | 按 v3 目标结束遥控 Wi-Fi，BLE 不断开；停止 BLE 按键与前台遥控监控；GPS 依相机能力继续，不保证互斥机自动恢复菜单 |
| 断开蓝牙 | 沿用 v3 破坏性确认；释放遥控及 Wi-Fi，持久化用户主动断开 |

GPS 状态必须依据真实写入，不把「手机有定位」等同「已写入相机」。已知互斥机在启用前给确认：左取消，右继续遥控；说明需在相机关闭蓝牙遥控才能恢复位置联动。未知机显示兼容性待验证，不宣传同时可用。

## 8. 开发拆分与验收（未执行）

**R1 BLE 单次恢复**：入口/偏好/前台监控接线、状态映射、防重复、离页释放、互斥提示、双语文案。验收正常单拍；断连/取消无残留按下；BLE 入队不显示已保存照片；用户断连意图不被浏览页面覆盖。

**R2 Wi-Fi 取景和单拍**：按入口指定 purpose；复用网络 owner、生命周期与取消；取景失败和控制失败分离；与传输切换清理。验收 v3 separate 与 v4 combined 各一类实机；切换不双开 session；迟到回调不可复活；后台无 Wi-Fi 遥控流；不确定结果不自动补拍。

**R3 参数面板**：建立 descriptor（类型/access/form/current/合法值/会话版本）、串行写入、响应后读回、模式依赖、超时恢复。先单型号单属性白名单验证，再扩展；未知属性不可写；不能硬编码一套曝光档位应用所有机型。

**R4 独立对焦**：分别验证 BLE 半按和 AF-ON、Wi-Fi 半按 API、AF-S/AF-C/MF、不可合焦情况、触控取消与系统返回。触摸取景指定对焦点不在此阶段。

已有测试可扩展：`RemoteControlCoordinatorTest`、`WifiRemoteControllerTest`、`SonyWifiSessionTest`、`SonyPtpInitializerTest`、`SonyPtpShutterTest`、`SonyLiveViewStreamTest`。本次只读测试名称，未运行，不声称测试通过。

实机矩阵至少记录：型号、固件、Android 版本、协议版本、GPS并用、单拍/释放、对焦语义、首帧/恢复、浏览切换、参数读写及失败。重点覆盖 α6400 互斥路径与已在代码中备注的 α6700；α7 IV 不因文案而自动认定兼容。

## 9. 交付

- `remote-control-design.pdf`：16 页评审稿，亮/暗界面与状态契约。
- `remote-control-design.html` 与 `screens/*.svg`：可查看、可复用的矢量源稿。
- `build_design.py`：只写本目录的生成脚本，只读复用 v3 图形原语；使用 `PYTHONDONTWRITEBYTECODE=1` 防止改写 v3 缓存。
- `validation.md`：实际导出与检查结果。

未实现功能、未确认型号能力不属于交付缺陷，而是明确的下一阶段门控；本次不修改应用、todo 或 v3 定稿。
