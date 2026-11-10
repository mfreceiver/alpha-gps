"""AlphaGPS UI design v3 — explanatory diagrams (wide SVGs)."""
from lib3 import *  # noqa: F401,F403
import screens3 as S
import screens3b as B

INK = "#191C20"
INK2 = "#42474E"
BLUE = "#0B57D0"
RED = "#BA1A1A"
GREEN = "#1B6E3A"


def _marker():
    return ('<defs><marker id="ah" markerWidth="9" markerHeight="9" refX="7.5" refY="4.5" orient="auto">'
            '<path d="M0 0 L9 4.5 L0 9z" fill="#42474E"/></marker>'
            '<marker id="ahb" markerWidth="9" markerHeight="9" refX="7.5" refY="4.5" orient="auto">'
            '<path d="M0 0 L9 4.5 L0 9z" fill="#0B57D0"/></marker>'
            '<marker id="ahr" markerWidth="9" markerHeight="9" refX="7.5" refY="4.5" orient="auto">'
            '<path d="M0 0 L9 4.5 L0 9z" fill="#BA1A1A"/></marker></defs>')


def _arrow(x1, y1, x2, y2, col=INK2, dash=None, sw=1.6):
    mk = {INK2: "ah", BLUE: "ahb", RED: "ahr"}.get(col, "ah")
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="{sw}"{d} marker-end="url(#{mk})"/>'


def _box(x, y, w, h, title, sub=None, fill="#EDF1F7", stroke="#C2C7CF", tcol=INK):
    s = rect(x, y, w, h, fill, 14, stroke, 1.2)
    s += t(x + w / 2, y + (h / 2 + 5 if not sub else h / 2 - 3), title, 14, 600, tcol, "middle")
    if sub:
        s += t(x + w / 2, y + h / 2 + 15, sub, 11, 400, INK2, "middle")
    return s


# ---------------------------------------------------------------- glass layers
def glass_layers(theme="light"):
    was = mode()
    set_theme(theme)
    w_, h_ = 520, 330
    s = backdrop(w_, h_)
    lab = C["onSurf"]
    s += t(20, 30, "G0 背景：蓝色渐变 + 光斑（不模糊）", 12.5, 600, lab)
    s += glass(24, 48, 300, 130, 28, 1)
    s += t(44, 80, "G1 卡片 / 列表行", 14, 600, lab) + t(44, 101, "模糊 20 · 最通透", 12, 400, C["onSurfV"])
    s += glass(200, 120, 280, 64, 32, 2)
    s += t(222, 148, "G2 悬浮条 / 胶囊 / 顶栏", 14, 600, lab) + t(222, 168, "模糊 28 · 更实", 12, 400, C["onSurfV"])
    s += glass(96, 204, 320, 104, 28, 3)
    s += t(118, 238, "G3 对话框 / 浮层 / 底部面板", 14, 600, lab)
    s += t(118, 260, "模糊 32 · 最实 + 遮罩；承载需要决策的内容", 12, 400, C["onSurfV"])
    s += t(118, 282, "暗色：层级越高越亮、描边越明显", 12, 400, C["onSurfV"])
    out = svg_wrap(w_, h_, f'<clipPath id="gl"><rect width="{w_}" height="{h_}" rx="18"/></clipPath><g clip-path="url(#gl)">{s}</g>')
    set_theme(was)
    return out, w_, h_


def glass_vs_fallback(theme="light"):
    was = mode()
    set_theme(theme)
    w_, h_ = 520, 150
    s = backdrop(w_, h_)
    s += glass(16, 22, 236, 108, 24, 1)
    s += t(36, 56, "API 31+ · 实时模糊", 14, 600) + t(36, 78, "填充 α + 渐变描边 + 高光", 12, 400, C["onSurfV"])
    s += info_tag(36, 94, "RenderEffect / Haze", on_photo=False, size=11, h=20, pad=7)[0]
    s += glass_fallback(268, 22, 236, 108, 24, 1)
    s += t(288, 56, "API 26–30 · 降级", 14, 600) + t(288, 78, "近实色 + 1dp 描边 + 微渐变", 12, 400, C["onSurfV"])
    s += info_tag(288, 94, "无模糊 · 无额外开销", on_photo=False, size=11, h=20, pad=7)[0]
    out = svg_wrap(w_, h_, f'<clipPath id="gl"><rect width="{w_}" height="{h_}" rx="18"/></clipPath><g clip-path="url(#gl)">{s}</g>')
    set_theme(was)
    return out, w_, h_


