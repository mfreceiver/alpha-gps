#!/usr/bin/env bash
# release_lib.sh —— 发版脚本共用纯函数（release_build.sh source；自测脚本直接 source）。
# 全部函数遵循 fail-closed：解析/门禁失败 → stderr 带关键字 + 非零返回。
# 依赖 python3 做注释剥离（真词法状态机）；python3 缺失/执行失败/输入含不支持构造
#   → 显式 FAIL 提示依赖并以非零返回，fail-closed。

rl_fail() { echo "FAIL $1" >&2; }   # 注意：调用方必须紧跟 `return 1`（在 `||` 里不会替调用方退出函数）
rl_warn() { echo "WARN $1" >&2; }

# rl_strip_kotlin_comments_file <文件>: 词法级剔除 Kotlin 注释，文件其余内容（含字符串原文、
#   换行结构）原样输出到 stdout。逐字符状态机，正确处理：
#     - `//` 行注释（保留换行）；
#     - `/* */` 块注释，**支持 Kotlin 合法的嵌套**（按深度计数），块内换行保留以维持行结构；
#     - 普通字符串 "..."：`\"` 等反斜杠转义、`${...}` 字符串模板（模板内出现引号等复杂构造
#       → 不支持 → 整体 fail-closed 拒绝）；
#     - 三引号 raw string """..."""（内容原样保留，其中的 // /* 不当注释）；
#     - 字符字面量 'x' / '\''。
#   输入包含 NUL 字节、块注释/字符串/raw string/字符字面量未闭合、模板未闭合、
#   模板内嵌引号、文件不可读 → stderr FAIL + 非零返回（fail-closed，绝不静默输出残缺结果）。
#   说明：NUL 必须在此层拒绝——bash 变量无法承载 NUL，`read -d ''` 会截断出错误结果。
rl_strip_kotlin_comments_file() {
  command -v python3 >/dev/null 2>&1 \
    || { rl_fail "注释剥离: 缺少依赖 python3（词法扫描工具不可用），fail-closed"; return 1; }
  if python3 - "${1:?}" <<'RL_PY'
import sys
def die(msg):
    sys.stderr.write("FAIL 注释剥离: %s\n" % msg)
    sys.exit(1)
try:
    with open(sys.argv[1], "r", encoding="utf-8", errors="surrogateescape") as f:
        d = f.read()
except OSError as e:
    die("文件不可读: %s" % e)
if "\0" in d:
    die("输入包含 NUL 字节，明确拒绝（bash 变量无法安全承载 NUL），fail-closed")
out = []
i, n = 0, len(d)
while i < n:
    c = d[i]
    if c == "/" and i + 1 < n and d[i + 1] == "/":
        j = d.find("\n", i)
        i = n if j < 0 else j                      # 行注释吞到行尾，保留 \n
    elif c == "/" and i + 1 < n and d[i + 1] == "*":
        depth, i = 1, i + 2
        while i < n and depth:
            if d[i] == "/" and i + 1 < n and d[i + 1] == "*":
                depth += 1; i += 2
            elif d[i] == "*" and i + 1 < n and d[i + 1] == "/":
                depth -= 1; i += 2
            else:
                if d[i] == "\n":
                    out.append("\n")               # 块内换行保留，维持行结构
                i += 1
        if depth:
            die("块注释未闭合")
    elif c == '"':
        if d[i:i + 3] == '"""':
            j = d.find('"""', i + 3)
            if j < 0:
                die("raw string（三引号）未闭合")
            out.append(d[i:j + 3]); i = j + 3
        else:
            j = i + 1; buf = ['"']; closed = False
            while j < n:
                ch = d[j]
                if ch == "\\":
                    if j + 1 >= n:
                        die("字符串未闭合（结尾悬挂反斜杠）")
                    buf.append(d[j:j + 2]); j += 2
                elif ch == '"':
                    buf.append('"'); j += 1; closed = True; break
                elif ch == "$" and j + 1 < n and d[j + 1] == "{":
                    buf.append("${"); j += 2; dep = 1
                    while j < n and dep:
                        c2 = d[j]
                        if c2 == '"':
                            die("不支持: 字符串模板 ${...} 内嵌引号，整体 fail-closed")
                        if c2 == "{":
                            dep += 1
                        elif c2 == "}":
                            dep -= 1
                        if dep:
                            buf.append(c2)
                        j += 1
                    if dep:
                        die("字符串模板 ${...} 未闭合")
                    buf.append("}")
                else:
                    buf.append(ch); j += 1
            if not closed:
                die("字符串未闭合")
            out.append("".join(buf)); i = j
    elif c == "'":
        j = i + 1; buf = ["'"]
        if j < n and d[j] == "\\":
            if j + 1 >= n:
                die("字符字面量未闭合")
            buf.append(d[j:j + 2]); j += 2
        elif j < n:
            buf.append(d[j]); j += 1
        if j >= n or d[j] != "'":
            die("字符字面量未闭合")
        buf.append("'"); out.append("".join(buf)); i = j + 1
    else:
        out.append(c); i += 1
        continue
    continue
sys.stdout.write("".join(out))
RL_PY
  then
    return 0
  else
    rl_fail "注释剥离: 词法扫描失败（rc=$?），fail-closed"
    return 1
  fi
}

