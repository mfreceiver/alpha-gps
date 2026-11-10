#!/usr/bin/env bash
# rl_load_release_config / rl_cfg_py（scripts/release_lib.sh）+ release_build.sh 接线自测：
# 主仓库参数外置到不入库的 scripts/release.local.env（或 ALPHAGPS_* 环境变量）。
# 纯离线；只用占位值 forge.example.com:443 / owner/alphagps / origin。
#   场景 1  合法文件（含注释/空行）→ 三项主仓库参数 + GITHUB_REMOTE 默认 github
#   场景 2  环境变量优先于文件；GITHUB_REMOTE 可由文件/环境覆盖
#   场景 3  非法键（未知键 / export 前缀 / 小写键 / 键后空白）→ fail-closed
#   场景 4  非法值字符（空格 引号 $ 反引号 ; 括号 非 ASCII CR NUL 空值）→ fail-closed
#   场景 5  值形态非法（'..' / 前导 '-' / 端口过长 / repo 缺 owner / 远端名含 '/'）→ fail-closed
#   场景 6  重复键 → fail-closed
#   场景 7  缺失（文件不存在且无环境变量）→ fail-closed 并打印占位示例；不回退任何内置值
#   场景 8  文件不存在但环境变量齐全 → 通过；need_primary=0 允许缺失
#   场景 9  非法环境变量值 → fail-closed；目录/符号链接 → fail-closed
#   场景 10 文件内容绝不被执行（命令替换/反引号被拒，marker 文件不出现）
#   场景 11 入库示例 release.local.env.example 可被解析，.gitignore 忽略真实文件
#   场景 12 release_build.sh 接线：缺配置 → 非零退出且在任何构建/签名步骤之前；--help 无需配置
#   场景 13 变异验证：弱实现（值字符校验被删 / 重复键检查被删）必须放行对应坏输入
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

# 清掉测试进程里可能存在的真实配置环境变量，保证用例只受显式输入影响
unset ALPHAGPS_PRIMARY_HOST ALPHAGPS_PRIMARY_REPO ALPHAGPS_PRIMARY_REMOTE ALPHAGPS_GITHUB_REMOTE

# load <file> <need> [VAR=val ...] → 在干净子 shell 里加载并打印四个全局；rc 透传
load() {
  local file=$1 need=$2; shift 2
  env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE -u ALPHAGPS_GITHUB_REMOTE \
    "$@" bash -c 'source "$1"; rl_load_release_config "$2" "$3" || exit 1; printf "%s|%s|%s|%s\n" "$PRIMARY_HOST" "$PRIMARY_REPO" "$PRIMARY_REMOTE" "$GITHUB_REMOTE"' _ "$LIB" "$file" "$need"
}
good_file() { # $1=路径
  printf '%s\n' '# 注释行' '' 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443' \
    'ALPHAGPS_PRIMARY_REPO=owner/alphagps' 'ALPHAGPS_PRIMARY_REMOTE=origin' > "$1"
}
rejects() { # rejects <desc> <文件内容 printf 格式> [参数...]：写文件 → load 非零且 stderr 有 FAIL
  local desc=$1 content=$2
  printf "$content" > "$TMP/case.env"
  load "$TMP/case.env" 1 >"$TMP/out" 2>"$TMP/err"; local rc=$?
  check "拒绝: $desc" test "$rc" -ne 0
  check "拒绝: $desc → stderr 含 FAIL" grep -q "FAIL" "$TMP/err"
}

echo "== 场景 1: 合法文件 =="
good_file "$TMP/good.env"
OUT=$(load "$TMP/good.env" 1 2>"$TMP/err"); RC=$?
check "合法文件 rc=0 且解析为占位三项 + 默认 github 远端" \
  test "$RC" -eq 0 -a "$OUT" == "forge.example.com:443|owner/alphagps|origin|github"
printf 'ALPHAGPS_PRIMARY_HOST=forge.example.com\nALPHAGPS_PRIMARY_REPO=a_b/c.d-e\nALPHAGPS_PRIMARY_REMOTE=my-forge.1\nALPHAGPS_GITHUB_REMOTE=gh\n' > "$TMP/ok2.env"
OUT=$(load "$TMP/ok2.env" 1 2>"$TMP/err"); RC=$?
check "无端口 host / 含 . _ - 的 repo 与远端名 / 文件内 GITHUB_REMOTE=gh" \
  test "$RC" -eq 0 -a "$OUT" == "forge.example.com|a_b/c.d-e|my-forge.1|gh"
printf 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\nALPHAGPS_PRIMARY_REPO=owner/alphagps\nALPHAGPS_PRIMARY_REMOTE=origin' > "$TMP/nonl.env"
OUT=$(load "$TMP/nonl.env" 1 2>/dev/null); RC=$?
check "末行无换行同样可解析" test "$RC" -eq 0 -a "$OUT" == "forge.example.com:443|owner/alphagps|origin|github"

