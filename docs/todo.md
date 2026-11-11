# 待办（TODO）

> **纪律（必须遵守）**
> 1. 本文件**只放当前待完成**的事项。已完成、已放弃、已过期的条目**当次提交内立即删除**，不打勾保留、不挪到“已完成”区。
> 2. 完成历史靠 git 记录（conventional commit 信息 + tag），不在这里留痕。
> 3. 每条必须写清：**要做什么 / 验收标准 / 依据或位置**。写不清楚的先别入表。
> 4. 新发现的事项当天加入；做完一项，在**同一个提交**里删掉对应条目。
> 5. 按优先级排序（P1 > P2 > P3），同级按紧迫程度。每次编辑后检查是否有已过期条目。
> 6. 流程与背景说明不放这里（放 README 或 docs/ 下对应文档）；这里只放“还没做的事”。

最后整理：2026-11-11（基线 v0.1.4，versionCode 见 gradle.properties）

## P1

### 1. 真机冒烟：11 项 UI/UX 改造（0673adf..451c31f）
- 做什么：装新版后验证：单机直达、设备页简化/底部照片传输入口/删除二次确认、直达连接、图标顶栏与方形角标、预览页直接下载与时间副标题、退出应用（划走任务/根页返回）只结束 Wi-Fi，BLE/GPS 与有连接需求的前台服务保持、缩略图分批渐进显示（`adb logcat -s AlphaThumb` 看 batch 行）。
- 验收：逐项无异常；退出/后台不得主动断 BLE，设备页用户主动断开才断 BLE；无下载退后台即结束 Wi-Fi，有用户单张/批量下载则保留至结束。重开应用不自动重建已结束的 Wi-Fi，需用户再次启动照片传输。
- 依据：sonnet 五提交（ad8626b/8f542f6/acea142/b7226b2/451c31f）自报需真机验证清单。

### 9. 真机冒烟：第三轮 UI/UX（ac52215..E 提交）
- 做什么：装新版后验证：①打开相册先显示"正在读取照片目录"，随后日期列表（按日倒序、未知日期最后，张数按 capture 计），进入某日显示该日整日网格（0.2.0 起日期内不分页），Back：网格→日期→退出；切日/返回日期不清选择、不丢"已选 N 张（含其它日期）"、不清已完成汇总；②缩略图只加载可见+前后余量（`adb logcat -s AlphaThumb` 看 batch 行，快速滑动不应整页扫描，换日/刷新/断开立即停）；③网格长按拖动范围选取（长按格子变选中/取消、拖过范围批量、反向拖回恢复、边缘低速自动滚动、checkbox/放大镜仍独立）；④角标 JPG/RAW/J&R/HEIF/H&R 只按文件格式；⑤下载格式 JPG：RAW-only/HEIF-only 跳过并汇总"无 JPEG"，全跳过显示"下载 0/跳过 N"且不发起下载；JPG + RAW：单张 JPG+RAW 一次下两个无格式选择器（0.3.1 起默认为"每次询问"，该项须先在设置里选 JPG / JPG + RAW；"每次询问"与 RAW 的核对见 #37）；⑥设备页仅 Ⓘ：对话框含重命名/删除二次确认/保持活跃/自动校时与区域调整（合并一行）只读/名称行右侧 连接·取消·断开（断开已并入 Ⓘ 弹窗，设备页状态卡只有 连接/取消；设备列表每行的 ⓘ 打开同一弹窗）；断开后相机真实 BLE 断开（相机屏幕连接图标熄灭）、再开设备页仍保持断开、CDM 重新出现/重启不自动重连，点连接才恢复；多相机只断目标；Wi-Fi 会话中断开先关 Wi-Fi；⑦"保持活跃"开关：关（默认）时相机离开范围且无连接后服务应自行停止，开则保活；退出应用（划走/根页返回）不再断 BLE/GPS，仅立即取消下载并结束 Wi-Fi；Home/锁屏/切应用有用户下载时保留 Wi-Fi 至下载结束，无下载立即结束。
- 验收：逐项无异常；特别核对 ⑥ 用户主动断开后 CDM 重新出现不重连、多设备只断目标；⑦ 退出/后台仍继续 GPS 写入且前台服务不被 UI 退出停止，离开范围后的 presence 退休规则不变。
- 依据：本轮 5 提交自报需真机验证（日期/视口/拖选/planner/Info 连接状态机均含无法在 JVM 验证的 Android 传输与手势部分）。
- 子项（权限请求改造，fix(permissions)）：Android 12（API 31/32）全新安装，首次授权定位应一次弹出 FINE+COARSE 同请求（系统显示"精确/大致位置"二选一）；选"精确位置"后走后台定位分步授权（先前台全部通过再弹 ACCESS_BACKGROUND_LOCATION），选"仅大致位置"不放行、仍提示需要精确位置。API 33+ 同步确认 FINE+COARSE+通知一次请求。依据：sol 审阅 03dc59ae（Android 12 要求 FINE 与 COARSE 同请求，`PermissionBox.kt` `foregroundRuntimePermissions`）。

