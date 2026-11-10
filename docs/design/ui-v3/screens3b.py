"""alpha-gps UI design v3 — screens part B: date list, grid, viewer, settings, help, logs, notifications."""
import math

from lib3 import *  # noqa: F401,F403
from screens3 import base, end, row_card, icon_tile, CAM

PAL = [("#7FB3D5", "#2E5C7A"), ("#F5B7B1", "#8E4A44"), ("#A9DFBF", "#2F6B45"), ("#F9E79F", "#8A6D1B"),
       ("#D2B4DE", "#5B3A6B"), ("#AED6F1", "#3B6E96"), ("#FAD7A0", "#935D1A"), ("#A3E4D7", "#1F6E5E")]


def tile_defs():
    s = "<defs>"
    for i, (a_, b_) in enumerate(PAL):
        s += (f'<linearGradient id="tg{i}" x1="0" y1="0" x2="0" y2="1">'
              f'<stop offset="0" stop-color="{a_}"/><stop offset="1" stop-color="{b_}"/></linearGradient>')
    return s + "</defs>"


_uid = [0]


def photo_art(x, y, w, h, i, r=0):
    """Gradient 'photo' with a mountain silhouette, clipped to a rounded rect."""
    _uid[0] += 1
    cid = f"pa{_uid[0]}"
    sx = w * (0.42 + ((i * 13) % 30) / 100.0)
    g = f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/></clipPath><g clip-path="url(#{cid})">'
    g += rect(x, y, w, h, f"url(#tg{i % len(PAL)})")
    g += (f'<path d="M{x} {y + h * .8} L{x + sx - w * .2} {y + h * .5} L{x + sx} {y + h * .67} L{x + sx + w * .23} {y + h * .4} '
          f'L{x + w} {y + h * .75} L{x + w} {y + h} L{x} {y + h}Z" fill="#000" opacity="0.22"/>')
    g += circle(x + w * (0.72 - ((i * 7) % 30) / 100.0), y + h * .27, min(w, h) * 0.085, "#fff", op=0.55)
    return g + "</g>"


# ================================================================ date list
def date_list_screen(state="normal", callouts=False):
    s = tile_defs() + base()
    s += top_bar("照片", sub=f"{CAM} · 763 张" if state == "normal" else CAM, acts=("refresh",))
    if state == "empty":
        s += glass(195 - 56, 300, 112, 112, 56, 1) + icon("image", 195 - 26, 330, 52, C["outline"])
        s += t(195, 452, "存储卡里没有照片", 20, 600, anchor="middle")
        s += t(195, 479, "拍几张之后点右上角刷新", 13.5, 400, C["onSurfV"], "middle")
        return s + end()
    if state == "error":
        s += icon("error", 195 - 32, 304, 64, C["error"])
        s += t(195, 410, "照片目录读取中断", 20, 600, anchor="middle")
        s += t(195, 437, "与相机的连接已断开", 13.5, 400, C["onSurfV"], "middle")
        s += tonal_btn(195 - 70, 466, 140, 48, "重新读取", "refresh")
        return s + end()
    x = 20
    for lab, ic_ in (("64 GB", "sd_card"), ("剩余 21.3 GB", None), ("固件 2.00", None)):
        tg, tw = info_tag(x, 104, lab, on_photo=False, ic=ic_, size=11.5, h=22, pad=8)
        s += tg
        x += tw + 6
    rows = [("11月9日", "周日 · 今天", 227), ("11月8日", "周六", 64), ("11月3日", "周一", 118),
            ("10月28日", "周二", 9), ("10月21日", "周二", 342), ("未知日期", "无拍摄时间", 3)]
    y = 140
    for i, (d, sub, n) in enumerate(rows):
        s += row_card(y, 80)
        if d == "未知日期":
            s += rect(28, y + 12, 56, 56, C["onSurf"], 14, op=0.07) + icon("image", 44, y + 28, 24, C["outline"])
        else:
            s += photo_art(28, y + 12, 56, 56, i * 3 + 1, 14)
        s += t(98, y + 37, d, 17, 600)
        s += t(98, y + 59, sub, 12.5, 400, C["onSurfV"])
        s += t(334, y + 46, f"{n} 张", 14, 500, C["onSurfV"], "end")
        s += icon("chevron_right", 338, y + 28, 24, C["onSurfV"])
        y += 90
    if callouts:
        s += callout(1, 14, 62) + callout(2, 362, 50) + callout(3, 14, 115) + callout(4, 30, 150)
    return s + end()


