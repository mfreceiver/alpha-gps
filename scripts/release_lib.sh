#!/usr/bin/env bash
# release_lib.sh —— 发版脚本共用纯函数（release_build.sh source；自测脚本直接 source）。
# 全部函数遵循 fail-closed：解析/门禁失败 → stderr 带关键字 + 非零返回。
# versionCode 的单一来源是仓库根 gradle.properties 的平面 key=value 行
#   （`alphagps.versionCode=<N>`，N 为 1..2147483647 的 canonical 十进制）。
# 设计决策：不再解析/词法扫描 Kotlin 源码（app/build.gradle.kts）——那是一个
#   不可穷尽的问题（转义/注释/raw string/模板/编码都有旁路）；平面文件 + 严格
#   整行匹配把可出错的输入空间收缩到几乎为零。

rl_fail() { echo "FAIL $1" >&2; }   # 注意：调用方必须紧跟 `return 1`（在 `||` 里不会替调用方退出函数）
rl_warn() { echo "WARN $1" >&2; }

# rl_check_apk_arm64 <apk路径>
#   APK native ABI 检查（纯函数，zipfile 只读）：lib/ 下所有文件条目必须位于
#   lib/arm64-v8a/ 且该目录至少有一个 .so。zip 的纯目录条目（name 以 '/' 结尾，
#   如 'lib/'）不参与 ABI 判定——部分打包器会显式写入目录项，空目录无 native 含义。
#   输出到 stdout: OK <so数> / BAD_ABI <样本> / NO_SO / OPEN_FAIL <原因>（均 rc=0，
#   由调用方按输出分支 fail-closed）。
rl_check_apk_arm64() {
  python3 - "${1:-}" <<'PY'
import sys, zipfile
try:
    z = zipfile.ZipFile(sys.argv[1])
    names = z.namelist()
except Exception as e:
    print(f"OPEN_FAIL {e}"); sys.exit(0)
bad = sorted(n for n in names
             if not n.endswith("/") and n.startswith("lib/") and not n.startswith("lib/arm64-v8a/"))
so = [n for n in names
      if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
if bad:
    print("BAD_ABI " + ",".join(bad[:5]))
elif not so:
    print("NO_SO")
else:
    print("OK " + str(len(so)))
PY
}

# rl_check_apk_lang <apk路径>
#   APK composeResources 语言检查（纯函数，zipfile 只读）：assets/composeResources/*/values*
#   下的语言目录集合必须 ⊆ {values, values-zh-rCN}（语言资源纪律：仅 en 基线 + zh-rCN），
#   且至少含 values（en 基线缺失 = 资源生成异常，fail-closed）。drawable 等其它资源目录
#   与 zip 纯目录条目（name 以 '/' 结尾）不参与判定。
#   输出到 stdout: OK <语言目录列表> / BAD_LANG <多余目录样本> / NO_VALUES /
#   OPEN_FAIL <原因>（均 rc=0，由调用方按输出分支 fail-closed）。
rl_check_apk_lang() {
  python3 - "${1:-}" <<'PY'
import sys, zipfile
try:
    z = zipfile.ZipFile(sys.argv[1])
    names = z.namelist()
except Exception as e:
    print(f"OPEN_FAIL {e}"); sys.exit(0)
allowed = {"values", "values-zh-rCN"}
langs = set()
for n in names:
    if n.endswith("/") or not n.startswith("assets/composeResources/"):
        continue
    parts = n.split("/")
    # assets/composeResources/<pkg>/<langdir>/<file...>（至少 4 段才算语言目录内文件）
    if len(parts) >= 4 and parts[3].startswith("values"):
        langs.add(parts[3])
bad = sorted(l for l in langs if l not in allowed)
if bad:
    print("BAD_LANG " + ",".join(bad[:5]))
elif "values" not in langs:
    print("NO_VALUES")
else:
    print("OK " + ",".join(sorted(langs)))
PY
}

# assert_asset_url_trusted <url> <host[:port]> <owner/repo> <tag> <asset文件名>
#   主仓库资产下载 URL 同源校验（纯函数、无网络；GitHub 步骤的 browser_download_url 也复用），全部用 python3 urllib.parse
#   解析（不用正则猜），fail-closed：
#     - URL 非空、不含控制字符（<0x20/0x7f）、空白、反斜杠、'@'（userinfo 旁路）；
#     - scheme 恰为 https；
#     - netloc（host[:port]）与期望精确相等（大小写敏感、无默认端口省略余地）；
#     - 无 userinfo（urlsplit 的 username/password 为 None）；
#     - path 恰等于 /<owner/repo>/releases/download/<tag>/<asset>（精确整串相等，
#       同时覆盖 `..`、`%2e%2e`、错误 repo、错误 tag、前缀拼接等一切路径偏移）；
#     - query/fragment 必须为空（urlsplit 无独立 params；';' 留在 path 中，
#       被精确整串比较天然拒绝）。
#   任一不满足 → stderr FAIL + 非零返回。
assert_asset_url_trusted() {
  local url=${1:-} host=${2:-} repo=${3:-} tag=${4:-} asset=${5:-}
  [[ -n "$host" && -n "$repo" && -n "$tag" && -n "$asset" ]] \
    || { rl_fail "资产 URL 校验: 参数不完整（host/repo/tag/asset），fail-closed"; return 1; }
  if [[ -z "$url" ]]; then
    rl_fail "资产 URL 校验: URL 为空，fail-closed"
    return 1
  fi
  if ! python3 - "$url" "$host" "$repo" "$tag" "$asset" <<'PY'
import sys
from urllib.parse import urlsplit

url, host, repo, tag, asset = sys.argv[1:6]

def fail(msg):
    sys.stderr.write(f"FAIL 资产 URL 校验: {msg}，fail-closed\n")
    sys.exit(1)

for c in url:
    if ord(c) < 0x20 or ord(c) == 0x7F or c.isspace() or c == "\\":
        fail(f"URL 含控制字符/空白/反斜杠（ord={ord(c):#x}）")
if "@" in url:
    fail("URL 含 '@'（userinfo 旁路）")
if not url.startswith("https://"):
    fail(f"URL 必须以小写 'https://' 开头（scheme 大小写严格），实际开头: {url[:12]!r}")
p = urlsplit(url)
if p.scheme != "https":
    fail(f"scheme 必须是 https，实际 '{p.scheme or '(空)'}'")
if p.username is not None or p.password is not None:
    fail("URL 含 userinfo（username/password）")
if p.netloc != host:
    fail(f"host:port 必须精确等于 '{host}'，实际 '{p.netloc}'")
expected_path = f"/{repo}/releases/download/{tag}/{asset}"
if p.path != expected_path:
    fail(f"path 必须精确等于 '{expected_path}'，实际 '{p.path}'")
if p.query or p.fragment:
    fail("query/fragment 必须为空")
sys.exit(0)
PY
  then
    rl_fail "资产 URL 校验失败: $url（fail-closed）"
    return 1
  fi
}

# rl_check_code <值>: canonical 十进制正整数（^[1-9][0-9]{0,9}$）且 ≤ 2147483647
#   （Android Int 上限；10 位时用等长字符串字典序比较，不进算术，避免 2^31/2^63 回绕）。
rl_check_code() {
  local v=${1:-}
  [[ "$v" =~ ^[1-9][0-9]{0,9}$ ]] || return 1
  [[ ${#v} -ne 10 ]] && return 0
  [[ ! "$v" > 2147483647 ]]
}

# ── 历史兼容表（不可变的已发布事实）────────────────────────────────────────
# 这三个 tag 发布时 versionCode 尚写在 app/build.gradle.kts，其 gradle.properties
# 没有本 key——已发布产物的 code 不能改写，只能以表内事实参与单调性比较：
#   v0.1.0 → 1, v0.1.1 → 2, v0.1.2 → 3
legacy_code_for_tag() { # <tag> → stdout=code / rc≠0 非历史 tag
  case "${1:-}" in
    v0.1.0) echo 1 ;;
    v0.1.1) echo 2 ;;
    v0.1.2) echo 3 ;;
    *) return 1 ;;
  esac
}

