#!/usr/bin/env bash
# alpha-gps 发版脚本（精简移植自 dshdroid/scripts/release_build.sh，保留 fail-closed 精神）：
#   - 版本注入：app/build.gradle.kts 的 versionName 硬编码（无 v 前缀，如 "0.1.0"），构建前
#     sed 改写、构建后（含失败）自动还原；versionCode 实际值来自产物 manifest，
#     预期值来自仓库根 gradle.properties 的 alphagps.versionCode（平面文件严格解析，
#     不再解析 Kotlin 源码）
#   - 产物 app/build/outputs/apk/foss/release/app-foss-release.apk（签名可用时）归档为
#     release/alpha-gps-<版本>.apk；release/ 只留最新版，SHA256SUMS 每次全量重生成
#   - 断言：构建后必须调用 scripts/release_assert_apk.sh（签名者/证书钉定/包名/版本/权限）
#   - 发布（--publish / --publish-only）两步：①主仓库：push 当前分支+tag 到主仓库 git 远端
#     （PRIMARY_REMOTE）→ 主仓库 /api/v1 REST 建 release（按 tag 查询复用/404 新建）→ 上传本次
#     APK 单文件资产 → 资产对账；失败即中止。②**主仓库成功后**才做 GitHub 步骤（M10 双源发布，
#     可 --no-github 跳过；--publish-github-only 单独重跑）：推 tag（默认不推分支）到本机 GitHub
#     远端 → api.github.com 建/复用 release → uploads.github.com 上传 → 手动走一跳重定向下载
#     重算 sha256 对账。GitHub 失败**不回滚**主仓库，退出码 40（区别于主仓库失败的 1）。
#   - 主仓库参数（host/repo/本机远端名）不入库：scripts/release.local.env 或 ALPHAGPS_PRIMARY_*
#     环境变量（见 scripts/release.local.env.example）；缺失 fail-closed
#   - 硬红线：release 资产只上传本次 APK；任何 *.jks/*.keystore/*.p12/*.pass/*.key
#     绝不上传；口令/token 只经 stdin/curl 配置流传递，绝不进 argv/日志
#   - GitHub 推送目的地：验证远端的**全部**有效目的地（fetch 与 push 两侧、含 insteadOf/pushurl/
#     pushInsteadOf 重写后，且各恰好一个）都等于公开 URL，之后 ls-remote/push 直接对已验证的
#     URL 字面量执行（见 release_lib.sh rl_gh_verify_remote）；日志里的 URL 一律净化（无 userinfo/query）

# 第一条命令即关闭 xtrace（`bash -x scripts/release_build.sh` / SHELLOPTS=xtrace 也一样），先于读取
# 任何配置/签名口令/token；并把 BASH_XTRACEFD 指向 /dev/null，即使之后某处误开 set -x，
# 轨迹也不会落到 stderr。敏感函数内再各自 `set +x` 做纵深防御。
{ set +x; } 2>/dev/null
exec {_XTRACE_NULL_FD}>/dev/null
BASH_XTRACEFD=$_XTRACE_NULL_FD
set -euo pipefail

fail() { echo "FAIL $1" >&2; exit 1; }
log()  { echo ">>> $1"; }
warn() { echo "WARN $1" >&2; }
pass() { echo "OK   $1"; }

SCRIPT_REAL=$(readlink -f -- "${BASH_SOURCE[0]}")
REPO_ROOT=$(cd "$(dirname "$SCRIPT_REAL")/.." && pwd)
cd "$REPO_ROOT"

source "$REPO_ROOT/scripts/release_lib.sh"   # parse_version_code_props / expected_code_for_tag / assert_code_monotonic（纯函数）

RELEASE_DIR="$REPO_ROOT/release"
REPO_PROPS="$REPO_ROOT/gradle.properties"
OUT_APK_DIR="$REPO_ROOT/app/build/outputs/apk/foss/release"
BUILD_GRADLE="$REPO_ROOT/app/build.gradle.kts"
GRADLE_PROPS=$HOME/.gradle/gradle.properties
SIGNING_KEYSTORE_PATH=$HOME/.config/dshdroid/signing/dshdroid.jks
SIGNING_KEY_ALIAS=dshdroid
PINNED_CERT_SHA256="18ff24c56bc52533c75ec709ea53497aa4323dac13c511979ac0a5fd7f9d97d7"
# 本仓库 versionName 硬编码无 v 前缀（如 "0.1.0"），产物内嵌 versionName = v$VERSION_NAME
# 主发布仓库参数（PRIMARY_HOST / PRIMARY_REPO / PRIMARY_REMOTE）不内置于仓库：从不入库的
# scripts/release.local.env 或环境变量 ALPHAGPS_PRIMARY_HOST/REPO/REMOTE 读取（见
# scripts/release_lib.sh rl_load_release_config；缺失 fail-closed 并打印占位示例）。
LOCAL_ENV_FILE="$REPO_ROOT/scripts/release.local.env"

