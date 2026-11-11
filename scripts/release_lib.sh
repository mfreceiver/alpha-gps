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
# 注释（注释内容可含中文，不被解释）；仅允许下列六个键；值只含 [A-Za-z0-9._:/-]；同键不得
# 重复；不得含 CR/NUL/引号/空白/'$'/反引号/非 ASCII 值等。脚本**不 source 该文件**——逐行解析、逐值校验后再赋值，
# 文件内容永远不会被当作 shell 代码执行（比“校验后 source”更严格）。
#   ALPHAGPS_PRIMARY_HOST    主仓库 host[:port]
#   ALPHAGPS_PRIMARY_REPO    主仓库 owner/repo
#   ALPHAGPS_PRIMARY_REMOTE  本机 git 远端名（主仓库）
#   ALPHAGPS_GITHUB_REMOTE   本机 git 远端名（GitHub；可选，默认 github）
#   ALPHAGPS_SIGNING_KEYSTORE 签名 keystore 路径（可选；缺省用 release_build.sh 内置默认）。须为绝对路径，
#                            或以 '~/' 开头（脚本把开头的 '~/' 替换为 $HOME/；这是唯一的展开，不做任何
#                            shell 展开）；路径段只含 [A-Za-z0-9._-]，不含 '..'/空段
#   ALPHAGPS_SIGNING_ALIAS   签名 key alias（可选；缺省用内置默认）；形态 [A-Za-z0-9][A-Za-z0-9._-]*
# 同名环境变量优先于文件（同样经过校验）。配置了签名两键时覆盖调用方预设的
# SIGNING_KEYSTORE_PATH / SIGNING_KEY_ALIAS；未配置则保持调用方的默认值不变。

RL_CFG_EXAMPLE='示例（写入 scripts/release.local.env，该文件不入库；也可改用同名环境变量）：
  ALPHAGPS_PRIMARY_HOST=forge.example.com:443
  ALPHAGPS_PRIMARY_REPO=owner/alphagps
  ALPHAGPS_PRIMARY_REMOTE=origin
  # 可选（默认 github）：
  # ALPHAGPS_GITHUB_REMOTE=github
  # 可选（签名 keystore / alias 不在内置默认位置时）：
  # ALPHAGPS_SIGNING_KEYSTORE=~/.config/alphagps/signing/release.jks
  # ALPHAGPS_SIGNING_ALIAS=release'

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
    # 绝对路径；开头的 '~/' 在下面先折成 '/'（见 check_value），其余字符一律按白名单校验
    "ALPHAGPS_SIGNING_KEYSTORE": re.compile(r"/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*"),
    "ALPHAGPS_SIGNING_ALIAS": re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*"),
}

def die(msg):
    sys.stderr.write(f"FAIL 本地发布配置: {msg}，fail-closed\n")
    sys.exit(1)

