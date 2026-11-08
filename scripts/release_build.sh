#!/usr/bin/env bash
# alpha-gps 发版脚本（精简移植自 dshdroid/scripts/release_build.sh，保留 fail-closed 精神）：
#   - 版本注入：app/build.gradle.kts 的 versionName 硬编码（带 v 前缀），构建前 sed 改写、
#     构建后（含失败）自动还原；versionCode 不做公式镜像，一律从产物 manifest 读取
#   - 产物 app/build/outputs/apk/foss/release/app-foss-release.apk（签名可用时）归档为
#     release/alpha-gps-<版本>.apk；release/ 只留最新版，SHA256SUMS 每次全量重生成
#   - 断言：构建后必须调用 scripts/release_assert_apk.sh（签名者/证书钉定/包名/版本/权限）
#   - 发布（--publish / --publish-only）：push 当前分支+tag 到 Primary forge remote "primary"
#     （https://forge.example.com:443/owner/alphagps）→ Primary forge API 建 release（按 tag
#     查询复用/404 新建）→ 上传本次 APK 单文件资产 → 资产对账
#   - 硬红线：release 资产只上传本次 APK；任何 *.jks/*.keystore/*.p12/*.pass/*.key
#     绝不上传；口令只经 env/curl 配置流传递，绝不进 argv/日志
# 用法: scripts/release_build.sh <X.Y.Z> [--allow-dirty|--publish|--publish-only]
#   --allow-dirty:  仅构建验证（跳过归档/tag/发布；不要求干净树）
#   --publish:      构建+归档+tag 后立即对外发布（要求干净树）
#   --publish-only: 不构建：校验 HEAD 对应 tag 与 release/ 归档完整后仅发布（幂等重试）
set -euo pipefail

fail() { echo "FAIL $1" >&2; exit 1; }
log()  { echo ">>> $1"; }
warn() { echo "WARN $1" >&2; }
pass() { echo "OK   $1"; }

SCRIPT_REAL=$(readlink -f -- "${BASH_SOURCE[0]}")
REPO_ROOT=$(cd "$(dirname "$SCRIPT_REAL")/.." && pwd)
cd "$REPO_ROOT"

RELEASE_DIR="$REPO_ROOT/release"
OUT_APK_DIR="$REPO_ROOT/app/build/outputs/apk/foss/release"
BUILD_GRADLE="$REPO_ROOT/app/build.gradle.kts"
GRADLE_PROPS=$HOME/.gradle/gradle.properties
SIGNING_KEYSTORE_PATH=$HOME/.config/dshdroid/signing/dshdroid.jks
SIGNING_KEY_ALIAS=dshdroid
PINNED_CERT_SHA256="18ff24c56bc52533c75ec709ea53497aa4323dac13c511979ac0a5fd7f9d97d7"
# 本仓库 versionName 硬编码带 v 前缀（如 "v1.7.2"），产物内嵌 versionName = v$VERSION_NAME
PRIMARY_HOST=forge.example.com:443
PRIMARY_REPO=owner/alphagps
PRIMARY_REMOTE=primary

# ─── 参数解析 ────────────────────────────────────────────────────────────
ALLOW_DIRTY=0; PUBLISH=0; PUBLISH_ONLY=0; VERSION_NAME=""
for arg in "$@"; do
  case "$arg" in
    --allow-dirty)  ALLOW_DIRTY=1 ;;
    --publish)      PUBLISH=1 ;;
    --publish-only) PUBLISH_ONLY=1 ;;
    -h|--help)      sed -n '2,17p' "$SCRIPT_REAL"; exit 0 ;;
    *)
      if [[ -z "$VERSION_NAME" ]]; then VERSION_NAME=$arg
      else fail "无法识别的参数: $arg（用法: $0 <X.Y.Z> [--allow-dirty|--publish|--publish-only]）"; fi ;;
  esac