# ─── 参数解析 ────────────────────────────────────────────────────────────
usage() {
  cat <<'USAGE'
用法: scripts/release_build.sh <X.Y.Z> [模式] [GitHub 选项]
模式（互斥；缺省=构建+归档+打 tag，不对外发布）:
  --allow-dirty          仅构建验证（跳过归档/tag/发布；不要求干净树）
  --publish              构建+归档+tag 后立即发布：主仓库 → 成功后 GitHub（要求干净树）
  --publish-only         不构建：校验 HEAD 对应 tag 与 release/ 归档完整后发布：主仓库 → GitHub（幂等重试）
  --publish-github-only  不构建、不碰主仓库：同上校验后只做 GitHub 步骤（同 tag 同名同 sha256 资产跳过；同名不同 sha 中止）
GitHub 选项（仅在会执行 GitHub 步骤的模式下有效）:
  --no-github            （仅 --publish/--publish-only）跳过 GitHub 步骤
  --github-tag-only      只推 tag 不推分支（默认行为，显式写出）
  --github-push-branch   同时推当前分支到 GitHub 远端（默认不推）
说明: GitHub 步骤推送 tag（可选再加当前分支），tag/分支可达的全部提交历史对公开可见。
退出码: 0 成功 | 1 失败（用法/配置/前置校验/构建/主仓库发布） | 40 GitHub 步骤失败（若在主仓库成功之后则
        主仓库已成功且不回滚，用 --publish-github-only 重跑）
配置: 主仓库 host/repo/远端名见 scripts/release.local.env（不入库）；GitHub 远端名默认 github，需先
      git remote add github https://github.com/mfreceiver/alpha-gps.git
USAGE
}
ALLOW_DIRTY=0; PUBLISH=0; PUBLISH_ONLY=0; PUBLISH_GH_ONLY=0
NO_GITHUB=0; GH_TAG_ONLY=0; GH_PUSH_BRANCH=0; VERSION_NAME=""
for arg in "$@"; do
  case "$arg" in
    --allow-dirty)          ALLOW_DIRTY=1 ;;
    --publish)              PUBLISH=1 ;;
    --publish-only)         PUBLISH_ONLY=1 ;;
    --publish-github-only)  PUBLISH_GH_ONLY=1 ;;
    --no-github)            NO_GITHUB=1 ;;
    --github-tag-only)      GH_TAG_ONLY=1 ;;
    --github-push-branch)   GH_PUSH_BRANCH=1 ;;
    -h|--help)              usage; exit 0 ;;
    *)
      if [[ -z "$VERSION_NAME" ]]; then VERSION_NAME=$arg
      else fail "无法识别的参数: $arg（见 $0 --help）"; fi ;;
  esac
done
[[ -n "$VERSION_NAME" ]] || { echo "用法: $0 <X.Y.Z> [--allow-dirty|--publish|--publish-only|--publish-github-only] （详见 --help）" >&2; exit 1; }
(( PUBLISH + PUBLISH_ONLY + PUBLISH_GH_ONLY <= 1 )) || fail "--publish / --publish-only / --publish-github-only 互斥"
(( ALLOW_DIRTY + PUBLISH + PUBLISH_ONLY + PUBLISH_GH_ONLY <= 1 )) || fail "--allow-dirty 仅限构建验证，不能与发布模式组合"
(( ! NO_GITHUB || PUBLISH || PUBLISH_ONLY )) || fail "--no-github 仅可与 --publish / --publish-only 组合"
(( ! (GH_TAG_ONLY && GH_PUSH_BRANCH) )) || fail "--github-tag-only 与 --github-push-branch 互斥"
# GitHub 步骤是否会执行：--publish-github-only，或发布模式且未 --no-github
GITHUB_STEP=0
if (( PUBLISH_GH_ONLY )) || { (( PUBLISH || PUBLISH_ONLY )) && (( ! NO_GITHUB )); }; then GITHUB_STEP=1; fi
(( GITHUB_STEP || ( ! GH_TAG_ONLY && ! GH_PUSH_BRANCH ) )) \
  || fail "--github-tag-only / --github-push-branch 仅在会执行 GitHub 步骤的发布模式下有效"
