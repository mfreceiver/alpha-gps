# -*- coding: utf-8 -*-
"""v2 phone-screen SVG builders (390x844 dp).  Call lib2.set_theme('light'|'dark') before building."""
from lib2 import *  # noqa
import lib2

W, H = 390, 844
PAL = [("#7FB3D5", "#2E5C7A"), ("#F5B7B1", "#8E4A44"), ("#A9DFBF", "#2F6B45"), ("#F9E79F", "#8A6D1B"),
       ("#D2B4DE", "#5B3A6B"), ("#AED6F1", "#3B6E96"), ("#FAD7A0", "#935D1A"), ("#A3E4D7", "#1F6E5E")]


def tile_defs():
    s = "<defs>"
    for i, (a_, b_) in enumerate(PAL):
        s += (f'<linearGradient id="tg{i}" x1="0" y1="0" x2="0" y2="1">'
              f'<stop offset="0" stop-color="{a_}"/><stop offset="1" stop-color="{b_}"/></linearGradient>')
    return s + "</defs>"


def blend(c1, c2, k):
    """k of c2 over c1 (hex #rrggbb)."""
    a_ = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    b_ = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * k):02X}" for x, y in zip(a_, b_))


def sbar(col=None):
    c = col or C["onSurf"]
    return (t(24, 22, "9:41", 13, 600, c) + icon("signal", 330, 8, 16, c) + battery(354, 10, 80, c, 18, 9))


def nav_pill(col=None):
    return rect(145, 830, 100, 4, col or C["onSurf"], 2, op=0.5)


def top_bar(title, sub=None, back="arrow_back", acts=(), y=32, bg=None, tcol=None, icol=None, acol=None):
    """Left-aligned title; acts = list of icon names (rightmost last). Returns svg."""
    tcol = tcol or C["onSurf"]
    icol = icol or C["onSurf"]
    s = rect(0, y, W, 64, bg or C["bg"])
    if back:
        s += icon(back, 20, y + 20, 24, icol)
    x0 = 68
    if sub:
        s += t(x0, y + 31, title, 20, 500, tcol) + t(x0, y + 50, sub, 12.5, 400, C["onSurfV"])
    else:
        s += t(x0, y + 40, title, 20, 500, tcol)
    n = len(acts)
    for i, name in enumerate(acts):
        cx = W - 28 - 48 * (n - 1 - i)
        col = (acol or {}).get(name, C["onSurfV"]) if isinstance(acol, dict) else (acol or C["onSurfV"])
        s += icon(name, cx - 12, y + 20, 24, col)
    return s


def scrim_layer(k=None):
    return rect(0, 0, W, H, C["scrim"], 0, op=k if k is not None else C["scrimA"])


def shadow_rect(x, y, w, h, r, fill, stroke=None):
    return (rect(x, y + 3, w, h, "#000", r, op=0.10) + rect(x - 1, y + 1, w + 2, h, "#000", r, op=0.05) +
            rect(x, y, w, h, fill, r, stroke))


def switch(x, y, on=True, w=44, h=26):
    if on:
        return (rect(x, y, w, h, C["primary"], h / 2) + circle(x + w - h / 2, y + h / 2, h / 2 - 3, C["onPrimary"]))
    return (rect(x, y, w, h, "none", h / 2, C["outline"], 2) + circle(x + h / 2, y + h / 2, 7, C["outline"]))


def text_btn(cx, cy, label, col=None, size=14.5):
    return t(cx, cy + 5, label, size, 500, col or C["primary"], "middle")


def filled_btn(x, y, w, h, label, fill=None, tcol=None, size=15, r=None):
    return (rect(x, y, w, h, fill or C["primary"], r if r is not None else h / 2) +
            t(x + w / 2, y + h / 2 + size * 0.36, label, size, 600, tcol or C["onPrimary"], "middle"))


def tonal_btn(x, y, w, h, label):
    return filled_btn(x, y, w, h, label, C["secC"], C["onSecC"])


# ======================================================================================= device page
def _cell(cx, y, kind, state, caption):
    """One status cell (校时 / 信号 / 电量) centred at cx; y = cell top."""
    s = ""
    cy = y + 14
    dim = state == "off"
    if kind == "sync":
        if state == "ok":
            s += icon("check_circle", cx - 12, cy - 12, 24, C["green"])
        elif state == "warn":
            s += icon("warning", cx - 12, cy - 12, 24, C["amber"])
        elif state == "syncing":
            s += spinner(cx, cy, 9, C["primary"])
        else:
            s += icon("schedule", cx - 12, cy - 12, 24, C["outline"], 0.6)
    elif kind == "signal":
        lvl, col = {"ok": (3, C["green"]), "warn": (1, C["red"]), "syncing": (3, C["green"]), "off": (0, C["outline"])}[state]
        s += bars(cx - 11, cy - 9, lvl, col, dim=dim)
    else:
        pct, col, val = {"ok": (82, C["green"], "82%"), "warn": (18, C["red"], "18%"),
                         "syncing": (82, C["green"], "82%"), "off": (82, C["outline"], "82%")}[state]
        s += battery(cx - 28, cy - 5.5, pct, col, 22, 11, dim=dim)
        s += t(cx + 6, cy + 5, val, 14.5, 600, C["red"] if state == "warn" else (C["onSurfV"] if dim else C["onSurf"]))
    s += t(cx, y + 42, caption, 12, 400, C["onSurfV"], "middle")
    return s


