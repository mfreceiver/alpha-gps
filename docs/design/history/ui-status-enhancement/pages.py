# -*- coding: utf-8 -*-
"""Build mockups/*.svg, index.html and the PDF.  Run: python3 pages.py"""
import os, subprocess, sys
from lib import *
import screens as S
import extras as E

HERE = os.path.dirname(os.path.abspath(__file__))
MOCK = os.path.join(HERE, "mockups")
os.makedirs(MOCK, exist_ok=True)


def save(name, svg):
    with open(os.path.join(MOCK, name + ".svg"), "w", encoding="utf-8") as f:
        f.write(svg)
    return f"mockups/{name}.svg"


# ------------------------------------------------------------------ standalone mockups
F = {}
F["dev_ok"] = save("01_device_connected", S.phone_svg(S.device_screen("ok", True)))
F["dev_ok_plain"] = save("01b_device_connected_plain", S.phone_svg(S.device_screen("ok")))
F["dev_conn"] = save("02_device_connecting", S.phone_svg(S.device_screen("connecting")))
F["dev_disc"] = save("03_device_disconnected", S.phone_svg(S.device_screen("disconnected")))
F["dev_warn"] = save("04_device_warning", S.phone_svg(S.device_screen("warn")))
F["info_top"] = save("05_info_dialog_top", S.phone_svg(S.info_screen(0, True, "a")))
F["info_scr"] = save("06_info_dialog_scrolled", S.phone_svg(S.info_screen(260, True, "b")))
F["info_disc"] = save("07_info_dialog_disconnected", S.phone_svg(S.info_screen(0, False, "c")))
F["wifi_base"] = save("08_wifi_basic", S.phone_svg(S.wifi_screen(False, callouts=True)))
F["wifi_ext"] = save("09_wifi_extended", S.phone_svg(S.wifi_screen(True, callouts=True, snackbar=True)))
F["wifi_sel"] = save("10_wifi_extended_selecting", S.phone_svg(S.wifi_screen(True, selected_idx=5)))
F["wifi_na"] = save("11_wifi_toggle_unavailable", S.phone_svg(S.wifi_screen(False, strip_variant="partial", toggle_unavailable=True)))
F["wifi_load"] = save("12_wifi_strip_loading", S.phone_svg(S.wifi_screen(False, strip_variant="loading")))
F["strips"] = save("13_info_strip_variants", E.strip_sheet())
F["tog_off"] = save("14a_toggle_off", E.toggle_crop("off"))
F["tog_on"] = save("14b_toggle_on", E.toggle_crop("on"))
F["tog_none"] = save("14c_toggle_unavailable", E.toggle_crop("none"))
F["tile"] = save("15_tile_badge_spec", E.tile_spec())
F["flow"] = save("16_data_flow", E.dataflow())
F["states"] = save("17_state_machines", E.state_machines())
F["wflow"] = save("18_wifi_session_flow", E.wifi_flow())


def img(key, w=None, h=None, cls=""):
    a = ""
    if w:
        a += f' width="{w}"'
    if h:
        a += f' height="{h}"'
    return f'<img src="{F[key]}"{a} class="{cls}">'


def phone(key, scale, cap):
    return (f'<figure class="ph"><img src="{F[key]}" width="{int(390 * scale)}" height="{int(844 * scale)}">'
            f'<figcaption>{cap}</figcaption></figure>')


def n(i):
    return f'<span class="n">{i}</span>'


def tag(kind):
    m = {"Z": ("零成本", "z"), "S": ("手机侧新计数器", "s"), "R": ("新增 RSSI 读取", "r"),
         "P": ("新增 PTP 请求", "p"), "D": ("解析即得", "d"), "N": ("新增状态字段", "z")}
    return f'<span class="tag {m[kind][1]}">{m[kind][0]}</span>'


CSS = """
@page { size: 1400px 1000px; margin: 0 }
* { box-sizing: border-box }
body { margin:0; font-family:'Source Han Sans CN','Noto Sans CJK SC',sans-serif; color:#1D1B20; font-size:14px; line-height:1.55 }
.page { width:1400px; height:1000px; padding:28px 36px 20px; page-break-after:always; position:relative; overflow:hidden; background:#fff }
.page:last-child { page-break-after:auto }
.hd { display:flex; align-items:baseline; border-bottom:2px solid #6750A4; padding-bottom:8px; margin-bottom:14px }
.hd h1 { font-size:24px; margin:0; color:#6750A4; font-weight:700 }
.hd .sub { margin-left:16px; color:#625B71; font-size:14px }
.hd .no { margin-left:auto; color:#79747E; font-size:13px }
.row { display:flex; gap:24px; align-items:flex-start }
.col { flex:1; min-width:0 }
table { border-collapse:collapse; width:100%; font-size:12.5px }
th { background:#6750A4; color:#fff; text-align:left; padding:6px 8px; font-weight:600 }
td { border-bottom:1px solid #E0DAE6; padding:6px 8px; vertical-align:top }
tr:nth-child(even) td { background:#FAF7FC }
h2 { font-size:17px; color:#6750A4; margin:12px 0 6px }
h3 { font-size:14px; margin:10px 0 4px }
.n { display:inline-block; width:18px; height:18px; border-radius:9px; background:#E5322D; color:#fff; font-size:11px; font-weight:700; text-align:center; line-height:18px; margin-right:6px }
.tag { display:inline-block; font-size:11px; padding:0 7px; border-radius:9px; border:1px solid; white-space:nowrap }
.tag.z { background:#E3F4E4; color:#2E7D32; border-color:#2E7D32 }
.tag.s { background:#E1ECFB; color:#1E5FB3; border-color:#1E5FB3 }
.tag.r { background:#DDF3F3; color:#00796B; border-color:#00796B }
.tag.p { background:#FFEBD5; color:#C25E00; border-color:#C25E00 }
.tag.d { background:#EFE3F7; color:#7B3FA0; border-color:#7B3FA0 }
.ph { margin:0; text-align:center }
.ph figcaption { font-size:12.5px; color:#49454F; margin-top:4px }
.note { background:#FFF4D1; border:1px solid #C9A227; border-radius:8px; padding:8px 12px; font-size:12.5px }
.box { background:#F3EDF7; border-radius:10px; padding:10px 14px }
ul { margin:4px 0; padding-left:20px } li { margin:2px 0 }
code { background:#EEE8F4; padding:0 4px; border-radius:3px; font-size:12px; font-family:'DejaVu Sans Mono',monospace }
.todo { color:#B3261E; font-weight:600 }
.ok { color:#2E7D32; font-weight:600 }
.dense table { font-size:11.2px } .dense td { padding:3px 7px } .dense code { font-size:10.5px }
.small { font-size:12px; color:#49454F }
img { display:block }
"""