# 不构建、只校验既有归档的模式
ARCHIVE_ONLY=0; MODE_FLAG=""
if (( PUBLISH_ONLY )); then ARCHIVE_ONLY=1; MODE_FLAG=--publish-only; fi
if (( PUBLISH_GH_ONLY )); then ARCHIVE_ONLY=1; MODE_FLAG=--publish-github-only; fi
[[ "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || fail "版本号必须是 X.Y.Z 语义化格式（如 1.7.3），当前: '${VERSION_NAME}'"

# 主仓库参数外置：本机配置/环境变量，缺失或非法 fail-closed（除 --publish-github-only 外所有
# 模式都需要主仓库远端做版本唯一性/发布；-h/--help 已在上面提前退出，不受影响）。
# GITHUB_REMOTE（本机 GitHub 远端名，默认 github）同在此解析。
if (( PUBLISH_GH_ONLY )); then rl_load_release_config "$LOCAL_ENV_FILE" 0 || exit 1
else rl_load_release_config "$LOCAL_ENV_FILE" 1 || exit 1; fi

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
  { set +x; } 2>/dev/null
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
  log "主仓库 凭据预取"
  local CRED GU GP
  CRED="$(printf 'protocol=https\nhost=%s\n\n' "$PRIMARY_HOST" | timeout 15 git credential fill)" \
    || fail "主仓库 凭据缺失/超时（git credential fill 15s 内未非交互返回 $PRIMARY_HOST 凭据）"
  GU="$(printf '%s' "$CRED" | sed -n 's/^username=//p')"
  GP="$(printf '%s' "$CRED" | sed -n 's/^password=//p')"
  [[ -n "$GU" && -n "$GP" ]] || fail '主仓库 凭据不完整（username/password 缺失）'
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
  # 兼容不回传资产 sha256 的主仓库 forge：size 弱校验 + 全量下载重算 sha256 强校验（~20MB 可接受）
  verify_asset_by_download() {
    local vname="$1" vsize="$2" vdurl="$3" vlsha="$4"
    local dl="$WORK_DIR/asset_verify.bin" dsha lsize HTTP_CODE RC
    lsize="$(wc -c < "$RELEASE_DIR/$APK_NAME" | tr -d ' ')"
    [[ -n "$vdurl" ]] || fail "资产强校验：$vname 远端 sha256 缺失且无下载 URL（fail-closed）"
    [[ "$vsize" != "-1" && "$vsize" == "$lsize" ]] \
      || fail "资产强校验：$vname 远端 size($vsize) ≠ 本地字节数($lsize)（fail-closed）"
    log "远端未回传 sha256，改用 size + 全量下载重算哈希: $vname"
    # 同源硬校验（fail-closed）：下载 URL 必须与 PRIMARY_HOST/PRIMARY_REPO/TAG_NAME/资产名
    # 精确同源（见 scripts/release_lib.sh assert_asset_url_trusted），防止资产表被
    # 篡改后带凭据 curl 指向任意主机。
    assert_asset_url_trusted "$vdurl" "$PRIMARY_HOST" "$PRIMARY_REPO" "$TAG_NAME" "$vname" \
      || fail "资产 URL 未通过同源校验: $vname → $vdurl（fail-closed）"
    RC=0
    # -q 必须是首参：忽略 ~/.curlrc（防本机配置注入 -L/代理改写目标）；--no-location
    # 显式不跟随重定向（3xx 表现为非 200 → fail-closed）；--proto 双保险确保即使被
    # 重定向也只允许 https。主仓库 browser_download_url 直接返回附件字节，无 302 到
    # 对象存储的场景，无需跟随。-K - 配置流只携带 user，不受 -q 影响。
    HTTP_CODE="$(curl_config | curl -q -K - -sS -m 300 --no-location --proto '=https' --proto-redir '=https' -o "$dl" -w '%{http_code}' "$vdurl")" || RC=$?
    [[ "$RC" -eq 0 && "$HTTP_CODE" == "200" ]] \
      || { rm -f "$dl"; fail "资产强校验：$vname 下载失败 HTTP ${HTTP_CODE:-n/a}（fail-closed）"; }
    dsha="$(sha256sum "$dl" | awk '{print $1}')"
    rm -f "$dl"
    [[ "$dsha" == "$vlsha" ]] || fail "资产强校验：$vname 下载重算 sha256 不一致（$dsha ≠ 本地 $vlsha）"
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

  # 主仓库 release：publish-only 先按 tag 查询——200 复用 / 404 新建 / 其他 fail
  log "主仓库 release"
  local REL_ID="" GETF RC HTTP
  if [[ "$PUBLISH_ONLY" -eq 1 ]]; then
    GETF="$WORK_DIR/rel_get.json"; RC=0
    HTTP="$(curl_config | curl -K - -sS -m 30 -o "$GETF" -w '%{http_code}' "$API/releases/tags/$TAG_NAME")" || RC=$?
    if [[ "$RC" -ne 0 ]]; then fail "主仓库 release 查询失败（curl rc=$RC, HTTP=${HTTP:-n/a}）"; fi
    if [[ "$HTTP" == "200" ]]; then
      REL_ID="$(python3 -c 'import json; print(json.load(open("'"$GETF"'")).get("id",""))' 2>/dev/null || true)"
      [[ -n "$REL_ID" ]] || fail "主仓库 release 查询响应无 id"
      log "主仓库 已有 tag $TAG_NAME 的 release（id=$REL_ID）→ 复用并做资产对账"
    elif [[ "$HTTP" != "404" ]]; then
      fail "主仓库 release 查询失败: HTTP $HTTP"
    else
      log "主仓库 无 tag $TAG_NAME 的 release → 新建"
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
    if [[ "$RC" -ne 0 ]]; then head -c 300 "$RESPF" >&2 2>/dev/null || true; fail "主仓库 release 创建失败（curl rc=$RC, HTTP=${HTTP:-n/a}）"; fi
    [[ "$HTTP" =~ ^2 ]] || { head -c 300 "$RESPF" >&2 2>/dev/null || true; fail "主仓库 release 创建失败: HTTP $HTTP"; }
    REL_ID="$(python3 -c 'import json; print(json.load(open("'"$RESPF"'")).get("id",""))' 2>/dev/null || true)"
    [[ -n "$REL_ID" ]] || fail "主仓库 release 响应无 id（解析失败）"
  fi

  # 资产上传（幂等）：先查现有资产表；缺失 → 上传确认 2xx；同名 sha 一致 → 跳过
  local VF="$WORK_DIR/rel_assets.json" ASSET_LIST name lsha HTTP_CODE
  RC=0
  HTTP="$(curl_config | curl -K - -sS -m 30 -o "$VF" -w '%{http_code}' "$API/releases/$REL_ID")" || RC=$?
  [[ "$RC" -eq 0 && "$HTTP" == "200" ]] || fail "主仓库 release 资产表查询失败: HTTP ${HTTP:-n/a}"
  ASSET_LIST="$(parse_assets "$VF")" || fail "主仓库 release 资产表解析失败（fail-closed）"
  lsha="$(awk -v f="release/$APK_NAME" '$2==f{print $1}' "$RELEASE_DIR/SHA256SUMS")"
  [[ -n "$lsha" ]] || fail "资产对账：release/$APK_NAME 无本地 SHA256SUMS 条目（fail-closed）"
  name="$APK_NAME"
  rsha="$(awk -F'\t' -v n="$name" '$1==n{print $3}' <<<"$ASSET_LIST")"
  if [[ -n "$rsha" ]]; then
    if [[ "$rsha" != "-" ]]; then
      [[ "$rsha" == "$lsha" ]] || fail "远端资产同名但内容不一致: $name（远端 $rsha ≠ 本地 $lsha）"
      pass "资产已存在且 sha256 一致: $name → 跳过上传"
    else
      verify_asset_by_download "$name" \
        "$(awk -F'\t' -v n="$name" '$1==n{print $2}' <<<"$ASSET_LIST")" \
        "$(awk -F'\t' -v n="$name" '$1==n{print $4}' <<<"$ASSET_LIST")" "$lsha"
      pass "资产已存在（远端 sha256 缺失，已下载强校验一致）: $name → 跳过上传"
    fi
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
  [[ -n "$rsha" ]] || fail "发布后资产核验：$name 不在远端资产表中（fail-closed）"
  if [[ "$rsha" != "-" ]]; then
    [[ "$rsha" == "$lsha" ]] || fail "发布后资产核验：$name 哈希不一致（远端 $rsha ≠ 本地 $lsha）"
    pass "资产对账通过: $name sha256 一致"
  else
    verify_asset_by_download "$name" \
      "$(awk -F'\t' -v n="$name" '$1==n{print $2}' <<<"$ASSET_FINAL")" \
      "$(awk -F'\t' -v n="$name" '$1==n{print $4}' <<<"$ASSET_FINAL")" "$lsha"
    pass "资产对账通过: $name（远端 sha256 缺失，已下载重算一致）"
  fi
  rm -rf -- "$WORK_DIR"
  log "主仓库 release: https://$PRIMARY_HOST/$PRIMARY_REPO/releases/tag/$TAG_NAME"
}

# ─── GitHub 步骤（M10 双源发布；主仓库成功后才执行）─────────────────────────────
# 公开仓库 github.com/mfreceiver/alpha-gps（常量，见 release_lib.sh RL_GH_*）。
# 凭据：git credential fill（protocol=https host=github.com，15s 超时），token 只存在于 shell
# 变量 GH_TOKEN（不 export），经 stdin→curl -K - 配置流传递，绝不进 argv/日志；整个步骤在
# set +x 下运行。带 token 的请求只发往 api.github.com / uploads.github.com（rl_gh_url_allowed
# 白名单硬校验，且 --no-location 不跟随重定向）。
# 对账下载是匿名的（release 公开）：首跳 browser_download_url（先过 assert_asset_url_trusted
# 同源校验）不跟随、读 Location，须 https 且主机在下载白名单；第二跳（无凭据、仍不跟随）必须
# 直接 200，再遇重定向或主机不在白名单 → 中止。绝不放开 --proto-redir / -L。
# 失败语义：整个 publish_github 跑在子 shell，任何失败 → 子 shell 非零 → 脚本以 40 退出。
GITHUB_REPO=$RL_GH_REPO
GH_WEB_HOST=github.com
GH_API="https://api.github.com/repos/$GITHUB_REPO"
GH_UPLOAD="https://uploads.github.com/repos/$GITHUB_REPO"
EXIT_GITHUB_FAIL=40
GH_TOKEN=""
GH_AFTER_PRIMARY=0

gh_err() { echo "FAIL $1" >&2; }

# gh_preflight：本地前置检查（在构建/任何对外动作之前执行；失败 → 普通失败 exit 1，尚未发布任何东西）
gh_preflight() {
  { set +x; } 2>/dev/null
  local CRED
  # 校验远端的全部有效目的地（fetch+push、含重写，各恰好一个且等于公开 URL）；错误信息由库净化后输出
  rl_gh_verify_remote "$GITHUB_REMOTE" || exit 1
  [[ "$GITHUB_REMOTE" != "${PRIMARY_REMOTE:-}" ]] \
    || fail "GitHub 远端名不能与主仓库远端名相同（$GITHUB_REMOTE）"
  CRED="$(printf 'protocol=https\nhost=github.com\n\n' | timeout 15 git credential fill)" \
    || fail "GitHub 凭据缺失/超时（git credential fill 15s 内未非交互返回 github.com 凭据）"
  GH_TOKEN="$(printf '%s' "$CRED" | sed -n 's/^password=//p')"
  CRED=""
  [[ -n "$GH_TOKEN" ]] || fail "GitHub 凭据不完整（password/token 缺失）"
  printf '%s' "$GH_TOKEN" | rl_gh_curl_config > /dev/null || fail "GitHub token 形态非法"
  pass "GitHub 前置检查通过（远端 $GITHUB_REMOTE → github.com/$GITHUB_REPO；凭据已取得，不回显）"
}

# gh_api <api|upload> <输出文件> <curl 参数...> <URL>  → stdout=HTTP 码；rc=curl/白名单失败
gh_api() {
  local kind=$1 out=$2; shift 2
  local url=${!#}
  rl_gh_url_allowed "$kind" "$url" || return 97
  printf '%s' "$GH_TOKEN" | rl_gh_curl_config \
    | curl -q -K - -sS -m 60 --no-location --proto '=https' --proto-redir '=https' \
        -o "$out" -w '%{http_code}' "$@"
}

# gh_fetch_noredirect <url> <输出文件> <头文件> → stdout=HTTP 码。匿名、不跟随重定向。
gh_fetch_noredirect() {
  curl -q -sS -m 300 --max-filesize 209715200 --no-location --proto '=https' --proto-redir '=https' \
    -D "$3" -o "$2" -w '%{http_code}' --url "$1"
}

# gh_remote_sha256 <资产名> <browser_download_url> → stdout=下载内容 sha256（手动走一跳重定向）
gh_remote_sha256() {
  local name=$1 url=$2 dl="$GH_WORK/asset_dl.bin" code loc lochost
  assert_asset_url_trusted "$url" "$GH_WEB_HOST" "$GITHUB_REPO" "$TAG_NAME" "$name" >&2 \
    || { gh_err "GitHub 资产 URL 未通过同源校验: $name"; return 1; }
  rm -f "$dl" "$GH_WORK/hop1.hdr" "$GH_WORK/hop2.hdr"
  code="$(gh_fetch_noredirect "$url" "$dl" "$GH_WORK/hop1.hdr")" \
    || { gh_err "GitHub 资产下载首跳失败（curl 错误）: $name"; return 1; }
  case "$code" in
    200) ;;   # 无重定向直接返回字节（不预期，但安全：来源是 assert_asset_url_trusted 通过的 github.com URL）
    301|302|303|307|308)
      loc="$(rl_gh_location_from_headers "$GH_WORK/hop1.hdr")" \
        || { gh_err "GitHub 资产下载：首跳 $code 但 Location 头不可用"; return 1; }
      rl_gh_url_allowed download "$loc" >&2 \
        || { gh_err "GitHub 资产下载：重定向目标未通过 https+主机白名单校验（不跟随）"; return 1; }
      lochost=${loc#https://}; lochost=${lochost%%[/?#]*}
      echo ">>> 资产下载重定向一跳 → $lochost（白名单内；第二跳匿名、不跟随）" >&2
      rm -f "$dl"
      code="$(gh_fetch_noredirect "$loc" "$dl" "$GH_WORK/hop2.hdr")" \
        || { gh_err "GitHub 资产下载第二跳失败（curl 错误）: $lochost"; return 1; }
      case "$code" in
        200) ;;
        30[12378]) gh_err "GitHub 资产下载：第二跳仍重定向（HTTP $code），中止（只允许走一跳）"; return 1 ;;
        *) gh_err "GitHub 资产下载第二跳 HTTP $code"; return 1 ;;
      esac ;;
    *) gh_err "GitHub 资产下载首跳 HTTP $code（期望 200 或 3xx）"; return 1 ;;
  esac
  sha256sum "$dl" | awk '{print $1}'
  rm -f "$dl"
}

