#!/usr/bin/env bash
# alpha-gps APK 断言（移植自另一个内部项目的发版脚本，fail-closed 全硬门）：
#   apksigner: verify 通过 + 单一签名者 + 证书 SHA256 与期望一致 + 拒绝 debug 主体
#   aapt badging: package=cn.vectory.alphagps（app/build.gradle.kts applicationId）、
#                 INTERNET 权限、versionName/versionCode 与期望一致
# 用法: scripts/release_assert_apk.sh <apk> <期望versionName> <期望versionCode> <期望证书SHA256>
set -euo pipefail

# shellcheck source=release_lib.sh
source "$(cd "$(dirname "$0")" && pwd)/release_lib.sh"

if [[ $# -ne 4 ]]; then
  echo "用法: $0 <apk> <期望versionName> <期望versionCode> <期望证书SHA256>" >&2
  exit 1
fi
APK_PATH=$1; EXPECT_NAME=$2; EXPECT_CODE=$3; EXPECT_SHA=$4
EXPECT_PACKAGE='cn.vectory.alphagps'   # app/build.gradle.kts applicationId

if [[ -t 2 ]]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_BLU=$'\033[34m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_BLU=""; C_RST=""
fi
fail() { echo "${C_RED}FAIL${C_RST} $1" >&2; exit 1; }
pass() { echo "${C_GRN}OK${C_RST}  $1"; }

[[ -f "$APK_PATH" ]] || fail "APK 不存在: $APK_PATH"

# ─── SDK 工具发现：local.properties sdk.dir → ANDROID_HOME → ~/Android/Sdk ──
REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
SDK_DIR=""
if [[ -f "$REPO_ROOT/local.properties" ]]; then
  SDK_DIR=$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$REPO_ROOT/local.properties" | head -1)
fi
[[ -z "$SDK_DIR" && -n "${ANDROID_HOME:-}" ]] && SDK_DIR=$ANDROID_HOME
[[ -z "$SDK_DIR" ]] && SDK_DIR=$HOME/Android/Sdk
BT_DIR=$(ls -d "$SDK_DIR"/build-tools/* 2>/dev/null | sort -V | tail -1)
[[ -n "$BT_DIR" && -x "$BT_DIR/apksigner" && -x "$BT_DIR/aapt" ]] \
  || fail "未找到 build-tools（SDK 发现链: local.properties → ANDROID_HOME → ~/Android/Sdk，当前 SDK_DIR=$SDK_DIR）"
APKSIGNER=$BT_DIR/apksigner
AAPT=$BT_DIR/aapt

# ─── 1. 签名断言 ─────────────────────────────────────────────────────────
"$APKSIGNER" verify "$APK_PATH" > /dev/null 2>&1 || fail "apksigner verify 未通过"
pass "apksigner verify 通过"

CERTS=$("$APKSIGNER" verify --print-certs "$APK_PATH" 2>/dev/null) || fail "apksigner --print-certs 失败"
SIGNER_LINES=$(grep -c '^Signer #[0-9]* certificate DN:' <<< "$CERTS" || true)
[[ "$SIGNER_LINES" -eq 1 ]] || fail "签名者数量必须恰为 1，实际: $SIGNER_LINES"

DN=$(sed -nE "s/^Signer #1 certificate DN: (.*)$/\1/p" <<< "$CERTS")
DN_LC=${DN,,}
if [[ "$DN_LC" == *"cn=android debug"* ]]; then
  fail "拒绝 debug 签名主体: $DN"
fi

CERT_SHA=$(sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)$/\1/p' <<< "$CERTS" | tr 'A-F' 'a-f')
[[ -n "$CERT_SHA" ]] || fail "无法提取签名证书 SHA256"
SHA_COUNT=$(grep -c '^Signer #1 certificate SHA-256 digest:' <<< "$CERTS" || true)
[[ "$SHA_COUNT" -eq 1 ]] || fail "SHA256 digest 行必须恰为 1，实际: $SHA_COUNT"
[[ "$CERT_SHA" == "${EXPECT_SHA,,}" ]] \
  || fail "签名证书 SHA256 不一致（实际 $CERT_SHA，期望 $EXPECT_SHA）"
pass "单一签名者、非 debug 主体、证书 SHA256 一致"

# ─── 2. badging 断言 ─────────────────────────────────────────────────────
BADGING=$("$AAPT" dump badging "$APK_PATH" 2>/dev/null) || fail "aapt dump badging 失败"
FIRST_LINE=$(head -1 <<< "$BADGING")

PKG_NAME=$(sed -nE "s/^package: name='([^']+)'.*$/\1/p" <<< "$FIRST_LINE")
[[ "$PKG_NAME" == "$EXPECT_PACKAGE" ]] \
  || fail "包名不一致（实际 '$PKG_NAME'，期望 '$EXPECT_PACKAGE'——app/build.gradle.kts applicationId）"

VERSION_NAME=$(sed -nE "s/^package:.*versionName='([^']+)'.*$/\1/p" <<< "$FIRST_LINE")
[[ "$VERSION_NAME" == "$EXPECT_NAME" ]] \
  || fail "versionName 不一致（实际 '$VERSION_NAME'，期望 '$EXPECT_NAME'）"

VERSION_CODE=$(sed -nE "s/^package:.*versionCode='([0-9]+)'.*$/\1/p" <<< "$FIRST_LINE")
[[ "$VERSION_CODE" == "$EXPECT_CODE" ]] \
  || fail "versionCode 不一致（实际 '$VERSION_CODE'，期望 '$EXPECT_CODE'）"

grep -q "^uses-permission: name='android.permission.INTERNET'$" <<< "$BADGING" \
  || fail "缺少 INTERNET 权限"
pass "package=$EXPECT_PACKAGE、INTERNET 权限、versionName=$VERSION_NAME、versionCode=$VERSION_CODE"

# ─── 3. ABI 断言（仅 arm64-v8a；app/build.gradle.kts ndk.abiFilters）──────
# lib/ 下所有文件条目必须位于 lib/arm64-v8a/ 且该目录至少有一个 .so；
# zip 纯目录条目（如 'lib/'，name 以 '/' 结尾）不参与判定。见 rl_check_apk_arm64。
ABI_OUT=$(rl_check_apk_arm64 "$APK_PATH") || fail "APK ABI 检查：zipfile 打开/读取失败（非有效 zip/APK）"
case "$ABI_OUT" in
  OK\ *) pass "APK 仅含 arm64-v8a（lib/arm64-v8a/ 下 ${ABI_OUT#OK } 个 .so）" ;;
  OPEN_FAIL*) fail "APK 无法作为 zip 打开（ABI 检查）: $ABI_OUT" ;;
  BAD_ABI*)  fail "APK 含非 arm64-v8a 的 native 库目录（必须仅 arm64-v8a）: $ABI_OUT" ;;
  NO_SO)     fail "APK 的 lib/arm64-v8a/ 下没有任何 .so（native 库缺失）" ;;
  *)         fail "APK ABI 检查输出异常: $ABI_OUT" ;;
esac

echo
echo "${C_GRN}全部断言通过${C_RST}: $(basename "$APK_PATH")"