def device_screen(state="ok", overlay=None, callouts=False, info=None):
    """state: ok | off | warn | syncing.   overlay: None | ov_* svg string appended on top."""
    b = rect(0, 0, W, H, C["bg"]) + sbar()
    b += top_bar("Alpha 7 IV", acts=["info"], acol=C["onSurfV"])
    # ---- hero card: connection + 3 status cells
    hy, hh = 112, 154
    b += rect(16, hy, 358, hh, C["card"], 20)
    b += circle(56, hy + 40, 24, C["primaryC"]) + icon("camera", 43, hy + 27, 26, C["onPrimaryC"])
    b += t(92, hy + 36, "Alpha 7 IV", 18, 600)
    dot, stxt, scol = {"ok": (C["green"], "已连接", C["green"]), "warn": (C["green"], "已连接", C["green"]),
                       "syncing": (C["green"], "已连接", C["green"]),
                       "off": (C["outline"], "未连接", C["onSurfV"])}[state]
    b += circle(97, hy + 55, 4.5, dot) + t(108, hy + 60, stxt, 14, 500, scol)
    if state == "off":
        b += filled_btn(274, hy + 22, 84, 38, "连接", size=14.5)
    b += line(32, hy + 86, 358, hy + 86, C["outlineV"], 1)
    cw = 358 / 3
    cs = {"ok": ("ok",) * 3, "warn": ("warn",) * 3, "syncing": ("syncing", "ok", "ok"), "off": ("off",) * 3}[state]
    for i, (kind, cap) in enumerate([("sync", "校时"), ("signal", "信号"), ("batt", "电量")]):
        b += _cell(16 + cw * (i + 0.5), hy + 96, kind, cs[i], cap)
    for i in (1, 2):
        b += line(16 + cw * i, hy + 98, 16 + cw * i, hy + 140, C["outlineV"], 1)
    # ---- Wi-Fi transfer entry (primary action) directly under the hero
    wy = hy + hh + 12
    if state == "off":
        b += rect(16, wy, 358, 76, C["card"], 20)
        b += icon("wifi", 36, wy + 26, 26, C["outline"]) + t(76, wy + 36, "Wi-Fi 照片传输", 17, 600, C["outline"])
        b += t(76, wy + 56, "连接相机后可用", 12.5, 400, C["outline"])
        b += icon("chevron_right", 334, wy + 26, 24, C["outlineV"])
    else:
        b += rect(16, wy, 358, 76, C["primaryC"], 20)
        b += icon("wifi", 36, wy + 25, 26, C["onPrimaryC"]) + t(76, wy + 44, "Wi-Fi 照片传输", 17, 600, C["onPrimaryC"])
        b += icon("chevron_right", 334, wy + 26, 24, C["onPrimaryC"])
    # ---- GPS card
    gy = wy + 76 + 12
    b += rect(16, gy, 358, 112, C["card"], 20)
    gcol = C["outline"] if state == "off" else C["primary"]
    b += icon("gps", 32, gy + 14, 24, gcol) + t(68, gy + 32, "GPS 位置传输", 15, 500)
    chip = {"ok": ("传输中", C["green"], C["greenC"]), "syncing": ("传输中", C["green"], C["greenC"]),
            "warn": ("延迟", C["amberText"], C["amberC"]), "off": ("已停止", C["onSurfV"], C["hi"])}[state]
    cwid = 68
    b += rect(374 - 16 - cwid, gy + 14, cwid, 24, chip[2], 12) + circle(374 - 16 - cwid + 12, gy + 26, 4, chip[1])
    b += t(374 - 16 - cwid + 21, gy + 31, chip[0], 12, 500, chip[1])
    vals = {"ok": ("128", "126", "3 秒前"), "syncing": ("0", "0", "—"), "warn": ("130", "126", "38 秒前"),
            "off": ("126", "126", "—")}[state]
    cols = [16 + 358 / 6, 16 + 358 / 2, 16 + 358 * 5 / 6]
    ncol = C["onSurfV"] if state == "off" else C["onSurf"]
    for i, (v, cap) in enumerate(zip(vals, ["已传输", "已写入", "最近写入"])):
        sz = 24 if i < 2 else 20
        nc = C["amberText"] if (state == "warn" and i == 2) else ncol
        b += t(cols[i], gy + 74, v, sz, 600, nc, "middle") + t(cols[i], gy + 96, cap, 12, 400, C["onSurfV"], "middle")
    for i in (1, 2):
        b += line(16 + 358 * i / 3, gy + 56, 16 + 358 * i / 3, gy + 100, C["outlineV"], 1)
    b += nav_pill()
    if callouts:
        b += callout(1, 26, hy + 4) + callout(2, 366, hy + 96) + callout(3, 366, wy + 4) + callout(4, 366, gy + 4)
        if state == "off":
            b += callout(5, 366, hy + 24)
    if overlay:
        b += overlay
    return b