- 子项（M1 后台/退出常驻）：真机分别在单张、跨文件批量下载中按 Home、锁屏、切换应用：确认 Wi-Fi 网络请求与下载不断，最后一项成功/失败/用户取消后仍在后台则释放 Wi-Fi（不留半文件，已提交文件保留），期间回前台则完成后继续保留会话。无下载、只有缩略图/大图预览时退后台立即释放；返回/旋转/活动重建不自动重连。下载中划卡、根页 Back 均立即取消并释放 Wi-Fi，但多台相机 BLE/GPS 与 FGS 保持；设备页主动断开仍仅断目标且保留既有持久化意图。验收还需覆盖后台超过 30 分钟的超时日志/清理、相机或网络丢失、OEM 电池策略。依据：`BackgroundWifiPolicy`、`WifiRemoteController` 的后台保留与 `AppExitCoordinator` 的 Wi-Fi-only 退出；宿主测试不能验证 Android 锁屏调度/系统网络许可窗口。

### 16. 真机冒烟：0.2.0 Wi-Fi 屏（连接叠层 / 日期列表 / 整日网格 / 选择与悬浮下载）
- 做什么：装新版后验证：①设备页点“照片传输”→ 全屏叠层 4 段环依次走 启动相机 Wi-Fi / 允许连接 / 连接相机 / 读取照片目录，卡片尺寸与按钮位置全程不跳变，取消即结束会话回设备页；成功短暂绿环后自动进日期列表；失败红色图标无底色、无帮助/重试，轻触/返回/5 秒自动回设备页；拒绝附近设备权限时出现“去授权”且不自动关闭；②日期列表：倒序、未知日期最后、副标题只有星期（无今天/昨天）、未知日期无副标题、张数；刷新图标读取中转圈；返回=结束 Wi-Fi 回设备页（蓝牙不断）；横屏两列、近方形限宽；③整日网格：无分页栏，长日（>60 张）一次连续滚动，每格为固定正方形深色近黑框（主题 token，亮暗主题一致），照片按原比例完整缩放居中（**不裁剪**：相机 160×120 缩略图可能自带黑边——此为推断、未经真机确认；黑边与近黑框融为一体，无需核对无黑边；前 3 张缩略图的 AlphaThumb 日志含解码后宽高与近黑边缘行列数，供真机确认黑边是否在相机缩略图像素里），四角：左上放大镜、右上文件大小（无单位 MB：<10 一位小数、≥10 取整，均从原始字节只做一次 half-up 舍入，<0.1 显示“<0.1”；JPG-only 为 JPG 大小、Original 为各文件之和，RAW-only 在 JPG-only 下显示 RAW 大小并带警示图标，但该 RAW 已被手动保存时显示“已保存”；候选文件全部已保存才显示“已保存”，存在超限且无可下载文件时显示“超过大小上限，将跳过”（超限优先，与文件顺序无关），均不显示大小）、左下格式、右下复选框，放大镜/复选框不触发点格子，`adb logcat -s AlphaThumb` 只见可见+两行余量的 batch，快速滑动不扫整日、静置不全量下载；视图选项（grid_view 图标）小/中/大=4/3/2 列，菜单为玻璃弹层+玻璃开关，仅 大小/文件格式/文件大小 三项，默认关、重启后保持；从 0.2.0 升级（存过保护开关）不崩溃；④长按进选择，长按拖动连续范围、反向拖回恢复；顶栏“已选 N 张”+✕；悬浮“下载 N 张 JPG（x MB）”为玻璃主按钮（无深色粗外圈），按钮金额与各格标签按同一 saved-aware 计划计算（JVM 测试只保证字节口径一致；可见数字各自舍入，不保证逐项相加等于已舍入的总数；真机上仍需目视复核），按设置格式直接下载（JPG 跳过无 JPG 并汇总、JPG + RAW 全部原始文件；0.3.1 默认"每次询问"先弹格式选择，见 #37）；跨日选择保留并一次下载；下载中底部玻璃进度条可取消，完成显示“已下载 N、跳过 M”；下载中按 Home 不断 Wi-Fi（M1 例外）；⑤预览（放大镜）不改选择，返回保留选择；整日内左右翻到首/末张为止。
- 验收：逐项无异常；叠层不跳变；长日滚动不卡顿、内存稳定（缩略图缓存上限 300）；退后台/划卡语义与 M1 一致。
- 依据：wave2/wifi 包（M4/M5/M6）自报需真机验证（Compose 手势/弹层/窗口适配与真实相机目录无法在 JVM 验证）。

