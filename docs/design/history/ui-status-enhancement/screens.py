# -*- coding: utf-8 -*-
"""Phone-screen SVG builders (390x844 logical px = dp)."""
from lib import *

W, H = 390, 844

PAL = [("#7FB3D5", "#2E5C7A"), ("#F5B7B1", "#8E4A44"), ("#A9DFBF", "#2F6B45"), ("#F9E79F", "#8A6D1B"),
       ("#D2B4DE", "#5B3A6B"), ("#AED6F1", "#3B6E96"), ("#FAD7A0", "#935D1A"), ("#A3E4D7", "#1F6E5E")]


def tile_defs():
    s = "<defs>"
    for i, (a, b) in enumerate(PAL):
        s += (f'<linearGradient id="tg{i}" x1="0" y1="0" x2="0" y2="1">'
              f'<stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>')
    return s + "</defs>"


def status_bar(dark=False):
    c = C["onSurf"]
    return (t(24, 22, "9:41", 13, 600, c) + icon("signal", 330, 8, 16, c) +
            battery(354, 10, 80, c, 18, 9))


def app_bar(title, back=True, actions=(), center=True, y=32, extra=""):
    s = rect(0, y, W, 64, C["bg"])
    if back:
        s += icon("arrow_back", 16, y + 20, 24, C["onSurf"])
    if center:
        s += t(W / 2, y + 40, title, 20, 500, C["onSurf"], "middle")
    else:
        s += t(64, y + 40, title, 20, 500, C["onSurf"])
    return s + "".join(actions) + extra


def nav_pill():
    return rect(145, 830, 100, 4, "#1D1B20", 2, op=0.6)


# ---------------------------------------------------------------------------------- device page
def _row(y, lead, label, sub, value, vcol=None, lcol=None, dim=False):
    op = 0.5 if dim else 1
    s = f'<g opacity="{op}">' + lead
    s += t(68, y + 26, label, 15, 500, lcol or C["onSurf"])
    s += t(68, y + 44, sub, 12, 400, C["onSurfV"])
    s += t(358, y + 34, value, 15, 600, vcol or C["onSurf"], "end")
    return s + "</g>"