def legacy_device_screen():
    """Wireframe of today's device page (purple M3 baseline) for the before/after comparison."""
    saved = dict(C)
    C.update(dict(bg="#FEF7FF", primary="#6750A4", onSurf="#1D1B20", onSurfV="#49454F", outline="#79747E",
                  outlineV="#CAC4D0", card="#F3EDF7", onPrimary="#FFFFFF"))
    b = rect(0, 0, W, H, C["bg"]) + sbar()
    b += rect(0, 32, W, 64, C["bg"]) + icon("arrow_back", 20, 52, 24) + t(W / 2, 72, "Alpha 7 IV", 20, 500, None, "middle")
    b += icon("info", 346, 52, 24, C["onSurfV"])
    b += t(16, 128, "相机名称", 12, 400, C["onSurfV"]) + t(16, 152, "Alpha 7 IV", 16, 600)
    b += circle(20, 172, 4, "#2E7D32") + t(32, 177, "连接状态：已连接", 14, 400, C["onSurfV"])
    b += rect(16, 192, 112, 40, "none", 20, C["outline"], 1) + t(72, 217, "断开连接", 14, 500, C["primary"], "middle")
    b += rect(16, 744, 358, 72, C["primary"], 36) + t(190, 786, "Wi-Fi 照片传输", 16, 500, "#FFFFFF", "middle")
    b += t(190, 804, "（说明小字）", 11, 400, "#E8DEF8", "middle")
    b += nav_pill()
    b += callout(1, 142, 212) + callout(2, 372, 780)
    C.clear()
    C.update(saved)
    return b


# ======================================================================================= overlays
CARD_W, CARD_H = 286, 252


def _dots(cx, y, idx, n=4):
    s = ""
    gap = 20
    x0 = cx - gap * (n - 1) / 2
    for i in range(n):
        x = x0 + i * gap
        if i < idx:
            s += circle(x, y, 4, C["primary"])
        elif i == idx:
            s += circle(x, y, 7, C["primaryC"]) + circle(x, y, 4.5, C["primary"])
        else:
            s += circle(x, y, 4, C["outlineV"])
    return s


def _wave(cx, cy, col):
    return (arc(cx, cy + 6, 22, -42, 42, col, 3) + arc(cx, cy + 6, 14, -42, 42, col, 3) + circle(cx, cy + 6, 3, col))


