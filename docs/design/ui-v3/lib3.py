"""AlphaGPS UI design v3 — SVG primitives, colour themes and the glass surface tokens.

Standalone (no dependency on the v1/v2 toolchain under ../history).
Mockups approximate backdrop blur statically: a soft gradient backdrop plus translucent fills.
"""
import html
import math

from icons3 import ICONS

FONT = "'Source Han Sans CN','Noto Sans CJK SC','Noto Sans CJK JP',sans-serif"
W, H = 390, 844

# ---------------------------------------------------------------- colour themes (fixed blue, no dynamic colour)
LIGHT = dict(
    bg="#F3F6FC", bg2="#E9EFFA", surface="#F9FAFE", card="#EDF1F7", hi="#E1E6ED",
    outline="#72777F", outlineV="#C2C7CF", onSurf="#191C20", onSurfV="#42474E",
    primary="#0B57D0", onPrimary="#FFFFFF", primaryC="#D3E3FD", onPrimaryC="#041E49",
    secC="#D8E3F8", onSecC="#111C2B", tertiary="#6B4FA8", tertiaryC="#E9DDFF",
    error="#BA1A1A", onError="#FFFFFF", errorC="#FFDAD6", onErrorC="#410002",
    green="#1B6E3A", greenC="#C8F0D2", onGreenC="#00210D",
    amber="#F9A825", amberText="#7A5200", amberC="#FFE9B3", onAmberC="#261900",
    scrim="#000000", scrimA=0.32, sysbar="#191C20", frame="#9AA3AE",
    blob1="#A8C7FA", blob2="#CDBDF7", blob3="#A6E6D6",
)
DARK = dict(
    bg="#0E1217", bg2="#121821", surface="#101418", card="#1D2125", hi="#32363B",
    outline="#8C9199", outlineV="#42474E", onSurf="#E0E2E8", onSurfV="#C2C7CF",
    primary="#A8C7FA", onPrimary="#062E6F", primaryC="#0842A0", onPrimaryC="#D3E3FD",
    secC="#3C4758", onSecC="#D8E3F8", tertiary="#CFBCFF", tertiaryC="#4F378A",
    error="#FFB4AB", onError="#690005", errorC="#93000A", onErrorC="#FFDAD6",
    green="#7DDB93", greenC="#0B5227", onGreenC="#C8F0D2",
    amber="#FFC857", amberText="#FFC857", amberC="#5C4200", onAmberC="#FFE9B3",
    scrim="#000000", scrimA=0.52, sysbar="#E0E2E8", frame="#5A626C",
    blob1="#0B57D0", blob2="#5B3FA8", blob3="#0E6B5C",
)
THEMES = {"light": LIGHT, "dark": DARK}
C = {}
_mode = ["light"]
RATING = {1: "#90A4AE", 2: "#4FC3F7", 3: "#66BB6A", 4: "#FFB300", 5: "#FF7043"}

# ---------------------------------------------------------------- glass tokens
# level -> (tint, fill alpha, stroke alpha top, stroke alpha bottom, sheen alpha, blur dp, shadow (dy, blur, colour, alpha))
GLASS = {
    "light": {
        1: dict(tint="#FFFFFF", a=0.58, rimT=0.95, rimB=0.35, sheen=0.30, blur=20, sh=(4, 16, "#0B57D0", 0.10)),
        2: dict(tint="#F9FAFE", a=0.78, rimT=0.95, rimB=0.40, sheen=0.22, blur=28, sh=(6, 20, "#0A1F44", 0.18)),
        3: dict(tint="#FFFFFF", a=0.86, rimT=1.00, rimB=0.45, sheen=0.18, blur=32, sh=(12, 32, "#0A1F44", 0.24)),
    },
    "dark": {
        1: dict(tint="#FFFFFF", a=0.07, rimT=0.22, rimB=0.06, sheen=0.05, blur=20, sh=(4, 16, "#000000", 0.30)),
        2: dict(tint="#20252B", a=0.74, rimT=0.26, rimB=0.08, sheen=0.06, blur=28, sh=(6, 20, "#000000", 0.42)),
        3: dict(tint="#272C33", a=0.86, rimT=0.30, rimB=0.10, sheen=0.07, blur=32, sh=(12, 32, "#000000", 0.55)),
    },
}
# Always-dark glass used over photos (viewer chrome)
GLASS_V = dict(tint="#101418", a=0.66, rimT=0.26, rimB=0.08, sheen=0.05, blur=24, sh=(4, 16, "#000000", 0.40))
# API < 31 fallback (no blur): opaque-ish solid + hairline + micro gradient
FALLBACK = {
    "light": {1: ("#F5F8FD", 0.94), 2: ("#F9FAFE", 0.96), 3: ("#FFFFFF", 0.98)},
    "dark": {1: ("#1A1F25", 0.94), 2: ("#20252B", 0.96), 3: ("#272C33", 0.98)},
}


