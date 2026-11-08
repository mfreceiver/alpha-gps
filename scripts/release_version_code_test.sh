#!/usr/bin/env bash
# release_lib.sh（parse_version_code / parse_application_id / assert_code_monotonic）
# + release_build.sh versionCode 独立预期 wiring 的自测。全部走 mock/fake（沙箱 git 仓库、
# fake aapt/apksigner，真实 scripts/release_assert_apk.sh），不依赖真实构建/发布。
#   场景 1  parse_version_code 正常解析（真实 app/build.gradle.kts，不钉死具体值）
#   场景 2  0 处匹配 → fail-closed
#   场景 3  多处匹配 → fail-closed
#   场景 4  stdin 解析（git show 风格）
#   场景 5  code 递增 → 单调性 pass（并命中正确的上一 tag）
#   场景 6  code 未递增 → 单调性 fail
#   场景 7  无任何前一 tag → 仅警告
#   场景 8  仅有上游 tag（applicationId 明确为上游值）→ 被过滤，仅警告（不误比 172 体系）
#   场景 9  P2-1a 多空格 applicationId 的定制版 tag 不得被当作上游跳过（未 bump → fail）
#   场景 10 P2-1b 注释伪 applicationId 的上游 tag 不得被误认成本定制版
#   场景 11 身份无法确定（0 处 applicationId）→ fail-closed
#   场景 12 身份无法确定（多处 applicationId）→ fail-closed
#   场景 13 tag 源码不可读 → fail-closed
#   场景 14 P2-2 仅块注释中的伪 versionCode → fail-closed（不得输出 9）
#   场景 15 P2-2 有效赋值 + 注释伪赋值并存 → 只取有效的那处
#   场景 16 P2-2 非法值（2147483648 / 2^64+1 / 010 / 0）→ 全部 fail-closed；2147483647 边界放行
#   场景 17 tag 枚举失败（非 git 仓库）→ fail-closed
#   场景 18 assert_code_monotonic 不污染调用方的 i 变量
#   场景 19 wiring：APK code ≠ 源码 EXPECT_CODE → release_build.sh 整链 fail-closed（真实断言脚本）
#   场景 20 wiring：APK code == 源码 EXPECT_CODE → 真实 release_assert_apk.sh 断言通过
# fixture 纪律：wiring 沙箱用测试内固定模板 gradle（FIXTURE_CODE/FIXTURE_NAME 常量），
# 与仓库真实 versionCode/versionName 解耦——仓库 bump 后本测试依然通过。
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
LIB=$HERE/release_lib.sh
# shellcheck source=release_lib.sh
source "$LIB"

FIXTURE_CODE=3      # wiring 沙箱模板 gradle 的 versionCode（测试内常量，与真实仓库解耦）
FIXTURE_NAME=0.1.2  # 同上，versionName

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

echo "== 场景 1: parse_version_code 正常解析（真实源文件，不钉死值） =="
OUT=$(parse_version_code "$HERE/../app/build.gradle.kts"); RC=$?
check "退出码 0" test "$RC" -eq 0
check "输出为 canonical 正整数" bash -c '[[ "$1" =~ ^[1-9][0-9]{0,9}$ ]]' _ "$OUT"

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