pages = []


def page(title, sub, body):
    i = len(pages) + 1
    pages.append(f'<section class="page"><div class="hd"><h1>{title}</h1><span class="sub">{sub}</span>'
                 f'<span class="no">alpha-gps · 状态增强 UI 设计方案 · {i} / 14</span></div>{body}</section>')


# ============================================================ 1 cover
page("相机页状态增强 · UI/UX 设计方案", "待用户确认后再派开发 · 本方案不含任何应用代码改动",
     f"""
<div class="row">
 <div class="col" style="flex:1.1">
  <div class="box"><b>需求（用户原文要点）</b>
  <ul>
   <li>相机页增加：<b>时间已校正</b>、<b>电池电量</b>、<b>信号质量（RSSI）</b>、<b>GPS 手机侧传输/写入统计</b>（已传输次数、已写入次数、最近一次写入时间）</li>
   <li>进入 Wi-Fi（照片传输）后：顶部显示 <b>固件版本、序列号、卡容量</b></li>
   <li>右上角 <b>切换开关</b>：打开后每张照片额外显示 <b>保护状态（锁）</b>、<b>评分（彩色星 + 数字）</b></li>
  </ul></div>
  <h2>设计原则</h2>
  <ul>
   <li><b>如实标注来源</b>：每个数据项注明「从哪来、何时刷新、没有时显示什么」，不做相机/协议不支持的承诺。</li>
   <li><b>不拖慢主流程</b>：校时、GPS 传输、Wi-Fi 首屏照片列表不等待任何新增信息；信息类请求排在首屏之后，失败只影响该项。</li>
   <li><b>陈旧不装新</b>：电量、断开后的计数一律灰显并标时间；绝不把缓存值当实时值。</li>
   <li><b>基础态零变化</b>：开关默认关闭时，照片网格与现状完全一致；新增叠层放在原先空闲的右上角与格式角标上方。</li>
   <li><b>隐私</b>：序列号默认遮罩、仅内存、不入日志；缓存不落盘。</li>
   <li><b>沿用现有风格</b>：Material 3、现有 Ⓘ / 卡片 / 顶栏结构、Material 图标、仅 en + zh-rCN 文案。</li>
  </ul>
  <div class="note" style="margin-top:10px"><b>阅读提示</b>：红色圆点编号 {n(1)} = 该屏新增元素，对应右侧/下方说明；
  标签 {tag('Z')} {tag('S')} {tag('R')} {tag('P')} {tag('D')} 表示数据获取成本（见第 3 页）。</div>
 </div>
 <div class="col" style="flex:1">
  <h2>目录</h2>
  <table><tr><th style="width:44px">页</th><th>内容</th></tr>
  <tr><td>1</td><td>封面、需求、设计原则、目录</td></tr>
  <tr><td>2</td><td><b>现状核实</b>：代码里每项数据现在有没有、在哪里（含保护/评分结论）</td></tr>
  <tr><td>3</td><td>数据来源 / 刷新时机 / 不可用回退总表</td></tr>
  <tr><td>4</td><td>设备页（已连接）高保真 + 6 处新增说明</td></tr>
  <tr><td>5</td><td>设备页状态变体：校时中 / 未连接 / 异常告警</td></tr>
  <tr><td>6</td><td>Ⓘ 相机信息对话框：实时状态 + GPS 传输明细</td></tr>
  <tr><td>7</td><td>Wi-Fi 照片页：基础态 vs 扩展态（右上角开关）</td></tr>
  <tr><td>8</td><td>顶部信息条 5 种状态 + 开关交互（含不可用提示）</td></tr>
  <tr><td>9</td><td>缩略图角标规范：锁、星级配色、占位、无障碍</td></tr>
  <tr><td>10</td><td>数据流图：触发 → 获取 → 状态 → 界面</td></tr>
  <tr><td>11</td><td>状态机与刷新节奏：校时、GPS 新鲜度、RSSI、电量缓存</td></tr>
  <tr><td>12</td><td>Wi-Fi 会话交互流程（9 步）与失败原则</td></tr>
  <tr><td>13</td><td>文案（中/英）、图标清单、状态色</td></tr>
  <tr><td>14</td><td>关键取舍、待用户确认的问题、待实机核实项、开发拆分建议</td></tr></table>
  <h2>文件位置</h2>
  <p class="small">本 PDF：<code>design/ui-status-enhancement.pdf</code><br>
  单屏 SVG：<code>design/ui-status-enhancement/mockups/*.svg</code>（21 张）<br>
  生成脚本：<code>design/ui-status-enhancement/pages.py</code>（可重复构建）<br>
  说明文档：<code>design/ui-status-enhancement/README.md</code>、<code>SPEC.md</code></p>
 </div>
</div>""")

