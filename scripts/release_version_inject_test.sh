#!/usr/bin/env bash
# scripts/release_version_inject.sh 的故障注入自测。
# 通过 PATH shim 定向让 cp/sed/grep/mv 失败或产生残缺输出，验证：
#   1. 正常 inject：源文件被改写、完整备份保留；
#   2. cp 中途失败（部分写入）：非零退出、无备份残留、源文件未改动；
#   3. cp "成功"但内容残缺：cmp 校验拦截、备份删除、源文件未改动；
#   4. sed 失败：非零退出、自动还原、源文件与原文 cmp 一致、无备份残留；
#   5. 注入后 grep 校验失败：同上；
#   6. 残留备份：拒绝 inject；
#   7. restore 时 mv 失败：非零退出、备份保留、源文件未动、输出人工恢复提示。
# 另含 release_build.sh 端到端 wiring 场景（见末尾 run_parent_*）：
#   8. 构建失败 → EXIT trap 还原，构建文件回到原文；
#   9. 构建成功但还原 mv 失败 → 脚本非零退出、备份保留（后续归档/tag/发布不会执行）；
#  10. 残留备份 B 与源文件 A 字节不同 → inject 拒绝、父层 EXIT trap 不激活：
#      A/B 均逐字节原样、备份未消费、未进入 gradlew/后续步骤。
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
INJECT=$HERE/release_version_inject.sh
BUILD=$HERE/../app/build.gradle.kts

TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/shim" "$TMP/work"
# 沙箱 HOME：伪造 gradle.properties 与 keystore 占位（keytool 已被 shim，只需文件存在）
mkdir -p "$TMP/home/.gradle" "$TMP/home/.config/dshdroid/signing"
printf 'DSH_KEYSTORE_PASSWORD=x\nDSH_KEY_PASSWORD=x\n' > "$TMP/home/.gradle/gradle.properties"
printf 'placeholder\n' > "$TMP/home/.config/dshdroid/signing/dshdroid.jks"

# 主仓库参数外置（scripts/release.local.env 不入库）：沙箱用环境变量注入占位值。
export ALPHAGPS_PRIMARY_HOST=forge.example.com:443
export ALPHAGPS_PRIMARY_REPO=owner/alphagps
export ALPHAGPS_PRIMARY_REMOTE=primary

PASS=0; FAILED=0
check() { # check <desc> <expr...>
  local desc=$1; shift
  if "$@"; then PASS=$((PASS+1)); echo "ok   - $desc"
  else FAILED=$((FAILED+1)); echo "FAIL - $desc"; fi
}

# make_shim <name> <body...>: 生成 PATH shim；真实二进制路径在生成时解析并内嵌，
# 其余参数原样透传。
make_shim() {
  local name=$1 body=$2
  local real; real=$(PATH="${PATH//$TMP\/shim:/}" command -v "$name") \
    || { echo "make_shim: 找不到真实 $name" >&2; exit 2; }
  printf '#!/usr/bin/env bash\nexec_real(){ exec "%s" "$@"; }\n%s\n' \
    "$real" "$body" > "$TMP/shim/$name"
  chmod +x "$TMP/shim/$name"
}
shim_path() { PATH="$TMP/shim:$PATH" "$@"; }

snapshot() { cp -- "$WORK_GRADLE" "$TMP/orig.gradle.kts"; }
gradle_untouched() { cmp -s -- "$TMP/orig.gradle.kts" "$WORK_GRADLE"; }