# ================================================================ grid
# (format, protected, rating, MB)
TILES = [("J+R", 0, 0, 54.6), ("JPG", 1, 3, 6.6), ("J+R", 0, 5, 51.2), ("RAW", 0, 0, 47.9),
         ("JPG", 0, 0, 7.1), ("J+R", 1, 4, 55.0), ("HEIF", 0, 2, 4.2), ("JPG", 1, 0, 6.9),
         ("J+R", 0, 0, 53.3), ("JPG", 0, 1, 7.4), ("RAW", 1, 5, 48.8), ("J+R", 0, 0, 52.7),
         ("JPG", 0, 0, 6.2), ("J+R", 0, 3, 54.1), ("JPG", 0, 0, 7.7), ("HEIF", 1, 0, 4.0)]


def pr_label(prot, rating):
    """Merged protect + rating label text (None when neither)."""
    if prot and rating:
        return f"★{rating}", True
    if prot:
        return "", True
    if rating:
        return f"★{rating}", False
    return None, False


def pr_tag(xr, y, prot, rating, size=10.5, h=17):
    """Merged tag, right-aligned at xr."""
    lab, lock = pr_label(prot, rating)
    if lab is None and not lock:
        return ""
    if lock and not lab:
        w_ = h + 2
        return rect(xr - w_, y, w_, h, "#000", 4, op=0.60) + icon("lock", xr - w_ + (w_ - size - 1) / 2, y + (h - size - 1) / 2, size + 1, "#fff")
    tg, tw = info_tag(xr, y, lab, size=size, h=h, ic="lock" if lock else None, anchor="end")
    return tg


def tile(x, y, s_, i, opts, selecting=False, sel=False, r=8):
    fmt, prot, rating, mb = TILES[i % len(TILES)]
    small = s_ < 100
    fs = 9.5 if small else 10.5
    th = 15 if small else 17
    pad = 4 if small else 6
    if sel:
        g = rect(x, y, s_, s_, C["primaryC"], r)
        g += photo_art(x + 6, y + 6, s_ - 12, s_ - 12, i, r - 2)
        g += rect(x + 1.25, y + 1.25, s_ - 2.5, s_ - 2.5, "none", r, C["primary"], 2.5)
        ix, iy, iw = x + 6, y + 6, s_ - 12
    else:
        g = photo_art(x, y, s_, s_, i, r)
        ix, iy, iw = x, y, s_
    if opts.get("pr"):
        g += pr_tag(ix + iw - pad, iy + pad, prot, rating, fs, th)
    bx = ix + pad
    by = iy + iw - pad - th
    if opts.get("fmt"):
        tg, tw = info_tag(bx, by, fmt, size=fs, h=th, pad=4)
        g += tg
        bx += tw + 3
    if opts.get("size"):
        g += info_tag(bx, by, f"{mb} MB", size=fs, h=th, pad=4)[0]
    if selecting:
        cx, cy = x + 15, y + 15
        if sel:
            g += circle(cx, cy, 10, C["primary"], "#fff", 1.5) + icon("check", cx - 8, cy - 8, 16, C["onPrimary"])
        else:
            g += circle(cx, cy, 9.5, "#000", "#fff", 1.6, op=None).replace('fill="#000"', 'fill="#000" fill-opacity="0.25"')
    return g


def grid_geom(mode_):
    cols = {"S": 4, "M": 3, "L": 2}[mode_]
    pad, gap = 6, 4
    return cols, pad, gap, (W - 2 * pad - gap * (cols - 1)) / cols


def seg_control(x, y, w_, labels, idx, h=40):
    s = rect(x, y, w_, h, C["onSurf"], h / 2, op=0.07 if mode() == "light" else 0.14)
    sw = (w_ - 6) / len(labels)
    for i, lab in enumerate(labels):
        if i == idx:
            s += rect(x + 3 + i * sw, y + 3, sw, h - 6, C["primary"], (h - 6) / 2)
        s += t(x + 3 + i * sw + sw / 2, y + h / 2 + 5, lab, 14, 600, C["onPrimary"] if i == idx else C["onSurfV"], "middle")
    return s


def view_panel(mode_="S", fmt=False, size=False, pr=False, x=146, y=88):
    """Glass popover (level 3) anchored under the 'tune' action."""
    w_, h_ = 232, 262
    s = glass(x, y, w_, h_, 24, 3)
    s += t(x + 20, y + 32, "缩略图大小", 12.5, 600, C["onSurfV"])
    s += seg_control(x + 16, y + 44, w_ - 32, ("小", "中", "大"), "SML".index(mode_))
    s += t(x + 20, y + 114, "显示标签", 12.5, 600, C["onSurfV"])
    for i, (lab, on) in enumerate((("文件格式", fmt), ("文件大小", size), ("保护与评分", pr))):
        yy = y + 126 + i * 44
        s += t(x + 20, yy + 26, lab, 15, 500)
        s += switch(x + w_ - 68, yy + 8, on)
    return s


