# -*- coding: utf-8 -*-
"""v2 diagram sheets (SVG): navigation / disconnect semantics, paging removal, viewer swipe, tile anatomy."""
from lib2 import *  # noqa
import screens2 as S


def _box(x, y, w, h, title, sub, fill=None, stroke=None, tcol=None):
    s = rect(x, y, w, h, fill or C["card"], 14, stroke or C["outlineV"], 1.2)
    s += t(x + w / 2, y + 28, title, 15, 700, tcol or C["onSurf"], "middle")
    s += t(x + w / 2, y + 48, sub, 11.5, 400, C["onSurfV"], "middle")
    return s


def _arrow(x1, y1, x2, y2, col="#5B5566", dash=None, marker="ah", sw=1.8):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="{sw}"{d} marker-end="url(#{marker})"/>'


def _bar_icons(x, y, w, names, back=True, sel=None):
    """mini top bar: back icon + action icons (right aligned)."""
    s = rect(x + 10, y, w - 20, 30, C["bg"], 6, C["outlineV"], 0.8)
    if back:
        s += icon("arrow_back", x + 14, y + 5, 20, C["onSurf"])
    n = len(names)
    for i, nm in enumerate(names):
        s += icon(nm, x + w - 14 - 22 - 24 * (n - 1 - i), y + 5, 20, C["onSurfV"])
    return s


def nav_diagram():
    w, h = 1330, 470
    b = rect(0, 0, w, h, "#FFFFFF")
    names = [("相机列表", "（首页）", []), ("设备页", "BLE 已连接", ["info"]), ("Wi-Fi 连接叠层", "叠在设备页上", []),
             ("日期列表", "信息条 + 日期", ["refresh"]), ("日期网格", "连续滚动", ["refresh", "tune"]),
             ("单张预览", "左右滑动", ["check_box_outline"])]
    bw, gap, x0, y0 = 172, 44, 14, 62
    xs = [x0 + i * (bw + gap) for i in range(6)]
    for i, (a_, s_, acts) in enumerate(names):
        fill = C["primaryC"] if i == 1 else (C["secC"] if i == 2 else C["card"])
        b += _box(xs[i], y0, bw, 150, a_, s_, fill)
        if i > 0:
            b += _bar_icons(xs[i], y0 + 62, bw, acts, True)
        if i == 2:
            b += rect(xs[i] + 22, y0 + 108, bw - 44, 28, C["card2"], 10) + t(xs[i] + bw / 2, y0 + 127, "取消 · 重试", 12, 500, C["primary"], "middle")
        if i == 3:
            b += t(xs[i] + bw / 2, y0 + 130, "无断开图标", 11.5, 600, C["red"], "middle")
        if i == 4:
            b += t(xs[i] + bw / 2, y0 + 130, "无页码栏 · 无断开图标", 11.5, 600, C["red"], "middle")
        if i == 5:
            b += t(xs[i] + bw / 2, y0 + 130, "底部仅「下载」", 11.5, 500, C["onSurfV"], "middle")
    # forward arrows
    for i in range(5):
        b += _arrow(xs[i] + bw + 2, y0 + 75, xs[i + 1] - 2, y0 + 75)
    b += t(xs[1] + bw + gap / 2, y0 + 62, "进入", 10.5, 400, C["onSurfV"], "middle")
    b += t(xs[1] + bw + gap / 2 + 0, y0 + 96, "点入口", 10.5, 400, C["onSurfV"], "middle") if False else ""
    # back arrows (below)
    yb = y0 + 150 + 56
    for i in range(5, 0, -1):
        x_from = xs[i] + bw / 2
        x_to = xs[i - 1] + bw / 2
        col = "#E5322D" if i in (1, 3) else "#5B5566"
        b += _arrow(x_from, yb, x_to + 6, yb, col, None, "ahr" if col == "#E5322D" else "ah", 2.2)
        b += line(x_from, y0 + 150, x_from, yb, col, 2.2) + line(x_to, yb, x_to, y0 + 150, col, 2.2)
    # labels on the back arrows
    lab = {5: "返回 → 网格", 4: "返回 → 日期", 3: "返回 → 设备页", 2: "（叠层取消 → 留在设备页）", 1: "左上返回 → 相机列表"}
    for i, txt in lab.items():
        xm = (xs[i] + xs[i - 1]) / 2 + bw / 2
        col = "#E5322D" if i in (1, 3) else C["onSurfV"]
        b += t(xm, yb + 20, txt, 11.5, 700 if i in (1, 3) else 400, col, "middle")
    # disconnect semantic markers
    b += circle(xm := (xs[3] + xs[2]) / 2 + bw / 2, yb - 22, 11, "#E5322D") + t(xm, yb - 18, "A", 12, 700, "#fff", "middle")
    b += circle(xm1 := (xs[1] + xs[0]) / 2 + bw / 2, yb - 22, 11, "#E5322D") + t(xm1, yb - 18, "B", 12, 700, "#fff", "middle")
    # legend
    ly = yb + 52
    b += rect(14, ly, 1302, 118, C["card"], 12)
    b += circle(34, ly + 24, 10, "#E5322D") + t(34, ly + 28, "A", 12, 700, "#fff", "middle")
    b += t(54, ly + 28, "结束 Wi-Fi 传输会话（相机 Wi-Fi 关闭、手机网络恢复）：从日期列表返回设备页时发生 —— 沿用现有 navigateBack 行为，仅作为「离开照片区」的副作用，不是用户可见的“断开”。", 12.5, 400)
    b += circle(34, ly + 56, 10, "#E5322D") + t(34, ly + 60, "B", 12, 700, "#fff", "middle")
    b += t(54, ly + 60, "真正的断开（蓝牙主连接）：只在设备页点左上角返回键时发生。沿用现有 DeviceDetailViewModel.disconnect 的服务端断开链路；进入设备页时自动连接（沿用现有“直达连接”）。", 12.5, 400)
    b += t(34, ly + 92, "叠层上的「取消」只取消「本次 Wi-Fi 连接」，回到未启动 Wi-Fi 的设备页，蓝牙连接不受影响；照片浏览各页顶栏一律不放断开图标。", 12.5, 400, C["onSurfV"])
    return svg_wrap(w, h, b)