def device_screen(v="ok", callouts=False, dim_scrim=False):
    """v: ok | connecting | disconnected | warn"""
    b = tile_defs() if False else ""
    b += rect(0, 0, W, H, C["bg"])
    b += status_bar()
    b += app_bar("Alpha 7 IV", True, [icon("info", 350, 52, 24, C["onSurfV"])])
    # name block (unchanged from today)
    status_txt = {"ok": "已连接", "connecting": "连接中…", "disconnected": "已断开", "warn": "已连接"}[v]
    dot = {"ok": C["green"], "connecting": C["amber"], "disconnected": C["outline"], "warn": C["green"]}[v]
    b += t(16, 128, "相机名称", 12, 400, C["onSurfV"])
    b += t(16, 152, "Alpha 7 IV", 16, 600)
    b += circle(20, 172, 4, dot)
    b += t(32, 177, f"连接状态：{status_txt}", 14, 400, C["onSurfV"])
    btn = {"ok": "断开连接", "connecting": "连接中…", "disconnected": "连接", "warn": "断开连接"}[v]
    bcol = C["onSurf"] if v != "connecting" else C["outline"]
    b += rect(16, 192, 120, 40, "none", 20, C["outline"] if v != "connecting" else C["outlineV"], 1)
    b += t(76, 217, btn, 14, 500, C["primary"] if v != "connecting" else C["outline"], "middle")

    # ---- NEW card 1: camera status
    c1y = 252
    b += rect(16, c1y, 358, 196, C["card"], 16)
    r0, r1, r2 = c1y + 8, c1y + 68, c1y + 128
    if v == "ok":
        b += _row(r0, icon("check_circle", 32, r0 + 18, 24, C["green"]), "时间已校正", "相机已确认 · 本次连接", "14:32:08")
        b += _row(r1, bars(33, r1 + 21, 3, C["green"]), "蓝牙信号：良好", "每 5 秒刷新", "−58 dBm")
        b += _row(r2, battery(30, r2 + 24, 82), "电池电量", "14:20 获取 · 来自 Wi-Fi 会话", "82%")
    elif v == "connecting":
        b += _row(r0, spinner(44, r0 + 30, 9), "正在校时…", "最长等待 30 秒，不影响 GPS 传输", "—", C["onSurfV"])
        b += _row(r1, bars(33, r1 + 21, 3, C["green"]), "蓝牙信号：良好", "每 5 秒刷新", "−61 dBm")
        b += _row(r2, battery(30, r2 + 24, 82), "电池电量", "昨天 18:20 获取 · 来自 Wi-Fi 会话", "82%", C["onSurfV"])
    elif v == "disconnected":
        b += _row(r0, icon("schedule", 32, r0 + 18, 24, C["outline"]), "未校时", "连接后自动校正", "—", C["onSurfV"], dim=True)
        b += _row(r1, bars(33, r1 + 21, 0, C["outline"], dim=True), "蓝牙信号", "未连接", "—", C["onSurfV"], dim=True)
        b += _row(r2, battery(30, r2 + 24, 82, C["outline"]), "电池电量", "上次获取：昨天 18:20", "82%", C["onSurfV"])
    else:  # warn
        b += _row(r0, icon("warning", 32, r0 + 18, 24, C["amber"]), "相机拒绝校时", "原因码 0x0003 · 时区变化时自动重试", "未校正", C["amberText"])
        b += _row(r1, bars(33, r1 + 21, 1, C["red"]), "蓝牙信号：弱", "靠近相机可改善", "−88 dBm", C["red"])
        b += _row(r2, battery(30, r2 + 24, 18, C["red"]), "电池电量低", "14:20 获取 · 来自 Wi-Fi 会话", "18%", C["red"], C["red"])
    b += line(32, r1 - 0.5, 358, r1 - 0.5, C["outlineV"], 1)
    b += line(32, r2 - 0.5, 358, r2 - 0.5, C["outlineV"], 1)

    # ---- NEW card 2: GPS transmission
    c2y = 464
    c2h = 190 if v == "warn" else 150
    b += rect(16, c2y, 358, c2h, C["card"], 16)
    b += icon("gps", 32, c2y + 14, 24, C["primary"] if v != "disconnected" else C["outline"])
    b += t(68, c2y + 32, "GPS 位置传输" if v != "disconnected" else "GPS 位置传输（上次连接）", 15, 500)
    chip = {"ok": ("传输中", C["green"], "#DFF3E0"), "connecting": ("等待握手", C["outline"], "#E7E0EC"),
            "disconnected": ("已停止", C["outline"], "#E7E0EC"), "warn": ("写入延迟", C["amberText"], "#FFF0C2")}[v]
    cw = 72
    b += rect(374 - 16 - cw, c2y + 14, cw, 24, chip[2], 12)
    b += circle(374 - 16 - cw + 12, c2y + 26, 4, chip[1])
    b += t(374 - 16 - cw + 20, c2y + 31, chip[0], 12, 500, chip[1])
    vals = {"ok": ("128", "126", "3 秒前", "14:32:40"), "connecting": ("0", "0", "—", "握手完成后开始"),
            "disconnected": ("126", "126", "—", "14:32:40"), "warn": ("130", "126", "38 秒前", "14:32:02")}[v]
    colx = [16 + 358 / 6, 16 + 358 / 2, 16 + 358 * 5 / 6]
    dim = v == "disconnected"
    numc = C["onSurfV"] if dim else C["onSurf"]
    for i, (num, cap) in enumerate(zip(vals[:3], ["已传输（次）", "已写入（次）", "最近写入"])):
        sz = 24 if i < 2 else 20
        nc = C["amberText"] if (v == "warn" and i == 2) else numc
        b += t(colx[i], c2y + 78, num, sz, 600, nc, "middle")
        b += t(colx[i], c2y + 98, cap, 12, 400, C["onSurfV"], "middle")
    b += t(colx[2], c2y + 114, vals[3], 11, 400, C["onSurfV"], "middle")
    b += line(16 + 358 / 3, c2y + 56, 16 + 358 / 3, c2y + 106, C["outlineV"], 1)
    b += line(16 + 358 * 2 / 3, c2y + 56, 16 + 358 * 2 / 3, c2y + 106, C["outlineV"], 1)
    b += t(32, c2y + 138, "已传输＝手机发出；已写入＝相机蓝牙确认收到", 11, 400, C["onSurfV"])
    if v == "warn":
        b += rect(28, c2y + 120 + 24, 334, 0, "none")
        b += rect(28, c2y + 146, 334, 34, "#FFF0C2", 8)
        b += icon("warning", 36, c2y + 154, 18, C["amberText"])
        b += t(60, c2y + 168, "已 38 秒未收到相机确认；手机暂无定位", 12, 500, C["amberText"])
        b = b.replace(t(32, c2y + 138, "已传输＝手机发出；已写入＝相机蓝牙确认收到", 11, 400, C["onSurfV"]),
                      t(32, c2y + 138, "已传输＝手机发出；已写入＝相机蓝牙确认收到", 11, 400, C["onSurfV"]))

    # bottom Wi-Fi entry (unchanged)
    b += rect(16, 724, 358, 88, C["primary"], 16)
    b += icon("camera", 36, 742, 28, C["onPrimary"]) if False else icon("camera", 36, 742 + 16, 28, C["onPrimary"])
    b += t(80, 764, "Wi-Fi 照片传输", 16, 500, C["onPrimary"])
    b += t(80, 786, "浏览并下载相机里的照片", 12, 400, C["onPrimary"])
    b += icon("chevron_right", 330, 756, 24, C["onPrimary"])
    b += nav_pill()
    if callouts:
        b += callout(1, 382, r0 + 34) + callout(2, 382, r1 + 34) + callout(3, 382, r2 + 34)
        b += callout(4, 382, c2y + 26) + callout(5, 382, c2y + 76) + callout(6, 382, c2y + 112)
    return b