# ============================================================ 2 verification
page("现状核实", "基于当前分支 feature/browse-only-download 的代码阅读；标「待实机核实」的需连真机确认",
     f"""<div class="dense">
<table>
<tr><th style="width:118px">数据项</th><th style="width:560px">代码现状（文件:行）</th><th style="width:110px">结论</th><th>对设计的影响</th></tr>
<tr><td><b>连接状态/名称/MAC/MTU</b></td><td>设备页名称块 + Ⓘ 已显示；<code>CameraSession.mtu</code>；Connected 仅当 <code>BleSessionPhase.Transmitting</code>（ui/device/DeviceDetailLogic.kt）</td><td class="ok">已有</td><td>不改</td></tr>
<tr><td><b>时间已校正</b></td><td><code>CameraTimeSyncController.sync()</code>（session/CameraTimeSyncController.kt:154-215）写 CC13，等待 ≤30 s 的 CC0E 回执 <code>CameraTimeSettingResult(applied, reason)</code>；结果<b>只写日志</b>，<code>CameraSession</code>（CameraSessionRegistry.kt:14-42）无对应字段</td><td class="todo">数据已产生，未保存</td><td>新增 <code>timeSync</code> 状态字段；其余零成本</td></tr>
<tr><td><b>RSSI</b></td><td>全工程无 <code>readRemoteRssi</code> / <code>onReadRemoteRssi</code>（AndroidBleTransport.kt 仅有 onMtuChanged:382、onCharacteristicWrite:403）；iOS 仅 IosCentralShell 扫描回调有 RSSI</td><td class="todo">未实现</td><td>新增 transport 读取方法 + 事件，页面可见时 5 s 轮询</td></tr>
<tr><td><b>电池电量</b></td><td>相机电量无任何读取（docs/todo.md 第 10 项亦记「无电池协议」）；PTP 通道已有 0x9209 属性解析（SonyCameraProperties.kt）可复用</td><td class="todo">未实现</td><td>仅 Wi-Fi/PTP 会话读 1 次并缓存；BLE 期间显示「上次获取」</td></tr>
<tr><td><b>GPS 传输/写入统计</b></td><td><code>LocationTransmissionManager.sendToDevice()</code>（bluetooth/location/…:177-188）仅 <code>writeCharacteristic</code> 入队成功后置全局布尔 <code>_isTransmitting</code>；写完成回调 <code>BleSessionCoordinator.onCharacteristicWrite</code> 里位置特征分支为空（BleSessionCoordinator.kt:116-118「nothing to do」）</td><td class="todo">无计数器</td><td>新增 per-device 计数器：已传输=入队成功；已写入=写回调 Success；另记失败/字节/最近写入时间</td></tr>
<tr><td><b>固件版本 / 序列号</b></td><td><code>PtpDeviceInfoParser.parse</code>（remote/wifi/PtpDeviceInfo.kt:20-53）只解析到 OperationsSupported，注释写明「后面的字符串可能含序列号」——<b>有意不解析</b>；<code>PtpDeviceInfo</code> 仅含 supportedOperations</td><td class="todo">丢弃</td><td>扩展解析 DeviceVersion、SerialNumber，仅内存、不入日志；设计加遮罩</td></tr>
<tr><td><b>卡容量</b></td><td>全工程无 GetStorageInfo（0x1005）；旧路径仅用 GET_STORAGE_IDS 0x1004；v4 catalog 路径不查存储</td><td class="todo">未实现</td><td>每卡新增 1 次请求（卡槽 1）</td></tr>
<tr><td><b>保护状态 · v4 catalog</b></td><td><code>SonyContentParser.parse</code>（SonyContentCatalog.kt:25-103）每条记录第 57 行 <code>take(8) // Rating, protection flag.</code> — 这 8 字节<b>读到了但被跳过</b>；<code>WifiCameraPhoto</code>（WifiRemoteState.kt）无 protected/rating 字段</td><td class="ok">协议已返回，代码丢弃</td><td>解析即得，<b>零额外流量</b>；字节布局（4+4？顺序/取值范围）<span class="todo">待实机核实</span></td></tr>
<tr><td><b>评分 · v4 catalog</b></td><td>同上，8 字节同时含评分与保护标志（注释原文）。按「内容记录」存放，JPG+RAW 同一拍摄共享（captureId = "content:$slot:$contentId"）</td><td class="ok">同上</td><td>评分/保护按「一次拍摄」展示，与网格已按 capture 合并一致</td></tr>
<tr><td><b>保护状态 · 旧路径</b></td><td>① GetObjectInfo 0x1008：<code>SonyImageInfo.parse</code>（SonyImageTransfer.kt:43-81）只读 format@4、size@8、parent@38，标准 ObjectInfo 的 ProtectionStatus（偏移 6，UINT16）<b>未读</b>；② GetObjectPropList 0x9805：请求全部属性，但 <code>SonyMtpObjectInfo</code> 只保留 0xDC01/02/04/07/08/0B，0xDC03(ProtectionStatus) <b>被丢弃</b></td><td class="ok">可取，未读</td><td>读取即得，无额外请求；取值 0=无、1=只读、0x8001=Sony 保护？<span class="todo">待实机核实</span></td></tr>
<tr><td><b>评分 · 旧路径</b></td><td>标准 ObjectInfo <b>不含</b>评分；PropList 可能含 MTP Rating（0xDC8A）或 Sony 私有属性，目前被丢弃</td><td class="todo">不确定</td><td>旧路径评分可能不可得 → 开关提示「当前读取方式不提供评分」，只显示锁</td></tr>
<tr><td><b>照片网格叠层</b></td><td>WifiPhotoBrowser.kt：格式角标左下、放大镜左上（40dp 触控）、选择框右下；<b>右上角空闲</b></td><td class="ok">有空位</td><td>锁放右上，评分叠在格式角标正上方</td></tr>
<tr><td><b>图标资源</b></td><td>已有 arrow_back/info/refresh/link_off/search/check/… ；<b>无</b> lock、star、battery、signal、schedule、visibility 等</td><td class="todo">缺图标</td><td>新增 Material Symbols 矢量（见第 13 页）</td></tr>
</table>
<div class="note" style="margin-top:8px"><b>保护/评分核实结论（一句话）</b>：v4 catalog 路径协议已带「评分+保护」8 字节但被 <code>take(8)</code> 丢弃，解析即得、零额外流量；旧 GetObjectInfo 路径有保护位（ProtectionStatus）但无评分，PropList 路径两者可能都在但被丢弃。布局与取值均需实机抓包确认前，设计按「可能没有」做了完整回退。</div></div>""")

