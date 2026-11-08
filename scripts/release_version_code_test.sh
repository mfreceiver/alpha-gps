#!/usr/bin/env bash
# release_lib.sh（parse_version_code_props / parse_version_code_from_git /
# expected_code_for_tag / assert_code_monotonic）+ release_build.sh versionCode
# 独立预期 wiring 的自测。全部走 mock/fake（沙箱 git 仓库、fake aapt/apksigner，
# 真实 scripts/release_assert_apk.sh），不依赖真实构建/发布。
# 设计契约：versionCode 的单一来源是仓库根 gradle.properties 的平面 key=value 行；
#   脚本只做严格的平面解析（无 Kotlin 词法），任何异常输入 fail-closed。
#   场景 1  parse_version_code_props 正例（含其它无关属性行）
#   场景 2  0 处（key 缺失）→ fail-closed
#   场景 3  2 处 → fail-closed
#   场景 4  key 出现在注释行（`# key=9` 与真实行并存）→ 2 行含子串 → fail-closed
#   场景 5  带空格 `alphagps.versionCode = 3` → fail-closed
#   场景 6  带冒号 `alphagps.versionCode: 3` → fail-closed
#   场景 7  尾随空白 → fail-closed
#   场景 8  前导零 03 / 0 / 2147483648 / 18446744073709551617 → 全部 fail-closed
#   场景 9  CR 行尾 / NUL 字节 / 文件缺失 → 全部 fail-closed
#   场景 10 2147483647 边界 → 放行
#   场景 11 parse_version_code_from_git：git show 失败 / NUL 内容 / 成功路径（沙箱 git 仓库）
#   场景 12 assert_code_monotonic：LEGACY 表命中（v0.1.2→3，前一 tag v0.1.1=表值 2）
#   场景 13 上游 tag（gradle.properties 无 key）→ 跳过，仅 WARN
#   场景 14 定制版新 tag 用属性：递增通过 / 未递增失败
#   场景 15 无前 tag → 仅 WARN 放行
#   场景 16 grep rc=2 / rc=127（函数覆盖）→ fail-closed
#   场景 17 git tag 枚举失败（非 git 仓库）→ fail-closed；sort 失败（函数覆盖）→ fail-closed
#   场景 18 v0.1.10 与 v0.1.2 排序（sort -V）：比较对象必须是 v0.1.10
#   场景 19 assert_code_monotonic 不污染调用方 i 变量
#   场景 20 临时文件清理：errexit 失败路径不遗留（TMPDIR 沙箱计数）+ TERM 信号后不遗留
#   场景 21 wiring：APK code ≠ gradle.properties EXPECT_CODE → release_build.sh 整链
#           fail-closed（真实断言脚本拦截）
#   场景 22 wiring：APK code == EXPECT_CODE → 真实 release_assert_apk.sh 全链通过
#   场景 23 publish-only 对 LEGACY tag（v0.1.2）使用表值 3：走到凭据预取才停（无网络）
#   场景 24 publish-only 对非 LEGACY 且无 key 的 tag → fail-closed
#   场景 25 变异验证：git show 62bc54d 的旧 release_lib.sh——对旧式 Kotlin 模板仍可解析
#           （兼容对照），但对本仓库新 app/build.gradle.kts 必然 0 处失败（证明迁移必要）
# fixture 纪律：wiring 沙箱用测试内固定模板 gradle.properties（FIXTURE_CODE/FIXTURE_NAME
# 常量），与仓库真实 versionCode/versionName 解耦——仓库 bump 后本测试依然通过。
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
LIB=$HERE/release_lib.sh
# shellcheck source=release_lib.sh
source "$LIB"

FIXTURE_CODE=3      # wiring 沙箱模板 gradle.properties 的 versionCode（与真实仓库解耦）
FIXTURE_NAME=0.1.2  # 同上，versionName

TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT

PASS=0; FAILED=0
check() { # check <desc> <expr...>
  local desc=$1; shift
  if "$@"; then PASS=$((PASS+1)); echo "ok   - $desc"
  else FAILED=$((FAILED+1)); echo "FAIL - $desc"; fi
}