def check_value(key, val):
    if key not in SHAPE:
        die(f"不允许的键 '{key}'（仅允许 {', '.join(SHAPE)}）")
    if key == "ALPHAGPS_SIGNING_KEYSTORE" and val.startswith("~/"):
        val = val[1:]  # 仅允许开头的 '~/'（bash 侧替换为 $HOME/）；'~user'、中间的 '~' 仍被下面拒绝
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
#   设置全局：PRIMARY_HOST / PRIMARY_REPO / PRIMARY_REMOTE / GITHUB_REMOTE；
#   另：配置了 ALPHAGPS_SIGNING_KEYSTORE（'~/' 前缀→$HOME/）/ ALPHAGPS_SIGNING_ALIAS 时才覆盖
#   SIGNING_KEYSTORE_PATH / SIGNING_KEY_ALIAS（未配置则不动调用方预设的默认值）。
#   need_primary=1 且主仓库三项任一缺失 → fail-closed 并打印占位示例（绝不回退到任何内置真实值）。
#   文件不存在不是错误（全部由环境变量提供即可）；文件存在但非法 → fail-closed。
rl_load_release_config() {
  local file=${1:-} need=${2:-1} line k v src missing="" cfg_ks="" cfg_alias=
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
  for k in ALPHAGPS_PRIMARY_HOST ALPHAGPS_PRIMARY_REPO ALPHAGPS_PRIMARY_REMOTE ALPHAGPS_GITHUB_REMOTE \
           ALPHAGPS_SIGNING_KEYSTORE ALPHAGPS_SIGNING_ALIAS; do
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
      ALPHAGPS_SIGNING_KEYSTORE) cfg_ks=$v ;;
      ALPHAGPS_SIGNING_ALIAS)  cfg_alias=$v ;;
    esac
  done
  if [[ -n "$cfg_ks" ]]; then
    if [[ "$cfg_ks" == "~/"* ]]; then
      [[ "${HOME:-}" == /* ]] || { rl_fail "本地发布配置: ALPHAGPS_SIGNING_KEYSTORE 使用 '~/' 但 HOME 不是绝对路径，fail-closed"; return 1; }
      cfg_ks=${HOME%/}/${cfg_ks#"~/"}
    fi
    SIGNING_KEYSTORE_PATH=$cfg_ks
  fi
  [[ -z "$cfg_alias" ]] || SIGNING_KEY_ALIAS=$cfg_alias
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

# ── GitHub 步骤纯函数（M10 双源发布；无网络，可离线自测）────────────────────────
# 公开仓库与主机白名单（常量）。带凭据的请求只允许去 api/upload 主机；下载对账是匿名的
# （发布是公开 release），首跳 github.com，唯一一次重定向只允许落在下载白名单内。
RL_GH_REPO=mfreceiver/alpha-gps
RL_GH_API_HOSTS="api.github.com"
RL_GH_UPLOAD_HOSTS="uploads.github.com"
# 下载白名单核对记录（2026-11-10，开发时联网）：
#   GitHub 当前对 release 资产的 302 目标是 release-assets.githubusercontent.com
#   （实测：api.github.com/repos/<o>/<r>/releases/assets/<id> 带 Accept: application/octet-stream、
#   --no-location → 302 Location: https://release-assets.githubusercontent.com/github-production-release-asset/...）；
#   历史/回退域名 objects.githubusercontent.com 一并保留。该主机上 github.com 网页路径本机不可达
#   （超时），故网页路径的 302 目标按 GitHub 文档与上述同源 CDN 推断，未逐字节实测。
RL_GH_DOWNLOAD_HOSTS="github.com objects.githubusercontent.com release-assets.githubusercontent.com"

# rl_gh_url_allowed <api|upload|download> <url>
#   URL 白名单纯函数（urllib.parse，fail-closed）：URL 非空、无控制字符/空白/反斜杠、不含
#   '@'（userinfo 旁路）、必须以小写 'https://' 开头（http 降级拒绝）、无 userinfo、无 fragment、
#   netloc 与白名单项**精确相等**（大小写敏感；因此带端口、子域、后缀拼接、尾点、大写一律拒绝）。
rl_gh_url_allowed() {
  local kind=${1:-} url=${2:-} hosts
  case "$kind" in
    api)      hosts=$RL_GH_API_HOSTS ;;
    upload)   hosts=$RL_GH_UPLOAD_HOSTS ;;
    download) hosts=$RL_GH_DOWNLOAD_HOSTS ;;
    *) rl_fail "GitHub URL 白名单: 未知类别 '$kind'，fail-closed"; return 1 ;;
  esac
  python3 - "$url" "$hosts" "$kind" <<'PY'
import sys
from urllib.parse import urlsplit

url, hosts, kind = sys.argv[1], sys.argv[2].split(), sys.argv[3]

def fail(msg):
    sys.stderr.write(f"FAIL GitHub URL 白名单({kind}): {msg}，fail-closed\n")
    sys.exit(1)

if not url:
    fail("URL 为空")
for c in url:
    if ord(c) < 0x20 or ord(c) == 0x7F or c.isspace() or c == "\\":
        fail(f"URL 含控制字符/空白/反斜杠（ord={ord(c):#x}）")
if "@" in url:
    fail("URL 含 '@'（userinfo 旁路）")
if not url.startswith("https://"):
    fail(f"必须以小写 'https://' 开头（拒绝 http 降级/大小写 scheme），实际开头 {url[:12]!r}")
p = urlsplit(url)
if p.scheme != "https":
    fail(f"scheme 必须是 https，实际 '{p.scheme}'")
if p.username is not None or p.password is not None:
    fail("URL 含 userinfo")
if p.fragment:
    fail("URL 含 fragment")
if p.netloc not in hosts:
    fail(f"主机 '{p.netloc}' 不在白名单 {hosts}")
sys.exit(0)
PY
}

# rl_gh_location_from_headers <curl -D 头文件>
#   取**最后一个**响应块里恰好一个 Location 头（头名大小写不敏感），stdout 输出其值；
#   0 个/多个/无 HTTP 状态行 → 非零。相对 Location 原样输出（交给 rl_gh_url_allowed 拒绝）。
rl_gh_location_from_headers() {
  python3 - "${1:-}" <<'PY'
import sys
try:
    raw = open(sys.argv[1], "rb").read().decode("latin-1")
except OSError as e:
    sys.stderr.write(f"FAIL 重定向头: 无法读取头文件: {e}\n"); sys.exit(1)
blocks = [b for b in raw.replace("\r\n", "\n").split("\n\n") if b.strip()]
if not blocks or not blocks[-1].lstrip().startswith("HTTP/"):
    sys.stderr.write("FAIL 重定向头: 无 HTTP 响应块，fail-closed\n"); sys.exit(1)
locs = []
for line in blocks[-1].split("\n")[1:]:
    if ":" in line:
        k, v = line.split(":", 1)
        if k.strip().lower() == "location":
            locs.append(v.strip())
if len(locs) != 1 or not locs[0]:
    sys.stderr.write(f"FAIL 重定向头: Location 头须恰好 1 个（实际 {len(locs)}），fail-closed\n"); sys.exit(1)
print(locs[0])
PY
}

# ── GitHub 推送目的地校验（P2-1：验证 push 的**全部有效目的地**，并把传输钉死在已验证 URL）──
# 允许的公开 URL 只有一个规范形式（常量，**没有任何环境变量/配置可以覆盖**；自测靠沙箱里的
# fake git 在传输层替换，不削弱本校验）：
RL_GH_CANON_URL="https://github.com/$RL_GH_REPO.git"

# rl_gh_canon_url <url>
#   规范化比较函数：url 与 https://github.com/<RL_GH_REPO> 或 …/<RL_GH_REPO>.git **逐字节精确相等**
#   （大小写敏感；拒绝尾斜杠/userinfo/query/fragment/端口/空白/http/ssh/本地路径）才合法，
#   合法时 stdout 输出规范形式 RL_GH_CANON_URL（统一带 .git），否则 rc=1 且无输出。
rl_gh_canon_url() {
  case "${1-}" in
    "https://github.com/$RL_GH_REPO"|"https://github.com/$RL_GH_REPO.git") printf '%s\n' "$RL_GH_CANON_URL" ;;
    *) return 1 ;;
  esac
}

# rl_gh_remote_url_ok <url>   谓词封装：url 是否等于允许的公开 URL（任一规范等价形式）
rl_gh_remote_url_ok() { rl_gh_canon_url "${1-}" >/dev/null; }

# rl_gh_url_display   stdin=URL → stdout 净化后的展示串（错误日志里打印 URL 一律先经此函数）：
#   丢弃 userinfo（最后一个 '@' 之前的全部内容，含 token）、query、fragment，控制字符→'?'，
#   最多 60 字符。URL 经 stdin 传入（不进 argv，避免 /proc 可见）。
rl_gh_url_display() {
  python3 -c '
import sys
u = sys.stdin.read()
scheme = ""
if "://" in u:
    scheme, _, rest = u.partition("://")
    scheme += "://"
    if not scheme.replace("://", "").isalnum():
        scheme = "?://"
else:
    rest = u
if "@" in rest:
    rest = rest[rest.rindex("@") + 1:]
for sep in "?#":
    if sep in rest:
        rest = rest.split(sep, 1)[0]
out = scheme + rest
out = "".join(c if 0x20 <= ord(c) < 0x7F else "?" for c in out)
sys.stdout.write(out if len(out) <= 60 else out[:60] + "...")'
}

# rl_gh_urls_describe <换行分隔的 URL 列表>   → stdout 净化后的逗号分隔展示（最多 3 条）
rl_gh_urls_describe() {
  local line out="" n=0
  while IFS= read -r line; do
    n=$((n+1)); (( n <= 3 )) || break
    out+="${out:+, }$(printf '%s' "$line" | rl_gh_url_display)"
  done <<<"${1-}"
  printf '%s' "$out"
}

# rl_gh_single_canon <标签> <换行分隔的 URL 列表>
#   列表必须**恰好一条**且等于允许的公开 URL（rl_gh_canon_url），否则 fail-closed（0 条/多条/不符）。
#   stdout 输出规范 URL。错误中的 URL 一律净化（见 rl_gh_url_display）。
rl_gh_single_canon() {
  local label=${1:-} list=${2-} n canon
  if [[ -z "$list" ]]; then
    rl_fail "GitHub 远端 $label 有效目的地为空（fail-closed）"; return 1
  fi
  n=$(printf '%s\n' "$list" | wc -l | tr -d ' ')
  if [[ "$n" != 1 ]]; then
    rl_fail "GitHub 远端 $label 有效目的地有 $n 个（必须恰好 1 个且等于 $RL_GH_CANON_URL；净化后: $(rl_gh_urls_describe "$list")）（fail-closed）"
    return 1
  fi
  canon=$(rl_gh_canon_url "$list") || {
    rl_fail "GitHub 远端 $label 有效目的地必须精确为 $RL_GH_CANON_URL（或无 .git 后缀的等价形式）（实际，已净化: $(printf '%s' "$list" | rl_gh_url_display)）（fail-closed）"
    return 1
  }
  printf '%s\n' "$canon"
}

# rl_gh_url_unrewritten <url>
#   传输直接用 URL 字面量（不经远端名）；git 仍会对字面量套用 url.<base>.insteadOf / pushInsteadOf。
#   本函数读取全部生效配置里的这两类规则：任何规则的**值**是该 URL 的前缀（含空值）→ 会被改写 → 拒绝。
#   读配置失败（rc 非 0/1）同样 fail-closed。URL 经 argv 传入（仅公开常量）。
rl_gh_url_unrewritten() {
  python3 - "${1-}" <<'PY'
import subprocess, sys
url = sys.argv[1].encode()
r = subprocess.run(["git", "config", "-z", "--get-regexp", r"^url\..*\.(insteadof|pushinsteadof)$"],
                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
if r.returncode not in (0, 1):
    sys.stderr.write(f"FAIL GitHub 目的地: 读取 git 配置失败（rc={r.returncode}），fail-closed\n"); sys.exit(1)
bad = []
for ent in r.stdout.split(b"\0"):
    if not ent:
        continue
    key, _, val = ent.partition(b"\n")
    if url.startswith(val):
        kind = "pushInsteadOf" if key.lower().endswith(b".pushinsteadof") else "insteadOf"
        shown = "".join(chr(c) if 0x20 <= c < 0x7F else "?" for c in val.split(b"@")[-1][:60])
        bad.append(f"{kind}(值前缀 '{shown}')")
if bad:
    sys.stderr.write("FAIL GitHub 目的地: 存在会改写公开 URL 的 url.<base>.insteadOf/pushInsteadOf 规则 "
                     + ", ".join(bad) + "，fail-closed\n")
    sys.exit(1)
PY
}

# rl_gh_verify_remote <远端名>
#   验证本机 GitHub 远端的**全部有效目的地**，成功时设置全局 RL_GH_PINNED_URL（= 规范公开 URL）：
#     ① git remote get-url --all  <名>：fetch 侧（含 insteadOf 重写后）逐条检查，恰好 1 条且等于允许 URL
#     ② git remote get-url --push --all <名>：push 侧（含 pushurl / pushInsteadOf 重写后）同上
#     ③ 两侧规范形式一致
#     ④ 规范 URL 字面量本身不被任何 insteadOf/pushInsteadOf 规则改写（rl_gh_url_unrewritten）
#   调用方随后对 RL_GH_PINNED_URL **字面量**做 ls-remote/push（不再经远端名，从而 remote.<名>.push/
#   mirror/pushurl 等一切远端级配置都不再参与目的地决策）。任一不符 → rc=1（fail-closed）。
rl_gh_verify_remote() {
  local remote=${1:-} fetch_list push_list cf cp
  RL_GH_PINNED_URL=""
  [[ -n "$remote" ]] || { rl_fail "GitHub 远端名为空（fail-closed）"; return 1; }
  fetch_list=$(git remote get-url --all "$remote" 2>/dev/null) || {
    rl_fail "GitHub 远端 '$remote' 不存在（fail-closed，脚本不自动创建）。请先执行: git remote add $remote $RL_GH_CANON_URL"
    return 1
  }
  push_list=$(git remote get-url --push --all "$remote" 2>/dev/null) || {
    rl_fail "GitHub 远端 '$remote' 的 push 目的地解析失败（fail-closed）"; return 1
  }
  cf=$(rl_gh_single_canon "fetch($remote)" "$fetch_list") || return 1
  cp=$(rl_gh_single_canon "push($remote)" "$push_list") || return 1
  [[ "$cf" == "$cp" ]] || { rl_fail "GitHub 远端 '$remote' fetch/push 目的地不一致（fail-closed）"; return 1; }
  rl_gh_url_unrewritten "$cf" || return 1
  RL_GH_PINNED_URL=$cf
  return 0
}

# rl_gh_curl_config   stdin=token → stdout curl 配置（-K - 用；token 只经配置流，绝不进 argv）
#   token 形态严格校验（[A-Za-z0-9_.-]+，防头注入）；Accept/版本/UA 头一并写入。
rl_gh_curl_config() {
  python3 -c '
import json, re, sys
tok = sys.stdin.read()
if not re.fullmatch(r"[A-Za-z0-9_.-]+", tok):
    sys.stderr.write("FAIL GitHub token 形态非法（仅允许 [A-Za-z0-9_.-]，非空），fail-closed\n"); sys.exit(1)
print("header = " + json.dumps("Authorization: Bearer " + tok))
print("header = \"Accept: application/vnd.github+json\"")
print("header = \"X-GitHub-Api-Version: 2022-11-28\"")
print("user-agent = \"alpha-gps-release\"")'
}

# rl_gh_release_field <release JSON 文件> <id|assets>
#   id     → stdout 正整数 release id
#   assets → 每行 TSV：name \t size \t sha256|- \t state \t browser_download_url
#            （sha256 取自 digest 字段 "sha256:<64hex>"，否则 '-'；名称含 TAB/换行 → 失败）
rl_gh_release_field() {
  python3 - "${1:-}" "${2:-}" <<'PY'
import json, re, sys
def die(m):
    sys.stderr.write(f"FAIL GitHub release JSON: {m}，fail-closed\n"); sys.exit(1)
try:
    rel = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception as e:
    die(f"解析失败: {e}")
if not isinstance(rel, dict):
    die("顶层不是对象")
mode = sys.argv[2]
if mode == "id":
    i = rel.get("id")
    if not isinstance(i, int) or isinstance(i, bool) or i <= 0:
        die("id 缺失或非正整数")
    print(i)
elif mode == "assets":
    assets = rel.get("assets", [])
    if not isinstance(assets, list):
        die("assets 非列表")
    for a in assets:
        if not isinstance(a, dict):
            die("assets 元素非对象")
        name = str(a.get("name", ""))
        url = str(a.get("browser_download_url") or "")
        if not name or any(c in name + url for c in "\t\n\r"):
            die("资产名/URL 为空或含 TAB/换行")
        d = a.get("digest") or ""
        m = re.fullmatch(r"sha256:([0-9a-fA-F]{64})", d) if isinstance(d, str) else None
        print("\t".join([name, str(a.get("size", -1)), m.group(1).lower() if m else "-",
                         str(a.get("state", "")), url]))
else:
    die(f"未知模式 {mode}")
PY
}