# ============================================================ 3 data table
page("数据来源 · 刷新时机 · 不可用回退", "每个新增数据项的成本、何时读、没有时显示什么",
     f"""
<table>
<tr><th style="width:112px">数据项</th><th style="width:118px">成本类型</th><th style="width:250px">来源</th><th style="width:250px">刷新时机</th><th style="width:230px">没有/过期时显示</th><th>展示位置</th></tr>
<tr><td><b>时间已校正</b></td><td>{tag('N')}</td><td>握手 SyncingTime 的 CC0E 回执（已有，仅需保存）</td><td>每次握手；时区/DST 变化 30 s 监测触发重校；断开复位</td><td>未开始「未校时」/校时中转圈/被拒绝「相机拒绝校时」/未确认/写入失败/「该相机不支持」</td><td>设备页卡片 {n(1)}；Ⓘ</td></tr>
<tr><td><b>蓝牙信号 RSSI</b></td><td>{tag('R')}</td><td><code>readRemoteRssi()</code>（新增，Android）；iOS <code>readRSSI()</code></td><td>页面可见且已连接：进入时读 1 次，之后每 5 s；不可见/断开立即停</td><td>「—」；读取失败保留上次值 ≤15 s；阈值分档强/良好/一般/弱</td><td>设备页 {n(2)}；Ⓘ</td></tr>
<tr><td><b>电池电量</b></td><td>{tag('P')}</td><td>Wi-Fi/PTP 会话内读：标准 0x5001 / Sony 0xD218（<span class="todo">待实机核实</span>）</td><td>Wi-Fi 会话 Ready 后 1 次；点「刷新」再读；BLE 期间<b>不读</b>，用缓存</td><td>≤30 分钟正常；30 分~24 小时灰色+获取时间；&gt;24 小时/换机「—」；≤20% 红色</td><td>设备页 {n(3)}；Ⓘ；Wi-Fi 信息条</td></tr>
<tr><td><b>已传输次数</b></td><td>{tag('S')}</td><td><code>sendToDevice</code> 入队成功 +1（手机侧自身业务数据）</td><td>每次发送（5 s 周期 / 新定位 / 握手完成）；连接时清零</td><td>未连接：灰显「上次连接」末值；等待握手显示 0</td><td>设备页 {n(4)}{n(5)}；Ⓘ 明细</td></tr>
<tr><td><b>已写入次数</b></td><td>{tag('S')}</td><td>位置特征 GATT 写完成回调 Success +1（<code>BleSessionCoordinator.kt:116</code> 空分支）</td><td>每次写回调；失败/取消/超时另计「失败/取消」</td><td>同上；与已传输差值持续增大 → 「写入延迟」提示</td><td>设备页；Ⓘ</td></tr>
<tr><td><b>最近写入时间</b></td><td>{tag('S')}</td><td>最近一次 Success 回调的时间戳</td><td>每次写回调；界面相对时间每秒刷新（「3 秒前」）</td><td>无写入「—」；&gt;15 s 琥珀、&gt;60 s 红</td><td>设备页 {n(6)}；Ⓘ</td></tr>
<tr><td><b>累计写入字节</b></td><td>{tag('S')}</td><td>每次成功写 += 包长（config.dataSize）</td><td>同上</td><td>—</td><td>Ⓘ 明细</td></tr>
<tr><td><b>固件版本</b></td><td>{tag('D')}</td><td>PTP GetDeviceInfo 0x1001 的 DeviceVersion（现解析后丢弃）</td><td>Wi-Fi 会话 OpenSession 后 1 次；会话内不变</td><td>「—」；Ⓘ 在 BLE 期间显示上次会话值（标注）</td><td>Wi-Fi 信息条；Ⓘ</td></tr>
<tr><td><b>序列号</b></td><td>{tag('D')}</td><td>同上 SerialNumber</td><td>同上；仅内存、会话结束清除、不入日志</td><td>「不可用」；默认遮罩「•••3456」，点眼睛展开 10 s</td><td>Wi-Fi 信息条；Ⓘ</td></tr>
<tr><td><b>卡容量/可用</b></td><td>{tag('P')}</td><td>PTP GetStorageInfo 0x1005（卡槽 1；仅浏览卡槽 1）</td><td>目录首屏返回后 1 次；点「刷新」重读</td><td>「—」；无卡「无卡」；可用未知不画进度条</td><td>Wi-Fi 信息条</td></tr>
<tr><td><b>保护（锁）</b></td><td>{tag('D')}</td><td>v4 catalog 记录 8 字节（SonyContentCatalog.kt:57）/ 旧路径 ProtectionStatus</td><td>随目录枚举/刷新；下载前 revalidate；不实时监听相机侧改动</td><td>字段缺失：开关点击提示「不提供保护/评分信息」，不画占位</td><td>缩略图右上角；开关控制</td></tr>
<tr><td><b>评分（星）</b></td><td>{tag('D')}</td><td>v4 catalog 同 8 字节；旧路径可能不可得</td><td>同上</td><td>0/未知不显示；旧路径无评分时仅显示锁</td><td>缩略图左下（格式角标上方）</td></tr>
</table>
<p class="small" style="margin-top:6px">成本图例：{tag('Z')}已有数据仅需落字段　{tag('S')}手机侧新增计数器（埋点）　{tag('R')}新增 RSSI 读取　{tag('P')}新增 PTP 请求（1 次/会话或 1 次/卡）　{tag('D')}协议已返回、代码丢弃 → 解析即得。</p>""")

