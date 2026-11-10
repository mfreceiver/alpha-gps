# -*- coding: utf-8 -*-
"""v2 design tooling: blue light/dark theme on top of lib.py (v1 helpers keep working because they
read the shared palette dict `C`, which set_theme() mutates in place).  Pure design tooling."""
import math
import lib
from lib import *  # noqa: F401,F403  (t, rect, circle, line, icon, ICONS, C, RATING, svg_wrap ...)

# --- extra Material icon paths --------------------------------------------------------------------
ICONS.update({
    "edit": "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    "delete": "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    "tune": "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
    "wifi": "M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z",
    "check": "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    "photo_library": "M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4l2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z",
    "chevron_left": "M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z",
    "check_box": "M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
    "check_box_outline": "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z",
    "cancel": "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z",
})

# --- palettes (blue seed; no dynamic color; light + dark only) ---------------------------------------
LIGHT = dict(
    bg="#F9FAFE", surfLowest="#FFFFFF", surfLow="#F3F6FB", card="#EDF1F7", card2="#E7EBF2", hi="#E1E6ED",
    outline="#72777F", outlineV="#C2C7CF", onSurf="#191C20", onSurfV="#42474E",
    primary="#0B57D0", onPrimary="#FFFFFF", primaryC="#D3E3FD", onPrimaryC="#041E49",
    secC="#D8E3F8", onSecC="#111C2B", secondary="#545F71",
    tertiary="#00696D", tertiaryC="#9CF1F4",
    error="#BA1A1A", onError="#FFFFFF", errorC="#FFDAD6", onErrorC="#410002",
    green="#1B6E3A", onGreen="#FFFFFF", greenC="#C8F0D2", onGreenC="#00210C",
    amber="#F9A825", amberText="#7A5200", amberC="#FFE9B3", onAmberC="#261A00",
    red="#BA1A1A", inverse="#2E3135", inverseOn="#EFF0F7", inversePrimary="#A8C7FA",
    scrim="#000000", scrimA=0.38, sysbar="#191C20", frame="#B5BCC6",
)
DARK = dict(
    bg="#101418", surfLowest="#0B0F13", surfLow="#181C20", card="#1D2125", card2="#272B30", hi="#32363B",
    outline="#8C9199", outlineV="#42474E", onSurf="#E0E2E8", onSurfV="#C2C7CF",
    primary="#A8C7FA", onPrimary="#062E6F", primaryC="#0842A0", onPrimaryC="#D3E3FD",
    secC="#3C4758", onSecC="#D8E3F8", secondary="#BCC7DB",
    tertiary="#81D4D8", tertiaryC="#004F52",
    error="#FFB4AB", onError="#690005", errorC="#93000A", onErrorC="#FFDAD6",
    green="#7DDB93", onGreen="#00391A", greenC="#0B5227", onGreenC="#C8F0D2",
    amber="#FFC857", amberText="#FFC857", amberC="#5C4200", onAmberC="#FFE9B3",
    red="#FFB4AB", inverse="#E0E2E8", inverseOn="#2E3135", inversePrimary="#0B57D0",
    scrim="#000000", scrimA=0.6, sysbar="#E0E2E8", frame="#4A5058",
)
THEMES = {"light": LIGHT, "dark": DARK}
_mode = ["light"]


def set_theme(name):
    _mode[0] = name
    C.update(THEMES[name])
    C["callout"] = "#E5322D"


def mode():
    return _mode[0]


set_theme("light")


def arc(cx, cy, r, a0, a1, color, sw=3, cap="round"):
    """Arc from angle a0 to a1 (degrees, 0 = 12 o'clock, clockwise)."""
    def pt(a_):
        rad = math.radians(a_ - 90)
        return cx + r * math.cos(rad), cy + r * math.sin(rad)
    x0, y0 = pt(a0)
    x1, y1 = pt(a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return (f'<path d="M{x0:.2f} {y0:.2f} A{r} {r} 0 {large} 1 {x1:.2f} {y1:.2f}" fill="none" '
            f'stroke="{color}" stroke-width="{sw}" stroke-linecap="{cap}"/>')


def ring(cx, cy, r, sweep=100, start=-20, color=None, sw=4, track=True):
    color = color or C["primary"]
    s = circle(cx, cy, r, "none", C["outlineV"], sw) if track else ""
    return s + arc(cx, cy, r, start, start + sweep, color, sw)


def phone_frame(body, W=390, H=844, scale=1.0, extra_defs=""):
    clip = f'<clipPath id="pc"><rect width="{W}" height="{H}" rx="26"/></clipPath>'
    shell = (f'<rect x="0.5" y="0.5" width="{W - 1}" height="{H - 1}" rx="26" fill="none" '
             f'stroke="{C["frame"]}" stroke-width="1.2"/>')
    return svg_wrap(W, H, f'{extra_defs}{clip}<g clip-path="url(#pc)">{body}</g>{shell}', scale, defs=True)