def ov_card(kind, cy=410):
    """Modal progress card over the *original* screen.  Same size for every step (no layout jump)."""
    x0, y0 = (W - CARD_W) / 2, cy - CARD_H / 2
    b = scrim_layer()
    b += shadow_rect(x0, y0, CARD_W, CARD_H, 28, C["card2"])
    cx = W / 2
    rcy = y0 + 62
    steps = {"prepare": 0, "approve": 1, "join": 2, "catalog": 3, "ready": 4}
    title = {"prepare": "正在启动相机 Wi-Fi", "approve": "请允许连接相机 Wi-Fi", "join": "正在连接相机",
             "catalog": "正在读取照片目录", "ready": "已连接", "fail": "未能连接相机 Wi-Fi",
             "ble": "正在连接相机", "ble_fail": "未能连接相机", "closing": "正在断开…"}[kind]
    hint = {"approve": "请在系统弹窗中点「连接」", "fail": "相机无响应，请确认相机已开机",
            "ble_fail": "请确认相机已开机且在附近"}.get(kind, "")
    # --- visual
    if kind in ("prepare", "join", "ble", "closing"):
        b += ring(cx, rcy, 30, 110, -30, C["primary"], 4)
        b += icon("camera" if kind in ("prepare", "ble") else "wifi", cx - 14, rcy - 14, 28, C["primary"])
    elif kind == "approve":
        b += circle(cx, rcy, 30, C["primaryC"]) + _wave(cx, rcy - 4, C["onPrimaryC"])
        b += arc(cx, rcy, 36, 10, 40, C["primary"], 3) + arc(cx, rcy, 36, 190, 220, C["primary"], 3)
    elif kind == "catalog":
        b += ring(cx, rcy, 30, 110, 140, C["primary"], 4)
        b += icon("photo_library", cx - 14, rcy - 14, 28, C["primary"])
    elif kind == "ready":
        b += circle(cx, rcy, 30, C["greenC"]) + icon("check", cx - 18, rcy - 18, 36, C["green"])
    elif kind in ("fail", "ble_fail"):
        b += circle(cx, rcy, 30, C["errorC"]) + icon("error", cx - 18, rcy - 18, 36, C["error"])
    b += t(cx, y0 + 126, title, 17, 600, None, "middle")
    if hint:
        b += t(cx, y0 + 150, hint, 13, 400, C["error"] if "fail" in kind else C["onSurfV"], "middle")
    if kind in steps:
        b += _dots(cx, y0 + 178, steps[kind])
    elif kind in ("fail", "ble_fail"):
        pass
    # --- actions
    ay = y0 + 222
    b += line(x0 + 20, ay - 24, x0 + CARD_W - 20, ay - 24, C["outlineV"], 1) if kind not in ("ready", "closing") else ""
    if kind in ("prepare", "approve", "join", "catalog", "ble"):
        b += text_btn(cx, ay, "取消")
    elif kind == "fail":
        b += text_btn(x0 + 56, ay, "帮助", C["onSurfV"]) + text_btn(x0 + CARD_W - 124, ay, "取消", C["onSurfV"])
        b += text_btn(x0 + CARD_W - 56, ay, "重试")
    elif kind == "ble_fail":
        b += text_btn(x0 + CARD_W - 124, ay, "取消", C["onSurfV"]) + text_btn(x0 + CARD_W - 56, ay, "重试")
    elif kind == "closing":
        b += t(cx, ay + 5, "请稍候", 13, 400, C["outline"], "middle")
    return b


# ======================================================================================= Info dialogs
def info_legacy():
    """Today's Ⓘ dialog (purple baseline) – wireframe for the comparison."""
    saved = dict(C)
    C.update(dict(bg="#FEF7FF", primary="#6750A4", onSurf="#1D1B20", onSurfV="#49454F", outline="#79747E",
                  outlineV="#CAC4D0", card2="#ECE6F0", error="#B3261E"))
    b = rect(0, 0, W, H, "#FEF7FF") + sbar() + rect(0, 0, W, H, "#000", 0, op=0.32)
    x0, y0, w = 24, 70, 342
    b += rect(x0, y0, w, 700, "#ECE6F0", 28)
    y = y0 + 34
    b += t(x0 + 24, y, "相机信息", 22, 500)
    y += 34
    b += t(x0 + 24, y, "Alpha 7 IV", 16, 500) + t(x0 + 24, y + 20, "蓝牙地址  AA:BB:CC:DD:EE:FF", 12.5, 400, C["onSurfV"])
    y += 40
    b += line(x0 + 24, y, x0 + w - 24, y, C["outlineV"])
    y += 26
    b += t(x0 + 24, y, "保持活跃", 15, 500)
    b += switch(x0 + w - 24 - 44, y - 18, False)
    for i, ln in enumerate(["相机离开范围后，仍为该相机保持后台服务", "运行。退出应用仍会断开连接。"]):
        b += t(x0 + 24, y + 20 + i * 16, ln, 12, 400, C["onSurfV"])
    y += 78
    for lab in ("自动校时", "自动区域调整"):
        b += t(x0 + 24, y, lab, 15, 500) + t(x0 + 24, y + 19, "已启用（应用会保持开启）", 12.5, 400, C["onSurfV"])
        y += 52
    b += t(x0 + 24, y, "蓝牙 MTU", 15, 500) + t(x0 + w - 24, y, "517 字节", 14, 400, C["onSurfV"], "end")
    y += 20
    b += line(x0 + 24, y, x0 + w - 24, y, C["outlineV"])
    y += 34
    b += t(x0 + 44, y, "重命名", 14, 500, C["primary"]) + t(x0 + 140, y, "帮助", 14, 500, C["primary"]) + \
        t(x0 + 220, y, "移除", 14, 500, "#B3261E")
    b += t(x0 + w - 36, y + 54, "OK", 14, 500, C["primary"], "end")
    b += callout(1, 352, 232) + callout(2, 300, 298) + callout(3, x0 + 70, y + 22) + callout(4, x0 + 160, y + 22) + callout(5, x0 + 238, y + 22)
    C.clear()
    C.update(saved)
    return b