echo "== 场景 2: 环境变量优先于文件 =="
OUT=$(load "$TMP/good.env" 1 ALPHAGPS_PRIMARY_HOST=env.example.org:8443 ALPHAGPS_GITHUB_REMOTE=ghub 2>"$TMP/err"); RC=$?
check "env 覆盖 HOST 与 GITHUB_REMOTE，其余取文件" \
  test "$RC" -eq 0 -a "$OUT" == "env.example.org:8443|owner/alphagps|origin|ghub"
OUT=$(load "$TMP/good.env" 1 ALPHAGPS_PRIMARY_REMOTE= 2>/dev/null); RC=$?
check "env 为空串视为未设置 → 取文件值" test "$RC" -eq 0 -a "$OUT" == "forge.example.com:443|owner/alphagps|origin|github"

echo "== 场景 3: 非法键 → fail-closed =="
rejects "未知键 EVIL=1" 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\nEVIL=1\n'
rejects "export 前缀" 'export ALPHAGPS_PRIMARY_HOST=forge.example.com:443\n'
rejects "小写键" 'alphagps_primary_host=forge.example.com:443\n'
rejects "键后空白 KEY =v" 'ALPHAGPS_PRIMARY_HOST =forge.example.com:443\n'
rejects "等号后空白 KEY= v" 'ALPHAGPS_PRIMARY_HOST= forge.example.com:443\n'
rejects "旧式内部变量名 PRIMARY_HOST 不是合法键（须带 ALPHAGPS_ 前缀）" 'PRIMARY_HOST=forge.example.com:443\n'
rejects "无等号行" 'ALPHAGPS_PRIMARY_HOST\n'
rejects "行首空白" ' ALPHAGPS_PRIMARY_HOST=forge.example.com:443\n'

echo "== 场景 4: 非法值字符 → fail-closed =="
rejects "值含空格" 'ALPHAGPS_PRIMARY_HOST=forge.example.com :443\n'
rejects "尾随空格" 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443 \n'
rejects "单引号" "ALPHAGPS_PRIMARY_HOST='forge.example.com:443'\\n"
rejects "双引号" 'ALPHAGPS_PRIMARY_HOST="forge.example.com:443"\n'
rejects "\$ 变量展开" 'ALPHAGPS_PRIMARY_REPO=owner/$HOME\n'
rejects "反引号" 'ALPHAGPS_PRIMARY_REPO=owner/`id`\n'
rejects "分号" 'ALPHAGPS_PRIMARY_REPO=owner/a;b\n'
rejects "圆括号" 'ALPHAGPS_PRIMARY_REPO=owner/(a)\n'
rejects "@ 符号（userinfo 旁路）" 'ALPHAGPS_PRIMARY_HOST=u@forge.example.com:443\n'
rejects "非 ASCII" 'ALPHAGPS_PRIMARY_REMOTE=远端\n'
rejects "CR 行尾" 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\r\n'
rejects "NUL 字节" 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\0\n'
rejects "空值" 'ALPHAGPS_PRIMARY_HOST=\n'

echo "== 场景 5: 值形态非法 → fail-closed =="
rejects "host 含 '..'" 'ALPHAGPS_PRIMARY_HOST=forge..example.com\n'
rejects "host 前导 '-'（选项注入）" 'ALPHAGPS_PRIMARY_HOST=-forge.example.com\n'
rejects "host 端口过长" 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443210\n'
rejects "host 含路径" 'ALPHAGPS_PRIMARY_HOST=forge.example.com/x\n'
rejects "repo 缺 owner" 'ALPHAGPS_PRIMARY_REPO=alphagps\n'
rejects "repo 三段" 'ALPHAGPS_PRIMARY_REPO=a/b/c\n'
rejects "repo 穿越 owner/.." 'ALPHAGPS_PRIMARY_REPO=owner/..\n'
rejects "远端名含 '/'" 'ALPHAGPS_PRIMARY_REMOTE=a/b\n'
rejects "远端名前导 '-'（git 选项注入）" 'ALPHAGPS_PRIMARY_REMOTE=--upload-pack=x\n'

echo "== 场景 6: 重复键 → fail-closed =="
rejects "同键两行" 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\nALPHAGPS_PRIMARY_HOST=other.example.org\n'
check "错误点名『重复』" grep -q "重复" "$TMP/err"