# ---------------------------------------------------------------- info tag vs operation button
def tag_vs_button(theme="light"):
    was = mode()
    set_theme(theme)
    w_, h_ = 640, 250
    s = B.tile_defs() + backdrop(w_, h_)
    s += t(20, 28, "信息 tag（只读）", 13.5, 700, C["onSurf"])
    s += B.photo_art(20, 42, 150, 110, 5, 12)
    s += info_tag(28, 128, "JPG")[0] + info_tag(60, 128, "6.6 MB")[0]
    s += B.pr_tag(162, 50, 1, 4)
    x = 186
    for lab, ic_, col in (("传输中", None, C["green"]), ("64 GB", "sd_card", None), ("剩余 21.3 GB", None, None)):
        tg, tw = info_tag(x, 52, lab, on_photo=False, ic=ic_, col=col, size=11.5, h=22, pad=8)
        s += tg
        x += tw + 6
    s += t(186, 100, "扁平 · 半透明 · 无描边 · 无阴影", 12, 400, C["onSurfV"])
    s += t(186, 120, "圆角 4 · 字号 10–12 · 不响应点击", 12, 400, C["onSurfV"])
    s += t(186, 140, "颜色只表达状态（绿/琥珀/红/中性）", 12, 400, C["onSurfV"])
    s += line(20, 170, w_ - 20, 170, C["outlineV"], 1, "3 4")
    s += t(20, 196, "操作按钮（可点）", 13.5, 700, C["onSurf"])
    s += primary_btn(150, 180, 150, 48, "下载 3 张", "download", size=14.5)
    s += tonal_btn(312, 184, 92, 40, "连接", "bluetooth")
    s += text_btn(440, 204, "取消")
    s += rect(476, 184, 112, 40, "none", 20, C["error"], 1.5) + t(532, 209, "移除相机", 13.5, 600, C["error"], "middle")
    s += circle(612, 204, 20, C["onSurf"], op=0.07) + icon("grid_view", 600, 192, 24, C["onSurfV"])
    out = svg_wrap(w_, h_, f'<clipPath id="gl"><rect width="{w_}" height="{h_}" rx="18"/></clipPath><g clip-path="url(#gl)">{s}</g>')
    set_theme(was)
    return out, w_, h_


# ---------------------------------------------------------------- overlay anatomy
def overlay_anatomy():
    w_, h_ = 600, 380
    s = backdrop(w_, h_) + rect(0, 0, w_, h_, "#000", op=0.18)
    card = S.ov_card("approve", 5, 2, guides=True)
    s += f'<g transform="translate(-10,-232)">{card}</g>'
    return svg_wrap(w_, h_, f'<clipPath id="gl"><rect width="{w_}" height="{h_}" rx="18"/></clipPath><g clip-path="url(#gl)">{s}</g>'), w_, h_


# ---------------------------------------------------------------- tile anatomy
def tile_anatomy():
    w_, h_ = 600, 300
    s = B.tile_defs() + rect(0, 0, w_, h_, "#F3F6FC", 18)
    x, y, sz = 200, 30, 240
    s += B.photo_art(x, y, sz, sz, 5, 16)
    s += B.pr_tag(x + sz - 10, y + 10, 1, 4, 14, 24)
    tg, tw = info_tag(x + 10, y + sz - 34, "J+R", size=14, h=24, pad=7)
    s += tg
    s += info_tag(x + 10 + tw + 5, y + sz - 34, "54.6 MB", size=14, h=24, pad=7)[0]
    s += circle(x + 24, y + 24, 13, "#000", "#fff", 2, op=None).replace('fill="#000"', 'fill="#000" fill-opacity="0.25"')
    red = "#E5322D"
    notes = [((x + sz - 30, y + 22), (470, 40), "start", "① 保护与评分", "合并为一个标签"),
             ((x + 28, y + sz - 22), (150, 232), "end", "② 文件格式", "JPG / RAW / HEIF / J+R"),
             ((x + 100, y + sz - 22), (150, 276), "end", "③ 文件大小", "MB，J+R 为合计"),
             ((x + 24, y + 24), (150, 46), "end", "④ 选择圈（仅选择态）", "操作控件：命中区 48dp")]
    for (px, py), (tx, ty), anc, a_, b_ in notes:
        ex = tx - 6 if anc == "start" else tx + 6
        s += line(px, py, ex, ty - 4, red, 1.1) + circle(px, py, 3, red)
        s += t(tx, ty, a_, 12.5, 700, INK, anc) + t(tx, ty + 17, b_, 11, 400, INK2, anc)
    return svg_wrap(w_, h_, s), w_, h_