def info_dialog(wifi=True, callouts=False, bg_state="ok"):
    """v2 minimal Ⓘ: no helper texts; ✓ icons; pen beside the name; danger zone at the bottom."""
    b = device_screen(bg_state) + scrim_layer()
    x0, w = 24, 342
    rows = 4 + (2 if wifi else 0)
    h = 66 + 62 + 46 + 56 + 46 * 3 + (46 * 2 if wifi else 0) + 86 + 8
    y0 = (H - h) / 2 - 10
    b += shadow_rect(x0, y0, w, h, 28, C["card2"])
    y = y0
    b += t(x0 + 24, y + 42, "相机信息", 20, 500) + icon("close", x0 + w - 48, y + 22, 24, C["onSurfV"])
    y += 66
    b += t(x0 + 24, y + 28, "Alpha 7 IV", 19, 600) + icon("edit", x0 + w - 48, y + 8, 24, C["primary"])
    y += 52
    b += t(x0 + 24, y + 18, "蓝牙地址", 14, 400, C["onSurfV"]) + t(x0 + w - 24, y + 18, "AA:BB:CC:DD:EE:FF", 14, 500, None, "end")
    y += 38
    b += line(x0 + 24, y, x0 + w - 24, y, C["outlineV"])
    y += 8
    b += t(x0 + 24, y + 30, "保持活跃", 15, 500) + switch(x0 + w - 24 - 44, y + 14, True)
    y += 54
    for lab in ("自动校时", "自动区域调整"):
        b += t(x0 + 24, y + 27, lab, 15, 500) + icon("check_circle", x0 + w - 48, y + 9, 24, C["green"])
        y += 46
    b += t(x0 + 24, y + 27, "蓝牙 MTU", 15, 500) + t(x0 + w - 24, y + 27, "517", 15, 500, C["onSurfV"], "end")
    y += 46
    if wifi:
        b += t(x0 + 24, y + 27, "固件", 15, 500) + t(x0 + w - 24, y + 27, "2.00", 15, 500, C["onSurfV"], "end")
        y += 46
        b += t(x0 + 24, y + 27, "序列号", 15, 500) + t(x0 + w - 24, y + 27, "••••••3F2A", 15, 500, C["onSurfV"], "end")
        y += 46
    y += 4
    b += line(x0 + 24, y, x0 + w - 24, y, C["outlineV"])
    y += 14
    bw = w - 48
    b += rect(x0 + 24, y, bw, 48, "none", 24, C["error"], 1.5)
    b += icon("delete", x0 + w / 2 - 66, y + 12, 24, C["error"]) + t(x0 + w / 2 - 34, y + 30, "移除相机", 15, 600, C["error"])
    if callouts:
        b += callout(1, x0 + w - 70, y0 + 34) if False else ""
        b += callout(1, x0 + w - 22, y0 + 66 + 8) + callout(2, x0 + w - 22, y0 + 66 + 52 + 38 + 8 + 54 + 18)
        b += callout(3, x0 + 12, y + 24) + callout(4, x0 + w - 10, y0 + 12)
    return b


def rename_dialog(callouts=False):
    b = device_screen("ok") + scrim_layer()
    x0, w, h = 32, 326, 214
    y0 = 250
    b += shadow_rect(x0, y0, w, h, 28, C["card2"])
    b += t(x0 + 24, y0 + 42, "重命名", 20, 500)
    b += rect(x0 + 24, y0 + 66, w - 48, 56, "none", 6, C["primary"], 2)
    b += t(x0 + 34, y0 + 60, "相机名称", 11, 400, C["primary"], extra=f'style="paint-order:stroke" stroke="{C["card2"]}" stroke-width="6"')
    b += t(x0 + 38, y0 + 100, "Alpha 7 IV", 16, 400)
    b += rect(x0 + w - 70, y0 + 80, 2, 24, C["primary"])
    b += text_btn(x0 + w - 130, y0 + 168, "取消", C["primary"]) + text_btn(x0 + w - 56, y0 + 168, "保存", C["primary"])
    return b


def remove_dialog():
    b = device_screen("ok") + scrim_layer()
    x0, w, h = 32, 326, 232
    y0 = 240
    b += shadow_rect(x0, y0, w, h, 28, C["card2"])
    b += icon("delete", x0 + w / 2 - 12, y0 + 22, 24, C["error"])
    b += t(x0 + w / 2, y0 + 72, "移除此相机？", 20, 500, None, "middle")
    b += t(x0 + 24, y0 + 104, "“Alpha 7 IV”将与此手机解除配对，", 13.5, 400, C["onSurfV"])
    b += t(x0 + 24, y0 + 124, "之后可重新配对。", 13.5, 400, C["onSurfV"])
    b += text_btn(x0 + w - 130, y0 + 190, "取消", C["primary"]) + text_btn(x0 + w - 56, y0 + 190, "移除", C["error"])
    return b