# ============================================================ 4 device page
page("设备页（已连接）", "新增「相机状态」「GPS 位置传输」两张卡片，位于连接按钮与底部 Wi-Fi 入口之间",
     f"""
<div class="row">
 {phone('dev_ok', 1.0, '设备页 · 已连接 · 390×844')}
 <div class="col">
  <h2>新增元素说明</h2>
  <table>
  <tr><th style="width:28px"></th><th style="width:150px">元素</th><th>说明 / 来源 / 刷新</th></tr>
  <tr><td>{n(1)}</td><td><b>时间已校正</b><br>绿勾 + 时刻</td><td>来源：握手 CC0E 回执（相机确认 applied=1 才显示「时间已校正」）。副文案「相机已确认 · 本次连接」；右侧为校正完成时刻。刷新：每次握手、时区/DST 变化自动重校。回退：见第 5、11 页。</td></tr>
  <tr><td>{n(2)}</td><td><b>蓝牙信号</b><br>4 格 + dBm</td><td>来源：readRemoteRssi（新增）。分档强 ≥−60 / 良好 / 一般 / 弱 &lt;−85，颜色绿/绿/琥珀/红，文字同时给出档位和 dBm（不只靠颜色）。刷新：本页可见时每 5 s；离开页面即停。</td></tr>
  <tr><td>{n(3)}</td><td><b>电池电量</b><br>图标 + %</td><td>来源：Wi-Fi/PTP 会话内读取 1 次并缓存。副文案固定「HH:mm 获取 · 来自 Wi-Fi 会话」，让用户知道不是实时。≤20% 红色并改称「电池电量低」。</td></tr>
  <tr><td>{n(4)}</td><td><b>GPS 位置传输</b><br>状态标签</td><td>标签：传输中（最近写入 ≤15 s）/ 等待握手 / 已停止（未连接）/ 写入延迟（&gt;15 s 无确认）。</td></tr>
  <tr><td>{n(5)}</td><td><b>已传输 / 已写入</b></td><td>手机侧新计数器。已传输=手机发出（入队成功）；已写入=相机蓝牙确认收到（GATT 写完成）。两者差值反映丢包/拥塞。连接时清零，Ⓘ 内可重置。</td></tr>
  <tr><td>{n(6)}</td><td><b>最近写入</b></td><td>相对时间（「3 秒前」每秒走字）+ 绝对时刻（14:32:40）。用于确认数据真的在传：走字停了就是没写进去。</td></tr>
  </table>
  <h2>布局取舍</h2>
  <ul>
   <li>卡片放在连接按钮下，Wi-Fi 入口保持底部固定，不挤占主操作；整体仍可纵向滚动（小屏/大字体时）。</li>
   <li>两张卡合并成「相机状态」与「GPS」两组，而不是 6 个散行：先看信号/时间/电量，再看 GPS 是否在写。</li>
   <li>卡片为只读信息，不可点击；点 Ⓘ 看全部明细（第 6 页）。</li>
   <li>名称块、连接按钮、Wi-Fi 入口完全不变。</li>
  </ul>
  <h2>颜色与文字规则（一览）</h2>
  <table><tr><th style="width:90px">项</th><th>正常</th><th>注意（琥珀）</th><th>异常（红）</th><th>无数据（灰）</th></tr>
  <tr><td>时间</td><td>绿勾 已校正</td><td>校时中 / 未确认</td><td>拒绝 / 写入失败</td><td>未校时 / 不支持</td></tr>
  <tr><td>信号</td><td>强/良好 绿</td><td>一般</td><td>弱 &lt;−85</td><td>—</td></tr>
  <tr><td>电量</td><td>&gt;20%</td><td>—</td><td>≤20% 电池电量低</td><td>缓存过期 / 未获取</td></tr>
  <tr><td>GPS 写入</td><td>≤15 s</td><td>15~60 s 写入延迟</td><td>&gt;60 s</td><td>已停止（未连接）</td></tr></table>
  <div class="note" style="margin-top:8px">所有颜色都配文字语义（档位词/告警图标），色觉异常用户不丢信息；整体不引入新色系，沿用现有连接状态的绿/琥珀/灰。</div>
 </div>
</div>""")

# ============================================================ 5 device variants
page("设备页状态变体", "校时中 / 未连接 / 异常告警：每个状态都有明确的回退，不显示陈旧数据冒充实时",
     f"""
<div class="row">
 {phone('dev_conn', 0.78, '校时中（握手阶段）')}
 {phone('dev_disc', 0.78, '未连接')}
 {phone('dev_warn', 0.78, '异常告警')}
 <div class="col" style="flex:0 0 330px">
  <h2>各状态规则</h2>
  <h3>校时中</h3>
  <ul><li>时间行显示转圈「正在校时…」，最长 30 s，不阻塞 GPS（与现有握手一致）</li>
  <li>电量为缓存值 → 灰色并标「昨天 18:20 获取」</li>
  <li>GPS 计数 0，标签「等待握手」</li></ul>
  <h3>未连接</h3>
  <ul><li>时间「未校时 · 连接后自动校正」；信号「未连接 —」</li>
  <li>电量保留上次值（灰），标「上次获取」</li>
  <li>GPS 卡标题加「（上次连接）」，数字灰显，标签「已停止」</li></ul>
  <h3>异常告警</h3>
  <ul><li>相机拒绝校时：琥珀警告图标 + 原因码，右侧「未校正」；时区变化时自动重试</li>
  <li>信号弱：红色 1 格 +「靠近相机可改善」</li>
  <li>电量 ≤20%：红色并改称「电池电量低」</li>
  <li>写入延迟：&gt;15 s 无确认 → 琥珀条给出最高优先级原因</li></ul>
  <div class="note">告警只改颜色与副文案，不弹窗、不震动，不打断拍摄。</div>
 </div>
</div>""")