### 19. 真机冒烟：0.2.0 照片预览页（M7 全屏查看器）
- 做什么：装新版后验证：①顶栏仅 返回/文件名/拍摄时间（第二行）/“k / N”（该日内）；②底部单层玻璃信息条“格式 · 大小”（无叠加深色标签），大小只出现一次且与网格标签同口径（JPG-only 为 JPG 大小，Original 为全部文件之和），格式只写将被下载的文件（JPG-only 下 J+R 显示“JPG · JPG 大小”，Original 下“JPG + RAW · 总和”），RAW-only 在 JPG-only 下显示“仅 RAW”；③详情抽屉：竖屏从照片下部约 35% 区域（含信息条）上滑或点按信息条呼出不透明深色底部抽屉，在标题行下拉/点遮罩/✕/返回键关闭（返回键先关抽屉），左右翻页不误触发；抽屉依次含拍摄时间、分辨率、相机、镜头、焦距、等效焦距（与焦距不同时）、光圈/快门/ISO/曝光补偿（两列）、各格式大小与右侧直方图，无文件名与“格式”行，读不到的项不出现；扩展 EXIF 与抽屉无关：在某张照片停留约 250ms 后 `adb logcat` 见一次原片头部读取，快速横滑不堆积请求、滑走即取消，下载中不读，开关抽屉不再发起读取；B/C 宽屏右侧面板内容与抽屉一致；设置页已无直方图/拍摄参数/镜头三开关；④单张“下载”为玻璃主按钮（非不透明浅蓝），按设置的下载格式直接下载（"每次询问"见 #37），下载中玻璃进度叠层可取消、横滑与胶片条锁定，完成后按钮变灰“已保存”；JPG-only 下 RAW-only 照片标“仅 RAW”、提示“此照片没有 JPG”并可手动“下载 RAW”；批量下载进行中进入预览按钮禁用并提示；下载中按 Home 不断 Wi-Fi（M1 例外）；⑤右下方形复选框与网格选择共享；胶片条缩略图完整显示不裁剪，返回网格保留选择；⑥胶片条当前项高亮、点击跳转，长日（>100 张）快速拖动 `adb logcat -s AlphaThumb` 只见可见±4 的 batch；⑦窗口三档：竖屏（A）、折叠屏展开/平板近方形（B，右侧 360dp 详情面板）、横屏（C，顶栏叠在图上、底部单行），信息条不拉伸；旋转后停留在同一张；⑧TalkBack：详情抽屉打开时焦点进入抽屉并朗读标题、背后照片/胶片条不可达，关闭（✕/遮罩/返回键）后焦点回到信息条（sharednew 的 androidHostTest 已有 Robolectric + compose ui-test，但本页抽屉的焦点/语义没有对应 Compose 用例，TalkBack 朗读与焦点回位需真机验证）。
- 验收：逐项无异常；预览不重复旋转（EXIF 方向正确）；读取/下载/直方图不阻塞主线程；退后台/划卡语义与 M1 一致。
- 依据：wave3/m7 实现与 sol 对 0c9b324..ef196de 的复审（Compose 手势/窗口适配/真实相机部分读取无法在 JVM 验证）；`docs/wifi-architecture.md` “Live status and photo metadata” 的 Viewer use 段。

### 33. 真机核对：预览页详情抽屉观感与卡顿（批 P）
- 做什么：装含批 P 的版本，竖屏预览页点信息条/上滑打开详情抽屉：①面板不透明深色（#1B1F24），下层信息条/下载按钮/胶片条完全看不到；无顶部拖动横条；“照片详情”在左、✕ 在右且收在面板内；②遮罩淡入 + 面板自下滑入/滑出，无整层闪烁；③内容顺序与两列排版（拍摄参数两列、各格式大小与直方图并排）在 360dp 宽与字体 200% 下不截断、不重叠，尽量不滚动；④读取中占位行与真值等高，数据到达时无明显跳动；⑤首次打开与第二次打开的顺滑度对比（配合 #34 的帧日志）。
- 验收：观感可接受；不满意项在 `WifiPhotoViewer.kt` 的抽屉参数（`VIEWER_DETAILS_ENTER_MILLIS`、行高、直方图尺寸）或 `viewerSheet` token 调整，`PaletteContrastTest` 仍全过。
- 依据：批 P 只能在 JVM 验证重组范围与读取时机（`ViewerDetailsDrawerComposeTest`），渲染与帧耗时需真机。

### 34. 真机读取预览页抽屉帧耗时日志（AlphaViewer）
- 做什么：用法——打开预览页，点信息条打开详情抽屉，等约 1 秒或关闭；在 设置 → 运行日志（INFO 默认可见）或 `adb logcat -s AlphaViewer` 看到一行 `details open first=<是否本进程首次> end=<timer|closed|reopened|left> frames=… janky=…(>刷新周期ms) worst=…ms p90=…ms worst[input anim layout draw sync command swap gpu]`。采集只在抽屉打开到入场动画结束后 600ms（或提前关闭）之间启用（`app/.../ui/device/ViewerFrameMetrics.kt`）。冷启动后首次打开、再次打开、杀进程重开各记一次，与 #35 安装 profile 前后对比。
- 验收：记录各场景的 frames/janky/worst/p90；若首次打开最差帧仍明显（例如 >50ms），按 worst[] 中占比最大的阶段（layout/draw/command/gpu）决定下一步。
- 依据：批 P 新增诊断；JVM 无法测帧耗时。

### 35. 真机核对 Baseline Profile 效果
- 做什么：release APK 已含 `assets/dexopt/baseline.prof(m)`（手写通配规则 `app/src/main/baseline-prof.txt`，AGP 展开 + R8 改写后编译；androidx.profileinstaller 1.4.0 已随 Compose 依赖进入 release）。侧载安装后 profile 由 profileinstaller 在首次启动后写入，真正生效要等系统后台 dexopt（空闲+充电）；要立即生效可 `adb shell cmd package compile -m speed-profile -f cn.vectory.alphagps`。核对：`adb shell dumpsys package dexopt | grep -A3 cn.vectory.alphagps` 显示 speed-profile；对比生效前后 #34 日志中“首次打开”的 worst/p90。
- 验收：有生效前后的对比数据；若无改善，记录数据后再评估规则范围（不盲目扩大）。
- 依据：本机无法验证首次打开的实际改善（只验证了规则进入合并/编译产物与 APK）。