# ---------------------------------------------------------------- navigation + disconnect semantics
def nav_diagram():
    w_, h_ = 1320, 330
    s = _marker() + rect(0, 0, w_, h_, "#F7F9FD", 18, "#D9DEE7", 1)
    names = [("设备清单", "帮助 · 日志 · 设置"), ("设备页", "蓝牙状态 · 位置传输"), ("连接叠层", "蓝牙→Wi‑Fi→目录"),
             ("日期列表", "Wi‑Fi 会话中"), ("日期网格", "小/中/大 · 选择"), ("单张预览", "信息 · 下载")]
    bw, bh, gap, x0, y0 = 150, 62, 74, 30, 120
    xs = []
    for i, (a_, b_) in enumerate(names):
        x = x0 + i * (bw + gap)
        xs.append(x)
        wifi = i >= 3
        s += _box(x, y0, bw, bh, a_, b_, "#D3E3FD" if wifi else ("#FFFFFF" if i != 2 else "#EDF1F7"),
                  BLUE if wifi else "#C2C7CF")
    for i in range(5):
        s += _arrow(xs[i] + bw + 4, y0 + 20, xs[i + 1] - 5, y0 + 20, BLUE if i >= 1 else INK2)
        s += _arrow(xs[i + 1] - 4, y0 + 44, xs[i] + bw + 5, y0 + 44, INK2, "5 4")
    fw = ["轻触相机", "点照片传输", "就绪（自动）", "轻触日期", "轻触照片"]
    for i, lab in enumerate(fw):
        s += t(xs[i] + bw + gap / 2, y0 + 10, lab, 10.5, 600, BLUE if i >= 1 else INK2, "middle")
    back = [("返回：仅离开页面", "蓝牙保持、GPS 照常", INK2), ("取消：停止连接", "已连上的蓝牙保留", INK2),
            ("返回：自动结束 Wi‑Fi", "叠层「正在断开…」", RED), ("返回", "Wi‑Fi 保持", INK2), ("返回", "Wi‑Fi 保持", INK2)]
    for i, (a_, b_, col) in enumerate(back):
        cx = xs[i] + bw + gap / 2
        s += t(cx, y0 + bh + 26, a_, 11, 700, col, "middle") + t(cx, y0 + bh + 42, b_, 10.5, 400, INK2, "middle")
    # spans
    s += rect(xs[0], 34, xs[5] + bw - xs[0], 26, "#C8F0D2", 13)
    s += t((xs[0] + xs[5] + bw) / 2, 52, "蓝牙 + GPS 写入：全程常驻（Home / 后台 / 划卡 / 根页返回 都不断开）——仅「断开蓝牙」确认后才断", 12.5, 600, "#00210D", "middle")
    s += rect(xs[3], 70, xs[5] + bw - xs[3], 26, "#D3E3FD", 13)
    s += t((xs[3] + xs[5] + bw) / 2, 88, "Wi‑Fi 会话：只存在于这三屏；离开即结束", 12.5, 600, "#041E49", "middle")
    s += rect(xs[2] - 6, 70, bw + 12, 26, "#EDF1F7", 13) + t(xs[2] + bw / 2, 88, "蓝牙未连时自动先连", 11.5, 600, INK2, "middle")
    # exits
    s += rect(30, 250, w_ - 60, 60, "#FFFFFF", 14, "#D9DEE7", 1)
    s += t(48, 275, "离开应用", 13, 700, INK)
    items = [("Home / 切后台", "蓝牙+GPS 常驻；Wi‑Fi 30 秒后结束", 150), ("根页返回", "回桌面；蓝牙+GPS 常驻；Wi‑Fi 结束", 400),
             ("最近任务划掉", "蓝牙+GPS 常驻（前台服务续跑）；Wi‑Fi 结束", 690), ("设备页「断开蓝牙」并确认", "蓝牙断开、GPS 停；之后只能手动连接", 1010)]
    for a_, b_, x in items:
        s += t(x, 275, a_, 12.5, 700, RED if "断开蓝牙" in a_ else INK) + t(x, 295, b_, 11, 400, INK2)
    return svg_wrap(w_, h_, s), w_, h_


