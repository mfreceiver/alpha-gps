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
#   场景 21 P2-A URL 字符串含 /* 后接真实赋值 → 解析到真实值（不被字符串内 /* 吞掉）
#   场景 22 P2-A 成对标记字符串 "/*" 与 "*/" → 不构成注释，真实赋值正常解析
#   场景 23 P2-A 【设计决策变更】出现块注释 /* 即整体 fail-closed（不再支持嵌套块注释剥离）
#   场景 24 P2-A 【设计决策变更】全部赋值都在块注释内 → fail-closed（原因同 23）
#   场景 25 P2-A 真实 custom + 行注释内 upstream 伪赋值 → 判定 custom 且 code 取真实值
#   场景 26 P2-A 行尾注释 applicationId = "..." // c → 字符串完整保留后剥注释
#   场景 27 P2-A 【设计决策变更】raw string（"""）→ 不支持，fail-closed
#   场景 28 P2-A 字符串模板 ${...}（任何形式）→ 不支持构造 → fail-closed
#   场景 29 P2-A 未闭合块注释 / 未闭合字符串 / 未闭合 raw string → fail-closed
#   场景 30 P2-A NUL 字节输入（文件与 stdin 两路）→ 明确拒绝 fail-closed
#   场景 31 P2-A sol 复现：tag 源码含 URL+嵌套注释陷阱 → 块注释 fail-closed，assert 3 0.1.3 不得 rc0
#   场景 32 P2-B grep rc=2（函数覆盖）→ fail-closed
#   场景 33 P2-B grep rc=127（函数覆盖）→ fail-closed；python3 缺失 → 明确提示依赖
#   场景 34 sol-1 三引号开头（含四引号结尾变体）+ 注释伪赋值 → fail-closed
#   场景 35 sol-2 raw string 内伪赋值 / 多行赋值 → fail-closed；纯多行赋值 → 0 处 fail-closed
#   场景 36 sol-3 val s = """${foo""" → fail-closed
#   场景 37 CRLF 行尾（逐字节保留，不归一化）→ 正常解析
#   场景 38 首个 BOM 显式剥离 → 正常解析
#   场景 39 '\u002f' 完整 6 字符消费；截断 '\u00' → fail-closed
#   场景 40 普通字符串内裸换行 → fail-closed
#   场景 41 fd0 关闭（0<&-）→ 立即非零（不得挂起；timeout 5，超时视为失败），两 parser 各一条
#   场景 42 NUL 在 tag 调用层（git show 原始字节经临时文件，不经 bash 变量）→ fail-closed
#   场景 43 publish-only 接线：tag 源码 versionCode 含 NUL → fail-closed（不经 bash 变量丢 NUL）
#   场景 44 扫描失败路径不遗留临时文件（stdin 路径无 mktemp；git 路径任何退出均清理）
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
# 设计决策：窄白名单——CODE 状态出现 /* 即整体拒绝（不再解析块注释）
printf '/*\n versionCode = 9\n*/\n' > "$TMP/blockonly.gradle"
OUT=$(parse_version_code "$TMP/blockonly.gradle" 2>"$TMP/err"); RC=$?
check "非零退出（块注释不在支持集合）" test "$RC" -ne 0
check "未输出 9" test "$OUT" != "9"
check "错误含『不支持构造: 块注释』" grep -q "不支持构造: 块注释" "$TMP/err"