publish_github() {
  { set +x; } 2>/dev/null
  GH_WORK=$(mktemp -d)
  trap 'rm -rf -- "$GH_WORK"' EXIT
  [[ -n "$GH_TOKEN" ]] || fail "GitHub token 未就绪（内部错误）"
  # 推送前（主仓库发布之后）重新验证并钉定目的地：后续 ls-remote/push 只对该 URL 字面量执行
  rl_gh_verify_remote "$GITHUB_REMOTE" || exit 1
  local GH_URL=$RL_GH_PINNED_URL
  log "GitHub 步骤: 推送 tag $TAG_NAME$( (( GH_PUSH_BRANCH )) && echo ' + 当前分支' ) 到远端 $GITHUB_REMOTE（github.com/$GITHUB_REPO）"
  warn "GitHub 步骤会推送 tag$( (( GH_PUSH_BRANCH )) && echo ' 与当前分支' )，其可达的全部提交历史对公开可见"

  local lsha lsize BRANCH LOCAL_SHA REMOTE_OUT REMOTE_SHA
  lsha="$(awk -v f="release/$APK_NAME" '$2==f{print $1}' "$RELEASE_DIR/SHA256SUMS")"
  [[ -n "$lsha" ]] || fail "GitHub 对账：release/$APK_NAME 无本地 SHA256SUMS 条目（fail-closed）"
  lsize="$(wc -c < "$RELEASE_DIR/$APK_NAME" | tr -d ' ')"

  # ① 远端 tag：已存在且同 commit → 跳过 push；不一致 → 中止（禁止移动已发布 tag）
  LOCAL_SHA="$(git rev-parse "$TAG_NAME^{commit}")" || fail "本地 tag $TAG_NAME 不可解析"
  if ! REMOTE_OUT="$(timeout 30 git ls-remote --tags "$GH_URL" "refs/tags/$TAG_NAME" "refs/tags/$TAG_NAME^{}" 2>&1)"; then
    fail "GitHub 远端 tag 查询失败（$GITHUB_REMOTE 不可达/网络错误，fail-closed）"
  fi
  REMOTE_SHA="$(awk -v t="refs/tags/$TAG_NAME" -v p="refs/tags/$TAG_NAME^{}" '
    $2 == p { peeled = $1 }
    $2 == t { plain = $1 }
    END { if (peeled != "") print peeled; else if (plain != "") print plain }' <<<"$REMOTE_OUT")"
  if [[ -n "$REMOTE_SHA" ]]; then
    [[ "$REMOTE_SHA" == "$LOCAL_SHA" ]] \
      || fail "GitHub 远端 tag $TAG_NAME 指向 $REMOTE_SHA ≠ 本地 $LOCAL_SHA（禁止移动已发布 tag）"
    log "GitHub 远端 tag $TAG_NAME 已存在且同 commit → 跳过 tag push"
  else
    timeout 120 git -c push.followTags=false push --no-recurse-submodules "$GH_URL" "refs/tags/$TAG_NAME:refs/tags/$TAG_NAME" \
      || fail "git push $GITHUB_REMOTE $TAG_NAME 失败"
  fi
  if (( GH_PUSH_BRANCH )); then
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    [[ "$BRANCH" != HEAD ]] || fail "--github-push-branch：当前是游离 HEAD，无分支可推"
    timeout 120 git -c push.followTags=false push --no-recurse-submodules "$GH_URL" "refs/heads/$BRANCH:refs/heads/$BRANCH" \
      || fail "git push $GITHUB_REMOTE $BRANCH 失败"
  fi

  # ② release：按 tag 查询——200 复用 / 404 新建 / 其它 fail-closed
  log "GitHub release"
  local REL_ID="" HTTP RC=0 GETF="$GH_WORK/rel_get.json" RESPF="$GH_WORK/rel_resp.json" BODYF="$GH_WORK/rel_body.json"
  HTTP="$(gh_api api "$GETF" "$GH_API/releases/tags/$TAG_NAME")" || RC=$?
  [[ "$RC" -eq 0 ]] || fail "GitHub release 查询失败（rc=$RC, HTTP=${HTTP:-n/a}）"
  if [[ "$HTTP" == "200" ]]; then
    REL_ID="$(rl_gh_release_field "$GETF" id)" || fail "GitHub release 查询响应无有效 id"
    log "GitHub 已有 tag $TAG_NAME 的 release（id=$REL_ID）→ 复用并做资产对账"
  elif [[ "$HTTP" != "404" ]]; then
    head -c 300 "$GETF" >&2 2>/dev/null || true
    fail "GitHub release 查询失败: HTTP $HTTP"
  else
    log "GitHub 无 tag $TAG_NAME 的 release → 新建"
    python3 -c "
import json
print(json.dumps({'tag_name': '$TAG_NAME', 'name': '$TAG_NAME', 'body': 'alpha-gps $TAG_NAME'}))
" > "$BODYF"
    RC=0
    HTTP="$(gh_api api "$RESPF" -X POST -H 'Content-Type: application/json' -d @"$BODYF" "$GH_API/releases")" || RC=$?
    [[ "$RC" -eq 0 && "$HTTP" == "201" ]] \
      || { head -c 300 "$RESPF" >&2 2>/dev/null || true; fail "GitHub release 创建失败（rc=$RC, HTTP=${HTTP:-n/a}）"; }
    REL_ID="$(rl_gh_release_field "$RESPF" id)" || fail "GitHub release 创建响应无有效 id"
  fi

  # ③ 资产：同名存在 → 先快检（state/size/digest）；同名不同内容中止；缺失 → 上传
  local VF="$GH_WORK/rel_assets.json" ASSET_LINE
  fetch_asset_line() { # 重新枚举 release，输出本次 APK 资产行（0/1 行）
    RC=0
    HTTP="$(gh_api api "$VF" "$GH_API/releases/$REL_ID")" || RC=$?
    [[ "$RC" -eq 0 && "$HTTP" == "200" ]] || { gh_err "GitHub release 资产表查询失败: HTTP ${HTTP:-n/a}"; return 1; }
    local all; all="$(rl_gh_release_field "$VF" assets)" || { gh_err "GitHub 资产表解析失败"; return 1; }
    awk -F'\t' -v n="$APK_NAME" '$1==n' <<<"$all"
  }
  ASSET_LINE="$(fetch_asset_line)" || fail "GitHub 资产枚举失败（fail-closed）"
  if [[ -n "$ASSET_LINE" ]]; then
    [[ "$(wc -l <<<"$ASSET_LINE")" -eq 1 ]] || fail "GitHub 资产表同名资产出现多次: $APK_NAME"
    local a_size a_sha a_state
    a_size=$(cut -f2 <<<"$ASSET_LINE"); a_sha=$(cut -f3 <<<"$ASSET_LINE"); a_state=$(cut -f4 <<<"$ASSET_LINE")
    [[ "$a_state" == uploaded ]] \
      || fail "GitHub 资产 $APK_NAME 状态为 '$a_state'（非 uploaded，疑似残缺上传）；请在 GitHub release 页手工删除后重跑"
    [[ "$a_size" == "$lsize" ]] \
      || fail "GitHub 远端资产同名但内容不一致: $APK_NAME（size 远端 $a_size ≠ 本地 $lsize），中止"
    if [[ "$a_sha" != "-" && "$a_sha" != "$lsha" ]]; then
      fail "GitHub 远端资产同名但 sha256 不一致: $APK_NAME（远端 digest $a_sha ≠ 本地 $lsha），中止"
    fi
    log "GitHub 已存在同名资产且 size/digest 一致: $APK_NAME → 跳过上传，进入下载对账"
  else
    local UPF="$GH_WORK/asset_resp.json"
    RC=0
    HTTP="$(gh_api upload "$UPF" -m 600 -X POST -H 'Content-Type: application/vnd.android.package-archive' \
      --data-binary @"$RELEASE_DIR/$APK_NAME" "$GH_UPLOAD/releases/$REL_ID/assets?name=$APK_NAME")" || RC=$?
    [[ "$RC" -eq 0 && "$HTTP" == "201" ]] \
      || { head -c 300 "$UPF" >&2 2>/dev/null || true; fail "GitHub 资产上传失败: $APK_NAME（rc=$RC, HTTP=${HTTP:-n/a}）"; }
    pass "GitHub 资产上传 OK: $APK_NAME（HTTP $HTTP）"
    ASSET_LINE="$(fetch_asset_line)" || fail "GitHub 上传后资产枚举失败（fail-closed）"
    [[ -n "$ASSET_LINE" ]] || fail "GitHub 上传后资产表中找不到 $APK_NAME（fail-closed）"
    [[ "$(cut -f4 <<<"$ASSET_LINE")" == uploaded && "$(cut -f2 <<<"$ASSET_LINE")" == "$lsize" ]] \
      || fail "GitHub 上传后资产状态/size 异常: $(cut -f2,4 <<<"$ASSET_LINE")"
  fi

  # ④ 对账：下载 browser_download_url（手动走一跳）重算 sha256
  local a_url rsha
  a_url=$(cut -f5 <<<"$ASSET_LINE")
  [[ -n "$a_url" ]] || fail "GitHub 资产无 browser_download_url（fail-closed）"
  rsha="$(gh_remote_sha256 "$APK_NAME" "$a_url")" || fail "GitHub 资产下载对账失败: $APK_NAME"
  [[ "$rsha" == "$lsha" ]] \
    || fail "GitHub 资产对账：$APK_NAME 下载重算 sha256 不一致（远端 $rsha ≠ 本地 $lsha）"
  pass "GitHub 资产对账通过: $APK_NAME sha256 一致"
  log "GitHub release: https://github.com/$GITHUB_REPO/releases/tag/$TAG_NAME"
}

