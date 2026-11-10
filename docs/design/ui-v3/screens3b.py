"""AlphaGPS UI design v3 — screens part B: date list, grid, viewer, settings, help, logs, notifications."""
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
    # one line per card (only cards that are present); capacity only — no firmware / serial here
    for k, row in enumerate(((("卡 1", "sd_card"), ("64 GB · 剩余 21.3 GB", None)),
                             (("卡 2", "sd_card"), ("128 GB · 剩余 96.0 GB", None)))):
        x = 20
        for lab, ic_ in row:
            tg, tw = info_tag(x, 102 + k * 27, lab, on_photo=False, ic=ic_, size=11.5, h=22, pad=8)
            s += tg
            x += tw + 6
    rows = [("11月9日", "周日 · 今天", 227), ("11月8日", "周六", 64), ("11月3日", "周一", 118),
            ("10月28日", "周二", 9), ("10月21日", "周二", 342), ("未知日期", "无拍摄时间", 3)]
    y = 164
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
        s += callout(1, 14, 62) + callout(2, 362, 50) + callout(3, 14, 113) + callout(4, 30, 174)
    return s + end()


# ================================================================ grid
# (format, protected, rating, MB)
TILES = [("J+R", 0, 0, 54.6), ("JPG", 1, 3, 6.6), ("J+R", 0, 5, 51.2), ("RAW", 0, 0, 47.9),
         ("JPG", 0, 0, 7.1), ("J+R", 1, 4, 55.0), ("HEIF", 0, 2, 4.2), ("JPG", 1, 0, 6.9),
         ("J+R", 0, 0, 53.3), ("JPG", 0, 1, 7.4), ("RAW", 1, 5, 48.8), ("J+R", 0, 0, 52.7),
         ("JPG", 0, 0, 6.2), ("J+R", 0, 3, 54.1), ("JPG", 0, 0, 7.7), ("HEIF", 1, 0, 4.0)]


CARD2 = {3, 10, 11}  # tiles stored on card 2 (demo)


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
    if opts.get("card") and (i % len(TILES)) in CARD2:
        # shown only when both cards hold photos; card 1 is never labelled
        g += info_tag(ix + iw - pad, by, "2", size=fs, h=th, pad=4, ic="sd_card", anchor="end")[0]
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
    """Glass popover (level 3) anchored under the 'grid_view' action."""
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
    opts = dict(fmt=fmt, size=size, pr=pr, card=True)
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
        s += top_bar("11月9日", sub="227 张", acts=("grid_view",))
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