WORK_GRADLE=$TMP/work/build.gradle.kts
fresh() {
  rm -f -- "$WORK_GRADLE" "$WORK_GRADLE.release-bak"
  cp -- "$BUILD" "$WORK_GRADLE"
  snapshot
}
clean_shims() { rm -f "$TMP"/shim/*; }

echo "== 场景 1: 正常 inject =="
fresh
shim_path bash "$INJECT" inject "$WORK_GRADLE" v9.9.9
check "inject 退出码 0" test $? -eq 0
check "源文件已改写为 v9.9.9" grep -q 'versionName = "v9.9.9"' "$WORK_GRADLE"
check "备份保留" test -f "$WORK_GRADLE.release-bak"
shim_path bash "$INJECT" restore "$WORK_GRADLE"
check "restore 后源文件与原文一致" gradle_untouched
check "restore 后备份消失" test ! -e "$WORK_GRADLE.release-bak"

echo "== 场景 2: cp 中途失败（部分写入后退出 1）=="
fresh; clean_shims
make_shim cp 'if [[ "${1:-}" == "--" && "${3:-}" == *.release-bak ]]; then head -c 50 "$2" > "$3" 2>/dev/null || true; echo "cp: simulated partial write failure" >&2; exit 1; fi; exec_real "$@"'
shim_path bash "$INJECT" inject "$WORK_GRADLE" v9.9.9
RC=$?
check "inject 非零退出" test "$RC" -ne 0
check "残缺备份被删除" test ! -e "$WORK_GRADLE.release-bak"
check "源文件未改动" gradle_untouched

echo "== 场景 3: cp 成功但内容残缺（完整性校验拦截）=="
fresh; clean_shims
make_shim cp 'if [[ "${1:-}" == "--" && "${3:-}" == *.release-bak ]]; then head -c 20 "$2" > "$3"; exit 0; fi; exec_real "$@"'
shim_path bash "$INJECT" inject "$WORK_GRADLE" v9.9.9
RC=$?
check "inject 非零退出" test "$RC" -ne 0
check "残缺备份被删除" test ! -e "$WORK_GRADLE.release-bak"
check "源文件未改动（未被残缺备份覆盖）" gradle_untouched

echo "== 场景 4: sed 失败（备份已激活→自动还原）=="
fresh; clean_shims
make_shim sed 'echo "sed: simulated failure" >&2; exit 1'
shim_path bash "$INJECT" inject "$WORK_GRADLE" v9.9.9
RC=$?
check "inject 非零退出" test "$RC" -ne 0
check "源文件自动还原（cmp 一致）" gradle_untouched
check "还原后备份消失" test ! -e "$WORK_GRADLE.release-bak"

echo "== 场景 5: 注入后 grep 校验失败 =="
fresh; clean_shims
make_shim grep 'if [[ "${1:-}" == -qE ]]; then exit 1; fi; exec_real "$@"'
shim_path bash "$INJECT" inject "$WORK_GRADLE" v9.9.9
RC=$?
check "inject 非零退出" test "$RC" -ne 0
check "源文件自动还原（cmp 一致）" gradle_untouched
check "还原后备份消失" test ! -e "$WORK_GRADLE.release-bak"

echo "== 场景 6: 残留备份拒绝 inject =="
fresh
cp -- "$WORK_GRADLE" "$WORK_GRADLE.release-bak"
bash "$INJECT" inject "$WORK_GRADLE" v9.9.9 >/dev/null 2>&1
check "inject 非零退出" test $? -ne 0
check "源文件未改动" gradle_untouched
check "残留备份仍保留（等人工处理）" test -f "$WORK_GRADLE.release-bak"

echo "== 场景 7: restore 时 mv 失败（fail-closed）=="
fresh; clean_shims
bash "$INJECT" inject "$WORK_GRADLE" v9.9.9 >/dev/null
printf 'FAKE-ORIGINAL\n' > "$WORK_GRADLE"   # 模拟 sed 已改写
make_shim mv 'echo "mv: simulated failure" >&2; exit 1'
shim_path bash "$INJECT" restore "$WORK_GRADLE" > "$TMP/restore.log" 2>&1
RC=$?
check "restore 非零退出" test "$RC" -ne 0
check "备份保留（未被消费）" test -f "$WORK_GRADLE.release-bak"
check "改写后的源文件未被残缺覆盖（仍是 FAKE-ORIGINAL）" grep -q FAKE-ORIGINAL "$WORK_GRADLE"
check "输出人工恢复提示" grep -q "人工" "$TMP/restore.log"

# ── release_build.sh 端到端 wiring：沙箱仓库 + 受控 JAVA_HOME(keytool/java shim) ──
# release_build.sh 会把 $JAVA_HOME/bin 前置到 PATH，外部 PATH shim 盖不过 JDK 的
# 真 keytool；因此用沙箱 JAVA_HOME（bin/java 桩 + bin/keytool shim）接管解析。
setup_sandbox() { # $1=gradlew-body
  local SB=$TMP/sandbox
  rm -rf -- "$SB"
  mkdir -p "$SB/scripts" "$SB/app" "$SB/home/jdk/bin" "$SB/home/.gradle" "$SB/home/.config/dshdroid/signing"
  cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" "$SB/scripts/"
  cp -- "$BUILD" "$SB/app/build.gradle.kts"
  # 沙箱 gradle.properties：versionCode key 必须存在（release_build.sh 严格解析它取
  # EXPECT_CODE；值与真实仓库解耦，仅要求合法）
  printf 'alphagps.versionCode=99\n' > "$SB/gradle.properties"
  printf '#!/usr/bin/env bash\n%s\n' "$1" > "$SB/gradlew"; chmod +x "$SB/gradlew"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$SB/home/jdk/bin/java"; chmod +x "$SB/home/jdk/bin/java"
  # 沙箱 HOME：伪造 gradle.properties 与 keystore 占位（keytool 已被 shim，只需文件存在）
  printf 'DSH_KEYSTORE_PASSWORD=x\nDSH_KEY_PASSWORD=x\n' > "$SB/home/.gradle/gradle.properties"
  printf 'placeholder\n' > "$SB/home/.config/dshdroid/signing/dshdroid.jks"
  ( cd "$SB" && git init -q && git add -A && git -c user.email=t@t -c user.name=t commit -qm init )
  # 本地 bare 仓库充当主仓库远端 "primary"，ls-remote 走本地路径，无网络
  rm -rf -- "$TMP/primary.git"
  git init -q --bare "$TMP/primary.git"
  ( cd "$SB" && git remote add primary "$TMP/primary.git" )
  echo "$SB"
}

# make_keytool_shim: 输出钉定证书 SHA256 的 keytool shim（SHA 从沙箱内 release_build.sh
# 的 PINNED_CERT_SHA256 动态提取，避免手工转抄漂移），放进沙箱 JAVA_HOME/bin。
make_keytool_shim() { # $1=沙箱根
  local SB=$1
  local pin sha_col
  pin=$(sed -nE 's/^PINNED_CERT_SHA256="([0-9a-fA-F]+)"$/\1/p' "$SB/scripts/release_build.sh")
  [[ ${#pin} -eq 64 ]] || { echo "test-setup: 无法从 release_build.sh 提取 PINNED_CERT_SHA256"; exit 2; }
  sha_col=$(sed 's/../&:/g; s/:$//' <<<"$pin")
  printf '#!/usr/bin/env bash\nif [[ "${1:-}" == -list ]]; then printf "  SHA256: %s\\n" "%s"; exit 0; fi\nexit 1\n' \
    "$sha_col" > "$SB/home/jdk/bin/keytool"
  chmod +x "$SB/home/jdk/bin/keytool"
}

echo "== 场景 8: release_build.sh 构建失败 → EXIT trap 自动还原 =="
clean_shims
SB=$(setup_sandbox 'echo "gradlew: simulated build failure" >&2; exit 1')
make_keytool_shim "$SB"
( cd "$SB" && PATH="$SB/home/jdk/bin:$TMP/shim:$PATH" HOME="$SB/home" JAVA_HOME="$SB/home/jdk" \
    DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
    bash scripts/release_build.sh 9.9.9 --allow-dirty ) > "$TMP/parent1.log" 2>&1
RC=$?
check "release_build.sh 非零退出（构建失败）" test "$RC" -ne 0
check "注入的构建失败确实被触发（走过版本注入与构建）" grep -q "gradlew: simulated build failure" "$TMP/parent1.log"
check "构建文件已还原（与原文 cmp 一致）" cmp -s -- "$BUILD" "$SB/app/build.gradle.kts"
check "备份已消费（无 .release-bak 残留）" test ! -e "$SB/app/build.gradle.kts.release-bak"

echo "== 场景 9: 构建成功但还原 mv 失败 → 终止且备份保留 =="
clean_shims
make_shim mv 'echo "mv: simulated failure" >&2; exit 1'
SB2=$(setup_sandbox 'exit 0')
make_keytool_shim "$SB2"
( cd "$SB2" && PATH="$SB2/home/jdk/bin:$TMP/shim:$PATH" HOME="$SB2/home" JAVA_HOME="$SB2/home/jdk" \
    DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
    bash scripts/release_build.sh 9.9.9 --allow-dirty ) > "$TMP/parent3.log" 2>&1
RC=$?
check "release_build.sh 非零退出（还原失败不继续）" test "$RC" -ne 0
check "备份保留（等人工恢复）" test -f "$SB2/app/build.gradle.kts.release-bak"
check "改写后的构建文件未被覆盖回残缺态（仍含 v9.9.9 注入）" grep -q 'versionName = "v9.9.9"' "$SB2/app/build.gradle.kts"
check "未创建 release/ 目录" test ! -d "$SB2/release"
# 用归档步骤的独有日志锚点（"归档完成"）判定：--allow-dirty 的 warn 本身含"归档"字样，
# 直接 grep "归档" 会误报（此前的复合条件因此从未真正通过过）。
check "未输出归档步骤日志" bash -c '! grep -q "归档完成" "$1"' _ "$TMP/parent3.log"
check "输出人工恢复提示" grep -q "人工恢复" "$TMP/parent3.log"

echo "== 场景 10: 残留备份 ≠ 源文件 → 父层不接管未知备份 =="
clean_shims
SB3=$(setup_sandbox 'echo RAN > gradlew-ran-marker; exit 0')
make_keytool_shim "$SB3"
# 源文件 A：在原文基础上附加未提交修改（与备份 B 字节不同）
printf '\n// uncommitted local edit (A)\n' >> "$SB3/app/build.gradle.kts"
cp -- "$SB3/app/build.gradle.kts" "$TMP/A.gradle.kts"
printf '// stale backup from crashed run (B)\n' > "$SB3/app/build.gradle.kts.release-bak"
cp -- "$SB3/app/build.gradle.kts.release-bak" "$TMP/B.release-bak"
( cd "$SB3" && PATH="$SB3/home/jdk/bin:$TMP/shim:$PATH" HOME="$SB3/home" JAVA_HOME="$SB3/home/jdk" \
    DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
    bash scripts/release_build.sh 9.9.9 --allow-dirty ) > "$TMP/parent4.log" 2>&1
RC=$?
check "release_build.sh 非零退出（inject 拒绝残留备份）" test "$RC" -ne 0
check "输出残留备份拒绝信息" grep -q "残留备份" "$TMP/parent4.log"
check "源文件 A 逐字节原样" cmp -s -- "$TMP/A.gradle.kts" "$SB3/app/build.gradle.kts"
check "残留备份 B 逐字节原样且未被消费" cmp -s -- "$TMP/B.release-bak" "$SB3/app/build.gradle.kts.release-bak"
check "未进入 gradlew（marker 未生成）" test ! -e "$SB3/gradlew-ran-marker"
check "未创建 release/ 目录" test ! -d "$SB3/release"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
