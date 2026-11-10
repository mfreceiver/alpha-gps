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
- 开发分支 `feature/browse-only-download`；主发布仓库地址（host/仓库路径/本机远端名）**不入库**，见本机 `scripts/release.local.env`（已 .gitignore，格式示例见入库的 `scripts/release.local.env.example`，也可用环境变量 `ALPHAGPS_PRIMARY_HOST/REPO/REMOTE`）；公开地址只写 GitHub：https://github.com/mfreceiver/alpha-gps（Issues：/issues）。**任何入库文件/代码/注释/测试不得出现主发布仓库的主机名、端口、仓库路径或其产品名。** `origin` 是上游 Saschl/alpha-gps，仅用于 fetch，同步方式：fetch → main 快进 → merge（不 rebase）进功能分支。
- 包名 `cn.vectory.alphagps`（与上游 `com.saschl.cameragps` 共存），签名复用统一密钥，证书 SHA256 `18ff24c5...97d7`。
- 发版：`scripts/release_build.sh <X.Y.Z>` 构建归档并打 tag，`--publish-only` 推送并发布到主仓库（release.local.env 配置）。**versionCode 只在仓库根 `gradle.properties` 的 `alphagps.versionCode` 一处 bump**；发版脚本对该平面文件做严格解析（key 恰好一行、整行精确匹配 canonical 正整数），断言 APK 并校验相对上一发布 tag 递增（`scripts/release_lib.sh`，不再解析 Kotlin 源码；v0.1.0–v0.1.2 用内置历史表）。发版属对外动作，须用户确认。
- **双源发布（M10）**：`--publish` / `--publish-only` 先发主仓库，**成功后**才做 GitHub 步骤（github.com/mfreceiver/alpha-gps）：推 tag 到本机 GitHub 远端（名由 `release.local.env` 的 `ALPHAGPS_GITHUB_REMOTE` 配置，默认 `github`；不存在则 fail-closed 并提示 `git remote add github https://github.com/mfreceiver/alpha-gps.git`，脚本不自动创建）→ api.github.com 建/复用 release → uploads.github.com 上传 → 下载 `browser_download_url`（手动走一跳到白名单 CDN 域，二跳匿名、不再跟随）重算 sha256 对账。凭据走 `git credential fill`（host=github.com），token 不进日志/argv。选项：`--no-github` 跳过 GitHub 步骤；`--publish-github-only` 只补 GitHub 步骤（同 tag 同名同 sha256 资产跳过、同名不同 sha 中止，幂等）；`--github-tag-only`（默认）只推 tag，`--github-push-branch` 才再推当前分支。**GitHub 步骤会推送 tag（`--github-push-branch` 时再加当前分支），其可达的历史对公开可见。**
- 发版脚本退出码：`0` 成功；`1` 失败（用法/配置/前置/构建/主仓库发布，此时 GitHub 步骤未执行）；`40` GitHub 步骤失败（若出现在主仓库成功之后，主仓库不回滚，修复后 `--publish-github-only` 重跑）。仅 `--publish-github-only` 失败时 40 不涉及主仓库。
- GitHub 目的地规则：本机 GitHub 远端的 fetch 与 push 侧都必须解析到唯一公开 URL，传输钉在 URL 字面量上（见 `rl_gh_verify_remote`）。
- **历史已净化（2026-11）**：自有提交范围的私有主机/仓库/账号标识、`@local`/私有邮箱作者与旧版设计 PDF 已从历史移除（保留包名 `cn.vectory.alphagps`）；tag v0.1.x 已随之迁移，已发布 APK 不变。**每次向 GitHub 推送前必须对待推 tag/分支的全部可达对象做指纹扫描**（私有主机名、端口、主发布仓库的产品名、私有邮箱、`/home/<user>/` 绝对路径），非空不得推送；新增入库内容也不得带这些标识。
- 发版脚本自测：`scripts/*_test.sh`（无网络、沙箱 + fake curl，不碰真实仓库），改动发版脚本后须全部通过。
- 验证命令：`./gradlew --no-daemon :sharednew:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`。
- 本机 `local.properties` 的 `sdk.dir=$HOME/Android/Sdk` 不入库（已被 .gitignore 忽略）。
- **仅 arm64**：app 与 heif 的 `defaultConfig.ndk.abiFilters` 只含 `arm64-v8a`（native 仅构建/打包该 ABI）；老 32 位（armeabi-v7a）与 x86/x86_64 设备/模拟器不可安装。`scripts/release_assert_apk.sh` 断言 APK `lib/` 下仅含 `arm64-v8a` 且至少一个 `.so`。
- `release_build.sh` 构建前清理 compose 资源生成残留（sharednew/build 与 app/build 的生成/中间 assets 目录），并断言 APK 仅含 en+zh-rCN 的 composeResources（`assets/composeResources/*/values*` ⊆ {values, values-zh-rCN}）。

## UI 组件库用法（设计 v3）
- 位置与清单见 `docs/design/README.md`「组件库在代码中的位置」；主题唯一入口 `CameraGpsTheme`（固定蓝色，不用动态色），颜色从 `AlphaTheme.colors` / `MaterialTheme.colorScheme` 取，不写死色值。
- 每个页面根部用 `GlassBackground { … }`（G0 底光 + 模糊取样源 + 对话框叠层）；卡片 / 行用 `GlassCard`/`GlassSurface(level = …)`，按角色选 G1/G2/G3/GV。照片网格不作模糊取样源（`GlassBackground(contentIsBackdropSource = false)` 或不包进取样区）。
- 按钮：文字操作一律 `CapsuleButton`（role 主/次/色调/危险/危险描边，≥48dp）；纯图标用 `GlassIconButton`（必须给 contentDescription）；信息标签用 `InfoTag`（不可点）。对话框一律 `CapsuleDialog`（取消左、确认右，危险操作 `destructive = true`），不再新增 `AlertDialog`。不用分隔线。
- 窗口适配用 `windowClassOf(widthDp, heightDp)` / `currentWindowClass()`（A 竖长 / B 近方形 / C 横屏）。

## 语言资源纪律
- 仅支持英文基线（`values/`）+ 简体中文（`values-zh-rCN/`）；其余语言已移除，`app/build.gradle.kts` 用 `androidResources.localeFilters`（en、zh-rCN）屏蔽依赖库语言。
- 新增文案只需写 `values/` 与 `values-zh-rCN/` 两处（app 的 `app/src/main/res` 与 sharednew 的 `commonMain/composeResources` 同理），不要新增其它语言目录。
- 删减语言后必须清理 sharednew/build 的 compose 资源生成目录再验证 APK，增量构建会残留旧语言。