# run_github_step：子 shell 隔离执行；任何失败 → 以 EXIT_GITHUB_FAIL(40) 退出并明确报告
run_github_step() {
  local rc=0
  ( publish_github ) || rc=$?
  if (( rc != 0 )); then
    if (( GH_AFTER_PRIMARY )); then
      echo "FATAL release_build: 主仓库发布已成功，但 GitHub 步骤失败（子步骤 rc=$rc；脚本退出码 $EXIT_GITHUB_FAIL；主仓库不回滚）。排查后单独重跑: scripts/release_build.sh $VERSION_NAME --publish-github-only" >&2
    else
      echo "FATAL release_build: GitHub 步骤失败（子步骤 rc=$rc；脚本退出码 $EXIT_GITHUB_FAIL；本次未涉及主仓库）" >&2
    fi
    exit "$EXIT_GITHUB_FAIL"
  fi
}

# ─── 签名预检（fail-closed）──────────────────────────────────────────────
{ set +x; } 2>/dev/null   # 纵深防御：下面开始读取/赋值签名口令
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
if [[ -n "$GIT_STATUS" ]] && (( PUBLISH || PUBLISH_ONLY || PUBLISH_GH_ONLY )); then
  echo "$GIT_STATUS" | head -5 >&2
  fail "工作树不干净：发布（--publish/--publish-only/--publish-github-only）不允许脏树（--allow-dirty 仅限构建验证）"