### 23. 真机核对：0.3.0 B0 玻璃材质与新组件
- 做什么：装含 B0 的版本（亮/暗各一遍，Android 12 与 14+ 各一台）打开调试预览或已迁移页面，核对：①背景彩色光斑可见但不抢眼；卡片/浮条/对话框能明显看出毛玻璃（双向边缘光、顶部高光带、双层阴影、细噪点），文字清晰；②API 31/32 低端机长列表滚动不掉帧（噪点 + 每卡片模糊）；③对话框/菜单/面板/Snackbar 背后的页面模糊随滚动刷新；④动态字体 200% 下 GlassSegmented/StatusBadge/GlassTopBar 标题不截断；⑤TalkBack：Snackbar 播报、RevealValueRow 状态、图标长按 tooltip 不被朗读；⑥暗色开关/复选框选中态（浅蓝底 + 深蓝滑块/勾）观感可接受。⑦（页面迁移收尾并入）设备详情标题改左对齐后观感；⑧Welcome 页：`WelcomeScreenTest` 几何断言（375×627 slide 不滚动；412×860 标题与 slide 顶部 >80dp；该 androidTest 目前仅编译、未运行，需在设备/模拟器上跑一遍）；⑨设置页单选行与开关行观感：`GlassRadio` 视觉圈 20dp，设置页由整行 `SharedRadioOption`（≥48dp、整行 selectable，`GlassRadio(onClick = null)`）承担点击，独立使用的 `GlassRadio(onClick ≠ null)` 点击区为 48dp——核对整行点按、选中圈外观与 `GlassSwitch` 行高一致；⑩弹出菜单内 `GlassSegmented` 宽度；⑪网格复选框在很亮缩略图上的可见性；⑫预览页下载按钮对比度（见 #26）；⑬排障页返回按钮无障碍标签由 Close 变为 Back（TalkBack 朗读）；⑭信息值字号 14→15 后各行/弹窗折行；⑮`LocationPermissionBoxTest` 使用已弃用的 `createComposeRule`（既有编译警告，换成新 API 或保留并记录）；⑯TalkBack 聚焦 `GlassTextField`（重命名相机、手动加入 Wi-Fi 的 SSID/密码、IP）读出标签名称、空值时 placeholder 提示、错误时错误文本，且标签/说明不重复朗读；在设备/模拟器上跑 `GlassTextFieldSemanticsTest`（目前仅编译）；⑰弹层焦点：外接键盘/方向键打开网格"视图选项"弹层与各对话框/底部面板，焦点进入弹层、Tab/方向键到不了背后页面、关闭后回到打开它的按钮（触屏关闭后不会强制聚焦 opener；全局焦点框/IME/TalkBack 待设备验证）；在设备/模拟器上跑 `GlassOverlayFocusTest`（目前仅编译）；⑱字体 200% 下网格"视图选项"弹层（固定 248dp 宽）内的 `GlassSegmented`（小/中/大）不截断。
- 验收：不满意的项按 `docs/glass-material-notes.md` 的参数说明调 token，调后 `PaletteContrastTest` 仍全部通过（不放宽阈值）。
- 依据：B0 只能在 JVM 做对比度矩阵与纯逻辑测试，渲染观感/性能/无障碍需真机（`docs/glass-material-notes.md` §7）；⑦–⑮ 来自 P1–P3 页面迁移批次的自报（`docs/ui-audit-2026-11.md`）；⑯–⑱ 来自 0.3.0 样式统一复审（sharednew androidHostTest 已有 Robolectric + compose ui-test（`sharednew/build.gradle.kts`），弹层焦点已有 JVM 真实 Compose 用例（`OverlayFocusComposeTest`）；其余语义/TalkBack、以及设备上的 Compose 层测试在 `app/src/androidTest/.../ui/components/`，仅编译、待真机跑）。

### 36. 真机核对：设备列表 ⓘ、相机信息弹窗与设备页状态卡（批 Q）
- 做什么：装含批 Q 的版本（亮/暗各一遍）核对：①设备列表：卡片无垃圾桶/箭头（左滑删除已移除），点卡片任意处进入设备页，右端 ⓘ（48dp）只打开“相机信息”弹窗、不进入设备页；对**未连接/不在范围**的相机，弹窗内重命名、保持活跃、连接/取消、移除（二次确认）都可用，移除后列表立即刷新、弹窗关闭；从列表断开（二次确认）后再进设备页仍保持断开；旋转屏幕弹窗保持；②弹窗：名称 + ✎ 在左，连接控制胶囊在右（已连接=断开、连接中=取消、未连接=连接），超长名称省略号不挤压按钮；无蓝牙地址行；状态行左侧无对号；“自动校时与区域调整”一行，相机关掉其一时显示哪项未启用；③设备页状态卡：标题下正常无副标题，相机关闭位置联动/位置写入失败/时间校正失败或被拒时出现一行琥珀色说明；右侧蓝牙图标为品牌蓝（亮 #0082FC / 暗 #4DA6FF，目视可辨）、外环弧长随信号档 100/75/50/25%（每 10 秒更新，切换不闪烁），未连接灰色图标无环；电量块（横向电池 + 百分比）在用过照片传输后出现，>30 分钟灰显并带“x 分钟前获取”，从未读到或 >24 小时整块隐藏；④位置传输三格：定位信号显示 `±N m`（强 ≤10 绿、≤30 琥珀、>30 红），室内/关定位/超过 2 分钟无新定位显示“—”；定位写入计数；最近写入；⑤TalkBack：列表卡片读名称+状态并带“打开相机页”操作，ⓘ 读“相机信息：<名称>”；状态卡读标题与异常说明、蓝牙信号档、电量。
- 验收：逐项无异常；设备页可见时 RSSI 仍按 10 秒更新（`acquireSignalMonitoring` 租约），定位精度暴露不改变 GPS 发送节奏（对照 `adb logcat` 写入间隔与改动前一致）。
- 依据：批 Q 提交（`SharedDeviceList.kt`、`AssociatedDevicesList.kt` 的 `DeviceInfoDialogHost`、`DeviceInfoDialogs.kt`、`DeviceStatusVisuals.kt`、`LocationTransmissionManager.latestFix` → `CameraLiveStatusHub.phoneLocation`）；JVM 已覆盖：`DeviceListInfoComposeTest`（Robolectric：整卡点击/ⓘ 互不触发、弹窗按状态切换按钮）、`DeviceStatusCardLogicTest`、`DeviceIndicatorsTest`、`PaletteContrastTest.bluetoothBlueIconReadableOnCardGlass`；渲染观感、Canvas 圆环/电池、TalkBack 朗读与真实 CDM/BLE 行为需真机。

