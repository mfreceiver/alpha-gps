"""AlphaGPS UI design v3 — screens part A: device list, pairing, device page, dialogs, connection overlay."""
from lib3 import *  # noqa: F401,F403
import lib3 as L

CAM = "Alpha 7 IV"


def base(bar=True):
    return backdrop() + (sbar() if bar else "")


def end():
    return nav_pill()


def row_card(y, h=76, x=16, w=358, r=24, level=1):
    return glass(x, y, w, h, r, level)


def icon_tile(cx, cy, name, r=22, bg=None, fg=None, size=24):
    bg = bg or C["primaryC"]
    fg = fg or C["onPrimaryC"]
    return circle(cx, cy, r, bg) + icon(name, cx - size / 2, cy - size / 2, size, fg)


# ================================================================ device list
def device_list_screen(state="normal", callouts=False):
    s = base()
    s += top_bar("相机", back=None, acts=("help", "list", "settings"), size=28)
    if state == "empty":
        s += circle(195, 330, 76, "#fff", op=0.5 if mode() == "light" else 0.06)
        s += glass(195 - 60, 330 - 60, 120, 120, 60, 1)
        s += icon("camera", 195 - 28, 330 - 28, 56, C["primary"])
        s += t(195, 446, "还没有相机", 22, 600, anchor="middle")
        s += t(195, 474, "添加一台 Sony 相机，拍摄时自动写入位置", 14, 400, C["onSurfV"], "middle")
        s += primary_btn(95, 512, 200, 56, "添加相机", "add")
        s += t(195, 600, "相机需先在菜单中开启「位置信息链接」", 12.5, 400, C["onSurfV"], "middle")
        return s + end()
    y = 104
    if state == "perm":
        s += glass(16, y, 358, 92, 24, 1, tint=C["errorC"], a=0.78 if mode() == "light" else 0.45)
        s += icon("warning", 32, y + 18, 24, C["error"])
        s += t(66, y + 32, "缺少权限", 15.5, 600, C["onErrorC"])
        s += t(66, y + 53, "需要「附近设备」与「位置」权限，", 12.5, 400, C["onErrorC"])
        s += t(66, y + 71, "才能连接相机并写入位置", 12.5, 400, C["onErrorC"])
        s += tonal_btn(284, y + 26, 76, 40, "去授权", col=C["error"], tcol=C["onError"])
        y += 108
    s += section_label(28, y + 18, "已配对")
    y += 30
    rows = [("Alpha 7 IV", "已连接 · 位置同步中", "ok"), ("ZV-E10 II", "未连接", "off"),
            ("Alpha 6700", "相机已关闭「位置信息链接」", "err")]
    if state == "perm":
        rows = rows[:2]
    for name, st, k in rows:
        s += row_card(y, 80)
        s += icon_tile(52, y + 40, "camera", 22,
                       C["primaryC"] if k == "ok" else (C["errorC"] if k == "err" else blend(C["bg"], C["onSurf"], 0.08)),
                       C["onPrimaryC"] if k == "ok" else (C["error"] if k == "err" else C["onSurfV"]))
        s += t(88, y + 36, name, 16.5, 600)
        col = {"ok": C["green"], "off": C["onSurfV"], "err": C["error"]}[k]
        if k == "ok":
            s += dot(93, y + 54, C["green"], 4)
            s += t(104, y + 59, st, 13, 500, col)
        else:
            s += t(88, y + 59, st, 13, 400 if k == "off" else 500, col)
        s += icon("chevron_right", 338, y + 28, 24, C["onSurfV"])
        y += 90
    s += section_label(28, y + 22, "附近的相机")
    y += 34
    s += row_card(y, 72)
    s += icon_tile(52, y + 36, "bluetooth", 22, blend(C["bg"], C["onSurf"], 0.08), C["onSurfV"])
    s += t(88, y + 33, "ILCE-7CM2", 16, 600)
    s += t(88, y + 54, "可配对", 13, 400, C["onSurfV"])
    s += tonal_btn(286, y + 16, 72, 40, "配对")
    # extended FAB (operation: raised primary)
    s += primary_btn(222, 744, 152, 56, "添加相机", "add")
    if callouts:
        s += callout(1, 250, 42) + callout(2, 22, 150) + callout(3, 310, y + 8) + callout(4, 214, 750)
    return s + end()