# ============================================================ 6 info dialog
page("Ⓘ 相机信息对话框", "在现有行（保持活跃/自动校时/自动区域/MTU）下方新增「实时状态」与「GPS 传输明细」两段",
     f"""
<div class="row">
 {phone('info_top', 0.78, '顶部（现有内容不变）')}
 {phone('info_scr', 0.78, '滚动后：新增两段')}
 {phone('info_disc', 0.78, '未连接')}
 <div class="col" style="flex:0 0 330px">
  <h2>新增段落</h2>
  <p>{n(1)}<b>实时状态</b>：时间校正、蓝牙信号、电池电量、固件版本、序列号。</p>
  <ul><li>固件/序列号只有进入过 Wi-Fi 会话才有；之前显示「—（进入 Wi-Fi 照片传输后显示）」；已有则标「上次 Wi-Fi 会话」</li>
  <li>序列号遮罩，Ⓘ 内不提供展开（需要完整值去 Wi-Fi 页）</li>
  <li>RSSI 在对话框可见期间同样每 5 s 刷新</li></ul>
  <p>{n(2)}<b>GPS 传输明细</b>：已传输、已写入（绿）、失败/取消、累计写入字节、最近写入。</p>
  <ul><li>未连接时标题加「（上次连接）」</li>
  <li>失败/取消=Failure/Timeout/Cancelled/NotInitiated</li></ul>
  <p>{n(3)}<b>重置计数</b>：文字按钮，清零已传输/已写入/失败/字节；不弹确认（可随时再累计）。</p>
  <h2>不变</h2>
  <ul><li>标题、名称、蓝牙地址、保持活跃开关、自动校时/区域行、MTU</li>
  <li>底部重命名/帮助/删除、确认按钮「OK」（沿用现有 zh 文案）</li>
  <li>对话框内容区仍限高 420dp 滚动</li></ul>
  <div class="note">红竖条仅为本方案标注「新增行」，实现时不显示。</div>
 </div>
</div>""")

# ============================================================ 7 wifi basic vs ext
page("Wi-Fi 照片页：基础态 vs 扩展态", "顶部新增信息条；右上角星标开关切换角标；开关默认关闭，基础态网格与现状一致",
     f"""
<div class="row">
 {phone('wifi_base', 0.93, '基础态（开关关）')}
 {phone('wifi_ext', 0.93, '扩展态（开关开）')}
 <div class="col">
  <h2>说明</h2>
  <table><tr><th style="width:28px"></th><th>元素</th></tr>
  <tr><td>{n(1)}</td><td><b>右上角开关</b>：顶栏动作区新增星标图标，位于刷新/断开连接左侧。开启=圆形底色 + 主色图标（Material 图标开关范式），关闭=普通灰色图标。无障碍 role=Switch。偏好记忆，下次进入沿用，默认关。</td></tr>
  <tr><td>{n(2)}</td><td><b>顶部信息条</b>：固件版本 / 序列号（遮罩）/ 存储卡 1 容量 + 已用进度条 + 电量。随列表滚动，不吸顶，避免压缩网格；点击序列号旁眼睛可展开。五种状态见第 8 页。</td></tr>
  <tr><td>{n(3)}</td><td><b>保护锁</b>：缩略图右上角，24dp 方块（与左上放大镜同尺寸同样式），仅已保护时出现。</td></tr>
  <tr><td>{n(4)}</td><td><b>评分胶囊</b>：左下格式角标上方，星形按星级着色 + 数字。0/未知不显示。</td></tr>
  <tr><td>{n(5)}</td><td><b>反馈条</b>：首次打开开关时底部提示「已显示保护与评分」并说明图例；之后不再重复。</td></tr>
  </table>
  <h2>关键取舍</h2>
  <ul>
   <li><b>开关放顶栏而非信息条内</b>：用户要求「右上角」；顶栏图标是全局视图选项，不随信息条滚走。</li>
   <li><b>默认关</b>：绝大多数时候用户只想挑图下载，避免网格变花；开一次就记住。</li>
   <li><b>不增加点击目标</b>：锁/星为只读叠层，不影响点击预览、长按多选、拖选。</li>
   <li><b>不变</b>：日期标题、网格列数、格式角标、放大镜、选择框、翻页条。</li>
   <li>选择模式下顶栏改为全选/下载，开关暂隐藏（沿用现有动作替换行为），角标显隐保持。</li>
  </ul>
 </div>
</div>""")

# ============================================================ 8 strip + toggle
page("顶部信息条状态 · 开关交互", "信息条每项独立加载、独立失败；开关在无数据时给出明确提示而不是静默无效",
     f"""
<div class="row">
 <div>{img('strips', 740)}</div>
 <div class="col">
  <h2>开关三态</h2>
  <table><tr><th style="width:312px">状态</th><th>示意与行为</th></tr>
  <tr><td>{img('tog_off', 300)}</td><td><b>关闭（默认）</b>：灰色星标图标。长按出现提示气泡「显示保护与评分」。点按 → 开启。</td></tr>
  <tr><td>{img('tog_on', 300)}</td><td><b>开启</b>：圆形底色 + 主色图标；首次开启底部反馈条「已显示保护与评分」，带「撤销」。偏好记忆。</td></tr>
  <tr><td>{img('tog_none', 300)}</td><td><b>不可用</b>：当前相机/读取方式没有保护与评分字段 → 点按显示反馈条「此相机或当前读取方式不提供保护/评分信息」，开关保持关闭；不画任何占位。</td></tr>
  </table>
  <h2>信息条规则</h2>
  <ul>
   <li><b>出现时机</b>：Wi-Fi 会话 Ready 后立即出现骨架；固件/序列号（GetDeviceInfo）与目录同批就绪；容量、电量在首屏目录返回后补读。</li>
   <li><b>序列号</b>：默认「•••3456」；点眼睛展开，10 秒或离开页面自动重遮；展开时不进日志/截图提示。</li>
   <li><b>容量</b>：显示卡槽 1「128 GB」+ 进度条「已用 54 GB · 可用 74 GB」；仅浏览卡槽 1，与现有行为一致。</li>
   <li><b>失败</b>：任何一项失败只显示「—」/「不可用」，无错误弹窗；「刷新」顶栏按钮可重试。</li>
   <li><b>无卡</b>：容量位置显示「无卡」，并与现有空列表提示并存。</li>
   <li><b>刷新</b>：点「刷新」不重新出现整条骨架，旧值保留，读到新值后原位替换。</li>
  </ul>
 </div>
</div>""")

# ============================================================ 9 tile spec
page("缩略图角标规范", "锁放右上、评分叠在格式角标上方；星级色阶 + 数字双通道；不增加点击目标",
     f"""<div style="margin:0 -10px">{img('tile', 1400)}</div>""")

# ============================================================ 10 dataflow
page("数据流图", "触发 → 获取动作 → 状态落点 → 界面位置。颜色表示成本类型（图例在底部）",
     f"""<div>{img('flow', 1328)}</div>""")