# ---------------------------------------------------------------------------------- Info dialog
def _kv(y, label, value, vcol=None, new=False, vsz=14, dim=False):
    s = f'<g opacity="{0.55 if dim else 1}">' + t(0, y, label, 14, 400, C["onSurf"])
    s += t(294, y, value, vsz, 400, vcol or C["onSurfV"], "end")
    if new:
        s += rect(-6, y - 17, 3, 22, C["callout"], 1.5)
    return s + "</g>"


def info_content(connected=True):
    """Dialog content in dialog-local coordinates; origin = content area top-left. Height ~ 680."""
    s = t(0, 18, "Alpha 7 IV", 16, 600)
    s += t(0, 44, "蓝牙地址：AA:BB:CC:DD:EE:FF", 14, 400, C["onSurfV"])
    s += line(0, 58, 294, 58, C["outlineV"])
    s += t(0, 84, "保持活跃", 14) + t(0, 102, "相机离开范围后，仍为该相机保持后台服务运行。", 11, 400, C["onSurfV"])
    s += t(0, 116, "退出应用仍会断开连接。", 11, 400, C["onSurfV"])
    s += rect(242, 74, 52, 32, C["primary"], 16) + circle(278, 90, 11, "#fff")
    s += _kv(148, "自动校时", "已启用（应用会保持开启）" if connected else "未知（相机未连接）", vsz=12)
    s += _kv(178, "自动区域调整", "已启用（应用会保持开启）" if connected else "未知（相机未连接）", vsz=12)
    s += _kv(208, "蓝牙 MTU", "247 字节" if connected else "—")
    s += line(0, 226, 294, 226, C["outlineV"])
    # --- NEW: live status
    s += t(0, 252, "实时状态", 12, 600, C["primary"]) + callout(1, 308, 247)
    if connected:
        s += _kv(282, "时间校正", "已校正 · 14:32:08", C["green"], True)
        s += _kv(310, "蓝牙信号", "−58 dBm（良好）", None, True)
        s += _kv(338, "电池电量", "82% · 14:20 获取", None, True)
        s += _kv(366, "固件版本", "2.00（上次 Wi-Fi 会话）", None, True, 13)
        s += _kv(394, "序列号", "••••3456", None, True)
    else:
        s += _kv(282, "时间校正", "未校时（连接后自动校正）", None, True, 12, True)
        s += _kv(310, "蓝牙信号", "—", None, True, 14, True)
        s += _kv(338, "电池电量", "82% · 昨天 18:20 获取", None, True)
        s += _kv(366, "固件版本", "—（进入 Wi-Fi 照片传输后显示）", None, True, 11, True)
        s += _kv(394, "序列号", "—", None, True, 14, True)
    s += line(0, 414, 294, 414, C["outlineV"])
    s += t(0, 440, "GPS 传输明细" + ("" if connected else "（上次连接）"), 12, 600, C["primary"]) + callout(2, 308, 435)
    dm = not connected
    s += _kv(470, "已传输（手机发出）", "128 次", None, True, dim=False)
    s += _kv(498, "已写入（相机确认）", "126 次", C["green"] if connected else None, True)
    s += _kv(526, "失败 / 取消", "2 次", None, True)
    s += _kv(554, "累计写入", "11.6 KB", None, True)
    s += _kv(582, "最近写入", "14:32:40（3 秒前）" if connected else "14:32:40", None, True)
    s += t(294, 614, "重置计数", 14, 500, C["primary"], "end") + callout(3, 308, 609)
    s += line(0, 632, 294, 632, C["outlineV"])
    s += t(0, 664, "重命名", 14, 500, C["primary"]) + t(72, 664, "帮助", 14, 500, C["primary"]) + t(126, 664, "删除", 14, 500, C["error"])
    return s


