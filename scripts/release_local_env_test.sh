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
#   场景 14 可选键 ALPHAGPS_SIGNING_KEYSTORE / ALPHAGPS_SIGNING_ALIAS（库层）：未配置保持调用方默认值；
#           配置后生效（'~/'→$HOME/、绝对路径）；env 优先于文件；非法值/形态 fail-closed；不执行；变异验证
#   场景 15 release_build.sh 接线：默认 / 配置 / env 覆盖三种情形下传给 keytool 的 -keystore / -alias；
#           非法配置在签名预检之前终止；--help 提及两键
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

echo "== 场景 14: 签名 keystore/alias 可选键（库层） =="
DEF_KS=/def/home/.config/alphagps/signing/release.jks; DEF_AL=release
# sload <文件> [VAR=val ...] → 干净子 shell（HOME=/home/user）里预置默认值后加载，打印 SIGNING_KEYSTORE_PATH|SIGNING_KEY_ALIAS
sload() {
  local file=$1; shift
  env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE -u ALPHAGPS_GITHUB_REMOTE \
      -u ALPHAGPS_SIGNING_KEYSTORE -u ALPHAGPS_SIGNING_ALIAS HOME=/home/user "$@" \
    bash -c 'source "$1"; SIGNING_KEYSTORE_PATH=$2; SIGNING_KEY_ALIAS=$3; rl_load_release_config "$4" 1 || exit 1; printf "%s|%s\n" "$SIGNING_KEYSTORE_PATH" "$SIGNING_KEY_ALIAS"' _ "$LIB" "$DEF_KS" "$DEF_AL" "$file"
}
sign_file() { # $1=路径 $2...=追加行（原样）；先写合法主仓库三项
  local f=$1; shift; good_file "$f"; printf '%s\n' "$@" >> "$f"
}
sign_rejects() { # sign_rejects <desc> <追加行> <应被点名的键> [VAR=val ...(env)]
  local desc=$1 line=$2 key=$3; shift 3
  sign_file "$TMP/s.env" "$line"
  sload "$TMP/s.env" "$@" >"$TMP/sout" 2>"$TMP/serr"; local rc=$?
  check "拒绝: $desc" test "$rc" -ne 0
  check "拒绝: $desc → stderr 含 FAIL 且点名 $key" bash -c 'grep -q FAIL "$1" && grep -q "$2" "$1"' _ "$TMP/serr" "$key"
  check "拒绝: $desc → 无标准输出（不输出被污染的值）" test ! -s "$TMP/sout"
}
good_file "$TMP/g14.env"
OUT=$(sload "$TMP/g14.env" 2>"$TMP/err"); RC=$?
check "未配置 → 保持调用方默认 keystore/alias" test "$RC" -eq 0 -a "$OUT" == "$DEF_KS|$DEF_AL"
sign_file "$TMP/c14.env" 'ALPHAGPS_SIGNING_KEYSTORE=~/.config/custom/my.jks' 'ALPHAGPS_SIGNING_ALIAS=myalias'
OUT=$(sload "$TMP/c14.env" 2>"$TMP/err"); RC=$?
check "配置后生效：'~/' → \$HOME/，alias 取配置值" test "$RC" -eq 0 -a "$OUT" == "/home/user/.config/custom/my.jks|myalias"
sign_file "$TMP/c14b.env" 'ALPHAGPS_SIGNING_KEYSTORE=/opt/keys/release.jks'
OUT=$(sload "$TMP/c14b.env" 2>"$TMP/err"); RC=$?
check "绝对路径原样生效；只配 keystore 时 alias 仍为默认" test "$RC" -eq 0 -a "$OUT" == "/opt/keys/release.jks|$DEF_AL"
sign_file "$TMP/c14c.env" 'ALPHAGPS_SIGNING_ALIAS=only-alias.1'
OUT=$(sload "$TMP/c14c.env" 2>"$TMP/err"); RC=$?
check "只配 alias 时 keystore 仍为默认" test "$RC" -eq 0 -a "$OUT" == "$DEF_KS|only-alias.1"
OUT=$(sload "$TMP/c14.env" 'ALPHAGPS_SIGNING_ALIAS=envalias' 'ALPHAGPS_SIGNING_KEYSTORE=~/x/y.jks' 2>"$TMP/err"); RC=$?
check "env 优先于文件（env 里的 '~/' 字面量同样替换为 \$HOME/）" test "$RC" -eq 0 -a "$OUT" == "/home/user/x/y.jks|envalias"
OUT=$(sload "$TMP/c14.env" 'ALPHAGPS_SIGNING_ALIAS=' 'ALPHAGPS_SIGNING_KEYSTORE=' 2>"$TMP/err"); RC=$?
check "env 为空串视为未设置 → 取文件值" test "$RC" -eq 0 -a "$OUT" == "/home/user/.config/custom/my.jks|myalias"
OUT=$(sload "$TMP/c14.env" HOME=/home/user/ 2>"$TMP/err"); RC=$?
check "HOME 带尾部 '/' 不产生双斜杠" test "$RC" -eq 0 -a "$OUT" == "/home/user/.config/custom/my.jks|myalias"
OUT=$(sload "$TMP/c14.env" HOME=rel/home 2>"$TMP/err"); RC=$?
check "使用 '~/' 但 HOME 非绝对路径 → fail-closed" test "$RC" -ne 0 -a "$(grep -c FAIL "$TMP/err")" -ge 1
OUT=$(sload "$TMP/c14b.env" HOME=rel/home 2>"$TMP/err"); RC=$?
check "绝对 keystore 路径不依赖 HOME" test "$RC" -eq 0 -a "$OUT" == "/opt/keys/release.jks|$DEF_AL"
# 非法 keystore 值
sign_rejects "keystore 含 \$HOME 展开" 'ALPHAGPS_SIGNING_KEYSTORE=$HOME/.config/x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含 \${HOME} 展开" 'ALPHAGPS_SIGNING_KEYSTORE=${HOME}/x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore '~user/' 形式" 'ALPHAGPS_SIGNING_KEYSTORE=~root/x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 单独 '~'" 'ALPHAGPS_SIGNING_KEYSTORE=~' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 仅 '~/'（无文件名）" 'ALPHAGPS_SIGNING_KEYSTORE=~/' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 路径中间的 '~'" 'ALPHAGPS_SIGNING_KEYSTORE=/a/~/x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 相对路径" 'ALPHAGPS_SIGNING_KEYSTORE=keys/x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 前导 '-'" 'ALPHAGPS_SIGNING_KEYSTORE=-x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含 '..'（~/ 形式）" 'ALPHAGPS_SIGNING_KEYSTORE=~/../x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含 '..'（绝对形式）" 'ALPHAGPS_SIGNING_KEYSTORE=/a/../x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 空路径段 '//'" 'ALPHAGPS_SIGNING_KEYSTORE=/a//x.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 以 '/' 结尾" 'ALPHAGPS_SIGNING_KEYSTORE=/a/b/' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含空格" 'ALPHAGPS_SIGNING_KEYSTORE=~/a b.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含命令替换" 'ALPHAGPS_SIGNING_KEYSTORE=~/$(id).jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含反引号" 'ALPHAGPS_SIGNING_KEYSTORE=~/`id`.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含引号" "ALPHAGPS_SIGNING_KEYSTORE='~/x.jks'" ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含分号" 'ALPHAGPS_SIGNING_KEYSTORE=~/a;b.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含 ':'（白名单字符但形态不允许）" 'ALPHAGPS_SIGNING_KEYSTORE=~/a:b.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 含非 ASCII" 'ALPHAGPS_SIGNING_KEYSTORE=~/密钥.jks' ALPHAGPS_SIGNING_KEYSTORE
sign_rejects "keystore 空值" 'ALPHAGPS_SIGNING_KEYSTORE=' ALPHAGPS_SIGNING_KEYSTORE
# 非法 alias 值
sign_rejects "alias 含空格" 'ALPHAGPS_SIGNING_ALIAS=my alias' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 前导 '-'（keytool 选项注入）" 'ALPHAGPS_SIGNING_ALIAS=-storepass' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 含 '/'" 'ALPHAGPS_SIGNING_ALIAS=a/b' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 含 ':'" 'ALPHAGPS_SIGNING_ALIAS=a:b' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 含 \$ 展开" 'ALPHAGPS_SIGNING_ALIAS=$USER' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 含分号" 'ALPHAGPS_SIGNING_ALIAS=a;b' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 含非 ASCII" 'ALPHAGPS_SIGNING_ALIAS=别名' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 空值" 'ALPHAGPS_SIGNING_ALIAS=' ALPHAGPS_SIGNING_ALIAS
sign_rejects "alias 含 '..'" 'ALPHAGPS_SIGNING_ALIAS=a..b' ALPHAGPS_SIGNING_ALIAS
sign_rejects "重复 keystore 键" 'ALPHAGPS_SIGNING_KEYSTORE=/a/x.jks
ALPHAGPS_SIGNING_KEYSTORE=/b/y.jks' ALPHAGPS_SIGNING_KEYSTORE
# 非法 env 值（经 env 通道，同样校验、点名变量）
good_file "$TMP/e14.env"
for bad in '$HOME/x.jks' '~root/x.jks' 'rel/x.jks' '/a/../b.jks' '/a b/c.jks'; do
  sload "$TMP/e14.env" "ALPHAGPS_SIGNING_KEYSTORE=$bad" >"$TMP/sout" 2>"$TMP/serr"; RC=$?
  check "env keystore 非法值 [$bad] → 非零、点名变量、无输出" \
    bash -c 'test "$1" -ne 0 && grep -q ALPHAGPS_SIGNING_KEYSTORE "$2" && test ! -s "$3"' _ "$RC" "$TMP/serr" "$TMP/sout"
