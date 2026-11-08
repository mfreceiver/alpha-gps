#!/usr/bin/env bash
# release_lib.sh —— 发版脚本共用纯函数（release_build.sh source；自测脚本直接 source）。
# 全部函数遵循 fail-closed：解析/门禁失败 → stderr 带关键字 + 非零返回。

rl_fail() { echo "FAIL $1" >&2; }   # 注意：调用方必须紧跟 `return 1`（在 `||` 里不会替调用方退出函数）
rl_warn() { echo "WARN $1" >&2; }

# parse_version_code <文件路径|->
#   严格解析 `versionCode = N`（整行、等号两侧单空格、十进制正整数），必须恰好匹配 1 处，
#   否则 fail-closed（0 处或多处都拒绝——防止 pick 错误行或构建文件被意外改出第二处）。
#   "-" 从 stdin 读（配合 `git show <tag>:app/build.gradle.kts` 用）。
#   stdout 输出解析到的 N。
parse_version_code() {
  local src
  if [[ "${1:-}" == "-" ]]; then
    src=$(cat) || { rl_fail "versionCode 解析: stdin 读取失败"; return 1; }
  else
    [[ -n "${1:-}" && -f "$1" ]] || { rl_fail "versionCode 解析: 文件不存在: '${1:-}'"; return 1; }
    src=$(<"$1")
  fi
  local -a hits
  mapfile -t hits < <(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)[[:space:]]*$/\1/p' <<<"$src")
  if (( ${#hits[@]} != 1 )); then
    rl_fail "versionCode 解析: 'versionCode = N'（整行）必须恰好匹配 1 处，实际 ${#hits[@]} 处——fail-closed"
    return 1
  fi
  (( hits[0] > 0 )) || { rl_fail "versionCode 解析: 非法值 ${hits[0]}"; return 1; }
  printf '%s\n' "${hits[0]}"
}

# assert_code_monotonic <expect_code> <当前版本 X.Y.Z>
#   单调递增门禁：在版本排序中小于当前版本的所有 tag 里，取“最大且属本定制版”的 tag，
#   其 tag 提交的 app/build.gradle.kts 中 versionCode 为 PREV_CODE，要求
#   expect_code > PREV_CODE。
#
#   为什么按 applicationId 过滤而不是按 `v0.*` 前缀：本仓库继承上游旧 tag（v1.0.x～v1.7.x，
#   versionCode 体系不同，为 172 级别），直接取“小于当前版本的最大 tag”会拿到上游 tag 误比；
#   而仅按 v0.* 前缀过滤在将来定制版版本号超过 1.x（如 v2.0.0）时仍会漏进/漏掉错误的 tag。
#   按“tag 提交的 app/build.gradle.kts 含 applicationId = \"cn.vectory.alphagps\"”过滤，
#   只可能命中本定制版提交，与版本号前缀无关，更稳妥。
#
#   找不到任何本定制版前一 tag（首次发版/仅上游 tag）→ 仅警告（rl_warn），返回 0。
#   命中本定制版 tag 但其 versionCode 解析失败 → fail-closed。
#   使用当前目录的 git 仓库（调用方负责 cwd 在仓库根）。
assert_code_monotonic() {
  local expect=$1 cur=$2
  [[ "$expect" =~ ^[0-9]+$ && "$cur" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || { rl_fail "versionCode 单调性: 参数非法 expect='$expect' cur='$cur'"; return 1; }
  local -a tags=()
  local tag
  # 版本排序（sort -V）中严格排在 v$cur 之前的 tag；无该 tag 时用 v$cur 自身做分区点同样正确
  while IFS= read -r tag; do
    [[ "$tag" == "v$cur" ]] && break
    tags+=("$tag")
  done < <({ git tag --list | grep -Ex 'v[0-9]+\.[0-9]+\.[0-9]+'; echo "v$cur"; } | sort -Vu)
  local content prev_code
  for (( i=${#tags[@]}-1; i>=0; i-- )); do
    tag=${tags[i]}
    content=$(git show "$tag:app/build.gradle.kts" 2>/dev/null) || continue   # 不可读的 tag 跳过
    grep -q 'applicationId = "cn.vectory.alphagps"' <<<"$content" || continue  # 上游 tag：不同 versionCode 体系，跳过
    prev_code=$(parse_version_code - <<<"$content") || {
      rl_fail "versionCode 单调性: 本定制版 tag $tag 的 app/build.gradle.kts versionCode 解析失败（fail-closed）"
      return 1
    }
    if (( expect <= prev_code )); then
      rl_fail "versionCode 单调性: expect($expect) 未相对上一发布 tag $tag 递增（其 versionCode=$prev_code）——请先 bump app/build.gradle.kts 的 versionCode"
      return 1
    fi
    echo "OK   versionCode 单调性: $expect > $tag(versionCode=$prev_code)"
    return 0
  done
  rl_warn "当前版本 $cur 之前找不到本定制版 tag——跳过 versionCode 单调性比较（仅警告）"
  return 0
}