def set_theme(name):
    _mode[0] = name
    C.clear()
    C.update(THEMES[name])
    C["callout"] = "#E5322D"


def mode():
    return _mode[0]


set_theme("light")


# ---------------------------------------------------------------- primitives
def esc(s):
    return html.escape(str(s), quote=False)


def t(x, y, s, size=14, weight=400, fill=None, anchor="start", extra=""):
    fill = fill or C["onSurf"]
    return (f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{fill}" '
            f'text-anchor="{anchor}" {extra}>{esc(s)}</text>')


def rect(x, y, w, h, fill, r=0, stroke=None, sw=1, op=None, extra=""):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"'
    if stroke:
        s += f' stroke="{stroke}" stroke-width="{sw}"'
    if op is not None:
        s += f' opacity="{op}"'
    return s + f' {extra}/>'


def circle(cx, cy, r, fill, stroke=None, sw=1, op=None):
    s = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"'
    if stroke:
        s += f' stroke="{stroke}" stroke-width="{sw}"'
    if op is not None:
        s += f' opacity="{op}"'
    return s + '/>'


def line(x1, y1, x2, y2, stroke, sw=1, dash=None, extra=""):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" stroke-width="{sw}"{d} {extra}/>'


def icon(name, x, y, size=24, fill=None, op=None):
    """x,y = top-left of the icon box."""
    fill = fill or C["onSurfV"]
    sc = size / 24.0
    o = f' opacity="{op}"' if op is not None else ""
    return f'<path d="{ICONS[name]}" transform="translate({x},{y}) scale({sc:.4f})" fill="{fill}"{o}/>'


def callout(n, x, y, r=9):
    return circle(x, y, r, C["callout"], "#fff", 1.2) + t(x, y + 4, str(n), 11, 700, "#fff", "middle")


def blend(c1, c2, k):
    """k of c2 over c1 (hex #rrggbb)."""
    a_ = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    b_ = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * k):02X}" for x, y in zip(a_, b_))


def lum(hx):
    v = []
    for i in (1, 3, 5):
        c = int(hx[i:i + 2], 16) / 255
        v.append(c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]


