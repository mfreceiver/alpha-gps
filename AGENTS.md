# AGENTS.md（alpha-gps 定制版）

## 项目定位与名称规范
- 产品名一律 **AlphaGPS**（无连字符，A/G/P/S 大写，显示时区分大小写）。保持小写不动的只有：包名 `cn.vectory.alphagps`、仓库名 `alphagps`、文件名 `alpha-gps-*`。
- 定位：向索尼 Alpha 相机经蓝牙写入 GPS 位置与时间 + 经相机 Wi-Fi 浏览/下载照片的个人定制版；**不涉及任何应用市场分发**（无 Google Play / F-Droid / App Store / Obtainium，商店徽章与 fastlane/website 素材已删除）。
- 崩溃上报（Sentry）不用：W2 移除后应用完全不收集、不上传任何数据（见 privacy.md）。
- `docs/brand/` 项目品牌图标（SVG 源，含导出方法）；`docs/design/` 界面设计方案（索引 `docs/design/README.md`，当前生效版本 v3）。

## 待办纪律
- 待办唯一入口：`docs/todo.md`，**只放当前待完成**的事项。
- 完成一项，在**同一次提交**中把对应条目从文件里删除；不打勾保留，不建“已完成”区。完成历史靠 git 记录。
- 新发现的事项当天写入（要做什么 / 验收标准 / 依据位置），按 P1>P2>P3 排序。
- 开始一次工作前先读 `docs/todo.md`；结束时核对其中是否有已完成或已过期的条目。

## 文档目录
- `docs/todo.md` 待办；`docs/wifi-architecture.md` Wi-Fi 架构；`docs/design/` 界面设计方案（索引 `docs/design/README.md`，当前生效版本 v3）。设计里的待开发拆分不复制进 todo，开发时再按上面的纪律入 todo。

## 构建与发版要点
- 开发分支即 `main`（不再使用独立功能分支）；主发布仓库地址（host/仓库路径/本机远端名）**不入库**，见本机 `scripts/release.local.env`（已 .gitignore，格式示例见入库的 `scripts/release.local.env.example`，也可用环境变量 `ALPHAGPS_PRIMARY_HOST/REPO/REMOTE`）；公开地址只写 GitHub：https://github.com/mfreceiver/alpha-gps（Issues：/issues）。**任何入库文件/代码/注释/测试不得出现主发布仓库的主机名、端口、仓库路径或其产品名。** `origin` 是上游 Saschl/alpha-gps，仅用于 fetch，不再全量跟踪上游：只在需要时 `git fetch origin`，对比差异后挑选有价值的改动（`git cherry-pick`/手工移植）到 `main`，不做整体 merge/rebase。
- 包名 `cn.vectory.alphagps`（与上游 `com.saschl.cameragps` 共存），签名复用统一密钥，证书 SHA256 `18ff24c5...97d7`。发版脚本默认 keystore 为 `$HOME/.config/alphagps/signing/release.jks`、alias `release`；本机位置/alias 不同时在 `release.local.env`（或同名环境变量）设可选键 `ALPHAGPS_SIGNING_KEYSTORE`（绝对路径或 `~/` 开头，仅此一种展开）/ `ALPHAGPS_SIGNING_ALIAS`（值白名单校验、非法 fail-closed；口令仍来自 `~/.gradle/gradle.properties`，不入此文件）。
- 发版：`scripts/release_build.sh <X.Y.Z>` 构建归档并打 tag，`--publish-only` 推送并发布到主仓库（release.local.env 配置）。**versionCode 只在仓库根 `gradle.properties` 的 `alphagps.versionCode` 一处 bump**；发版脚本对该平面文件做严格解析（key 恰好一行、整行精确匹配 canonical 正整数），断言 APK 并校验相对上一发布 tag 递增（`scripts/release_lib.sh`，不再解析 Kotlin 源码；v0.1.0–v0.1.2 用内置历史表）。发版属对外动作，须用户确认。
- **双源发布（M10）**：`--publish` / `--publish-only` 先发主仓库，**成功后**才做 GitHub 步骤（github.com/mfreceiver/alpha-gps）：推 tag 到本机 GitHub 远端（名由 `release.local.env` 的 `ALPHAGPS_GITHUB_REMOTE` 配置，默认 `github`；不存在则 fail-closed 并提示 `git remote add github https://github.com/mfreceiver/alpha-gps.git`，脚本不自动创建）→ api.github.com 建/复用 release → uploads.github.com 上传 → 下载 `browser_download_url`（手动走一跳到白名单 CDN 域，二跳匿名、不再跟随）重算 sha256 对账。凭据走 `git credential fill`（host=github.com），token 不进日志/argv。选项：`--no-github` 跳过 GitHub 步骤；`--publish-github-only` 只补 GitHub 步骤（同 tag 同名同 sha256 资产跳过、同名不同 sha 中止，幂等）；`--github-tag-only`（默认）只推 tag，`--github-push-branch` 才再推当前分支。**GitHub 步骤会推送 tag（`--github-push-branch` 时再加当前分支），其可达的历史对公开可见。**
- 发版脚本退出码：`0` 成功；`1` 失败（用法/配置/前置/构建/主仓库发布，此时 GitHub 步骤未执行）；`40` GitHub 步骤失败（若出现在主仓库成功之后，主仓库不回滚，修复后 `--publish-github-only` 重跑）。仅 `--publish-github-only` 失败时 40 不涉及主仓库。
- GitHub 目的地规则：本机 GitHub 远端的 fetch 与 push 侧都必须解析到唯一公开 URL，传输钉在 URL 字面量上（见 `rl_gh_verify_remote`）。
- **历史已净化（2026-11）**：自有提交范围的私有主机/仓库/账号标识、`@local`/私有邮箱作者与旧版设计 PDF 已从历史移除（保留包名 `cn.vectory.alphagps`）；tag v0.1.x 已随之迁移，已发布 APK 不变。2026-11 再次改写自有历史（10 个 v0.* tag 哈希随之变化、已发布 APK 不变）：移除了发版脚本及其自测中对另一内部项目的名称/路径/证书 DN 引用（统一换成通用默认值 `alphagps/signing/release.jks`、alias `release`，本机真实路径走上面的可选键）；新增入库内容同样不得出现另一内部项目的名称。**每次向 GitHub 推送前必须对待推 tag/分支的全部可达对象做指纹扫描**（私有主机名、端口、主发布仓库的产品名、私有邮箱、`/home/<user>/` 绝对路径），非空不得推送；新增入库内容也不得带这些标识。
- 发版脚本自测：`scripts/*_test.sh`（无网络、沙箱 + fake curl，不碰真实仓库），改动发版脚本后须全部通过。
- 验证命令：`./gradlew --no-daemon :sharednew:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`。
- 手写 Baseline Profile：`app/src/main/baseline-prof.txt`（HRF 通配规则，AGP `expandReleaseArtProfileWildcards` 展开、R8 改写后编入 APK 的 `assets/dexopt/baseline.prof`；profileinstaller 随 Compose 传递依赖进入 release）。新增热点 UI 包时同步规则。
- 本机 `local.properties` 的 `sdk.dir=$HOME/Android/Sdk` 不入库（已被 .gitignore 忽略）。
- **仅 arm64**：app 与 heif 的 `defaultConfig.ndk.abiFilters` 只含 `arm64-v8a`（native 仅构建/打包该 ABI）；老 32 位（armeabi-v7a）与 x86/x86_64 设备/模拟器不可安装。`scripts/release_assert_apk.sh` 断言 APK `lib/` 下仅含 `arm64-v8a` 且至少一个 `.so`。
- `release_build.sh` 构建前清理 compose 资源生成残留（sharednew/build 与 app/build 的生成/中间 assets 目录），并断言 APK 仅含 en+zh-rCN 的 composeResources（`assets/composeResources/*/values*` ⊆ {values, values-zh-rCN}）。

