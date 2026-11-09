# AGENTS.md（alpha-gps 定制版）

## 待办纪律
- 待办唯一入口：`docs/todo.md`，**只放当前待完成**的事项。
- 完成一项，在**同一次提交**中把对应条目从文件里删除；不打勾保留，不建“已完成”区。完成历史靠 git 记录。
- 新发现的事项当天写入（要做什么 / 验收标准 / 依据位置），按 P1>P2>P3 排序。
- 开始一次工作前先读 `docs/todo.md`；结束时核对其中是否有已完成或已过期的条目。

## 构建与发版要点
- 开发分支 `feature/browse-only-download`，远端 `primary`（`https://forge.example.com:443/owner/alphagps.git`）；`origin` 是上游 Saschl/alpha-gps，仅用于 fetch，同步方式：fetch → main 快进 → merge（不 rebase）进功能分支。
- 包名 `cn.vectory.alphagps`（与上游 `com.saschl.cameragps` 共存），签名复用统一密钥，证书 SHA256 `18ff24c5...97d7`。
- 发版：`scripts/release_build.sh <X.Y.Z>` 构建归档并打 tag，`--publish-only` 推送并发布 Primary forge Release。**versionCode 只在仓库根 `gradle.properties` 的 `alphagps.versionCode` 一处 bump**；发版脚本对该平面文件做严格解析（key 恰好一行、整行精确匹配 canonical 正整数），断言 APK 并校验相对上一发布 tag 递增（`scripts/release_lib.sh`，不再解析 Kotlin 源码；v0.1.0–v0.1.2 用内置历史表）。发版属对外动作，须用户确认。
- iOS 目标默认禁用（`gradle.properties` 的 `alphagps.enableIos=false`），源码保留；Linux 无法编译 iOS。
- 验证命令：`./gradlew --no-daemon :sharednew:testAndroidHostTest :app:testFossDebugUnitTest :app:assembleFossDebug :app:compileFossDebugAndroidTestKotlin`。
- 本机 `local.properties` 的 `sdk.dir=/home/user/Android/Sdk` 不入库（已被 .gitignore 忽略）。
- **仅 arm64**：app 与 heif 的 `defaultConfig.ndk.abiFilters` 只含 `arm64-v8a`（native 仅构建/打包该 ABI）；老 32 位（armeabi-v7a）与 x86/x86_64 设备/模拟器不可安装。`scripts/release_assert_apk.sh` 断言 APK `lib/` 下仅含 `arm64-v8a` 且至少一个 `.so`。
- `release_build.sh` 构建前清理 compose 资源生成残留（sharednew/build 与 app/build 的生成/中间 assets 目录），并断言 APK 仅含 en+zh-rCN 的 composeResources（`assets/composeResources/*/values*` ⊆ {values, values-zh-rCN}）。

## 语言资源纪律
- 仅支持英文基线（`values/`）+ 简体中文（`values-zh-rCN/`）；其余语言已移除，`app/build.gradle.kts` 用 `androidResources.localeFilters`（en、zh-rCN）屏蔽依赖库语言。
- 新增文案只需写 `values/` 与 `values-zh-rCN/` 两处（app 的 `app/src/main/res` 与 sharednew 的 `commonMain/composeResources` 同理），不要新增其它语言目录。
- 删减语言后必须清理 sharednew/build 的 compose 资源生成目录再验证 APK，增量构建会残留旧语言。