def contrast(a_, b_):
    la, lb = lum(a_), lum(b_)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def arc(cx, cy, r, a0, a1, color, sw=3, cap="round", op=None):
    """Arc from angle a0 to a1 (degrees, 0 = 12 o'clock, clockwise)."""
    def pt(a_):
        rad = math.radians(a_ - 90)
        return cx + r * math.cos(rad), cy + r * math.sin(rad)
    x0, y0 = pt(a0)
    x1, y1 = pt(a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    o = f' opacity="{op}"' if op is not None else ""
    return (f'<path d="M{x0:.2f} {y0:.2f} A{r} {r} 0 {large} 1 {x1:.2f} {y1:.2f}" fill="none" '
            f'stroke="{color}" stroke-width="{sw}" stroke-linecap="{cap}"{o}/>')


def seg_ring(cx, cy, r, n, done, active=True, sw=5, gap=14, col=None, fail=False):
    """Segmented step ring: n segments, `done` finished, the next one 'active' (half-drawn)."""
    col = col or C["primary"]
    s = ""
    span = 360.0 / n
    for i in range(n):
        a0 = i * span + gap / 2
        a1 = (i + 1) * span - gap / 2
        s += arc(cx, cy, r, a0, a1, C["outlineV"], sw, op=0.7 if mode() == "light" else 0.9)
        if i < done:
            s += arc(cx, cy, r, a0, a1, col, sw)
        elif i == done and active:
            s += arc(cx, cy, r, a0, a0 + (a1 - a0) * 0.55, C["error"] if fail else col, sw)
    return s


def battery(x, y, pct, color=None, w=24, h=12, dim=False):
    color = color or (C["green"] if pct is not None and pct > 20 else C["error"])
    op = 0.45 if dim else 1
    s = f'<g opacity="{op}">'
    s += rect(x, y, w, h, "none", 3, C["onSurfV"], 1.4)
    s += rect(x + w + 1, y + 3.5, 2, h - 7, C["onSurfV"], 1)
    if pct is not None:
        s += rect(x + 2, y + 2, max(1.5, (w - 4) * pct / 100.0), h - 4, color, 1.5)
    else:
        s += t(x + w / 2, y + h - 2, "?", 9, 700, C["onSurfV"], "middle")
    return s + '</g>'


def bars(x, y, level, color, total=4, dim=False):
    op = 0.45 if dim else 1
    s = f'<g opacity="{op}">'
    for i in range(total):
        hh = 5 + i * 4
        fill = color if i < level else C["outlineV"]
        s += rect(x + i * 6, y + 18 - hh, 4, hh, fill, 1.5)
    return s + '</g>'


def spinner(cx, cy, r=8, color=None, sw=2.5):
    color = color or C["primary"]
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{C["outlineV"]}" stroke-width="{sw}" opacity="0.6"/>'
            + arc(cx, cy, r, 0, 100, color, sw))


# ---------------------------------------------------------------- defs / backdrop / glass
def defs():
    m = mode()
    s = "<defs>"
    for i, key in enumerate(("blob1", "blob2", "blob3"), 1):
        s += (f'<radialGradient id="bl{i}"><stop offset="0" stop-color="{C[key]}" stop-opacity="1"/>'
              f'<stop offset="1" stop-color="{C[key]}" stop-opacity="0"/></radialGradient>')
    tables = dict(GLASS[m])
    tables["v"] = GLASS_V
    for lv, g in tables.items():
        s += (f'<linearGradient id="rim{lv}" x1="0" y1="0" x2="0" y2="1">'
              f'<stop offset="0" stop-color="#fff" stop-opacity="{g["rimT"]}"/>'
              f'<stop offset="1" stop-color="#fff" stop-opacity="{g["rimB"]}"/></linearGradient>')
        s += (f'<linearGradient id="sheen{lv}" x1="0" y1="0" x2="0.9" y2="1">'
              f'<stop offset="0" stop-color="#fff" stop-opacity="{g["sheen"]}"/>'
              f'<stop offset="0.55" stop-color="#fff" stop-opacity="0"/></linearGradient>')
        dy, bl, col, al = g["sh"]
        s += (f'<filter id="sh{lv}" x="-30%" y="-30%" width="160%" height="180%">'
              f'<feDropShadow dx="0" dy="{dy}" stdDeviation="{bl / 2}" flood-color="{col}" flood-opacity="{al}"/></filter>')
    # primary button gradient + glow
    p = C["primary"]
    p2 = blend(p, "#FFFFFF", 0.16) if m == "light" else blend(p, "#FFFFFF", 0.25)
    s += (f'<linearGradient id="pbtn" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{p2}"/>'
          f'<stop offset="1" stop-color="{p}"/></linearGradient>')
    s += (f'<filter id="pglow" x="-30%" y="-40%" width="160%" height="220%">'
          f'<feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="{"#0B57D0" if m == "light" else "#000"}" '
          f'flood-opacity="{0.34 if m == "light" else 0.5}"/></filter>')
    s += ('<linearGradient id="vtop" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity="0.72"/>'
          '<stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient>')
    s += ('<linearGradient id="vbot" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#000" stop-opacity="0.8"/>'
          '<stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient>')
    s += '<filter id="blur6"><feGaussianBlur stdDeviation="6"/></filter>'
    s += '<filter id="blur3"><feGaussianBlur stdDeviation="3"/></filter>'
    return s + "</defs>"


