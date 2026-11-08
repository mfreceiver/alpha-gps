#!/usr/bin/env bash
# release_lib.sh —— 发版脚本共用纯函数（release_build.sh source；自测脚本直接 source）。
# 全部函数遵循 fail-closed：解析/门禁失败 → stderr 带关键字 + 非零返回。
# 依赖 python3 做注释剥离；python3 缺失/执行失败/输入含不支持构造
#   → 显式 FAIL 提示依赖/原因并以非零返回，fail-closed。

rl_fail() { echo "FAIL $1" >&2; }   # 注意：调用方必须紧跟 `return 1`（在 `||` 里不会替调用方退出函数）
rl_warn() { echo "WARN $1" >&2; }

# rl_strip_kotlin_comments_file <文件|/dev/stdin>: 窄白名单词法扫描——只支持：
#   - `//` 行注释（删除注释文本，保留换行）；
#   - 普通单行字符串 "..."（`\"`、`\\` 等反斜杠转义；字符串内的 //、/*、*/ 是普通内容）；
#   - 字符字面量 'x' / '\n' / '\uXXXX'（完整 4 位十六进制，按 6 字符消费）。
# 以下构造在 CODE 状态遇到即整体 fail-closed（设计决策：不再尝试完整 Kotlin 词法，
# 实测全部 89 个 v* tag 的 app/build.gradle.kts 与当前文件均不含这些构造）：
#   - 块注释起始 `/*`（字符串内的 `/*` 是普通内容，不算）；
#   - 三引号 `"""`（任何位置，含四引号结尾等 raw string 变体）；
#   - 字符串内 `${` 模板、普通字符串内裸换行（未闭合）；
#   - 未闭合的字符串/字符字面量、非法/截断的 \uXXXX、NUL 字节、非 UTF-8 输入。
# BOM：显式剥离首个 U+FEFF 后扫描（中段 BOM 按 NUL 之外的普通字符处理，不特殊接受）。
# CRLF：不归一化，逐字节原样保留（\r 属 [[:space:]]，下游整行匹配兼容）。
# 说明：NUL 必须在此层拒绝——bash 变量无法承载 NUL，`read -d ''` 会截断出错误结果。
# 脚本经 -c 传入（而非 heredoc），python 的 fd0 保持不动，`/dev/stdin` 模式可直接读数据。
RL_PY=$(cat <<'RL_PY'
import sys
def die(m):
    sys.stderr.write("FAIL 注释剥离: %s\n" % m)
    sys.exit(1)
try:
    with open(sys.argv[1], "rb") as f:
        raw = f.read()
except OSError as e:
    die("文件不可读: %s" % e)
if raw.startswith(b"\xef\xbb\xbf"):
    raw = raw[3:]
try:
    d = raw.decode("utf-8", "surrogateescape")
except UnicodeDecodeError as e:
    die("非 UTF-8 输入: %s" % e)
if "\0" in d:
    die("输入包含 NUL 字节（bash 变量无法安全承载 NUL），fail-closed")
HEX = "0123456789abcdefABCDEF"
out = []
i, n = 0, len(d)
while i < n:
    c = d[i]
    if c == "/" and d[i + 1:i + 2] == "/":            # 行注释：删到行尾，保留 \n
        j = d.find("\n", i)
        i = n if j < 0 else j
    elif c == "/" and d[i + 1:i + 2] == "*":
        die("不支持构造: 块注释 /*（窄白名单设计：仅支持行注释，其余 fail-closed）")
    elif d[i:i + 3] == '"""':
        die("不支持构造: 三引号 \"\"\"（raw string，窄白名单设计，fail-closed）")
    elif c == '"':                                     # 普通单行字符串
        j = i + 1
        while True:
            if j >= n:
                die("字符串未闭合")
            ch = d[j]
            if ch == "\n":
                die("字符串未闭合（普通字符串内出现裸换行）")
            if ch == "\\":
                if j + 1 >= n or d[j + 1] == "\n":
                    die("字符串未闭合（结尾悬挂反斜杠）")
                j += 2
            elif ch == '"':
                j += 1
                break
            elif ch == "$" and d[j + 1:j + 2] == "{":
                die("不支持构造: 字符串模板 ${（窄白名单设计，fail-closed）")
            else:
                j += 1
        out.append(d[i:j]); i = j
    elif c == "'":                                     # 字符字面量（\uXXXX 按 6 字符消费）
        j = i + 1
        if d[j:j + 1] != "\\":
            if d[j:j + 1] in ("", "\n"):
                die("字符字面量未闭合")
            j += 1
        elif d[j + 1:j + 2] == "u":
            k = j + 2
            if len(d[k:k + 4]) != 4 or any(x not in HEX for x in d[k:k + 4]):
                die("字符字面量 \\uXXXX 转义非法/截断（须完整 4 位十六进制）")
            j = k + 4
        else:
            if d[j + 1:j + 2] in ("", "\n"):
                die("字符字面量未闭合")
            j += 2
        if d[j:j + 1] != "'":
            die("字符字面量未闭合")
        j += 1
        out.append(d[i:j]); i = j
    else:
        out.append(c); i += 1
sys.stdout.buffer.write("".join(out).encode("utf-8", "surrogateescape"))
RL_PY
)
rl_strip_kotlin_comments_file() {
  command -v python3 >/dev/null 2>&1 \
    || { rl_fail "注释剥离: 缺少依赖 python3（词法扫描工具不可用），fail-closed"; return 1; }
  python3 -c "$RL_PY" "${1:?}" \
    || { rl_fail "注释剥离: 词法扫描失败，fail-closed"; return 1; }
}

