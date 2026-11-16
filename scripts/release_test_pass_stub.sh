#!/usr/bin/env bash
# 仅供离线自测：在沙箱内创建 pass 替身与虚构凭据，绝不调用系统 pass。
make_test_pass() { # <bin目录> <沙箱 HOME>
  local bin=$1 home=$2
  mkdir -p "$bin" "$home/.fake-pass"
  printf '%s\n' '~/.config/alphagps/signing/release.jks' > "$home/.fake-pass/store-file"
  printf '%s\n' release > "$home/.fake-pass/key-alias"
  printf '%s\n' fake-store-password > "$home/.fake-pass/store-password"
  printf '%s\n' fake-key-password > "$home/.fake-pass/key-password"
  cat > "$bin/pass" <<'STUB'
#!/usr/bin/env bash
[[ $# == 2 && $1 == show && $2 == android/release-shared/* ]] || exit 1
name=${2#android/release-shared/}
case "$name" in store-file|store-password|key-alias|key-password) ;; *) exit 1 ;; esac
[[ -f "$HOME/.fake-pass/$name" ]] || exit 1
exec cat "$HOME/.fake-pass/$name"
STUB
  chmod +x "$bin/pass"
}