# ======================================================================================= Wi-Fi pages
def date_list_screen(overlay_top=None, callouts=False):
    b = rect(0, 0, W, H, C["bg"]) + sbar()
    b += top_bar("相机照片", acts=["refresh"])
    # info strip
    y = 108
    b += rect(16, y, 358, 76, C["card"], 16)
    b += icon("sd_card", 32, y + 14, 22, C["onSurfV"]) + t(62, y + 31, "64 GB", 15, 600) + t(112, y + 31, "· 剩余 21.3 GB", 13.5, 400, C["onSurfV"])
    b += t(358, y + 31, "固件 2.00", 13, 400, C["onSurfV"], "end")
    b += rect(32, y + 48, 326, 6, C["outlineV"], 3) + rect(32, y + 48, 326 * 0.67, 6, C["primary"], 3)
    y += 76 + 8
    for d, n_ in [("11月9日", 227), ("11月8日", 64), ("11月3日", 118), ("10月28日", 9), ("10月21日", 342), ("未知日期", 3)]:
        b += t(24, y + 36, d, 17, 500) + t(366, y + 36, f"{n_} 张", 14, 400, C["onSurfV"], "end")
        y += 56
        b += line(16, y, 374, y, C["outlineV"], 1)
    b += nav_pill()
    if callouts:
        b += callout(1, 372, 114) + callout(2, 338, 44)
    if overlay_top:
        b += overlay_top
    return b


def _bgp(i):
    return PAL[i % len(PAL)]


def photo_art(x, y, w, h, i, r=0, cid=None):
    """Gradient 'photo' with a mountain silhouette, clipped to a rounded rect."""
    cid = cid or f"pa{int(x)}_{int(y)}_{i}_{int(w)}"
    sx = w * (0.42 + ((i * 13) % 30) / 100.0)
    g = f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/></clipPath><g clip-path="url(#{cid})">'
    g += rect(x, y, w, h, f"url(#tg{i % len(PAL)})")
    g += (f'<path d="M{x} {y + h * .8} L{x + sx - w * .2} {y + h * .5} L{x + sx} {y + h * .67} L{x + sx + w * .23} {y + h * .4} '
          f'L{x + w} {y + h * .75} L{x + w} {y + h} L{x} {y + h}Z" fill="#000" opacity="0.22"/>')
    g += circle(x + w * (0.72 - ((i * 7) % 30) / 100.0), y + h * .27, w * 0.085, "#fff").replace('fill="#fff"', 'fill="#fff" opacity="0.55"')
    return g, "</g>"


TILES = [("J&R", 0, 0, 54.6), ("JPG", 1, 3, 6.6), ("J&R", 0, 5, 51.2), ("RAW", 0, 0, 47.9),
         ("JPG", 0, 4, 7.1), ("HEIF", 1, 5, 4.8), ("J&R", 0, 2, 53.0), ("J&R", 1, 1, 49.7),
         ("JPG", 0, 0, 6.2), ("JPG", 1, 0, 5.9), ("RAW", 0, 3, 48.4), ("J&R", 0, 4, 55.3),
         ("JPG", 0, 0, 6.9), ("J&R", 1, 2, 52.2), ("JPG", 0, 5, 6.4), ("RAW", 0, 1, 47.1),
         ("JPG", 1, 4, 7.4), ("J&R", 0, 0, 50.6), ("JPG", 0, 2, 6.0), ("HEIF", 0, 3, 5.2),
         ("JPG", 0, 0, 6.5), ("J&R", 1, 5, 54.0), ("JPG", 0, 1, 6.8), ("JPG", 0, 0, 6.1)]