fi
if [[ -n "$GIT_STATUS" ]] && (( ! ALLOW_DIRTY )); then
  echo "$GIT_STATUS" | head -5 >&2
  fail "工作树不干净。--allow-dirty 仅限构建验证（不归档/不打 tag）"
fi
if [[ -n "$GIT_STATUS" ]] && (( ALLOW_DIRTY )); then
  warn "脏树 --allow-dirty 模式：仅构建验证，将跳过归档与 tag"
fi
pass "分支 $BRANCH${GIT_STATUS:+（脏树，--allow-dirty）}"

# ─── GitHub 前置检查（先于构建与一切对外动作；失败 = 尚未发布任何东西的普通失败）────
if (( GITHUB_STEP )); then
  log "GitHub 前置检查（远端/凭据；GitHub 步骤会推送 tag$( (( GH_PUSH_BRANCH )) && echo ' 与当前分支' )，其可达历史对公开可见）"
  gh_preflight
fi

# ─── 版本唯一性（fail-closed；--publish-only / --publish-github-only 不出新版本，跳过）──
if (( ! ARCHIVE_ONLY )); then
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
if (( ARCHIVE_ONLY )); then
  log "$MODE_FLAG：校验归档完整性（不构建）"
  git rev-parse -q --verify "refs/tags/$TAG_NAME" > /dev/null \
    || fail "$MODE_FLAG：本地 tag $TAG_NAME 不存在。发版产物由 release_build.sh 归档时创建，勿手工造 tag 绕门禁"
  [[ "$(git rev-parse "$TAG_NAME^{commit}")" == "$(git rev-parse HEAD)" ]] \
    || fail "$MODE_FLAG：tag $TAG_NAME 不在 HEAD 上（HEAD 版本与归档不符；在干净树重跑 release_build.sh 出该版本）"
  [[ -f "$RELEASE_DIR/$APK_NAME" ]] || fail "$MODE_FLAG：归档缺失 release/$APK_NAME"
  [[ -f "$RELEASE_DIR/SHA256SUMS" ]] || fail "$MODE_FLAG：release/SHA256SUMS 缺失"
  grep -qxF "release/$APK_NAME" <(awk '{print $2}' "$RELEASE_DIR/SHA256SUMS") \
    || fail "$MODE_FLAG：SHA256SUMS 缺当前版本条目: release/$APK_NAME"
  sha256sum -c "$RELEASE_DIR/SHA256SUMS" > /dev/null \
    || fail "$MODE_FLAG：SHA256SUMS 校验失败（归档损坏）"
  # 预期 versionCode 独立来源：被发布 tag 提交的 gradle.properties（上方门禁已确认
  # tag 在 HEAD 上；LEGACY 表中的历史 tag 用表值——其提交的 gradle.properties 尚无该 key），
  # 不信任归档 APK 自读值（否则等于自比，APK code 错也会 PASS）。
  # git show 原始字节经临时文件直入 parser（不经 bash 变量——NUL 会被丢弃仅留 warning）。
  EXPECT_CODE=$(expected_code_for_tag "$TAG_NAME") \
    || fail "$MODE_FLAG：无法读取/解析 tag 提交的 gradle.properties 的 versionCode（fail-closed）: $TAG_NAME"
  assert_code_monotonic "$EXPECT_CODE" "$VERSION_NAME" || fail "$MODE_FLAG：versionCode 单调性门禁失败"
  find_aapt
  EMBEDDED_NAME=$(apk_version_name "$RELEASE_DIR/$APK_NAME")
  VERSION_CODE=$(apk_version_code "$RELEASE_DIR/$APK_NAME")
  [[ -n "$EMBEDDED_NAME" && -n "$VERSION_CODE" ]] \
    || fail "$MODE_FLAG：归档 APK 内嵌版本解析失败（fail-closed）: $APK_NAME"
  [[ "$EMBEDDED_NAME" == "$EMBED_NAME" ]] \
    || fail "$MODE_FLAG：归档 APK 内嵌 versionName($EMBEDDED_NAME)与请求版本($EMBED_NAME)不一致"
  "$REPO_ROOT/scripts/release_assert_apk.sh" "$RELEASE_DIR/$APK_NAME" "$EMBED_NAME" "$EXPECT_CODE" "$PINNED_CERT_SHA256" \
    || fail "$MODE_FLAG：归档 APK 断言失败（含 versionCode 与 tag 提交 gradle.properties 期望值比对）"
  pass "归档完整（APK+SHA256SUMS+断言）"
  if (( PUBLISH_ONLY )); then publish_release; fi
  if (( GITHUB_STEP )); then GH_AFTER_PRIMARY=$PUBLISH_ONLY; run_github_step; fi
  echo
  echo "$MODE_FLAG 完成: $VERSION_NAME (versionCode $VERSION_CODE)  tag: $TAG_NAME"
  exit 0
