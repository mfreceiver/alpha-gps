# -*- coding: utf-8 -*-
"""Component sheets and diagrams (strip variants, tile spec, toggle crops, data flow, state machines, wifi flow)."""
from lib import *
from screens import strip, tile, tile_defs, app_bar, status_bar, W


def box(x, y, w, h, fill, stroke, lines, size=12, r=8, tcol=None, weight=400, lh=16, pad=10):
    s = rect(x, y, w, h, fill, r, stroke, 1.2)
    n = len(lines)
    y0 = y + h / 2 - (n - 1) * lh / 2 + size * 0.35
    for i, ln in enumerate(lines):
        w_ = 600 if (i == 0 and weight == 600) else 400
        s += t(x + pad, y0 + i * lh, ln, size, w_, tcol or C["onSurf"])
    return s


def arrow(x1, y1, x2, y2, col="#5B5566", dash=None, red=False):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    m = "ahr" if red else "ah"
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="1.6"{d} marker-end="url(#{m})"/>'


def path_arrow(d, col="#5B5566", dash=None):
    ds = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<path d="{d}" fill="none" stroke="{col}" stroke-width="1.6"{ds} marker-end="url(#ah)"/>'


# ------------------------------------------------------------------ strip variants sheet
def strip_sheet():
    b = ""
    items = [("ok", "正常（默认）", "序列号遮罩，仅显示末 4 位；点眼睛图标展开/收起；长按复制（复制后提示「已复制」）。"),
             ("loading", "读取中（骨架）", "Ready 后立即出现骨架，逐项被真实值替换，各项独立，互不等待。"),
             ("partial", "部分不可用", "固件/序列号读取失败或相机未返回：显示「—」「不可用」，不影响照片浏览；可用空间未知时不画进度条。"),
             ("revealed", "序列号已展开", "展开 10 秒后自动重新遮罩（防止录屏/截图泄露）；离开页面即遮罩。"),
             ("nocard", "无卡 / 卡槽 1 无介质", "新机型仅浏览卡槽 1：无卡时显示「无卡」+说明；与现有「没有支持的照片」空态并存。")]
    y = 10
    for v, name, note in items:
        sv, h = strip(10, y + 22, v)
        b += t(10, y + 14, name, 13, 600, C["primary"])
        b += sv
        # note text on the right
        words = note
        # simple wrap at ~26 CJK chars
        lines, cur = [], ""
        for ch in words:
            cur += ch
            if len(cur) >= 26:
                lines.append(cur)
                cur = ""
        if cur:
            lines.append(cur)
        for i, ln in enumerate(lines):
            b += t(392, y + 54 + i * 17, ln, 12, 400, C["onSurfV"])
        y += 146
    return svg_wrap(740, y + 4, b, 1.0, bg="#FFFFFF")


# ------------------------------------------------------------------ toggle crops
def toggle_crop(kind):
    """kind: off | on | none (unavailable)."""
    b = rect(0, 0, 390, 150, C["bg"])
    tog = 266
    acts = ""
    if kind == "on":
        acts += circle(tog, 32, 20, C["secC"]) + icon("stars", tog - 12, 20, 24, C["primary"])
    else:
        acts += icon("stars", tog - 12, 20, 24, C["onSurfV"])
    acts += icon("refresh", 302, 20, 24, C["onSurfV"]) + icon("link_off", 350, 20, 24, C["onSurfV"])
    b += icon("arrow_back", 16, 20, 24, C["onSurf"]) + t(64, 40, "11月9日", 20, 500) + acts
    if kind == "none":
        b += rect(16, 86, 358, 52, "#322F35", 8)
        b += t(32, 108, "此相机或当前读取方式不提供保护/评分信息", 13, 400, "#F5EFF7")
        b += t(32, 126, "照片浏览不受影响", 11, 400, "#CAC4D0")
    if kind == "on":
        b += rect(16, 86, 358, 52, "#322F35", 8) + t(32, 117, "已显示保护与评分", 14, 400, "#F5EFF7")
        b += t(358, 117, "撤销", 14, 500, "#D0BCFF", "end")
    if kind == "off":
        b += rect(196, 58, 140, 28, "#322F35", 6) + t(266, 77, "显示保护与评分", 12, 400, "#F5EFF7", "middle")
        b += t(10, 120, "（长按图标出现提示）", 11, 400, C["onSurfV"])
    return svg_wrap(390, 150, b, 1.0, bg="#FFFFFF")