done
for bad in '-x' 'a b' 'a;b' 'a/b'; do
  sload "$TMP/e14.env" "ALPHAGPS_SIGNING_ALIAS=$bad" >"$TMP/sout" 2>"$TMP/serr"; RC=$?
  check "env alias 非法值 [$bad] → 非零、点名变量、无输出" \
    bash -c 'test "$1" -ne 0 && grep -q ALPHAGPS_SIGNING_ALIAS "$2" && test ! -s "$3"' _ "$RC" "$TMP/serr" "$TMP/sout"
done
# 文件内容绝不被执行
rm -f "$TMP/pwned14"
sign_file "$TMP/inj14.env" "ALPHAGPS_SIGNING_KEYSTORE=~/\$(touch $TMP/pwned14).jks" "ALPHAGPS_SIGNING_ALIAS=\`touch $TMP/pwned14\`"
sload "$TMP/inj14.env" >/dev/null 2>&1; RC=$?
check "命令替换/反引号被拒且 marker 未生成（未执行）" test "$RC" -ne 0 -a ! -e "$TMP/pwned14"
# 变异：删形态校验后，':' 类值被放行（证明上面的 ':' 用例有效）；真实库拒绝
sed 's/if not SHAPE\[key\].fullmatch(val):/if False:/' "$LIB" > "$TMP/release_lib_mut14.sh"
printf 'ALPHAGPS_SIGNING_ALIAS=a:b\n' > "$TMP/mut14.env"
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$TMP/release_lib_mut14.sh" "$TMP/mut14.env" >/dev/null 2>&1; RC=$?
check "变异C（删形态校验）放行 alias 'a:b'（rc=0 才证明形态用例有效）" test "$RC" -eq 0
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$LIB" "$TMP/mut14.env" >/dev/null 2>&1; RC=$?
check "真实库拒绝同一输入" test "$RC" -ne 0
# 变异：删 '~/' 折叠 → 合法的 '~/' 值被拒（证明折叠分支是用例 '~/ → $HOME/' 生效所必需）
# 变异须语法有效（把折叠语句换成 pass，保持 if 体不空），并以控制组证明拒绝来自折叠缺失而非 Python 语法错误
sed 's/val = val\[1:\]/pass/' "$LIB" > "$TMP/release_lib_mut14b.sh"
printf 'ALPHAGPS_SIGNING_KEYSTORE=~/x.jks\n' > "$TMP/mut14b.env"
printf 'ALPHAGPS_SIGNING_KEYSTORE=/opt/key.jks\n' > "$TMP/mut14c.env"
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$TMP/release_lib_mut14b.sh" "$TMP/mut14b.env" >/dev/null 2>"$TMP/mut14b.err"; RC=$?
check "变异D（折叠换成 pass）拒绝合法 '~/x.jks'（rc≠0 且非 Python 语法错误，才证明折叠用例有效）" \
  bash -c 'test "$1" -ne 0 && ! grep -qi "IndentationError\|SyntaxError" "$2"' _ "$RC" "$TMP/mut14b.err"
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$TMP/release_lib_mut14b.sh" "$TMP/mut14c.env" >/dev/null 2>&1; RC=$?
check "变异D 控制组：合法绝对路径 '/opt/key.jks' 在变异库中仍通过（排除语法错误假阳性）" test "$RC" -eq 0
bash -c 'source "$1"; rl_cfg_py file "$2"' _ "$LIB" "$TMP/mut14b.env" >/dev/null 2>&1; RC=$?
check "真实库接受 '~/x.jks'" test "$RC" -eq 0

