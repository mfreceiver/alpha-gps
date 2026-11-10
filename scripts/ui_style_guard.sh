#!/usr/bin/env bash
# UI 样式守卫（0.3.0 B0）：UI 代码不得绕过主题 token 硬编码字号/颜色。
#
# 扫描范围（*.kt）：
#   sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/
#   app/src/main/java/com/saschl/cameragps/ui/
# 豁免：token 文件目录 sharednew/.../ui/theme/（Type.kt / Color.kt / Glass.kt …，唯一允许写色值与字号的地方）。
# 违规模式（按行计数）：`fontSize =`、`MaterialTheme.typography`、`Color.White`、`Color(0x`。
#
# 基线：docs/ui-style-guard-baseline.txt，每行 "<违规行数> <相对路径>"（# 开头为注释）。
#   - 不在基线里的文件：必须 0 违规；
#   - 基线里的文件：违规数只能 ≤ 基线值（迁移后应同步下调/删除该行）；
#   - 基线行对应文件已 0 违规或已删除：提示可清理（不报错）。
# 选项：
#   --strict           额外要求基线为空（最后一批迁移完成后在 CI/验证命令中使用）
#   --update-baseline  按当前扫描结果重写基线（仅限迁移后下调；脚本拒绝使任何文件计数上升）
#   --list             打印当前所有违规行
# 环境变量：UI_STYLE_GUARD_ROOT（仓库根，默认脚本上级目录；自测用）、
#           UI_STYLE_GUARD_BASELINE（基线路径，默认 $ROOT/docs/ui-style-guard-baseline.txt）。
# 退出码：0 通过；1 违规；2 用法错误。
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
ROOT=${UI_STYLE_GUARD_ROOT:-$(cd "$HERE/.." && pwd)}
BASELINE=${UI_STYLE_GUARD_BASELINE:-$ROOT/docs/ui-style-guard-baseline.txt}

SCAN_DIRS=(
  "sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui"
  "app/src/main/java/com/saschl/cameragps/ui"
)
EXEMPT_PREFIX="sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/theme/"
PATTERN='fontSize[[:space:]]*=|MaterialTheme\.typography|Color\.White|Color\(0x'

STRICT=0; UPDATE=0; LIST=0
for arg in "$@"; do
  case $arg in
    --strict) STRICT=1 ;;
    --update-baseline) UPDATE=1 ;;
    --list) LIST=1 ;;
    -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
    *) echo "ui_style_guard: 未知参数 $arg" >&2; exit 2 ;;
  esac
done

# 当前违规：输出 "<count> <path>"，按路径排序。
scan() {
  local dir
  for dir in "${SCAN_DIRS[@]}"; do
    [ -d "$ROOT/$dir" ] || continue
    (cd "$ROOT" && grep -rnE --include='*.kt' "$PATTERN" "$dir" 2>/dev/null)
  done | while IFS= read -r line; do
    local path=${line%%:*}
    case $path in "$EXEMPT_PREFIX"*) continue ;; esac
    printf '%s\n' "$line"
  done
}

HITS=$(scan)
if [ "$LIST" -eq 1 ] && [ -n "$HITS" ]; then printf '%s\n' "$HITS"; fi
CURRENT=$(printf '%s\n' "$HITS" | sed -n 's/^\([^:]*\):.*/\1/p' | sort | uniq -c | awk '{print $1" "$2}')

declare -A BASE=()
if [ -f "$BASELINE" ]; then
  while read -r count path rest; do
    case $count in ''|'#'*) continue ;; esac
    if ! [[ $count =~ ^[0-9]+$ ]] || [ -z "$path" ] || [ -n "${rest:-}" ]; then
      echo "ui_style_guard: 基线格式错误：'$count $path ${rest:-}'" >&2; exit 2
    fi
    BASE[$path]=$count
  done < "$BASELINE"
fi

declare -A CUR=()
while read -r count path; do
  [ -n "${count:-}" ] || continue
  CUR[$path]=$count
done <<< "$CURRENT"

FAIL=0
BOOTSTRAP=0
if [ "$UPDATE" -eq 1 ] && [ ! -f "$BASELINE" ]; then BOOTSTRAP=1; fi
for path in "${!CUR[@]}"; do
  [ "$BOOTSTRAP" -eq 1 ] && continue
  n=${CUR[$path]}
  if [ -z "${BASE[$path]+x}" ]; then
    echo "FAIL ui-style: $path 新增 $n 处硬编码样式（不在基线中，必须为 0）" >&2
    FAIL=1
  elif [ "$n" -gt "${BASE[$path]}" ]; then
    echo "FAIL ui-style: $path 违规 $n > 基线 ${BASE[$path]}（只允许下降）" >&2
    FAIL=1
  elif [ "$n" -lt "${BASE[$path]}" ]; then
    echo "note ui-style: $path 违规 $n < 基线 ${BASE[$path]}，请下调基线" >&2
  fi
done
for path in "${!BASE[@]}"; do
  if [ -z "${CUR[$path]+x}" ]; then
    echo "note ui-style: $path 已无违规（或已删除），请从基线移除" >&2
  fi
done

if [ "$UPDATE" -eq 1 ]; then
  # 首次建立基线（基线文件不存在）时允许把现状写入；之后只允许下降。
  if [ "$FAIL" -ne 0 ]; then
    echo "ui_style_guard: 存在新增/上升的违规，拒绝更新基线" >&2; exit 1
  fi
  {
    echo "# ui_style_guard.sh 基线：<违规行数> <路径>。只允许下降；迁移完一个文件就删掉它的行。"
    echo "# 最后一批迁移后本文件应只剩注释，并以 --strict 运行守卫。"
    [ -n "$CURRENT" ] && printf '%s\n' "$CURRENT" | sort -k2
  } > "$BASELINE"
  echo "ui_style_guard: 基线已更新（$(printf '%s\n' "$CURRENT" | grep -c . ) 个文件）"
  exit 0
fi

if [ "$STRICT" -eq 1 ] && [ "${#BASE[@]}" -gt 0 ] && [ "$UPDATE" -eq 0 ]; then
  echo "FAIL ui-style: --strict 要求基线为空（仍有 ${#BASE[@]} 个文件）" >&2
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then exit 1; fi
total=0; for path in "${!CUR[@]}"; do total=$((total + CUR[$path])); done
echo "ok ui-style: ${#CUR[@]} 个基线文件共 $total 处待迁移，无新增违规"
exit 0