echo "== 场景 7: 缺失 → fail-closed 并给占位示例 =="
OUT=$(load "$TMP/nonexistent.env" 1 2>"$TMP/err"); RC=$?
check "文件不存在且无 env → 非零" test "$RC" -ne 0
check "错误点名缺失的三个键" bash -c 'grep -q ALPHAGPS_PRIMARY_HOST "$1" && grep -q ALPHAGPS_PRIMARY_REPO "$1" && grep -q ALPHAGPS_PRIMARY_REMOTE "$1"' _ "$TMP/err"
check "错误含占位示例 forge.example.com:443 / owner/alphagps / origin" \
  bash -c 'grep -q "forge.example.com:443" "$1" && grep -q "owner/alphagps" "$1" && grep -q "ALPHAGPS_PRIMARY_REMOTE=origin" "$1"' _ "$TMP/err"
check "错误含 FAIL 且无标准输出（不回退内置值）" bash -c 'grep -q FAIL "$1" && [[ -z "$2" ]]' _ "$TMP/err" "$OUT"
printf 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\n' > "$TMP/partial.env"
OUT=$(load "$TMP/partial.env" 1 2>"$TMP/err"); RC=$?
check "只给 HOST → 非零且点名缺 REPO/REMOTE" test "$RC" -ne 0 -a "$(grep -c 'ALPHAGPS_PRIMARY_REPO' "$TMP/err")" -ge 1
: > "$TMP/empty.env"
OUT=$(load "$TMP/empty.env" 1 2>/dev/null); RC=$?
check "空文件 → 非零" test "$RC" -ne 0

echo "== 场景 8: 仅环境变量 / need_primary=0 =="
OUT=$(load "$TMP/nonexistent.env" 1 ALPHAGPS_PRIMARY_HOST=forge.example.com:443 ALPHAGPS_PRIMARY_REPO=owner/alphagps ALPHAGPS_PRIMARY_REMOTE=origin 2>"$TMP/err"); RC=$?
check "文件不存在 + env 齐全 → 通过" test "$RC" -eq 0 -a "$OUT" == "forge.example.com:443|owner/alphagps|origin|github"
OUT=$(load "$TMP/nonexistent.env" 0 2>"$TMP/err"); RC=$?
check "need_primary=0（GitHub-only 模式）允许全部缺失，GITHUB_REMOTE 默认 github" test "$RC" -eq 0 -a "$OUT" == "|||github"
printf 'EVIL=1\n' > "$TMP/bad8.env"
OUT=$(load "$TMP/bad8.env" 0 2>"$TMP/err"); RC=$?
check "need_primary=0 时文件若存在但非法仍 fail-closed" test "$RC" -ne 0

echo "== 场景 9: 非法 env 值 / 目录 / 符号链接 =="
OUT=$(load "$TMP/good.env" 1 'ALPHAGPS_PRIMARY_HOST=evil.example.org;rm' 2>"$TMP/err"); RC=$?
check "env 值含 ';' → 非零" test "$RC" -ne 0
check "错误点名环境变量名" grep -q "ALPHAGPS_PRIMARY_HOST" "$TMP/err"
OUT=$(load "$TMP/good.env" 1 'ALPHAGPS_GITHUB_REMOTE=-x' 2>"$TMP/err"); RC=$?
check "env GITHUB_REMOTE 前导 '-' → 非零" test "$RC" -ne 0
mkdir -p "$TMP/dir.env"
OUT=$(load "$TMP/dir.env" 1 2>"$TMP/err"); RC=$?
check "路径是目录 → 非零" test "$RC" -ne 0
ln -sf "$TMP/good.env" "$TMP/link.env"
OUT=$(load "$TMP/link.env" 1 2>"$TMP/err"); RC=$?
check "路径是符号链接 → 非零" test "$RC" -ne 0

echo "== 场景 10: 文件内容绝不被执行 =="
rm -f "$TMP/pwned"
printf 'ALPHAGPS_PRIMARY_HOST=$(touch %s)\nALPHAGPS_PRIMARY_REPO=owner/alphagps\nALPHAGPS_PRIMARY_REMOTE=origin\n' "$TMP/pwned" > "$TMP/inj.env"
load "$TMP/inj.env" 1 >/dev/null 2>&1; RC=$?
check "命令替换值被拒" test "$RC" -ne 0
check "marker 文件未生成（未执行）" test ! -e "$TMP/pwned"
printf 'touch %s\nALPHAGPS_PRIMARY_HOST=forge.example.com:443\n' "$TMP/pwned" > "$TMP/inj2.env"
load "$TMP/inj2.env" 1 >/dev/null 2>&1; RC=$?
check "裸命令行被拒（非 KEY=VALUE）" test "$RC" -ne 0
check "marker 文件仍未生成" test ! -e "$TMP/pwned"