# rl_strip_kotlin_comments: stdin → stdout（rl_strip_kotlin_comments_file 的流式包装）。
#   stdin 经临时文件中转（不经 bash 变量，避免 NUL 截断/内容丢失），读取失败有明确报错。
rl_strip_kotlin_comments() {
  local tmp
  tmp=$(mktemp "${TMPDIR:-/tmp}/rlstrip.XXXXXX") \
    || { rl_fail "注释剥离: 无法创建临时文件，fail-closed"; return 1; }
  if ! cat > "$tmp"; then
    rm -f -- "$tmp"
    rl_fail "注释剥离: stdin 读取失败/不可用，fail-closed"
    return 1
  fi
  rl_strip_kotlin_comments_file "$tmp"
  local rc=$?
  rm -f -- "$tmp"
  return "$rc"
}

# rl_check_code <值>: canonical 十进制正整数（^[1-9][0-9]{0,9}$）且 ≤ 2147483647
#   （Android Int 上限；10 位时用等长字符串字典序比较，不进算术，避免 2^31/2^63 回绕）。
rl_check_code() {
  local v=${1:-}
  [[ "$v" =~ ^[1-9][0-9]{0,9}$ ]] || return 1
  [[ ${#v} -ne 10 ]] && return 0
  [[ ! "$v" > 2147483647 ]]
}

# parse_version_code <文件路径|->
#   严格解析 `versionCode = N`（整行；先剔除块/行注释；等号两侧空白容忍；canonical 十进制
#   正整数且 ≤ 2147483647），必须恰好匹配 1 处，否则 fail-closed（0 处或多处都拒绝——
#   防止 pick 错误行或构建文件被意外改出第二处；仅存在于注释中的伪赋值不算）。
#   "-" 从 stdin 读（配合 `git show <tag>:app/build.gradle.kts` 用）；stdin 为终端时拒绝
#   （避免挂起等待交互输入）。
#   stdout 输出解析到的 N。
parse_version_code() {
  local stripped
  if [[ "${1:-}" == "-" ]]; then
    [[ -t 0 ]] && { rl_fail "versionCode 解析: stdin 是终端（会挂起等待输入），fail-closed"; return 1; }
    # stdin 直接流式进词法剥离（不经过 bash 变量，NUL/读取失败在剥离层显式 fail-closed）
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

# parse_application_id <文件路径|->
#   注释感知地解析 `applicationId = "..."`（先剔除块/行注释再匹配
#   ^\s*applicationId\s*=\s*"([^"]+)"\s*$），必须恰好 1 处。
#   stdout 输出解析到的包名（rc=0）；0 处/多处/文件不可读 → rc≠0 + FAIL 关键字
#   （调用方据此区分“身份无法确定”并 fail-closed，绝不静默当上游）。
parse_application_id() {
  local stripped
  if [[ "${1:-}" == "-" ]]; then
    [[ -t 0 ]] && { rl_fail "applicationId 解析: stdin 是终端（会挂起等待输入），fail-closed"; return 1; }
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
  local tag content aid prev_code
  local i   # 防止污染调用方同名变量（j 等其它循环变量目前不存在；新增时同样 local）
  # 版本排序（sort -V）中严格排在 v$cur 之前的 tag；无该 tag 时用 v$cur 自身做分区点同样正确
  while IFS= read -r tag; do
    [[ "$tag" == "v$cur" ]] && break
    [[ -n "$tag" ]] || continue   # 空行（无匹配/空枚举时 printf 产生的空行）不入列
    tags+=("$tag")
  done <<<"$sorted"
  for (( i=${#tags[@]}-1; i>=0; i-- )); do
    tag=${tags[i]}
    content=$(git show "$tag:app/build.gradle.kts" 2>/dev/null) \
      || { rl_fail "versionCode 单调性: tag $tag 的 app/build.gradle.kts 不可读——身份无法确定，fail-closed"; return 1; }
    aid=$(parse_application_id - <<<"$content") \
      || { rl_fail "versionCode 单调性: tag $tag 的 applicationId 身份无法确定（0 处/多处/注释干扰），fail-closed"; return 1; }
    [[ "$aid" == "cn.vectory.alphagps" ]] || continue   # 上游 tag：不同 versionCode 体系，跳过
    prev_code=$(parse_version_code - <<<"$content") || {
      rl_fail "versionCode 单调性: 本定制版 tag $tag 的 app/build.gradle.kts versionCode 解析失败（fail-closed）"
      return 1
    }
    if (( 10#$expect <= 10#$prev_code )); then
      rl_fail "versionCode 单调性: expect($expect) 未相对上一发布 tag $tag 递增（其 versionCode=$prev_code）——请先 bump app/build.gradle.kts 的 versionCode"
      return 1
    fi
    echo "OK   versionCode 单调性: $expect > $tag(versionCode=$prev_code)"
    return 0
  done
  rl_warn "当前版本 $cur 之前找不到本定制版 tag——跳过 versionCode 单调性比较（仅警告）"
  return 0
}