### 37. 真机核对：设置页分组与下载格式“每次询问”弹层（批 R）
- 做什么：装新版后验证：①设置页四组（通用：应用启用/语言/触感反馈/重新显示欢迎页；照片传输：下载格式；通知与后台：事件音效/电池与后台/开机自启；帮助与诊断：帮助/运行日志/更新日志），组标题为小号次要色文字、一组一张玻璃卡、无分隔线，亮暗主题与大字体下不截断；设置页已无日志级别；②运行日志页顶栏下“日志级别”行点开页内玻璃菜单（非系统弹窗），选中项有勾，切换后立即生效（VERBOSE/DEBUG 时新日志立刻出现）且重进应用保持，复制/清空不受影响；③下载格式四选一：从 0.3.0 升级的用户保持原选择（原片→JPG + RAW、仅 JPG→JPG），新装默认“每次询问”；④“每次询问”：预览页 JPG+RAW 照片点“下载”弹出页内选择（仅列该张实有格式与大小，默认选中 JPG，取消不下载），只有一种格式时直接下载；信息条显示“JPG x MB / RAW y MB”，网格格子不显示大小；选择模式悬浮按钮“下载所选 N 张”弹出 JPG / RAW / JPG + RAW 三项，各显示文件数、大小与将跳过数量，选区里没有 RAW 时 RAW 置灰，确认后进度/汇总/失败即停/重试剩余与固定格式一致；⑤RAW：只下 ARW，无 RAW 的照片在批量里跳过并汇总“N 个无 RAW”、格子带警示图标、预览页信息条“仅 JPG”并提示“此照片没有 RAW”，可手动“下载 JPG”；⑥TalkBack：弹层打开时焦点进入弹层、背后不可达，关闭后回到“下载”按钮；日志级别行朗读“日志级别，INFO”。
- 验收：逐项无异常；弹层与菜单均为页内叠层，没有系统窗口。
- 依据：批 R 提交（`WifiFormatPolicy.kt` 的 `WifiDownloadFormatSetting`/`RawOnly`/`NoRaw`/`batchFormatChoices`/`singleFormatChoices`，`WifiFormatChooser.kt`，`WifiPhotoViewer.kt` 的 `ViewerDownloadArea`，`WifiPhotoBrowser.kt`，`SharedLogViewerScreen.kt` 的 `LogLevelSelector`，`SettingsScreen.kt`，`PreferencesManager.wifiDownloadFormat`）；JVM 已覆盖：`WifiFormatPolicyTest`、`WifiPhotoViewerModelTest`、`WifiRemoteControllerTest`（RAW/询问批量）、`WifiFormatChooserTest`、`LogLevelOptionsTest`、`WifiDownloadFormatPreferencesTest`（app，含旧值迁移），`WifiFormatChooserComposeTest`（Robolectric：预览页询问弹层的选择/取消/单格式直下与批量行文案/置灰）；批量悬浮按钮→弹层的接线、日志级别菜单、设置页布局与焦点回位没有 Compose 用例，需真机。另：`ViewerDetailsDrawerComposeTest.kt:161-218` 用 `Dispatchers.Default` + `waitUntil` 5s + 两处 `Thread.sleep(600)`（等待“不再发生读取/重组”的负向断言窗口），建议改为统一测试调度器（可控虚拟时间）或显式完成信号；`WifiFormatChooserComposeTest` 未覆盖网格“下载所选 N 张”→chooser 的接线（上文已承认）。

## P2

### 13. 恢复遥控拍摄功能（后续计划，暂不实现）
- 做什么：遥控与"照片传输"做平级入口。**协议层整体保留**（BLE FF01/FF02 + RemoteControlCoordinator + `triggerShutterSequence`、Wi-Fi capture/liveview、DB `remoteControlEnabled`），UI 入口已移除。研究与设计见 `docs/design/remote-control/`（`research.md`、`remote-control-design.pdf`）。拆分：R1 蓝牙单次拍摄恢复（入口/状态/防重复/离页释放/与位置联动互斥提示）；R2 Wi-Fi 取景+单拍（按用途建会话，当前 Android `AppServices.kt:72` 固定 `browseOnly=true` 是取景被关的原因，并非 v4 不支持 HTTP 取景）；R3 参数面板（缺 access/form 完整属性模型与写入读回链，先单机型单属性白名单）；R4 独立对焦。后台语义沿用 v3：后台/划卡结束 Wi-Fi，蓝牙+GPS 常驻（有用户指定下载时保留 Wi-Fi 至完成）。
- 验收：设计评审后分阶段实施；恢复入口不得影响 GPS 写入/时间校正/照片下载；保留的协议层测试继续通过；需实机矩阵（型号/固件/协议版本/GPS 并用/单拍释放/取景首帧/浏览切换）。
- 待用户决策/实机核实：①第一版做到 R1 还是 R1+R2；②ILCE-7M4 上蓝牙遥控是否与位置联动冲突（官方只明确 α6400 互斥）；③v4 取景能否稳定出帧；④参数面板与独立对焦是否后置。
- 依据：用户 2026-11-10 决定；astra 研究报告。