fi

# ─── versionCode 独立预期（仓库根 gradle.properties 严格解析，先于版本注入）────
log "versionCode 期望值: 从 gradle.properties 严格解析（注入前）"
EXPECT_CODE=$(parse_version_code_props "$REPO_PROPS") \
  || fail "gradle.properties versionCode 解析失败（key 必须恰好一处且整行严格 key=canonical十进制），fail-closed"
assert_code_monotonic "$EXPECT_CODE" "$VERSION_NAME" || fail "versionCode 单调性门禁失败"
pass "gradle.properties versionCode=$EXPECT_CODE（独立预期来源 + 单调性检查）"

# ─── 版本注入（sed 改写 + 构建后还原）────────────────────────────────────
# app/build.gradle.kts 硬编码 versionName = "X.Y.Z"（无 v 前缀，无 env 注入），构建前改写、
# 无论如何（成功/失败/中断经 EXIT trap）还原备份；父层 trap 仅在 inject 成功、
# 还原责任交接完成后激活。备份/还原的 fail-closed 语义
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
# 先注入、后激活 trap：只有 inject 成功返回（helper 已确认备份完整并把还原责任
# 移交父层）后，父层 EXIT trap 才接管还原。inject 失败（含残留备份拒绝）时父层
# 绝不 mv 未知/残留备份去覆盖源文件——helper 自身的 cp+cmp 门槛与 trap 已保证
# 源文件未被改动或已自动还原。
bash "$VERSION_INJECT" inject "$BUILD_GRADLE" "$EMBED_NAME" || fail "版本注入失败（源文件已还原或未改动；父层不接管残留备份）"
trap restore_gradle EXIT

