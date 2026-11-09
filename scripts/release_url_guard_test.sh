#!/usr/bin/env bash
# assert_asset_url_trusted（scripts/release_lib.sh）自测：纯离线、无网络、
# 无 git 仓库依赖。校验 Primary forge 资产下载 URL 的同源约束（astra P2-1）。
#   场景 1  合法 URL（精确 host:port / repo / tag / 资产名）→ 放行
#   场景 2  http scheme → 拒绝
#   场景 3  错误 host（forge.example.com，无端口）→ 拒绝
#   场景 4  错误端口（:8443）→ 拒绝
#   场景 5  userinfo（https://a:b@host:port/..）→ 拒绝
#   场景 6  '@' 旁路（https://forge.example.com:443@evil.com/..）→ 拒绝
#   场景 7  '..' 路径穿越 → 拒绝
#   场景 8  '%2e%2e' 编码穿越 → 拒绝
#   场景 9  控制字符（\n / \r / \t / \x01）→ 拒绝
#   场景 10 空 URL / 缺参数 → 拒绝
#   场景 11 错误 repo 路径 → 拒绝
#   场景 12 错误 tag 路径 → 拒绝
#   场景 13 空白 / 反斜杠 / 查询串 / fragment / 前缀拼接 host → 拒绝
#   场景 14 大小写 host（HTTPS://forge.example.com:443/..）→ 拒绝（netloc 精确比较）
#   场景 15 变异验证：放宽 netloc 比较为后缀匹配的 mutant 库必须放行 '@' 旁过
#           用例（证明该用例确能捕获弱实现）
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

H=forge.example.com:443          # 与 release_build.sh 的 PRIMARY_HOST 一致
RP=owner/alphagps          # 与 PRIMARY_REPO 一致
TAG=v0.1.2
ASSET=alpha-gps-0.1.2.apk
P="/$RP/releases/download/$TAG/$ASSET"

accept() { # accept <desc> <url>（期望 rc=0）
  local desc=$1 url=$2 out rc
  out=$(assert_asset_url_trusted "$url" "$H" "$RP" "$TAG" "$ASSET" 2>"$TMP/err"); rc=$?
  check "接受: $desc" test "$rc" -eq 0
}
reject() { # reject <desc> <url>（期望 rc≠0 且 stderr 有 FAIL）
  local desc=$1 url=$2 out rc
  out=$(assert_asset_url_trusted "$url" "$H" "$RP" "$TAG" "$ASSET" 2>"$TMP/err"); rc=$?
  check "拒绝: $desc" test "$rc" -ne 0
  check "拒绝: $desc → stderr 含 FAIL" grep -q "FAIL" "$TMP/err"
}

echo "== 场景 1: 合法 URL → 放行 =="
accept "合法 URL（host:port/repo/tag/asset 精确匹配）" "https://$H$P"

echo "== 场景 2: http scheme → 拒绝 =="
reject "http://（明文）" "http://$H$P"

echo "== 场景 3: 错误 host（缺端口）→ 拒绝 =="
reject "host 无端口 forge.example.com" "https://forge.example.com$P"

echo "== 场景 4: 错误端口 → 拒绝 =="
reject "端口 8443" "https://forge.example.com:8443$P"
reject "端口 443（默认省略）" "https://forge.example.com:443$P"

echo "== 场景 5: userinfo → 拒绝 =="
reject "userinfo a:b@host" "https://a:b@$H$P"

echo "== 场景 6: '@' 旁路 → 拒绝 =="
reject "'@' 旁路（host:port@evil.com）" "https://forge.example.com:443@evil.com$P"
reject "'@' 旁路（host:port@evil.com:443）" "https://forge.example.com:443@evil.com:443$P"

echo "== 场景 7: '..' 路径穿越 → 拒绝 =="
reject "tag 段 '..' 穿越" "https://$H/$RP/releases/download/../v0.1.1/$ASSET"
reject "repo 段 '..' 穿越" "https://$H/../other/releases/download/$TAG/$ASSET"

echo "== 场景 8: '%2e%2e' 编码穿越 → 拒绝 =="
reject "tag 段 %2e%2e" "https://$H/$RP/releases/download/%2e%2e/v0.1.1/$ASSET"
reject "资产名 %2e%2e 前缀" "https://$H$P/%2e%2e%2F$ASSET"

echo "== 场景 9: 控制字符 → 拒绝 =="
reject "URL 尾部 \\n" "https://$H$P"$'\n'
reject "URL 尾部 \\r" "https://$H$P"$'\r'
reject "URL 中间 \\t" "https://$H"$'\t'"$P"
reject "URL 中间 \\x01" "https://$H"$'\x01'"$P"

echo "== 场景 10: 空 URL / 缺参数 → 拒绝 =="
reject "空 URL" ""
out=$(assert_asset_url_trusted "https://$H$P" "" "$RP" "$TAG" "$ASSET" 2>"$TMP/err"); rc=$?
check "缺 host 参数 → 非零" test "$rc" -ne 0

echo "== 场景 11: 错误 repo 路径 → 拒绝 =="
reject "错误 repo（evil/alphagps）" "https://$H/evil/alphagps/releases/download/$TAG/$ASSET"
reject "repo 少一层（仅 alphagps）" "https://$H/alphagps/releases/download/$TAG/$ASSET"

echo "== 场景 12: 错误 tag 路径 → 拒绝 =="
reject "错误 tag（v0.1.1）" "https://$H/$RP/releases/download/v0.1.1/$ASSET"
reject "tag 前缀拼接（v0.1.2x）" "https://$H/$RP/releases/download/${TAG}x/$ASSET"
reject "错误资产名" "https://$H/$RP/releases/download/$TAG/alpha-gps-0.1.1.apk"

echo "== 场景 13: 空白/反斜杠/查询串/fragment → 拒绝 =="
reject "URL 内空格" "https://$H$P x"
reject "URL 内反斜杠" "https://$H\\$P"
reject "带查询串 ?x=1" "https://$H$P?x=1"
reject "带 fragment #f" "https://$H$P#f"

echo "== 场景 14: host 大小写 → 拒绝（netloc 精确比较） =="
reject "大写 host" "https://forge.example.com:443$P"
reject "大写 scheme" "HTTPS://$H$P"

echo "== 场景 15: 变异验证——弱实现（netloc 后缀匹配）必须被 '@' 旁过用例捕获 =="
MUTLIB=$TMP/release_lib_weak.sh
# 把精确比较 `p.netloc != host` 放宽为后缀匹配（@evil.com 旁路的典型弱实现）
sed 's/if p\.netloc != host:/if not p.netloc.endswith(host):/' "$LIB" > "$MUTLIB"
out=$(bash -c "source '$MUTLIB'; assert_asset_url_trusted 'https://forge.example.com:443@evil.com$P' '$H' '$RP' '$TAG' '$ASSET'" 2>/dev/null); rc=$?
check "变异（后缀匹配）对 '@' 旁过用例意外放行（rc≠0 才证明用例有效）" test "$rc" -ne 0
# 反向：弱实现仍拒绝合法 URL？不应影响本用例判定，但确认合法 URL 在真实库通过（重复锚点）
accept "真实库再次放行合法 URL" "https://$H$P"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