def dl_pill(kind="idle", n=3, mb="67.8 MB", y=748):
    """Floating download pill (operation)."""
    if kind == "idle":
        lab = f"下载 {n} 张 · {mb}"
        w_ = 236
        return primary_btn((W - w_) / 2, y, w_, 56, lab, "download", size=15.5)
    if kind == "mini":
        s = primary_btn(W - 16 - 84, y, 84, 56, f"{n}", "download", size=16)
        return s
    # downloading: glass level 2 pill with progress + cancel
    w_ = 300
    x = (W - w_) / 2
    s = glass(x, y, w_, 56, 28, 2)
    s += circle(x + 30, y + 28, 13, "none", C["outlineV"], 3) + arc(x + 30, y + 28, 13, 0, 155, C["primary"], 3)
    s += t(x + 54, y + 25, "正在下载 2 / 3", 14.5, 600)
    s += t(x + 54, y + 43, "DSC03457.ARW · 43%", 11.5, 400, C["onSurfV"])
    s += text_btn(x + w_ - 22, y + 28, "取消", anchor="end")
    return s


def grid_screen(mode_="S", fmt=False, size=False, pr=False, selecting=0, panel=False, callouts=False,
                sel_set=(1, 2, 5), pill="idle", drag=False, scrolled=False):
    cols, pad, gap, s_ = grid_geom(mode_)
    opts = dict(fmt=fmt, size=size, pr=pr)
    s = tile_defs() + base()
    y0 = 100
    rows = int((H - y0) / (s_ + gap)) + 1
    r = {"S": 8, "M": 10, "L": 14}[mode_]
    for rr in range(rows):
        for cc in range(cols):
            i = rr * cols + cc
            s += tile(pad + cc * (s_ + gap), y0 + rr * (s_ + gap), s_, i, opts, bool(selecting), bool(selecting) and i in sel_set, r)
    # top bar sits on the backdrop (content scrolls under a solid-ish bar; no live blur over the grid)
    s += rect(0, 0, W, 96, C["bg"], op=0.94) + sbar()
    if selecting:
        s += top_bar(f"已选 {len(sel_set)} 张", back="close", acts=("select_all",))
    else:
        s += top_bar("11月9日", sub="227 张", acts=("tune",))
    # bottom fade so the pill reads on any photo
    if selecting:
        s += f'<rect x="0" y="{H - 150}" width="{W}" height="150" fill="url(#vbot)" opacity="{0.35 if mode() == "light" else 0.5}"/>'
        s += dl_pill(pill, len(sel_set))
    if drag:
        pts = [(pad + 1.5 * (s_ + gap), y0 + 0.5 * (s_ + gap)), (pad + 2.5 * (s_ + gap), y0 + 0.55 * (s_ + gap)),
               (pad + 1.6 * (s_ + gap), y0 + 1.5 * (s_ + gap))]
        d = "M" + " L".join(f"{a_:.0f} {b_:.0f}" for a_, b_ in pts)
        s += f'<path d="{d}" fill="none" stroke="#fff" stroke-width="3" stroke-dasharray="6 5" stroke-linecap="round"/>'
        s += circle(pts[-1][0], pts[-1][1], 17, "#fff", C["primary"], 2, op=0.85)
    if panel:
        s += scrim(0.16)
        s += view_panel(mode_, fmt, size, pr)
    if callouts:
        s += callout(1, 352, 48)
    return s + end()


# ================================================================ viewer (always dark chrome)
VW = "#FFFFFF"
VW2 = "#C4C9D1"
VW3 = "#8E959E"


def histogram(x, y, w, h, seed=3, rgb=False):
    def curve(sd, amp=1.0):
        pts = []
        for i in range(41):
            u = i / 40
            v = (math.exp(-((u - 0.28 - sd * 0.03) ** 2) / 0.012) * 0.9 + math.exp(-((u - 0.62 + sd * 0.02) ** 2) / 0.03) * 0.6
                 + math.exp(-((u - 0.9) ** 2) / 0.004) * 0.25 * sd) * amp
            pts.append((x + u * w, y + h - min(v, 1.0) * h * 0.95))
        return f"M{x} {y + h} L" + " L".join(f"{a_:.1f} {b_:.1f}" for a_, b_ in pts) + f" L{x + w} {y + h}Z"
    s = rect(x, y, w, h, "#fff", 8, op=0.06)
    if rgb:
        for sd, col in ((0, "#FF5A5A"), (1.6, "#4ADE80"), (3, "#60A5FA")):
            s += f'<path d="{curve(sd, 0.9)}" fill="{col}" opacity="0.38"/>'
    s += f'<path d="{curve(seed * 0.5)}" fill="#fff" opacity="{0.55 if rgb else 0.75}"/>'
    for k in (0.25, 0.5, 0.75):
        s += line(x + w * k, y, x + w * k, y + h, "#fff", 0.6, extra='opacity="0.14"')
    return s