# ─── compose 资源生成残留清理（构建前；只删生成/中间产物，绝不碰源码目录）────
# 增量构建会把已删除语言的 Compose 资源（values-de 等 .cvr）残留在下列生成/中间
# 目录里并再次打进 APK。逐个 realpath 校验位于对应模块 build/ 内且存在才删，
# 空/防误删双保险（${REPO_ROOT:?} 防空变量）。产物侧由 release_assert_apk.sh 的
# 语言断言（rl_check_apk_lang）兜底。
clean_compose_residue() {
  local base=${REPO_ROOT:?} d rp cleaned=0
  for d in \
    "$base/sharednew/build/generated/compose" \
    "$base/sharednew/build/generated/assets/copyAndroidMainComposeResourcesToAndroidAssets" \
    "$base/sharednew/build/generated/supportedLanguages" \
    "$base/sharednew/build/intermediates/assets" \
    "$base/app/build/intermediates/assets" \
    "$base/app/build/intermediates/compressed_assets" \
  ; do
    [[ -n "${d:?}" ]] || { fail "compose 残留清理：目标路径为空（fail-closed）"; }
    [[ -e "$d" ]] || continue
    rp=$(realpath -- "$d") || { fail "compose 残留清理：realpath 失败: $d"; }
    case "$rp" in
      "$base/sharednew/build/generated/"*|"$base/sharednew/build/intermediates/"*|"$base/app/build/intermediates/"*) ;;
      *) fail "compose 残留清理：realpath 越界（不在 sharednew/build 或 app/build 生成/中间目录内）: $rp"; ;;
    esac
    rm -rf -- "$rp" || fail "compose 残留清理：rm 失败: $rp"
    log "清理 compose 资源生成残留: ${rp#"$base"/}"
    cleaned=$((cleaned+1))
  done
  pass "compose 资源生成残留清理完成（清理 $cleaned 个目录；构建将以全新资源重新生成）"
}
clean_compose_residue

# ─── 构建 ────────────────────────────────────────────────────────────────
log "构建 :app:assembleFossRelease（versionName=$EMBED_NAME）"
export SIGNING_KEYSTORE_PATH SIGNING_KEY_ALIAS
export SIGNING_KEY_PASSWORD=$DSH_KEY_PASSWORD
export SIGNING_STORE_PASSWORD=$DSH_KEYSTORE_PASSWORD
./gradlew --no-daemon :app:assembleFossRelease --console=plain || fail "构建失败"
restore_gradle || fail "构建成功但版本注入还原失败（备份保留在 $GRADLE_BAK，请人工恢复）"
trap - EXIT
# 注入 helper 只注入 versionName；还原后 gradle.properties 的 code 必须与注入前一致
# （构建不改该文件，断言纯防意外改动），否则 fail-closed
EXPECT_CODE_AFTER=$(parse_version_code_props "$REPO_PROPS") \
  || fail "构建后 gradle.properties versionCode 再解析失败（fail-closed）"
[[ "$EXPECT_CODE_AFTER" == "$EXPECT_CODE" ]] \
  || fail "构建前后 gradle.properties versionCode 不一致（$EXPECT_CODE → $EXPECT_CODE_AFTER），fail-closed"

OUT_APK=""
for cand in "$OUT_APK_DIR/app-foss-release.apk" "$OUT_APK_DIR/app-foss-release-unsigned.apk"; do
  [[ -f "$cand" ]] && OUT_APK=$cand && break
done
[[ -n "$OUT_APK" ]] || fail "构建后未找到产物（$OUT_APK_DIR/app-foss-release[.apk|-unsigned.apk]）"
[[ "$OUT_APK" != *-unsigned.apk ]] || warn "产物为 unsigned（签名配置未生效；断言大概率失败）"
pass "构建完成: $OUT_APK"

# ─── 读取构建产物版本（versionCode 实际值 = 产物 manifest；预期值 = 受控源码）──
log "读取构建产物版本"
find_aapt
EMBEDDED_NAME=$(apk_version_name "$OUT_APK")
VERSION_CODE=$(apk_version_code "$OUT_APK")
[[ -n "$EMBEDDED_NAME" && -n "$VERSION_CODE" ]] \
  || fail "构建产物内嵌版本解析失败（fail-closed）: $OUT_APK"
[[ "$EMBEDDED_NAME" == "$EMBED_NAME" ]] \
  || fail "构建产物内嵌 versionName($EMBEDDED_NAME)与请求版本($EMBED_NAME)不一致"
pass "versionName=$EMBEDDED_NAME versionCode=$VERSION_CODE"

# ─── 断言（期望 versionCode = gradle.properties 严格解析的 EXPECT_CODE，非 APK 自读值）──
log "断言产物"
"$REPO_ROOT/scripts/release_assert_apk.sh" "$OUT_APK" "$EMBED_NAME" "$EXPECT_CODE" "$PINNED_CERT_SHA256" \
  || fail "产物断言失败（含 versionCode 与 gradle.properties 期望值比对）"

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
  if (( GITHUB_STEP )); then GH_AFTER_PRIMARY=1; run_github_step; fi
  echo
  echo "========================================================================="
  echo "  发版完成: $EMBED_NAME (code $VERSION_CODE)  tag: $TAG_NAME"
  echo "========================================================================="
  exit 0
fi

echo
echo "构建+归档+tag 完成: $EMBED_NAME (code $VERSION_CODE)  tag: $TAG_NAME"
echo "对外发布: scripts/release_build.sh $VERSION_NAME --publish-only（主仓库→GitHub；可加 --no-github；只补 GitHub 用 --publish-github-only）"