### 25. 日志页 snackbar 是否遮挡日志面板底部（真机确认）
- 做什么：日志页 `GlassSnackbarHost` 作为页内叠层浮在底部，估计会盖住日志面板最后约 70dp。真机（竖屏/横屏、大字体）触发复制/清空等提示，看是否遮住最后几行日志且无法滚出；若遮挡，给日志列表加底部 contentPadding（或提示出现时留出空间）。
- 验收：提示出现期间最后一行日志仍可见/可滚动到；无遮挡则删除本条。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/logs/SharedLogViewerScreen.kt:116`（`GlassSnackbarHost(snackbar)`）。

### 26. 预览页下载按钮在竖屏不是真毛玻璃
- 做什么：竖屏下载按钮位于胶片条下方的纯黑背景上，背后没有可模糊内容，所以呈现为深色着色胶囊而非毛玻璃。要真毛玻璃需让按钮浮在照片区域之上（布局改动：下载区叠在照片底部，照片需为 `hazeSource`），并核对与信息条/抽屉手势区的关系；不改则在设计里确认现状可接受。
- 验收：竖屏下载按钮取样到照片并呈现玻璃（或文档确认接受现状）；对比度仍过 `PaletteContrastTest`；翻页/下载状态不受影响；B/C 窗口不回归。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiPhotoViewer.kt` 竖屏下载行（`ViewerDownloadArea`，约 :456、:921）；预览根背景 `:372`。