## Android 后台与退出语义
- Home/锁屏/切换应用/划走任务卡/根页 Back 均不主动断开 BLE/GPS，不停止仍有连接需求的前台服务；设备页用户主动“断开”的持久化意图、二次确认与 presence 退休逻辑保持独立。
- Wi-Fi 照片传输只在应用可见时保留；退后台无用户下载即结束。有已接受的单张/批量下载则保留整个下载任务与网络请求，成功/失败/取消后若仍在后台自动结束；缩略图、预览、结果汇总不算下载。回前台取消待结束，不能自动重开已结束会话。
- 划卡/根页 Back 为明确退出，即使下载中也立即取消并结束 Wi-Fi；不加任何 BLE/GATT 或 FGS 关闭回调。`AppExitGate` 的 owner/epoch 与退休快照反例必须保留。后台下载保留最多 30 分钟（超时记日志并走正常取消清理）；生命周期与真机限制见 `docs/wifi-architecture.md`。
- 应用内通用帮助与运行日志的入口在设置页“帮助与诊断”组（设置总览按 通用 / 照片传输 / 通知与后台 / 帮助与诊断 分组，用 `SharedSettingsGroup`；日志级别在运行日志页顶部，不在设置页）（设备清单顶栏只留设置图标；清单底部“需要帮助？”进入连接故障排查），赞助链接仅保留在关于页（标明受益人为上游作者）；连接故障排查不等同于通用帮助入口。