# parse_version_code_props <文件路径>
#   严格解析平面 key=value 文件中的 versionCode：
#     - 只接受文件路径参数（无 stdin 模式，无 fd0 问题）；
#     - 含 NUL 字节 → 拒绝（bash 变量无法安全承载 NUL）；
#     - 含任何 CR（\r）→ 拒绝（平面文件要求 LF 行尾，简单严格）；
#     - 包含子串 `alphagps.versionCode` 的行必须恰好 1 行（注释里写出该 key 名
#       也会被计入——gradle.properties 的注释因此被要求不得写出该 key 名）；
#     - 该行必须整行严格匹配 `^alphagps\.versionCode=[1-9][0-9]{0,9}$`
#       （无空格、无注释、无尾随空白、无前导零）；
#     - 值 ≤ 2147483647（长度/字典序比较，不进算术）。
#   stdout 输出解析到的 N；任何不满足 → stderr FAIL + 非零。
parse_version_code_props() {
  local path=${1:-} line n
  [[ -n "$path" && -f "$path" ]] \
    || { rl_fail "versionCode 解析: 文件不存在: '${path:-}'"; return 1; }
  local zrc=0
  LC_ALL=C grep -qaP '\x00' "$path" 2>/dev/null || zrc=$?   # if/|| 形式：裸 set -e 下 rc1 不触发 errexit
  if (( zrc == 0 )); then
    rl_fail "versionCode 解析: 文件含 NUL 字节（bash 变量无法安全承载 NUL），fail-closed"
    return 1
  fi
  if (( zrc > 1 )); then
    rl_fail "versionCode 解析: NUL 探测 grep 异常（rc=$zrc），fail-closed"
    return 1
  fi
  local crc=0
  LC_ALL=C grep -qa $'\r' "$path" 2>/dev/null || crc=$?
  if (( crc == 0 )); then
    rl_fail "versionCode 解析: 文件含 CR（平面文件要求纯 LF 行尾），fail-closed"
    return 1
  fi
  if (( crc > 1 )); then
    rl_fail "versionCode 解析: CR 探测 grep 异常（rc=$crc），fail-closed"
    return 1
  fi
  local n grc=0
  n=$(LC_ALL=C grep -caF 'alphagps.versionCode' "$path" 2>/dev/null) || grc=$?
  if (( grc > 1 )); then
    rl_fail "versionCode 解析: key 计数 grep 异常（rc=$grc），fail-closed"
    return 1
  fi
  if [[ "$n" != "1" ]]; then
    rl_fail "versionCode 解析: 包含该 key 子串的行必须恰好 1 行，实际 ${n:-0} 行——fail-closed"
    return 1
  fi
  # 抽取行 grep 的 rc 显式区分（不用 `|| true` 吞 rc2/rc127 等）：
  #   0=正常；1=无匹配（与上面计数==1 矛盾，fail-closed）；≥2=命令异常，fail-closed。
  # `||` 上下文同时保证裸 set -e 调用下不会因 rc1 提前静默退出。
  local xrc=0
  line=$(LC_ALL=C grep -aF 'alphagps.versionCode' "$path" 2>/dev/null) || xrc=$?
  if (( xrc == 1 )); then
    rl_fail "versionCode 解析: 抽取 grep 无匹配（与计数 1 矛盾），fail-closed"
    return 1
  fi
  if (( xrc >= 2 )); then
    rl_fail "versionCode 解析: 抽取 grep 异常（rc=$xrc），fail-closed"
    return 1
  fi
  if [[ ! "$line" =~ ^alphagps\.versionCode=[1-9][0-9]{0,9}$ ]]; then
    rl_fail "versionCode 解析: 该 key 所在行必须整行严格匹配 'key=canonical十进制正整数'（无空格/注释/尾随空白/前导零），实际: '$line'"
    return 1
  fi
  local v=${line#alphagps.versionCode=}
  rl_check_code "$v" \
    || { rl_fail "versionCode 解析: 非法值 '$v'（须为 canonical 十进制正整数且 ≤ 2147483647）"; return 1; }
  printf '%s\n' "$v"
}

# parse_version_code_from_git <tag> [仓库内路径=gradle.properties]
#   `git show <tag>:<path>` 的原始字节写入 mktemp 临时文件（不经 bash 变量——
#   变量会丢弃 NUL 仅留 warning，导致脏数据伪装成功），再走文件路径 parser。
#   在 ( ... ) subshell 内 `trap 'rm -f "$tmp"' EXIT` 并在 subshell 内完成 git rc
#   检查与 parser 调用：errexit/信号/正常返回均清理，且不覆盖调用方的 trap。
#   stdout 输出解析到的 N。
parse_version_code_from_git() {
  local tag=${1:?} path=${2:-gradle.properties}
  (
    tmp=$(mktemp "${TMPDIR:-/tmp}/rlgit.XXXXXX") \
      || { rl_fail "versionCode 解析: 无法创建临时文件，fail-closed"; exit 1; }
    trap 'rm -f -- "$tmp"' EXIT
    if ! git show "$tag:$path" > "$tmp" 2>/dev/null; then
      rl_fail "versionCode 解析: git show $tag:$path 失败（tag 不存在/路径缺失/git 异常），fail-closed"
      exit 1
    fi
    parse_version_code_props "$tmp"
  )
}

# expected_code_for_tag <tag>
#   取某 tag 的预期 versionCode 的统一入口：
#     - LEGACY 表中的历史 tag → 表值（其 gradle.properties 没有本 key）；
#     - 否则 → 该 tag 提交的 gradle.properties 严格解析（解析失败 fail-closed）。
#   stdout 输出 code。
expected_code_for_tag() {
  local tag=${1:?} v
  if v=$(legacy_code_for_tag "$tag"); then
    printf '%s\n' "$v"
    return 0
  fi
  parse_version_code_from_git "$tag"
}

# tag_code_or_skip <tag>
#   assert_code_monotonic 用的单 tag 判定（不入 LEGACY 表的 tag）：
#     rc=0 → 本定制版 tag，stdout=其 versionCode；
#     rc=3 → 上游 tag，跳过（且仅限两种可证明的“上游”情形：
#            a) tag 提交树里确实不存在 gradle.properties 路径；
#            b) 文件存在但其中无本 key 子串）；
#     rc=1 → fail-closed（tag 不可解析 / git ls-tree / git show / grep 异常 /
#            含 key 但解析失败）。
#   存在性判定（不再把任意 git show 失败当“上游缺文件”）：
#     1. `git rev-parse --verify --quiet "$tag^{commit}"` 先确认 tag 可解析为提交；
#     2. `git ls-tree --name-only "$tag" -- gradle.properties`：命令自身 rc≠0 →
#        fail-closed；rc=0 且输出恰为该路径 → 存在；rc=0 且输出为空 → 路径不
#        存在 → 上游跳过；输出含其它内容 → fail-closed（不该发生）。
#     3. 只有存在才 `git show` 取内容——此时 git show 失败一律 fail-closed。
#   git show 原始字节经 subshell 内 mktemp 临时文件中转（trap EXIT 清理，含信号路径）。
tag_code_or_skip() {
  local tag=${1:?}
  (
    if ! git rev-parse --verify --quiet "$tag^{commit}" >/dev/null 2>&1; then
      rl_fail "versionCode 单调性: tag $tag 无法解析为提交（rev-parse 失败），fail-closed"
      exit 1
    fi
    local lsrc lout
    if ! lout=$(git ls-tree --name-only "$tag" -- gradle.properties 2>/dev/null); then
      lsrc=$?
      rl_fail "versionCode 单调性: tag $tag 的 gradle.properties 存在性检查 git ls-tree 异常（rc=$lsrc），fail-closed"
      exit 1
    fi
    case "$lout" in
      gradle.properties) : ;;   # 路径确实存在 → 继续取内容解析
      '') exit 3 ;;             # 输出空且 rc=0：路径不存在 → 上游 tag，跳过
      *)
        rl_fail "versionCode 单调性: tag $tag 的 git ls-tree 输出异常（'$lout'），fail-closed"
        exit 1 ;;
    esac
    tmp=$(mktemp "${TMPDIR:-/tmp}/rlmono.XXXXXX") \
      || { rl_fail "versionCode 单调性: 无法创建临时文件，fail-closed"; exit 1; }
    trap 'rm -f -- "$tmp"' EXIT
    if ! git show "$tag:gradle.properties" > "$tmp" 2>/dev/null; then
      rl_fail "versionCode 单调性: tag $tag 的 git show 失败（ls-tree 已确认路径存在，非上游缺文件），fail-closed"
      exit 1
    fi
    LC_ALL=C grep -qaF 'alphagps.versionCode' "$tmp" 2>/dev/null
    local grc=$?
    if (( grc == 1 )); then exit 3; fi   # 无该 key：上游 tag，跳过
    if (( grc != 0 )); then
      rl_fail "versionCode 单调性: tag $tag 的 gradle.properties key 探测 grep 异常（rc=$grc），fail-closed"
      exit 1
    fi
    parse_version_code_props "$tmp" || {
      rl_fail "versionCode 单调性: 本定制版 tag $tag 的 gradle.properties versionCode 解析失败（fail-closed）"
      exit 1
    }
  )
}