def backdrop(w=W, h=H):
    """G0: the app backdrop — soft blue gradient with light blobs (what the glass refracts)."""
    op = (0.60, 0.42, 0.36) if mode() == "light" else (0.42, 0.30, 0.26)
    s = rect(0, 0, w, h, C["bg"])
    s += f'<circle cx="{w * 0.12:.0f}" cy="{h * 0.10:.0f}" r="{w * 0.78:.0f}" fill="url(#bl1)" opacity="{op[0]}"/>'
    s += f'<circle cx="{w * 1.02:.0f}" cy="{h * 0.46:.0f}" r="{w * 0.70:.0f}" fill="url(#bl2)" opacity="{op[1]}"/>'
    s += f'<circle cx="{w * 0.20:.0f}" cy="{h * 0.95:.0f}" r="{w * 0.75:.0f}" fill="url(#bl3)" opacity="{op[2]}"/>'
    return s


def glass(x, y, w, h, r=24, level=1, shadow=True, tint=None, a=None, sheen=True):
    """A glass surface. level 1 = card, 2 = floating bar/pill, 3 = dialog/sheet/popup, 'v' = viewer (always dark)."""
    g = GLASS_V if level == "v" else GLASS[mode()][level]
    f = f' filter="url(#sh{level})"' if shadow else ""
    s = ""
    if level == 3:
        # mockup-only: emulate the 32dp backdrop blur (content behind is washed out, not legible)
        s += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{C["bg"]}" fill-opacity="0.72"/>'
    s += (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{tint or g["tint"]}" '
         f'fill-opacity="{a if a is not None else g["a"]}"{f}/>')
    if sheen:
        s += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="url(#sheen{level})"/>'
    s += (f'<rect x="{x + 0.5}" y="{y + 0.5}" width="{w - 1}" height="{h - 1}" rx="{max(r - 0.5, 0)}" fill="none" '
          f'stroke="url(#rim{level})" stroke-width="1"/>')
    return s


def glass_fallback(x, y, w, h, r=24, level=1):
    """API < 31 rendering of the same surface: near-opaque solid + hairline + 3 % vertical gradient."""
    col, a_ = FALLBACK[mode()][level]
    stroke = "#FFFFFF" if mode() == "dark" else "#C2C7CF"
    so = 0.14 if mode() == "dark" else 0.7
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{col}" fill-opacity="{a_}"/>'
            f'<rect x="{x + 0.5}" y="{y + 0.5}" width="{w - 1}" height="{h - 1}" rx="{r}" fill="none" stroke="{stroke}" '
            f'stroke-opacity="{so}" stroke-width="1"/>')


def scrim(k=None, w=W, h=H):
    return rect(0, 0, w, h, C["scrim"], 0, op=C["scrimA"] if k is None else k)


def svg_wrap(w, h, body, scale=1.0, bg=None):
    b = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * scale:.1f}" height="{h * scale:.1f}" '
            f'viewBox="0 0 {w} {h}" font-family="{FONT}">{defs()}{b}{body}</svg>')


def phone_frame(body, w=W, h=H, scale=1.0, extra_defs=""):
    clip = f'<clipPath id="pc"><rect width="{w}" height="{h}" rx="30"/></clipPath>'
    shell = (f'<rect x="0.6" y="0.6" width="{w - 1.2}" height="{h - 1.2}" rx="30" fill="none" '
             f'stroke="{C["frame"]}" stroke-width="1.2"/>')
    return svg_wrap(w, h, f'{extra_defs}{clip}<g clip-path="url(#pc)">{body}</g>{shell}', scale)