echo "== 场景 15: release_build.sh 接线（keytool 实参 / 预检前终止 / --help） =="
SB=$TMP/sb15; mkdir -p "$SB/scripts" "$SB/home/.gradle" "$SB/bin"
cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" "$HERE/release_assert_apk.sh" "$SB/scripts/"
printf 'DSH_KEYSTORE_PASSWORD=x\nDSH_KEY_PASSWORD=x\n' > "$SB/home/.gradle/gradle.properties"
# 假 keytool：只记录 argv（不记录环境，口令走 env 不会出现），然后失败以止步于签名预检
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$*" >> "%s/keytool.args"\nexit 1\n' "$SB" > "$SB/bin/keytool"
chmod +x "$SB/bin/keytool"
run15() { # run15 <日志> [VAR=val ...]
  local log=$1; shift
  rm -f "$SB/keytool.args"
  ( cd "$SB" && env -u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE -u ALPHAGPS_GITHUB_REMOTE \
      -u ALPHAGPS_SIGNING_KEYSTORE -u ALPHAGPS_SIGNING_ALIAS HOME="$SB/home" PATH="$SB/bin:$PATH" "$@" \
      timeout 30 bash scripts/release_build.sh 0.1.3 --allow-dirty ) > "$log" 2>&1
}
good_file "$SB/scripts/release.local.env"
run15 "$TMP/w15a.log"; RC=$?
check "默认：keystore 缺失时报内置默认路径（\$HOME/.config/alphagps/signing/release.jks）" \
  bash -c 'test "$1" -ne 0 && grep -qF "keystore 不存在或不可读: $2/home/.config/alphagps/signing/release.jks" "$3"' _ "$RC" "$SB" "$TMP/w15a.log"