# ================================================================ welcome + pairing
def welcome_screen():
    s = base()
    s += brand_icon(195 - 52, 120, 104)
    s += t(195, 268, "AlphaGPS", 28, 700, anchor="middle")
    s += t(195, 296, "为 Sony 相机的每张照片写入位置", 14.5, 400, C["onSurfV"], "middle")
    items = [("gps", "自动写入位置", "连接后持续把手机定位发送到相机"),
             ("wifi", "照片传输", "浏览并下载相机存储卡里的照片"),
             ("sync", "后台常驻", "离开应用后仍保持连接与位置写入")]
    y = 336
    for ic_, a_, b_ in items:
        s += row_card(y, 76)
        s += icon_tile(52, y + 38, ic_, 22)
        s += t(88, y + 34, a_, 15.5, 600) + t(88, y + 55, b_, 12.5, 400, C["onSurfV"])
        y += 86
    s += primary_btn(24, 716, 342, 56, "开始使用")
    s += t(195, 798, "继续即表示同意隐私说明", 12, 400, C["onSurfV"], "middle")
    return s + end()


def pairing_prep_screen(state="idle"):
    s = base()
    s += top_bar("", acts=())
    s += t(24, 132, "准备配对", 28, 700)
    s += t(24, 158, "在相机上完成以下三步", 14.5, 400, C["onSurfV"])
    steps = [("打开蓝牙", "菜单 → 网络 → 蓝牙 → 蓝牙功能：开"),
             ("进入配对", "菜单 → 网络 → 蓝牙 → 配对"),
             ("保持相机屏幕停留在配对界面", "找到相机后在手机与相机上确认")]
    y = 184
    for i, (a_, b_) in enumerate(steps, 1):
        s += row_card(y, 80)
        s += circle(52, y + 40, 18, C["primary"]) + t(52, y + 46, str(i), 16, 700, C["onPrimary"], "middle")
        s += t(84, y + 36, a_, 15.5, 600) + t(84, y + 57, b_, 12.5, 400, C["onSurfV"])
        y += 90
    s += glass(16, y + 4, 358, 62, 20, 1, shadow=False, tint=C["secC"], a=0.7 if mode() == "light" else 0.5)
    s += icon("info", 30, y + 23, 22, C["primary"])
    s += t(62, y + 31, "相机还需开启「位置信息链接」，", 12.5, 400, C["onSecC"])
    s += t(62, y + 49, "否则可以连接但不会写入位置", 12.5, 400, C["onSecC"])
    if state == "idle":
        s += primary_btn(24, 696, 342, 56, "搜索相机", "search")
        s += text_btn(195, 786, "找不到相机？查看配对步骤详解")
    else:
        s += scrim(0.18)
        s += glass(0, 470, 390, 400, 32, 3)
        s += rect(195 - 18, 482, 36, 4, C["outlineV"], 2)
        s += t(24, 526, "正在搜索…", 20, 600) + spinner(352, 520, 10)
        s += t(24, 550, "轻触相机开始配对", 13, 400, C["onSurfV"])
        y = 570
        for name, sig in (("ILCE-7M4", 4), ("ILCE-7CM2", 2)):
            s += rect(16, y, 358, 68, blend(C["bg"], C["onSurf"], 0.05), 20, op=0.9)
            s += icon_tile(50, y + 34, "camera", 20)
            s += t(84, y + 40, name, 16, 600)
            s += bars(322, y + 24, sig, C["primary"])
            y += 78
        s += text_btn(366, 772, "取消", anchor="end")
    return s + end()


def dialog_box(y, h, w=318):
    x = (W - w) / 2
    return glass(x, y, w, h, 28, 3), x


def pairing_failed_dialog():
    s = pairing_prep_screen("idle").replace(nav_pill(), "") + scrim()
    g, x = dialog_box(274, 292)
    s += g
    s += icon("error", 195 - 24, 296, 48, C["error"])
    s += t(195, 376, "配对未完成", 20, 600, anchor="middle")
    s += t(195, 404, "相机没有回应配对请求。", 13.5, 400, C["onSurfV"], "middle")
    s += t(195, 424, "请确认相机停留在配对界面，", 13.5, 400, C["onSurfV"], "middle")
    s += t(195, 444, "并在相机上选择「确定」。", 13.5, 400, C["onSurfV"], "middle")
    s += dlg_btns(x + 20, 498, 278, "取消", "重试")
    return s + end()


