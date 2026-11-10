#!/usr/bin/env bash
# M10 双源发布（GitHub 步骤）自测：纯函数（scripts/release_lib.sh rl_gh_*）+ release_build.sh 接线。
# 全程无网络：沙箱 git 仓库 + 本地 bare 仓库充当主仓库/GitHub 远端 + fake curl（python，模拟主仓库
# REST、api/uploads/github.com、CDN 302）+ fake git（PATH 垫片：仅传输层 ls-remote/push 把公开 URL
# 字面量换成本地 bare、其它网络目的地/按远端名传输一律拒绝并记录；其余全部透传真实 git）+ git
# credential store（沙箱 HOME）。**沙箱里 GitHub 远端的 url 就是真实公开 URL，不再用 insteadOf 改写**
# （否则会被目的地校验正确拒绝）；生产校验不留任何测试后门。不会对真实 GitHub/主仓库做任何请求。
#   场景 1  rl_gh_url_allowed：api/upload/download 合法主机放行
#   场景 2  拒绝：'@' 旁路 / 子域 / 后缀拼接 / 端口 / 大小写 / http 降级 / 类别串用 / 控制字符 /
#           反斜杠 / 空白 / fragment / 空 / 未知类别 / 裸 githubusercontent.com / raw 子域
#   场景 3  变异验证：netloc 后缀匹配、去掉 scheme 校验的弱实现必须放行对应坏输入
#   场景 4  rl_gh_location_from_headers：大小写、缺失、重复、多响应块取最后一块、无状态行
#   场景 5  rl_gh_remote_url_ok / rl_gh_curl_config（token 形态/头注入）/ rl_gh_release_field
#   场景 6  --publish-only 主仓库→GitHub 全链成功：顺序、只推 tag、token 不进 argv、
#           带凭据请求只去 api/uploads、下载两跳匿名且无 -L、历史公开提示、成功输出
#   场景 7  --publish-github-only 幂等重跑：无主仓库请求/无需主仓库配置/不再上传，同 tag 同 sha 跳过
#   场景 8  同名资产不同内容中止（size 不同 / digest 不同 / 无 digest 但内容被篡改）→ 退出码 40
#   场景 9  主仓库失败 → 不执行 GitHub 步骤（退出码 1，无 GitHub 请求/推送）
#   场景 10 GitHub 失败 → 退出码 40，报告“主仓库已成功、GitHub 失败”，不回滚（无 DELETE）
#   场景 11 重定向策略：evil 主机 / http 降级 / '@' 旁路 / 相对 Location / 二次重定向 / 内容篡改
#           → 全部中止且从不请求恶意主机；objects.githubusercontent.com 与 release-assets 均放行
#   场景 12 前置检查：缺 GitHub 远端 / 远端 url 非 github.com/mfreceiver/alpha-gps / 缺凭据 →
#           退出码 1 且尚未发起任何请求；--no-github 可绕过；自定义远端名
#   场景 13 GitHub 远端 tag 已存在于不同 commit → 中止；同 commit → 跳过
#   场景 14 分支推送：--github-tag-only 只推 tag；--github-push-branch 同时推当前分支
#   场景 15 选项互斥/适用性校验与 --help
#   场景 16 --publish-github-only 前置：本地 tag 缺失/归档 sha 被改/tag 不在 HEAD/脏树 → 退出码 1
#   场景 17 主仓库无 sha256 字段分支（下载强校验）仍工作
#   场景 18 脚本变异验证：去掉重定向白名单校验 / 改成跟随重定向 / 放过第二跳 3xx（第二跳 302 带着与本地
#           完全相同的字节也必须退出 40，不靠哈希兜底）→ 必须检出
#   场景 19 推送目的地校验：规范化函数、URL 净化、真实 git 配置矩阵（split fetch/push、pushurl、insteadOf、
#           pushInsteadOf、多 URL、别名+字面量改写、env 注入配置）+ 库级变异
#   场景 20 脚本级目的地：上述绕过 → 退出码 1 且零请求零传输；传输钉定到 URL 字面量（wire 日志不变量、
#           followTags=false、无 force）；中途被改 pushurl → 40；生产默认值不可被环境变量绕过 + 变异
set -uo pipefail