# assert_code_monotonic <expect_code> <当前版本 X.Y.Z>
#   单调递增门禁：在版本排序（sort -V）中小于当前版本的所有 v? tag 里，取最大且
#   属本定制版的 tag，其 versionCode 为 PREV_CODE，要求 expect_code > PREV_CODE
#   （比较用 10# 前缀防八进制）。
#   本定制版判定（不再读 app/build.gradle.kts / 解析 Kotlin）：
#     - tag 在 LEGACY 表（v0.1.0/1/2）→ 本定制版，code 取表值；
#     - 否则 tag 提交的 gradle.properties 含本 key 子串 → 严格解析
#       （解析失败 fail-closed，绝不静默当上游）；解析成功即本定制版；
#     - 否则（无该 key / 文件缺失）→ 上游 tag（versionCode 体系不同，172 级别），跳过。
#   tag 枚举失败（git tag / grep 异常 rc / sort 失败）→ fail-closed；只有
#   “枚举成功但确实没有前一个定制版 tag”才 WARN 放行。
#   使用当前目录的 git 仓库（调用方负责 cwd 在仓库根）。
assert_code_monotonic() {
  local expect=$1 cur=$2
  rl_check_code "$expect" && [[ "$cur" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || { rl_fail "versionCode 单调性: 参数非法 expect='$expect' cur='$cur'"; return 1; }
  # 先把枚举结果收进变量并逐级检查退出码：git / grep / sort 任一失败 → fail-closed。
  # 不用 process substitution（会吞掉管道里非末段命令的失败），也不用 `|| true` 吞 grep
  # 的异常退出码（rc2/rc127 等）——那种吞法会把“过滤出错”伪装成“没有前 tag”→ 误 WARN 放行。
  local all_tags prev_tags sorted grc
  all_tags=$(git tag --list 2>/dev/null) || { rl_fail "versionCode 单调性: git tag 枚举失败（不在 git 仓库/git 异常），fail-closed"; return 1; }
  if prev_tags=$(printf '%s\n' "$all_tags" | grep -Ex 'v[0-9]+\.[0-9]+\.[0-9]+' 2>/dev/null); then
    grc=0
  else
    grc=$?
  fi
  # grep 只允许 0（有匹配）/ 1（无匹配）；2（用法/IO 错误）、127（命令不存在）等 → fail-closed
  case "$grc" in
    0|1) ;;
    *) rl_fail "versionCode 单调性: tag 过滤 grep 异常（rc=$grc），fail-closed"; return 1 ;;
  esac
  sorted=$( { printf '%s\n' "$prev_tags"; printf 'v%s\n' "$cur"; } | sort -Vu ) \
    || { rl_fail "versionCode 单调性: tag 排序失败（sort -Vu），fail-closed"; return 1; }
  local -a tags=()
  local tag prev_code
  local i   # 防止污染调用方同名变量（新增循环变量时同样 local）
  # 版本排序（sort -V）中严格排在 v$cur 之前的 tag；无该 tag 时用 v$cur 自身做分区点同样正确
  while IFS= read -r tag; do
    [[ "$tag" == "v$cur" ]] && break
    [[ -n "$tag" ]] || continue   # 空行（无匹配/空枚举时 printf 产生的空行）不入列
    tags+=("$tag")
  done <<<"$sorted"
  for (( i=${#tags[@]}-1; i>=0; i-- )); do
    tag=${tags[i]}
    if prev_code=$(legacy_code_for_tag "$tag"); then
      :   # LEGACY 表命中的本定制版 tag，code 已取表值
    else
      prev_code=$(tag_code_or_skip "$tag")
      local trc=$?
      if (( trc == 3 )); then
        continue   # 上游 tag：不同 versionCode 体系，跳过
      fi
      if (( trc != 0 )); then
        rl_fail "versionCode 单调性: tag $tag 判定失败（trc=$trc），fail-closed"
        return 1
      fi
    fi
    if (( 10#$expect <= 10#$prev_code )); then
      rl_fail "versionCode 单调性: expect($expect) 未相对上一发布 tag $tag 递增（其 versionCode=$prev_code）——请先 bump 仓库根 gradle.properties 中的 versionCode key"
      return 1
    fi
    echo "OK   versionCode 单调性: $expect > $tag(versionCode=$prev_code)"
    return 0
  done
  rl_warn "当前版本 $cur 之前找不到本定制版 tag——跳过 versionCode 单调性比较（仅警告）"
  return 0
}

# ── 本地发布配置（主仓库参数外置；不入库）──────────────────────────────────────
# 主发布仓库的主机/仓库路径/git 远端名不写进任何入库文件，改从本机 scripts/release.local.env
# （已 .gitignore）或环境变量读取。文件格式：每行 `KEY=VALUE`；允许空行与以 '#' 开头的整行
# 注释（注释内容可含中文，不被解释）；仅允许下列四个键；值只含 [A-Za-z0-9._:/-]；同键不得
# 重复；不得含 CR/NUL/引号/空白/'$'/反引号/非 ASCII 值等。脚本**不 source 该文件**——逐行解析、逐值校验后再赋值，
# 文件内容永远不会被当作 shell 代码执行（比“校验后 source”更严格）。
#   ALPHAGPS_PRIMARY_HOST    主仓库 host[:port]
#   ALPHAGPS_PRIMARY_REPO    主仓库 owner/repo
#   ALPHAGPS_PRIMARY_REMOTE  本机 git 远端名（主仓库）
#   ALPHAGPS_GITHUB_REMOTE   本机 git 远端名（GitHub；可选，默认 github）
# 同名环境变量优先于文件（同样经过校验）。

RL_CFG_EXAMPLE='示例（写入 scripts/release.local.env，该文件不入库；也可改用同名环境变量）：
  ALPHAGPS_PRIMARY_HOST=forge.example.com:443
  ALPHAGPS_PRIMARY_REPO=owner/alphagps
  ALPHAGPS_PRIMARY_REMOTE=origin
  # 可选（默认 github）：
  # ALPHAGPS_GITHUB_REMOTE=github'

# rl_cfg_py file <路径> → stdout 每行 KEY=VALUE（已校验）；rl_cfg_py value <KEY> <VALUE> → rc 0/1。
# 任一违规 → stderr FAIL + 非零。
rl_cfg_py() {
  python3 - "$@" <<'PY'
import re, sys

CHARS = re.compile(r"[A-Za-z0-9._:/-]+")
_remote = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")
SHAPE = {
    "ALPHAGPS_PRIMARY_HOST": re.compile(r"[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]{1,5})?"),
    "ALPHAGPS_PRIMARY_REPO": re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*"),
    "ALPHAGPS_PRIMARY_REMOTE": _remote,
    "ALPHAGPS_GITHUB_REMOTE": _remote,
}

def die(msg):
    sys.stderr.write(f"FAIL 本地发布配置: {msg}，fail-closed\n")
    sys.exit(1)

def check_value(key, val):
    if key not in SHAPE:
        die(f"不允许的键 '{key}'（仅允许 {', '.join(SHAPE)}）")
    if not CHARS.fullmatch(val):
        die(f"{key} 的值含非法字符（仅允许 [A-Za-z0-9._:/-]，且非空）")
    if ".." in val:
        die(f"{key} 的值含 '..'")
    if not SHAPE[key].fullmatch(val):
        die(f"{key} 的值形态非法（须以字母/数字开头，host[:port] / owner/repo / 远端名）")

mode = sys.argv[1]
if mode == "value":
    check_value(sys.argv[2], sys.argv[3])
    sys.exit(0)
if mode != "file":
    die(f"内部错误：未知模式 {mode}")
try:
    data = open(sys.argv[2], "rb").read()
except OSError as e:
    die(f"无法读取 {sys.argv[2]}: {e}")
if b"\x00" in data:
    die("文件含 NUL 字节")
if b"\r" in data:
    die("文件含 CR（要求纯 LF 行尾）")
try:
    text = data.decode("utf-8")
except UnicodeDecodeError:
    die("文件不是合法 UTF-8")
lines = text.split("\n")
if lines and lines[-1] == "":
    lines.pop()
seen = {}
for n, line in enumerate(lines, 1):
    if line == "" or line.startswith("#"):
        continue
    m = re.fullmatch(r"([A-Z][A-Z0-9_]*)=(.*)", line)
    if not m:
        die(f"第 {n} 行不是 KEY=VALUE 形式（键后不得有空白，不接受 export/引号）")
    key, val = m.group(1), m.group(2)
    check_value(key, val)
    if key in seen:
        die(f"第 {n} 行键 {key} 重复（首次出现在第 {seen[key]} 行）")
    seen[key] = n
    print(f"{key}={val}")
PY
}

# rl_load_release_config <env文件路径> <need_primary: 1|0>
#   解析顺序：同名环境变量（非空）> 文件 > 默认值（仅 GITHUB_REMOTE=github）。
#   设置全局：PRIMARY_HOST / PRIMARY_REPO / PRIMARY_REMOTE / GITHUB_REMOTE。
#   need_primary=1 且主仓库三项任一缺失 → fail-closed 并打印占位示例（绝不回退到任何内置真实值）。
#   文件不存在不是错误（全部由环境变量提供即可）；文件存在但非法 → fail-closed。
rl_load_release_config() {
  local file=${1:-} need=${2:-1} line k v src missing=""
  local -A fileval=()
  local parsed
  if [[ -n "$file" && ( -e "$file" || -L "$file" ) ]]; then
    [[ -f "$file" && ! -L "$file" ]] \
      || { rl_fail "本地发布配置: $file 必须是普通文件（非目录/符号链接），fail-closed"; return 1; }
    parsed=$(rl_cfg_py file "$file") || return 1
    while IFS= read -r line; do
      [[ -n "$line" ]] || continue
      fileval["${line%%=*}"]=${line#*=}
    done <<<"$parsed"
  fi
  PRIMARY_HOST=""; PRIMARY_REPO=""; PRIMARY_REMOTE=""; GITHUB_REMOTE=""
  for k in ALPHAGPS_PRIMARY_HOST ALPHAGPS_PRIMARY_REPO ALPHAGPS_PRIMARY_REMOTE ALPHAGPS_GITHUB_REMOTE; do
    v=${!k:-}; src=env
    if [[ -z "$v" ]]; then v=${fileval[$k]:-}; src=file; fi
    if [[ -n "$v" && "$src" == env ]]; then
      rl_cfg_py value "$k" "$v" || { rl_fail "本地发布配置: 环境变量 $k 的值非法"; return 1; }
    fi
    case "$k" in
      ALPHAGPS_PRIMARY_HOST)   PRIMARY_HOST=$v ;;
      ALPHAGPS_PRIMARY_REPO)   PRIMARY_REPO=$v ;;
      ALPHAGPS_PRIMARY_REMOTE) PRIMARY_REMOTE=$v ;;
      ALPHAGPS_GITHUB_REMOTE)  GITHUB_REMOTE=$v ;;
    esac
  done
  [[ -n "$GITHUB_REMOTE" ]] || GITHUB_REMOTE=github
  if [[ "$need" == 1 ]]; then
    [[ -n "$PRIMARY_HOST" ]]   || missing+=" ALPHAGPS_PRIMARY_HOST"
    [[ -n "$PRIMARY_REPO" ]]   || missing+=" ALPHAGPS_PRIMARY_REPO"
    [[ -n "$PRIMARY_REMOTE" ]] || missing+=" ALPHAGPS_PRIMARY_REMOTE"
    if [[ -n "$missing" ]]; then
      rl_fail "本地发布配置缺失:${missing}（fail-closed；主仓库参数不内置于仓库）
$RL_CFG_EXAMPLE"
      return 1
    fi
  fi
  return 0
}