# ================================================================ device page
def _cell(x, y, kind, state, stale=None):
    """Flat inner tile (information, not glass): icon / value / fixed item name. 104x88.
    Camera card kinds: time | sig | bat. GPS card kinds: sent | failed | last."""
    w_, h_ = 104, 88
    off = state == "off"
    s = rect(x, y, w_, h_, C["onSurf"], 18, op=0.05 if mode() == "light" else 0.07)
    cx = x + w_ / 2
    vcol = C["onSurf"] if not off else C["onSurfV"]
    if kind == "time":
        st = {"ok": ("已校正", C["green"], "check_circle"), "off": ("—", C["onSurfV"], "schedule"),
              "warn": ("未校正", C["amberText"], "schedule"), "connecting": ("—", C["onSurfV"], "schedule")}[state]
        s += icon(st[2], cx - 11, y + 11, 22, st[1], op=0.5 if off else None)
        val, name = st[0], "校时"
    elif kind == "sig":
        lvl = 0 if state in ("off", "connecting") else 4
        s += bars(cx - 11, y + 13, lvl, C["primary"], dim=off)
        val, name = ("强" if lvl else "—"), "信号"
        vcol = C["onSurf"] if lvl else C["onSurfV"]
    elif kind == "bat":
        pct = None if state == "connecting" else 78
        s += battery(cx - 13, y + 16, pct, dim=off)
        val, name = ("78%" if pct else "—"), "电量"
    elif kind == "sent":
        s += icon("upload", cx - 11, y + 11, 22, C["primary"], op=0.5 if off else None)
        val, name = "1,284 次", "已写入"
    elif kind == "failed":
        bad = state == "bad"
        s += icon("error", cx - 11, y + 11, 22, C["error"] if bad else C["onSurfV"], op=None if bad else 0.55)
        val, name = ("3" if bad else "0"), "失败"
        vcol = C["error"] if bad else vcol
    else:
        s += icon("schedule", cx - 11, y + 11, 22, C["green"] if state == "ok" else C["onSurfV"], op=0.5 if off else None)
        val, name = {"ok": "3 秒前", "off": "14:02", "bad": "2 分钟前", "connecting": "—"}[state], "最近写入"
        vcol = C["amberText"] if state == "bad" else vcol
    s += t(cx, y + 52, val, 15, 600, vcol, "middle")
    s += t(cx, y + 69, name, 11.5, 400, C["onSurfV"], "middle")
    if stale:
        s += t(cx, y + 82, stale, 9.5, 400, C["onSurfV"], "middle", 'opacity="0.7"')
    return s


def device_screen(state="ok", callouts=False):
    """state: ok | off (never/auto) | manual (user disconnected) | connecting | warn (time correction rejected).
    Matches the 0.2.0 implementation: connection card (status line + fixed trailing slot) and ONE
    location card with three cells (signal / written / last write)."""
    s = base()
    s += top_bar(CAM, acts=("info",))
    bt_on = state in ("ok", "warn")
    # --- connection card: status line + supporting line, trailing slot, battery info tag
    s += glass(16, 104, 358, 122, 28, 1)
    if bt_on:
        s += dot(42, 141, C["green"], 5)
        s += t(56, 147, "蓝牙已连接", 18, 600)
        sub = "位置同步中 · 相机拒绝了时间校正" if state == "warn" else "位置同步中 · 时间已校正"
        s += t(36, 171, sub, 12.5, 400, C["amberText"] if state == "warn" else C["onSurfV"])
        # trailing slot: subdued 'disconnect' icon (confirmed in a danger dialog)
        s += icon("bluetooth_disabled", 326, 130, 22, C["onSurfV"], op=0.7)
    elif state == "connecting":
        s += spinner(43, 141, 7)
        s += t(58, 147, "正在连接蓝牙…", 18, 600)
        s += t(36, 171, "相机在附近时自动连接", 12.5, 400, C["onSurfV"])
        s += text_btn(354, 140, "取消", anchor="end")
    else:
        s += circle(42, 141, 5, "none", C["outline"], 1.8)
        s += t(56, 147, "蓝牙未连接", 18, 600)
        sub = "已手动断开，点“连接”重新连接。" if state == "manual" else "相机在附近时自动连接"
        s += t(36, 171, sub, 12.5, 400, C["onSurfV"])
        s += tonal_btn(266, 122, 92, 40, "连接", "bluetooth")
    bat = "电量 78%" if state in ("ok", "connecting") else ("电量 78% · 42 分钟前获取" if state == "warn" else "电量未知")
    g, _ = info_tag(36, 190, bat, on_photo=False, size=11.5, pad=7, h=21)
    s += g
    # --- location card: one row of three cells
    gy = 238
    s += glass(16, gy, 358, 152, 28, 1)
    s += section_label(36, gy + 30, "位置传输")
    cs = "ok" if bt_on else ("connecting" if state == "connecting" else "off")
    s += _cell(28, gy + 48, "sig", cs)
    s += _cell(143, gy + 48, "sent", "ok" if bt_on else "off")
    s += _cell(258, gy + 48, "last", cs)
    # --- primary action (bottom, thumb zone)
    cap = "浏览并下载相机里的照片" if bt_on else "将先连接蓝牙，再开始传输"
    s += t(195, 690, cap, 12.5, 400, C["onSurfV"], "middle")
    s += primary_btn(16, 706, 358, 68, "照片传输", "wifi", size=17)
    if callouts:
        s += callout(1, 372, 110) + callout(2, 322, 124) + callout(3, 372, 244) + callout(4, 28, 716) + callout(5, 44, 48)
        s += callout(6, 24, 198)
    return s + end()