PROPS_TPL() { # $1=code → stdout（含无关属性行，贴近真实文件形态）
  printf 'org.gradle.jvmargs=-Xmx4096m\nalphagps.enableIos=false\nalphagps.versionCode=%s\nkotlin.code.style=official\n' "$1"
}

# ── 沙箱 git 仓库（单调性/from_git 用；一律 $TMP 下构造，用后随 $TMP 删除）──
make_git_repo() { # $1=目录
  mkdir -p "$1"
  git -C "$1" init -q
  git -C "$1" -c user.email=t@t -c user.name=t commit -q --allow-empty -m init
}
commit_file() { # $1=repo $2=相对路径 $3=内容printf格式串 $4=msg（自动建父目录；printf 直接落盘，可含 \0）
  mkdir -p "$1/$(dirname "$2")"
  printf "$3" > "$1/$2"
  git -C "$1" add "$2"
  git -C "$1" -c user.email=t@t -c user.name=t commit -qm "$4"
}

echo "== 场景 1: parse_version_code_props 正例 =="
PROPS_TPL 5 > "$TMP/ok.props"
OUT=$(parse_version_code_props "$TMP/ok.props" 2>"$TMP/err"); RC=$?
check "退出码 0 且解析为 5" test "$RC" -eq 0 -a "$OUT" == "5"
OUT=$(parse_version_code_props "$HERE/../gradle.properties" 2>"$TMP/err"); RC=$?
check "真实仓库 gradle.properties 解析为 canonical 正整数" test "$RC" -eq 0 \
  -a "$(bash -c '[[ "$1" =~ ^[1-9][0-9]{0,9}$ ]] && echo ok' _ "$OUT")" == "ok"