done
[[ -n "$VERSION_NAME" ]] || { echo "用法: $0 <X.Y.Z> [--allow-dirty|--publish|--publish-only]" >&2; exit 1; }
(( PUBLISH + PUBLISH_ONLY <= 1 )) || fail "--publish 与 --publish-only 互斥"
(( ALLOW_DIRTY + PUBLISH + PUBLISH_ONLY <= 1 )) || fail "--allow-dirty 仅限构建验证，不能与发布模式组合"
[[ "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || fail "版本号必须是 X.Y.Z 语义化格式（如 1.7.3），当前: '${VERSION_NAME}'"

EMBED_NAME="v$VERSION_NAME"          # 产物 manifest 期望 versionName（仓库惯例带 v）
TAG_NAME="v$VERSION_NAME"
APK_NAME="alpha-gps-$VERSION_NAME.apk"

# ─── 环境兜底 ────────────────────────────────────────────────────────────
DSH_JAVA_HOME=/home/user/jdk/jdk-21.0.12+8
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME:-}/bin/java" ]] && [[ -x "$DSH_JAVA_HOME/bin/java" ]]; then
  export JAVA_HOME=$DSH_JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export GIT_TERMINAL_PROMPT=0 GIT_ASKPASS=true

# ─── aapt 发现（local.properties → ANDROID_HOME → ~/Android/Sdk）─────────
find_aapt() {
  local sdk_dir="" bt_dir=""
  [[ -f "$REPO_ROOT/local.properties" ]] && sdk_dir=$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$REPO_ROOT/local.properties" | head -1)
  [[ -z "$sdk_dir" && -n "${ANDROID_HOME:-}" ]] && sdk_dir=$ANDROID_HOME
  [[ -z "$sdk_dir" ]] && sdk_dir=$HOME/Android/Sdk
  bt_dir=$(ls -d "$sdk_dir"/build-tools/* 2>/dev/null | sort -V | tail -1)
  [[ -n "$bt_dir" && -x "$bt_dir/aapt" ]] || fail "未找到 aapt（SDK 发现链: local.properties → ANDROID_HOME → ~/Android/Sdk）"
  AAPT=$bt_dir/aapt
}
apk_version_name() { sed -nE "s/^package: name='[^']+'.*versionName='([^']+)'.*$/\1/p" <<< "$(head -1 < <("$AAPT" dump badging "$1" 2>/dev/null))"; }
apk_version_code() { sed -nE "s/^package:.*versionCode='([0-9]+)'.*$/\1/p" <<< "$(head -1 < <("$AAPT" dump badging "$1" 2>/dev/null))"; }

# ─── 对外发布（--publish / --publish-only 公共出口）──────────────────────
publish_release() {
  # 上传白名单（硬红线）：只允许本次版本 APK；逐条自检排除密钥/证书形态
  local -a UPLOAD_ASSETS=("$RELEASE_DIR/$APK_NAME")
  local u
  for u in "${UPLOAD_ASSETS[@]}"; do
    [[ "$(basename "$u")" == "$APK_NAME" ]] || fail "上传白名单异常: $u"
    case "$(basename "$u")" in
      *.p12|*.pass|*.crt|*.key|*.jks|*.keystore|*.pem)
        fail "上传集合包含证书/私钥材料（硬红线，绝不上传）: $u" ;;
    esac
  done
  log "上传白名单: $APK_NAME（仅 APK；密钥/证书/口令文件绝不上传）"

  # 凭据预取（口令只经 stdin/配置流，绝不进 argv/环境）
  log "Primary forge 凭据预取"
  local CRED GU GP
  CRED="$(printf 'protocol=https\nhost=%s\n\n' "$PRIMARY_HOST" | timeout 15 git credential fill)" \
    || fail "Primary forge 凭据缺失/超时（git credential fill 15s 内未非交互返回 $PRIMARY_HOST 凭据）"
  GU="$(printf '%s' "$CRED" | sed -n 's/^username=//p')"
  GP="$(printf '%s' "$CRED" | sed -n 's/^password=//p')"
  [[ -n "$GU" && -n "$GP" ]] || fail 'Primary forge 凭据不完整（username/password 缺失）'
  local API="https://$PRIMARY_HOST/api/v1/repos/$PRIMARY_REPO"
  curl_config() {
    printf '%s\0%s\0' "$GU" "$GP" | python3 -c '
import json, sys
user, password, _ = sys.stdin.buffer.read().decode().split("\0", 2)
print("user = " + json.dumps(user + ":" + password))'
  }
  parse_assets() {
    python3 - "$1" <<'PY'
import json, sys
try:
    rel = json.load(open(sys.argv[1], encoding="utf-8"))
    assets = rel.get("assets", [])
    if not isinstance(assets, list):
        raise ValueError(f"assets 字段非列表: {type(assets).__name__}")
    for a in assets:
        print("\t".join([
            str(a.get("name", "")), str(a.get("size", -1)),
            str(a.get("sha256") or "-"), str(a.get("browser_download_url") or "")]))
except Exception as e:
    sys.stderr.write(f"资产表解析失败: {e}\n")
    sys.exit(1)
PY
  }
  local WORK_DIR; WORK_DIR=$(mktemp -d)
  # 注：不用 trap RETURN——它会在本函数内每个嵌套函数（curl_config 等）返回时误触发；
  # 正常路径结尾显式清理，失败路径泄漏一个 /tmp 临时目录（可接受，fail-closed 优先）。

  # 远端 tag 校验（幂等重试基础）：已存在且同 commit → 跳过 tag push；不一致 → 拒
  log "远端 tag 校验 + push 当前分支"
  local BRANCH REMOTE_OUT REMOTE_SHA LOCAL_SHA SKIP_TAG_PUSH=0
  BRANCH=$(git rev-parse --abbrev-ref HEAD)
  if ! REMOTE_OUT="$(timeout 30 git ls-remote --tags "$PRIMARY_REMOTE" "refs/tags/$TAG_NAME" "refs/tags/$TAG_NAME^{}" 2>&1)"; then
    fail "远端 tag 查询失败（$PRIMARY_REMOTE 不可达/网络错误，fail-closed）"
  fi
  REMOTE_SHA="$(awk -v t="refs/tags/$TAG_NAME" -v p="refs/tags/$TAG_NAME^{}" '
    $2 == p { peeled = $1 }
    $2 == t { plain = $1 }
    END { if (peeled != "") print peeled; else if (plain != "") print plain }' <<<"$REMOTE_OUT")"
  if [[ -n "$REMOTE_SHA" ]]; then
    LOCAL_SHA="$(git rev-parse "$TAG_NAME^{commit}")"
    if [[ "$PUBLISH_ONLY" -eq 1 && "$REMOTE_SHA" == "$LOCAL_SHA" ]]; then
      log "远端 tag $TAG_NAME 已存在且与本地同 commit → 跳过 tag push（幂等重试）"
      SKIP_TAG_PUSH=1
    elif [[ "$PUBLISH_ONLY" -eq 1 ]]; then
      fail "远端 tag $TAG_NAME 指向 $REMOTE_SHA ≠ 本地 $LOCAL_SHA（禁止移动已发布 tag）"
    else
      fail "远端已存在 tag $TAG_NAME（构建型模式禁止同版本重出）"
    fi
  fi
  timeout 120 git push "$PRIMARY_REMOTE" "$BRANCH" || fail "git push $PRIMARY_REMOTE $BRANCH 失败"
  [[ "$SKIP_TAG_PUSH" -eq 1 ]] || { timeout 120 git push "$PRIMARY_REMOTE" "$TAG_NAME" || fail "git push $PRIMARY_REMOTE $TAG_NAME 失败"; }

  # Primary forge release：publish-only 先按 tag 查询——200 复用 / 404 新建 / 其他 fail
  log "Primary forge release"
  local REL_ID="" GETF RC HTTP
  if [[ "$PUBLISH_ONLY" -eq 1 ]]; then
    GETF="$WORK_DIR/rel_get.json"; RC=0
    HTTP="$(curl_config | curl -K - -sS -m 30 -o "$GETF" -w '%{http_code}' "$API/releases/tags/$TAG_NAME")" || RC=$?
    if [[ "$RC" -ne 0 ]]; then fail "Primary forge release 查询失败（curl rc=$RC, HTTP=${HTTP:-n/a}）"; fi
    if [[ "$HTTP" == "200" ]]; then
      REL_ID="$(python3 -c 'import json; print(json.load(open("'"$GETF"'")).get("id",""))' 2>/dev/null || true)"
      [[ -n "$REL_ID" ]] || fail "Primary forge release 查询响应无 id"
      log "Primary forge 已有 tag $TAG_NAME 的 release（id=$REL_ID）→ 复用并做资产对账"
    elif [[ "$HTTP" != "404" ]]; then
      fail "Primary forge release 查询失败: HTTP $HTTP"
    else
      log "Primary forge 无 tag $TAG_NAME 的 release → 新建"
    fi
  fi
  if [[ -z "$REL_ID" ]]; then
    local BODYF="$WORK_DIR/rel_body.json" RESPF="$WORK_DIR/rel_resp.json"
    python3 -c "
import json
print(json.dumps({'tag_name': '$TAG_NAME', 'name': '$TAG_NAME', 'body': 'alpha-gps $TAG_NAME'}))
" > "$BODYF"
    RC=0
    HTTP="$(curl_config | curl -K - -sS -m 30 -o "$RESPF" -w '%{http_code}' \
      -X POST -H 'Content-Type: application/json' -d @"$BODYF" "$API/releases")" || RC=$?
    if [[ "$RC" -ne 0 ]]; then head -c 300 "$RESPF" >&2 2>/dev/null || true; fail "Primary forge release 创建失败（curl rc=$RC, HTTP=${HTTP:-n/a}）"; fi
    [[ "$HTTP" =~ ^2 ]] || { head -c 300 "$RESPF" >&2 2>/dev/null || true; fail "Primary forge release 创建失败: HTTP $HTTP"; }
    REL_ID="$(python3 -c 'import json; print(json.load(open("'"$RESPF"'")).get("id",""))' 2>/dev/null || true)"
    [[ -n "$REL_ID" ]] || fail "Primary forge release 响应无 id（解析失败）"
  fi

  # 资产上传（幂等）：先查现有资产表；缺失 → 上传确认 2xx；同名 sha 一致 → 跳过
  local VF="$WORK_DIR/rel_assets.json" ASSET_LIST name lsha HTTP_CODE
  RC=0
  HTTP="$(curl_config | curl -K - -sS -m 30 -o "$VF" -w '%{http_code}' "$API/releases/$REL_ID")" || RC=$?
  [[ "$RC" -eq 0 && "$HTTP" == "200" ]] || fail "Primary forge release 资产表查询失败: HTTP ${HTTP:-n/a}"
  ASSET_LIST="$(parse_assets "$VF")" || fail "Primary forge release 资产表解析失败（fail-closed）"
  lsha="$(awk -v f="release/$APK_NAME" '$2==f{print $1}' "$RELEASE_DIR/SHA256SUMS")"
  [[ -n "$lsha" ]] || fail "资产对账：release/$APK_NAME 无本地 SHA256SUMS 条目（fail-closed）"
  name="$APK_NAME"
  rsha="$(awk -F'\t' -v n="$name" '$1==n{print $3}' <<<"$ASSET_LIST")"
  if [[ -n "$rsha" ]]; then
    [[ "$rsha" != "-" ]] || fail "资产对账：$name 远端 sha256 缺失（fail-closed）"
    [[ "$rsha" == "$lsha" ]] || fail "远端资产同名但内容不一致: $name（远端 $rsha ≠ 本地 $lsha）"
    pass "资产已存在且 sha256 一致: $name → 跳过上传"
  else
    RC=0
    HTTP_CODE="$(curl_config | curl -K - -sS -m 300 -o "$WORK_DIR/asset_resp" -w '%{http_code}' \
      -X POST "$API/releases/$REL_ID/assets?name=$name" \
      -F "attachment=@$RELEASE_DIR/$APK_NAME")" || RC=$?
    [[ "$RC" -eq 0 && "$HTTP_CODE" =~ ^2 ]] \
      || { head -c 300 "$WORK_DIR/asset_resp" >&2 2>/dev/null || true; fail "资产上传失败: $name HTTP ${HTTP_CODE:-n/a}"; }
    pass "资产上传 OK: $name（HTTP $HTTP_CODE）"
  fi

  # 发布后对账：重新枚举，同名资产 sha256 必须与本地 SHA256SUMS 一致
  RC=0
  HTTP="$(curl_config | curl -K - -sS -m 30 -o "$VF" -w '%{http_code}' "$API/releases/$REL_ID")" || RC=$?
  [[ "$RC" -eq 0 && "$HTTP" == "200" ]] || fail "发布后资产核验：release 查询失败: HTTP ${HTTP:-n/a}"
  ASSET_FINAL="$(parse_assets "$VF")" || fail "发布后资产核验：资产表解析失败（fail-closed）"
  rsha="$(awk -F'\t' -v n="$name" '$1==n{print $3}' <<<"$ASSET_FINAL")"
  [[ -n "$rsha" && "$rsha" != "-" ]] || fail "发布后资产核验：$name 不在远端资产表中（fail-closed）"
  [[ "$rsha" == "$lsha" ]] || fail "发布后资产核验：$name 哈希不一致（远端 $rsha ≠ 本地 $lsha）"
  pass "资产对账通过: $name sha256 一致"
  rm -rf -- "$WORK_DIR"
  log "Primary forge release: https://$PRIMARY_HOST/$PRIMARY_REPO/releases/tag/$TAG_NAME"
}

# ─── 签名预检（fail-closed）──────────────────────────────────────────────
log "签名预检"
[[ -f "$GRADLE_PROPS" ]] || fail "未找到 $GRADLE_PROPS（DSH_* 签名凭据应位于其中）"
prop_of() { sed -nE "s/^$1=(.*)$/\1/p" "$GRADLE_PROPS" | head -1; }
DSH_KEYSTORE_PASSWORD=$(prop_of DSH_KEYSTORE_PASSWORD)
DSH_KEY_PASSWORD=$(prop_of DSH_KEYSTORE_PASSWORD)
[[ -n "$(prop_of DSH_KEY_PASSWORD)" ]] && DSH_KEY_PASSWORD=$(prop_of DSH_KEY_PASSWORD)
[[ -n "$DSH_KEYSTORE_PASSWORD" ]] || fail "$GRADLE_PROPS 缺少 DSH_KEYSTORE_PASSWORD"
[[ -f "$SIGNING_KEYSTORE_PATH" ]] || fail "keystore 不存在或不可读: $SIGNING_KEYSTORE_PATH"

KEYTOOL_OUT=$(mktemp)
export KS_PASS=$DSH_KEYSTORE_PASSWORD   # 口令经 env 传递，绝不进 argv
if ! keytool -list -v -keystore "$SIGNING_KEYSTORE_PATH" -storepass:env KS_PASS -alias "$SIGNING_KEY_ALIAS" \
  > "$KEYTOOL_OUT" 2>&1; then
  rm -f "$KEYTOOL_OUT"
  fail "keytool 校验失败：keystore 不可读 / 口令错误 / alias '$SIGNING_KEY_ALIAS' 不存在"
fi
KEYSTORE_SHA=$(sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*([0-9A-Fa-f:]+)[[:space:]]*$/\1/p' "$KEYTOOL_OUT" \
  | head -1 | tr -d ':' | tr 'A-F' 'a-f')
rm -f "$KEYTOOL_OUT"
[[ -n "$KEYSTORE_SHA" ]] || fail "无法从 keytool 输出提取证书 SHA256"
[[ "$KEYSTORE_SHA" == "$PINNED_CERT_SHA256" ]] \
  || fail "keystore 证书 SHA256 与钉定值不一致（实际 $KEYSTORE_SHA，期望 $PINNED_CERT_SHA256）——签名身份已变，必须人工确认后更新钉定值"
pass "keystore 可读、alias=$SIGNING_KEY_ALIAS、证书 SHA256 与钉定值一致"

# ─── git 门禁 ────────────────────────────────────────────────────────────
log "git 门禁"
BRANCH=$(git rev-parse --abbrev-ref HEAD)
GIT_STATUS=$(git status --porcelain)
if [[ -n "$GIT_STATUS" ]] && (( PUBLISH || PUBLISH_ONLY )); then
  echo "$GIT_STATUS" | head -5 >&2
  fail "工作树不干净：发布（--publish/--publish-only）不允许脏树（--allow-dirty 仅限构建验证）"
fi
if [[ -n "$GIT_STATUS" ]] && (( ! ALLOW_DIRTY )); then
  echo "$GIT_STATUS" | head -5 >&2
  fail "工作树不干净。--allow-dirty 仅限构建验证（不归档/不打 tag）"
fi
if [[ -n "$GIT_STATUS" ]] && (( ALLOW_DIRTY )); then
  warn "脏树 --allow-dirty 模式：仅构建验证，将跳过归档与 tag"
fi
pass "分支 $BRANCH${GIT_STATUS:+（脏树，--allow-dirty）}"

# ─── 版本唯一性（fail-closed；--publish-only 不出新版本，跳过）──────────
if (( ! PUBLISH_ONLY )); then
  log "版本唯一性检查: $VERSION_NAME"
  git rev-parse -q --verify "refs/tags/$TAG_NAME" > /dev/null \
    && fail "本地 tag $TAG_NAME 已存在（重发已归档版本用 --publish-only）"
  REMOTE_TAGS=$(timeout 30 git ls-remote --tags "$PRIMARY_REMOTE" "refs/tags/$TAG_NAME") \
    || fail "查询远端 tag 失败（网络/权限），fail-closed 拒绝发版"
  [[ -z "$REMOTE_TAGS" ]] || fail "远端已存在 tag $TAG_NAME"
  [[ -e "$RELEASE_DIR/$APK_NAME" ]] && fail "release/ 已有该版本产物: $APK_NAME"
  pass "tag $TAG_NAME 本地/远端均不存在，release/ 无该版本产物"
fi

# ─── --publish-only：不构建，校验 HEAD 对应 tag 与 release/ 归档完整后仅发布 ──
if (( PUBLISH_ONLY )); then
  log "--publish-only：校验归档完整性（不构建）"
  git rev-parse -q --verify "refs/tags/$TAG_NAME" > /dev/null \
    || fail "--publish-only：本地 tag $TAG_NAME 不存在。发版产物由 release_build.sh 归档时创建，勿手工造 tag 绕门禁"
  [[ "$(git rev-parse "$TAG_NAME^{commit}")" == "$(git rev-parse HEAD)" ]] \
    || fail "--publish-only：tag $TAG_NAME 不在 HEAD 上（HEAD 版本与归档不符；在干净树重跑 release_build.sh 出该版本）"
  [[ -f "$RELEASE_DIR/$APK_NAME" ]] || fail "--publish-only：归档缺失 release/$APK_NAME"
  [[ -f "$RELEASE_DIR/SHA256SUMS" ]] || fail "--publish-only：release/SHA256SUMS 缺失"
  grep -qxF "release/$APK_NAME" <(awk '{print $2}' "$RELEASE_DIR/SHA256SUMS") \
    || fail "--publish-only：SHA256SUMS 缺当前版本条目: release/$APK_NAME"
  sha256sum -c "$RELEASE_DIR/SHA256SUMS" > /dev/null \
    || fail "--publish-only：SHA256SUMS 校验失败（归档损坏）"
  find_aapt
  EMBEDDED_NAME=$(apk_version_name "$RELEASE_DIR/$APK_NAME")
  VERSION_CODE=$(apk_version_code "$RELEASE_DIR/$APK_NAME")
  [[ -n "$EMBEDDED_NAME" && -n "$VERSION_CODE" ]] \
    || fail "--publish-only：归档 APK 内嵌版本解析失败（fail-closed）: $APK_NAME"
  [[ "$EMBEDDED_NAME" == "$EMBED_NAME" ]] \
    || fail "--publish-only：归档 APK 内嵌 versionName($EMBEDDED_NAME)与请求版本($EMBED_NAME)不一致"
  "$REPO_ROOT/scripts/release_assert_apk.sh" "$RELEASE_DIR/$APK_NAME" "$EMBED_NAME" "$VERSION_CODE" "$PINNED_CERT_SHA256" \
    || fail "--publish-only：归档 APK 断言失败"
  pass "归档完整（APK+SHA256SUMS+断言）"
  publish_release
  echo
  echo "--publish-only 完成: $VERSION_NAME (versionCode $VERSION_CODE)  tag: $TAG_NAME"
  exit 0
fi

# ─── 版本注入（sed 改写 + 构建后还原）────────────────────────────────────
# app/build.gradle.kts 硬编码 versionName = "vX.Y.Z"（无 env 注入），构建前改写、
# 无论如何（成功/失败/中断经 EXIT trap）还原备份。备份/还原的 fail-closed 语义
# （备份完整校验后才激活恢复责任；还原失败保留备份并终止）在
# scripts/release_version_inject.sh 中实现并由 scripts/release_version_inject_test.sh
# 做故障注入自测。
log "版本注入: versionName → $EMBED_NAME（构建后还原）"
VERSION_INJECT="$REPO_ROOT/scripts/release_version_inject.sh"
BUILD_GRADLE="$REPO_ROOT/app/build.gradle.kts"
GRADLE_BAK="$BUILD_GRADLE.release-bak"
# 还原失败必须终止后续归档/tag/发布：绝不带着改写过的构建文件继续，也绝不把
# 残缺备份静默盖回去——保留备份、非零退出，人工处理。
restore_gradle() {
  if ! bash "$VERSION_INJECT" restore "$BUILD_GRADLE"; then
    echo "FATAL release_build: 版本注入还原失败，已终止（备份保留在 $GRADLE_BAK，请人工恢复后重试）" >&2
    exit 1
  fi
}
trap restore_gradle EXIT
bash "$VERSION_INJECT" inject "$BUILD_GRADLE" "$EMBED_NAME" || fail "版本注入失败（源文件已还原或未改动）"

# ─── 构建 ────────────────────────────────────────────────────────────────
log "构建 :app:assembleFossRelease（versionName=$EMBED_NAME）"
export SIGNING_KEYSTORE_PATH SIGNING_KEY_ALIAS
export SIGNING_KEY_PASSWORD=$DSH_KEY_PASSWORD
export SIGNING_STORE_PASSWORD=$DSH_KEYSTORE_PASSWORD
./gradlew --no-daemon :app:assembleFossRelease --console=plain || fail "构建失败"
restore_gradle || fail "构建成功但版本注入还原失败（备份保留在 $GRADLE_BAK，请人工恢复）"
trap - EXIT

OUT_APK=""
for cand in "$OUT_APK_DIR/app-foss-release.apk" "$OUT_APK_DIR/app-foss-release-unsigned.apk"; do
  [[ -f "$cand" ]] && OUT_APK=$cand && break
done
[[ -n "$OUT_APK" ]] || fail "构建后未找到产物（$OUT_APK_DIR/app-foss-release[.apk|-unsigned.apk]）"
[[ "$OUT_APK" != *-unsigned.apk ]] || warn "产物为 unsigned（签名配置未生效；断言大概率失败）"
pass "构建完成: $OUT_APK"

# ─── 读取构建产物版本（versionCode 单一来源 = 产物 manifest）─────────────
log "读取构建产物版本"
find_aapt
EMBEDDED_NAME=$(apk_version_name "$OUT_APK")
VERSION_CODE=$(apk_version_code "$OUT_APK")
[[ -n "$EMBEDDED_NAME" && -n "$VERSION_CODE" ]] \
  || fail "构建产物内嵌版本解析失败（fail-closed）: $OUT_APK"
[[ "$EMBEDDED_NAME" == "$EMBED_NAME" ]] \
  || fail "构建产物内嵌 versionName($EMBEDDED_NAME)与请求版本($EMBED_NAME)不一致"
pass "versionName=$EMBEDDED_NAME versionCode=$VERSION_CODE"

# ─── 断言 ────────────────────────────────────────────────────────────────
log "断言产物"
"$REPO_ROOT/scripts/release_assert_apk.sh" "$OUT_APK" "$EMBED_NAME" "$VERSION_CODE" "$PINNED_CERT_SHA256" \
  || fail "产物断言失败"

# ─── --allow-dirty 到此为止（不归档/不打 tag/不发布）──────────────────────
if (( ALLOW_DIRTY )); then
  echo
  echo "========================================================================="
  echo "  构建验证通过（--allow-dirty 脏树模式）"
  echo "  版本: $EMBED_NAME (code $VERSION_CODE)    tag: $TAG_NAME（未打）"
  echo "  产物: $OUT_APK（未归档）"
  echo "  release/ 未做任何变更"
  echo "========================================================================="
  exit 0
fi

# ─── 归档（release/ 只留最新版，SHA256SUMS 全量重生成）────────────────────
log "归档 release/$APK_NAME"
mkdir -p "$RELEASE_DIR"
cp -- "$OUT_APK" "$RELEASE_DIR/$APK_NAME"
# 清理旧版本 APK（只留最新），随后全量重生成 SHA256SUMS
find "$RELEASE_DIR" -maxdepth 1 -type f -name '*.apk' ! -name "$APK_NAME" -delete
( cd "$REPO_ROOT" && sha256sum "release/$APK_NAME" > "$RELEASE_DIR/SHA256SUMS" )
pass "归档完成（release/ 仅保留 $APK_NAME + SHA256SUMS）"

# ─── tag（HEAD 打 tag）──────────────────────────────────────────────────
log "打 tag $TAG_NAME"
git tag "$TAG_NAME" || fail "git tag $TAG_NAME 失败"
pass "tag $TAG_NAME → $(git rev-parse --short HEAD)"

if (( PUBLISH )); then
  publish_release
  echo
  echo "========================================================================="
  echo "  发版完成: $EMBED_NAME (code $VERSION_CODE)  tag: $TAG_NAME"
  echo "========================================================================="
  exit 0
fi

echo
echo "构建+归档+tag 完成: $EMBED_NAME (code $VERSION_CODE)  tag: $TAG_NAME"
echo "对外发布: scripts/release_build.sh $VERSION_NAME --publish-only"