# ---------------------------------------------------------------- cache / lazy loading
def cache_diagram():
    w_, h_ = 760, 250
    s = rect(0, 0, w_, h_, "#F7F9FD", 18, "#D9DEE7", 1)
    s += t(24, 32, "一天 = 一条连续列表（例：227 张，4 列 → 57 行）", 13, 700, INK)
    x0, y0, bw = 24, 56, 712
    s += rect(x0, y0, bw, 40, "#E1E6ED", 8)
    segs = [(0.00, 0.22, "#C2C7CF", "已回收（仅保留元数据）"), (0.22, 0.34, "#A8C7FA", "LRU 保留"), (0.34, 0.42, "#6FA0F2", "预取 −2 屏"),
            (0.42, 0.56, "#0B57D0", "可见窗口"), (0.56, 0.68, "#6FA0F2", "预取 +3 屏（顺滚动方向加权）"), (0.68, 0.74, "#A8C7FA", "LRU"),
            (0.74, 1.0, "#E1E6ED", "未请求")]
    for a_, b_, col, lab in segs:
        s += rect(x0 + bw * a_, y0, bw * (b_ - a_), 40, col, 0)
    s += rect(x0 + bw * 0.42, y0 - 5, bw * 0.14, 50, "none", 6, INK, 1.6)
    s += t(x0 + bw * 0.49, y0 + 25, "可见", 12, 700, "#fff", "middle")
    ly = 122
    leg = [("#0B57D0", "可见窗口：立即请求缩略图（并发 ≤ 2）"), ("#6FA0F2", "预取：停止滚动 150 ms 后请求；快速滑动时只取消、不排队"),
           ("#A8C7FA", "LRU：缩略图位图上限 ≈ 300 张（小图约 25 MB），超出按最久未见回收"),
           ("#C2C7CF", "已回收：再次进入窗口时重新请求；元数据（文件名/大小/格式/保护评分）整日常驻内存")]
    for i, (col, lab) in enumerate(leg):
        s += rect(24, ly + i * 26, 16, 16, col, 4) + t(48, ly + i * 26 + 13, lab, 12, 400, INK)
    s += t(24, 238, "预览 Pager 覆盖整日：当前 ±1 预取预览图；预览图 LRU 5 张；EXIF 结果 LRU 200 条（每条 < 1 KB）", 12, 600, BLUE)
    return svg_wrap(w_, h_, s), w_, h_


# ---------------------------------------------------------------- viewer zones
def viewer_zones():
    w_, h_ = 420, 430
    s = rect(0, 0, w_, h_, "#F7F9FD", 18, "#D9DEE7", 1)
    px, py, pw, ph = 20, 16, 184, 398
    s += rect(px, py, pw, ph, "#000", 16)
    k = pw / 390

    def z(y0_, y1_, col, lab, sub, op=0.9):
        nonlocal s
        yy = py + y0_ * k
        hh = (y1_ - y0_) * k
        s += rect(px + 4, yy, pw - 8, hh, col, 5, op=op)
        s += line(px + pw, yy + hh / 2, px + pw + 14, yy + hh / 2, "#8C9199", 1)
        s += t(px + pw + 18, yy + hh / 2 + 1, lab, 12, 700, INK) + t(px + pw + 18, yy + hh / 2 + 16, sub, 10.5, 400, INK2)
    z(36, 92, "#3B4350", "顶栏（常驻）", "名称+大小 / 时间 / 序号")
    z(104, 216, "#274B8F", "上空白区 ①", "拍摄参数卡（EXIF）")
    z(228, 264, "#1B6E3A", "上空白区 ②", "定位状态 + 保护评分")
    z(292, 552, "#7FB3D5", "照片 3:2", "单击=沉浸；双击/捏合=缩放")
    z(564, 660, "#5B3A6B", "下空白区", "直方图 + 文件信息；上滑展开")
    z(672, 728, "#8A6D1B", "胶片条（常驻）", "±4 张，零通信")
    z(740, 796, "#0B57D0", "下载（常驻）", "操作按钮，位置固定")
    return svg_wrap(w_, h_, s), w_, h_


