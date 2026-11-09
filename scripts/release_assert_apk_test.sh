#!/usr/bin/env bash
# rl_check_apk_arm64 / rl_check_apk_lang（scripts/release_lib.sh）自测：纯离线、无网络、无 git 仓库依赖。
# 用 python3 构造最小 zip，验证 release_assert_apk.sh 第 3 节 ABI 断言与第 4 节语言断言的判定：
#   场景 1  lib/ 目录项 + lib/arm64-v8a/x.so → OK（目录项不再被误拒）
#   场景 2  lib/ 目录项 + lib/x86_64/x.so → BAD_ABI（非法 ABI 拒绝）
#   场景 3  仅 lib/ 目录项（无任何 .so）→ NO_SO
#   场景 4  非 zip 文件 → OPEN_FAIL
#   场景 5  无目录项、两个 arm64 .so → OK 2
#   场景 6  composeResources values+values-zh-rCN（drawable 忽略）→ OK
#   场景 7  composeResources 残留 values-de → BAD_LANG
#   场景 8  仅 values-zh-rCN（无 en 基线）→ NO_VALUES
#   场景 9  无 composeResources 条目 → NO_VALUES
#   场景 10 非 zip 文件 → OPEN_FAIL（语言检查）
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

# mkzip <输出.zip> <条目...>（条目以 '/' 结尾写入为目录项，否则写入小文件）
mkzip() {
  local out=$1; shift
  python3 - "$out" "$@" <<'PY'
import sys, zipfile
out, entries = sys.argv[1], sys.argv[2:]
with zipfile.ZipFile(out, "w") as z:
    for e in entries:
        if e.endswith("/"):
            z.writestr(e, b"")           # 显式目录项
        else:
            z.writestr(e, b"\x7fELFfake")
PY
}

echo "== 场景 1: lib/ 目录项 + arm64 .so → OK（目录项容忍） =="
Z1=$TMP/ok_dir.apk; mkzip "$Z1" "lib/" "lib/arm64-v8a/libx.so"
OUT=$(rl_check_apk_arm64 "$Z1")
check "输出 OK 1" test "$OUT" == "OK 1"

echo "== 场景 2: lib/x86_64/x.so → BAD_ABI =="
Z2=$TMP/bad_abi.apk; mkzip "$Z2" "lib/" "lib/arm64-v8a/libx.so" "lib/x86_64/libx.so"
OUT=$(rl_check_apk_arm64 "$Z2")
check "输出 BAD_ABI 且点名 x86_64 条目" bash -c '[[ "$1" == BAD_ABI* ]] && [[ "$1" == *lib/x86_64/libx.so* ]]' _ "$OUT"

echo "== 场景 3: 仅 lib/ 目录项 → NO_SO =="
Z3=$TMP/no_so.apk; mkzip "$Z3" "lib/"
OUT=$(rl_check_apk_arm64 "$Z3")
check "输出 NO_SO" test "$OUT" == "NO_SO"

echo "== 场景 4: 非 zip 文件 → OPEN_FAIL =="
Z4=$TMP/not_zip.apk; printf 'this is not a zip\n' > "$Z4"
OUT=$(rl_check_apk_arm64 "$Z4")
check "输出 OPEN_FAIL" bash -c '[[ "$1" == OPEN_FAIL* ]]' _ "$OUT"

echo "== 场景 5: 无目录项、两个 arm64 .so → OK 2 =="
Z5=$TMP/ok_nodir.apk; mkzip "$Z5" "lib/arm64-v8a/liba.so" "lib/arm64-v8a/libb.so"
OUT=$(rl_check_apk_arm64 "$Z5")
check "输出 OK 2" test "$OUT" == "OK 2"

echo "== 场景 6: composeResources values + values-zh-rCN → OK =="
Z6=$TMP/lang_ok.apk; PKG="assets/composeResources/cameragps.sharednew.generated.resources"
mkzip "$Z6" "lib/arm64-v8a/libx.so" "$PKG/values/strings.cvr" "$PKG/values-zh-rCN/strings.cvr" "$PKG/drawable/icon.png"
OUT=$(rl_check_apk_lang "$Z6")
check "输出 OK values,values-zh-rCN（drawable 忽略）" test "$OUT" == "OK values,values-zh-rCN"

echo "== 场景 7: composeResources 残留 values-de → BAD_LANG =="
Z7=$TMP/lang_de.apk; mkzip "$Z7" "$PKG/values/strings.cvr" "$PKG/values-zh-rCN/strings.cvr" "$PKG/values-de/strings.cvr"
OUT=$(rl_check_apk_lang "$Z7")
check "输出 BAD_LANG 且点名 values-de" bash -c '[[ "$1" == BAD_LANG* ]] && [[ "$1" == *values-de* ]]' _ "$OUT"

echo "== 场景 8: composeResources 无 values（仅 values-zh-rCN）→ NO_VALUES =="
Z8=$TMP/lang_noen.apk; mkzip "$Z8" "$PKG/values-zh-rCN/strings.cvr"
OUT=$(rl_check_apk_lang "$Z8")
check "输出 NO_VALUES" test "$OUT" == "NO_VALUES"

echo "== 场景 9: 无 composeResources 条目 → NO_VALUES（缺 en 基线即拒绝） =="
Z9=$TMP/lang_none.apk; mkzip "$Z9" "lib/arm64-v8a/libx.so"
OUT=$(rl_check_apk_lang "$Z9")
check "输出 NO_VALUES" test "$OUT" == "NO_VALUES"

echo "== 场景 10: 非 zip 文件 → OPEN_FAIL（语言检查同样 fail-closed） =="
OUT=$(rl_check_apk_lang "$Z4")
check "输出 OPEN_FAIL" bash -c '[[ "$1" == OPEN_FAIL* ]]' _ "$OUT"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