echo "== 场景 15: P2-2 有效赋值 + 行注释伪赋值并存 → 只取有效的那处 =="
printf '        // versionCode = 99\n        versionCode = 3\n' > "$TMP/mixed.gradle"
OUT=$(parse_version_code "$TMP/mixed.gradle" 2>"$TMP/err"); RC=$?
check "退出码 0 且解析为 3" test "$RC" -eq 0 -a "$OUT" == "3"
# 设计决策变更：同一文件再加 /* ... */ 伪赋值 → 整体 fail-closed（块注释不支持）
printf '        // versionCode = 99\n        /* versionCode = 88 */\n        versionCode = 3\n' > "$TMP/mixedblk.gradle"
OUT=$(parse_version_code "$TMP/mixedblk.gradle" 2>"$TMP/err"); RC=$?
check "含块注释伪赋值 → 非零（设计决策：块注释 fail-closed）" test "$RC" -ne 0
check "错误含『不支持构造: 块注释』" grep -q "不支持构造: 块注释" "$TMP/err"

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
           "$SB/home/.config/alphagps/signing" "$SB/sdk/build-tools/33.0.0"
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
  printf '#!/usr/bin/env bash\nif [[ "${1:-} ${2:-}" == "verify --print-certs" ]]; then printf "Signer #1 certificate DN: CN=release\\nSigner #1 certificate SHA-256 digest: %s\\n" "%s"; exit 0; fi\n[[ "${1:-}" == verify ]] && exit 0\nexit 1\n' \
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

# ── P2-A/P2-B：词法注释剥离与严格 tag 枚举 ──────────────────────────────────────
echo "== 场景 21: URL 字符串含 /* 后接真实赋值 → 解析真实值，不被吞 =="
cat > "$TMP/url.gradle" <<'UG'
val url = "https://example.test/*"
applicationId = "cn.vectory.alphagps"
versionCode = 9
UG
OUT=$(parse_application_id "$TMP/url.gradle" 2>"$TMP/err"); RC=$?
check "applicationId 解析为真实 custom" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"
OUT=$(parse_version_code "$TMP/url.gradle" 2>"$TMP/err"); RC=$?
check "versionCode 解析为真实 9" test "$RC" -eq 0 -a "$OUT" == "9"
# 设计决策变更：同一陷阱再加真实块注释（嵌套与否都一样）→ 整体 fail-closed
cat > "$TMP/urlblk.gradle" <<'UG'
val url = "https://example.test/*"
applicationId = "cn.vectory.alphagps"
versionCode = 9
/* outer comment
    /* inner comment */
applicationId = "com.saschl.cameragps"
versionCode = 3
*/
UG
OUT=$(parse_version_code "$TMP/urlblk.gradle" 2>"$TMP/err"); RC=$?
check "URL 陷阱 + 真实块注释 → 非零（块注释 fail-closed）" test "$RC" -ne 0
check "错误含『不支持构造: 块注释』" grep -q "不支持构造: 块注释" "$TMP/err"

echo "== 场景 22: 成对标记字符串 \"/*\" 与 \"*/\" → 不构成注释 =="
printf 'val a = "/*"\nval b = "*/"\napplicationId = "cn.vectory.alphagps"\nversionCode = 3\n' > "$TMP/pair.gradle"
OUT=$(parse_version_code "$TMP/pair.gradle" 2>"$TMP/err"); RC=$?
check "退出码 0 且解析为 3" test "$RC" -eq 0 -a "$OUT" == "3"
OUT=$(parse_application_id "$TMP/pair.gradle" 2>"$TMP/err"); RC=$?
check "applicationId 正常解析" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"

echo "== 场景 23: 【设计决策变更】出现块注释（嵌套）即整体 fail-closed =="
# 旧断言（嵌套正确剥离、只取注释外 3）已按窄白名单设计改为拒绝整份文件
printf 'versionCode = 3\n/* outer\n/* inner\nversionCode = 99\n*/\nversionCode = 88\n*/\n' > "$TMP/nest.gradle"
OUT=$(parse_version_code "$TMP/nest.gradle" 2>"$TMP/err"); RC=$?
check "非零退出（块注释不支持）" test "$RC" -ne 0
check "未输出 3/88/99" test "$OUT" != "3" -a "$OUT" != "88" -a "$OUT" != "9"
check "错误含『不支持构造: 块注释』" grep -q "不支持构造: 块注释" "$TMP/err"

