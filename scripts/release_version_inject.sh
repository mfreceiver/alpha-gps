#!/usr/bin/env bash
# release_build.sh 的版本注入助手（fail-closed 备份/还原语义）：
#   inject <BUILD_GRADLE> <EMBED_NAME>  备份→sed 改写→校验；任何失败都把 BUILD_GRADLE
#                                       还原为字节一致的原文并退出非零
#   restore <BUILD_GRADLE>              把备份移回 BUILD_GRADLE；mv 失败时保留备份、
#                                       退出非零并提示人工恢复
# 关键约束（对应评审 R2）：
#   - 备份责任只在 cp 成功且 cmp 校验通过后才激活（残缺备份绝不用于覆盖源文件）；
#   - 校验失败的残缺备份立即删除，源文件从未被触碰，无需还原；
#   - restore 失败绝不静默降级：保留 $BAK、退出 1，由调用方终止后续归档/tag/发布。
set -euo pipefail

fail() { echo "FAIL release_version_inject: $1" >&2; exit 1; }

usage() { echo "用法: $0 inject <BUILD_GRADLE> <EMBED_NAME> | restore <BUILD_GRADLE>" >&2; exit 2; }

[[ $# -ge 2 ]] || usage
CMD=$1
BUILD_GRADLE=$2
BAK="$BUILD_GRADLE.release-bak"

do_restore() {
  if [[ ! -f "$BAK" ]]; then
    return 0
  fi
  if mv -f -- "$BAK" "$BUILD_GRADLE"; then
    return 0
  fi
  echo "FATAL release_version_inject: 还原失败——无法将备份移回 $BUILD_GRADLE" >&2
  echo "       备份已保留在 $BAK，请人工核对后恢复，勿直接重跑发版" >&2
  return 1
}

case "$CMD" in
  restore)
    do_restore || exit 1
    ;;
  inject)
    EMBED_NAME=${3:?用法: inject <BUILD_GRADLE> <EMBED_NAME>}
    [[ -f "$BUILD_GRADLE" ]] || fail "目标文件不存在: $BUILD_GRADLE"
    [[ -f "$BAK" ]] && fail "残留备份存在: $BAK（上次构建异常退出？请人工核对后删除）"

    VN_LINES=$(grep -cE '^[[:space:]]*versionName = "' "$BUILD_GRADLE" || true)
    [[ "$VN_LINES" -eq 1 ]] \
      || fail "app/build.gradle.kts 中 versionName 定义行数异常（$VN_LINES，期望 1）——版本注入锚点失效，人工检查"

    # 备份：cp 失败（可能留下部分写入的备份文件）或校验不一致都视为无有效备份——
    # 删除残缺备份直接失败；此时源文件尚未被改动，恢复责任尚未激活。
    if ! cp -- "$BUILD_GRADLE" "$BAK"; then
      rm -f -- "$BAK"
      fail "备份失败: $BUILD_GRADLE → $BAK（源文件未改动）"
    fi
    if ! cmp -s -- "$BUILD_GRADLE" "$BAK"; then
      rm -f -- "$BAK"
      fail "备份校验失败（cmp 不一致，可能磁盘/IO 异常）：已删除残缺备份，源文件未改动"
    fi

    # 备份已确认完整：从此刻起任何异常退出都必须先还原再报错。
    restore_on_exit() {
      local rc=$?
      if (( BACKUP_ACTIVE )); then
        if ! do_restore; then
          echo "FAIL release_version_inject: 注入失败且自动还原也失败；备份保留在 $BAK，请人工恢复" >&2
          exit 1
        fi
      fi
      exit "$rc"
    }
    BACKUP_ACTIVE=1
    trap restore_on_exit EXIT

    if ! sed -i -E "s/^([[:space:]]*versionName = )\"[^\"]*\"/\1\"$EMBED_NAME\"/" "$BUILD_GRADLE"; then
      fail "versionName 改写失败（已触发还原）"
    fi
    if ! grep -qE "^[[:space:]]*versionName = \"$EMBED_NAME\"$" "$BUILD_GRADLE"; then
      fail "版本注入后校验失败（已触发还原）"
    fi

    # 注入成功：备份继续保留给外层构建流程，还原责任移交 release_build.sh 的 EXIT trap。
    trap - EXIT
    ;;
  *)
    usage
    ;;
esac