### 27. 页内弹层焦点：真机验证与剩余缺口
- 做什么：组件级焦点已实现（`GlassOverlayFocus.kt`）：Popover/Menu/`CapsuleDialog`/`GlassBottomSheet` 打开时焦点移入面板容器；**多层模态叠层仅最顶层可获焦**——底页与被上层模态盖住的下层弹层共用同一层门控（焦点组拒绝进入 + 读屏隐藏，`overlayLayerGate`/`layerCovered`）；关闭**逐层恢复**（`OverlayFocusMemory` 栈）：关上层回下层的锚点/打开前**焦点路径**的末端（经叠层系统自有的各焦点层逐级保存：面板、对话框/底部面板内容、`ScrollbarLazyColumn`，即 `overlayFocusLevel`）/面板容器，关最后一层回 `glassAnchor` 或打开前底页焦点项，菜单→对话框同帧替换时对话框接管菜单的返回目标，乱序关闭（先关下层）时上层接管下层的返回目标；叠层 entry 带生命周期（`register`/`unregister`、`isLive`），已关闭 entry 的迟到面板 effect 不入栈、不请求焦点，settle 时剪掉无对应 close 的孤儿层；`GlassPopover`/`GlassMenu` 可传 `paneTitle`。JVM 上已真跑（Robolectric，`sharednew/src/androidHostTest/.../glass/OverlayFocusComposeTest.kt`）：对话框上再开无锚点对话框后回原按钮/同级按钮不混淆/对话框滚动内容内条目、下层 `GlassBottomSheet` 滚动区内叶子与下层 `GlassPopover` 内叶子（上层无锚点对话框关闭后回到该叶子）、页面列表行、菜单/Popover 回锚点、触屏关闭后不会强制聚焦 opener（Robolectric 里 always-focusable 的 page box 会获初始焦点，用例只断言 opener 未获焦；全局焦点框/IME/TalkBack 待设备验证）、注册后面板 effect 前即关闭不留栈帧不抢焦点；这些用例同时读真实 `OverlayFocusMemory.lastReturn`（只读观测，生产不读取）断言返回结果种类：下层原叶子恢复=Saved、锚点=Anchor、调用方自建焦点组降级=Container；时序模型见 `OverlayHostLifecycleTest`（用 FakeTarget，不执行真实恢复闭包）。剩余：①在设备/模拟器上跑 `app/src/androidTest/.../ui/components/GlassOverlayFocusTest.kt`（单菜单、对话框内菜单两层叠加、菜单→对话框替换、先关下层、无 `GlassBackground` 回退；目前仅编译）；②外接键盘 Tab/方向键与 TalkBack 逐层实测：对话框内再开菜单/确认框时，Tab/方向键与读屏滑动都到不了下层对话框和底页，关上层回到下层打开它的按钮、再关回到页面上的原按钮；**对话框内按钮/输入框有焦点时再开无锚点对话框，关闭后回到该按钮/输入框**（不是下层面板，含输入框的 IME 行为）；触屏关闭后不会强制聚焦 opener；全局焦点框/IME/TalkBack 待设备验证（同 #23 ⑰）；**打开后立即关闭（注册后面板首帧前即关闭，如快速双击或打开即被状态关闭）不闪焦点、关闭后焦点仍在原处**；③焦点落在面板容器而非首个条目，键盘用户需多按一次——真机评估是否改为直接聚焦首个可聚焦条目（Compose 对 focusGroup 的 requestFocus 行为需真机确认）；④面板内 Tab 到末项后不循环（下层拒绝进入，焦点停住），评估是否需要循环；⑤非模态叠层（Snackbar、tooltip）在最顶层模态之上时仍可获焦，在其下方则与下层一样被隔离——真机确认 Snackbar 动作按钮在对话框打开时的可达性可接受。
- 已知剩余缺口（接受，待评估）：焦点项位于**调用方自建的焦点组**之下（对话框内容里调用方自己的 `verticalScroll`/`LazyColumn`、页面上非 `ScrollbarLazyColumn` 的滚动容器）时，只能恢复到最近一级叠层自有焦点层（对话框则落到面板容器，返回结果如实报 Container，不冒充 Saved：落点（面板容器）与不落到错误条目由真实 Compose 用例 `OverlayFocusComposeTest.anItemBelowAFocusGroupTheOverlayDoesNotOwnFallsBackToTheLowerPanelNotAWrongItem` 覆盖，结果种类 Container 由同用例对真实节点恢复闭包返回值的断言覆盖）；如需覆盖，给那些容器加 `overlayFocusLevel()`，或等 Compose 的 `isFocusRestorationEnabled` 转正。
- **下次 Compose 升级前必须处理或加升级回归门禁**：`tryFocus` 仍按异常文案（`DETACHED_FOCUS_TARGET`："called on an unattached node"/"FocusRequester is not initialized"）识别"焦点目标已 detach"作为第二道防线；第一道是 `OverlayFocusNode` 的 `isAttached` 与 `OverlayFocusHandle` 的绑定（调用前排除已退出目标）。升级时核对这两条文案是否仍存在，或改为纯生命周期判定后删除 matcher；不得退回整体 catch `IllegalStateException`。同时复核 `saveFocusedChild`/`restoreFocusedChild` 语义（1.12.1 只存直接孩子）与 `OverlayFocusComposeTest` 全部通过。
- 现状说明：无 `GlassBackground` 时 `CapsuleDialog` 走平台 `Dialog` 回退，新焦点逻辑**不作用于回退路径**（无叠层 entry → 面板不加 focusable、不请求焦点、不经焦点记忆），保持 0.2.1 行为，焦点由窗口管理（`GlassOverlayFocusTest.dialogWithoutGlassBackgroundKeepsTheWindowFallbackFocus` 待真机跑）。
- 验收：①②通过；③④⑤与已知剩余缺口有结论（改或记录为接受）；不引入系统窗口；`OverlayFocusMemoryTest`、`OverlayHostLifecycleTest`、`OverlayFocusComposeTest` 仍通过。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/GlassOverlayFocus.kt`（焦点路径、`OverlayFocusNode`/`OverlayFocusHandle`）、`GlassOverlayHost.kt`（层门控、entry 生命周期、栈式返回）、`GlassBackground.kt`（底页门控 + settle）、`GlassPopover.kt`；基线行为为叠加的 `PopupProperties(focusable = true)` / `Dialog` 窗口。

### 38. 预览页详情抽屉不是完整键盘模态（沿袭 0.3.0，非 0.3.1 回归）
- 做什么：竖屏详情抽屉对硬键盘/DPAD 用户不是完整模态：①`WifiPhotoViewer.kt:484-504`（`ViewerSemanticsGate`）只用 `clearAndSetSemantics` 对读屏隐藏背景，没有焦点闸，Tab/方向键仍可进入背后的照片/信息条/下载/胶片条；②`WifiPhotoViewer.kt:797-865`：关闭时 `onClose` 立即 `drawer.hide()` 并 `stripFocus.requestFocus()`，但 200ms 退出动画（`VIEWER_DETAILS_EXIT_MILLIS`）期间 sheet 仍在组合内、仍 `focusable()` 且带 `paneTitle` 语义；`NavigationBackHandler` 的 `isBackEnabled = open`，动画期间 Back 已不被抽屉接管；③快速重开（200ms 内再次打开）不会重启 `LaunchedEffect(Unit)` 的 sheet 焦点请求（sheet 未离开组合），焦点留在信息条/背景。
- 复现：硬键盘/DPAD 打开详情后遍历焦点（应进不了背后控件）；关闭动画期间按 Tab/Back；关闭后 200ms 内重开，看焦点是否进入抽屉。
- 建议：把可交互模态的 presence、背景 focus 与 semantics 隐藏、Back 启用统一到同一个状态（含退出动画期间），抽屉彻底离场后再恢复焦点到信息条；重开时重新请求 sheet 焦点（以 open 为 key）；补暂停 Compose clock 的退出动画/重开测试（参考 `overlayLayerGate`/`OverlayFocusComposeTest` 的做法）。
- 验收：键盘焦点在抽屉打开及退出动画期间到不了背景；退出动画结束后焦点回信息条；200ms 内重开焦点进入抽屉；Back 在退出动画期间行为明确；有上述 Compose 用例并随 `testAndroidHostTest` 通过。
- 依据：sol 对 23aafd0..594605b 的审阅（既有 P2，沿袭 0.3.0）；`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiPhotoViewer.kt`；`GlassOverlayFocus.kt` 的 `overlayLayerGate`；TalkBack 侧见 #19 ⑧。

## P3

### 28. 网格格子放大镜触摸区与 GlassTileAction 统一
- 做什么：网格格子的放大镜保留自有 40dp 触摸区（`TILE_ACTION_DP=40`，格子角落空间有限），而 `GlassTileAction` 强制 48dp 点击区。给 `GlassTileAction` 加 `touchTarget` 参数（≥40dp 钳制，同 `GlassIconButton`），网格放大镜/复选框改用它，删除自绘实现。
- 验收：网格放大镜与复选框都用 `GlassTileAction(touchTarget = …)`；`WifiBrowserModelTest` 的触摸区/角标几何断言仍通过；触摸区不重叠、不触发点格子。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiPhotoBrowser.kt:644`（`TileActionSize`）、`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiBrowserModel.kt:138`；`sharednew/src/commonTest/.../remote/WifiBrowserModelTest.kt:125-133`（`TILE_ACTION_DP`）；`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/GlassSelectionControls.kt:205`。