## UI 组件库用法（设计 v3 + 0.3.0 样式统一）
- 速查（组件签名、示例、旧控件→新组件对照）：`docs/ui-components-cheatsheet.md`；玻璃材质参数与取值依据、Haze 行为验证：`docs/glass-material-notes.md`；规范来源 `docs/ui-audit-2026-11.md`。组件在 `sharednew/.../ui/components/glass/`，token 在 `sharednew/.../ui/theme/`。主题唯一入口 `CameraGpsTheme`（固定蓝色，不用动态色）。
- **文字只用 `AlphaType` 6 档**：Title 22/28 SB、Heading 18/24 SB、Body 15/22、Label 15/20 SB、Caption 13/18、Tag 11/14 SB，日志/许可证用 Mono 12；letterSpacing 0。`MaterialTheme.typography` 槽已映射到同 6 档，但新代码不直接用；过渡别名 `AlphaTextStyles` 已删除（0.3.0）。
- **颜色只用 8 个角色**（`AlphaTheme.colors`）：textPrimary / textSecondary / textMuted / accent / success / warning（文字 `warningText`、图标 `warningIcon`）/ danger / onPhoto（及 onPhotoSecondary、onPhotoMuted）。Viewer 与照片上（GV 在亮暗主题都是深色）的警示/错误用照片专用的 `onPhotoWarning`（浅琥珀 #FFE08A）/ `onPhotoDanger`（浅红 #FFDAD6），**不得**用 `warningText`/`warningIcon`/`danger`/`success`（亮色主题值在深色 Viewer 上仅 ≈1.0–3.3:1）；`GlassIconButton(container = GlassLevel.Viewer)` 的 Danger 自动取 onPhotoDanger。品牌识别色 token `bluetoothBlue`（亮 #0082FC / 暗 #4DA6FF）只用于设备页蓝牙图标，不作状态色。正文 ≥4.5:1，大字/图标/textMuted ≥3:1，由 `PaletteContrastTest` 矩阵守住——不达标调 token，不放宽阈值。
- 预览页竖屏详情抽屉是**不透明**面板（`AlphaTheme.colors.viewerSheet`，非玻璃、不模糊），动画只用遮罩 alpha + 面板 `graphicsLayer` 位移；抽屉开关状态只在抽屉层与语义闸读取（`ViewerDrawerState`/`ViewerBody`），不得在 `WifiPhotoViewer` 页面作用域读取（`ViewerDetailsDrawerComposeTest` 用重组计数守住）。
- **守卫**：UI 目录（`sharednew/.../ui/`、`app/.../cameragps/ui/`，`ui/theme/` 除外）不得出现 `fontSize =`、`MaterialTheme.typography`、`Color.White`、`Color(0x`。`scripts/ui_style_guard.sh` 以 `docs/ui-style-guard-baseline.txt` 为基线只允许下降；迁移完页面后 `--update-baseline` 下调并一起提交，基线清空后改用 `--strict`。改守卫后跑 `scripts/ui_style_guard_test.sh`。
- 每个页面根部用 `GlassBackground { … }`（背景光斑 + 模糊取样源 + 页内叠层宿主）；卡片 / 行用 `GlassCard`/`GlassSurface(level = …)`，按角色选 G1 Card / G2 Float / G3 Modal / GV Viewer；卡片内的玻璃自动降为不模糊的半透明片。照片网格不作模糊取样源（`GlassBackground(contentIsBackdropSource = false)` 或不包进取样区）。
- **页内叠层规则**：菜单、气泡、提示、Snackbar、底部面板、对话框一律走 `GlassOverlayHost`（`GlassMenu`/`GlassPopover`/`GlassSnackbarHost`/`GlassBottomSheet`/`CapsuleDialog`/`GlassIconButton` 自带 tooltip），**不得新开系统窗口**（`Popup`/`Dialog`/`DropdownMenu`/`ModalBottomSheet`/`AlertDialog`/M3 `TooltipBox`）——Haze 跨窗口取样未验证。modal 叠层仅最顶层可获焦（底页与被盖住的下层弹层拒绝焦点且对读屏隐藏），关闭时逐层恢复焦点到下层原按钮/输入框（焦点路径经叠层自有焦点层保存，弹层/页面内列表用 `ScrollbarLazyColumn`；见 `GlassOverlayFocus.kt`、todo #27）。
- 按钮：文字操作一律 `CapsuleButton`（role Primary/Secondary/Tonal/Danger/DangerOutlined/GlassPrimary，`glass = true` 为玻璃变体，文字永远 Label，size 只管高度/圆角/内边距，≥48dp）；纯图标用 `GlassIconButton`（必须给 contentDescription）；信息标签用 `InfoTag`（不可点），状态用 `StatusBadge`/`StatusLine`/`StatusRow`。开关/单选/复选/输入/分段/进度/顶栏用 `GlassSwitch`/`GlassRadio`/`GlassCheckbox`/`GlassTextField`/`GlassSegmented`/`GlassProgress*`/`GlassTopBar`，不用 M3 原生控件。对话框一律 `CapsuleDialog`（取消左、确认右，危险操作 `destructive = true`）。不用分隔线。
- 窗口适配用 `windowClassOf(widthDp, heightDp)` / `currentWindowClass()`（A 竖长 / B 近方形 / C 横屏）。

## 语言资源纪律
- 仅支持英文基线（`values/`）+ 简体中文（`values-zh-rCN/`）；其余语言已移除，`app/build.gradle.kts` 用 `androidResources.localeFilters`（en、zh-rCN）屏蔽依赖库语言。
- 新增文案只需写 `values/` 与 `values-zh-rCN/` 两处（app 的 `app/src/main/res` 与 sharednew 的 `commonMain/composeResources` 同理），不要新增其它语言目录。
- 删减语言后必须清理 sharednew/build 的 compose 资源生成目录再验证 APK，增量构建会残留旧语言。
