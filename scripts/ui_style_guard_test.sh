#!/usr/bin/env bash
# ui_style_guard.sh 自测：纯离线，在临时目录搭一个最小仓库骨架，不读写真实基线。
#   场景 1  真实仓库：当前基线下守卫通过（rc=0）
#   场景 2  干净骨架（无违规、空基线）→ 通过，--strict 也通过
#   场景 3  非基线文件新增 `fontSize =` / `MaterialTheme.typography` / `Color.White` / `Color(0x` → 各自失败
#   场景 4  token 目录 ui/theme/ 内的同类写法 → 豁免，通过
#   场景 5  基线文件违规数不变 → 通过；上升 → 失败；下降 → 通过并提示下调
#   场景 6  --strict：基线非空 → 失败
#   场景 7  --update-baseline：首次建立写入现状；之后拒绝上升、接受下降并重写
#   场景 8  基线格式错误 / 未知参数 → rc=2
#   场景 9  app/src/main/java/.../ui/ 同样受管；非 ui 目录（如 data/）不受管
#   场景 10 变异验证：去掉任一模式（只剩三种）时，对应的故意违规必须能被区分
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
GUARD=$HERE/ui_style_guard.sh

TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT

PASS=0; FAILED=0
check() { # check <desc> <expr...>
  local desc=$1; shift
  if "$@"; then PASS=$((PASS+1)); echo "ok   - $desc"
  else FAILED=$((FAILED+1)); echo "FAIL - $desc"; fi
}

SN=sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew
APPUI=app/src/main/java/com/saschl/cameragps/ui

# new_repo <name>：建骨架，返回根目录
new_repo() {
  local r=$TMP/$1
  mkdir -p "$r/$SN/ui/theme" "$r/$SN/ui/components/glass" "$r/$SN/ui/page" "$r/$SN/data" "$r/$APPUI" "$r/docs"
  printf 'package x\nval Ok = AlphaType.Body\n' > "$r/$SN/ui/components/glass/Clean.kt"
  printf 'package x\nval Hi = Color.White\nval T = Color(0xFF0B57D0)\nval S = TextStyle(fontSize = 15.sp)\n' > "$r/$SN/ui/theme/Tokens.kt"
  printf '# empty\n' > "$r/docs/ui-style-guard-baseline.txt"
  echo "$r"
}
# guard <root> [args…]：运行守卫，rc 写到全局 RC
guard() {
  local r=$1; shift
  UI_STYLE_GUARD_ROOT=$r "$GUARD" "$@" >"$TMP/out" 2>"$TMP/err"; RC=$?
}
rc_is() { test "$RC" -eq "$1"; }
err_has() { grep -q -- "$1" "$TMP/err"; }

# 场景 1
"$GUARD" >"$TMP/out" 2>"$TMP/err"; RC=$?
check "真实仓库在当前基线下通过" rc_is 0

# 场景 2
R=$(new_repo clean)
guard "$R"; check "干净骨架通过" rc_is 0
guard "$R" --strict; check "干净骨架 --strict 通过（空基线）" rc_is 0

# 场景 3
i=0
for snippet in 'Text("a", fontSize = 15.sp)' 'style = MaterialTheme.typography.bodyLarge' 'color = Color.White' 'color = Color(0x80FFFFFF)' 'Text("a", fontSize= 15.sp)'; do
  i=$((i+1))
  R=$(new_repo bad$i)
  printf 'package x\nfun f() { %s }\n' "$snippet" > "$R/$SN/ui/page/Page.kt"
  guard "$R"; check "新增违规被拒：$snippet" rc_is 1
  check "  报告文件名：$snippet" err_has "ui/page/Page.kt"
done

# 场景 4
R=$(new_repo theme)
printf 'package x\nval More = Color(0x11223344)\nval Style = TextStyle(fontSize = 22.sp)\nval W = Color.White\n' > "$R/$SN/ui/theme/Glass.kt"
guard "$R"; check "token 目录 ui/theme/ 豁免" rc_is 0

# 场景 5
R=$(new_repo base)
printf 'package x\nval a = Color.White\nval b = Color.White\n' > "$R/$SN/ui/page/Legacy.kt"
printf '2 %s\n' "$SN/ui/page/Legacy.kt" > "$R/docs/ui-style-guard-baseline.txt"
guard "$R"; check "基线文件计数不变 → 通过" rc_is 0
printf 'val c = Color(0xFF000000)\n' >> "$R/$SN/ui/page/Legacy.kt"
guard "$R"; check "基线文件计数上升 → 失败" rc_is 1
check "  提示只允许下降" err_has "只允许下降"
printf 'package x\nval a = AlphaTheme.colors.onPhoto\n' > "$R/$SN/ui/page/Legacy.kt"
guard "$R"; check "基线文件已清零 → 通过" rc_is 0
check "  提示从基线移除" err_has "请从基线移除"