def aspect_diagram():
    """Viewer layout at three window shapes (schematic)."""
    w_, h_ = 700, 372
    s = rect(0, 0, w_, h_, "#F7F9FD", 18, "#D9DEE7", 1)
    STRIP, FILM, DL, PH = "#3B4250", "#6B7482", "#A8C7FA", "#5E88AC"

    def dev(x, y, dw, dh, kind):
        o = rect(x, y, dw, dh, "#0B0D10", 12, "#42474E", 1.4)
        if kind == "A":
            o += rect(x + 4, y + 78, dw - 8, 70, PH, 2)
            o += rect(x + 8, y + 34, 30, 8, "#7DDB93", 3, op=.85)
            o += rect(x + 6, y + dh - 62, dw - 12, 14, STRIP, 5, op=.92)
            for k in range(7):
                o += rect(x + 8 + k * 14, y + dh - 44, 11, 11, FILM, 2)
            o += rect(x + dw / 2 - 26, y + dh - 26, 52, 14, DL, 7)
        elif kind == "B":
            o += rect(x + 6, y + 52, dw - 12, 126, PH, 2)
            o += rect(x + 10, y + 30, 30, 8, "#7DDB93", 3, op=.85)
            o += rect(x + 8, y + dh - 44, 112, 14, STRIP, 5, op=.92)
            o += circle(x + dw - 18, y + dh - 37, 9, DL)
            for k in range(11):
                o += rect(x + 22 + k * 14, y + dh - 22, 11, 11, FILM, 2)
            o += rect(x + dw - 72, y + 22, 66, dh - 74, "none", 6, BLUE, 1.2, extra='stroke-dasharray="4 3"')
            o += t(x + dw - 39, y + 44, "详情", 9.5, 600, "#A8C7FA", "middle")
        else:
            o += rect(x + 40, y + 6, dw - 80, dh - 12, PH, 2)
            o += rect(x + 46, y + 22, 30, 8, "#7DDB93", 3, op=.85)
            o += rect(x + 8, y + dh - 22, 104, 14, STRIP, 5, op=.92)
            for k in range(6):
                o += rect(x + 118 + k * 14, y + dh - 20, 11, 11, FILM, 2)
            o += circle(x + dw - 16, y + dh - 15, 9, DL)
            o += rect(x + dw - 70, y + 6, 64, dh - 36, "none", 6, BLUE, 1.2, extra='stroke-dasharray="4 3"')
            o += t(x + dw - 38, y + 26, "详情", 9.5, 600, "#A8C7FA", "middle")
        return o

    def cap(x, y, title, cond, lines):
        o = t(x, y, title, 12.5, 700, INK) + t(x, y + 16, cond, 10.5, 600, BLUE)
        for k, ln in enumerate(lines):
            o += t(x, y + 32 + k * 14, ln, 10.5, 400, INK2)
        return o

    s += dev(24, 18, 112, 240, "A")
    s += cap(20, 278, "A 竖长（手机竖屏）", "宽 < 600dp 且 高 ≥ 480dp", ["上：状态行（定位 · 锁星）", "下：下载 / 胶片条 / 信息条", "详情 = 底部上滑面板"])
    s += dev(206, 18, 206, 240, "B")
    s += cap(204, 278, "B 近方形（折叠屏内屏 / 小平板 / 分屏）", "宽 ≥ 600dp 且 高 ≥ 480dp", ["状态行在顶栏下左侧；信息条靠左（最宽 420dp）", "下载缩为右侧圆钮；胶片条独占最底一行", "详情 = 右侧面板（宽 360dp），照片让出该宽度"])
    s += dev(452, 76, 232, 108, "C")
    s += cap(452, 278, "C 横屏（手机横屏）", "高 < 480dp", ["底部一行：信息条 | 胶片条 | 圆形下载", "顶栏透明叠在照片上，状态行并入顶栏右侧", "详情 = 右侧面板（宽 360dp）"])
    s += t(452, 40, "三种窗口形状 · 同一组四个部件", 12, 700, INK)
    s += t(452, 58, "只换位置，不换样式；都半透明、可与照片交叠", 10.5, 400, INK2)
    return svg_wrap(w_, h_, s), w_, h_
