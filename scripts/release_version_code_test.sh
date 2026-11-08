#!/usr/bin/env bash
# release_lib.sh（parse_version_code / assert_code_monotonic）+ release_build.sh
# versionCode 独立预期 wiring 的自测。全部走 mock/fake（沙箱 git 仓库、stub aapt、
# stub release_assert_apk.sh 复刻其 :74-76 的比较语义），不依赖真实构建/发布。
#   场景 1  parse_version_code 正常解析（真实 app/build.gradle.kts）
#   场景 2  0 处匹配 → fail-closed
#   场景 3  多处匹配 → fail-closed
#   场景 4  stdin 解析（git show 风格）
#   场景 5  code 递增 → 单调性 pass（并命中正确的上一 tag）
#   场景 6  code 未递增 → 单调性 fail
#   场景 7  无任何前一 tag → 仅警告
#   场景 8  仅有上游 tag（applicationId 不同）→ 被过滤，仅警告（不误比 172 体系）
#   场景 9  wiring：APK code ≠ 源码 EXPECT_CODE → release_build.sh 整链 fail-closed
#   场景 10 wiring：APK code == 源码 EXPECT_CODE → 断言通过
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
LIB=$HERE/release_lib.sh
# shellcheck source=release_lib.sh
source "$LIB"

TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT

PASS=0; FAILED=0
check() { # check <desc> <expr...>
  local desc=$1; shift
  if "$@"; then PASS=$((PASS+1)); echo "ok   - $desc"
  else FAILED=$((FAILED+1)); echo "FAIL - $desc"; fi
}

GRADLE_TPL() { # $1=code $2=versionName $3=applicationId → stdout
  printf '        applicationId = "%s"\n        versionCode = %s\n        versionName = "%s"\n' "$3" "$1" "$2"
}

echo "== 场景 1: parse_version_code 正常解析（真实源文件） =="
OUT=$(parse_version_code "$HERE/../app/build.gradle.kts"); RC=$?
check "退出码 0" test "$RC" -eq 0
check "输出为纯数字且 >0" test "$OUT" -gt 0