def paging_diagram():
    w, h = 1330, 330
    b = rect(0, 0, w, h, "#FFFFFF")
    # --- before
    b += t(14, 26, "现状：日期内按 PAGE_SIZE=60 切页（227 张 → 4 页）", 14, 700, C["red"])
    pages = [("第 1 页", 60), ("第 2 页", 60), ("第 3 页", 60), ("第 4 页", 47)]
    x = 14
    for nme, cnt in pages:
        wd = 300 * cnt / 60
        wd = min(wd, 300)
        b += rect(x, 44, wd, 70, C["card"], 8, C["outlineV"], 1.2)
        b += t(x + wd / 2, 74, nme, 14, 600, None, "middle") + t(x + wd / 2, 96, f"{cnt} 张 · 一次只渲染这一页", 11.5, 400, C["onSurfV"], "middle")
        x += wd + 10
    b += rect(14, 126, 1000, 30, C["secC"], 15) + t(514, 146, "‹  第 1 / 4 页  ›   ← 底部分页栏（要取消）", 13, 600, C["onSecC"], "middle")
    b += t(14, 182, "代价/连带：选择需跨页(map)、全选只限本页、拖选限本页(PageIdentity)、预览翻到边界要加载邻页(changingPage 锁滑动)", 12, 400, C["onSurfV"])
    # --- after
    b += t(14, 220, "v2：日期内一个连续网格（LazyVerticalGrid 全量 227 项；缩略图仍只按可见窗口加载）", 14, 700, C["green"])
    b += rect(14, 236, 1000, 70, C["primaryC"], 8, C["primary"], 1.5)
    b += t(514, 262, "227 张 · 连续滚动 · 右侧快速滚动条", 14, 600, C["onPrimaryC"], "middle")
    b += rect(430, 270, 170, 26, "none", 6, C["primary"], 2, extra='stroke-dasharray="5 3"')
    b += t(515, 288, "可见窗口 + 前后余量 → 请求缩略图", 11.5, 600, C["primary"], "middle")
    b += t(1030, 262, "目录本来就整日常驻内存", 12.5, 400)
    b += t(1030, 282, "（CatalogSnapshot.byDate），", 12.5, 400)
    b += t(1030, 302, "分页只是 UI 切片 → 取消零相机流量增量", 12.5, 400)
    return svg_wrap(w, h, b)