def bt_disconnect_dialog():
    s = device_screen("ok").replace(nav_pill(), "") + scrim()
    g, x = dialog_box(296, 228)
    s += g
    s += icon("bluetooth_disabled", 195 - 16, 320, 32, C["onSurfV"])
    s += t(195, 388, "断开蓝牙？", 20, 600, anchor="middle")
    s += t(195, 418, "将停止向相机写入位置，之后需手动重新连接。", 13.5, 400, C["onSurfV"], "middle")
    s += dlg_btns(x + 20, 456, 278, "取消", "断开", "danger")
    return s + end()


def info_dialog(callouts=False):
    """Ⓘ as implemented in 0.2.0: no model / MTU / firmware / serial / help."""
    s = device_screen("ok").replace(nav_pill(), "") + scrim()
    x, y, w_, h_ = 24, 172, 342, 492
    s += glass(x, y, w_, h_, 28, 3)
    s += t(x + 24, y + 44, CAM, 22, 600)
    s += icon("edit", x + 24 + 112, y + 26, 20, C["primary"])
    s += icon("close", x + w_ - 44, y + 22, 24, C["onSurfV"])
    rows = [("蓝牙", "蓝牙已连接", "check"), ("地址", "AC:80:0A:3F:5C:21", None), ("位置写入", "进行中", None),
            ("自动校时", "已启用（应用会保持开启）", None), ("自动区域调整", "已启用（应用会保持开启）", None)]
    yy = y + 74
    for k, v, ic_ in rows:
        s += t(x + 24, yy + 22, k, 13.5, 400, C["onSurfV"])
        if ic_ == "check":
            s += icon("check_circle", x + 124, yy + 7, 18, C["green"])
            s += t(x + 148, yy + 22, v, 14.5, 500, C["green"])
        else:
            s += t(x + 124, yy + 22, v, 14.5, 500)
        yy += 42
    yy += 4
    s += rect(x + 12, yy + 8, w_ - 24, 76, C["onSurf"], 18, op=0.05 if mode() == "light" else 0.07)
    s += t(x + 28, yy + 40, "保持活跃", 15, 500)
    s += t(x + 28, yy + 60, "相机离开范围后，仍为该相机保持后台", 11.5, 400, C["onSurfV"])
    s += t(x + 28, yy + 75, "服务运行。", 11.5, 400, C["onSurfV"])
    s += switch(x + w_ - 76, yy + 32, False)
    # danger zone — outlined red button (operation, destructive)
    by = y + h_ - 72
    s += rect(x + 20, by, w_ - 40, 52, "none", 26, C["error"], 1.5)
    s += icon("delete", x + w_ / 2 - 52, by + 15, 22, C["error"])
    s += t(x + w_ / 2 - 22, by + 32, "移除相机", 15, 600, C["error"])
    if callouts:
        s += callout(1, x + 166, y + 24) + callout(2, x + w_ - 14, y + 20) + callout(3, x + 14, y + 80)
        s += callout(4, x + w_ - 16, y + 296) + callout(5, x + 14, by + 4)
    return s + end()


def remove_dialog():
    s = device_screen("ok").replace(nav_pill(), "") + scrim()
    g, x = dialog_box(292, 252)
    s += g
    s += icon("delete", 195 - 16, 316, 32, C["error"])
    s += t(195, 384, "移除此相机？", 20, 600, anchor="middle")
    s += t(195, 414, "“Alpha 7 IV”将与此手机解除配对，", 13.5, 400, C["onSurfV"], "middle")
    s += t(195, 435, "之后可重新配对。", 13.5, 400, C["onSurfV"], "middle")
    s += dlg_btns(x + 20, 476, 278, "取消", "移除", "danger")
    return s + end()