def info_screen(offset=0, connected=True, uid="a"):
    b = device_screen("ok" if connected else "disconnected")
    b += rect(0, 0, W, H, "#000", op=0.32)
    dx, dy, dw, dh = 24, 120, 342, 592
    b += rect(dx, dy, dw, dh, "#ECE6F0", 28)
    b += t(dx + 24, dy + 52, "相机信息", 24, 400)
    ax, ay, aw, ah = dx + 24, dy + 76, 294, 420
    b += f'<clipPath id="ic{uid}"><rect x="{ax - 8}" y="{ay}" width="{aw + 30}" height="{ah}"/></clipPath>'
    b += f'<g clip-path="url(#ic{uid})"><g transform="translate({ax},{ay - offset})">{info_content(connected)}</g></g>'
    # scrollbar
    total = 690.0
    th = ah * ah / total
    ty = ay + (ah - th) * offset / (total - ah)
    b += rect(dx + dw - 8, ty, 3, th, "#79747E", 1.5, op=0.6)
    if offset == 0:
        b += rect(ax - 8, ay + ah - 26, aw + 16, 26, "#ECE6F0", 0, op=0.0)
    b += t(dx + dw - 24, dy + dh - 24, "OK", 14, 500, C["primary"], "end")
    b += nav_pill()
    return b


# ---------------------------------------------------------------------------------- Wi-Fi page
def strip(x, y, variant="ok"):
    """Info strip (firmware / serial / card). returns (svg, height)."""
    h = 94
    s = rect(x, y, 358, h, C["card"], 12)
    xs = [x + 14, x + 126, x + 246]
    labels = ["固件版本", "序列号", "存储卡 1"]
    for lx, lab in zip(xs, labels):
        s += t(lx, y + 22, lab, 11, 400, C["onSurfV"])
    sk = lambda lx, w=58: rect(lx, y + 31, w, 12, C["outlineV"], 6)
    if variant == "loading":
        s += "".join(sk(lx) for lx in xs)
        s += rect(x + 14, y + 56, 220, 6, C["outlineV"], 3)
        s += t(x + 14, y + 80, "正在读取相机信息…", 11, 400, C["onSurfV"])
        s += rect(x + 286, y + 56, 56, 12, C["outlineV"], 6)
        return s, h
    fw, sn, cap = "2.00", "•••3456", "128 GB"
    fwc = snc = capc = C["onSurf"]
    if variant == "partial":
        fw, sn, cap = "—", "不可用", "128 GB"
        fwc = snc = C["outline"]
    if variant == "revealed":
        sn = "5012345678"
    if variant == "nocard":
        cap, capc = "无卡", C["amberText"]
    s += t(xs[0], y + 42, fw, 14, 600, fwc) + t(xs[1], y + 42, sn, 14, 600, snc) + t(xs[2], y + 42, cap, 14, 600, capc)
    if variant in ("ok", "revealed", "nocard"):
        s += icon("visibility", xs[1] + (96 if variant == "revealed" else 76), y + 28, 16, C["onSurfV"])
    if variant in ("ok", "revealed"):
        s += rect(x + 14, y + 56, 220, 6, C["outlineV"], 3) + rect(x + 14, y + 56, 220 * 54 / 128, 6, C["primary"], 3)
        s += t(x + 14, y + 80, "已用 54 GB · 可用 74 GB", 11, 400, C["onSurfV"])
    elif variant == "partial":
        s += t(x + 14, y + 80, "可用空间未知", 11, 400, C["onSurfV"])
    else:
        s += t(x + 14, y + 80, "未检测到存储卡（相机无法浏览照片）", 11, 500, C["amberText"])
    # battery chip (Wi-Fi session reads it too)
    if variant == "partial":
        s += battery(x + 284, y + 56, None, w=22) + t(x + 314, y + 66, "—", 13, 600, C["outline"])
    else:
        s += battery(x + 284, y + 56, 82) + t(x + 314, y + 66, "82%", 13, 600)
    return s, h


