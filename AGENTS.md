# AGENTS.md（alpha-gps 定制版）

## 待办纪律
- 待办唯一入口：`docs/todo.md`，**只放当前待完成**的事项。
- 完成一项，在**同一次提交**中把对应条目从文件里删除；不打勾保留，不建“已完成”区。完成历史靠 git 记录。
- 新发现的事项当天写入（要做什么 / 验收标准 / 依据位置），按 P1>P2>P3 排序。
- 开始一次工作前先读 `docs/todo.md`；结束时核对其中是否有已完成或已过期的条目。

## 构建与发版要点
- 开发分支 `feature/browse-only-download`，远端 `primary`（`https://forge.example.com:443/owner/alphagps.git`）；`origin` 是上游 Saschl/alpha-gps，仅用于 fetch，同步方式：fetch → main 快进 → merge（不 rebase）进功能分支。
- 包名 `cn.vectory.alphagps`（与上游 `com.saschl.cameragps` 共存），签名复用统一密钥，证书 SHA256 `18ff24c5...97d7`。
- 发版：`scripts/release_build.sh <X.Y.Z>` 构建归档并打 tag，`--publish-only` 推送并发布 Primary forge Release。**versionCode 需手动 bump**（`app/build.gradle.kts`），发版前对 APK 另行按预期 versionCode 用 `scripts/release_assert_apk.sh` 独立断言。发版属对外动作，须用户确认。
- iOS 目标默认禁用（`gradle.properties` 的 `alphagps.enableIos=false`），源码保留；Linux 无法编译 iOS。
- 验证命令：`./gradlew --no-daemon :sharednew:testAndroidHostTest :app:testFossDebugUnitTest :app:assembleFossDebug :app:compileFossDebugAndroidTestKotlin`。
- 本机 `local.properties` 的 `sdk.dir=/home/user/Android/Sdk` 不入库（已被 .gitignore 忽略）。