echo "== 场景 11: 入库示例可解析 / .gitignore 忽略真实文件 =="
OUT=$(load "$HERE/release.local.env.example" 1 2>"$TMP/err"); RC=$?
check "release.local.env.example 解析为占位值" test "$RC" -eq 0 -a "$OUT" == "forge.example.com:443|owner/alphagps|origin|github"
check ".gitignore 含 /scripts/release.local.env" grep -qxF '/scripts/release.local.env' "$HERE/../.gitignore"

echo "== 场景 12: release_build.sh 接线 =="
SB=$TMP/sb12; mkdir -p "$SB/scripts"
cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" "$HERE/release_assert_apk.sh" "$SB/scripts/"
( cd "$SB" && env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE -u ALPHAGPS_GITHUB_REMOTE \
    HOME="$SB/home" timeout 30 bash scripts/release_build.sh 0.1.3 --allow-dirty ) > "$TMP/w12.log" 2>&1; RC=$?
check "缺配置 → 非零且非超时" test "$RC" -ne 0 -a "$RC" -ne 124
check "日志含『本地发布配置缺失』与占位示例" bash -c 'grep -q "本地发布配置缺失" "$1" && grep -q "forge.example.com:443" "$1"' _ "$TMP/w12.log"
check "在签名预检/构建之前终止（无『签名预检』日志）" bash -c '! grep -q "签名预检" "$1"' _ "$TMP/w12.log"
# 沙箱 scripts/ 下放一个非法 release.local.env → fail-closed（走文件路径）
printf 'EVIL=1\n' > "$SB/scripts/release.local.env"
( cd "$SB" && env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE \
    HOME="$SB/home" timeout 30 bash scripts/release_build.sh 0.1.3 --allow-dirty ) > "$TMP/w12b.log" 2>&1; RC=$?
check "沙箱 release.local.env 含非法键 → 非零且点名『不允许的键』" test "$RC" -ne 0 -a "$(grep -c '不允许的键' "$TMP/w12b.log")" -ge 1
# 合法文件 → 越过配置门禁（之后因沙箱无 gradle.properties 等而在签名预检失败，但已不报配置错误）
good_file "$SB/scripts/release.local.env"
( cd "$SB" && env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE \
    HOME="$SB/home" timeout 30 bash scripts/release_build.sh 0.1.3 --allow-dirty ) > "$TMP/w12c.log" 2>&1; RC=$?
check "合法 release.local.env → 越过配置门禁（日志无『本地发布配置』报错，进入签名预检）" \
  bash -c '! grep -q "本地发布配置" "$1" && grep -q "签名预检" "$1"' _ "$TMP/w12c.log"
( cd "$SB" && env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE \
    bash scripts/release_build.sh --help ) > "$TMP/w12d.log" 2>&1; RC=$?
check "--help 退出 0 且不依赖配置" test "$RC" -eq 0

echo "== 场景 13: 变异验证（弱实现必须放行对应坏输入，证明用例有效） =="
MUTLIB=$TMP/release_lib_mut.sh
# 变异 A：字符白名单与形态校验都被删 → 远端名含 '@'（userinfo/选项注入类坏值）被放行
sed -e 's/if not CHARS.fullmatch(val):/if False:/' -e 's/if not SHAPE\[key\].fullmatch(val):/if False:/' "$LIB" > "$MUTLIB"
printf 'ALPHAGPS_PRIMARY_REMOTE=a@b\n' > "$TMP/mutA2.env"
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$MUTLIB" "$TMP/mutA2.env" >/dev/null 2>&1; RC=$?
check "变异A（字符白名单+形态校验都被删）放行 'a@b'（rc=0 才证明相关用例有效）" test "$RC" -eq 0
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$LIB" "$TMP/mutA2.env" >/dev/null 2>&1; RC=$?
check "真实库拒绝同一输入" test "$RC" -ne 0
# 变异 A2：只删字符白名单 → 形态正则兜底仍拦截（纵深防御：两层各自独立生效）
sed 's/if not CHARS.fullmatch(val):/if False:/' "$LIB" > "$MUTLIB"
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$MUTLIB" "$TMP/mutA2.env" >/dev/null 2>&1; RC=$?
check "仅删字符白名单时形态正则仍拦截 'a@b'（纵深防御）" test "$RC" -ne 0
# 变异 B：重复键检查被删 → 重复键被放行
sed 's/if key in seen:/if False:/' "$LIB" > "$MUTLIB"
printf 'ALPHAGPS_PRIMARY_HOST=forge.example.com:443\nALPHAGPS_PRIMARY_HOST=other.example.org\n' > "$TMP/mutB.env"
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$MUTLIB" "$TMP/mutB.env" >/dev/null 2>&1; RC=$?
check "变异B（删重复键检查）放行重复键（rc=0）" test "$RC" -eq 0
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$LIB" "$TMP/mutB.env" >/dev/null 2>&1; RC=$?
check "真实库拒绝重复键" test "$RC" -ne 0

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