# ------------------------------------------------------------------ tile spec page body
def tile_spec():
    b = tile_defs()
    b += t(20, 28, "缩略图角标解剖（96dp 方格，示意放大 3×）", 15, 600, C["primary"])
    b += tile(40, 50, 1, "J&R", 1, 4, True, False, 288)
    # leader labels
    def lead(tx, ty, lx, ly, text1, text2, newflag=False, n=None):
        col = C["callout"] if newflag else "#5B5566"
        s = line(tx, ty, lx - 6, ly, col, 1.4, "4 3")
        s += t(lx, ly - 4, text1, 13, 600, col) + t(lx, ly + 13, text2, 12, 400, C["onSurfV"])
        return s
    b += lead(40 + 288 - 24 * 3 + 12, 50 + 24, 360, 70, "新增 · 保护锁（右上）", "24dp 方块 r3，黑 55%，锁 16dp 白；仅已保护时出现", True)
    b += lead(40 + 40, 50 + 3 * 52 + 27, 360, 150, "新增 · 评分胶囊（左下，格式角标正上方）", "h18 r4 黑 70%；星 14dp 按星级着色，数字粗体白；0/未知不显示", True)
    b += lead(40 + 60, 50 + 3 * 74 + 27, 360, 235, "现有 · 格式角标（左下）", "JPG / RAW / J&R / HEIF，黑 70% r4（不变）")
    b += lead(40 + 3 * 20 + 10, 50 + 3 * 20, 360, 305, "现有 · 预览放大镜（左上）", "24dp 视觉 / 40dp 触控（不变）")
    b += lead(40 + 288 - 3 * 20, 50 + 288 - 3 * 20, 360, 375, "现有 · 选择框（右下）", "22dp，选中后锁/评分仍可见（不被遮盖）")
    b += t(360, 410, "间距：角标距边 4dp；评分与格式角标垂直间距 4dp", 12, 400, C["onSurfV"])
    b += t(360, 428, "最小格宽 80dp 时仍不溢出（胶囊 34dp，格 ≥ 80dp）", 12, 400, C["onSurfV"])
    # matrix
    mx, my = 20, 470
    b += t(mx, my + 20, "组合示例（真实尺寸 96dp ×1.25 = 120）", 15, 600, C["primary"])
    combos = [("JPG", 0, 0, False, "无保护无评分\n（与基础态一致）"), ("JPG", 1, 0, False, "仅保护"),
              ("RAW", 0, 3, False, "仅评分 3★"), ("J&R", 1, 5, False, "保护 + 5★"),
              ("JPG", 0, 1, False, "评分 1★"), ("HEIF", 1, 4, False, "HEIF 保护 + 4★"),
              ("J&R", 1, 2, True, "选中态：锁/评分仍可见"), ("JPG", 0, 0, False, "该路径无字段\n= 不画占位，开关给提示")]
    for i, (fm, pr, rt, sel, cap) in enumerate(combos):
        cx = mx + (i % 4) * 150
        cy = my + 36 + (i // 4) * 190
        b += tile(cx, cy, i + 3, fm, pr, rt, True, sel, 120)
        for j, ln in enumerate(cap.split("\n")):
            b += t(cx, cy + 138 + j * 15, ln, 11.5, 400, C["onSurfV"])
    # rating palette
    px, py = 820, 50
    b += t(px, py, "评分色阶（星 + 数字，双通道）", 15, 600, C["primary"])
    names = {1: "灰蓝", 2: "天蓝", 3: "绿", 4: "琥珀", 5: "橙红"}
    for r in range(1, 6):
        yy = py + 20 + (r - 1) * 36
        b += rect(px, yy, 52, 24, "#000", 5, op=0.75) + icon("star", px + 6, yy + 4, 16, RATING[r]) + t(px + 34, yy + 17, str(r), 13, 700, "#fff", "middle")
        b += t(px + 66, yy + 17, f"{r} 星 · {names[r]} {RATING[r]}", 13, 400)
    b += t(px, py + 218, "• 颜色只做辅助：数字永远显示，色觉异常用户读数字即可", 12, 400, C["onSurfV"])
    b += t(px, py + 236, "• 底衬为深色半透明胶囊，保证在任意照片上可读", 12, 400, C["onSurfV"])
    b += t(px, py + 254, "• 0 星（相机未评分）= 不显示，不用空星占位", 12, 400, C["onSurfV"])
    # a11y
    ax, ay = 820, 340
    b += t(ax, ay, "无障碍与语义", 15, 600, C["primary"])
    rows = ["• 格 contentDescription：文件名 + 「，已保护」+「，评分 4 星」（按需拼接）",
            "• 锁/评分图标自身 contentDescription = null，避免 TalkBack 重复朗读",
            "• 顶栏开关：role=Switch，描述「显示保护与评分」，状态「已开启/已关闭」",
            "• 触控：不新增可点击区域（锁/评分只读），不影响现有点击/长按/拖选手势",
            "• 保护 = 相机侧删除保护；本应用不提供删除，故锁仅作信息展示，",
            "  下载不受保护状态限制（只读取，不修改相机文件）"]
    for i, ln in enumerate(rows):
        b += t(ax, ay + 24 + i * 19, ln, 12, 400, C["onSurf"])
    # grid legend
    b += t(ax, ay + 170, "与现有叠层的占位关系（避免冲突）", 15, 600, C["primary"])
    b += rect(ax, ay + 186, 130, 130, "#E7E0EC", 4)
    b += rect(ax + 6, ay + 192, 28, 28, "#B9B2C0", 3) + t(ax + 20, ay + 211, "放", 11, 400, "#333", "middle")
    b += rect(ax + 96, ay + 192, 28, 28, "#F4B6B0", 3) + t(ax + 110, ay + 211, "锁", 11, 600, C["callout"], "middle")
    b += rect(ax + 6, ay + 244, 40, 18, "#F4B6B0", 3) + t(ax + 26, ay + 257, "评分", 10, 600, C["callout"], "middle")
    b += rect(ax + 6, ay + 266, 40, 18, "#B9B2C0", 3) + t(ax + 26, ay + 279, "格式", 10, 400, "#333", "middle")
    b += rect(ax + 98, ay + 290, 24, 24, "#B9B2C0", 3) + t(ax + 110, ay + 306, "选", 10, 400, "#333", "middle")
    b += t(ax + 150, ay + 215, "右上角原本空闲 → 放保护锁", 12, 400, C["onSurf"])
    b += t(ax + 150, ay + 255, "评分叠在格式角标上方", 12, 400, C["onSurf"])
    b += t(ax + 150, ay + 275, "（红 = 新增，灰 = 现有）", 12, 400, C["onSurfV"])
    return svg_wrap(1500, 880, b, 1.0, bg="#FFFFFF")


# ------------------------------------------------------------------ data flow
TYPECOL = {"Z": ("#E3F4E4", "#2E7D32"), "S": ("#E1ECFB", "#1E5FB3"), "R": ("#DDF3F3", "#00796B"),
           "P": ("#FFEBD5", "#C25E00"), "D": ("#EFE3F7", "#7B3FA0")}


def dataflow():
    b = ""
    cols = [(10, 250, "① 触发时机"), (300, 330, "② 获取动作 / 数据来源"), (670, 300, "③ 状态落点（内存）"), (1010, 320, "④ 界面位置")]
    for x, w, name in cols:
        b += rect(x, 8, w, 28, C["primary"], 6) + t(x + w / 2, 27, name, 13, 600, "#fff", "middle")
    rows = [
        ("Z", ["握手 SyncingTime 步骤", "时区/DST 偏移变化（30 s 监测）"],
         ["CC13 写入 → CC0E 通知回执（≤30 s）", "CameraTimeSyncController.sync()  已有，", "结果现仅写日志"],
         ["CameraSession.timeSync（新增字段）", "NotStarted·Syncing·Applied(at)·Rejected", "·Unconfirmed·WriteFailed·Unsupported"],
         ["设备页 ① 时间已校正", "Ⓘ「时间校正」"]),
        ("R", ["设备页/Ⓘ 可见且已连接", "进入 1 次，之后每 5 s", "离开页面/断开即停"],
         ["Android readRemoteRssi → onReadRemoteRssi", "iOS peripheral.readRSSI()", "新增 transport 方法 + RssiRead 事件"],
         ["CameraSession.rssi: Int?, rssiAt（新增）", "仅最新值，不存历史"],
         ["设备页 ② 蓝牙信号", "Ⓘ「蓝牙信号」"]),
        ("P", ["Wi-Fi 会话 Ready 后 1 次", "点「刷新」时再读", "（BLE 期间不读）"],
         ["PTP 属性：标准 0x5001 / Sony 0xD218", "（走 SonyCameraProperties 解析，待实机核实）", "新增 1 次 PTP 请求"],
         ["BatteryCache{percent, obtainedAt, cameraId}", "应用级缓存（新增），不落盘"],
         ["设备页 ③ 电池电量（带获取时间）", "Ⓘ · Wi-Fi 信息条电量"]),
        ("S", ["每次 sendToDevice（5 s 周期 /", "新定位 / 握手完成）", "每次写完成回调"],
         ["LocationTransmissionManager.sendToDevice：", "入队成功 → sent+1", "onCharacteristicWrite(位置特征) → written/failed"],
         ["CameraSession.gpsStats（新增）", "sent·written·failed·bytes·lastWriteAt", "连接时清零；断开保留末值"],
         ["设备页 ④⑤⑥ GPS 卡片", "Ⓘ 明细 · 重置计数"]),
        ("D", ["Wi-Fi 会话 OpenSession 后 1 次"],
         ["PTP GetDeviceInfo 0x1001", "PtpDeviceInfoParser 现仅解析到 OperationsSupported", "→ 继续解析 DeviceVersion / SerialNumber"],
         ["WifiRemoteState.deviceInfo（新增）", "firmware, serial 仅内存、不进日志", "会话结束即清除"],
         ["Wi-Fi 信息条 固件/序列号", "Ⓘ（本会话内可见）"]),
        ("P", ["同上 + 点「刷新」"],
         ["PTP GetStorageInfo 0x1005（卡槽 1）", "MaxCapacity / FreeSpaceInBytes", "新增 1 次请求/卡"],
         ["WifiRemoteState.storage{total, free}", "（新增）"],
         ["Wi-Fi 信息条 存储卡 + 进度条"]),
        ("D", ["目录枚举（进入、刷新）", "下载前 revalidate"],
         ["v4: SonyContentCatalog.kt:57 take(8) 已读未用", "旧: ObjectInfo.ProtectionStatus / PropList 0xDC03", "解析即得，零额外流量"],
         ["WifiCameraPhoto.protected: Boolean?", "WifiCameraPhoto.rating: Int?（null=未知）"],
         ["缩略图 锁 / ★n（顶栏开关控制）"]),
        ("Z", ["连接状态变化事件"],
         ["CameraSession.phase / mtu（已有）", "名称、MAC（已有）"],
         ["（无需改动）"],
         ["设备页名称块 · Ⓘ（已有）"]),
    ]
    y = 48
    rh = 96
    for typ, a, bb, c, d in rows:
        fill, stroke = TYPECOL[typ]
        h = rh - 10
        b += box(10, y, 250, h, "#FAF7FC", "#CAC4D0", a, 12)
        b += box(300, y, 330, h, fill, stroke, bb, 12)
        b += box(670, y, 300, h, "#FAF7FC", "#CAC4D0", c, 12)
        b += box(1010, y, 320, h, "#FAF7FC", "#CAC4D0", d, 12, weight=600)
        for x1, x2 in ((260, 300), (630, 670), (970, 1010)):
            b += arrow(x1, y + h / 2, x2 - 2, y + h / 2)
        y += rh
    # legend
    lx = 10
    leg = [("Z", "已有数据，仅需落字段"), ("S", "手机侧新增计数器"), ("R", "新增 RSSI 读取"), ("P", "新增 PTP 请求"), ("D", "协议已返回、代码丢弃 → 解析即得")]
    for k, txt in leg:
        f, s_ = TYPECOL[k]
        b += rect(lx, y + 4, 18, 14, f, 3, s_) + t(lx + 24, y + 16, txt, 12, 400, C["onSurf"])
        lx += 24 + 14 * len(txt) * 0.78 + 36
    return svg_wrap(1340, y + 30, b, 1.0, bg="#FFFFFF")


# ------------------------------------------------------------------ state machines
def state_machines():
    b = ""
    # (a) time sync
    b += t(10, 22, "A. 时间校正状态 TimeSyncStatus（新增）", 15, 600, C["primary"])
    def st(x, y, w, name, col, bg, sub=""):
        s = rect(x, y, w, 40, bg, 20, col, 1.5) + t(x + w / 2, y + 18 if sub else y + 25, name, 13, 600, col, "middle")
        if sub:
            s += t(x + w / 2, y + 32, sub, 10.5, 400, C["onSurfV"], "middle")
        return s
    b += st(10, 150, 110, "未开始", C["outline"], "#F1ECF4", "断开/握手前")
    b += st(190, 150, 110, "校时中", C["amberText"], "#FFF4D1", "Syncing")
    b += st(400, 50, 140, "已校正", C["green"], "#E3F4E4", "Applied(at)")
    b += st(400, 112, 140, "被相机拒绝", C["amberText"], "#FFF4D1", "Rejected(reason)")
    b += st(400, 174, 140, "校时未确认", C["amberText"], "#FFF4D1", "Unconfirmed")
    b += st(400, 236, 140, "写入失败", C["red"], "#FCE4E2", "WriteFailed")
    b += st(190, 236, 110, "不支持", C["outline"], "#F1ECF4", "CC0D 声明")
    b += arrow(120, 170, 188, 170)
    b += t(155, 160, "握手", 10.5, 400, C["onSurfV"], "middle")
    b += arrow(300, 160, 398, 72) + t(305, 108, "CC0E applied=1", 10.5, 400, C["green"])
    b += arrow(300, 164, 398, 132) + t(318, 140, "applied=0", 10.5, 400, C["amberText"])
    b += arrow(300, 176, 398, 194) + t(318, 200, "30 s 无回执", 10.5, 400, C["amberText"])
    b += arrow(290, 190, 398, 256) + t(308, 232, "CC13 写失败", 10.5, 400, C["red"])
    b += arrow(245, 190, 245, 234) + t(255, 214, "CC0D=不支持", 10.5, 400, C["onSurfV"])
    b += path_arrow("M470 50 C470 36 250 36 250 148", "#5B5566", "5 4") + t(360, 44, "tz/DST 偏移变化 → 重新 Syncing（结果出来前保留上次状态）", 10.5, 400, C["onSurfV"], "middle")
    b += t(10, 300, "规则：只有相机回执 applied=1 才算「已校正」；写成功但无回执 = 「未确认」（与「校时不阻塞 GPS」一致）。", 12, 400, C["onSurf"])
    b += t(10, 318, "断开/重连：状态复位为未开始；任何终态都不会让设备页误显示「已校正」。", 12, 400, C["onSurf"])

    # (b) GPS freshness
    ox = 700
    b += t(ox, 22, "B. GPS 写入新鲜度（距最近一次「已写入」确认）", 15, 600, C["primary"])
    segs = [(0, 15, C["green"], "传输中"), (15, 60, C["amber"], "写入延迟"), (60, 90, C["red"], "写入中断")]
    scale = 7.2
    for a, z, col, name in segs:
        b += rect(ox + a * scale, 60, (z - a) * scale, 26, col, 0, op=0.85)
        b += t(ox + (a + z) / 2 * scale, 78, name, 12, 600, "#fff" if col != C["amber"] else "#3b2a00", "middle")
    for sv in (0, 5, 10, 15, 30, 60, 90):
        b += line(ox + sv * scale, 86, ox + sv * scale, 94, "#5B5566") + t(ox + sv * scale, 108, f"{sv}s", 10.5, 400, C["onSurfV"], "middle")
    b += t(ox, 134, "上传周期 5 s（LOCATION_UPDATE_INTERVAL_MS）→ 15 s = 连续 3 个周期无确认；阈值为设计建议，可调。", 12, 400, C["onSurf"])
    b += t(ox, 156, "灰色「已停止」：会话非 Transmitting（未连接/握手中），不套用此色阶。", 12, 400, C["onSurf"])
    b += t(ox, 184, "原因提示优先级（只显示最高一条）：", 13, 600, C["onSurf"])
    for i, ln in enumerate(["1. 相机提示位置传输已关闭（locationDisabledByCamera，已有）",
                            "2. 手机暂无定位（LocationSource 无 fix / 定位权限）",
                            "3. 已传输 − 已写入 差值持续增大：蓝牙拥塞/队列取消",
                            "4. 其它：显示「已 N 秒未收到相机确认」"]):
        b += t(ox + 8, 206 + i * 19, ln, 12, 400, C["onSurf"])
    b += t(ox, 296, "已传输 = sendToDevice 入队成功；已写入 = GATT 写完成回调 Success。", 12, 400, C["onSurf"])
    b += t(ox, 314, "失败/取消 = Failure / Timeout / Cancelled / NotInitiated（仅在 Ⓘ 明细显示）。", 12, 400, C["onSurf"])

    # (c) RSSI buckets
    oy = 380
    b += t(10, oy, "C. 蓝牙信号 RSSI 分档与轮询", 15, 600, C["primary"])
    buckets = [("≥ −60 dBm", "强", 4, C["green"]), ("−60 ~ −75", "良好", 3, C["green"]),
               ("−75 ~ −85", "一般", 2, C["amber"]), ("< −85", "弱", 1, C["red"]), ("读取失败/未连接", "—", 0, C["outline"])]
    for i, (rg, nm, lv, col) in enumerate(buckets):
        yy = oy + 26 + i * 34
        b += bars(14, yy, lv, col) + t(50, yy + 15, rg, 13, 500) + t(190, yy + 15, nm, 13, 600, col if lv else C["outline"])
    for i, ln in enumerate(["• 页面可见 + 已连接：进入时读 1 次，之后每 5 s（与 GPS 周期对齐）",
                            "• 页面不可见 / 应用后台 / 断开：立即停止，不后台轮询（省电）",
                            "• 读取失败：保留上次值最长 15 s，之后显示「—」，不显示陈旧值",
                            "• 分档阈值为设计建议，实机标定后可调（待核实 α 系列实际范围）",
                            "• RSSI 读取是否占 GATT 队列：待实现时核实，须让位于位置写入"]):
        b += t(250, oy + 40 + i * 24, ln, 12, 400, C["onSurf"])

    # (d) battery cache
    b += t(ox, oy, "D. 电池缓存新鲜度", 15, 600, C["primary"])
    tl = [(0, 120, C["green"], "≤ 30 分钟：正常显示"), (120, 280, C["outline"], "30 分钟~24 小时：灰色 + 获取时间"), (400, 130, "#BDB6C4", "> 24 小时/换机：「—」")]
    for a, w, col, nm in tl:
        b += rect(ox + a, oy + 24, w, 26, col, 0, op=0.9) + t(ox + a + w / 2, oy + 42, nm, 11, 600, "#fff" if col != "#BDB6C4" else "#222", "middle")
    for i, ln in enumerate(["• 获取时机仅限 Wi-Fi/PTP 会话（Ready 后 1 次，点「刷新」再读 1 次）",
                            "• 缓存键 = cameraId（MAC）；仅应用进程内，不写数据库",
                            "• 电量 ≤ 20% 红色并改称「电池电量低」；≤ 10% 同色加粗",
                            "• 备选（待核实）：相机 BLE 若暴露标准 Battery Service 0x180F，",
                            "  可在 BLE 期间直接读取，此时「上次获取」标注可省略"]):
        b += t(ox, oy + 76 + i * 22, ln, 12, 400, C["onSurf"])
    return svg_wrap(1340, 640, b, 1.0, bg="#FFFFFF")


# ------------------------------------------------------------------ wifi flow
def wifi_flow():
    steps = [
        ("点击「Wi-Fi 照片传输」", "进入 Wi-Fi 页（现有）", "信息条不显示", "Z"),
        ("BLE 启动相机 Wi-Fi → 加入网络", "PreparingCamera → JoiningNetwork（现有）", "沿用现有进度/失败页", "Z"),
        ("OpenSession → GetDeviceInfo 0x1001", "OpeningSession → Ready", "固件、序列号入内存（解析即得，无额外请求）", "D"),
        ("枚举目录（第 1 页先出）", "browseDates / browsePhotos（现有）", "缩略图出现；保护/评分随记录入内存；开关可用", "D"),
        ("GetStorageInfo 0x1005（卡槽 1）", "目录首屏返回后再发，不拖慢首屏", "「存储卡」「可用/已用」进度条出现", "P"),
        ("读电量 0x5001 / 0xD218", "同一小批请求，失败只影响本项", "电量 chip 出现；写入设备页缓存", "P"),
        ("用户点右上角开关", "纯本地，不发请求", "角标即时显隐 + 偏好记忆", "Z"),
        ("用户点「刷新」", "重枚举目录 + GetStorageInfo + 电量", "角标/容量/电量更新；信息条骨架不重现（保留旧值）", "P"),
        ("断开 / 会话结束", "Closing → Idle", "信息条消失；固件/序列号清除；电量缓存保留", "Z"),
    ]
    b = rect(10, 8, 480, 28, C["primary"], 6) + t(250, 27, "协议 / 会话步骤", 13, 600, "#fff", "middle")
    b += rect(520, 8, 400, 28, C["primary"], 6) + t(720, 27, "内部动作", 13, 600, "#fff", "middle")
    b += rect(950, 8, 380, 28, C["primary"], 6) + t(1140, 27, "用户看到的界面", 13, 600, "#fff", "middle")
    y = 46
    for i, (a, bb, c, typ) in enumerate(steps):
        f, s_ = TYPECOL[typ]
        b += box(10, y, 480, 56, f, s_, [f"{i + 1}. {a}"], 13, weight=600)
        b += box(520, y, 400, 56, "#FAF7FC", "#CAC4D0", [bb], 12)
        b += box(950, y, 380, 56, "#FAF7FC", "#CAC4D0", [c], 12)
        b += arrow(490, y + 28, 518, y + 28) + arrow(920, y + 28, 948, y + 28)
        if i < len(steps) - 1:
            b += arrow(250, y + 56, 250, y + 70)
        y += 70
    b += rect(10, y + 2, 1320, 64, "#FFF4D1", 8, "#C9A227", 1.2)
    b += t(24, y + 26, "失败原则：每项独立。GetDeviceInfo/GetStorageInfo/电量/评分任一失败 → 该项显示「—」或「不可用」，", 13, 600, C["amberText"])
    b += t(24, y + 48, "不弹错误、不阻塞照片浏览与下载；用户点「刷新」可重试。PTP 事务本就串行，信息类请求排在首屏之后，不影响首屏速度。", 12, 400, C["onSurf"])
    return svg_wrap(1340, y + 80, b, 1.0, bg="#FFFFFF")