def rename_dialog():
    s = device_screen("ok").replace(nav_pill(), "") + scrim()
    g, x = dialog_box(300, 236)
    s += g
    s += t(x + 24, 344, "重命名相机", 20, 600)
    s += rect(x + 24, 368, 270, 56, "none", 14, C["primary"], 2)
    s += rect(x + 36, 361, 34, 14, GLASS[mode()][3]["tint"], 0) + t(x + 40, 372, "名称", 11.5, 500, C["primary"])
    s += t(x + 40, 402, CAM, 16, 400) + rect(x + 40 + 84, 384, 1.6, 24, C["primary"])
    s += dlg_btns(x + 20, 468, 278, "取消", "保存")
    return s + end()


# ================================================================ connection overlay (fixed-slot card)
OV = dict(x=40, y=262, w=310, h=320)
STAGES = {
    "ble": ("bluetooth", "正在连接蓝牙", "Alpha 7 IV", ""),
    "prepare": ("wifi", "正在启动相机 Wi‑Fi", "相机正在开启热点，约需数秒", ""),
    "approve": ("wifi", "请允许连接相机网络", "在系统弹窗中点「连接」", ""),
    "session": ("camera", "正在连接相机", "正在建立传输会话", ""),
    "catalog": ("photo_library", "正在读取照片目录", "已读取 600 / 763 张", ""),
    "ready": ("check", "已连接", "763 张照片", ""),
    "fail": ("error", "未能连接相机 Wi‑Fi", "相机没有开启热点，", "请确认相机已开机且未在录像"),
    "ble_fail": ("error", "未能连接蓝牙", "没有找到相机，", "请确认相机已开机并在附近"),
    "perm_fail": ("error", "缺少「附近设备」权限", "需要授权后才能加入相机网络", ""),
    "closing": ("wifi_off", "正在断开…", "正在结束照片传输", ""),
}


def ov_card(kind, n=5, done=0, guides=False):
    x, y, w_, h_ = OV["x"], OV["y"], OV["w"], OV["h"]
    cx = x + w_ / 2
    ic_, title, h1, h2 = STAGES[kind]
    s = glass(x, y, w_, h_, 32, 3)
    ry = y + 96
    fail = kind in ("fail", "ble_fail", "perm_fail")
    if fail:
        # no container, no ring: just a large icon
        s += icon("error", cx - 38, ry - 38, 76, C["error"])
    elif kind == "closing":
        for i in range(8):
            a0 = i * 45 + 8
            s += arc(cx, ry, 46, a0, a0 + 29, C["onSurfV"], 5, op=0.12 + 0.1 * i)
        s += icon("wifi_off", cx - 20, ry - 20, 40, C["onSurfV"])
    elif kind == "ready":
        s += seg_ring(cx, ry, 46, n, n, active=False, col=C["green"])
        s += icon("check", cx - 22, ry - 22, 44, C["green"])
    else:
        s += seg_ring(cx, ry, 46, n, done)
        s += icon(ic_, cx - 20, ry - 20, 40, C["primary"])
    s += t(cx, y + 190, title, 18.5, 600, anchor="middle")
    s += t(cx, y + 218, h1, 13.5, 400, C["onSurfV"], "middle")
    if h2:
        s += t(cx, y + 238, h2, 13.5, 400, C["onSurfV"], "middle")
    # action slot: always 48dp, bottom — one full-width pill (same family as dialog buttons)
    ay = y + h_ - 68
    if fail:
        if kind == "perm_fail":
            s += dlg_btns(x + 20, ay, w_ - 40, None, "去授权")
        else:
            s += t(cx, ay + 29, "轻触任意处返回", 12.5, 400, C["onSurfV"], "middle", 'opacity="0.8"')
    elif kind in ("ready", "closing"):
        pass
    else:
        s += dlg_btns(x + 20, ay, w_ - 40, "取消", None)
    if guides:
        gc = "#E5322D"
        for gy in (y + 24, y + 168, y + 252, ay, ay + 48):
            s += line(x - 22, gy, x + w_ + 22, gy, gc, 0.8, "4 3")
        for gy, lab in ((y + 96, "图标槽 144"), (y + 210, "文字槽 84（标题 1 行 + 提示 2 行）"), (ay + 24, "操作槽 48")):
            s += t(x + w_ + 26, gy + 4, lab, 11, 600, gc)
    return s


def overlay_screen(kind, n=5, done=0, bg="ok"):
    s = device_screen(bg).replace(nav_pill(), "") + scrim()
    s += ov_card(kind, n, done)
    return s + end()