# rl_strip_kotlin_comments: stdin → stdout。python 直接读 /dev/stdin（不经 bash 变量，
#   无临时文件；fd0 是否可用/是否终端由调用方先行检查）。
rl_strip_kotlin_comments() {
  rl_strip_kotlin_comments_file /dev/stdin
}

# rl_stdin_usable: fd0 可读性探测（fd0 已关闭 → 非零）。
#   实测选取 `[ -e /dev/fd/0 ]`：fd0 关闭时 /dev/fd/0 不存在 → 拒绝；终端/管道/here-string/
#   文件重定向（含 /dev/null）下均存在 → 放行。`{ : <&0; }` 的 dup 与 `cat <&0` 在 fd0
#   关闭时仍 rc=0（不可靠）；`read -t 0` 在无就绪数据的终端上会误报不可用（不可靠）。
#   必须在进入命令替换（cat 读自己的捕获 pipe）之前调用。
rl_stdin_usable() { [[ -e /dev/fd/0 ]]; }

# rl_check_code <值>: canonical 十进制正整数（^[1-9][0-9]{0,9}$）且 ≤ 2147483647
#   （Android Int 上限；10 位时用等长字符串字典序比较，不进算术，避免 2^31/2^63 回绕）。
rl_check_code() {
  local v=${1:-}
  [[ "$v" =~ ^[1-9][0-9]{0,9}$ ]] || return 1
  [[ ${#v} -ne 10 ]] && return 0
  [[ ! "$v" > 2147483647 ]]
}

# rl_stdin_guard <名字>: stdin 模式公共前置——终端拒绝（防挂起等交互输入）、
#   fd0 关闭拒绝（防命令替换子 shell 里 cat 读自己的捕获 pipe 导致永久挂起）。
rl_stdin_guard() {
  [[ -t 0 ]] && { rl_fail "$1 解析: stdin 是终端（会挂起等待输入），fail-closed"; return 1; }
  if ! rl_stdin_usable; then
    rl_fail "$1 解析: stdin 不可用（fd0 已关闭），fail-closed"
    return 1
  fi
  return 0
}

# parse_version_code <文件路径|->
#   严格解析 `versionCode = N`（整行；先窄白名单剥行注释；等号两侧空白容忍；canonical 十进制
#   正整数且 ≤ 2147483647），必须恰好匹配 1 处，否则 fail-closed（0 处或多处都拒绝——
#   防止 pick 错误行或构建文件被意外改出第二处；仅存在于注释中的伪赋值不算；
#   词法层不支持 raw string/多行字符串，字符串内容不跨行，行匹配不会命中字符串内部）。
#   "-" 从 stdin 读（配合 `git show <tag>:app/build.gradle.kts` 用）。
#   stdout 输出解析到的 N。
parse_version_code() {
  local stripped
  if [[ "${1:-}" == "-" ]]; then
    rl_stdin_guard versionCode || return 1
    stripped=$(rl_strip_kotlin_comments) || return 1
  else
    [[ -n "${1:-}" && -f "$1" ]] || { rl_fail "versionCode 解析: 文件不存在: '${1:-}'"; return 1; }
    stripped=$(rl_strip_kotlin_comments_file "$1") || return 1
  fi
  local -a hits
  mapfile -t hits < <(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' <<<"$stripped")
  if (( ${#hits[@]} != 1 )); then
    rl_fail "versionCode 解析: 'versionCode = N'（整行）必须恰好匹配 1 处，实际 ${#hits[@]} 处——fail-closed"
    return 1
  fi
  rl_check_code "${hits[0]}" \
    || { rl_fail "versionCode 解析: 非法值 '${hits[0]}'（须为 canonical 十进制正整数且 ≤ 2147483647，拒绝前导零/超 Int 上限）"; return 1; }
  printf '%s\n' "${hits[0]}"
}

# parse_version_code_from_git <tag> <仓库内路径>
#   `git show <tag>:<path>` 的原始字节直写临时文件（不经 bash 变量——变量会丢弃 NUL
#   仅留 warning，导致脏数据伪装成功），git rc 独立检查，再走文件路径 parser。
#   临时文件任何退出路径均清理；stdout 输出解析到的 N。
parse_version_code_from_git() {
  local tag=${1:?} path=${2:?} tmp rc
  tmp=$(mktemp "${TMPDIR:-/tmp}/rlgit.XXXXXX") \
    || { rl_fail "versionCode 解析: 无法创建临时文件，fail-closed"; return 1; }
  if ! git show "$tag:$path" > "$tmp" 2>/dev/null; then
    rm -f -- "$tmp"
    rl_fail "versionCode 解析: git show $tag:$path 失败（tag 不存在/路径缺失/git 异常），fail-closed"
    return 1
  fi
  parse_version_code "$tmp"
  rc=$?
  rm -f -- "$tmp"
  return "$rc"
}

# parse_application_id <文件路径|->
#   注释感知地解析 `applicationId = "..."`（先剥行注释再匹配
#   ^\s*applicationId\s*=\s*"([^"]+)"\s*$），必须恰好 1 处。
#   stdout 输出解析到的包名（rc=0）；0 处/多处/文件不可读/含不支持构造 → rc≠0 + FAIL 关键字
#   （调用方据此区分“身份无法确定”并 fail-closed，绝不静默当上游）。
parse_application_id() {
  local stripped
  if [[ "${1:-}" == "-" ]]; then
    rl_stdin_guard applicationId || return 1
    stripped=$(rl_strip_kotlin_comments) || return 1
  else
    [[ -n "${1:-}" && -f "$1" ]] || { rl_fail "applicationId 解析: 文件不存在: '${1:-}'"; return 1; }
    stripped=$(rl_strip_kotlin_comments_file "$1") || return 1
  fi
  local -a ids
  mapfile -t ids < <(sed -nE 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*$/\1/p' <<<"$stripped")
  if (( ${#ids[@]} != 1 )); then
    rl_fail "applicationId 解析: 'applicationId = \"...\"'（整行）必须恰好匹配 1 处，实际 ${#ids[@]} 处——身份无法确定，fail-closed"
    return 1
  fi
  printf '%s\n' "${ids[0]}"
}

# assert_code_monotonic <expect_code> <当前版本 X.Y.Z>
#   单调递增门禁：在版本排序中小于当前版本的所有 v? tag 里，取“最大且属本定制版”的 tag，
#   其 tag 提交的 app/build.gradle.kts 中 versionCode 为 PREV_CODE，要求
#   expect_code > PREV_CODE（比较用 10# 前缀防八进制）。
#
#   身份判定（实测依据：对本仓库全部非 v0.* 上游 tag 逐个 `git show <tag>:app/build.gradle.kts`
#   检查，v1.0.1～v1.7.1 均恰好 1 处 `applicationId = "com.saschl.cameragps"`，无缺失/其它写法）：
#     - 注释感知解析出恰好 1 处 applicationId：
#         = cn.vectory.alphagps        → 本定制版，参与比较；
#         = 其它明确值（com.saschl.cameragps 等）→ 上游 tag（versionCode 体系不同，为 172 级别），跳过；
#     - 0 处 / 多处 / 注释干扰 / tag 源码不可读 → 身份无法确定 → fail-closed（不静默当上游）。
#   为什么不用 `v0.*` 前缀过滤：将来定制版版本号超过 1.x（如 v2.0.0）时会漏进/漏掉错误的 tag。
#
#   tag 源码经临时文件中转（`git show` 原始字节直写文件，不经 bash 变量——NUL 会被
#   bash 静默丢弃仅留 warning，导致脏数据放行）；git rc 独立检查；临时文件任何路径清理。
#   tag 枚举失败（git tag / sort 失败）→ fail-closed；只有“枚举成功但确实没有前一个定制版 tag”
#   才 WARN 放行。命中本定制版 tag 但其 versionCode 解析失败 → fail-closed。
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
  local tag tmp aid prev_code
  local i   # 防止污染调用方同名变量（j 等其它循环变量目前不存在；新增时同样 local）
  # 版本排序（sort -V）中严格排在 v$cur 之前的 tag；无该 tag 时用 v$cur 自身做分区点同样正确
  while IFS= read -r tag; do
    [[ "$tag" == "v$cur" ]] && break
    [[ -n "$tag" ]] || continue   # 空行（无匹配/空枚举时 printf 产生的空行）不入列
    tags+=("$tag")
  done <<<"$sorted"
  tmp=$(mktemp "${TMPDIR:-/tmp}/rlmono.XXXXXX") \
    || { rl_fail "versionCode 单调性: 无法创建临时文件，fail-closed"; return 1; }
  for (( i=${#tags[@]}-1; i>=0; i-- )); do
    tag=${tags[i]}
    if ! git show "$tag:app/build.gradle.kts" > "$tmp" 2>/dev/null; then
      rm -f -- "$tmp"
      rl_fail "versionCode 单调性: git show $tag:app/build.gradle.kts 失败（不可读: tag 不存在/路径缺失/git 异常），fail-closed"
      return 1
    fi
    aid=$(parse_application_id "$tmp") \
      || { rm -f -- "$tmp"; rl_fail "versionCode 单调性: tag $tag 的 applicationId 身份无法确定（0 处/多处/注释干扰），fail-closed"; return 1; }
    if [[ "$aid" != "cn.vectory.alphagps" ]]; then
      continue   # 上游 tag：不同 versionCode 体系，跳过
    fi
    prev_code=$(parse_version_code "$tmp") || {
      rm -f -- "$tmp"
      rl_fail "versionCode 单调性: 本定制版 tag $tag 的 app/build.gradle.kts versionCode 解析失败（fail-closed）"
      return 1
    }
    rm -f -- "$tmp"
    if (( 10#$expect <= 10#$prev_code )); then
      rl_fail "versionCode 单调性: expect($expect) 未相对上一发布 tag $tag 递增（其 versionCode=$prev_code）——请先 bump app/build.gradle.kts 的 versionCode"
      return 1
    fi
    echo "OK   versionCode 单调性: $expect > $tag(versionCode=$prev_code)"
    return 0
  done
  rm -f -- "$tmp"
  rl_warn "当前版本 $cur 之前找不到本定制版 tag——跳过 versionCode 单调性比较（仅警告）"
  return 0
}