def tile(x, y, i, fmt, prot, rating, ext, sel=False, s=96):
    """One 96dp grid tile with today's overlays (+ lock / rating when ext)."""
    k = s / 96.0
    pal = i % len(PAL)
    g = f'<g transform="translate({x},{y}) scale({k:.4f})">'
    g += f'<clipPath id="tc{x}_{y}_{i}"><rect width="96" height="96" rx="4"/></clipPath><g clip-path="url(#tc{x}_{y}_{i})">'
    g += rect(0, 0, 96, 96, f"url(#tg{pal})")
    sx = 40 + (i * 13) % 30
    g += f'<path d="M0 78 L{sx - 18} 48 L{sx} 64 L{sx + 22} 38 L96 72 L96 96 L0 96Z" fill="#000" opacity="0.22"/>'
    g += circle(70 - (i * 7) % 30, 26, 8, "#fff", None) .replace('fill="#fff"', 'fill="#fff" opacity="0.55"')
    if sel:
        g += rect(0, 0, 96, 96, C["primary"], 0, op=0.25)
    # magnifier TL
    g += rect(8, 8, 24, 24, "#000", 3, op=0.55) + icon("search", 12, 12, 16, "#fff")
    # format badge BL
    fw = {"JPG": 30, "RAW": 30, "J&R": 30, "HEIF": 36}.get(fmt, 30)
    g += rect(4, 74, fw, 18, "#000", 4, op=0.7) + t(4 + fw / 2, 87, fmt, 10.5, 500, "#fff", "middle")
    if ext and rating:
        rw = 34
        g += rect(4, 52, rw, 18, "#000", 4, op=0.7) + icon("star", 7, 54, 14, RATING[rating])
        g += t(4 + 22, 65.5, str(rating), 11.5, 700, "#fff", "middle")
    if ext and prot:
        g += rect(64, 8, 24, 24, "#000", 3, op=0.55) + icon("lock", 68, 12, 16, "#fff")
    # selection check BR
    if sel:
        g += rect(65, 65, 22, 22, C["primary"], 3) + icon("check_circle", 65, 65, 22, C["primary"]).replace(C["primary"], "#fff", 1) if False else ""
        g += rect(65, 65, 22, 22, C["primary"], 3) + f'<path d="M69 76 l5 5 l9 -10" fill="none" stroke="#fff" stroke-width="2.5"/>'
    else:
        g += rect(66, 66, 20, 20, "#000", 3, "#fff", 2, 0.35)
    g += "</g></g>"
    return g


TILES = [("J&R", 0, 0), ("JPG", 1, 3), ("J&R", 0, 5), ("RAW", 0, 0),
         ("JPG", 0, 4), ("HEIF", 1, 5), ("J&R", 0, 2), ("J&R", 1, 1),
         ("JPG", 0, 0), ("JPG", 1, 0), ("RAW", 0, 3), ("J&R", 0, 4),
         ("JPG", 0, 0), ("J&R", 1, 2), ("JPG", 0, 5), ("RAW", 0, 1),
         ("JPG", 1, 4), ("J&R", 0, 0), ("JPG", 0, 2), ("HEIF", 0, 3),
         ("JPG", 0, 0), ("J&R", 1, 5), ("JPG", 0, 1), ("JPG", 0, 0)]