### 29. 预览页背景色抽 viewerBackground token
- 做什么：预览页背景 `Color.Black` 抽成 `viewerBackground` token（`ui/theme/Color.kt`，`AlphaColors`），预览页与相关 Viewer 玻璃/对比度测试引用它。
- 验收：`WifiPhotoViewer.kt` 无字面 `Color.Black` 背景；`PaletteContrastTest` 的 Viewer 背景取值引用 token，测试通过。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiPhotoViewer.kt:476`（`ViewerBody`）。

### 30. Wi-Fi 连接叠层分段 StepRing 保留自定义
- 做什么：连接叠层用自绘分段 `StepRing` 表达 4 步当前所处位置，不是 `GlassProgress*`（后者不表达分段）。如需统一进度控件，需先设计分段进度形态（`GlassProgressSteps` 之类）再迁移；否则保持现状并在设计文档确认。
- 验收：要么新增统一分段控件并替换 `StepRing`（保持“4 段依次走、成功绿环、失败红色图标”表现），要么文档确认保留后删除本条。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiConnectionOverlay.kt:240-262`。

### 31. 顶栏玻璃目前不真正“穿过内容”
- 做什么：`GlassTopBar` 的玻璃取样源是页面背景，内容按 Scaffold padding 排在栏下方，滚动内容并不从栏后穿过（`docs/glass-material-notes.md` §5.1 的结构改动未做）。让内容铺满全屏、栏悬浮其上（内容加顶部 contentPadding，栏取样到滚动内容），各页（`GlassPageScaffold`、设备页、Wi-Fi 屏）核对首屏位置与滚动。
- 验收：滚动时内容从顶栏后穿过并被模糊；首屏内容不被栏遮住；各页对话框/叠层宿主行为不回归。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/GlassTopBar.kt:55`、`GlassPageScaffold`；`docs/glass-material-notes.md` §5.1。

### 32. 排障指南“远程提示”步骤改用 GlassWarningCard
- 做什么：`guide_location_link_step_remote_note` 是警示性步骤，目前按普通步骤呈现，可用 `GlassWarningCard` 展示；需要新增标题字符串（`values` 与 `values-zh-rCN` 同写）。
- 验收：该步骤以警示卡片呈现且有标题；两种语言字符串齐全；排障页其余步骤不变。
- 依据：`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/help/TroubleshootingGuideScreen.kt:110`；`sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/components/glass/GlassStatus.kt:77`（`GlassWarningCard`）。

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
- 做什么：数据层已实现（best-effort）：Wi-Fi/PTP 会话就绪后读 1 次，标准 `0x5001`（DeviceInfo 的 DevicePropertiesSupported 列出时走 GetDevicePropValue 0x1015）否则 Sony 扩展 `0xD218`（ExtDeviceInfo 列出时走 0x9209），结果经 `CameraLiveStatus.battery`（{percent, obtainedAtMillis, cameraId}，`freshness`：≤30min 新鲜 / ≤24h 灰显带时间 / 更久显示"—"）暴露。依据仅来自 libgphoto2 的 USB Sony PTP 属性表（0x5001 UINT8 0..100；0xD218 INT8 −1=未知），**Wi-Fi 传输模式下相机是否列出/允许读取尚未验证**。待办：①实机（至少 ILCE-7M4）抓 `Wi-Fi PTP` 日志确认 DeviceInfo 的 DevicePropertiesSupported 与 ExtDeviceInfo 是否含 0x5001/0xD218、读数是否与相机屏幕一致；②若两者都不可读，删除 `SonyBatteryReader` 并在本条记录结论（设备页状态卡按缓存显示：无缓存或 >24 小时整块隐藏，>30 分钟灰显并带获取时间，不建 Wi-Fi 会话）。
- 验收：实机读数与相机一致或确认不可得。
- 依据：`sharednew/.../remote/wifi/SonyBatteryReader.kt`；libgphoto2 `camlibs/ptp2/ptp.h`（`PTP_DPC_BatteryLevel`、`PTP_DPC_SONY_BatteryLevel`）；ui-v3 设计"待实机核实"。

### 15. 实机核实：M3 状态数据层与 M8 照片元数据（M8 已由预览页接入）
- 做什么：①RSSI：`readRemoteRssi` 每 10s 一次在 Transmitting 时是否影响 GPS 写入/耗电，Android 回调 status 是否恒为 0；②EXIF：`GetPartialObject(0x101b)`/`0x9211`/catalog `0x923d` 读原片前 64KB 在 ILCE-7M4/其它机型是否稳定，EXIF 是否在首个 APP1 且 ≤64KB（否则退化为 Unavailable，需评估读 preview 字节）；③v4 目录记录 `take(8)`（评分/保护标志，保护/评分显示功能已移除，仅跳过）后的布局未知，v4 路径目前不暴露分辨率（`PhotoMetadata.width/height` 为 null），待抓包确定字节布局后再解析；v3 路径取自 PTP ObjectInfo（ImagePixWidth@26、ImagePixHeight@30）；④GPS 统计：设备页「定位写入」（原「已写入」）= GATT 写确认（相机 ATT 应答）次数，「入队」= 已入队发送次数，两者在断连/超时时可能不等。
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