# ---------------------------------------------------------------- shared chrome / widgets
def sbar(col=None):
    col = col or C["sysbar"]
    s = t(26, 22, "9:41", 13.5, 600, col)
    s += bars(318, 6, 4, col).replace(C["outlineV"], col)
    s += icon("wifi", 296, 6, 17, col)
    s += rect(348, 9, 22, 11, "none", 3, col, 1.2) + rect(350, 11, 15, 7, col, 1.5) + rect(371, 12.5, 2, 4, col, 1)
    return s


def nav_pill(col=None):
    return rect(W / 2 - 54, H - 14, 108, 4.5, col or C["sysbar"], 2.5, op=0.55)


def top_bar(title, sub=None, back="arrow_back", acts=(), y=32, tcol=None, icol=None, size=22, acol=None):
    """Left-aligned title (Q14). acts = icon names, right-aligned, 48dp slots."""
    tcol = tcol or C["onSurf"]
    icol = icol or C["onSurfV"]
    s = ""
    x = 20
    if back:
        s += icon(back, 16, y + 20, 24, icol)
        x = 56
    if sub:
        s += t(x, y + 28, title, 18, 600, tcol) + t(x, y + 47, sub, 12.5, 400, C["onSurfV"] if tcol == C["onSurf"] else icol)
    else:
        s += t(x, y + 40, title, size, 600, tcol)
    ax = W - 12
    for a_ in reversed(acts):
        ax -= 44
        col = acol.get(a_, icol) if acol else icol
        s += icon(a_, ax + 10, y + 20, 24, col)
    return s


def switch(x, y, on=True, w=48, h=28):
    if on:
        return rect(x, y, w, h, C["primary"], h / 2) + circle(x + w - h / 2, y + h / 2, h / 2 - 4, C["onPrimary"])
    return (rect(x, y, w, h, C["hi"], h / 2, C["outline"], 1.6, op=0.9)
            + circle(x + h / 2, y + h / 2, h / 2 - 7, C["outline"]))


def text_w(label, size):
    return sum(size * (1.0 if ord(ch) > 0x2E80 else (0.3 if ch == " " else 0.58)) for ch in label)


def primary_btn(x, y, w, h, label, ic=None, size=15.5, r=None, disabled=False, sub=None):
    """OPERATION button: filled primary gradient + glow + rim highlight (raised glass)."""
    r = h / 2 if r is None else r
    if disabled:
        s = rect(x, y, w, h, C["onSurf"], r, op=0.12)
        col = blend(C["bg"], C["onSurf"], 0.45)
    else:
        s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="url(#pbtn)" filter="url(#pglow)"/>'
        s += (f'<rect x="{x + 0.6}" y="{y + 0.6}" width="{w - 1.2}" height="{h - 1.2}" rx="{r}" fill="none" '
              f'stroke="#fff" stroke-opacity="{0.35 if mode() == "light" else 0.5}" stroke-width="1"/>')
        col = C["onPrimary"]
    tw = text_w(label, size)
    if ic:
        tot = 22 + 8 + tw
        sx = x + (w - tot) / 2
        s += icon(ic, sx, y + h / 2 - 11, 22, col)
        s += t(sx + 30, y + h / 2 + size * 0.36, label, size, 600, col)
    else:
        s += t(x + w / 2, y + h / 2 + size * 0.36, label, size, 600, col, "middle")
    return s


def tonal_btn(x, y, w, h, label, ic=None, size=14.5, col=None, tcol=None):
    col = col or C["primaryC"]
    tcol = tcol or C["onPrimaryC"]
    s = rect(x, y, w, h, col, h / 2)
    if ic:
        tw = text_w(label, size)
        sx = x + (w - (20 + 6 + tw)) / 2
        s += icon(ic, sx, y + h / 2 - 10, 20, tcol) + t(sx + 26, y + h / 2 + size * 0.36, label, size, 600, tcol)
    else:
        s += t(x + w / 2, y + h / 2 + size * 0.36, label, size, 600, tcol, "middle")
    return s


def text_btn(cx, cy, label, col=None, size=14.5, anchor="middle"):
    return t(cx, cy + 5, label, size, 600, col or C["primary"], anchor)