def pager(y=764):
    s = rect(0, y, W, 66, C["bg"]) + line(0, y, W, y, C["outlineV"])
    s += icon("chevron_right", 90, y + 20, 24, C["onSurfV"]).replace("<path", '<path transform-origin="0 0"', 0) if False else ""
    s += t(W / 2, y + 38, "第 1 / 5 页", 14, 500, C["onSurf"], "middle")
    s += rect(64, y + 16, 36, 32, C["secC"], 16) + t(82, y + 38, "‹", 20, 500, C["onSurfV"], "middle")
    s += rect(290, y + 16, 36, 32, C["secC"], 16) + t(308, y + 38, "›", 20, 500, C["onSurfV"], "middle")
    return s


def wifi_screen(ext=False, callouts=False, snackbar=False, strip_variant="ok", selected_idx=None, toggle_unavailable=False):
    b = tile_defs() + rect(0, 0, W, H, C["bg"]) + status_bar()
    # top bar actions: toggle / refresh / disconnect
    tog_cx = 266
    acts = ""
    if ext:
        acts += circle(tog_cx, 64, 20, C["secC"]) + icon("stars", tog_cx - 12, 52, 24, C["primary"])
    else:
        acts += icon("stars", tog_cx - 12, 52, 24, C["onSurfV"])
    acts += icon("refresh", 314 - 12, 52, 24, C["onSurfV"]) + icon("link_off", 362 - 12, 52, 24, C["onSurfV"])
    b += app_bar("11月9日", True, [acts], center=False)
    sv, sh = strip(16, 104, strip_variant)
    b += sv
    gy = 104 + sh + 8
    b += '<clipPath id="gridclip"><rect x="0" y="%d" width="390" height="%d"/></clipPath>' % (gy, 764 - gy)
    b += '<g clip-path="url(#gridclip)">'
    for n, (fmt, prot, rating) in enumerate(TILES):
        r, c = divmod(n, 4)
        b += tile(c * 98 - (0 if c < 3 else 0), gy + r * 98, n, fmt, prot, rating, ext, sel=(n == selected_idx), s=96 if c < 3 else 96)
    b += "</g>"
    b += pager()
    if snackbar:
        b += rect(16, 690, 358, 52, "#322F35", 8)
        b += t(32, 721, "已显示保护与评分", 14, 400, "#F5EFF7")
        b += icon("lock", 188, 710, 16, "#F5EFF7") + t(208, 721, "已保护", 12, 400, "#D0BCFF")
        b += icon("star", 262, 710, 16, RATING[4]) + t(282, 721, "评分 1–5", 12, 400, "#D0BCFF")
        b += t(358, 721, "", 14)
    if toggle_unavailable:
        b += rect(16, 690, 358, 52, "#322F35", 8)
        b += t(32, 712, "此相机或当前读取方式不提供保护/评分信息", 13, 400, "#F5EFF7")
        b += t(32, 730, "照片浏览不受影响", 11, 400, "#CAC4D0")
    b += nav_pill()
    if callouts:
        b += callout(1, 286, 46) + callout(2, 382, 150)
        if ext:
            b += callout(3, 186, gy + 4) + callout(4, 142, gy + 61)
            if snackbar:
                b += callout(5, 382, 690)
    return b


def phone_svg(body, scale=1.0, frame=True):
    inner = body
    shell = ""
    if frame:
        shell = f'<rect x="0.5" y="0.5" width="{W - 1}" height="{H - 1}" rx="26" fill="none" stroke="#B9B2C0" stroke-width="1"/>'
    clip = f'<clipPath id="pc"><rect width="{W}" height="{H}" rx="26"/></clipPath>'
    return svg_wrap(W, H, f'{clip}<g clip-path="url(#pc)">{inner}</g>{shell}', scale, defs=True)