mkdir -p "$SB/home/.config/alphagps/signing" "$SB/home/.config/custom"
: > "$SB/home/.config/alphagps/signing/release.jks"; : > "$SB/home/.config/custom/my.jks"
run15 "$TMP/w15b.log"; RC=$?
check "默认：keytool 收到默认 -keystore 与 -alias release" \
  bash -c 'grep -qF -- "-keystore $1/home/.config/alphagps/signing/release.jks " "$1/keytool.args" && grep -qE -- "-alias release( |$)" "$1/keytool.args"' _ "$SB"
check "默认：失败信息含 alias 'release'" grep -qF "alias 'release'" "$TMP/w15b.log"
sign_file "$SB/scripts/release.local.env" 'ALPHAGPS_SIGNING_KEYSTORE=~/.config/custom/my.jks' 'ALPHAGPS_SIGNING_ALIAS=myalias'
run15 "$TMP/w15c.log"; RC=$?
check "配置：keytool 收到 \$HOME 展开后的自定义 -keystore 与 -alias myalias" \
  bash -c 'grep -qF -- "-keystore $1/home/.config/custom/my.jks " "$1/keytool.args" && grep -qE -- "-alias myalias( |$)" "$1/keytool.args"' _ "$SB"
check "配置：不再使用默认路径/alias" bash -c '! grep -qF "alphagps/signing/release.jks" "$1/keytool.args" && ! grep -qE -- "-alias release( |$)" "$1/keytool.args"' _ "$SB"
run15 "$TMP/w15d.log" ALPHAGPS_SIGNING_ALIAS=envalias; RC=$?
check "env 覆盖文件：-alias envalias，keystore 仍取文件值" \
  bash -c 'grep -qE -- "-alias envalias( |$)" "$1/keytool.args" && grep -qF -- "-keystore $1/home/.config/custom/my.jks " "$1/keytool.args"' _ "$SB"
sign_file "$SB/scripts/release.local.env" 'ALPHAGPS_SIGNING_KEYSTORE=$HOME/.config/custom/my.jks'
run15 "$TMP/w15e.log"; RC=$?
check "非法 keystore 配置 → 非零，且在签名预检/keytool 之前终止" \
  bash -c 'test "$1" -ne 0 && grep -q "ALPHAGPS_SIGNING_KEYSTORE" "$2" && ! grep -q "签名预检" "$2" && test ! -e "$3/keytool.args"' _ "$RC" "$TMP/w15e.log" "$SB"
good_file "$SB/scripts/release.local.env"
run15 "$TMP/w15f.log" ALPHAGPS_SIGNING_ALIAS='a b'; RC=$?
check "非法 env alias → 非零且在签名预检前终止" \
  bash -c 'test "$1" -ne 0 && grep -q "ALPHAGPS_SIGNING_ALIAS" "$2" && ! grep -q "签名预检" "$2" && test ! -e "$3/keytool.args"' _ "$RC" "$TMP/w15f.log" "$SB"
( cd "$SB" && bash scripts/release_build.sh --help ) > "$TMP/w15g.log" 2>&1; RC=$?
check "--help 退出 0 并说明两个签名键" bash -c 'test "$1" -eq 0 && grep -q ALPHAGPS_SIGNING_KEYSTORE "$2" && grep -q ALPHAGPS_SIGNING_ALIAS "$2"' _ "$RC" "$TMP/w15g.log"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