# ============================================================ 11 state machines
page("状态机与刷新节奏", "时间校正状态、GPS 新鲜度阈值、RSSI 分档与轮询、电池缓存新鲜度",
     f"""<div>{img('states', 1328)}</div>""")

# ============================================================ 12 wifi flow
page("Wi-Fi 会话交互流程", "信息类请求都在首屏之后、互相独立；任何一项失败都不影响浏览与下载",
     f"""<div>{img('wflow', 1328)}</div>""")

# ============================================================ 13 copy / icons
page("文案 · 图标 · 状态色", "仅 en（values/）+ zh-rCN（values-zh-rCN/）两处；图标沿用 Material 风格",
     f"""
<div class="row">
 <div class="col" style="flex:1.25">
  <h2>文案（中 / 英）</h2>
  <table><tr><th>位置</th><th>中文</th><th>English</th></tr>
  <tr><td>时间 · 已校正</td><td>时间已校正 / 相机已确认 · 本次连接</td><td>Time synced / Confirmed by camera</td></tr>
  <tr><td>时间 · 其它</td><td>正在校时… / 未校时 / 相机拒绝校时 / 校时未确认 / 校时失败 / 该相机不支持校时</td><td>Syncing time… / Not synced / Camera rejected / Unconfirmed / Sync failed / Not supported</td></tr>
  <tr><td>信号</td><td>蓝牙信号：强/良好/一般/弱 · −58 dBm</td><td>Bluetooth signal: Strong/Good/Fair/Weak</td></tr>
  <tr><td>电量</td><td>电池电量 / 电池电量低 / 14:20 获取 · 来自 Wi-Fi 会话 / 上次获取：昨天 18:20</td><td>Battery / Battery low / Read at 14:20 via Wi-Fi / Last read: yesterday 18:20</td></tr>
  <tr><td>GPS 卡</td><td>GPS 位置传输（上次连接）/ 已传输（次）/ 已写入（次）/ 最近写入 / 3 秒前</td><td>GPS transmission (last connection) / Sent / Written / Last write / 3 s ago</td></tr>
  <tr><td>GPS 标签</td><td>传输中 / 等待握手 / 已停止 / 写入延迟</td><td>Transmitting / Waiting for handshake / Stopped / Write delayed</td></tr>
  <tr><td>GPS 说明</td><td>已传输＝手机发出；已写入＝相机蓝牙确认收到</td><td>Sent = from phone; Written = confirmed by camera</td></tr>
  <tr><td>GPS 提示</td><td>已 38 秒未收到相机确认；手机暂无定位</td><td>No camera confirmation for 38 s; phone has no location fix</td></tr>
  <tr><td>Ⓘ 明细</td><td>实时状态 / GPS 传输明细 / 失败 / 取消 / 累计写入 / 重置计数</td><td>Live status / GPS transmission details / Failed / cancelled / Total written / Reset counters</td></tr>
  <tr><td>信息条</td><td>固件版本 / 序列号 / 存储卡 1 / 已用 54 GB · 可用 74 GB / 无卡 / 不可用 / 正在读取相机信息…</td><td>Firmware / Serial number / Card 1 / 54 GB used · 74 GB free / No card / Unavailable / Reading camera info…</td></tr>
  <tr><td>序列号</td><td>显示/隐藏序列号、已复制</td><td>Show / hide serial number, Copied</td></tr>
  <tr><td>开关</td><td>显示保护与评分 / 已显示保护与评分 / 此相机或当前读取方式不提供保护/评分信息</td><td>Show protection &amp; rating / Protection &amp; rating shown / This camera or read mode does not provide protection/rating</td></tr>
  <tr><td>照片读屏</td><td>已保护 / 评分 4 星</td><td>Protected / Rated 4 stars</td></tr>
  </table>
 </div>
 <div class="col" style="flex:0.75">
  <h2>图标清单</h2>
  <table><tr><th>用途</th><th>Material 图标</th><th>现状</th></tr>
  <tr><td>时间已校正</td><td>check_circle</td><td class="todo">新增</td></tr>
  <tr><td>未校时/校时中</td><td>schedule / 转圈</td><td class="todo">新增</td></tr>
  <tr><td>告警</td><td>warning</td><td class="ok">已有</td></tr>
  <tr><td>信号</td><td>signal_cellular_alt（4 格）</td><td class="todo">新增</td></tr>
  <tr><td>电量</td><td>battery_full / battery_alert</td><td class="todo">新增</td></tr>
  <tr><td>GPS</td><td>my_location</td><td class="todo">新增</td></tr>
  <tr><td>保护</td><td>lock</td><td class="todo">新增</td></tr>
  <tr><td>评分开关/星</td><td>stars / star</td><td class="todo">新增</td></tr>
  <tr><td>序列号显隐</td><td>visibility / visibility_off</td><td class="todo">新增</td></tr>
  <tr><td>刷新/断开/Ⓘ</td><td>refresh / link_off / info</td><td class="ok">已有</td></tr>
  </table>
  <h2>状态色（与现有一致）</h2>
  <p><span style="color:#2E7D32;font-weight:700">■ 绿 #2E7D32</span> 正常/已校正 · <span style="color:#8A5A00;font-weight:700">■ 琥珀 #F9A825</span> 注意/进行中 · <span style="color:#B3261E;font-weight:700">■ 红 #B3261E</span> 异常 · <span style="color:#79747E;font-weight:700">■ 灰</span> 未连接/过期。文字永远同时给出语义，不只靠颜色。</p>
  <h2>无障碍</h2>
  <ul><li>状态行整体朗读「蓝牙信号 良好 负 58 分贝毫瓦」；动态数字 liveRegion 不抢读（polite 且仅状态档位变化时）。</li>
  <li>字体放大到 200% 时卡片纵向增长；设备页整体可滚动，Wi-Fi 入口保持底部。</li>
  <li>对比度：深色胶囊底 + 白字 ≥ 4.5:1；浅色卡上状态文字沿用 on-surface。</li></ul>
 </div>
</div>""")