HERE=$(cd "$(dirname "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
LIB=$HERE/release_lib.sh
# shellcheck source=release_lib.sh
source "$LIB"

TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT

PASS=0; FAILED=0
check() { # check <desc> <expr...>
  local desc=$1; shift
  if "$@"; then PASS=$((PASS+1)); echo "ok   - $desc"
  else FAILED=$((FAILED+1)); echo "FAIL - $desc"; fi
}

export ALPHAGPS_PRIMARY_HOST=forge.example.com:443
export ALPHAGPS_PRIMARY_REPO=owner/alphagps
export ALPHAGPS_PRIMARY_REMOTE=primary
unset ALPHAGPS_GITHUB_REMOTE

# ══ 纯函数 ═════════════════════════════════════════════════════════════════
allow() { # allow <kind> <url>
  local k=$1 u=$2
  rl_gh_url_allowed "$k" "$u" >/dev/null 2>"$TMP/err"; local rc=$?
  check "放行[$k]: $u" test "$rc" -eq 0
}
deny() { # deny <kind> <url>
  local k=$1 u=$2
  rl_gh_url_allowed "$k" "$u" >/dev/null 2>"$TMP/err"; local rc=$?
  check "拒绝[$k]: $(printf '%q' "$u")" test "$rc" -ne 0
  check "拒绝[$k]: stderr 含 FAIL" grep -q FAIL "$TMP/err"
}

echo "== 场景 1: rl_gh_url_allowed 合法主机 =="
allow api "https://api.github.com/repos/mfreceiver/alpha-gps/releases"
allow upload "https://uploads.github.com/repos/mfreceiver/alpha-gps/releases/1/assets?name=x.apk"
allow download "https://github.com/mfreceiver/alpha-gps/releases/download/v1/x.apk"
allow download "https://objects.githubusercontent.com/github-production-release-asset-2e65be/1/abc?X-Amz-Signature=s&x=1"
allow download "https://release-assets.githubusercontent.com/github-production-release-asset/1/abc?sp=r"

echo "== 场景 2: rl_gh_url_allowed 拒绝清单 =="
deny api "https://api.github.com@evil.com/x"
deny api "https://evil.com@api.github.com/x"
deny api "https://api.github.com:443@evil.com/x"
deny api "https://evil.api.github.com/x"
deny api "https://api.github.com.evil.com/x"
deny api "https://api.github.com:443/x"
deny api "https://api.github.com:8443/x"
deny api "https://API.GITHUB.COM/x"
deny api "http://api.github.com/x"
deny api "HTTPS://api.github.com/x"
deny api "ftp://api.github.com/x"
deny api "//api.github.com/x"
deny api "/relative/path"
deny api "https://api.github.com./x"
deny api "https://api.github.com/x#frag"
deny api "https://api.github.com/x"$'\n'
deny api "https://api.github.com/x"$'\r'
deny api "https://api.github.com/"$'\t'"x"
deny api "https://api.github.com/ x"
deny api "https://api.github.com\\@evil.com/x"
deny api ""
deny api "https://uploads.github.com/x"            # 类别串用：upload 主机不是 api
deny upload "https://api.github.com/x"             # 类别串用：api 主机不是 upload
deny upload "https://github.com/x"
deny api "https://objects.githubusercontent.com/x"
deny download "https://api.github.com/x"           # 下载白名单不含 api/uploads
deny download "https://uploads.github.com/x"
deny download "https://githubusercontent.com/x"    # 裸域不在白名单
deny download "https://raw.githubusercontent.com/x"
deny download "https://evilobjects.githubusercontent.com/x"
deny download "https://objects.githubusercontent.com.evil.com/x"
deny download "https://evil.objects.githubusercontent.com/x"
deny download "https://github.com.evil.com/x"
deny download "https://evil-github.com/x"
deny download "https://github.com:443/x"
deny download "https://release-assets.githubusercontent.com:8443/x"
deny download "https://gıthub.com/x"               # 非 ASCII 同形主机
rl_gh_url_allowed bogus "https://github.com/x" >/dev/null 2>"$TMP/err"; rc=$?
check "未知类别 → 非零" test "$rc" -ne 0

echo "== 场景 3: 白名单变异验证 =="
MUTLIB=$TMP/lib_mut.sh
sed 's/if p\.netloc not in hosts:/if not any(p.netloc.endswith(h) for h in hosts):/' "$LIB" > "$MUTLIB"
bash -c 'source "$1"; rl_gh_url_allowed download "https://evil.objects.githubusercontent.com/x"' _ "$MUTLIB" >/dev/null 2>&1; rc=$?
check "变异A（netloc 后缀匹配）放行子域主机（rc=0 才证明用例有效）" test "$rc" -eq 0
sed -e 's/if not url\.startswith("https:\/\/"):/if False:/' -e 's/if p\.scheme != "https":/if False:/' "$LIB" > "$MUTLIB"
bash -c 'source "$1"; rl_gh_url_allowed download "http://github.com/x"' _ "$MUTLIB" >/dev/null 2>&1; rc=$?
check "变异B（去掉 https 校验）放行 http 降级（rc=0）" test "$rc" -eq 0
sed 's/if "@" in url:/if False:/' "$LIB" > "$MUTLIB"
bash -c 'source "$1"; rl_gh_url_allowed api "https://api.github.com@evil.com/x"' _ "$MUTLIB" >/dev/null 2>&1; rc=$?
check "仅删 '@' 检查时 netloc 精确匹配仍拦截旁路（纵深防御）" test "$rc" -ne 0
sed -e 's/if "@" in url:/if False:/' -e 's/if p\.username is not None or p\.password is not None:/if False:/' -e 's/if p\.netloc not in hosts:/if not p.netloc.startswith(hosts[0]):/' "$LIB" > "$MUTLIB"
bash -c 'source "$1"; rl_gh_url_allowed api "https://api.github.com@evil.com/x"' _ "$MUTLIB" >/dev/null 2>&1; rc=$?
check "变异C（删 '@'/userinfo 检查 + netloc 前缀匹配）放行 userinfo 旁路（rc=0）" test "$rc" -eq 0
rl_gh_url_allowed download "http://github.com/x" >/dev/null 2>&1; rc=$?
check "真实库仍拒绝 http 降级" test "$rc" -ne 0

echo "== 场景 4: rl_gh_location_from_headers =="
hdr() { printf "$1" > "$TMP/h.txt"; }
hdr 'HTTP/2 302 \r\nlocation: https://release-assets.githubusercontent.com/a?b=1\r\ncontent-length: 0\r\n\r\n'
OUT=$(rl_gh_location_from_headers "$TMP/h.txt" 2>/dev/null); rc=$?
check "小写 location 头" test "$rc" -eq 0 -a "$OUT" == "https://release-assets.githubusercontent.com/a?b=1"
hdr 'HTTP/1.1 302 Found\r\nLOCATION: https://x.example/p\r\n\r\n'
OUT=$(rl_gh_location_from_headers "$TMP/h.txt" 2>/dev/null); rc=$?
check "大写 LOCATION 头（大小写不敏感）" test "$rc" -eq 0 -a "$OUT" == "https://x.example/p"
hdr 'HTTP/2 302 \r\ncontent-length: 0\r\n\r\n'
rl_gh_location_from_headers "$TMP/h.txt" >/dev/null 2>"$TMP/err"; rc=$?
check "无 Location → 非零" test "$rc" -ne 0
hdr 'HTTP/2 302 \r\nlocation: https://a.example/1\r\nlocation: https://b.example/2\r\n\r\n'
rl_gh_location_from_headers "$TMP/h.txt" >/dev/null 2>"$TMP/err"; rc=$?
check "两个 Location → 非零" test "$rc" -ne 0
hdr 'HTTP/1.1 100 Continue\r\nlocation: https://stale.example/\r\n\r\nHTTP/2 302 \r\nlocation: https://last.example/z\r\n\r\n'
OUT=$(rl_gh_location_from_headers "$TMP/h.txt" 2>/dev/null); rc=$?
check "多响应块只看最后一块" test "$rc" -eq 0 -a "$OUT" == "https://last.example/z"
hdr 'location: https://a.example/\r\n\r\n'
rl_gh_location_from_headers "$TMP/h.txt" >/dev/null 2>"$TMP/err"; rc=$?
check "无 HTTP 状态行 → 非零" test "$rc" -ne 0
rl_gh_location_from_headers "$TMP/nonexistent" >/dev/null 2>"$TMP/err"; rc=$?
check "头文件缺失 → 非零" test "$rc" -ne 0

echo "== 场景 5: remote url / curl 配置 / release JSON =="
for u in "https://github.com/mfreceiver/alpha-gps" "https://github.com/mfreceiver/alpha-gps.git"; do
  rl_gh_remote_url_ok "$u"; check "远端 url 放行: $u" test $? -eq 0
done
for u in "https://github.com/mfreceiver/alpha-gps.git/" "http://github.com/mfreceiver/alpha-gps.git" \
         "git@github.com:mfreceiver/alpha-gps.git" "https://github.com.evil.com/mfreceiver/alpha-gps.git" \
         "https://github.com/Saschl/alpha-gps.git" "https://github.com/mfreceiver/alpha-gps2.git" \
         "https://user@github.com/mfreceiver/alpha-gps.git" "/tmp/some/local.git" "" "https://github.com/mfreceiver/alpha-gps.git "; do
  rl_gh_remote_url_ok "$u"; check "远端 url 拒绝: '$u'" test $? -ne 0
done
OUT=$(printf '%s' 'ghp_AbC123-_.x' | rl_gh_curl_config 2>/dev/null); rc=$?
check "合法 token 生成配置（含 Authorization: Bearer）" test "$rc" -eq 0 -a "$(grep -c 'Authorization: Bearer ghp_AbC123-_.x' <<<"$OUT")" -eq 1
for t in $'abc\nX-Evil: 1' 'abc def' 'abc"def' '' 'abc;def' $'abc\r'; do
  printf '%s' "$t" | rl_gh_curl_config >/dev/null 2>"$TMP/err"; rc=$?
  check "非法 token 形态拒绝: $(printf '%q' "$t")" test "$rc" -ne 0
done
cat > "$TMP/rel.json" <<'JSON'
{"id": 21, "assets": [
 {"name": "a.apk", "size": 5, "digest": "sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "state": "uploaded", "browser_download_url": "https://github.com/o/r/releases/download/v1/a.apk"},
 {"name": "b.apk", "size": 6, "digest": null, "state": "starter", "browser_download_url": "https://github.com/o/r/releases/download/v1/b.apk"}]}
JSON
OUT=$(rl_gh_release_field "$TMP/rel.json" id 2>/dev/null); check "release id=21" test "$OUT" == "21"
OUT=$(rl_gh_release_field "$TMP/rel.json" assets 2>/dev/null)
check "assets: digest 小写化并取 sha256 / null → '-' / state 保留" \
  test "$(head -1 <<<"$OUT" | cut -f3)" == "$(printf 'a%.0s' $(seq 1 64))" -a "$(sed -n 2p <<<"$OUT" | cut -f3,4)" == $'-\tstarter'
echo '{"id": "21"}' > "$TMP/rel2.json"; rl_gh_release_field "$TMP/rel2.json" id >/dev/null 2>&1; check "id 非整数 → 非零" test $? -ne 0
echo '{"id": 0}' > "$TMP/rel2.json"; rl_gh_release_field "$TMP/rel2.json" id >/dev/null 2>&1; check "id=0 → 非零" test $? -ne 0
echo 'not json' > "$TMP/rel2.json"; rl_gh_release_field "$TMP/rel2.json" assets >/dev/null 2>&1; check "非 JSON → 非零" test $? -ne 0
echo '{"assets": {"a":1}}' > "$TMP/rel2.json"; rl_gh_release_field "$TMP/rel2.json" assets >/dev/null 2>&1; check "assets 非列表 → 非零" test $? -ne 0

# ══ 接线：沙箱 + fake curl ═══════════════════════════════════════════════════
SHIM=$TMP/shim; mkdir -p "$SHIM"
cat > "$SHIM/curl" <<'PYEOF'
#!/usr/bin/env python3
# fake curl：模拟主仓库 REST / api.github.com / uploads.github.com / github.com 下载 302 / CDN。
import hashlib, json, os, re, sys
from urllib.parse import urlsplit, parse_qs

D = os.environ["FAKE_DIR"]
LOG = os.environ["FAKE_LOG"]
ST = os.path.join(D, "state.json")
argv = sys.argv[1:]
VALOPTS = {"-o", "-w", "-X", "-d", "-H", "-m", "-D", "-K", "--data-binary", "--proto",
           "--proto-redir", "--max-filesize", "--url", "-F", "-u", "--user"}
o, flags, url, i = {}, set(), None, 0
while i < len(argv):
    a = argv[i]
    if a in VALOPTS:
        o.setdefault(a, []).append(argv[i + 1]); i += 2
    elif a.startswith("-"):
        flags.add(a); i += 1
    else:
        url = a; i += 1
if "--url" in o:
    url = o["--url"][-1]
with open(os.path.join(D, "argv.log"), "a") as f:
    f.write(" ".join(argv) + "\n")
cfg = ""
if o.get("-K") == ["-"]:
    cfg = sys.stdin.read()
follow = bool(flags & {"-L", "--location"})
state = json.load(open(ST)) if os.path.exists(ST) else {}
state.setdefault("primary", None); state.setdefault("gh", None)
method = (o.get("-X") or [None])[-1] or ("POST" if ({"-d", "--data-binary", "-F"} & set(o)) else "GET")

def log(u, m):
    with open(LOG, "a") as f:
        f.write(json.dumps({"method": m, "url": u, "cfg": bool(cfg), "auth": "Authorization: Bearer" in cfg,
                            "follow": follow, "no_location": "--no-location" in flags,
                            "proto_redir": (o.get("--proto-redir") or [""])[-1]}) + "\n")

def body_path():
    for k in ("--data-binary", "-F"):
        for v in o.get(k, []):
            mm = re.search(r"@(.+)$", v)
            if mm:
                return mm.group(1)
    return None

def body_file():
    for v in o.get("-d", []):
        if v.startswith("@"):
            return v[1:]
    raise SystemExit("no body")

def j(obj): return json.dumps(obj).encode()

def prel(r):
    nosha = os.environ.get("FAKE_PRIMARY_NOSHA")
    return {"id": r["id"], "assets": [
        {"name": a["name"], "size": a["size"], "sha256": None if nosha else a["sha"],
         "browser_download_url": "https://forge.example.com:443/owner/alphagps/releases/download/%s/%s" % (os.environ.get("FAKE_TAG", "v0.1.2"), a["name"])}
        for a in r["assets"]]}

def grel(r):
    dm = os.environ.get("FAKE_GH_DIGEST", "")
    out = []
    for a in r["assets"]:
        d = {"name": a["name"], "size": a["size"], "state": a.get("state", "uploaded"),
             "browser_download_url": "https://github.com/mfreceiver/alpha-gps/releases/download/%s/%s" % (r["tag"], a["name"])}
        if os.environ.get("FAKE_GH_BAD_URL"):
            d["browser_download_url"] = "https://evil.example.org/mfreceiver/alpha-gps/releases/download/%s/%s" % (r["tag"], a["name"])
        if dm == "none":
            pass
        elif dm == "wrong":
            d["digest"] = "sha256:" + "0" * 64
        else:
            d["digest"] = "sha256:" + a["sha"]
        out.append(d)
    return {"id": r["id"], "tag_name": r["tag"], "assets": out}

def handle(u, m):
    sp = urlsplit(u); host, path = sp.netloc, sp.path
    q = parse_qs(sp.query)
    env = os.environ
    if host == "forge.example.com:443":
        api = "/api/v1/repos/owner/alphagps"
        if path.startswith("/owner/alphagps/releases/download/"):
            name = path.rsplit("/", 1)[1]
            p = os.path.join(D, "pblobs", name)
            return (200, {}, open(p, "rb").read()) if os.path.exists(p) else (404, {}, b"")
        if re.fullmatch(api + r"/releases/tags/(.+)", path) and m == "GET":
            r = state["primary"]
            return (200, {}, j(prel(r))) if r else (404, {}, b"{}")
        if path == api + "/releases" and m == "POST":
            if env.get("FAKE_PRIMARY_HOOK"):
                os.system(env["FAKE_PRIMARY_HOOK"])   # 测试钩子：主仓库发布过程中改动本机配置
            if env.get("FAKE_PRIMARY_FAIL") == "create":
                return 500, {}, b'{"message":"boom"}'
            state["primary"] = {"id": 11, "assets": []}
            return 201, {}, j(prel(state["primary"]))
        if path == api + "/releases/11" and m == "GET":
            return 200, {}, j(prel(state["primary"]))
        if path == api + "/releases/11/assets" and m == "POST":
            p = body_path(); name = q["name"][0]
            os.makedirs(os.path.join(D, "pblobs"), exist_ok=True)
            data = open(p, "rb").read()
            open(os.path.join(D, "pblobs", name), "wb").write(data)
            state["primary"]["assets"].append({"name": name, "size": len(data), "sha": hashlib.sha256(data).hexdigest()})
            return 201, {}, b"{}"
        return 599, {}, b"UNHANDLED primary " + u.encode()
    GH = "/repos/mfreceiver/alpha-gps"
    if host == "api.github.com":
        if re.fullmatch(GH + r"/releases/tags/(.+)", path) and m == "GET":
            if env.get("FAKE_GH_TAG_STATUS"):
                return int(env["FAKE_GH_TAG_STATUS"]), {}, b'{"message":"x"}'
            r = state["gh"]
            return (200, {}, j(grel(r))) if r else (404, {}, b'{"message":"Not Found"}')
        if path == GH + "/releases" and m == "POST":
            if env.get("FAKE_GH_FAIL") == "create":
                return 422, {}, b'{"message":"Validation Failed"}'
            state["gh"] = {"id": 21, "tag": json.loads(open(body_file(), "rb").read())["tag_name"], "assets": []}
            return 201, {}, j(grel(state["gh"]))
        if path == GH + "/releases/21" and m == "GET":
            return 200, {}, j(grel(state["gh"]))
        return 599, {}, b"UNHANDLED api " + u.encode()
    if host == "uploads.github.com":
        if path == GH + "/releases/21/assets" and m == "POST":
            if env.get("FAKE_GH_FAIL") == "upload":
                return 500, {}, b'{"message":"upload boom"}'
            p = body_path(); name = q["name"][0]
            os.makedirs(os.path.join(D, "blobs"), exist_ok=True)
            data = open(p, "rb").read()
            open(os.path.join(D, "blobs", name), "wb").write(data)
            state["gh"]["assets"].append({"name": name, "size": len(data), "sha": hashlib.sha256(data).hexdigest(), "state": "uploaded"})
            return 201, {}, b"{}"
        return 599, {}, b"UNHANDLED upload " + u.encode()
    if host == "github.com":
        mm = re.fullmatch(r"/mfreceiver/alpha-gps/releases/download/([^/]+)/([^/]+)", path)
        if mm:
            if env.get("FAKE_GH_REDIR_LOCATION"):
                loc = env["FAKE_GH_REDIR_LOCATION"]
            else:
                loc = "https://%s/github-production-release-asset/1/%s?X-Amz-Signature=zzz" % (
                    env.get("FAKE_GH_REDIR_HOST", "release-assets.githubusercontent.com"), mm.group(2))
            return 302, {"location": loc}, b""
        return 599, {}, b"UNHANDLED github.com " + u.encode()
    if path.startswith("/github-production-release-asset/"):   # CDN（任意主机都会服务，便于检出误请求）
        name = path.rsplit("/", 1)[1]
        p = os.path.join(D, "blobs", name)
        if env.get("FAKE_GH_DOUBLE") and not q.get("again"):
            # FAKE_GH_DOUBLE_BODY：第二跳 302 同时携带与本地完全相同的字节（哈希兜底无法区分，只能靠状态码）
            same = open(p, "rb").read() if (env.get("FAKE_GH_DOUBLE_BODY") and os.path.exists(p)) else b""
            return 302, {"location": "https://%s%s?again=1" % (host, path)}, same
        if not os.path.exists(p):
            return 404, {}, b""
        data = open(p, "rb").read()
        if env.get("FAKE_GH_TAMPER"):
            data = bytes([data[0] ^ 1]) + data[1:]
        return 200, {}, data
    return 599, {}, b"UNHANDLED " + u.encode()

cur, m = url, method
log(cur, m)
code, hdrs, body = handle(cur, m)
hops = 0
while follow and 300 <= code < 400 and "location" in hdrs and hops < 5:   # -L：跟随并记录（供检出违规）
    cur, m = hdrs["location"], "GET"; log(cur, m); code, hdrs, body = handle(cur, m); hops += 1
json.dump(state, open(ST, "w"))
if "-D" in o:
    with open(o["-D"][-1], "wb") as f:
        f.write(("HTTP/2 %d \r\n" % code + "".join("%s: %s\r\n" % kv for kv in hdrs.items()) + "\r\n").encode())
out = (o.get("-o") or [None])[-1]
if out and out != "/dev/null":
    with open(out, "wb") as f:
        f.write(body)
if "-w" in o and "%{http_code}" in o["-w"][-1]:
    sys.stdout.write(str(code))
sys.exit(0 if code != 599 else 6)
PYEOF
chmod +x "$SHIM/curl"

# fake git：传输层（ls-remote/push/fetch/pull/clone，且非 --get-url）按目的地处理，其余命令全部透传。
#   目的地 == 公开规范 URL 字面量 → 换成沙箱本地 bare（FAKE_GH_BARE）；
#   目的地是 github 远端名(github/gh)或含 '://' '@' 的其它网络地址 → 拒绝（exit 97）并记 BLOCKED（测试绝不触网）；
#   其余（primary 远端名/本地路径）原样透传。每次传输调用把原始 argv 记入 $FAKE_DIR/gitwire.log。
cat > "$SHIM/git" <<'GITSHIM'
#!/usr/bin/env bash
real=${FAKE_REAL_GIT:?}
CANON=https://github.com/mfreceiver/alpha-gps.git
args=("$@"); i=0; sub=""
while (( i < ${#args[@]} )); do
  case "${args[i]}" in
    -c|-C) i=$((i+2)) ;;
    -*) i=$((i+1)) ;;
    *) sub=${args[i]}; break ;;
  esac
done
case "$sub" in
  ls-remote|push|fetch|pull|clone)
    getu=0; for a in "${args[@]}"; do [[ "$a" == --get-url ]] && getu=1; done
    if (( ! getu )); then
      j=$((i+1)); di=-1
      while (( j < ${#args[@]} )); do
        case "${args[j]}" in -*) ;; *) di=$j; break ;; esac
        j=$((j+1))
      done
      dest=""; (( di >= 0 )) && dest=${args[di]}
      echo "ARGV: $*" >> "${FAKE_DIR:?}/gitwire.log"
      if [[ "$dest" == "$CANON" ]]; then args[di]=${FAKE_GH_BARE:?}
      else
        case "$dest" in
          github|gh|*://*|*@*) echo "BLOCKED $sub dest=$dest" >> "$FAKE_DIR/gitwire.log"
                               echo "fake git: 拒绝非钉定/网络传输目的地: $dest" >&2; exit 97 ;;
        esac
      fi
    fi ;;
esac
exec "$real" "${args[@]}"
GITSHIM
chmod +x "$SHIM/git"
REAL_GIT=$(command -v git)

cat > "$TMP/logq.py" <<'PYEOF'
import json, sys
from urllib.parse import urlsplit
for line in open(sys.argv[1]):
    r = json.loads(line); u = urlsplit(r["url"])
    print("%s %s %s auth=%d cfg=%d follow=%d noloc=%d pr=%s" % (r["method"], u.netloc, u.path, r["auth"], r["cfg"],
          r["follow"], r["no_location"], r["proto_redir"]))
PYEOF

FIXTURE_CODE=3; FIXTURE_NAME=0.1.2; TAGV=v0.1.2; APKN=alpha-gps-0.1.2.apk
GH_TOKEN_VALUE=ghp_FAKEtoken123

make_sb() { # $1=名称 → $TMP/$1（沙箱仓库）；同时建 $TMP/$1-primary.git 与 $TMP/$1-github.git
  local SB=$TMP/$1
  rm -rf -- "$SB" "$SB-primary.git" "$SB-github.git"
  mkdir -p "$SB/scripts" "$SB/app" "$SB/home/jdk/bin" "$SB/home/.gradle" "$SB/home/.config/alphagps/signing" \
           "$SB/sdk/build-tools/33.0.0" "$SB/release"
  cp -- "$HERE/release_build.sh" "$HERE/release_version_inject.sh" "$HERE/release_lib.sh" "$HERE/release_assert_apk.sh" "$SB/scripts/"
  printf 'org.gradle.jvmargs=-Xmx1m\nalphagps.versionCode=%s\n' "$FIXTURE_CODE" > "$SB/gradle.properties"
  printf '        applicationId = "cn.vectory.alphagps"\n        versionName = "%s"\n' "$FIXTURE_NAME" > "$SB/app/build.gradle.kts"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$SB/home/jdk/bin/java"; chmod +x "$SB/home/jdk/bin/java"
  local pin sha_col
  pin=$(sed -nE 's/^PINNED_CERT_SHA256="([0-9a-fA-F]+)"$/\1/p' "$SB/scripts/release_build.sh")
  [[ ${#pin} -eq 64 ]] || { echo "test-setup: 无法提取 PINNED_CERT_SHA256"; exit 2; }
  sha_col=$(sed 's/../&:/g; s/:$//' <<<"$pin")
  printf '#!/usr/bin/env bash\nif [[ "${1:-}" == -list ]]; then printf "  SHA256: %%s\\n" "%s"; exit 0; fi\nexit 1\n' \
    "$sha_col" > "$SB/home/jdk/bin/keytool"; chmod +x "$SB/home/jdk/bin/keytool"
  printf '#!/usr/bin/env bash\nif [[ "${1:-} ${2:-}" == "verify --print-certs" ]]; then printf "Signer #1 certificate DN: CN=release\\nSigner #1 certificate SHA-256 digest: %s\\n" "%s"; exit 0; fi\n[[ "${1:-}" == verify ]] && exit 0\nexit 1\n' \
    "$pin" > "$SB/sdk/build-tools/33.0.0/apksigner"; chmod +x "$SB/sdk/build-tools/33.0.0/apksigner"
  cat > "$SB/sdk/build-tools/33.0.0/aapt" <<'AAPT'
#!/usr/bin/env bash
if [[ "${1:-} ${2:-}" == "dump badging" ]]; then
  printf "package: name='cn.vectory.alphagps' versionCode='%s' versionName='v%s'\n" "${AAPT_CODE:?}" "${AAPT_VNAME:?}"
  printf "uses-permission: name='android.permission.INTERNET'\n"
fi
exit 0
AAPT
  chmod +x "$SB/sdk/build-tools/33.0.0/aapt"
  printf 'sdk.dir=%s\n' "$SB/sdk" > "$SB/local.properties"
  printf 'DSH_KEYSTORE_PASSWORD=x\nDSH_KEY_PASSWORD=x\n' > "$SB/home/.gradle/gradle.properties"
  printf 'placeholder\n' > "$SB/home/.config/alphagps/signing/release.jks"
  printf 'https://ghuser:%s@github.com\nhttps://puser:ppw@forge.example.com:443\n' "$GH_TOKEN_VALUE" > "$SB/home/.git-credentials"
  printf '[credential]\n\thelper = store --file=%s/home/.git-credentials\n' "$SB" > "$SB/home/.gitconfig"
  ( cd "$SB" && git init -q && git add -A . && git -c user.email=t@t -c user.name=t commit -qm init && git tag v0.1.1 )
  local CR=assets/composeResources/cameragps.sharednew.generated.resources
  python3 -c 'import zipfile; z=zipfile.ZipFile("'"$SB"'/release/'"$APKN"'","w"); z.writestr("AndroidManifest.xml",""); z.writestr("lib/arm64-v8a/libsqliteJni.so",b"\x7fELF"); z.writestr("'"$CR"'/values/strings.cvr",b"en"); z.writestr("'"$CR"'/values-zh-rCN/strings.cvr",b"zh")'
  ( cd "$SB" && sha256sum "release/$APKN" > release/SHA256SUMS && git add -A . \
      && git -c user.email=t@t -c user.name=t commit -qm archive && git tag "$TAGV" )
  git init -q --bare "$SB-primary.git"; git init -q --bare "$SB-github.git"
  ( cd "$SB" && git remote add primary "$SB-primary.git" \
      && git remote add github https://github.com/mfreceiver/alpha-gps.git )   # 真实公开 URL；传输由 fake git 垫片换成本地 bare
  LSHA=$(sha256sum "$SB/release/$APKN" | awk '{print $1}')
}

fake_reset() { FAKE_DIR=$TMP/fake; rm -rf -- "$FAKE_DIR"; mkdir -p "$FAKE_DIR"; FAKE_LOG=$FAKE_DIR/req.jsonl; : > "$FAKE_LOG"; export FAKE_DIR FAKE_LOG; }
fake_clear_env() { unset FAKE_GH_FAIL FAKE_PRIMARY_FAIL FAKE_PRIMARY_NOSHA FAKE_GH_DIGEST FAKE_GH_REDIR_HOST FAKE_GH_REDIR_LOCATION FAKE_GH_DOUBLE FAKE_GH_TAMPER FAKE_GH_BAD_URL FAKE_GH_TAG_STATUS FAKE_GH_DOUBLE_BODY FAKE_PRIMARY_HOOK; }

rb() { # rb <SB> <输出文件> <release_build.sh 参数...>   （FAKE_* 由调用方 export 或前缀传入；NOPRIMARY=1 去掉主仓库配置）
  local SB=$1 out=$2; shift 2
  : > "$FAKE_LOG"; : > "$FAKE_DIR/gitwire.log"
  local -a envu=()
  [[ -n "${NOPRIMARY:-}" ]] && envu=(-u ALPHAGPS_PRIMARY_HOST -u ALPHAGPS_PRIMARY_REPO -u ALPHAGPS_PRIMARY_REMOTE)
  [[ -n "${RB_SHELLOPTS:-}" ]] && envu+=(SHELLOPTS=xtrace)   # 以 SHELLOPTS=xtrace 启动（含子 shell 继承）
  # RB_BASH_FLAGS（如 -x）：用 `bash -x scripts/release_build.sh` 启动
  # shellcheck disable=SC2086
  ( cd "$SB" && env "${envu[@]}" PATH="$SHIM:$SB/home/jdk/bin:$PATH" HOME="$SB/home" JAVA_HOME="$SB/home/jdk" \
      FAKE_GH_BARE="$SB-github.git" FAKE_REAL_GIT="$REAL_GIT" \
      DSH_KEYSTORE_PASSWORD=x DSH_KEY_PASSWORD=x AAPT_CODE=$FIXTURE_CODE AAPT_VNAME=$FIXTURE_NAME \
      timeout 120 bash ${RB_BASH_FLAGS:-} scripts/release_build.sh 0.1.2 "$@" ) > "$out" 2>&1
}
summ() { python3 "$TMP/logq.py" "$FAKE_LOG"; }
has_sum() { summ | grep -q -E -- "$1"; }
no_sum() { ! summ | grep -q -E -- "$1"; }
gh_tags() { git -C "$1" for-each-ref --format='%(refname)' refs/tags; }
gh_heads() { git -C "$1" for-each-ref --format='%(refname)' refs/heads; }
no_evil_requests() { ! grep -q -E 'evil|attacker' "$FAKE_LOG"; }
retag() { ( cd "$1" && git -c user.email=t@t -c user.name=t commit -qam mutate && git tag -d "$TAGV" >/dev/null && git tag "$TAGV" ); }

echo "== 场景 6: --publish-only 主仓库→GitHub 全链成功 =="
make_sb s6; fake_reset; fake_clear_env
rb "$TMP/s6" "$TMP/s6.log" --publish-only; RC=$?
check "退出码 0" test "$RC" -eq 0
summ > "$TMP/s6.sum"
check "主仓库发布先于 GitHub（POST 主仓库 release 行号 < 首个 api.github.com 行号）" \
  awk '/^POST forge.example.com:443 \/api\/v1\/repos\/owner\/alphagps\/releases /{p=NR} /api.github.com/ && !g{g=NR} END{exit !(p && g && p<g)}' "$TMP/s6.sum"
check "GitHub: 建 release（POST）" grep -q '^POST api.github.com /repos/mfreceiver/alpha-gps/releases ' "$TMP/s6.sum"
check "GitHub: 上传到 uploads.github.com" grep -q '^POST uploads.github.com /repos/mfreceiver/alpha-gps/releases/21/assets ' "$TMP/s6.sum"
check "GitHub: 下载首跳 github.com + 第二跳 release-assets.githubusercontent.com（共 2 次下载）" \
  test "$(grep -c -E '^GET (github.com /mfreceiver/alpha-gps/releases/download/|release-assets.githubusercontent.com /github-production-release-asset/)' "$TMP/s6.sum")" -eq 2
check "带凭据请求只发往 api.github.com / uploads.github.com" \
  bash -c '! grep -E "auth=1" "$1" | grep -v -E "^[A-Z]+ (api|uploads)\.github\.com " | grep -q -E "github"' _ "$TMP/s6.sum"
check "下载两跳均无凭据配置（cfg=0）" bash -c '! grep -E "^GET (github.com|release-assets)" "$1" | grep -q "cfg=1"' _ "$TMP/s6.sum"
grep -v 'forge.example.com' "$TMP/s6.sum" > "$TMP/s6.gh.sum"
check "GitHub 侧请求有 ≥6 条（防空过滤）" test "$(wc -l < "$TMP/s6.gh.sum")" -ge 6
check "GitHub 侧所有请求 --no-location 且无 -L（follow=0 noloc=1）" bash -c '! grep -q -E "follow=1|noloc=0" "$1"' _ "$TMP/s6.gh.sum"
check "GitHub 侧所有请求 proto-redir 均为 =https（未放开）" bash -c '! grep -v "pr==https" "$1" | grep -q .' _ "$TMP/s6.gh.sum"
check "token 不在任何 curl argv 中" bash -c '! grep -q "$2" "$1/argv.log"' _ "$FAKE_DIR" "$GH_TOKEN_VALUE"
check "token 不在脚本输出中" bash -c '! grep -q "$2" "$1"' _ "$TMP/s6.log" "$GH_TOKEN_VALUE"
check "GitHub 远端只有 tag（默认不推分支）" test "$(gh_tags "$TMP/s6-github.git")" == "refs/tags/$TAGV" -a -z "$(gh_heads "$TMP/s6-github.git")"
check "主仓库远端收到分支与 tag" test -n "$(gh_heads "$TMP/s6-primary.git")" -a "$(gh_tags "$TMP/s6-primary.git")" == "refs/tags/$TAGV"
check "输出含『历史对公开可见』提示" grep -q "历史对公开可见" "$TMP/s6.log"
check "输出含 GitHub release 公开链接" grep -q "https://github.com/mfreceiver/alpha-gps/releases/tag/$TAGV" "$TMP/s6.log"
check "输出含资产对账通过（GitHub）" grep -q "GitHub 资产对账通过" "$TMP/s6.log"
check "GitHub 上存的资产与本地 sha 一致" test "$(sha256sum "$FAKE_DIR/blobs/$APKN" | awk '{print $1}')" == "$LSHA"

echo "== 场景 7: --publish-github-only 幂等重跑（同 sha 跳过；不需要主仓库配置） =="
NOPRIMARY=1 rb "$TMP/s6" "$TMP/s7.log" --publish-github-only; RC=$?
check "退出码 0" test "$RC" -eq 0
summ > "$TMP/s7.sum"
check "无任何主仓库请求" bash -c '! grep -q "forge.example.com" "$1"' _ "$TMP/s7.sum"
check "未再次上传（无 uploads.github.com 请求）" bash -c '! grep -q "uploads.github.com" "$1"' _ "$TMP/s7.sum"
check "未再次建 release（复用，无 POST api.github.com）" bash -c '! grep -q "^POST api.github.com" "$1"' _ "$TMP/s7.sum"
check "日志说明跳过上传并对账通过" bash -c 'grep -q "跳过上传" "$1" && grep -q "GitHub 资产对账通过" "$1"' _ "$TMP/s7.log"
check "日志说明远端 tag 同 commit 跳过 push" grep -q "跳过 tag push" "$TMP/s7.log"
check "输出为 --publish-github-only 完成" grep -q -- "--publish-github-only 完成" "$TMP/s7.log"

echo "== 场景 8: 同名资产不同内容 → 中止（退出码 40，不上传） =="
seed_gh_asset() { # $1=same|diff  $2=可选覆盖 size
  rm -rf "$FAKE_DIR/blobs" "$FAKE_DIR/state.json"; mkdir -p "$FAKE_DIR/blobs"
  local f=$FAKE_DIR/blobs/$APKN
  cp "$TMP/s6/release/$APKN" "$f"
  if [[ "$1" != same ]]; then printf '\x00' | dd of="$f" bs=1 seek=0 count=1 conv=notrunc 2>/dev/null; fi
  python3 - "$f" "$FAKE_DIR/state.json" "${2:-}" <<'PY'
import hashlib, json, sys
data = open(sys.argv[1], "rb").read()
size = int(sys.argv[3]) if sys.argv[3] else len(data)
json.dump({"primary": None, "gh": {"id": 21, "tag": "v0.1.2", "assets": [
    {"name": "alpha-gps-0.1.2.apk", "size": size, "sha": hashlib.sha256(data).hexdigest(), "state": "uploaded"}]}},
    open(sys.argv[2], "w"))
PY
}
fake_reset; fake_clear_env
seed_gh_asset diff 999999
NOPRIMARY=1 rb "$TMP/s6" "$TMP/s8a.log" --publish-github-only; RC=$?
check "size 不同 → 退出码 40" test "$RC" -eq 40
check "报告内容不一致且未涉及主仓库" bash -c 'grep -q "内容不一致" "$1" && grep -q "未涉及主仓库" "$1"' _ "$TMP/s8a.log"
check "未上传" no_sum "uploads.github.com"
seed_gh_asset diff
NOPRIMARY=1 rb "$TMP/s6" "$TMP/s8b.log" --publish-github-only; RC=$?
check "同 size、远端 digest ≠ 本地 sha → 退出码 40" test "$RC" -eq 40
check "报告 sha256 不一致" grep -q "sha256 不一致" "$TMP/s8b.log"
seed_gh_asset same
FAKE_GH_DIGEST=wrong NOPRIMARY=1 rb "$TMP/s6" "$TMP/s8c.log" --publish-github-only; RC=$?
check "digest 明确不等于本地 → 退出码 40" test "$RC" -eq 40
seed_gh_asset diff
FAKE_GH_DIGEST=none NOPRIMARY=1 rb "$TMP/s6" "$TMP/s8d.log" --publish-github-only; RC=$?
check "无 digest、同 size、内容被改 → 下载重算对账不一致 → 退出码 40" test "$RC" -eq 40
check "报告下载重算不一致" grep -q "下载重算 sha256 不一致" "$TMP/s8d.log"
check "该路径未上传" no_sum "uploads.github.com"
seed_gh_asset same
FAKE_GH_DIGEST=none NOPRIMARY=1 rb "$TMP/s6" "$TMP/s8e.log" --publish-github-only; RC=$?
check "无 digest、内容相同 → 下载对账通过，退出码 0（跳过上传）" test "$RC" -eq 0

echo "== 场景 9: 主仓库失败 → 不执行 GitHub 步骤 =="
make_sb s9; fake_reset; fake_clear_env
FAKE_PRIMARY_FAIL=create rb "$TMP/s9" "$TMP/s9.log" --publish-only; RC=$?
check "退出码 1（主仓库失败，区别于 GitHub 的 40）" test "$RC" -eq 1
summ > "$TMP/s9.sum"
check "没有任何 GitHub 请求" bash -c '! grep -q -E "github" "$1"' _ "$TMP/s9.sum"
check "GitHub 远端无 tag（未推送）" test -z "$(gh_tags "$TMP/s9-github.git")"
check "输出点名主仓库 release 创建失败" grep -q "主仓库 release 创建失败" "$TMP/s9.log"
check "输出不含『GitHub 步骤失败』（根本没走到）" bash -c '! grep -q "GitHub 步骤失败" "$1"' _ "$TMP/s9.log"

echo "== 场景 10: GitHub 失败 → 退出码 40，报告主仓库已成功，不回滚 =="
make_sb s10; fake_reset; fake_clear_env
FAKE_GH_FAIL=upload rb "$TMP/s10" "$TMP/s10.log" --publish-only; RC=$?
check "退出码 40" test "$RC" -eq 40
check "输出『主仓库发布已成功，但 GitHub 步骤失败』" grep -q "主仓库发布已成功，但 GitHub 步骤失败" "$TMP/s10.log"
check "输出给出单独重跑命令 --publish-github-only" grep -q -- "--publish-github-only" "$TMP/s10.log"
summ > "$TMP/s10.sum"
check "主仓库已发布（有 POST 主仓库资产上传）" grep -q '^POST forge.example.com:443 /api/v1/repos/owner/alphagps/releases/11/assets' "$TMP/s10.sum"
check "未回滚：没有任何 DELETE 请求" bash -c '! grep -q "^DELETE" "$1"' _ "$TMP/s10.sum"
check "主仓库远端 tag 仍在" test "$(gh_tags "$TMP/s10-primary.git")" == "refs/tags/$TAGV"
check "GitHub 上传失败详情已输出" grep -q "GitHub 资产上传失败" "$TMP/s10.log"
make_sb s10b; fake_reset; fake_clear_env
FAKE_GH_FAIL=create rb "$TMP/s10b" "$TMP/s10b.log" --publish-only; RC=$?
check "release 创建失败 → 40" test "$RC" -eq 40
make_sb s10c; fake_reset; fake_clear_env
FAKE_GH_TAG_STATUS=500 rb "$TMP/s10c" "$TMP/s10c.log" --publish-only; RC=$?
check "release 查询 500（非 200/404）→ fail-closed 40（而不是当作 404 新建）" test "$RC" -eq 40
check "该路径没有创建 GitHub release" no_sum "^POST api.github.com"

echo "== 场景 11: 重定向策略 =="
redir_case() { # redir_case <desc> <期望rc> <FAKE_ 环境赋值...>
  local desc=$1 want=$2; shift 2
  make_sb s11; fake_reset; fake_clear_env
  ( export "$@"; NOPRIMARY=1 rb "$TMP/s11" "$TMP/s11.log" --publish-github-only ); RC=$?
  check "$desc → 退出码 $want" test "$RC" -eq "$want"
}
redir_case "重定向到 evil 主机" 40 FAKE_GH_REDIR_HOST=evil.example.org
check "从未请求 evil 主机" no_evil_requests
check "报告重定向目标未通过白名单" grep -q "未通过 https+主机白名单校验" "$TMP/s11.log"
redir_case "Location 为 http:// 降级" 40 FAKE_GH_REDIR_LOCATION=http://release-assets.githubusercontent.com/github-production-release-asset/1/x
check "未发起 http 请求" bash -c '! grep -q "\"url\": \"http://" "$FAKE_LOG"'
redir_case "Location '@' 旁路" 40 'FAKE_GH_REDIR_LOCATION=https://release-assets.githubusercontent.com@evil.example.org/github-production-release-asset/1/x'
check "从未请求 evil 主机（@ 旁路）" no_evil_requests
redir_case "Location 相对路径" 40 FAKE_GH_REDIR_LOCATION=/github-production-release-asset/1/x
redir_case "Location 子域伪装 release-assets.githubusercontent.com.evil.example.org" 40 FAKE_GH_REDIR_HOST=release-assets.githubusercontent.com.evil.example.org
check "从未请求子域伪装主机" no_evil_requests
redir_case "Location 带端口" 40 FAKE_GH_REDIR_HOST=release-assets.githubusercontent.com:8443
redir_case "二次重定向（白名单主机再 302）" 40 FAKE_GH_DOUBLE=1
check "报告第二跳仍重定向" grep -q "第二跳仍重定向" "$TMP/s11.log"
check "二次重定向只发起 2 次下载请求（不跟随第三跳）" test "$(summ | grep -c -E '^GET (github.com|release-assets)')" -eq 2
redir_case "下载内容被篡改（sha 不一致）" 40 FAKE_GH_TAMPER=1
check "报告下载重算不一致" grep -q "下载重算 sha256 不一致" "$TMP/s11.log"
redir_case "browser_download_url 指向 evil 主机（同源校验）" 40 FAKE_GH_BAD_URL=1
check "同源校验拦截" grep -q "同源校验" "$TMP/s11.log"
check "从未请求 evil.example.org" no_evil_requests
redir_case "旧域名 objects.githubusercontent.com 也放行" 0 FAKE_GH_REDIR_HOST=objects.githubusercontent.com
check "第二跳确实发往 objects.githubusercontent.com" has_sum "^GET objects.githubusercontent.com "
redir_case "release-assets.githubusercontent.com 放行（默认）" 0 FAKE_GH_REDIR_HOST=release-assets.githubusercontent.com

echo "== 场景 12: 前置检查（先于任何对外动作） =="
make_sb s12; fake_reset; fake_clear_env
git -C "$TMP/s12" remote remove github
rb "$TMP/s12" "$TMP/s12a.log" --publish-only; RC=$?
check "缺 GitHub 远端 → 退出码 1" test "$RC" -eq 1
check "提示 git remote add github https://github.com/mfreceiver/alpha-gps.git" \
  grep -q "git remote add github https://github.com/mfreceiver/alpha-gps.git" "$TMP/s12a.log"
check "尚未发起任何请求（主仓库也没动）" test ! -s "$FAKE_LOG"
check "脚本没有自动创建远端" test -z "$(git -C "$TMP/s12" remote | grep -x github)"
rb "$TMP/s12" "$TMP/s12b.log" --publish-only --no-github; RC=$?
check "--no-github 绕过 GitHub 前置：主仓库照常发布 → 退出码 0" test "$RC" -eq 0
check "--no-github 无任何 GitHub 请求" no_sum github
make_sb s12c; fake_reset
git -C "$TMP/s12c" remote set-url github https://github.com/Saschl/alpha-gps.git
rb "$TMP/s12c" "$TMP/s12c.log" --publish-only; RC=$?
check "GitHub 远端 url 指向别的仓库 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
make_sb s12d; fake_reset
git -C "$TMP/s12d" remote set-url github "$TMP/s12d-github.git"
rb "$TMP/s12d" "$TMP/s12d.log" --publish-only; RC=$?
check "GitHub 远端 url 是本地路径 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
make_sb s12e; fake_reset
printf 'https://puser:ppw@forge.example.com:443\n' > "$TMP/s12e/home/.git-credentials"
retag "$TMP/s12e"
rb "$TMP/s12e" "$TMP/s12e.log" --publish-only; RC=$?
check "缺 github.com 凭据 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
check "报告 GitHub 凭据缺失" grep -q "GitHub 凭据" "$TMP/s12e.log"
make_sb s12f; fake_reset
ALPHAGPS_GITHUB_REMOTE=primary rb "$TMP/s12f" "$TMP/s12f.log" --publish-only; RC=$?
check "GITHUB_REMOTE 与主仓库远端同名 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
make_sb s12g; fake_reset; git -C "$TMP/s12g" remote rename github gh >/dev/null 2>&1
ALPHAGPS_GITHUB_REMOTE=gh rb "$TMP/s12g" "$TMP/s12g.log" --publish-only; RC=$?
check "ALPHAGPS_GITHUB_REMOTE=gh 自定义远端名生效 → 退出码 0" test "$RC" -eq 0
check "自定义远端收到 tag" test "$(gh_tags "$TMP/s12g-github.git")" == "refs/tags/$TAGV"

echo "== 场景 13: GitHub 远端 tag 已存在 =="
make_sb s13; fake_reset; fake_clear_env
( cd "$TMP/s13" && git -c user.email=t@t -c user.name=t commit -q --allow-empty -m other \
    && git push -q "$TMP/s13-github.git" HEAD:refs/tags/$TAGV 2>/dev/null; git reset -q --hard HEAD~1 )
check "预置：GitHub 远端 tag 指向另一个 commit" test "$(git -C "$TMP/s13-github.git" rev-parse "$TAGV^{commit}")" != "$(git -C "$TMP/s13" rev-parse "$TAGV^{commit}")"
NOPRIMARY=1 rb "$TMP/s13" "$TMP/s13.log" --publish-github-only; RC=$?
check "tag 指向不同 commit → 退出码 40（禁止移动已发布 tag）" test "$RC" -eq 40
check "报告 tag 指向不一致" grep -q "禁止移动已发布 tag" "$TMP/s13.log"
check "未发起 release 请求" test ! -s "$FAKE_LOG"
make_sb s13b; fake_reset
git -C "$TMP/s13b" push -q "$TMP/s13b-github.git" "$TAGV" 2>/dev/null
NOPRIMARY=1 rb "$TMP/s13b" "$TMP/s13b.log" --publish-github-only; RC=$?
check "tag 已存在且同 commit → 跳过 push，退出码 0" test "$RC" -eq 0
check "日志含『跳过 tag push』" grep -q "跳过 tag push" "$TMP/s13b.log"

echo "== 场景 14: 分支推送选项 =="
make_sb s14; fake_reset; fake_clear_env
NOPRIMARY=1 rb "$TMP/s14" "$TMP/s14.log" --publish-github-only --github-tag-only; RC=$?
check "--github-tag-only：退出码 0" test "$RC" -eq 0
check "--github-tag-only：GitHub 远端无分支" test -z "$(gh_heads "$TMP/s14-github.git")"
make_sb s14b; fake_reset
NOPRIMARY=1 rb "$TMP/s14b" "$TMP/s14b.log" --publish-github-only --github-push-branch; RC=$?
check "--github-push-branch：退出码 0" test "$RC" -eq 0
BR=$(git -C "$TMP/s14b" rev-parse --abbrev-ref HEAD)
check "--github-push-branch：GitHub 远端有当前分支 $BR" test "$(gh_heads "$TMP/s14b-github.git")" == "refs/heads/$BR"
check "--github-push-branch：输出提示分支一并公开" grep -q "与当前分支" "$TMP/s14b.log"

echo "== 场景 15: 选项互斥/适用性 =="
make_sb s15; fake_reset; fake_clear_env
for combo in "--publish-github-only --publish-only" "--publish-github-only --publish" "--publish --publish-only" \
             "--publish-github-only --no-github" "--allow-dirty --no-github" "--allow-dirty --github-push-branch" \
             "--publish-only --github-tag-only --github-push-branch" "--no-github --github-tag-only" \
             "--no-github --publish-only --github-push-branch" "--allow-dirty --publish-github-only"; do
  # shellcheck disable=SC2086
  rb "$TMP/s15" "$TMP/s15.log" $combo; RC=$?
  check "组合 '$combo' → 退出码 1 且无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
done
( cd "$TMP/s15" && bash scripts/release_build.sh --help ) > "$TMP/help.log" 2>&1; RC=$?
check "--help 退出 0" test "$RC" -eq 0
check "--help 列出新选项与退出码 40" bash -c 'for k in --publish-github-only --no-github --github-tag-only --github-push-branch "40"; do grep -q -- "$k" "$1" || exit 1; done' _ "$TMP/help.log"
check "--help 含『历史对公开可见』" grep -q "历史对公开可见" "$TMP/help.log"

echo "== 场景 16: --publish-github-only 前置（本地 tag/归档） =="
make_sb s16; fake_reset; fake_clear_env
git -C "$TMP/s16" tag -d "$TAGV" >/dev/null
NOPRIMARY=1 rb "$TMP/s16" "$TMP/s16a.log" --publish-github-only; RC=$?
check "本地 tag 缺失 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
check "报告本地 tag 不存在" grep -q "本地 tag $TAGV 不存在" "$TMP/s16a.log"
make_sb s16b; fake_reset
printf 'tampered' >> "$TMP/s16b/release/$APKN"
( cd "$TMP/s16b" && git -c user.email=t@t -c user.name=t commit -qam tamper && git tag -d "$TAGV" >/dev/null && git tag "$TAGV" )
NOPRIMARY=1 rb "$TMP/s16b" "$TMP/s16b.log" --publish-github-only; RC=$?
check "归档与 SHA256SUMS 不符 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
check "报告 SHA256SUMS 校验失败" grep -q "SHA256SUMS 校验失败" "$TMP/s16b.log"
make_sb s16c; fake_reset
( cd "$TMP/s16c" && git -c user.email=t@t -c user.name=t commit -q --allow-empty -m later )
NOPRIMARY=1 rb "$TMP/s16c" "$TMP/s16c.log" --publish-github-only; RC=$?
check "tag 不在 HEAD → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"
make_sb s16d; fake_reset
echo dirty >> "$TMP/s16d/gradle.properties"
NOPRIMARY=1 rb "$TMP/s16d" "$TMP/s16d.log" --publish-github-only; RC=$?
check "脏树 → 退出码 1，无请求" test "$RC" -eq 1 -a ! -s "$FAKE_LOG"

echo "== 场景 17: 主仓库资产表无 sha256 字段 → 下载强校验分支仍可用 =="
make_sb s17; fake_reset; fake_clear_env
FAKE_PRIMARY_NOSHA=1 rb "$TMP/s17" "$TMP/s17.log" --publish-only --no-github; RC=$?
check "退出码 0" test "$RC" -eq 0
check "走了主仓库下载强校验" grep -q "全量下载重算哈希" "$TMP/s17.log"
check "主仓库下载不跟随重定向且 proto-redir 受限" bash -c 'summ() { python3 "$2" "$3"; }; summ _ "$1" "$2" | grep "owner/alphagps/releases/download" | grep -q "noloc=1 pr==https"' _ "$TMP/logq.py" "$FAKE_LOG"

echo "== 场景 18: 脚本变异验证（证明重定向用例能检出弱实现） =="
make_sb mutA; fake_reset; fake_clear_env
sed -i 's/^      rl_gh_url_allowed download "\$loc" >&2 \\$/      true \\/' "$TMP/mutA/scripts/release_build.sh"
check "变异A 前置：sed 确实改动了脚本" bash -c '! diff -q "$1" "$2" >/dev/null' _ "$HERE/release_build.sh" "$TMP/mutA/scripts/release_build.sh"
retag "$TMP/mutA"
( export FAKE_GH_REDIR_HOST=evil.example.org; NOPRIMARY=1 rb "$TMP/mutA" "$TMP/mutA.log" --publish-github-only ); RC=$?
check "变异A（删重定向白名单校验）：evil 重定向被放行（rc=0 且请求了 evil 主机）" bash -c '[[ "$1" -eq 0 ]] && grep -q evil "$2"' _ "$RC" "$FAKE_LOG"
make_sb mutB; fake_reset; fake_clear_env
sed -i "s/--no-location --proto '=https' --proto-redir '=https' \\\\\$/--location --proto '=https' --proto-redir '=https' \\\\/" "$TMP/mutB/scripts/release_build.sh"
check "变异B 前置：sed 确实改动了脚本" bash -c '! diff -q "$1" "$2" >/dev/null' _ "$HERE/release_build.sh" "$TMP/mutB/scripts/release_build.sh"
retag "$TMP/mutB"
( NOPRIMARY=1 rb "$TMP/mutB" "$TMP/mutB.log" --publish-github-only ); RC=$?
summ > "$TMP/mutB.sum"
check "变异B（改为跟随重定向）：不变量『所有请求 follow=0 noloc=1』被检出违规" bash -c 'grep -q -E "follow=1|noloc=0" "$1"' _ "$TMP/mutB.sum"
make_sb mutC; fake_reset; fake_clear_env
sed -i 's/^        30\[12378\]) gh_err .*$/        30[12378]) : ;;/' "$TMP/mutC/scripts/release_build.sh"
check "变异C 前置：sed 确实改动了脚本" bash -c '! diff -q "$1" "$2" >/dev/null' _ "$HERE/release_build.sh" "$TMP/mutC/scripts/release_build.sh"
retag "$TMP/mutC"
( export FAKE_GH_DOUBLE=1; NOPRIMARY=1 rb "$TMP/mutC" "$TMP/mutC.log" --publish-github-only ); RC=$?
check "变异C（放过第二跳 3xx）：二次重定向用例不再报『第二跳仍重定向』（用例能区分）" bash -c '! grep -q "第二跳仍重定向" "$1"' _ "$TMP/mutC.log"
# 加强：第二跳 302 携带与本地**完全相同**的字节——哈希对账无法区分，必须靠状态码判定（退出码，而非错误文本）
make_sb mutC2; fake_reset; fake_clear_env
cp -- "$TMP/mutC/scripts/release_build.sh" "$TMP/mutC2/scripts/release_build.sh"
retag "$TMP/mutC2"
( export FAKE_GH_DOUBLE=1 FAKE_GH_DOUBLE_BODY=1; NOPRIMARY=1 rb "$TMP/mutC2" "$TMP/mutC2.log" --publish-github-only ); RC=$?
check "变异C + 第二跳 302 携带相同字节：哈希兜底下被当作成功（rc=0，证明该用例能暴露状态漏洞）" test "$RC" -eq 0
check "变异C + 同字节：对账确实通过（哈希相同）" grep -q "GitHub 资产对账通过" "$TMP/mutC2.log"
make_sb realC2; fake_reset; fake_clear_env
retag "$TMP/realC2"
( export FAKE_GH_DOUBLE=1 FAKE_GH_DOUBLE_BODY=1; NOPRIMARY=1 rb "$TMP/realC2" "$TMP/realC2.log" --publish-github-only ); RC=$?
check "真实脚本：第二跳 302 携带与本地完全相同字节仍必须退出 40" test "$RC" -eq 40
check "真实脚本：报告第二跳仍重定向（而非靠哈希不一致）" bash -c 'grep -q "第二跳仍重定向" "$1" && ! grep -q "对账通过" "$1"' _ "$TMP/realC2.log"
fake_clear_env

echo "== 场景 19: GitHub 推送目的地校验（规范化函数 + URL 净化 + 真实 git 配置矩阵 + 库级变异） =="
CANON=https://github.com/mfreceiver/alpha-gps.git
FAKE_TOK=ghp_FAKEleak999
OUT=$(rl_gh_canon_url "${CANON%.git}" 2>/dev/null); check "规范化: 无 .git 后缀 → 规范形式（带 .git）" test "$OUT" == "$CANON"
OUT=$(rl_gh_canon_url "$CANON" 2>/dev/null); check "规范化: 带 .git 后缀 → 原样" test "$OUT" == "$CANON"
for u in "$CANON/" "${CANON%.git}/" "http://github.com/mfreceiver/alpha-gps.git" "https://github.com/MFreceiver/alpha-gps.git" \
         "https://GitHub.com/mfreceiver/alpha-gps.git" "https://github.com:443/mfreceiver/alpha-gps.git" \
         "https://$FAKE_TOK@github.com/mfreceiver/alpha-gps.git" "$CANON?x=1" "$CANON#f" " $CANON" "$CANON " \
         "$CANON"$'\n'"$CANON" "$CANON.git" "git@github.com:mfreceiver/alpha-gps.git" \
         "ssh://git@github.com/mfreceiver/alpha-gps.git" "/tmp/x.git" "file:///tmp/x.git" ""; do
  rl_gh_canon_url "$u" >"$TMP/o" 2>/dev/null; rc=$?
  check "规范化拒绝（无输出）: $(printf '%q' "${u//$FAKE_TOK/<tok>}")" test "$rc" -ne 0 -a ! -s "$TMP/o"
done
# URL 净化（错误日志里打印 URL 之前必经）
disp() { printf '%s' "$1" | rl_gh_url_display; }
check "净化: userinfo(token) 被去掉" test "$(disp "https://$FAKE_TOK@github.com/a/b.git")" == "https://github.com/a/b.git"
check "净化: user:token + query + fragment 全去掉" test "$(disp "https://x-access-token:$FAKE_TOK@github.com/a/b.git?k=$FAKE_TOK#f")" == "https://github.com/a/b.git"
check "净化: query 里的 token 被去掉" test "$(disp "$CANON?token=$FAKE_TOK")" == "$CANON"
check "净化: scp 形式 user@host:path 去掉 user" test "$(disp "git@github.com:a/b.git")" == "github.com:a/b.git"
check "净化: 控制字符 → '?'" test "$(disp $'https://github.com/a\x01b')" == "https://github.com/a?b"
check "净化: 超长截断（≤63 字符）" test "${#"$(disp "https://github.com/$(printf 'a%.0s' $(seq 1 200))")"}" -le 63
check "净化: 路径里的 '@' 之前内容也丢弃（保守）" bash -c '! grep -q "$2" <<<"$1"' _ "$(disp "https://github.com/x/$FAKE_TOK@y")" "$FAKE_TOK"

VR_DIR=$TMP/vr; VR_LIB=$LIB
vr() { # vr <setup 片段(在新建的沙箱仓库里 eval)> [ENV=val ...] → VR_RC / VR_OUT / $TMP/vr.err（git 配置隔离：无全局/系统配置）
  local setup=$1; shift
  rm -rf -- "$VR_DIR"; git init -q "$VR_DIR"
  ( cd "$VR_DIR" && git remote add github "$CANON" && eval "$setup" ) >/dev/null 2>&1
  VR_OUT=$( cd "$VR_DIR" && env GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 "$@" \
      bash -c 'source "$1"; rl_gh_verify_remote github && echo "PIN=$RL_GH_PINNED_URL"' _ "$VR_LIB" 2>"$TMP/vr.err" ); VR_RC=$?
}
vr_ok() { vr "$2" "${@:3}"; check "目的地放行: $1" test "$VR_RC" -eq 0 -a "$VR_OUT" == "PIN=$CANON"; }
vr_bad() { # vr_bad <desc> <setup> <stderr 需含的正则> [ENV...]
  vr "$2" "${@:4}"
  check "目的地拒绝: $1" test "$VR_RC" -ne 0 -a -z "$VR_OUT"
  check "目的地拒绝: $1 → 错误含 /$3/" grep -q -E -- "$3" "$TMP/vr.err"
  check "目的地拒绝: $1 → 错误不含令牌" bash -c '! grep -q "$2" "$1"' _ "$TMP/vr.err" "$FAKE_TOK"
}
vr_ok "基线：url/pushurl 均为公开 URL（无 pushurl）" ':'
vr_ok "url 无 .git 后缀（规范等价）" 'git config remote.github.url "${CANON%.git}"'
vr_ok "显式 pushurl 等于公开 URL" 'git config remote.github.pushurl "$CANON"'
vr_ok "显式 pushurl 无 .git 后缀" 'git config remote.github.pushurl "${CANON%.git}"'
vr_ok "remote.github.push/mirror 配置不影响目的地（传输钉死字面量后不参与）" 'git config remote.github.push "+refs/*:refs/*"; git config remote.github.mirror true'
vr_bad "split：fetch=公开，pushurl=别的主机" 'git config remote.github.pushurl https://example.invalid/x.git' 'push\(github\).*必须精确为'
vr_bad "split：pushurl=本地 bare 路径" 'git config remote.github.pushurl "$TMP/evil.git"' 'push\(github\).*必须精确为'
vr_bad "split：pushurl http 降级" 'git config remote.github.pushurl http://github.com/mfreceiver/alpha-gps.git' 'push\(github\).*必须精确为'
vr_bad "split：pushurl ssh" 'git config remote.github.pushurl git@github.com:mfreceiver/alpha-gps.git' 'push\(github\).*必须精确为'
vr_bad "split：fetch=别处，pushurl=公开" 'git config remote.github.url https://example.invalid/x.git; git config remote.github.pushurl "$CANON"' 'fetch\(github\).*必须精确为'
vr_bad "多 fetch URL（第二个是别处）" 'git config --add remote.github.url https://example.invalid/x.git' '2 个'
vr_bad "多 fetch URL（两个都是公开 URL，仍拒绝：必须恰好一个）" 'git config --add remote.github.url "$CANON"' '2 个'
vr_bad "多 pushurl（两个都是公开 URL）" 'git config --add remote.github.pushurl "$CANON"; git config --add remote.github.pushurl "$CANON"' 'push\(github\).*2 个'
vr_bad "多 pushurl（第一个别处、第二个公开）" 'git config --add remote.github.pushurl https://example.invalid/x.git; git config --add remote.github.pushurl "$CANON"' 'push\(github\).*2 个'
vr_bad "insteadOf 把公开 URL 改写到本地 bare（旧测试沙箱的做法）" 'git config "url.$TMP/evil.git.insteadOf" "$CANON"' 'fetch\(github\)'
vr_bad "insteadOf 前缀整站改写" 'git config url.https://example.invalid/.insteadOf https://github.com/' 'fetch\(github\)'
vr_bad "pushInsteadOf 只改写 push 侧（fetch 侧仍是公开 URL）" 'git config "url.$TMP/evil.git.pushInsteadOf" "$CANON"' 'push\(github\)'
vr_bad "别名 url + insteadOf→公开，另有 pushInsteadOf 会改写公开 URL 字面量" \
  'git config remote.github.url gh:x; git config "url.$CANON.insteadOf" gh:x; git config url.https://example.invalid/.pushInsteadOf https://github.com/' '改写公开 URL'
vr_bad "别名 url + 值为空的 insteadOf 规则（保守拒绝）" \
  'git config remote.github.url gh:x; git config "url.$CANON.insteadOf" gh:x; git config url.https://example.invalid/.insteadOf ""' '改写公开 URL'
vr_bad "环境变量注入 GIT_CONFIG_COUNT insteadOf" ':' 'fetch\(github\)' GIT_CONFIG_COUNT=1 "GIT_CONFIG_KEY_0=url.$TMP/evil.git.insteadOf" "GIT_CONFIG_VALUE_0=$CANON"
vr_bad "url 内嵌令牌（userinfo）" 'git config remote.github.url "https://$FAKE_TOK@github.com/mfreceiver/alpha-gps.git"' '必须精确为'
vr_bad "url 内嵌 user:token + query" 'git config remote.github.url "https://x-access-token:$FAKE_TOK@github.com/mfreceiver/alpha-gps.git?t=$FAKE_TOK"' '必须精确为'
vr_bad "pushurl 内嵌令牌" 'git config remote.github.pushurl "https://$FAKE_TOK@github.com/mfreceiver/alpha-gps.git"' 'push\(github\)'
vr_bad "多个 pushurl 均含令牌" 'git config --add remote.github.pushurl "https://$FAKE_TOK@evil.example/a"; git config --add remote.github.pushurl "https://u:$FAKE_TOK@evil.example/b"' '2 个'
vr_bad "url 末尾斜杠" 'git config remote.github.url "$CANON/"' '必须精确为'
vr_bad "url 大小写主机" 'git config remote.github.url https://GitHub.com/mfreceiver/alpha-gps.git' '必须精确为'
vr_bad "url 指向上游 Saschl" 'git config remote.github.url https://github.com/Saschl/alpha-gps.git' '必须精确为'
( cd "$VR_DIR" && env GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 bash -c 'source "$1"; rl_gh_verify_remote nosuch' _ "$LIB" ) >/dev/null 2>"$TMP/vr.err"; rc=$?
check "远端不存在 → 非零，提示 git remote add nosuch $CANON" bash -c '[[ "$1" -ne 0 ]] && grep -q "git remote add nosuch $2" "$3"' _ "$rc" "$CANON" "$TMP/vr.err"

# 库级变异：弱实现必须放过对应绕过（证明矩阵用例有效）
MV=$TMP/lib_mv.sh
mvlib() { # mvlib <sed 表达式...> → 生成变异库并断言确实改动
  sed "$@" "$LIB" > "$MV"
  check "库变异前置：sed 确实改动了 release_lib.sh" bash -c '! diff -q "$1" "$2" >/dev/null' _ "$LIB" "$MV"
}
mvlib -e '/cp=\$(rl_gh_single_canon "push(\$remote)"/d' -e '/\[\[ "\$cf" == "\$cp" \]\] ||/d'
VR_LIB=$MV; vr 'git config remote.github.pushurl https://example.invalid/x.git'
check "变异V1（只验 fetch 侧 = 原缺陷）放行 split pushurl（rc=0 才证明用例有效）" test "$VR_RC" -eq 0
mvlib -e 's/if \[\[ "\$n" != 1 \]\]; then/if false; then/' -e 's/canon=\$(rl_gh_canon_url "\$list")/canon=$(rl_gh_canon_url "$(head -1 <<<"$list")")/'
VR_LIB=$MV; vr 'git config --add remote.github.pushurl "$CANON"; git config --add remote.github.pushurl https://example.invalid/x.git'
check "变异V2（只看第一条、无数量检查）放行多 pushurl（rc=0）" test "$VR_RC" -eq 0
mvlib -e 's/rl_gh_url_unrewritten "\$cf" || return 1/:/'
VR_LIB=$MV; vr 'git config remote.github.url gh:x; git config "url.$CANON.insteadOf" gh:x; git config url.https://example.invalid/.pushInsteadOf https://github.com/'
check "变异V3（不检查字面量改写规则）放行别名+pushInsteadOf（rc=0）" test "$VR_RC" -eq 0
mvlib -e 's/^rl_gh_canon_url() {$/rl_gh_canon_url() { case "${1-}" in "https:\/\/github.com\/mfreceiver\/"*) printf "%s\\n" "$RL_GH_CANON_URL"; return 0;; esac/'
VR_LIB=$MV; vr 'git config remote.github.pushurl https://github.com/mfreceiver/other.git'
check "变异V4（规范化只比对前缀）放行同账号其它仓库（rc=0）" test "$VR_RC" -eq 0
VR_LIB=$LIB; vr 'git config remote.github.pushurl https://github.com/mfreceiver/other.git'
check "真实库拒绝同账号其它仓库 pushurl" test "$VR_RC" -ne 0

echo "== 场景 20: 脚本级目的地：绕过全部被拒 / 传输钉定到字面量 / 生产默认不可被环境变量绕过 =="
no_gh_transport() { ! grep -q -E 'ls-remote|push|BLOCKED' "$FAKE_DIR/gitwire.log"; }
dest_case() { # dest_case <名> <desc> <setup 片段(cwd=沙箱；$EVIL=诱饵 bare)> [VAR=val ...]
  local n=$1 desc=$2 setup=$3; shift 3
  make_sb "$n"; fake_reset; fake_clear_env
  local SB=$TMP/$n EVIL=$TMP/$n-evil.git
  git init -q --bare "$EVIL"
  ( cd "$SB" && eval "$setup" ) >/dev/null 2>&1
  ( for kv in "$@"; do export "$kv"; done; NOPRIMARY=1 rb "$SB" "$TMP/$n.log" --publish-github-only ); RC=$?
  check "$desc → 退出码 1（前置阶段拒绝）" test "$RC" -eq 1
  check "$desc → 零 HTTP 请求" test ! -s "$FAKE_LOG"
  check "$desc → 零 git 传输调用（未 ls-remote/push，更无 BLOCKED）" no_gh_transport
  check "$desc → 诱饵与 GitHub bare 均无任何 ref" test -z "$(git -C "$EVIL" for-each-ref)$(git -C "$SB-github.git" for-each-ref)"
}
dest_case d1 "pushurl 指向诱饵仓库(split fetch/push)" 'git config remote.github.pushurl "$EVIL"'
dest_case d2 "insteadOf 把公开 URL 改写到本地 bare（旧测试的做法）" 'git config "url.$SB-github.git.insteadOf" "$CANON"'
dest_case d3 "pushInsteadOf 把 push 改写到诱饵" 'git config "url.$EVIL.pushInsteadOf" "$CANON"'
dest_case d4 "两个 fetch URL" 'git config --add remote.github.url "$CANON"'
dest_case d5 "两个 pushurl（均为公开 URL）" 'git config --add remote.github.pushurl "$CANON"; git config --add remote.github.pushurl "$CANON"'
dest_case d6 "GIT_CONFIG_COUNT 环境注入 insteadOf→诱饵" ':' GIT_CONFIG_COUNT=1 "GIT_CONFIG_KEY_0=url.$TMP/d6-evil.git.insteadOf" "GIT_CONFIG_VALUE_0=$CANON"
dest_case d7 "别名 url + pushInsteadOf 改写公开 URL 字面量" 'git config remote.github.url gh:x; git config "url.$CANON.insteadOf" gh:x; git config "url.$EVIL.pushInsteadOf" "$CANON"'
dest_case d8 "生产默认值不可被环境变量绕过（ALPHAGPS_SELFTEST/ALPHAGPS_TEST_*/RL_GH_* 全设为诱饵）" 'git remote set-url github "$EVIL"' \
  ALPHAGPS_SELFTEST=1 "ALPHAGPS_TEST_GITHUB_URL=$TMP/d8-evil.git" "ALPHAGPS_GITHUB_URL=$TMP/d8-evil.git" \
  "ALPHAGPS_GH_ALLOWED_URL=$TMP/d8-evil.git" "RL_GH_REPO=evil/x" "RL_GH_CANON_URL=$TMP/d8-evil.git"

# 生产默认值：常量无条件赋值；库/脚本里没有任何测试覆盖入口
check "库常量 RL_GH_REPO 为字面量无条件赋值" test "$(grep -c '^RL_GH_REPO=mfreceiver/alpha-gps$' "$LIB")" -eq 1
check "库常量 RL_GH_CANON_URL 无条件由 RL_GH_REPO 派生" test "$(grep -c '^RL_GH_CANON_URL="https://github.com/\$RL_GH_REPO.git"$' "$LIB")" -eq 1
check "入库脚本无 SELFTEST/ALPHAGPS_TEST_ 测试后门" bash -c '! grep -n -E "SELFTEST|ALPHAGPS_TEST_|ALLOWED_URL" "$1" "$2"' _ "$HERE/release_build.sh" "$LIB"
OUT=$(RL_GH_REPO=evil/x RL_GH_CANON_URL=https://evil.example/x.git ALPHAGPS_SELFTEST=1 bash -c 'source "$1"; echo "$RL_GH_REPO $RL_GH_CANON_URL"' _ "$LIB")
check "环境变量预置 RL_GH_* 后 source 库：仍是生产常量" test "$OUT" == "mfreceiver/alpha-gps https://github.com/mfreceiver/alpha-gps.git"
sed 's|^RL_GH_REPO=mfreceiver/alpha-gps$|RL_GH_REPO=${RL_GH_REPO:-mfreceiver/alpha-gps}|' "$LIB" > "$TMP/lib_envmut.sh"
OUT=$(RL_GH_REPO=evil/x bash -c 'source "$1"; echo "$RL_GH_REPO"' _ "$TMP/lib_envmut.sh")
check "变异E（常量改成可被环境变量覆盖）：RL_GH_REPO=evil/x 生效（证明上面的免疫用例有效）" test "$OUT" == "evil/x"

# 传输钉定：全链成功后核对 wire 日志不变量
not_wire_ok() { ! wire_pinned_ok; }
CANON_RE=${CANON//./\\.}
wire_pinned_ok() { # 所有指向 GitHub 的传输调用都是 钉定字面量 + 显式 refspec；无 BLOCKED/force/mirror/delete
  local w=$FAKE_DIR/gitwire.log
  ! grep -q BLOCKED "$w" \
  && ! grep -F "$CANON" "$w" | grep -q -E '(^| )(-f|--force|--mirror|--delete|--all|--prune)( |$)| \+refs/' \
  && [[ "$(grep -F "$CANON" "$w" | grep -c ' push ')" -ge 1 ]] \
  && ! grep -F "$CANON" "$w" | grep ' push ' | grep -v -q -E "^ARGV: -c push\.followTags=false push --no-recurse-submodules $CANON_RE refs/(tags|heads)/[^ :]+:refs/(tags|heads)/[^ :]+\$" \
  && ! grep -F "$CANON" "$w" | grep ' ls-remote ' | grep -v -q -E "^ARGV: ls-remote --tags $CANON_RE refs/tags/[^ ]+ refs/tags/[^ ]+\^\{\}\$"
}
make_sb s20; fake_reset; fake_clear_env
git -C "$TMP/s20" config push.followTags true                          # 用户全局式配置：本应顺带推送注解 tag
git -C "$TMP/s20" config remote.github.push '+refs/*:refs/*'          # 远端级 push 配置：钉定字面量后不应参与
git -C "$TMP/s20" tag -a extra-annotated -m extra                      # HEAD 上另一个注解 tag（followTags 会带上）
NOPRIMARY=1 rb "$TMP/s20" "$TMP/s20.log" --publish-github-only --github-push-branch; RC=$?
check "s20 全链（含 --github-push-branch）成功" test "$RC" -eq 0
check "s20 wire：所有 GitHub 传输调用均钉定到字面量 URL + 显式 refspec + followTags=false，无 force" wire_pinned_ok
check "s20 wire：tag push 与 branch push 各一次" test "$(grep -F "$CANON" "$FAKE_DIR/gitwire.log" | grep -c ' push ')" -eq 2
check "s20：GitHub 远端只收到发布 tag（followTags/远端级 push 配置/注解 tag 均未扩大推送集）" \
  test "$(gh_tags "$TMP/s20-github.git")" == "refs/tags/$TAGV"
check "s20：分支 push 只推当前分支" test "$(gh_heads "$TMP/s20-github.git")" == "refs/heads/$(git -C "$TMP/s20" rev-parse --abbrev-ref HEAD)"
# 变异：去掉 followTags=false → 注解 tag 被顺带推上去（证明上面的断言有效）
make_sb mf; fake_reset; fake_clear_env
sed -i 's/git -c push.followTags=false push /git push /' "$TMP/mf/scripts/release_build.sh"
check "变异F 前置：sed 确实改动了脚本" bash -c '! diff -q "$1" "$2" >/dev/null' _ "$HERE/release_build.sh" "$TMP/mf/scripts/release_build.sh"
git -C "$TMP/mf" config push.followTags true; git -C "$TMP/mf" tag -a extra-annotated -m extra; retag "$TMP/mf"
NOPRIMARY=1 rb "$TMP/mf" "$TMP/mf.log" --publish-github-only --github-push-branch; RC=$?
check "变异F（去掉 followTags=false）：注解 tag 被顺带推到 GitHub（用例能检出）" bash -c 'grep -q extra-annotated <<<"$1"' _ "$(gh_tags "$TMP/mf-github.git")"
check "变异F：wire 不变量被检出违规" not_wire_ok
# 变异：按远端名传输（原缺陷）→ fake git 记 BLOCKED（wire 不变量违规）
make_sb mn; fake_reset; fake_clear_env
sed -i -e 's/git ls-remote --tags "\$GH_URL"/git ls-remote --tags "$GITHUB_REMOTE"/' \
       -e 's/push --no-recurse-submodules "\$GH_URL"/push --no-recurse-submodules "$GITHUB_REMOTE"/' "$TMP/mn/scripts/release_build.sh"
check "变异N 前置：sed 确实改动了脚本" bash -c '! diff -q "$1" "$2" >/dev/null' _ "$HERE/release_build.sh" "$TMP/mn/scripts/release_build.sh"
retag "$TMP/mn"
NOPRIMARY=1 rb "$TMP/mn" "$TMP/mn.log" --publish-github-only; RC=$?
check "变异N（按远端名传输）：被 fake git 判定为非钉定传输（BLOCKED）且退出 40" bash -c 'grep -q BLOCKED "$1/gitwire.log" && [[ "$2" -eq 40 ]]' _ "$FAKE_DIR" "$RC"
check "变异N：wire 不变量被检出违规" not_wire_ok

# 主仓库发布期间（GitHub 前置之后）本机配置被改出 pushurl → GitHub 步骤重验证拒绝（40），诱饵零 ref
make_sb mid; fake_reset; fake_clear_env
git init -q --bare "$TMP/mid-evil.git"
FAKE_PRIMARY_HOOK="git -C $TMP/mid config remote.github.pushurl $TMP/mid-evil.git" rb "$TMP/mid" "$TMP/mid.log" --publish-only; RC=$?
check "中途被加 pushurl：退出码 40（GitHub 步骤重验证拒绝）" test "$RC" -eq 40
check "中途被加 pushurl：输出主仓库已成功、GitHub 失败" grep -q "主仓库发布已成功，但 GitHub 步骤失败" "$TMP/mid.log"
check "中途被加 pushurl：零 GitHub 传输、诱饵无 ref" bash -c '! grep -q -F -e "$3" -e BLOCKED "$1/gitwire.log" && [[ -z "$(git -C "$2" for-each-ref)" ]]' _ "$FAKE_DIR" "$TMP/mid-evil.git" "$CANON"

echo
SCENARIOS=$(grep -c '^echo "== 场景' -- "$0")
TOTAL=$((PASS+FAILED))
echo "结果: ${SCENARIOS} 场景 / ${TOTAL} 断言: PASS=$PASS FAIL=$FAILED"
[[ "$FAILED" -eq 0 ]]
