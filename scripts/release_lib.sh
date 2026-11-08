#!/usr/bin/env bash
# release_lib.sh —— 发版脚本共用纯函数（release_build.sh source；自测脚本直接 source）。
# 全部函数遵循 fail-closed：解析/门禁失败 → stderr 带关键字 + 非零返回。
# 依赖 perl 做注释剥离（本机标准配置）；perl 缺失时剥离输出为空 → 解析得 0 处 → fail-closed。

rl_fail() { echo "FAIL $1" >&2; }   # 注意：调用方必须紧跟 `return 1`（在 `||` 里不会替调用方退出函数）
rl_warn() { echo "WARN $1" >&2; }

# rl_strip_kotlin_comments: stdin → stdout，剔除 /* ... */ 块注释与 // 行注释后再做模式匹配。
#   已知取舍：字符串字面量内的 "//"（如 URL）也会被截断——只影响该行行尾，不会制造/消灭
#   整行的 applicationId/versionCode 赋值；字符串内的 "/*" 会吞掉后续行——最坏效果是把
#   真实赋值藏起来 → 计数为 0 → fail-closed，方向安全。
rl_strip_kotlin_comments() {
  perl -0pe 's{/\*.*?\*/}{}gs; s{//[^\n]*}{}g' 2>/dev/null || true
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
  local src
  if [[ "${1:-}" == "-" ]]; then
    [[ -t 0 ]] && { rl_fail "versionCode 解析: stdin 是终端（会挂起等待输入），fail-closed"; return 1; }
    IFS= read -r -d '' src || true   # fd0 关闭/EOF 立即返回，不再经 cat
  else
    [[ -n "${1:-}" && -f "$1" ]] || { rl_fail "versionCode 解析: 文件不存在: '${1:-}'"; return 1; }
    src=$(<"$1")
  fi
  local -a hits
  mapfile -t hits < <(rl_strip_kotlin_comments <<<"$src" \
    | sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p')
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
  local src
  if [[ "${1:-}" == "-" ]]; then
    [[ -t 0 ]] && { rl_fail "applicationId 解析: stdin 是终端（会挂起等待输入），fail-closed"; return 1; }
    IFS= read -r -d '' src || true
  else
    [[ -n "${1:-}" && -f "$1" ]] || { rl_fail "applicationId 解析: 文件不存在: '${1:-}'"; return 1; }
    src=$(<"$1")
  fi
  local -a ids
  mapfile -t ids < <(rl_strip_kotlin_comments <<<"$src" \
    | sed -nE 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*$/\1/p')
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
  # 先把枚举结果收进变量并逐级检查退出码：git/sort 任一失败 → fail-closed，
  # 不用 process substitution（会吞掉管道里非末段命令的失败）。
  local all_tags sorted
  all_tags=$(git tag --list 2>/dev/null) || { rl_fail "versionCode 单调性: git tag 枚举失败（不在 git 仓库/git 异常），fail-closed"; return 1; }
  sorted=$( { printf '%s\n' "$all_tags" | grep -Ex 'v[0-9]+\.[0-9]+\.[0-9]+' || true; printf 'v%s\n' "$cur"; } | sort -Vu ) \
    || { rl_fail "versionCode 单调性: tag 排序失败（sort -Vu），fail-closed"; return 1; }
  local -a tags=()
  local tag content aid prev_code
  local i   # 防止污染调用方同名变量（j 等其它循环变量目前不存在；新增时同样 local）
  # 版本排序（sort -V）中严格排在 v$cur 之前的 tag；无该 tag 时用 v$cur 自身做分区点同样正确
  while IFS= read -r tag; do
    [[ "$tag" == "v$cur" ]] && break
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