# ── 沙箱 git 仓库（单调性门禁用；一律 /tmp 构造，用后随 $TMP 删除，不碰真实仓库）──
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
commit_file() { # $1=repo $2=相对路径 $3=内容 $4=msg（自动建父目录）
  mkdir -p "$1/$(dirname "$2")"
  printf '%s\n' "$3" > "$1/$2"
  git -C "$1" add "$2"
  git -C "$1" -c user.email=t@t -c user.name=t commit -qm "$4"
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
git -C "$R3" tag v0.0.9   # 版本号小于当前，但 applicationId 明确属上游 → 必须被跳过
commit_gradle "$R3" 1 0.1.0 cn.vectory.alphagps "custom first"
OUT=$(cd "$R3" && assert_code_monotonic 1 0.1.0 2>"$TMP/err"); RC=$?
check "退出码 0（找不到本定制版前一 tag 仅警告）" test "$RC" -eq 0
check "stderr 含仅警告字样" grep -q "仅警告" "$TMP/err"
check "未与上游 tag 172 比较（stdout/stderr 均无 172）" test \
  "$( { grep -o '172' <<<"$OUT" || true; grep -o '172' "$TMP/err" || true; } | wc -l)" -eq 0

echo "== 场景 9: P2-1a 多空格 applicationId 的定制版 tag 不得被当作上游跳过 =="
R4=$TMP/wsfixed; make_git_repo "$R4"
mkdir -p "$R4/app"
commit_file "$R4" app/build.gradle.kts '        applicationId    =      "cn.vectory.alphagps"
        versionCode = 5
        versionName = "0.1.1"' "wsgradle"
git -C "$R4" tag v0.1.1
OUT=$(cd "$R4" && parse_application_id - < <(git show v0.1.1:app/build.gradle.kts) 2>"$TMP/err"); RC=$?
check "多空格写法能解析出 applicationId" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"
OUT=$(cd "$R4" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "未 bump（3 < tag 的 5）→ 非零退出（旧 grep 子串匹配会误当上游跳过放行）" test "$RC" -ne 0
check "错误点名 v0.1.1(versionCode=5)" grep -q "tag v0.1.1 递增（其 versionCode=5）" "$TMP/err"
OUT=$(cd "$R4" && assert_code_monotonic 6 0.1.2 2>"$TMP/err"); RC=$?
check "已 bump（6 > 5）→ 通过且命中该 tag" test "$RC" -eq 0
check "输出确认 6 > v0.1.1(5)" grep -q "6 > v0.1.1(versionCode=5)" <<<"$OUT"

echo "== 场景 10: P2-1b 注释伪 applicationId 的上游 tag 不得被误认成本定制版 =="
R5=$TMP/fakeid; make_git_repo "$R5"
commit_file "$R5" app/build.gradle.kts '        // applicationId = "cn.vectory.alphagps"
        applicationId = "com.saschl.cameragps"
        versionCode = 172
        versionName = "0.0.9"' "upstream with decoy comment"
git -C "$R5" tag v0.0.9
commit_gradle "$R5" 1 0.1.0 cn.vectory.alphagps "custom first"
OUT=$(cd "$R5" && assert_code_monotonic 1 0.1.0 2>"$TMP/err"); RC=$?
check "退出码 0（上游 tag 被正确跳过，仅警告）" test "$RC" -eq 0
check "未与 172 误比（stdout/stderr 均无 172）" test \
  "$( { grep -o '172' <<<"$OUT" || true; grep -o '172' "$TMP/err" || true; } | wc -l)" -eq 0

echo "== 场景 11: 身份无法确定（0 处 applicationId）→ fail-closed =="
R6=$TMP/noid; make_git_repo "$R6"
commit_file "$R6" app/build.gradle.kts '        versionCode = 2
        versionName = "0.1.1"' "no applicationId"
git -C "$R6" tag v0.1.1
commit_gradle "$R6" 3 0.1.2 cn.vectory.alphagps "cur"
OUT=$(cd "$R6" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "非零退出（不得静默当上游放行）" test "$RC" -ne 0
check "错误含『身份无法确定』" grep -q "身份无法确定" "$TMP/err"

echo "== 场景 12: 身份无法确定（多处 applicationId）→ fail-closed =="
R7=$TMP/twoid; make_git_repo "$R7"
commit_file "$R7" app/build.gradle.kts '        applicationId = "cn.vectory.alphagps"
        applicationId = "com.saschl.cameragps"
        versionCode = 2
        versionName = "0.1.1"' "two applicationIds"
git -C "$R7" tag v0.1.1
commit_gradle "$R7" 3 0.1.2 cn.vectory.alphagps "cur"
OUT=$(cd "$R7" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『身份无法确定』" grep -q "身份无法确定" "$TMP/err"

echo "== 场景 13: tag 源码不可读（tag 提交缺 app/build.gradle.kts）→ fail-closed =="
R8=$TMP/nofile; make_git_repo "$R8"
git -C "$R8" -c user.email=t@t -c user.name=t commit -q --allow-empty -m "no gradle"
git -C "$R8" tag v0.1.1
commit_gradle "$R8" 3 0.1.2 cn.vectory.alphagps "cur"
OUT=$(cd "$R8" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『不可读』" grep -q "不可读" "$TMP/err"

echo "== 场景 14: P2-2 仅块注释中的伪 versionCode → fail-closed =="
printf '/*\n versionCode = 9\n*/\n' > "$TMP/blockonly.gradle"
OUT=$(parse_version_code "$TMP/blockonly.gradle" 2>"$TMP/err"); RC=$?
check "非零退出（旧实现会输出 9 放行）" test "$RC" -ne 0
check "未输出 9" test "$OUT" != "9"
check "错误含『实际 0 处』" grep -q "实际 0 处" "$TMP/err"

echo "== 场景 15: P2-2 有效赋值 + 注释伪赋值并存 → 只取有效的那处 =="
printf '        // versionCode = 99\n        /* versionCode = 88 */\n        versionCode = 3\n' > "$TMP/mixed.gradle"
OUT=$(parse_version_code "$TMP/mixed.gradle" 2>"$TMP/err"); RC=$?
check "退出码 0 且解析为 3" test "$RC" -eq 0 -a "$OUT" == "3"

echo "== 场景 16: P2-2 非法值全部 fail-closed；2147483647 边界放行 =="
for bad in 2147483648 18446744073709551617 010 0; do
  printf '        versionCode = %s\n' "$bad" > "$TMP/bad.gradle"
  OUT=$(parse_version_code "$TMP/bad.gradle" 2>"$TMP/err"); RC=$?
  check "非法值 $bad → 非零退出" test "$RC" -ne 0
  check "非法值 $bad → 错误含『非法值』" grep -q "非法值" "$TMP/err"
done
printf '        versionCode = 2147483647\n' > "$TMP/max.gradle"
OUT=$(parse_version_code "$TMP/max.gradle" 2>"$TMP/err"); RC=$?
check "边界 2147483647 → 放行" test "$RC" -eq 0 -a "$OUT" == "2147483647"

echo "== 场景 17: tag 枚举失败（非 git 仓库）→ fail-closed =="
OUT=$(cd "$TMP" && assert_code_monotonic 3 0.1.2 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『枚举失败』" grep -q "枚举失败" "$TMP/err"

echo "== 场景 18: assert_code_monotonic 不污染调用方的 i 变量 =="
(
  cd "$R" || exit 1
  i=KEEP_SENTINEL
  assert_code_monotonic 3 0.1.2 >/dev/null 2>&1
  if [[ "$i" == "KEEP_SENTINEL" ]]; then exit 0; else exit 1; fi
); RC=$?
check "调用前后外部变量 i 不变（循环变量已 local）" test "$RC" -eq 0

# ── release_build.sh wiring（沙箱仓库 + fake aapt/apksigner + 真实断言脚本）────
# 用真实 scripts/release_assert_apk.sh：其 SDK 发现链 local.properties sdk.dir →
# ANDROID_HOME → ~/Android/Sdk，沙箱用 local.properties 指向 fake SDK（不改生产行为）。
# fake aapt 的 badging 首行由 AAPT_CODE / AAPT_VNAME 驱动；fake apksigner 报钉定证书。
setup_wiring_sandbox() { # $1=沙箱根
  local SB=$1
  rm -rf -- "$SB"
  mkdir -p "$SB/scripts" "$SB/app" "$SB/home/jdk/bin" "$SB/home/.gradle" \
           "$SB/home/.config/dshdroid/signing" "$SB/sdk/build-tools/33.0.0"
  cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" \
        "$HERE/release_assert_apk.sh" "$SB/scripts/"
  # 固定模板 gradle（与真实仓库 versionCode/versionName 解耦）
  printf '        applicationId = "cn.vectory.alphagps"\n        versionCode = %s\n        versionName = "%s"\n' \
    "$FIXTURE_CODE" "$FIXTURE_NAME" > "$SB/app/build.gradle.kts"
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
  # fake apksigner：verify 通过 + 单一签名者 + 钉定证书 SHA256（无冒号十六进制）
  printf '#!/usr/bin/env bash\nif [[ "${1:-} ${2:-}" == "verify --print-certs" ]]; then printf "Signer #1 certificate DN: CN=dshdroid\\nSigner #1 certificate SHA-256 digest: %s\\n" "%s"; exit 0; fi\n[[ "${1:-}" == verify ]] && exit 0\nexit 1\n' \
    "$pin" > "$SB/sdk/build-tools/33.0.0/apksigner"
  chmod +x "$SB/sdk/build-tools/33.0.0/apksigner"
  # fake aapt：badging 首行由 AAPT_CODE / AAPT_VNAME 驱动，并报 INTERNET 权限
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
  printf 'placeholder\n' > "$SB/home/.config/dshdroid/signing/dshdroid.jks"
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

echo "== 场景 19: wiring — APK code(999) ≠ 源码 EXPECT_CODE($FIXTURE_CODE) → 真实断言脚本拦截 =="
SB1=$TMP/wire1; setup_wiring_sandbox "$SB1"
run_wiring "$SB1" 999 "$TMP/wire1.log"; RC=$?
check "release_build.sh 非零退出（篡改 APK code 被真实断言脚本拦截）" test "$RC" -ne 0
check "日志含『versionCode 不一致』" grep -q "versionCode 不一致" "$TMP/wire1.log"
check "日志显示实际 999 / 期望 $FIXTURE_CODE（独立预期，非 APK 自读值）" grep -q "实际 '999'，期望 '$FIXTURE_CODE'" "$TMP/wire1.log"
check "源码 versionCode 未被改动（仍为 $FIXTURE_CODE）" bash -c 'grep -q "^        versionCode = $2$" "$1/app/build.gradle.kts"' _ "$SB1" "$FIXTURE_CODE"

echo "== 场景 20: wiring — APK code == 源码 EXPECT_CODE → 真实 release_assert_apk.sh 全链通过 =="
SB2=$TMP/wire2; setup_wiring_sandbox "$SB2"
run_wiring "$SB2" "$FIXTURE_CODE" "$TMP/wire2.log"; RC=$?
check "release_build.sh 退出码 0" test "$RC" -eq 0
check "真实断言脚本通过（『全部断言通过』字样，非 stub）" grep -q "全部断言通过" "$TMP/wire2.log"
check "断言确认 versionCode=$FIXTURE_CODE" grep -q "versionCode=$FIXTURE_CODE" "$TMP/wire2.log"
check "注入后源码 versionName 已还原为 $FIXTURE_NAME" grep -q "versionName = \"$FIXTURE_NAME\"" "$SB2/app/build.gradle.kts"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