echo "== 场景 2: 0 处匹配 → fail-closed =="
GRADLE_TPL 3 0.1.2 cn.vectory.alphagps | sed 's/versionCode.*//' > "$TMP/zero.gradle"
OUT=$(parse_version_code "$TMP/zero.gradle" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误信息含关键字『恰好匹配 1 处』" grep -q "恰好匹配 1 处" "$TMP/err"

echo "== 场景 3: 多处匹配 → fail-closed =="
{ GRADLE_TPL 3 0.1.2 cn.vectory.alphagps; GRADLE_TPL 4 0.1.3 cn.vectory.alphagps; } > "$TMP/two.gradle"
OUT=$(parse_version_code "$TMP/two.gradle" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误信息含实际匹配数『2 处』" grep -q "实际 2 处" "$TMP/err"

echo "== 场景 4: stdin 解析（git show 风格） =="
OUT=$(GRADLE_TPL 7 9.9.9 cn.vectory.alphagps | parse_version_code -); RC=$?
check "退出码 0 且解析为 7" test "$RC" -eq 0 -a "$OUT" == "7"

# ── 沙箱 git 仓库（单调性门禁用）──────────────────────────────────────────
make_git_repo() { # $1=目录; 之后调用方自行 commit/tag
  mkdir -p "$1"
  git -C "$1" init -q
  git -C "$1" -c user.email=t@t -c user.name=t commit -q --allow-empty -m init
}
commit_gradle() { # $1=repo $2=code $3=versionName $4=applicationId $5=msg
  mkdir -p "$1/app"
  GRADLE_TPL "$2" "$3" "$4" > "$1/app/build.gradle.kts"
  git -C "$1" add app/build.gradle.kts
  git -C "$1" -c user.email=t@t -c user.name=t commit -qm "$5"
}

echo "== 场景 5: code 递增 → 单调性 pass =="
R=$TMP/mono; make_git_repo "$R"
commit_gradle "$R" 2 0.1.1 cn.vectory.alphagps "v0.1.1"
git -C "$R" tag v0.1.1
commit_gradle "$R" 3 0.1.2 cn.vectory.alphagps "v0.1.2"
OUT=$(git -C "$R" show v0.1.1:app/build.gradle.kts | parse_version_code -)
check "上一 tag v0.1.1 源码 code 解析为 2" test "$OUT" == "2"
OUT=$(cd "$R" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "退出码 0" test "$RC" -eq 0
check "输出确认 3 > v0.1.1(2)" grep -q "3 > v0.1.1(versionCode=2)" <<<"$OUT"

echo "== 场景 6: code 未递增 → 单调性 fail =="
OUT=$(cd "$R" && assert_code_monotonic 2 0.1.3 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误信息含『未相对上一发布 tag』" grep -q "未相对上一发布 tag" "$TMP/err"
check "错误信息点名上一 tag v0.1.1 及其 code=2" grep -q "tag v0.1.1 递增（其 versionCode=2）" "$TMP/err"

echo "== 场景 7: 无任何前一 tag → 仅警告 =="
R2=$TMP/empty; make_git_repo "$R2"
commit_gradle "$R2" 1 0.1.0 cn.vectory.alphagps "first"
OUT=$(cd "$R2" && assert_code_monotonic 1 0.1.0 2>"$TMP/err"); RC=$?
check "退出码 0（警告不拦截）" test "$RC" -eq 0
check "stderr 含『跳过 versionCode 单调性比较（仅警告）』" grep -q "跳过 versionCode 单调性比较（仅警告）" "$TMP/err"

echo "== 场景 8: 仅有上游 tag → 被过滤，仅警告（不误比 172 体系） =="
R3=$TMP/upstream; make_git_repo "$R3"
commit_gradle "$R3" 172 0.0.9 com.saschl.cameragps "upstream old"
git -C "$R3" tag v0.0.9   # 版本号小于当前，但 applicationId 属上游 → 必须被跳过
commit_gradle "$R3" 1 0.1.0 cn.vectory.alphagps "custom first"
OUT=$(cd "$R3" && assert_code_monotonic 1 0.1.0 2>"$TMP/err"); RC=$?
check "退出码 0（找不到本定制版前一 tag 仅警告）" test "$RC" -eq 0
check "stderr 含仅警告字样" grep -q "仅警告" "$TMP/err"
check "未与上游 tag 172 比较（stdout/stderr 均无 172）" test \
  "$( { grep -o '172' <<<"$OUT" || true; grep -o '172' "$TMP/err" || true; } | wc -l)" -eq 0

# ── release_build.sh wiring（沙箱仓库 + fake aapt + stub 断言脚本）────────
# stub release_assert_apk.sh 复刻真实脚本 :74-76 的 versionCode 比较语义：
# 期望值取 $3，实际值取 fake aapt 报出的 AAPT_CODE（不经真实构建）。
setup_wiring_sandbox() { # $1=沙箱根
  local SB=$1
  rm -rf -- "$SB"
  mkdir -p "$SB/scripts" "$SB/app" "$SB/home/jdk/bin" "$SB/home/.gradle" \
           "$SB/home/.config/alphagps/signing" "$SB/sdk/build-tools/33.0.0"
  cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" "$SB/scripts/"
  cp -- "$HERE/../app/build.gradle.kts" "$SB/app/build.gradle.kts"
  cat > "$SB/scripts/release_assert_apk.sh" <<'STUB'
#!/usr/bin/env bash
# stub：复刻真实 release_assert_apk.sh :74-76 —— 实际 code 取 fake aapt 的 AAPT_CODE
ACTUAL="${AAPT_CODE:?stub 需 AAPT_CODE}"
if [[ "$ACTUAL" != "$3" ]]; then
  echo "FAIL versionCode 不一致（实际 '$ACTUAL'，期望 '$3'）" >&2
  exit 1
fi
echo "OK stub assert: versionCode=$3"
exit 0
STUB
  chmod +x "$SB/scripts/release_assert_apk.sh"
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
  # fake aapt：badging 首行由 AAPT_CODE / AAPT_VNAME 驱动
  cat > "$SB/sdk/build-tools/33.0.0/aapt" <<'AAPT'
#!/usr/bin/env bash
if [[ "${1:-} ${2:-}" == "dump badging" ]]; then
  printf "package: name='cn.vectory.alphagps' versionCode='%s' versionName='v%s'\n" "${AAPT_CODE:?}" "${AAPT_VNAME:?}"
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
      bash scripts/release_build.sh 0.1.3 --allow-dirty ) > "$3" 2>&1
}

echo "== 场景 9: wiring — APK code(999) ≠ 源码 EXPECT_CODE(3) → 整链 fail-closed =="
SB1=$TMP/wire1; setup_wiring_sandbox "$SB1"
run_wiring "$SB1" 999 "$TMP/wire1.log"; RC=$?
check "release_build.sh 非零退出（篡改 APK code 被拦截）" test "$RC" -ne 0
check "日志含『versionCode 不一致』" grep -q "versionCode 不一致" "$TMP/wire1.log"
check "日志显示实际 999 / 期望 3（独立预期，非 APK 自读值）" grep -q "实际 '999'，期望 '3'" "$TMP/wire1.log"
check "源码 versionCode 未被改动（仍为 3）" bash -c 'grep -q "^        versionCode = 3$" "$1/app/build.gradle.kts"' _ "$SB1"

echo "== 场景 10: wiring — APK code(3) == 源码 EXPECT_CODE(3) → 断言通过 =="
SB2=$TMP/wire2; setup_wiring_sandbox "$SB2"
run_wiring "$SB2" 3 "$TMP/wire2.log"; RC=$?
check "release_build.sh 退出码 0" test "$RC" -eq 0
check "日志含 stub 断言通过（versionCode=3）" grep -q "versionCode=3" "$TMP/wire2.log"
check "注入后源码 versionName 已还原为 0.1.2" grep -q 'versionName = "0.1.2"' "$SB2/app/build.gradle.kts"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