echo "== 场景 24: 【设计决策变更】全部赋值都在块注释内 → 块注释 fail-closed =="
printf '/*\n/*\nversionCode = 9\n*/\nversionCode = 8\n*/\n' > "$TMP/allcomment.gradle"
OUT=$(parse_version_code "$TMP/allcomment.gradle" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "未输出 8/9" test "$OUT" != "8" -a "$OUT" != "9"
check "错误含『不支持构造: 块注释』" grep -q "不支持构造: 块注释" "$TMP/err"

echo "== 场景 25: 真实 custom + 行注释内 upstream 伪赋值 → 判定 custom 且取真实 code =="
R9=$TMP/decoy; make_git_repo "$R9"
commit_file "$R9" app/build.gradle.kts 'val url = "https://example.test/*"
applicationId = "cn.vectory.alphagps"
versionCode = 9
// applicationId = "com.saschl.cameragps"
// versionCode = 3' "v0.1.2 decoy"
git -C "$R9" tag v0.1.2
OUT=$(cd "$R9" && git show v0.1.2:app/build.gradle.kts | parse_application_id -); RC=$?
check "tag 身份判定为真实 custom" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"
OUT=$(cd "$R9" && assert_code_monotonic 10 0.1.3 2>"$TMP/err"); RC=$?
check "code 取真实值 9：10 > 9 通过且点名 v0.1.2(versionCode=9)" test "$RC" -eq 0 \
  -a "$(grep -c '10 > v0.1.2(versionCode=9)' <<<"$OUT")" -eq 1

echo "== 场景 26: 行尾注释 applicationId = \"...\" // c → 字符串完整保留 =="
printf 'applicationId = "cn.vectory.alphagps" // custom build\nversionCode = 3\n' > "$TMP/tailc.gradle"
OUT=$(parse_application_id "$TMP/tailc.gradle" 2>"$TMP/err"); RC=$?
check "退出码 0 且包名完整" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"

echo "== 场景 27: 【设计决策变更】raw string（\"\"\"）→ 不支持，fail-closed =="
printf 'val s = """\nhttp://example.test/a//b\n"""\nversionCode = 3\n' > "$TMP/raw.gradle"
OUT=$(parse_version_code "$TMP/raw.gradle" 2>"$TMP/err"); RC=$?
check "非零退出（三引号不在支持集合）" test "$RC" -ne 0
check "错误含『不支持构造: 三引号』" grep -q "不支持构造: 三引号" "$TMP/err"

echo "== 场景 28: 字符串模板 \${...} → 不支持构造 fail-closed =="
printf 'val x = "a${na"me}b"\nversionCode = 3\n' | rl_strip_kotlin_comments > "$TMP/tpl.out" 2>"$TMP/err"; RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『字符串模板』" grep -q "字符串模板" "$TMP/err"

echo "== 场景 29: 未闭合块注释 / 字符串 / raw string → fail-closed =="
printf 'versionCode = 3\n/* never closed\n' > "$TMP/ub.gradle"
OUT=$(parse_version_code "$TMP/ub.gradle" 2>"$TMP/err"); RC=$?
check "未闭合块注释 → 非零" test "$RC" -ne 0
check "错误含『不支持构造: 块注释』" test "$(grep -c '不支持构造: 块注释' "$TMP/err")" -eq 1
printf 'val url = "https://x /*\nversionCode = 3\n' > "$TMP/us.gradle"
OUT=$(parse_version_code "$TMP/us.gradle" 2>"$TMP/err"); RC=$?
check "未闭合字符串（含 /* 陷阱）→ 非零" test "$RC" -ne 0
check "错误含『字符串未闭合』" grep -q "字符串未闭合" "$TMP/err"
printf 'val s = """\nabc\nversionCode = 3\n' > "$TMP/urs.gradle"
OUT=$(parse_version_code "$TMP/urs.gradle" 2>"$TMP/err"); RC=$?
check "未闭合 raw string → 非零" test "$RC" -ne 0
check "错误含『不支持构造: 三引号』" grep -q "不支持构造: 三引号" "$TMP/err"

echo "== 场景 30: NUL 字节输入（文件与 stdin）→ 明确拒绝 =="
printf 'versionCode = 3\0\nversionCode = 4\n' > "$TMP/nul.gradle"
OUT=$(parse_version_code "$TMP/nul.gradle" 2>"$TMP/err"); RC=$?
check "文件路径含 NUL → 非零" test "$RC" -ne 0
check "错误含『NUL』" grep -q "NUL" "$TMP/err"
OUT=$(printf 'versionCode = 3\0\n' | parse_version_code - 2>"$TMP/err"); RC=$?
check "stdin 含 NUL → 非零" test "$RC" -ne 0
check "stdin 错误含『NUL』" grep -q "NUL" "$TMP/err"

echo "== 场景 31: sol 复现——tag 源码含 URL+嵌套注释陷阱 → 块注释 fail-closed，assert 3 0.1.3 不得 rc0 =="
R10=$TMP/trap; make_git_repo "$R10"
commit_file "$R10" app/build.gradle.kts 'val url = "https://example.test/*"
applicationId = "cn.vectory.alphagps"
versionCode = 9
/* outer
    /* inner */
applicationId = "com.saschl.cameragps"
versionCode = 3
*/' "v0.1.2 trap"
git -C "$R10" tag v0.1.2
OUT=$(cd "$R10" && assert_code_monotonic 3 0.1.3 2>"$TMP/err"); RC=$?
check "非零退出（含块注释 → 身份无法确定，绝不 WARN 放行）" test "$RC" -ne 0
check "错误含『身份无法确定』" grep -q "身份无法确定" "$TMP/err"

echo "== 场景 32: P2-B grep rc=2（函数覆盖）→ fail-closed =="
R11=$TMP/greprc2; make_git_repo "$R11"
commit_gradle "$R11" 2 0.1.1 cn.vectory.alphagps "v0.1.1"
git -C "$R11" tag v0.1.1
OUT=$(cd "$R11" && { grep() { return 2; }; assert_code_monotonic 3 0.1.2; } 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『tag 过滤 grep 异常（rc=2）』" grep -q "tag 过滤 grep 异常（rc=2）" "$TMP/err"

echo "== 场景 33: P2-B grep rc=127（函数覆盖）→ fail-closed；python3 缺失有明确提示 =="
R12=$TMP/greprc127; make_git_repo "$R12"
commit_gradle "$R12" 2 0.1.1 cn.vectory.alphagps "v0.1.1"
git -C "$R12" tag v0.1.1
OUT=$(cd "$R12" && { grep() { return 127; }; assert_code_monotonic 3 0.1.2; } 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『tag 过滤 grep 异常（rc=127）』" grep -q "tag 过滤 grep 异常（rc=127）" "$TMP/err"
OUT=$(PATH= rl_strip_kotlin_comments_file "$TMP/nest.gradle" 2>"$TMP/err" </dev/null); RC=$?
check "python3 缺失 → 非零且提示『缺少依赖 python3』" test "$RC" -ne 0 -a "$(grep -c '缺少依赖 python3' "$TMP/err")" -eq 1

echo "== 场景 34: sol-1 三引号开头（含四引号结尾变体）+ 注释伪赋值 → fail-closed =="
cat > "$TMP/tq.gradle" <<'TQ'
val a = """x"""" // open-quote variant
// versionCode = 99
val d = """"text"""
versionCode = 3
TQ
OUT=$(parse_version_code "$TMP/tq.gradle" 2>"$TMP/err"); RC=$?
check "非零退出（含 \"\"\" → fail-closed）" test "$RC" -ne 0
check "错误含『不支持构造: 三引号』" grep -q "不支持构造: 三引号" "$TMP/err"

echo "== 场景 35: sol-2 raw string 内伪赋值 / 多行赋值 → fail-closed；纯多行赋值 → 0 处 fail-closed =="
printf 'val note = """\napplicationId = "com.saschl.cameragps"\nversionCode = 1\n"""\napplicationId =\n "cn.vectory.alphagps"\nversionCode = 2\n' > "$TMP/rawtrap.gradle"
OUT=$(parse_version_code "$TMP/rawtrap.gradle" 2>"$TMP/err"); RC=$?
check "raw string 伪赋值 → 非零（含 \"\"\"）" test "$RC" -ne 0
check "错误含『不支持构造: 三引号』" grep -q "不支持构造: 三引号" "$TMP/err"
OUT=$(parse_application_id "$TMP/rawtrap.gradle" 2>"$TMP/err"); RC=$?
check "applicationId 同文件同样非零" test "$RC" -ne 0
# 无 raw string 的纯多行赋值（applicationId 与值分行）→ 不构成整行匹配 → 0 处 fail-closed
printf 'applicationId =\n "cn.vectory.alphagps"\nversionCode = 3\n' > "$TMP/ml.gradle"
OUT=$(parse_application_id "$TMP/ml.gradle" 2>"$TMP/err"); RC=$?
check "纯多行赋值 → 非零" test "$RC" -ne 0
check "错误含『实际 0 处』" grep -q "实际 0 处" "$TMP/err"

echo "== 场景 36: sol-3 val s = \"\"\"\${foo\"\"\" → fail-closed =="
printf 'val s = """${foo"""\nversionCode = 3\n' > "$TMP/rawtpl.gradle"
OUT=$(parse_version_code "$TMP/rawtpl.gradle" 2>"$TMP/err"); RC=$?
check "非零退出（\"\"\" 先于模板被拒绝）" test "$RC" -ne 0
check "错误含『不支持构造: 三引号』" grep -q "不支持构造: 三引号" "$TMP/err"

echo "== 场景 37: CRLF 行尾（逐字节保留，不归一化）→ 正常解析 =="
printf 'applicationId = "cn.vectory.alphagps"\r\nversionCode = 4\r\n// note\r\n' > "$TMP/crlf.gradle"
OUT=$(parse_version_code "$TMP/crlf.gradle" 2>"$TMP/err"); RC=$?
check "versionCode 解析为 4" test "$RC" -eq 0 -a "$OUT" == "4"
OUT=$(parse_application_id "$TMP/crlf.gradle" 2>"$TMP/err"); RC=$?
check "applicationId 正常解析" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"

echo "== 场景 38: 首个 BOM 显式剥离 → 正常解析 =="
printf '\xef\xbb\xbfapplicationId = "cn.vectory.alphagps"\nversionCode = 5\n' > "$TMP/bom.gradle"
OUT=$(parse_version_code "$TMP/bom.gradle" 2>"$TMP/err"); RC=$?
check "versionCode 解析为 5" test "$RC" -eq 0 -a "$OUT" == "5"
OUT=$(parse_application_id "$TMP/bom.gradle" 2>"$TMP/err"); RC=$?
check "applicationId 正常解析" test "$RC" -eq 0 -a "$OUT" == "cn.vectory.alphagps"

echo "== 场景 39: '\\u002f' 完整 6 字符消费；截断 '\\u00' → fail-closed =="
cat > "$TMP/uchar.gradle" <<'UC'
val c = '\u002f'
applicationId = "cn.vectory.alphagps"
versionCode = 6
UC
OUT=$(parse_version_code "$TMP/uchar.gradle" 2>"$TMP/err"); RC=$?
check "完整 \\uXXXX 字符字面量不破坏后续解析" test "$RC" -eq 0 -a "$OUT" == "6"
cat > "$TMP/ubad.gradle" <<'UB'
val c = '\u00'
versionCode = 3
UB
OUT=$(parse_version_code "$TMP/ubad.gradle" 2>"$TMP/err"); RC=$?
check "截断 \\uXXXX → 非零" test "$RC" -ne 0
check "错误含『\\uXXXX 转义非法/截断』" grep -q "uXXXX 转义非法/截断" "$TMP/err"

echo "== 场景 40: 普通字符串内裸换行 → fail-closed =="
printf 'val s = "abc\ndef"\nversionCode = 3\n' > "$TMP/nlstr.gradle"
OUT=$(parse_version_code "$TMP/nlstr.gradle" 2>"$TMP/err"); RC=$?
check "非零退出" test "$RC" -ne 0
check "错误含『裸换行』" grep -q "裸换行" "$TMP/err"

echo "== 场景 41: fd0 关闭（0<&-）→ 立即非零，不得挂起（timeout 5，超时 rc=124 视为失败） =="
timeout 5 bash -uc "source '$LIB'; parse_version_code -" 0<&- >/dev/null 2>"$TMP/err"; RC=$?
check "parse_version_code 非零且非超时" test "$RC" -ne 0 -a "$RC" -ne 124
check "错误含『stdin 不可用』" grep -q "stdin 不可用" "$TMP/err"
timeout 5 bash -uc "source '$LIB'; parse_application_id -" 0<&- >/dev/null 2>"$TMP/err"; RC=$?
check "parse_application_id 非零且非超时" test "$RC" -ne 0 -a "$RC" -ne 124
check "错误含『stdin 不可用』" grep -q "stdin 不可用" "$TMP/err"

echo "== 场景 42: NUL 在 tag 调用层（git show 原始字节经临时文件，不经 bash 变量）→ fail-closed =="
R13=$TMP/tagnul; make_git_repo "$R13"
mkdir -p "$R13/app"
# 内容含 NUL：printf 格式串内 \0 直接落盘（不经 bash 变量），git 可正常存储
printf 'applicationId = "cn.vectory.alphagps"\nversionCode = 3\0\n' > "$R13/app/build.gradle.kts"
git -C "$R13" add app/build.gradle.kts
git -C "$R13" -c user.email=t@t -c user.name=t commit -qm "nul gradle"
git -C "$R13" tag v0.1.2
OUT=$(cd "$R13" && assert_code_monotonic 4 0.1.3 2>"$TMP/err"); RC=$?
check "非零退出（旧路径 bash 变量丢 NUL 后 rc0 放行）" test "$RC" -ne 0
check "错误含『NUL』" grep -q "NUL" "$TMP/err"

echo "== 场景 43: publish-only 接线——tag 源码 versionCode 含 NUL → fail-closed =="
SB3=$TMP/wire3; setup_wiring_sandbox "$SB3"
# 覆写模板 gradle 为含 NUL 版本（release/ 归档一并入同一 commit，保持树干净后打 tag 于 HEAD）
printf 'applicationId = "cn.vectory.alphagps"\nversionCode = %s\0\nversionName = "%s"\n' \
  "$FIXTURE_CODE" "$FIXTURE_NAME" > "$SB3/app/build.gradle.kts"
mkdir -p "$SB3/release"; : > "$SB3/release/alpha-gps-0.1.3.apk"
( cd "$SB3" && sha256sum release/alpha-gps-0.1.3.apk > release/SHA256SUMS )
( cd "$SB3" && git add -A && git -c user.email=t@t -c user.name=t commit -qm nul \
  && git tag v0.1.3 )
( cd "$SB3" && PATH="$SB3/home/jdk/bin:$PATH" HOME="$SB3/home" JAVA_HOME="$SB3/home/jdk" \
    DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x \
    AAPT_CODE="$FIXTURE_CODE" AAPT_VNAME=0.1.3 \
    timeout 20 bash scripts/release_build.sh 0.1.3 --publish-only ) > "$TMP/wire3.log" 2>&1; RC=$?
check "release_build.sh --publish-only 非零退出（NUL 不被 bash 变量丢弃）" test "$RC" -ne 0 -a "$RC" -ne 124
check "日志含『NUL』" grep -q "NUL" "$TMP/wire3.log"

echo "== 场景 44: 扫描失败路径不遗留临时文件（stdin 路径无 mktemp；git 路径任何退出均清理） =="
LEFT=$(ls "${TMPDIR:-/tmp}"/rlstrip.* "${TMPDIR:-/tmp}"/rlgit.* "${TMPDIR:-/tmp}"/rlmono.* 2>/dev/null | wc -l)
check "无 rlstrip/rlgit/rlmono 遗留（实际 $LEFT）" test "$LEFT" -eq 0

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