def tile(x, y, s, i, spec, opts, sel=False, selecting=False):
    """opts: dict(fmt, size, ext, mode).  s = tile edge in dp."""
    fmt, prot, rating, mb = spec
    mode_ = opts.get("mode", "S")
    p = 4 if mode_ == "S" else 6
    g, end = photo_art(x, y, s, s, i, 4)
    if sel:
        g += rect(x, y, s, s, C["primary"], 0, op=0.28)
    g += end
    if sel:
        g += rect(x + 0.75, y + 0.75, s - 1.5, s - 1.5, "none", 4, C["primary"], 3)
    # magnifier TL
    g += rect(x + p, y + p, 24, 24, "#000", 3, op=0.55) + icon("search", x + p + 4, y + p + 4, 16, "#fff")
    # protect TR
    if opts.get("ext") and prot:
        g += rect(x + s - p - 24 - (0), y + p, 24, 24, "#000", 3, op=0.55) + icon("lock", x + s - p - 20, y + p + 4, 16, "#fff")
    # selection BR
    if sel:
        g += rect(x + s - p - 22, y + s - p - 22, 22, 22, C["primary"], 4) + \
            f'<path d="M{x + s - p - 17} {y + s - p - 11} l5 5 l9 -10" fill="none" stroke="{C["onPrimary"]}" stroke-width="2.5"/>'
    else:
        g += rect(x + s - p - 21, y + s - p - 21, 20, 20, "#000", 4, "#fff", 2, 0.4)
    # BL: rating pill above, then format(+size) pill
    by = y + s - p - 18
    if opts.get("ext") and rating:
        g += rect(x + p, by - 22, 36, 18, "#000", 4, op=0.7) + icon("star", x + p + 3, by - 20, 14, RATING[rating])
        g += t(x + p + 24, by - 8, str(rating), 11.5, 700, "#fff", "middle")
    parts = []
    if opts.get("fmt"):
        parts.append(fmt)
    if opts.get("size") and mode_ != "S":
        parts.append(f"{mb:.1f} MB")
    if parts:
        txt = " · ".join(parts)
        wpx = 8 + len(txt) * (6.2 if "MB" in txt else 6.6) + (4 if len(parts) == 1 else 0)
        wpx = max(wpx, 30)
        g += rect(x + p, by, wpx, 18, "#000", 4, op=0.7) + t(x + p + wpx / 2, by + 13, txt, 10.5, 500, "#fff", "middle")
    return g


def grid_geom(mode_):
    ncol = {"S": 4, "M": 3, "L": 2}[mode_]
    pad, gap = 6, 4
    s = (W - 2 * pad - gap * (ncol - 1)) / ncol
    return ncol, pad, gap, s


def view_panel(mode_="M", fmt=True, size=True, ext=False, y=94):
    x0, w = 90, 288
    h = 268
    b = shadow_rect(x0, y, w, h, 16, C["card2"])
    b += t(x0 + 20, y + 30, "缩略图大小", 13, 500, C["onSurfV"])
    segs = ["小", "中", "大"]
    sw_ = (w - 40) / 3
    sy = y + 42
    for i, sname in enumerate(segs):
        on = "SML"[i] == mode_
        xs = x0 + 20 + i * sw_
        b += rect(xs, sy, sw_, 40, C["secC"] if on else "none", 0, C["outline"], 1)
        if on:
            b += icon("check", xs + 12, sy + 11, 18, C["onSecC"])
        b += t(xs + sw_ / 2 + (6 if on else 0), sy + 26, sname, 14.5, 500, C["onSecC"] if on else C["onSurf"], "middle")
    b += line(x0 + 16, y + 100, x0 + w - 16, y + 100, C["outlineV"])
    ry = y + 104
    for lab, on, dis in [("文件格式", fmt, False), ("文件大小", size, mode_ == "S"), ("保护与评分", ext, False)]:
        op = 'opacity="0.38"' if dis else ""
        b += f'<g {op}>' + t(x0 + 20, ry + 31, lab, 15, 500) + switch(x0 + w - 20 - 44, ry + 11, on) + "</g>"
        ry += 54
    return b


def grid_screen(mode_="S", fmt=True, size=False, ext=False, selecting=0, panel=False, callouts=False, title="11月9日", sel_set=(2, 5, 6)):
    b = tile_defs() + rect(0, 0, W, H, C["bg"]) + sbar()
    if selecting:
        b += top_bar(f"已选 {selecting} 张", back="close", acts=["select_all"], icol=C["onSurf"], acol=C["onSurfV"])
    else:
        b += top_bar(title, "227 张", acts=["refresh", "tune"])
        if panel:
            b += rect(W - 28 - 24 - 6, 32 + 14, 36, 36, C["secC"], 18) + icon("tune", W - 28 - 12, 32 + 20, 24, C["onSecC"])
    ncol, pad, gap, s = grid_geom(mode_)
    top = 100
    opts = dict(fmt=fmt, size=size, ext=ext, mode=mode_)
    b += f'<clipPath id="gc"><rect x="0" y="{top}" width="{W}" height="{H - top}"/></clipPath><g clip-path="url(#gc)">'
    n_ = 0
    r = 0
    while top + r * (s + gap) < H:
        for c in range(ncol):
            spec = TILES[n_ % len(TILES)]
            b += tile(pad + c * (s + gap), top + r * (s + gap), s, n_, spec, opts, sel=(selecting and n_ in sel_set))
            n_ += 1
        r += 1
    b += "</g>"
    # fast-scroll handle (replaces the page bar)
    b += rect(W - 7, 220, 4, 64, C["outline"], 2, op=0.65)
    if selecting:
        by = H - 98
        b += rect(0, by, W, 98, C["card"], 0) + filled_btn(16, by + 14, 358, 50, f"下载 {selecting} 张", size=16)
    if panel:
        b += rect(0, 0, W, H, "#000", 0, op=0.08) + view_panel(mode_, fmt, size, ext)
    b += nav_pill()
    if callouts:
        b += callout(1, 366, 52)
        if not selecting:
            b += callout(2, 300, 52) + callout(3, 24, 790)
    return b