# 场景 6
printf 'package x\nval a = Color.White\n' > "$R/$SN/ui/page/Legacy.kt"
guard "$R" --strict; check "--strict 且基线非空 → 失败" rc_is 1
check "  --strict 报告基线非空" err_has "要求基线为空"

# 场景 7
R=$(new_repo upd)
rm -f "$R/docs/ui-style-guard-baseline.txt"
printf 'package x\nval a = Color.White\nval b = Color.White\n' > "$R/$SN/ui/page/Legacy.kt"
guard "$R" --update-baseline; check "首次 --update-baseline 建立基线" rc_is 0
check "  基线写入现状 2 处" grep -qx "2 $SN/ui/page/Legacy.kt" "$R/docs/ui-style-guard-baseline.txt"
printf 'val c = Color.White\n' >> "$R/$SN/ui/page/Legacy.kt"
guard "$R" --update-baseline; check "已有基线时拒绝把上升写入基线" rc_is 1
check "  基线未被改写" grep -qx "2 $SN/ui/page/Legacy.kt" "$R/docs/ui-style-guard-baseline.txt"
printf 'package x\nval a = Color.White\n' > "$R/$SN/ui/page/Legacy.kt"
guard "$R" --update-baseline; check "下降后 --update-baseline 接受" rc_is 0
check "  基线下调为 1" grep -qx "1 $SN/ui/page/Legacy.kt" "$R/docs/ui-style-guard-baseline.txt"

# 场景 8
R=$(new_repo fmt)
printf 'two %s\n' "$SN/ui/page/X.kt" > "$R/docs/ui-style-guard-baseline.txt"
guard "$R"; check "基线计数非数字 → rc=2" rc_is 2
guard "$(new_repo arg)" --bogus; check "未知参数 → rc=2" rc_is 2

# 场景 9
R=$(new_repo app)
printf 'package x\nval a = Color.White\n' > "$R/$APPUI/Screen.kt"
guard "$R"; check "app ui/ 目录同样受管" rc_is 1
R=$(new_repo data)
printf 'package x\nval a = Color.White\n' > "$R/$SN/data/Model.kt"
guard "$R"; check "非 ui 目录不受管" rc_is 0

# 场景 10 变异验证：逐个删掉一种模式生成变异守卫，断言对应违规被漏检，原守卫才拒绝
ALTS=('fontSize[[:space:]]*=' 'MaterialTheme\.typography' 'Color\.White' 'Color\(0x')
SNIPS=('Text("a", fontSize = 1.sp)' 'MaterialTheme.typography.bodyLarge' 'Color.White' 'Color(0x80FFFFFF)')
joined=$(IFS='|'; echo "${ALTS[*]}")
check "测试中的模式表与守卫 PATTERN 一致" grep -qxF "PATTERN='$joined'" "$GUARD"
for i in 0 1 2 3; do
  rest=()
  for j in 0 1 2 3; do [ "$j" -eq "$i" ] || rest+=("${ALTS[$j]}"); done
  mpat=$(IFS='|'; echo "${rest[*]}")
  mutant=$TMP/mutant$i.sh
  LINE="PATTERN='$mpat'" awk '/^PATTERN=/{print ENVIRON["LINE"]; next} {print}' "$GUARD" > "$mutant"; chmod +x "$mutant"
  check "  变异守卫的 PATTERN 已改写" grep -qxF "PATTERN='$mpat'" "$mutant"
  R=$(new_repo mut$i)
  printf 'package x\nfun f() { %s }\n' "${SNIPS[$i]}" > "$R/$SN/ui/page/Page.kt"
  UI_STYLE_GUARD_ROOT=$R "$mutant" >/dev/null 2>&1; mrc=$?
  check "变异：删去 ${ALTS[$i]} 后漏检 ${SNIPS[$i]}" test "$mrc" -eq 0
  guard "$R"; check "  原守卫拒绝 ${SNIPS[$i]}" rc_is 1
  k=$(( (i + 1) % 4 )); R=$(new_repo mutk$i)
  printf 'package x\nfun f() { %s }\n' "${SNIPS[$k]}" > "$R/$SN/ui/page/Page.kt"
  UI_STYLE_GUARD_ROOT=$R "$mutant" >/dev/null 2>&1; mrc=$?
  check "  变异守卫仍能拦截其它模式 ${SNIPS[$k]}（非空转）" test "$mrc" -eq 1
done

echo "ui_style_guard_test: $PASS passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