# ============================================================ 14 tradeoffs
page("关键取舍 · 待确认 · 待实机核实 · 开发拆分", "请在「待确认」中给出意见；实机核实项不阻塞设计，但影响实现细节",
     f"""
<div class="row">
 <div class="col">
  <h2>关键取舍（已做）</h2>
  <ul>
   <li><b>已传输 vs 已写入</b>拆成两个计数：入队成功（手机发出）与 GATT 写完成（相机确认），差值直接暴露丢包/拥塞；满足「确认数据真的在传」。</li>
   <li><b>电量不在 BLE 期间读</b>：协议上仅 Wi-Fi/PTP 会话可靠；宁可显示「HH:mm 获取」的缓存也不假装实时。</li>
   <li><b>时间已校正严格以相机回执为准</b>：写成功但 30 s 无回执 = 「未确认」，不乐观显示。</li>
   <li><b>RSSI 只在页面可见时轮询</b>（5 s）：省电，且与 GPS 周期对齐；离开即停。</li>
   <li><b>序列号默认遮罩</b>：现代码有意不解析序列号（隐私），设计在不放弃该意图的前提下展示。</li>
   <li><b>开关放顶栏、默认关、记忆偏好</b>；无数据时点击给出原因，不静默。</li>
   <li><b>锁与评分只读</b>，不新增点击目标，不改变任何手势；应用不修改相机文件。</li>
   <li><b>每项独立失败</b>：任何信息读取失败都不阻塞浏览/下载/GPS。</li>
   <li><b>计数器不落盘</b>：仅会话内；断开后保留末值（灰）直到下次连接。</li>
  </ul>
  <h2>待用户确认</h2>
  <table><tr><th style="width:28px">Q</th><th>问题</th><th style="width:230px">建议（默认）</th></tr>
  <tr><td>1</td><td>状态卡片常驻设备页，还是折叠到 Ⓘ？</td><td>常驻（用户要「相机页」可见）</td></tr>
  <tr><td>2</td><td>评分开关默认关并记忆，还是默认开？</td><td>默认关 + 记忆</td></tr>
  <tr><td>3</td><td>序列号是否遮罩？</td><td>遮罩，末 4 位 + 眼睛展开</td></tr>
  <tr><td>4</td><td>评分 5 色星 + 数字，或单色星 + 数字？</td><td>5 色 + 数字（色辅助）</td></tr>
  <tr><td>5</td><td>GPS 新鲜度阈值 15 s / 60 s 是否合适？</td><td>15 s 琥珀 / 60 s 红</td></tr>
  <tr><td>6</td><td>iOS 是否同步（sharednew 代码共享，RSSI/BLE 需各端实现）？</td><td>先 Android，iOS 复用 UI</td></tr>
  <tr><td>7</td><td>保护锁是否点击可看「相机侧已保护，应用不会删除」提示？</td><td>不做，保持只读</td></tr>
  </table>
 </div>
 <div class="col">
  <h2>待实机核实（<span class="todo">不确定项</span>）</h2>
  <ol style="margin:4px 0;padding-left:22px">
   <li>电池属性码：标准 0x5001 BatteryLevel vs Sony 0xD218，取值范围/单位（百分比还是档位）</li>
   <li>v4 catalog 第 57 行 8 字节的布局：评分与保护标志的顺序、宽度、取值（评分 0–5？保护 0/1？）</li>
   <li>旧路径 PropList：0xDC03 保护取值；是否含评分属性（MTP 0xDC8A 或 Sony 私有）</li>
   <li>GetStorageInfo 对 α 机的返回（MaxCapacity/FreeSpace 是否准确；卡槽 1 的 StorageID）</li>
   <li>相机 BLE 是否暴露标准 Battery Service 0x180F（若有，可省掉「获取时间」标注）</li>
   <li>readRemoteRssi 对各 α 机型的可用性与数值范围；是否占用 GATT 队列、对位置写入有无影响</li>
   <li>DeviceVersion/SerialNumber 字符串格式（版本是否「2.00」样式、序列号长度）</li>
   <li>相机侧改评分/保护后，目录需多久/何种操作才能反映（决定「刷新」文案）</li>
  </ol>
  <h2>开发拆分建议（用户确认后派发）</h2>
  <table><tr><th style="width:84px">里程碑</th><th>范围</th><th style="width:100px">风险</th></tr>
  <tr><td>M1 BLE 侧</td><td>CameraSession.timeSync 状态字段；GPS 计数器（sendToDevice 入队 + 写回调）；RSSI 读取与轮询；设备页卡片 + Ⓘ 新段 + 文案/图标</td><td>低–中（改 transport 接口）</td></tr>
  <tr><td>M2 PTP 侧</td><td>解析 DeviceVersion/SerialNumber；GetStorageInfo；电量读取+缓存；Wi-Fi 信息条</td><td>中（需实机核实 3 项）</td></tr>
  <tr><td>M3 保护/评分</td><td>catalog 8 字节解析（先实机抓包）；旧路径 ProtectionStatus；WifiCameraPhoto 新字段；缩略图角标；顶栏开关与偏好</td><td>中–高（布局待核实）</td></tr>
  </table>
  <div class="note" style="margin-top:8px">建议顺序：M1 → M3（先做核实）→ M2；M1 不依赖任何协议核实，可立即开工。每个里程碑需补单测（计数器、状态机、解析）与 en/zh-rCN 文案；不新增其它语言目录。</div>
 </div>
</div>""")

html = f'<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>alpha-gps 状态增强 UI 设计方案</title><style>{CSS}</style></head><body>{"".join(pages)}</body></html>'
with open(os.path.join(HERE, "index.html"), "w", encoding="utf-8") as f:
    f.write(html)

out = os.path.join(os.path.dirname(HERE), "ui-status-enhancement.pdf")
cmd = ["google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox", "--no-pdf-header-footer",
       "--allow-file-access-from-files", f"--print-to-pdf={out}", "file://" + os.path.join(HERE, "index.html")]
r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
print(r.returncode, r.stderr[-300:])
print("pages:", len(pages), "pdf:", out, os.path.getsize(out) if os.path.exists(out) else None)