# ======================================================================================= viewer
class _dark:
    def __enter__(self):
        self.saved = dict(C)
        C.update(DARK)
        return self

    def __exit__(self, *a):
        C.clear()
        C.update(self.saved)


def viewer_screen(state="normal", idx=5, callouts=False, sel=False, size_txt="6.6 MB", name="DSC03456.JPG", download_label="下载", pct=43):
    """state: normal | downloading | saved | failed | nojpg | big | swipe"""
    with _dark():
        B = "#000000"
        b = tile_defs() + rect(0, 0, W, H, B) + sbar("#FFFFFF")
        b += rect(0, 32, W, 64, B) + icon("arrow_back", 20, 52, 24, "#FFFFFF")
        b += t(68, 62, name, 17, 600, "#FFFFFF") + t(68 + 10.3 * len(name) + 10, 62, size_txt, 13, 400, "#B8BEC8")
        b += t(68, 82, "2026-11-09 14:32:08", 12.5, 400, "#B8BEC8")
        b += t(296, 72, "6/227", 14, 500, "#FFFFFF", "end")
        if sel:
            b += rect(312, 52, 24, 24, C["primary"], 5) + icon("check", 313, 53, 22, C["onPrimary"])
        else:
            b += rect(313, 53, 22, 22, "none", 5, "#FFFFFF", 2)
        py, ph = 238, 260
        g, end = photo_art(0, py, W, ph, idx, 0, cid="vp")
        b += g + end
        if state == "swipe":
            # neighbours peeking while dragging
            for dx, k in ((-W + 70, idx - 1), (W - 70, idx + 1)):
                g, end = photo_art(dx, py, W, ph, k, 0, cid=f"vn{k}")
                b += g + end
        # bottom action
        by = H - 106
        if state in ("saved", "nojpg", "big"):
            lab = {"saved": "已保存", "nojpg": "无 JPEG", "big": "文件过大"}[state]
            b += rect(16, by, 358, 52, "#FFFFFF", 26, op=0.12) + t(195, by + 32, lab, 16, 600, "#FFFFFF", "middle", 'opacity="0.38"')
        elif state == "downloading":
            b += rect(16, by, 358, 52, "#FFFFFF", 26, op=0.12) + t(195, by + 32, "下载", 16, 600, "#FFFFFF", "middle", 'opacity="0.38"')
        else:
            b += filled_btn(16, by, 358, 52, "下载", size=16)
        if state in ("downloading", "failed"):
            b += rect(0, py, W, ph, "#000", 0, op=0.40)
            cw, chh = 176, 176
            cx0, cy0 = (W - cw) / 2, py + (ph - chh) / 2
            b += shadow_rect(cx0, cy0, cw, chh, 24, "#2A2E33")
            if state == "downloading":
                b += circle(195, cy0 + 62, 32, "none", "#4A5058", 5) + arc(195, cy0 + 62, 32, 0, 360 * pct / 100, C["primary"], 5)
                b += t(195, cy0 + 68, f"{pct}%", 16, 600, "#FFFFFF", "middle")
                b += line(cx0 + 16, cy0 + 118, cx0 + cw - 16, cy0 + 118, "#4A5058", 1)
                b += text_btn(195, cy0 + 144, "取消")
            else:
                b += circle(195, cy0 + 52, 28, C["errorC"]) + icon("error", 195 - 17, cy0 + 52 - 17, 34, C["error"])
                b += t(195, cy0 + 106, "保存失败", 15, 600, "#FFFFFF", "middle")
                b += line(cx0 + 16, cy0 + 122, cx0 + cw - 16, cy0 + 122, "#4A5058", 1)
                b += text_btn(cx0 + 46, cy0 + 148, "关闭", "#C2C7CF") + text_btn(cx0 + cw - 46, cy0 + 148, "重试")
        b += nav_pill("#FFFFFF")
        if callouts:
            b += callout(1, 62, 54) + callout(2, 62 + 120, 54) if False else ""
            b += callout(1, 60, 82) + callout(2, 232, 52) + callout(3, 300, 52) + callout(4, 372, by + 4)
            if state == "downloading":
                b += callout(5, 195 + 88, py + 40)
        return b