echo "== 场景 2: 0 处（key 缺失）→ fail-closed =="
printf 'org.gradle.jvmargs=-Xmx1m\n' > "$TMP/zero.props"
OUT=$(parse_version_code_props "$TMP/zero.props" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『恰好 1 行』" grep -q "恰好 1 行" "$TMP/err"

echo "== 场景 3: 2 处 → fail-closed =="
{ PROPS_TPL 3; PROPS_TPL 4; } > "$TMP/two.props"
OUT=$(parse_version_code_props "$TMP/two.props" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『实际 2 行』" grep -q "实际 2 行" "$TMP/err"

echo "== 场景 4: key 出现在注释行 → 2 行含子串 → fail-closed =="
printf '# bump: alphagps.versionCode=9\nalphagps.versionCode=3\n' > "$TMP/comment.props"
OUT=$(parse_version_code_props "$TMP/comment.props" 2>"$TMP/err"); RC=$?
check "非零退出（注释也计入子串行数）" test "$RC" -ne 0
check "错误含『实际 2 行』" grep -q "实际 2 行" "$TMP/err"

echo "== 场景 5: 带空格 → fail-closed =="
printf 'alphagps.versionCode = 3\n' > "$TMP/space.props"
OUT=$(parse_version_code_props "$TMP/space.props" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『整行严格匹配』" grep -q "整行严格匹配" "$TMP/err"

echo "== 场景 6: 带冒号 → fail-closed =="
printf 'alphagps.versionCode: 3\n' > "$TMP/colon.props"
OUT=$(parse_version_code_props "$TMP/colon.props" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0

echo "== 场景 7: 尾随空白 → fail-closed =="
printf 'alphagps.versionCode=3 \n' > "$TMP/tail.props"
OUT=$(parse_version_code_props "$TMP/tail.props" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
printf 'alphagps.versionCode=3\t\n' > "$TMP/tailt.props"
OUT=$(parse_version_code_props "$TMP/tailt.props" 2>"$TMP/err"); RC=$?
check "尾随 TAB 同样非零" test "$RC" -ne 0

echo "== 场景 8: 非法值全部 fail-closed =="
for bad in 03 0 2147483648 18446744073709551617; do
  printf 'alphagps.versionCode=%s\n' "$bad" > "$TMP/bad.props"
  OUT=$(parse_version_code_props "$TMP/bad.props" 2>"$TMP/err"); RC=$?
  check "非法值 $bad → 非零退出" test "$RC" -ne 0
  check "非法值 $bad → 错误含『严格匹配』或『非法值』" \
    bash -c 'grep -qE "整行严格匹配|非法值" "$1"' _ "$TMP/err"
done

echo "== 场景 9: CR / NUL / 文件缺失 → fail-closed =="
printf 'alphagps.versionCode=3\r\n' > "$TMP/cr.props"
OUT=$(parse_version_code_props "$TMP/cr.props" 2>"$TMP/err"); RC=$?
check "CR → 非零" test "$RC" -ne 0
check "CR 错误含『CR』" grep -q "CR" "$TMP/err"
printf 'alphagps.versionCode=3\0\n' > "$TMP/nul.props"
OUT=$(parse_version_code_props "$TMP/nul.props" 2>"$TMP/err"); RC=$?
check "NUL → 非零" test "$RC" -ne 0
check "NUL 错误含『NUL』" grep -q "NUL" "$TMP/err"
OUT=$(parse_version_code_props "$TMP/nonexistent.props" 2>"$TMP/err"); RC=$?
check "文件缺失 → 非零" test "$RC" -ne 0
check "文件缺失错误含『文件不存在』" grep -q "文件不存在" "$TMP/err"

echo "== 场景 10: 2147483647 边界 → 放行 =="
printf 'alphagps.versionCode=2147483647\n' > "$TMP/max.props"
OUT=$(parse_version_code_props "$TMP/max.props" 2>"$TMP/err"); RC=$?
check "边界 2147483647 → 放行" test "$RC" -eq 0 -a "$OUT" == "2147483647"

echo "== 场景 11: parse_version_code_from_git（沙箱 git 仓库） =="
R0=$TMP/fg; make_git_repo "$R0"
OUT=$(cd "$R0" && parse_version_code_from_git v-nonexist 2>"$TMP/err"); RC=$?
check "git show 失败 → 非零" test "$RC" -ne 0
check "错误含『git show』" grep -q "git show" "$TMP/err"
commit_file "$R0" gradle.properties 'alphagps.versionCode=3\0\n' "nul props"
git -C "$R0" tag v0.9.0-nul
OUT=$(cd "$R0" && parse_version_code_from_git v0.9.0-nul 2>"$TMP/err"); RC=$?
check "tag 内容含 NUL → 非零（不经 bash 变量丢 NUL）" test "$RC" -ne 0
check "错误含『NUL』" grep -q "NUL" "$TMP/err"
commit_file "$R0" gradle.properties 'a=1\nalphagps.versionCode=7\n' "good props"
git -C "$R0" tag v0.9.0
OUT=$(cd "$R0" && parse_version_code_from_git v0.9.0 2>"$TMP/err"); RC=$?
check "成功路径 → 解析为 7" test "$RC" -eq 0 -a "$OUT" == "7"
OUT=$(cd "$R0" && expected_code_for_tag v0.9.0 2>"$TMP/err"); RC=$?
check "expected_code_for_tag 非 LEGACY → from_git 值 7" test "$RC" -eq 0 -a "$OUT" == "7"
OUT=$(expected_code_for_tag v0.1.2 2>"$TMP/err"); RC=$?
check "expected_code_for_tag v0.1.2 → LEGACY 表值 3" test "$RC" -eq 0 -a "$OUT" == "3"
OUT=$(expected_code_for_tag v0.1.1 2>"$TMP/err"); RC=$?
check "expected_code_for_tag v0.1.1 → LEGACY 表值 2" test "$RC" -eq 0 -a "$OUT" == "2"

echo "== 场景 12: LEGACY 表命中（v0.1.2 前一 tag v0.1.1 → 表值 2） =="
R1=$TMP/legacy; make_git_repo "$R1"
commit_file "$R1" gradle.properties 'x=1\n' "old layout no key"   # v0.1.1 提交无该 key → 但走 LEGACY 表
git -C "$R1" tag v0.1.1
OUT=$(cd "$R1" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "3 > v0.1.1(表值 2) 通过" test "$RC" -eq 0
check "输出确认 3 > v0.1.1(versionCode=2)" grep -q "3 > v0.1.1(versionCode=2)" <<<"$OUT"
OUT=$(cd "$R1" && assert_code_monotonic 2 0.1.3 2>"$TMP/err"); RC=$?
check "2 ≤ v0.1.1(2) → 非零" test "$RC" -ne 0
check "错误点名 v0.1.1(versionCode=2)" grep -q "tag v0.1.1 递增（其 versionCode=2）" "$TMP/err"

echo "== 场景 13: 上游 tag（gradle.properties 无 key）→ 跳过，仅 WARN =="
R2=$TMP/upstream; make_git_repo "$R2"
commit_file "$R2" gradle.properties 'org.gradle.jvmargs=-Xmx1m\n' "upstream props no key"
git -C "$R2" tag v0.0.9
OUT=$(cd "$R2" && assert_code_monotonic 1 0.1.0 2>"$TMP/err"); RC=$?
check "退出码 0（仅警告）" test "$RC" -eq 0
check "stderr 含『仅警告』" grep -q "仅警告" "$TMP/err"

echo "== 场景 14: 定制版新 tag 用属性 =="
R3=$TMP/props; make_git_repo "$R3"
commit_file "$R3" gradle.properties 'alphagps.versionCode=5\n' "custom v0.2.0"
git -C "$R3" tag v0.2.0
OUT=$(cd "$R3" && assert_code_monotonic 6 0.2.1 2>"$TMP/err"); RC=$?
check "6 > v0.2.0(5) 通过" test "$RC" -eq 0 -a "$(grep -c '6 > v0.2.0(versionCode=5)' <<<"$OUT")" -eq 1
OUT=$(cd "$R3" && assert_code_monotonic 5 0.2.1 2>"$TMP/err"); RC=$?
check "5 ≤ 5 → 非零（未递增）" test "$RC" -ne 0
check "错误点名 v0.2.0(versionCode=5)" grep -q "tag v0.2.0 递增（其 versionCode=5）" "$TMP/err"
# 定制版 tag 的 props 含 key 但格式坏 → fail-closed（不静默当上游）
commit_file "$R3" gradle.properties 'alphagps.versionCode = 9\n' "bad format custom"
git -C "$R3" tag v0.2.1-bad 2>/dev/null || true
git -C "$R3" tag v0.2.2
OUT=$(cd "$R3" && assert_code_monotonic 7 0.2.3 2>"$TMP/err"); RC=$?
check "前一 tag props 含 key 但格式坏 → 非零（fail-closed）" test "$RC" -ne 0
check "错误含『解析失败』" grep -q "解析失败" "$TMP/err"

echo "== 场景 15: 无前 tag → 仅 WARN 放行 =="
R4=$TMP/empty; make_git_repo "$R4"
OUT=$(cd "$R4" && assert_code_monotonic 1 0.1.0 2>"$TMP/err"); RC=$?
check "退出码 0" test "$RC" -eq 0
check "stderr 含『仅警告』" grep -q "仅警告" "$TMP/err"

echo "== 场景 16: grep rc=2 / rc=127（函数覆盖）→ fail-closed =="
R5=$TMP/greprc; make_git_repo "$R5"
commit_file "$R5" gradle.properties 'alphagps.versionCode=2\n' "v0.1.1"
git -C "$R5" tag v0.1.1
for rc in 2 127; do
  OUT=$(cd "$R5" && { grep() { return "$MYRC"; }; MYRC=$rc assert_code_monotonic 3 0.1.2; } 2>"$TMP/err"); RC=$?
  check "grep rc=$rc → 非零" test "$RC" -ne 0
  check "错误含『tag 过滤 grep 异常（rc=$rc）』" grep -q "tag 过滤 grep 异常（rc=$rc）" "$TMP/err"
done

echo "== 场景 17: git tag 枚举失败 / sort 失败 → fail-closed =="
OUT=$(cd "$TMP" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "非 git 仓库 → 非零" test "$RC" -ne 0
check "错误含『枚举失败』" grep -q "枚举失败" "$TMP/err"
OUT=$(cd "$R5" && { sort() { return 2; }; assert_code_monotonic 3 0.1.2; } 2>"$TMP/err"); RC=$?
check "sort 失败 → 非零" test "$RC" -ne 0
check "错误含『排序失败』" grep -q "排序失败" "$TMP/err"

echo "== 场景 18: v0.1.10 与 v0.1.2 排序（sort -V：v0.1.10 > v0.1.2） =="
R6=$TMP/vsort; make_git_repo "$R6"
commit_file "$R6" gradle.properties 'alphagps.versionCode=3\n' "v0.1.2"
git -C "$R6" tag v0.1.2
commit_file "$R6" gradle.properties 'alphagps.versionCode=4\n' "v0.1.10"
git -C "$R6" tag v0.1.10
OUT=$(cd "$R6" && assert_code_monotonic 5 0.1.11 2>"$TMP/err"); RC=$?
check "比较对象是 v0.1.10(4) 而非 v0.1.2" test "$RC" -eq 0 \
  -a "$(grep -c '5 > v0.1.10(versionCode=4)' <<<"$OUT")" -eq 1
OUT=$(cd "$R6" && assert_code_monotonic 4 0.1.11 2>"$TMP/err"); RC=$?
check "4 ≤ v0.1.10(4) → 非零（若误比 v0.1.2 的 3 会放行）" test "$RC" -ne 0
check "错误点名 v0.1.10(versionCode=4)" grep -q "tag v0.1.10 递增（其 versionCode=4）" "$TMP/err"

echo "== 场景 19: assert_code_monotonic 不污染调用方 i 变量 =="
(
  cd "$R6" || exit 1
  i=KEEP_SENTINEL
  assert_code_monotonic 5 0.1.11 >/dev/null 2>&1
  [[ "$i" == "KEEP_SENTINEL" ]] && exit 0 || exit 1
); RC=$?
check "调用前后外部变量 i 不变" test "$RC" -eq 0

echo "== 场景 20: 临时文件清理（errexit 失败路径 + TERM 信号） =="
TMPCNT=$TMP/tmpwatch; rm -rf -- "$TMPCNT"; mkdir -p "$TMPCNT"
# 失败路径：NUL 内容使 parser 失败 → subshell trap EXIT 清理
OUT=$(cd "$R0" && TMPDIR="$TMPCNT" parse_version_code_from_git v0.9.0-nul 2>/dev/null); RC=$?
check "NUL 失败路径非零" test "$RC" -ne 0
check "失败路径无临时文件遗留（实际 $(ls -A "$TMPCNT" | wc -l) 个）" test "$(ls -A "$TMPCNT" | wc -l)" -eq 0
# TERM 路径：PATH 上放一个 sleep 挂起的 git shim，setsid 起独立进程组后整组 TERM；
# 前台 git 死后 subshell 走到 EXIT trap → 清理 mktemp 文件。
GSHIM=$TMP/gitshim; mkdir -p "$GSHIM"
printf '#!/usr/bin/env bash\nsleep 30\n' > "$GSHIM/git"; chmod +x "$GSHIM/git"
(
  cd "$R0" || exit 1
  setsid bash -c "source '$LIB'; parse_version_code_from_git v0.9.0" >/dev/null 2>&1 &
  BGPID=$!
  for _ in $(seq 1 100); do
    [[ "$(ls -A "$TMPCNT" 2>/dev/null | wc -l)" -gt 0 ]] && break
    sleep 0.05
  done
  [[ "$(ls -A "$TMPCNT" 2>/dev/null | wc -l)" -gt 0 ]]   # 临时文件已出现（否则后续断言必失败）
  kill -TERM -- -"$BGPID" 2>/dev/null
  wait $BGPID 2>/dev/null
) ; check "TERM 后已捕获到临时文件出现过" test $? -eq 0
sleep 0.5
LEFT=$(ls -A "$TMPCNT" 2>/dev/null | wc -l)
check "TERM 后无临时文件遗留（实际 $LEFT 个）" test "$LEFT" -eq 0

# ── release_build.sh wiring（沙箱仓库 + fake aapt/apksigner + 真实断言脚本）────
# 用真实 scripts/release_assert_apk.sh：其 SDK 发现链 local.properties sdk.dir →
# ANDROID_HOME → ~/Android/Sdk，沙箱用 local.properties 指向 fake SDK（不改生产行为）。
# fake aapt 的 badging 首行由 AAPT_CODE / AAPT_VNAME 驱动；fake apksigner 报钉定证书。
setup_wiring_sandbox() { # $1=沙箱根
  local SB=$1
  rm -rf -- "$SB"
  mkdir -p "$SB/scripts" "$SB/app" "$SB/home/jdk/bin" "$SB/home/.gradle" \
           "$SB/home/.config/alphagps/signing" "$SB/sdk/build-tools/33.0.0"
  cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" \
        "$HERE/release_assert_apk.sh" "$SB/scripts/"
  # 固定模板（与真实仓库 versionCode/versionName 解耦）：gradle.properties 为单一来源
  printf 'org.gradle.jvmargs=-Xmx1m\nalphagps.versionCode=%s\n' "$FIXTURE_CODE" > "$SB/gradle.properties"
  printf '        applicationId = "cn.vectory.alphagps"\n        versionName = "%s"\n' \
    "$FIXTURE_NAME" > "$SB/app/build.gradle.kts"
  cat > "$SB/gradlew" <<'GW'
#!/usr/bin/env bash
mkdir -p app/build/outputs/apk/foss/release
: > app/build/outputs/apk/foss/release/app-foss-release.apk
exit 0
GW
  chmod +x "$SB/gradlew"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$SB/home/jdk/bin/java"; chmod +x "$SB/home/jdk/bin/java"
  local pin sha_col
  pin=$(sed -nE 's/^PINNED_CERT_SHA256="([0-9a-fA-F]+)"$/\1/p' "$SB/scripts/release_build.sh")
  [[ ${#pin} -eq 64 ]] || { echo "test-setup: 无法提取 PINNED_CERT_SHA256"; exit 2; }
  sha_col=$(sed 's/../&:/g; s/:$//' <<<"$pin")
  printf '#!/usr/bin/env bash\nif [[ "${1:-}" == -list ]]; then printf "  SHA256: %%s\\n" "%s"; exit 0; fi\nexit 1\n' \
    "$sha_col" > "$SB/home/jdk/bin/keytool"; chmod +x "$SB/home/jdk/bin/keytool"
  printf '#!/usr/bin/env bash\nif [[ "${1:-} ${2:-}" == "verify --print-certs" ]]; then printf "Signer #1 certificate DN: CN=release\\nSigner #1 certificate SHA-256 digest: %s\\n" "%s"; exit 0; fi\n[[ "${1:-}" == verify ]] && exit 0\nexit 1\n' \
    "$pin" > "$SB/sdk/build-tools/33.0.0/apksigner"
  chmod +x "$SB/sdk/build-tools/33.0.0/apksigner"
  cat > "$SB/sdk/build-tools/33.0.0/aapt" <<'AAPT'
#!/usr/bin/env bash
if [[ "${1:-} ${2:-}" == "dump badging" ]]; then
  printf "package: name='cn.vectory.alphagps' versionCode='%s' versionName='v%s'\n" "${AAPT_CODE:?}" "${AAPT_VNAME:?}"
  printf "uses-permission: name='android.permission.INTERNET'\n"
fi
exit 0
AAPT
  chmod +x "$SB/sdk/build-tools/33.0.0/aapt"
  printf 'sdk.dir=%s\n' "$SB/sdk" > "$SB/local.properties"
  printf 'DSH_KEYSTORE_PASSWORD=x\nDSH_KEY_PASSWORD=x\n' > "$SB/home/.gradle/gradle.properties"
  printf 'placeholder\n' > "$SB/home/.config/alphagps/signing/release.jks"
  ( cd "$SB" && git init -q && git add -A && git -c user.email=t@t -c user.name=t commit -qm init )
  rm -rf -- "$TMP/wvectory.git"; git init -q --bare "$TMP/wvectory.git"
  ( cd "$SB" && git remote add primary "$TMP/wvectory.git" )
}

run_wiring() { # $1=SB $2=AAPT_CODE $3=输出log
  ( cd "$1" && PATH="$1/home/jdk/bin:$PATH" HOME="$1/home" JAVA_HOME="$1/home/jdk" \
      DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
      AAPT_CODE="$2" AAPT_VNAME=0.1.3 \
      timeout 30 bash scripts/release_build.sh 0.1.3 --allow-dirty ) > "$3" 2>&1
}

echo "== 场景 21: wiring — APK code(999) ≠ gradle.properties EXPECT_CODE($FIXTURE_CODE) → 真实断言脚本拦截 =="
SB1=$TMP/wire1; setup_wiring_sandbox "$SB1"
run_wiring "$SB1" 999 "$TMP/wire1.log"; RC=$?
check "release_build.sh 非零退出" test "$RC" -ne 0 -a "$RC" -ne 124
check "日志含『versionCode 不一致』" grep -q "versionCode 不一致" "$TMP/wire1.log"
check "日志显示实际 999 / 期望 $FIXTURE_CODE（独立预期，非 APK 自读值）" grep -q "实际 '999'，期望 '$FIXTURE_CODE'" "$TMP/wire1.log"
check "沙箱 gradle.properties 未被改动（仍为 $FIXTURE_CODE）" grep -q "^alphagps.versionCode=$FIXTURE_CODE$" "$SB1/gradle.properties"

echo "== 场景 22: wiring — APK code == EXPECT_CODE → 真实 release_assert_apk.sh 全链通过 =="
SB2=$TMP/wire2; setup_wiring_sandbox "$SB2"
run_wiring "$SB2" "$FIXTURE_CODE" "$TMP/wire2.log"; RC=$?
check "release_build.sh 退出码 0" test "$RC" -eq 0
check "真实断言脚本通过（『全部断言通过』字样，非 stub）" grep -q "全部断言通过" "$TMP/wire2.log"
check "断言确认 versionCode=$FIXTURE_CODE" grep -q "versionCode=$FIXTURE_CODE" "$TMP/wire2.log"
check "注入后 app 源码 versionName 已还原为 $FIXTURE_NAME" grep -q "versionName = \"$FIXTURE_NAME\"" "$SB2/app/build.gradle.kts"
check "构建前后一致性检查通过（无 fail）" bash -c '! grep -q "FAIL" "$1"' _ "$TMP/wire2.log"

echo "== 场景 23: publish-only 对 LEGACY tag（v0.1.2）使用表值 3（沙箱，无网络） =="
SB3=$TMP/wire3; setup_wiring_sandbox "$SB3"
# v0.1.1（LEGACY=2，早期提交）+ v0.1.2（HEAD）；归档 0.1.2 并让 fake aapt 报 code=3
( cd "$SB3" && git -c user.email=t@t -c user.name=t commit -q --allow-empty -m pre && git tag v0.1.1 )
mkdir -p "$SB3/release"; : > "$SB3/release/alpha-gps-0.1.2.apk"
( cd "$SB3" && sha256sum release/alpha-gps-0.1.2.apk > release/SHA256SUMS \
  && git add -A && git -c user.email=t@t -c user.name=t commit -qm archive && git tag v0.1.2 )
( cd "$SB3" && PATH="$SB3/home/jdk/bin:$PATH" HOME="$SB3/home" JAVA_HOME="$SB3/home/jdk" \
    DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
    AAPT_CODE=3 AAPT_VNAME=0.1.2 \
    timeout 30 bash scripts/release_build.sh 0.1.2 --publish-only ) > "$TMP/wire3.log" 2>&1; RC=$?
check "走到凭据预取才停（归档断言全过；沙箱无 Primary forge 凭据 → 非零退出，非超时）" \
  test "$RC" -ne 0 -a "$RC" -ne 124
check "归档完整性校验通过（『归档完整』）" grep -q "归档完整" "$TMP/wire3.log"
check "单调性对 v0.1.1 用 LEGACY 表值 2（3 > v0.1.1(versionCode=2)）" \
  grep -q "3 > v0.1.1(versionCode=2)" "$TMP/wire3.log"
check "APK 断言用 EXPECT_CODE=3（versionCode=3）" grep -q "versionCode=3" "$TMP/wire3.log"
check "停在凭据预取（『凭据』字样）" grep -q "凭据" "$TMP/wire3.log"

echo "== 场景 24: publish-only 对非 LEGACY 且无 key 的 tag → fail-closed =="
SB4=$TMP/wire4; setup_wiring_sandbox "$SB4"
printf 'org.gradle.jvmargs=-Xmx1m\n' > "$SB4/gradle.properties"   # 无 key
mkdir -p "$SB4/release"; : > "$SB4/release/alpha-gps-0.9.9.apk"
( cd "$SB4" && sha256sum release/alpha-gps-0.9.9.apk > release/SHA256SUMS \
  && git add -A && git -c user.email=t@t -c user.name=t commit -qm archive && git tag v0.9.9 )
( cd "$SB4" && PATH="$SB4/home/jdk/bin:$PATH" HOME="$SB4/home" JAVA_HOME="$SB4/home/jdk" \
    DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
    AAPT_CODE=1 AAPT_VNAME=0.9.9 \
    timeout 30 bash scripts/release_build.sh 0.9.9 --publish-only ) > "$TMP/wire4.log" 2>&1; RC=$?
check "非零退出（非超时）" test "$RC" -ne 0 -a "$RC" -ne 124
check "日志含『无法读取/解析』" grep -q "无法读取/解析" "$TMP/wire4.log"

echo "== 场景 25: 变异验证——旧库（62bc54d 的 release_lib.sh）对照 =="
git -C "$HERE/.." show 62bc54d:scripts/release_lib.sh > "$TMP/old_lib.sh" 2>/dev/null
if [[ -s "$TMP/old_lib.sh" ]]; then
  # 旧式 Kotlin 模板：旧库 parse_version_code 仍可解析（兼容对照，应通过）
  printf '        applicationId = "cn.vectory.alphagps"\n        versionCode = 7\n' > "$TMP/oldstyle.gradle"
  OUT=$(bash -c "source '$TMP/old_lib.sh'; parse_version_code '$TMP/oldstyle.gradle'" 2>/dev/null)
  check "旧库解析旧式 Kotlin 模板 → 7（对照）" test "$OUT" == "7"
  # 本仓库迁移后的 app/build.gradle.kts（versionCode 已改为 providers.gradleProperty）：
  # 旧库必然 0 处失败——新契约（gradle.properties 单一来源）的迁移必要性证明
  OUT=$(bash -c "source '$TMP/old_lib.sh'; parse_version_code '$HERE/../app/build.gradle.kts'" 2>/dev/null); RC=$?
  check "旧库解析迁移后的 app/build.gradle.kts → 失败（0 处）" test "$RC" -ne 0
  OUT=$(bash -c "source '$TMP/old_lib.sh'; parse_version_code_props x" 2>/dev/null); RC=$?
  check "旧库无 parse_version_code_props API（不可运行旧用例，如实说明）" test "$RC" -ne 0
else
  check "旧库 fixture 可提取" test 1 -eq 0
fi

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