def swipe_diagram():
    w, h = 1330, 330
    b = S.tile_defs() + rect(0, 0, w, h, "#FFFFFF")
    pw, ph = 250, 170
    xs = [60, 390, 720]
    labels = ["5 / 227（上一张）", "6 / 227（当前）", "7 / 227（下一张）"]
    for i, x in enumerate(xs):
        g, end = S.photo_art(x, 70, pw, ph, 4 + i, 8, cid=f"sw{i}")
        b += g + end
        b += rect(x, 70, pw, ph, "none", 8, "#111" if i != 1 else C["primary"], 3 if i == 1 else 1.2)
        b += t(x + pw / 2, 60, labels[i], 13, 700 if i == 1 else 400, None, "middle")
    b += _arrow(380, 280, 120, 280, "#E5322D", None, "ahr", 3) + t(250, 306, "右滑 → 上一张", 12.5, 700, "#E5322D", "middle")
    b += _arrow(500, 280, 760, 280, "#E5322D", None, "ahr", 3) + t(630, 306, "左滑 → 下一张", 12.5, 700, "#E5322D", "middle")
    b += t(1000, 86, "核验结论（代码）", 14, 700)
    ls = ["WifiPhotoViewer.kt:203 HorizontalPager", "  userScrollEnabled = !changingPage（:111 起）",
          "  changingPage = 翻页加载中且视图索引不在当前页",
          "WifiPhotoViewer.kt:116-123 滑到哪张就请求该张与", "  左右邻张的缩略图（预取）",
          "WifiPhotoViewer.kt:124-136 settledPage → viewPhotoAt", "  （驱动预览加载 + 记录当前索引）"]
    for i, ln in enumerate(ls):
        b += t(1000, 110 + i * 20, ln, 12, 400 if ln.startswith("  ") else 500, C["onSurfV"] if ln.startswith("  ") else None)
    b += t(1000, 262, "v2：取消分页后 changingPage 不再出现，", 12.5, 600, C["primary"])
    b += t(1000, 282, "整日连续可滑；仅「下载进行中」锁定滑动。", 12.5, 600, C["primary"])
    return svg_wrap(w, h, b)


def tile_anatomy():
    w, h = 1330, 330
    b = S.tile_defs() + rect(0, 0, w, h, "#FFFFFF")
    x = 20
    specs = [("S", 91.5, "小 · 4 列", dict(fmt=True, size=False, ext=True)),
             ("M", 123.3, "中 · 3 列", dict(fmt=True, size=True, ext=True)),
             ("L", 187.0, "大 · 2 列", dict(fmt=True, size=True, ext=True))]
    for mode_, s_, lab, o in specs:
        o = dict(o, mode=mode_)
        b += S.tile(x, 70, s_, 0 if mode_ != "L" else 1, ("J&R", 1, 4, 54.6), o)
        b += t(x + s_ / 2, 52, lab, 13, 700, None, "middle")
        b += t(x + s_ / 2, 70 + s_ + 22, f"{s_:.1f} dp 方块", 11.5, 400, C["onSurfV"], "middle")
        x += s_ + 40
    # legend
    b += t(700, 56, "角标位置（四角固定，信息不抢画面）", 14, 700)
    rows = [("左上", "放大镜（快速预览）— 常驻，不可关"), ("右上", "保护锁 — 「保护与评分」开关"),
            ("右下", "选择框 — 常驻"), ("左下 · 下", "格式「J&R / JPG / RAW / HEIF」— 「文件格式」开关"),
            ("左下 · 同胶囊", "文件大小「· 54.6 MB」— 「文件大小」开关；小号方块自动隐藏"),
            ("左下 · 上", "评分胶囊（星色 + 数字）— 「保护与评分」开关")]
    for i, (a_, b_) in enumerate(rows):
        b += t(700, 84 + i * 24, a_, 12.5, 600, C["primary"]) + t(800, 84 + i * 24, b_, 12.5, 400)
    b += t(700, 250, "小号 91.5dp：格式胶囊 ≈ 30dp 宽，右下选择框 22dp，二者不相撞；", 12, 400, C["onSurfV"])
    b += t(700, 270, "中号 123dp：「J&R · 54.6 MB」≈ 84dp + 选择框 22dp + 边距 < 123dp；", 12, 400, C["onSurfV"])
    b += t(700, 290, "「Original」下载策略的 J&R 显示 JPG+RAW 合计大小，JPG-only 显示 JPEG 大小。", 12, 400, C["onSurfV"])
    return svg_wrap(w, h, b)