def pill_btn(x, y, w, label, kind="secondary", h=48, size=15):
    """Dialog pill button. kind: secondary (pale glass) | primary (filled) | danger (filled red)."""
    if kind == "secondary":
        s = rect(x, y, w, h, C["onSurf"], h / 2, op=0.07 if mode() == "light" else 0.13)
        s += rect(x + 0.5, y + 0.5, w - 1, h - 1, "none", h / 2, "#FFFFFF", 1, op=0.7 if mode() == "light" else 0.16)
        col = C["onSurf"]
    else:
        fill, col = (C["error"], C["onError"]) if kind == "danger" else (C["primary"], C["onPrimary"])
        s = rect(x, y, w, h, fill, h / 2)
        s += rect(x + 0.6, y + 0.6, w - 1.2, h - 1.2, "none", h / 2, "#FFFFFF", 1, op=0.3)
    return s + t(x + w / 2, y + h / 2 + size * 0.36, label, size, 600, col, "middle")


def dlg_btns(x, y, w, left, right, kind="primary", h=48, gap=10):
    """Bottom button row of every dialog: cancel (left, pale) + confirm (right, filled); one label = full width."""
    if left is None or right is None:
        lab = right or left
        return pill_btn(x, y, w, lab, kind if right else "secondary", h)
    bw = (w - gap) / 2
    return pill_btn(x, y, bw, left, "secondary", h) + pill_btn(x + bw + gap, y, bw, right, kind, h)


def info_tag(x, y, label, on_photo=True, size=10.5, pad=5, h=17, col=None, ic=None, anchor="start"):
    """INFORMATION tag: flat translucent fill, no stroke, no shadow, 4dp radius, not clickable."""
    w_ = sum(size * (1.0 if ord(ch) > 0x2E80 else (0.3 if ch == " " else 0.6)) for ch in label) + pad * 2 + (size + 2 if ic else 0)
    if anchor == "end":
        x = x - w_
    if on_photo:
        s = rect(x, y, w_, h, "#000", 4, op=0.60)
        tc = "#FFFFFF"
    else:
        s = rect(x, y, w_, h, C["onSurf"], 4, op=0.08 if mode() == "light" else 0.14)
        tc = col or C["onSurfV"]
    tx = x + pad
    if ic:
        s += icon(ic, tx - 1, y + (h - size - 1) / 2, size + 1, col or tc)
        tx += size + 2
    s += t(tx, y + h / 2 + size * 0.36, label, size, 600, tc)
    return s, w_


def dot(cx, cy, col, r=4.5, halo=True):
    s = circle(cx, cy, r + 3.5, col, op=0.22) if halo else ""
    return s + circle(cx, cy, r, col)


def section_label(x, y, label):
    return t(x, y, label, 12.5, 600, C["primary"], extra='letter-spacing="0.6"')


# ---------------------------------------------------------------- brand icon (docs/brand, original artwork)
_BRAND = {"n": 0, "src": None}


def brand_icon(x, y, size):
    """Embed docs/brand/alpha-gps-icon.svg (1024 viewBox) at x,y; ids are made unique per use."""
    import os, re
    if _BRAND["src"] is None:
        p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "brand", "alpha-gps-icon.svg")
        src = open(p, encoding="utf-8").read()
        src = re.sub(r"<\?xml.*?\?>", "", src, flags=re.S)
        src = re.sub(r"<!--.*?-->", "", src, flags=re.S)
        src = re.sub(r"<title>.*?</title>", "", src, flags=re.S)
        m = re.search(r"<svg[^>]*>(.*)</svg>", src, re.S)
        _BRAND["src"] = m.group(1)
    _BRAND["n"] += 1
    p_ = f"br{_BRAND['n']}_"
    inner = re.sub(r'id="([^"]+)"', lambda m: f'id="{p_}{m.group(1)}"', _BRAND["src"])
    inner = re.sub(r"url\(#([^)]+)\)", lambda m: f"url(#{p_}{m.group(1)})", inner)
    return f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="0 0 1024 1024">{inner}</svg>'