def filmstrip(y, cur=5, n=9, sw=38):
    """Small translucent strip of neighbours, overlaid on the photo's bottom."""
    gap = 4
    x0 = W / 2 - sw / 2 - (n // 2) * (sw + gap)
    s = rect(0, y - 6, W, sw + 12, "#000", 0, op=0.28)
    for k in range(n):
        i = cur - n // 2 + k
        x = x0 + k * (sw + gap)
        if k == n // 2:
            s += photo_art(x - 3, y - 3, sw + 6, sw + 6, i, 8)
            s += rect(x - 3, y - 3, sw + 6, sw + 6, "none", 8, "#fff", 2)
        else:
            s += f'<g opacity="{0.7 if abs(k - n // 2) < 3 else 0.42}">' + photo_art(x, y, sw, sw, i, 6) + "</g>"
    return s


def v_topbar(name="DSC03456.JPG", idx="6 / 227", locked=False):
    """Top bar: back / file name / capture time / index. No file size here (it lives once, in the bottom strip)."""
    s = icon("arrow_back", 16, 52, 24, VW, op=0.4 if locked else None)
    s += t(56, 59, name, 16, 600, VW)
    s += t(56, 79, "2026-11-09 14:32:08", 12.5, 400, VW2)
    s += t(W - 20, 68, idx, 13, 500, VW2, "end")
    return s


def v_status(y=98, exif="ok", gps="ok", card2=False, prot=1, rating=4, x=16):
    """Status row in the dark area above the photo: location icon + lock/star tag (+ card 2 tag).
    When the photo is tall enough to reach this row it simply overlays the photo (top scrim behind)."""
    icn, col, op = {"ok": ("location_on", "#7DDB93", None), "none": ("location_off", "#FFC857", None),
                    "loading": ("location_on", VW3, 0.5), "fail": ("location_off", VW3, None)}[gps if exif != "loading" else "loading"]
    s = icon(icn, x, y, 22, col, op=op)
    tx = x + 30
    lab, lock = pr_label(prot, rating)
    if lab is not None or lock:
        tg, tw = vtag(tx, y + 1, lab or "", "lock" if lock else None)
        s += tg
        tx += tw + 6
    if card2:
        tg, tw = vtag(tx, y + 1, "卡 2", "sd_card")
        s += tg
    return s


def gps_chip(x, y, state="ok", w_=None):
    """Location line of the details sheet (coordinates live here, not in the strip)."""
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


def vtag(x, y, label, ic=None, size=11, h=20):
    """Information tag on the dark viewer glass (flat, white 14%)."""
    w_ = text_w(label, size) + 12 + (size + 3 if ic else 0)
    s = rect(x, y, w_, h, "#fff", 4, op=0.14)
    tx = x + 6
    if ic:
        s += icon(ic, tx - 1, y + (h - size - 1) / 2, size + 1, "#fff")
        tx += size + 3
    return s + t(tx, y + h / 2 + size * 0.36, label, size, 600, "#fff"), w_


def info_strip(y_bottom, exif="ok", gps="ok", opts=(), card2=False, x=12, w_=366, prot=1, rating=4,
               fmt="JPG+RAW", size_txt="6.6 MB"):
    """ONE translucent strip for every photo shape. Default row: format · resolution + file size (shown ONCE, here).
    size_txt = what the download button will fetch under the current download mode.
    opts ⊆ {params, lens, hist}: optional compact extras (settings; all off by default). Grows upward."""
    extra = [o for o in ("params", "lens") if o in opts]
    eh = 24 * len(extra)
    if "hist" in opts and not extra:
        eh = 34
    h_ = 40 + eh + (4 if eh else 0)
    y = y_bottom - h_
    s = glass(x, y, w_, h_, 20, "v", shadow=False)
    # --- default row
    cy = y + 20
    tx = x + 14
    tg, tw = vtag(tx, cy - 10, fmt)
    s += tg
    tx += tw + 10
    if exif == "ok":
        s += t(tx, cy + 4.5, "7008×4672", 12.5, 600, VW)
    elif exif == "loading":
        s += rect(tx, cy - 7, 74, 14, "#fff", 5, op=0.12)
    else:
        s += t(tx, cy + 4.5, "—", 12.5, 600, VW3)
    s += t(x + w_ - 44, cy + 4.5, size_txt, 12.5, 600, VW, "end")
    s += icon("expand_less", x + w_ - 34, cy - 12, 24, VW2)
    # --- optional extras (numbers only, no captions)
    ey = y + 40
    hx = x + w_ - 14 - 92
    for o in extra:
        if o == "params":
            if exif == "ok":
                for k, v in enumerate(("f/2.8", "1/250s", "ISO 400", "35mm", "+0.3EV")):
                    s += t(x + 14 + (0, 48, 106, 172, 222)[k], ey + 15, v, 12.5, 600, VW)
            elif exif == "loading":
                s += rect(x + 14, ey + 3, 200, 14, "#fff", 5, op=0.12)
            else:
                s += t(x + 14, ey + 15, "—", 12.5, 600, VW3)
        else:
            if exif == "ok":
                s += t(x + 14, ey + 15, "FE 24-70mm F2.8 GM II", 12, 400, VW2)
            elif exif == "loading":
                s += rect(x + 14, ey + 3, 140, 14, "#fff", 5, op=0.12)
            else:
                s += t(x + 14, ey + 15, "—", 12, 400, VW3)
        ey += 24
    if "hist" in opts:
        if extra:
            s += histogram(hx, y + 42, 92, eh - 2)
        else:
            s += histogram(x + 14, y + 40, w_ - 28, 30)
    return s, y


def v_download(state="normal", y=730):
    """Download control: 48dp pill floating over the photo (fixed colours — viewer chrome is always dark)."""
    if state == "saved":
        return (rect(105, y, 180, 48, "#FFFFFF", 24, op=0.16) + icon("check", 156, y + 13, 22, "#B5BBC4")
                + t(186, y + 30, "已保存", 15.5, 600, "#B5BBC4"))
    if state == "downloading":
        x, w_ = 45, 300
        s = glass(x, y, w_, 48, 24, "v")
        s += circle(x + 28, y + 24, 12, "none", "#555B63", 3) + arc(x + 28, y + 24, 12, 0, 155, "#A8C7FA", 3)
        s += t(x + 50, y + 21, "正在下载 43%", 14, 600, VW)
        s += t(x + 50, y + 38, "2.8 / 6.6 MB", 11, 400, VW2)
        s += text_btn(x + w_ - 22, y + 24, "取消", "#A8C7FA", anchor="end")
        return s
    s = rect(105, y, 180, 48, "#A8C7FA", 24, op=0.94, extra='filter="url(#shv)"')
    s += rect(105.6, y + 0.6, 178.8, 46.8, "none", 24, "#FFFFFF", 1, op=0.55)
    s += icon("download", 160, y + 13, 22, "#062E6F") + t(190, y + 30, "下载", 15.5, 600, "#062E6F")
    return s


def viewer_screen(orient="land", state="normal", exif="ok", gps="ok", sheet=False, callouts=False, opts=(), card2=False,
                  original=False):
    """One layout for every photo shape. orient only changes the demo photo (land 3:2 | port 2:3).
    state: normal | downloading | saved. opts: info-strip extras enabled in settings.
    original: download mode Original → the strip shows the sum of all files of this shot."""
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
        gc, gw = gps_chip(16, y + 26, gps)
        s += gc
        s += circle(W - 40, y + 44, 20, "#A8C7FA") + icon("download", W - 51, y + 33, 22, "#062E6F")
        s += t(20, y + 82, "海拔 12 m · GPS 时间 06:32:07 UTC", 12, 400, VW3)
        s += t(20, y + 112, "拍摄参数", 12.5, 600, "#A8C7FA")
        cells = [("f/2.8", "光圈"), ("1/250 s", "快门"), ("ISO 400", "感光度"), ("35 mm", "焦距"), ("+0.3 EV", "曝光补偿"),
                 ("A", "模式")]
        for i, (v, c) in enumerate(cells):
            cx = 16 + (i % 3) * 122
            cy = y + 122 + (i // 3) * 56
            s += rect(cx, cy, 114, 48, "#fff", 12, op=0.06)
            s += t(cx + 12, cy + 22, v, 15, 600, VW) + t(cx + 12, cy + 39, c, 10.5, 400, VW3)
        s += icon("camera", 18, y + 240, 16, VW2) + t(40, y + 253, "FE 24-70mm F2.8 GM II", 12.5, 500, VW2)
        s += t(20, y + 284, "直方图", 12.5, 600, "#A8C7FA")
        s += histogram(16, y + 294, 358, 62, rgb=True)
        s += t(20, y + 384, "文件", 12.5, 600, "#A8C7FA")
        s += t(W - 20, y + 384, "7008 × 4672 · 32.7 MP", 12, 500, VW2, "end")
        s += info_tag(20, y + 396, "JPG", size=10.5)[0] + t(54, y + 409, "6.6 MB", 12.5, 500, VW)
        s += info_tag(112, y + 396, "RAW", size=10.5)[0] + t(148, y + 409, "48.0 MB", 12.5, 500, VW)
        s += t(W - 20, y + 409, "合计 54.6 MB", 12.5, 400, VW2, "end")
        s += icon("lock", 19, y + 424, 14, VW2) + t(38, y + 436, "已保护", 12, 400, VW2)
        s += t(92, y + 436, "★★★★", 12, 600, RATING[4])
        s += icon("sd_card", 150, y + 424, 14, VW2) + t(168, y + 436, "卡 2", 12, 400, VW2)
        s += icon("check_circle", 250, y + 424, 14, "#7DDB93") + t(268, y + 436, "已保存 JPG", 12, 500, "#7DDB93")
        s += nav_pill(VW)
        set_theme(was)
        return s
    # photo: fit-centre in the whole window; chrome floats over it
    if orient == "land":
        s += photo_art(0, 292, 390, 260, 5)
    else:
        s += photo_art(0, 130, 390, 585, 2)
    s += f'<rect x="0" y="0" width="{W}" height="130" fill="url(#vtop)"/>'
    s += f'<rect x="0" y="{H - 290}" width="{W}" height="290" fill="url(#vbot)" opacity="0.9"/>'
    name = ("DSC03456.JPG", "6 / 227") if orient == "land" else ("DSC03461.JPG", "11 / 227")
    s += sbar(VW) + v_topbar(*name, locked=locked)
    s += v_status(98, exif, gps, card2)
    size_txt = ("54.6 MB" if orient == "land" else "55.1 MB") if original else ("6.6 MB" if orient == "land" else "7.1 MB")
    strip, sy = info_strip(658, exif, gps, opts, card2, size_txt=size_txt)
    s += strip
    s += filmstrip(674, cur=5 if orient == "land" else 10)
    if locked:
        s += rect(0, 668, W, 50, "#000", op=0.5)
    s += v_download(state)
    if callouts:
        for n_, (cx, cy) in enumerate(((16, 46), (10, 96), (10, sy + 4), (382, sy + 4), (10, 680), (98, 736)), 1):
            s += callout(n_, cx, cy)
    s += nav_pill(VW)
    set_theme(was)
    return s


# ================================================================ settings / help / logs
def list_row(y, title, value=None, ic=None, kind="nav", sub=None, h=60, on=True, x=16, w_=358):
    """kind: nav (chevron) | link (opens outside) | copy (copy + open) | switch | none (static information)."""
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
    elif kind == "copy":
        s += icon("content_copy", x + w_ - 74, y + h / 2 - 10, 20, C["onSurfV"])
        s += icon("open_in_new", x + w_ - 38, y + h / 2 - 10, 20, C["onSurfV"])
    elif kind != "none":
        if value:
            s += t(x + w_ - 42, y + h / 2 + 5, value, 13.5, 400, C["onSurfV"], "end")
        s += icon("chevron_right" if kind == "nav" else "open_in_new", x + w_ - 38, y + h / 2 - (12 if kind == "nav" else 10),
                  24 if kind == "nav" else 20, C["onSurfV"])
    return s


def settings_screen():
    s = base() + top_bar("设置")
    s += glass(16, 104, 358, 76, 24, 1)
    s += list_row(104, "位置同步", ic="gps", kind="switch", sub="总开关：关闭后不连接任何相机", h=76)
    s += section_label(28, 206, "连接与定位")
    s += glass(16, 216, 358, 162, 24, 1)
    s += list_row(216, "位置来源", "融合定位", "location_on", h=54)
    s += list_row(270, "提示音", "连接 / 断开", "volume_up", h=54)
    s += list_row(324, "电池优化", "未限制", "battery_full", h=54)
    s += section_label(28, 404, "照片")
    s += glass(16, 414, 358, 54, 24, 1)
    s += list_row(414, "预览信息条", "仅基础信息", "image", h=54)
    s += section_label(28, 494, "通用")
    s += glass(16, 504, 358, 162, 24, 1)
    s += list_row(504, "语言", "简体中文", "language", h=54)
    s += list_row(558, "日志级别", "信息", "list", h=54)
    s += list_row(612, "更新日志", "0.1.5", "info", h=54)
    s += t(195, 700, "AlphaGPS 0.1.5 · cn.vectory.alphagps", 12, 400, C["onSurfV"], "middle")
    return s + end()


def settings_sub_screen():
    """Sub page: what the viewer's info strip shows (three optional extras, all off by default)."""
    s = tile_defs() + base() + top_bar("预览信息条")
    # live preview of the strip with the current switches
    s += rect(16, 104, 358, 150, "#000", 24)
    s += f'<clipPath id="pvw"><rect x="16" y="104" width="358" height="150" rx="24"/></clipPath><g clip-path="url(#pvw)">'
    s += photo_art(16, 104, 358, 150, 5) + "</g>"
    s += info_strip(244, x=26, w_=338)[0]
    s += t(28, 280, "始终显示：格式 · 分辨率、文件大小", 12, 400, C["onSurfV"])
    s += section_label(28, 318, "额外显示在信息条里")
    s += glass(16, 330, 358, 216, 24, 1)
    s += list_row(330, "拍摄参数", kind="switch", sub="光圈、快门、ISO、焦距、曝光补偿（仅数值）", h=72, on=False)
    s += list_row(402, "镜头信息", kind="switch", sub="镜头型号", h=72, on=False)
    s += list_row(474, "直方图", kind="switch", sub="亮度分布小图", h=72, on=False)
    s += glass(16, 558, 358, 62, 20, 1, shadow=False)
    s += icon("info", 30, 578, 20, C["primary"])
    s += t(60, 584, "上滑打开的详情始终包含全部信息，", 12.5, 400, C["onSurfV"])
    s += t(60, 603, "不受这里的开关影响。", 12.5, 400, C["onSurfV"])
    return s + end()


def help_screen(callouts=False):
    s = base() + top_bar("帮助")
    s += glass(16, 104, 358, 96, 24, 1, tint=C["primaryC"], a=0.62 if mode() == "light" else 0.4)
    s += t(36, 138, "关于 AlphaGPS", 16.5, 600, C["onPrimaryC"])
    s += t(36, 162, "版本、来源、许可证、源码与隐私", 12.5, 400, C["onPrimaryC"])
    s += t(36, 181, "基于 Saschl/alpha-gps 修改 · GPL-3.0", 12.5, 400, C["onPrimaryC"])
    s += icon("chevron_right", 338, 140, 24, C["onPrimaryC"])
    s += glass(16, 212, 358, 72, 24, 1)
    s += icon_tile(52, 248, "search", 22)
    s += t(88, 244, "排障指南", 15.5, 600) + t(88, 265, "连不上、找不到相机时先看这里", 12.5, 400, C["onSurfV"])
    s += icon("chevron_right", 338, 236, 24, C["onSurfV"])
    s += section_label(28, 312, "常见问题")
    s += glass(16, 324, 358, 222, 24, 1)
    s += t(36, 356, "有文档或反馈渠道吗？", 15, 600) + icon("expand_less", 338, 338, 24, C["onSurfV"])
    s += t(36, 382, "源码与问题反馈都在代码仓库（两处地址）。", 12.5, 400, C["onSurfV"])
    for i, (lab, ic_) in enumerate((("代码仓库（GitHub）", "open_in_new"), ("代码仓库（Primary forge）", "open_in_new"), ("反馈问题（Issue）", "open_in_new"))):
        yy = 396 + i * 46
        s += rect(28, yy, 334, 40, C["onSurf"], 14, op=0.05 if mode() == "light" else 0.08)
        s += icon(ic_, 40, yy + 10, 20, C["primary"] if ic_ != "favorite" else C["tertiary"])
        s += t(70, yy + 25, lab, 14, 500, C["primary"])
    y = 556
    for q in ("我的位置数据会上传吗？", "如何开启后台同步？", "相机没有出现在列表里", "位置不准或写入延迟"):
        s += glass(16, y, 358, 56, 20, 1, shadow=False)
        s += t(36, y + 33, q, 14.5, 500) + icon("expand_more", 338, y + 16, 24, C["onSurfV"])
        y += 64
    if callouts:
        s += callout(1, 26, 222) + callout(2, 26, 494) + callout(3, 26, 112)
    return s + end()


def about_screen(callouts=False):
    s = base() + top_bar("关于")
    s += glass(16, 100, 358, 96, 24, 1)
    s += brand_icon(30, 120, 56)
    s += t(98, 136, "AlphaGPS", 19, 600)
    s += t(98, 158, "0.1.5 · cn.vectory.alphagps", 12.5, 400, C["onSurfV"])
    s += t(98, 178, "基于 Saschl/alpha-gps 修改的分支版本", 12.5, 400, C["onSurfV"])
    s += section_label(28, 220, "来源与许可")
    s += glass(16, 230, 358, 232, 24, 1)
    s += list_row(230, "上游项目", ic="code", kind="copy", sub="Saschl/alpha-gps · 作者 Saschl", h=58)
    s += list_row(288, "修改声明", ic="edit", kind="none", sub="由 mfreceiver 修改，自 2026 年 11 月起", h=58)
    s += list_row(346, "许可证", ic="gavel", sub="GPL-3.0，与上游项目相同", h=58)
    s += list_row(404, "开源组件许可", ic="list", sub="libheif、libde265、Compose、Haze 等", h=58)
    s += section_label(28, 486, "源码")
    s += glass(16, 496, 358, 116, 24, 1)
    s += list_row(496, "GitHub", ic="code", kind="copy", sub="github.com/mfreceiver/alpha-gps", h=58)
    s += list_row(554, "Primary forge", ic="code", kind="copy", sub="forge.example.com:443/owner/alphagps", h=58)
    s += section_label(28, 636, "其它")
    s += glass(16, 646, 358, 116, 24, 1)
    s += list_row(646, "隐私说明", ic="shield", sub="不收集、不上传任何数据", h=58)
    s += list_row(704, "支持上游作者", ic="favorite", kind="link", sub="Buy Me a Coffee · 赞助归上游作者 Saschl", h=58)
    s += t(195, 790, "本应用与 Sony 无关联；Sony、α 等为其各自所有者的商标。", 11, 400, C["onSurfV"], "middle")
    if callouts:
        s += callout(1, 26, 240) + callout(2, 26, 298) + callout(3, 26, 356) + callout(4, 26, 506)
        s += callout(5, 26, 656) + callout(6, 26, 714) + callout(7, 300, 248)
    return s + end()


def privacy_screen():
    """About → Privacy: the rewritten statement in plain language (full text also in privacy.md)."""
    s = base() + top_bar("隐私说明")
    s += glass(16, 100, 358, 84, 24, 1, tint=C["greenC"], a=0.7 if mode() == "light" else 0.45)
    s += icon("shield", 32, 120, 26, C["onGreenC"])
    s += t(70, 134, "AlphaGPS 不收集、不上传任何数据", 15.5, 600, C["onGreenC"])
    s += t(70, 158, "无账号 · 无统计 · 无崩溃上报 · 无广告", 12.5, 400, C["onGreenC"])
    items = [("location_on", "位置", "只写入你自己的相机，不上传"),
             ("bluetooth", "蓝牙与附近设备", "只用来发现并连接你的相机"),
             ("wifi", "相机 Wi‑Fi 与本地网络", "照片从相机下载到本机，不上传"),
             ("notifications", "通知", "显示连接与同步状态"),
             ("list", "日志", "只保存在本机；复制后由你自行决定是否分享")]
    y = 196
    s += glass(16, y, 358, len(items) * 62 + 8, 24, 1)
    for i, (ic_, a_, b_) in enumerate(items):
        s += list_row(y + 4 + i * 62, a_, ic=ic_, kind="none", sub=b_, h=62)
    y += len(items) * 62 + 20
    s += glass(16, y, 358, 116, 24, 1)
    s += list_row(y, "发布者", ic="code", kind="none", sub="mfreceiver（AlphaGPS，开源，GPL‑3.0）", h=58)
    s += list_row(y + 58, "联系与反馈", ic="open_in_new", kind="nav", sub="两处代码仓库的 Issue", h=58)
    s += t(28, y + 142, "完整文本见仓库 privacy.md；应用内置同一份，离线可读。", 12, 400, C["onSurfV"])
    return s + end()


def licenses_screen():
    s = base() + top_bar("开源组件许可")
    rows = [("AlphaGPS（本应用）", "GPL-3.0 · mfreceiver，基于 Saschl/alpha-gps"),
            ("libheif 1.23.5", "LGPL-3.0-or-later · 源码构建，动态链接"),
            ("libde265 1.1.3", "LGPL-3.0-or-later · 源码构建，动态链接"),
            ("Kotlin · kotlinx", "Apache-2.0"),
            ("Jetpack Compose · AndroidX · Room", "Apache-2.0"),
            ("Compose Multiplatform", "Apache-2.0"),
            ("Haze", "Apache-2.0 · 背景模糊"),
            ("Timber · Accompanist", "Apache-2.0"),
            ("Material Icons / Material Symbols", "Apache-2.0 · 应用内功能图标")]
    s += glass(16, 104, 358, len(rows) * 60 + 8, 24, 1)
    for i, (a_, b_) in enumerate(rows):
        s += list_row(108 + i * 60, a_, sub=b_, h=60)
    s += t(28, 104 + len(rows) * 60 + 36, "轻触任一项查看许可全文；全文随应用内置，离线可读。", 12, 400, C["onSurfV"])
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
    s = base() + top_bar("日志", acts=("content_copy", "download", "delete"))
    x = 20
    for lab, on in (("全部", True), ("警告", False), ("错误", False)):
        w_ = 60
        s += rect(x, 104, w_, 34, C["primary"] if on else C["onSurf"], 17, op=None if on else 0.07)
        s += t(x + w_ / 2, 126, lab, 13.5, 600, C["onPrimary"] if on else C["onSurfV"], "middle")
        x += w_ + 8
    s += glass(16, 150, 358, 590, 24, 1)
    lines = [("14:32:08", "I", "GPS 写入成功 #1284"), ("14:32:05", "I", "GPS 写入成功 #1283"),
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
    # snackbar after tapping 'copy'
    dk = mode() == "dark"
    s += rect(16, 756, 358, 48, "#E0E2E8" if dk else "#2E3135", 12)
    s += icon("check", 30, 769, 22, "#191C20" if dk else "#FFFFFF")
    s += t(62, 785, "已复制 10 条日志", 14, 500, "#191C20" if dk else "#FFFFFF")
    return s + end().replace(nav_pill(), "")


# ================================================================ notification shade (system-rendered)
def notif_card(x, y, w_, title, text, when, sub=None, col="#0B57D0", dark=False, ic="gps", h=None):
    bg = "#262A31" if dark else "#F1F4FA"
    fg = "#E3E5EA" if dark else "#191C20"
    fg2 = "#B9BEC7" if dark else "#42474E"
    h = h or (92 if sub else 74)
    s = rect(x, y, w_, h, bg, 26)
    s += circle(x + 30, y + 28, 12, col) + icon(ic, x + 22, y + 20, 16, "#fff")
    s += t(x + 52, y + 24, "AlphaGPS", 12, 500, fg2) + t(x + 118, y + 24, "· " + when, 12, 400, fg2)
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