def filmstrip(y, cur=5, n=9, sw=48):
    s = ""
    gap = 4
    x0 = W / 2 - sw / 2 - (n // 2) * (sw + gap)
    for k in range(n):
        i = cur - n // 2 + k
        x = x0 + k * (sw + gap)
        if k == n // 2:
            s += photo_art(x - 4, y - 4, sw + 8, sw + 8, i, 10)
            s += rect(x - 4, y - 4, sw + 8, sw + 8, "none", 10, "#fff", 2.2)
        else:
            s += f'<g opacity="{0.72 if abs(k - n // 2) < 3 else 0.4}">' + photo_art(x, y, sw, sw, i, 8) + "</g>"
    return s


def v_topbar(name="DSC03456.JPG", size_txt="6.6 MB", idx="6 / 227", locked=False):
    s = icon("arrow_back", 16, 52, 24, VW, op=0.4 if locked else None)
    s += t(56, 59, name, 16, 600, VW)
    s += t(56 + len(name) * 9.6 + 8, 59, size_txt, 13, 500, VW2)
    s += t(56, 79, "2026-11-09 14:32:08", 12.5, 400, VW2)
    s += t(W - 20, 68, idx, 13, 500, VW2, "end")
    return s


def gps_chip(x, y, state="ok", w_=None):
    """Location state chip (information; glass-v pill because it floats over black/photo)."""
    spec = {"ok": ("location_on", "#7DDB93", "已定位", "31.2304° N, 121.4737° E"),
            "none": ("location_off", "#FFC857", "未含位置", "拍摄时没有写入 GPS"),
            "loading": ("location_on", VW3, "读取位置…", ""),
            "fail": ("location_off", VW3, "位置信息不可用", "")}[state]
    w_ = w_ or {"ok": 262, "none": 232, "loading": 118, "fail": 146}[state]
    s = glass(x, y, w_, 36, 18, "v", shadow=False)
    s += icon(spec[0], x + 10, y + 8, 20, spec[1])
    s += t(x + 36, y + 23, spec[2], 13, 600, spec[1] if state in ("ok", "none") else VW2)
    if spec[3]:
        s += t(x + 36 + len(spec[2]) * 13 + 8, y + 23, spec[3], 12.5, 400, VW2)
    return s, w_


def params_card(x, y, w_, state="ok"):
    """Shooting parameters (EXIF). 4 cells + lens line."""
    s = glass(x, y, w_, 112, 20, "v", shadow=False)
    cw = (w_ - 16) / 4
    vals = [("f/2.8", "光圈"), ("1/250 s", "快门"), ("ISO 400", "感光度"), ("35 mm", "焦距")]
    for i, (v, c) in enumerate(vals):
        cx = x + 8 + cw * i + cw / 2
        if state == "ok":
            s += t(cx, y + 38, v, 17, 600, VW, "middle")
        elif state == "loading":
            s += rect(cx - 26, y + 22, 52, 18, "#fff", 6, op=0.12)
        else:
            s += t(cx, y + 38, "—", 17, 600, VW3, "middle")
        s += t(cx, y + 58, c, 11, 400, VW3, "middle")
    if state == "ok":
        s += icon("camera", x + 16, y + 77, 16, VW2)
        s += t(x + 38, y + 90, "FE 24-70mm F2.8 GM II", 12.5, 500, VW2)
        s += t(x + w_ - 16, y + 90, "+0.3 EV", 12.5, 600, VW, "end")
    elif state == "loading":
        s += rect(x + 16, y + 78, 180, 14, "#fff", 6, op=0.1)
    else:
        s += t(x + 16, y + 90, "拍摄参数不可用（未能读取文件头）", 12.5, 400, VW3)
    return s


def file_card(x, y, w_, saved=False, hist=True):
    s = glass(x, y, w_, 96, 20, "v", shadow=False)
    s += rect(x + w_ / 2 - 16, y + 7, 32, 4, "#fff", 2, op=0.45)
    if hist:
        s += histogram(x + 14, y + 22, 132, 60)
    tx = x + 160
    s += info_tag(tx, y + 22, "JPG", size=10.5)[0] + t(tx + 34, y + 35, "6.6 MB", 12.5, 500, VW)
    s += info_tag(tx + 92, y + 22, "RAW", size=10.5)[0] + t(tx + 128, y + 35, "48.0 MB", 12.5, 500, VW)
    s += t(tx, y + 59, "7008 × 4672 · 32.7 MP", 12.5, 400, VW2)
    s += icon("lock", tx - 1, y + 69, 14, VW2) + t(tx + 17, y + 81, "★★★★", 11.5, 600, RATING[4])
    if saved:
        s += icon("check_circle", tx + 86, y + 69, 14, "#7DDB93") + t(tx + 104, y + 81, "已保存 JPG", 11.5, 500, "#7DDB93")
    return s


def v_download(state="normal", y=740):
    if state == "saved":
        return (rect(95, y, 200, 56, "#FFFFFF", 28, op=0.12) + icon("check", 152, y + 17, 22, "#9AA1AA")
                + t(182, y + 34, "已保存", 16, 600, "#9AA1AA"))
    if state == "downloading":
        x, w_ = 45, 300
        s = glass(x, y, w_, 56, 28, "v")
        s += circle(x + 30, y + 28, 13, "none", "#555B63", 3) + arc(x + 30, y + 28, 13, 0, 155, "#A8C7FA", 3)
        s += t(x + 54, y + 25, "正在下载 43%", 14.5, 600, VW)
        s += t(x + 54, y + 43, "2.8 / 6.6 MB", 11.5, 400, VW2)
        s += text_btn(x + w_ - 22, y + 28, "取消", "#A8C7FA", anchor="end")
        return s
    # fixed colours: the viewer chrome is always dark, whatever the app theme
    s = rect(95, y, 200, 56, "#A8C7FA", 28, extra='filter="url(#shv)"')
    s += rect(95.6, y + 0.6, 198.8, 54.8, "none", 28, "#FFFFFF", 1, op=0.55)
    s += icon("download", 160, y + 17, 22, "#062E6F") + t(190, y + 34, "下载", 16, 600, "#062E6F")
    return s


def viewer_screen(orient="land", state="normal", exif="ok", gps="ok", sheet=False, callouts=False, immersive=False):
    """orient: land (3:2) | port (2:3). state: normal | downloading | saved."""
    was = mode()
    set_theme("dark")
    s = tile_defs() + rect(0, 0, W, H, "#000")
    locked = state == "downloading"
    if sheet:
        s += photo_art(0, 100, 390, 260, 5)
        s += sbar(VW) + v_topbar(locked=locked)
        y = 372
        s += glass(0, y, W, H - y + 30, 28, "v")
        s += rect(W / 2 - 18, y + 10, 36, 4, "#fff", 2, op=0.5)
        # header: location
        gc, gw = gps_chip(16, y + 26, gps)
        s += gc
        s += circle(W - 40, y + 44, 20, "#A8C7FA") + icon("download", W - 51, y + 33, 22, "#062E6F")
        s += t(20, y + 82, "海拔 12 m · GPS 时间 06:32:07 UTC", 12, 400, VW3)
        # params
        s += t(20, y + 112, "拍摄参数", 12.5, 600, "#A8C7FA")
        cells = [("f/2.8", "光圈"), ("1/250 s", "快门"), ("ISO 400", "感光度"), ("35 mm", "焦距"), ("+0.3 EV", "曝光补偿"),
                 ("A", "模式")]
        for i, (v, c) in enumerate(cells):
            cx = 16 + (i % 3) * 122
            cy = y + 122 + (i // 3) * 56
            s += rect(cx, cy, 114, 48, "#fff", 12, op=0.06)
            s += t(cx + 12, cy + 22, v, 15, 600, VW) + t(cx + 12, cy + 39, c, 10.5, 400, VW3)
        s += icon("camera", 18, y + 240, 16, VW2) + t(40, y + 253, "FE 24-70mm F2.8 GM II", 12.5, 500, VW2)
        # histogram
        s += t(20, y + 284, "直方图", 12.5, 600, "#A8C7FA")
        s += histogram(16, y + 294, 358, 62, rgb=True)
        # file
        s += t(20, y + 384, "文件", 12.5, 600, "#A8C7FA")
        s += info_tag(20, y + 396, "JPG", size=10.5)[0] + t(54, y + 409, "6.6 MB", 12.5, 500, VW)
        s += info_tag(112, y + 396, "RAW", size=10.5)[0] + t(148, y + 409, "48.0 MB", 12.5, 500, VW)
        s += t(W - 20, y + 409, "7008 × 4672 · 32.7 MP", 12.5, 400, VW2, "end")
        s += icon("lock", 19, y + 424, 14, VW2) + t(38, y + 436, "已保护", 12, 400, VW2)
        s += t(92, y + 436, "★★★★", 12, 600, RATING[4])
        s += icon("check_circle", 250, y + 424, 14, "#7DDB93") + t(268, y + 436, "已保存 JPG", 12, 500, "#7DDB93")
        s += nav_pill(VW)
        set_theme(was)
        return s
    if orient == "land":
        py, ph = 292, 260
        s += photo_art(0, py, 390, ph, 5)
        if not immersive:
            s += sbar(VW) + v_topbar(locked=locked)
            s += params_card(12, 104, 366, exif)
            gc, gw = gps_chip(12, 228, gps if exif != "loading" else "loading")
            s += gc
            if exif == "ok":
                s += pr_tag(378, 237, 1, 4, 11, 18)
            s += file_card(12, 564, 366, saved=state == "saved")
            s += filmstrip(676)
            s += v_download(state)
    else:
        pw, ph = 378.7, 568
        px = (W - pw) / 2
        s += photo_art(px, 96, pw, ph, 2)
        if not immersive:
            s += f'<rect x="0" y="0" width="{W}" height="130" fill="url(#vtop)"/>'
            s += sbar(VW) + v_topbar("DSC03461.JPG", "7.1 MB", "11 / 227", locked=locked)
            # compact one-line glass strip over the photo's bottom edge
            s += glass(16, 612, 358, 40, 20, "v", shadow=False)
            gcol = {"ok": "#7DDB93", "none": "#FFC857"}.get(gps, VW3)
            s += icon("location_on" if gps == "ok" else "location_off", 26, 622, 20, gcol)
            if exif == "ok":
                s += t(54, 637, "f/1.8 · 1/500 s · ISO 100 · 85 mm", 13, 600, VW)
            elif exif == "loading":
                s += rect(54, 624, 190, 16, "#fff", 6, op=0.12)
            else:
                s += t(54, 637, "拍摄参数不可用", 13, 400, VW3)
            s += icon("expand_less", 340, 620, 24, VW2)
            s += filmstrip(676, cur=10)
            s += v_download(state)
    if locked:
        s += rect(0, 668, W, 64, "#000", op=0.55)
    if callouts and orient == "land":
        for n_, (cx, cy) in enumerate(((16, 46), (10, 112), (10, 236), (380, 300), (10, 572), (10, 684), (88, 746)), 1):
            s += callout(n_, cx, cy)
    if callouts and orient == "port":
        s += callout(1, 12, 620) + callout(2, 372, 620)
    s += nav_pill(VW)
    set_theme(was)
    return s


# ================================================================ settings / help / logs
def list_row(y, title, value=None, ic=None, kind="nav", sub=None, h=60, on=True, x=16, w_=358):
    s = ""
    tx = x + 20
    if ic:
        s += icon(ic, x + 18, y + h / 2 - 11, 22, C["primary"])
        tx = x + 56
    if sub:
        s += t(tx, y + h / 2 - 2, title, 15.5, 500) + t(tx, y + h / 2 + 17, sub, 12, 400, C["onSurfV"])
    else:
        s += t(tx, y + h / 2 + 5.5, title, 15.5, 500)
    if kind == "switch":
        s += switch(x + w_ - 66, y + h / 2 - 14, on)
    else:
        if value:
            s += t(x + w_ - 42, y + h / 2 + 5, value, 13.5, 400, C["onSurfV"], "end")
        s += icon("chevron_right" if kind == "nav" else "open_in_new", x + w_ - 38, y + h / 2 - (12 if kind == "nav" else 10),
                  24 if kind == "nav" else 20, C["onSurfV"])
    return s


def settings_screen():
    s = base() + top_bar("设置")
    s += glass(16, 104, 358, 84, 24, 1)
    s += list_row(104, "位置同步", ic="gps", kind="switch", sub="总开关：关闭后不连接任何相机", h=84)
    s += section_label(28, 216, "连接与定位")
    s += glass(16, 228, 358, 180, 24, 1)
    s += list_row(228, "位置来源", "融合定位", "location_on")
    s += list_row(288, "提示音", "连接 / 断开", "volume_up")
    s += list_row(348, "电池优化", "未限制", "battery_full")
    s += section_label(28, 436, "通用")
    s += glass(16, 448, 358, 240, 24, 1)
    s += list_row(448, "语言", "简体中文", "language")
    s += list_row(508, "日志级别", "信息", "list")
    s += list_row(568, "更新日志", "0.1.5", "info")
    s += list_row(628, "崩溃报告", "已关闭", "warning")
    s += t(195, 730, "alpha-gps 0.1.5 · cn.vectory.alphagps", 12, 400, C["onSurfV"], "middle")
    return s + end()


def settings_sub_screen():
    """Example sub page: battery optimisation (status + one operation)."""
    s = base() + top_bar("电池优化")
    s += glass(16, 104, 358, 150, 24, 1)
    s += icon_tile(52, 148, "check", 22, C["greenC"], C["green"])
    s += t(88, 144, "未限制", 18, 600) + t(88, 166, "系统不会在后台停止位置同步", 12.5, 400, C["onSurfV"])
    s += t(36, 208, "若后台同步经常中断，请确认此项为「未限制」，", 12.5, 400, C["onSurfV"])
    s += t(36, 228, "并关闭厂商的自启动/后台冻结限制。", 12.5, 400, C["onSurfV"])
    s += glass(16, 266, 358, 120, 24, 1)
    s += list_row(266, "系统电池设置", kind="link", ic="battery_full")
    s += list_row(326, "厂商后台管理说明", kind="link", ic="info")
    return s + end()


def help_screen(callouts=False):
    s = base() + top_bar("帮助")
    s += glass(16, 104, 358, 96, 24, 1, tint=C["primaryC"], a=0.62 if mode() == "light" else 0.4)
    s += t(36, 138, "关于 alpha-gps", 16.5, 600, C["onPrimaryC"])
    s += t(36, 162, "通过蓝牙把手机定位写入 Sony 相机，", 12.5, 400, C["onPrimaryC"])
    s += t(36, 181, "并可经 Wi‑Fi 浏览、下载相机照片。", 12.5, 400, C["onPrimaryC"])
    s += glass(16, 212, 358, 72, 24, 1)
    s += icon_tile(52, 248, "search", 22)
    s += t(88, 244, "排障指南", 15.5, 600) + t(88, 265, "连不上、找不到相机时先看这里", 12.5, 400, C["onSurfV"])
    s += icon("chevron_right", 338, 236, 24, C["onSurfV"])
    s += section_label(28, 312, "常见问题")
    # expanded FAQ
    s += glass(16, 324, 358, 222, 24, 1)
    s += t(36, 356, "有文档或反馈渠道吗？", 15, 600) + icon("expand_less", 338, 338, 24, C["onSurfV"])
    s += t(36, 382, "源码与问题反馈在代码仓库；也可以写邮件。", 12.5, 400, C["onSurfV"])
    for i, (lab, ic_) in enumerate((("代码仓库", "open_in_new"), ("发送邮件", "open_in_new"), ("请作者喝杯咖啡", "favorite"))):
        yy = 396 + i * 46
        s += rect(28, yy, 334, 40, C["onSurf"], 14, op=0.05 if mode() == "light" else 0.08)
        s += icon(ic_, 40, yy + 10, 20, C["primary"] if ic_ != "favorite" else C["tertiary"])
        s += t(70, yy + 25, lab, 14, 500, C["primary"])
        if ic_ == "favorite":
            s += icon("open_in_new", 332, yy + 11, 18, C["onSurfV"])
    y = 556
    for q in ("我的位置数据会上传吗？", "如何开启后台同步？", "相机没有出现在列表里", "位置不准或写入延迟"):
        s += glass(16, y, 358, 56, 20, 1, shadow=False)
        s += t(36, y + 33, q, 14.5, 500) + icon("expand_more", 338, y + 16, 24, C["onSurfV"])
        y += 64
    if callouts:
        s += callout(1, 26, 222) + callout(2, 26, 494)
    return s + end()


def troubleshoot_screen():
    s = base() + top_bar("排障指南")
    steps = [("相机端", ["蓝牙功能：开", "位置信息链接：开", "未处于录像 / USB 连接中"]),
             ("手机端", ["蓝牙、定位已开启", "已授予「附近设备」「位置」权限", "电池优化设为「未限制」"]),
             ("仍然不行", ["在相机上删除配对后重新配对", "重启相机与手机蓝牙"])]
    y = 104
    for i, (ttl, items) in enumerate(steps, 1):
        h_ = 52 + len(items) * 28
        s += glass(16, y, 358, h_, 24, 1)
        s += circle(46, y + 30, 14, C["primary"]) + t(46, y + 35, str(i), 13.5, 700, C["onPrimary"], "middle")
        s += t(72, y + 36, ttl, 16, 600)
        for k, it in enumerate(items):
            s += icon("check", 36, y + 50 + k * 28, 18, C["green"])
            s += t(62, y + 64 + k * 28, it, 13.5, 400, C["onSurfV"])
        y += h_ + 12
    s += tonal_btn(16, y + 4, 358, 48, "查看日志", "list")
    return s + end()


def logs_screen():
    s = base() + top_bar("日志", acts=("download", "delete"))
    x = 20
    for lab, on in (("全部", True), ("警告", False), ("错误", False)):
        w_ = 60
        s += rect(x, 104, w_, 34, C["primary"] if on else C["onSurf"], 17, op=None if on else 0.07)
        s += t(x + w_ / 2, 126, lab, 13.5, 600, C["onPrimary"] if on else C["onSurfV"], "middle")
        x += w_ + 8
    s += glass(16, 150, 358, 640, 24, 1)
    lines = [("14:32:08", "I", "GPS 写入成功 #1284 (31.2304,121.4737)"), ("14:32:05", "I", "GPS 写入成功 #1283"),
             ("14:31:50", "W", "RSSI -84 dBm 信号偏弱"), ("14:30:02", "I", "CC0E 校时 applied=1"),
             ("14:30:01", "I", "蓝牙已连接 MTU 517"), ("14:29:58", "I", "开始连接 ILCE-7M4"),
             ("14:02:11", "E", "GATT 133：连接超时"), ("14:02:01", "I", "相机离开范围，进入待命"),
             ("13:58:40", "I", "Wi‑Fi 会话结束（返回设备页）"), ("13:41:07", "I", "下载完成 DSC03456.JPG 6.6 MB")]
    y = 178
    for tm, lv, msg in lines:
        col = {"I": C["onSurfV"], "W": C["amberText"], "E": C["error"]}[lv]
        s += t(30, y, tm, 11, 500, C["onSurfV"], extra='font-family="monospace"')
        s += info_tag(88, y - 13, lv, on_photo=False, size=10, h=17, pad=5, col=col)[0]
        s += t(112, y, msg, 12, 400, C["onSurf"] if lv != "E" else C["error"])
        y += 30
    return s + end()


# ================================================================ notification shade (system-rendered)
def notif_card(x, y, w_, title, text, when, sub=None, col="#0B57D0", dark=False, ic="gps", h=None):
    bg = "#262A31" if dark else "#F1F4FA"
    fg = "#E3E5EA" if dark else "#191C20"
    fg2 = "#B9BEC7" if dark else "#42474E"
    h = h or (92 if sub else 74)
    s = rect(x, y, w_, h, bg, 26)
    s += circle(x + 30, y + 28, 12, col) + icon(ic, x + 22, y + 20, 16, "#fff")
    s += t(x + 52, y + 24, "alpha-gps", 12, 500, fg2) + t(x + 122, y + 24, "· " + when, 12, 400, fg2)
    s += icon("expand_more", x + w_ - 38, y + 12, 22, fg2)
    s += t(x + 52, y + 46, title, 14.5, 600, fg)
    s += t(x + 52, y + 65, text, 13, 400, fg2)
    if sub:
        s += t(x + 52, y + 83, sub, 12, 400, fg2)
    return s


def notif_shade(dark=False):
    w_, h_ = 420, 420
    bg = "#12151A" if dark else "#DCE3EE"
    s = rect(0, 0, w_, h_, bg, 24)
    s += t(24, 34, "9:41", 16, 600, "#E3E5EA" if dark else "#191C20")
    s += t(24, 66, "静默通知", 12.5, 600, "#B9BEC7" if dark else "#42474E")
    c = "#A8C7FA" if dark else "#0B57D0"
    s += notif_card(12, 78, w_ - 24, "Alpha 7 IV · 位置同步中", "已连接，正在向相机写入位置", "现在", col=c, dark=dark)
    s += notif_card(12, 160, w_ - 24, "等待相机", "相机不在附近，回到范围后自动连接", "12 分钟前", col="#7A828C", dark=dark, ic="sync")
    s += t(24, 262, "提醒通知", 12.5, 600, "#B9BEC7" if dark else "#42474E")
    s += notif_card(12, 274, w_ - 24, "相机已断开", "Alpha 7 IV · 14:02 断开，位置写入已停止", "14:02", col="#BA1A1A" if not dark else "#FFB4AB",
                    dark=dark, ic="bluetooth_disabled")
    s += notif_card(12, 356, w_ - 24, "2 台相机 · 位置同步中", "Alpha 7 IV、ZV-E10 II", "现在", col=c, dark=dark, h=74)
    return s, w_, 440
