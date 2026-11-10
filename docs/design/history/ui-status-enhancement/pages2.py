# -*- coding: utf-8 -*-
"""v2: build mockups-v2/*.svg, index-v2.html and design/ui-status-enhancement-v2.pdf.
Run: python3 pages2.py   (needs python3 + google-chrome)."""
import os, subprocess
import lib2
from lib2 import set_theme, phone_frame, C, LIGHT, DARK, RATING, svg_wrap
import screens2 as S
import extras2 as E

HERE = os.path.dirname(os.path.abspath(__file__))
MOCK = os.path.join(HERE, "mockups-v2")
os.makedirs(MOCK, exist_ok=True)
F = {}
TOTAL = 17


def save(key, name, svg):
    with open(os.path.join(MOCK, name + ".svg"), "w", encoding="utf-8") as f:
        f.write(svg)
    F[key] = f"mockups-v2/{name}.svg"


def ph(body):
    return phone_frame(body)


# ------------------------------------------------------------------ mockups (light)
set_theme("light")
save("dev_old", "01a_device_current", ph(S.legacy_device_screen()))
save("dev_ok", "01b_device_connected", ph(S.device_screen("ok", callouts=True)))
save("dev_off", "01c_device_disconnected", ph(S.device_screen("off", callouts=True)))
save("dev_warn", "01d_device_warning", ph(S.device_screen("warn")))
for k in ("prepare", "approve", "join", "catalog", "ready"):
    save("ov_" + k, f"02_overlay_{k}", ph(S.device_screen("ok", overlay=S.ov_card(k))))
save("ov_fail", "02_overlay_fail", ph(S.device_screen("ok", overlay=S.ov_card("fail"))))
save("ov_ble", "02_overlay_ble", ph(S.device_screen("off", overlay=S.ov_card("ble"))))
save("ov_ble_fail", "02_overlay_ble_fail", ph(S.device_screen("off", overlay=S.ov_card("ble_fail"))))
save("ov_closing", "02_overlay_closing", ph(S.device_screen("ok", overlay=S.ov_card("closing"))))
save("info_old", "03a_info_current", ph(S.info_legacy()))
save("info_new", "03b_info_v2", ph(S.info_dialog(True, True)))
save("rename", "03c_rename", ph(S.rename_dialog()))
save("remove", "03d_remove_confirm", ph(S.remove_dialog()))
save("nav", "04_navigation", E.nav_diagram())
save("dates", "05a_date_list", ph(S.date_list_screen(callouts=True)))
save("grid_s", "05b_grid_small", ph(S.grid_screen("S", callouts=True)))
save("grid_sel", "05c_grid_selecting", ph(S.grid_screen("S", selecting=3)))
save("grid_s2", "06a_grid_S", ph(S.grid_screen("S")))
save("grid_m", "06b_grid_M", ph(S.grid_screen("M", size=True)))
save("grid_l", "06c_grid_L", ph(S.grid_screen("L", size=True, ext=True)))
save("grid_panel", "06d_grid_panel", ph(S.grid_screen("M", size=True, panel=True)))
save("anatomy", "06e_tile_anatomy", E.tile_anatomy())
save("paging", "07_paging", E.paging_diagram())
save("v_norm", "08a_viewer_normal", ph(S.viewer_screen("normal", callouts=True)))
save("v_dl", "08b_viewer_downloading", ph(S.viewer_screen("downloading", callouts=True)))
save("v_saved", "08c_viewer_saved", ph(S.viewer_screen("saved")))
save("v_fail", "08d_viewer_failed", ph(S.viewer_screen("failed")))
save("swipe", "08e_swipe", E.swipe_diagram())
# ------------------------------------------------------------------ mockups (dark)
set_theme("dark")
save("d_dev", "09a_dark_device_warn", ph(S.device_screen("warn")))
save("d_grid", "09b_dark_grid_M", ph(S.grid_screen("M", size=True, ext=True)))
save("d_ov", "09c_dark_overlay", ph(S.device_screen("ok", overlay=S.ov_card("join"))))
save("d_info", "09d_dark_info", ph(S.info_dialog(True)))
set_theme("light")


def phone(key, scale, cap):
    return (f'<figure class="ph"><img src="{F[key]}" width="{int(390 * scale)}" height="{int(844 * scale)}">'
            f'<figcaption>{cap}</figcaption></figure>')


def wide(key, w, h, scale):
    return f'<img src="{F[key]}" width="{int(w * scale)}" height="{int(h * scale)}">'


def n(i):
    return f'<span class="n">{i}</span>'


def lum(hx):
    r, g, b = [int(hx[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def cr(a_, b_):
    la, lb = sorted([lum(a_), lum(b_)], reverse=True)
    return (la + 0.05) / (lb + 0.05)


CSS = """
@page { size: 1400px 1000px; margin: 0 }
* { box-sizing: border-box }
body { margin:0; font-family:'Source Han Sans CN','Noto Sans CJK SC',sans-serif; color:#191C20; font-size:14px; line-height:1.55 }
.page { width:1400px; height:1000px; padding:26px 36px 18px; page-break-after:always; position:relative; overflow:hidden; background:#fff }
.page:last-child { page-break-after:auto }
.hd { display:flex; align-items:baseline; border-bottom:2px solid #0B57D0; padding-bottom:8px; margin-bottom:12px }
.hd h1 { font-size:23px; margin:0; color:#0B57D0; font-weight:700 }
.hd .sub { margin-left:16px; color:#545F71; font-size:14px }
.hd .no { margin-left:auto; color:#72777F; font-size:12.5px }
.row { display:flex; gap:22px; align-items:flex-start }
.col { flex:1; min-width:0 }
table { border-collapse:collapse; width:100%; font-size:12.3px }
th code { background:#ffffff33; color:#fff } th { background:#0B57D0; color:#fff; text-align:left; padding:5px 8px; font-weight:600 }
td { border-bottom:1px solid #DDE3EA; padding:5px 8px; vertical-align:top }
tr:nth-child(even) td { background:#F5F8FC }
h2 { font-size:16.5px; color:#0B57D0; margin:10px 0 5px }
h3 { font-size:14px; margin:8px 0 3px }
.n { display:inline-block; width:18px; height:18px; border-radius:9px; background:#E5322D; color:#fff; font-size:11px; font-weight:700; text-align:center; line-height:18px; margin-right:5px }
.tag { display:inline-block; font-size:11px; padding:0 7px; border-radius:9px; border:1px solid; white-space:nowrap }
.tag.g { background:#C8F0D2; color:#1B6E3A; border-color:#1B6E3A }
.tag.r { background:#FFDAD6; color:#BA1A1A; border-color:#BA1A1A }
.tag.b { background:#D3E3FD; color:#0B57D0; border-color:#0B57D0 }
.tag.y { background:#FFE9B3; color:#7A5200; border-color:#7A5200 }
.ph { margin:0; text-align:center }
.ph figcaption { font-size:12px; color:#42474E; margin-top:3px; line-height:1.35 }
.phs { display:flex; gap:12px; align-items:flex-start }
.note { background:#FFF4D1; border:1px solid #C9A227; border-radius:8px; padding:7px 11px; font-size:12.3px }
.box { background:#EDF1F7; border-radius:10px; padding:9px 13px }
.ok { color:#1B6E3A; font-weight:600 } .todo { color:#BA1A1A; font-weight:600 }
ul { margin:3px 0; padding-left:19px } li { margin:2px 0 }
code { background:#E7EBF2; padding:0 4px; border-radius:3px; font-size:11.5px; font-family:'DejaVu Sans Mono',monospace }
.dense table { font-size:11.4px } .dense td { padding:3px 7px } .small { font-size:12px; color:#42474E }
img { display:block }
.sw { display:inline-block; width:15px; height:15px; border-radius:3px; border:1px solid #0003; vertical-align:-3px; margin-right:4px }
"""

pages = []


def page(title, sub, body):
    i = len(pages) + 1
    pages.append(f'<section class="page"><div class="hd"><h1>{title}</h1><span class="sub">{sub}</span>'
                 f'<span class="no">alpha-gps · 界面改版 UI 设计方案 v2 · {i} / {TOTAL}</span></div>{body}</section>')


# ============================================================ 1 cover
page("相机 / 照片界面改版 · UI/UX 设计方案 v2", "仅设计交付 · 不含任何应用代码改动 · v1 保留为 design/ui-status-enhancement.pdf", f"""
<div class="row">
 <div class="col" style="flex:1.25">
  <h2 style="margin-top:0">用户意见 → 落实位置</h2>
  <table class="dense">
  <tr><th style="width:56px">意见</th><th>要点</th><th style="width:74px">页</th></tr>
  <tr><td>A1</td><td>Ⓘ 说明文字全删；「已启用」改绿色对号，一切多余删除</td><td>7</td></tr>
  <tr><td>A2</td><td>重命名 → 名称右侧笔图标；移除不再放左下角 → 底部「危险区」整宽按钮 + 二次确认</td><td>7</td></tr>
  <tr><td>A3</td><td>删除「帮助」按钮（失败叠层内保留一个上下文「帮助」，见 Q11）</td><td>7 · 6</td></tr>
  <tr><td>B1</td><td>启动/连接过程改为<b>叠在原界面上的动画叠层</b>，文案精简，含取消</td><td>5 · 6</td></tr>
  <tr><td>B2</td><td>图标按钮 / 文字按钮规则表 + 全界面检查</td><td>3</td></tr>
  <tr><td>B3</td><td>设备页重排：「连接」进状态卡，Wi-Fi 入口上移，底部不再钉按钮</td><td>4</td></tr>
  <tr><td>B4</td><td>断开语义：仅「设备页左上返回」= 真断开；不设计断开按钮；子页不放断开</td><td>8</td></tr>
  <tr><td>C1</td><td>日期网格顶栏「视图选项」：小/中/大 + 格式/大小/保护评分开关</td><td>9 · 10</td></tr>
  <tr><td>C2</td><td>核实分页现状 → 取消分页的结论与代价</td><td>2 · 11</td></tr>
  <tr><td>D1–D4</td><td>预览页：名称+大小同行、下方时间、底部只有「下载」、居中叠层进度、已保存置灰</td><td>12</td></tr>
  <tr><td>D5</td><td>核验左右滑动：<span class="ok">已确认支持</span>（HorizontalPager），保留并明确</td><td>2 · 12</td></tr>
  <tr><td>E</td><td>蓝色主色、仅亮/暗、不用动态色：完整色板 + 暗色对照 + Theme.kt 清理清单</td><td>13 · 14</td></tr>
  <tr><td>保留项</td><td>v1 的校时/电量/信号/GPS 统计、Wi-Fi 信息条、保护/评分 → 重排承载位置</td><td>15</td></tr>
  </table>
  <div class="note" style="margin-top:8px"><b>阅读提示</b>：红色圆点编号 {n(1)} = 该屏的说明项；全部 mockup 为 390×844 dp 逻辑屏；
  亮色为主，第 14 页为暗色对照。变更清单第 16 页，待确认问题与开发拆分第 17 页。</div>
 </div>
 <div class="col" style="flex:1">
  <h2 style="margin-top:0">本版设计原则</h2>
  <ul>
   <li><b>一个界面，一份状态</b>：连接/下载进度直接叠在当前界面上，用户始终知道自己在哪、能怎么取消，不再跳到整页空白。</li>
   <li><b>外面只留基本信息</b>：设备页只放连接状态、校时/信号/电量、GPS 三数；其余（固件、序列号、MTU…）进 Ⓘ，且 Ⓘ 里没有说明文字。</li>
   <li><b>图标做“动作”，文字做“决定”</b>：顶栏/行内的高频小动作用图标；页面主操作、危险操作、对话框决定用文字按钮（第 3 页规则表）。</li>
   <li><b>断开只有一个入口</b>：设备页左上返回；其余页面不出现“断开”图标或按钮。</li>
   <li><b>连续优于分页</b>：日期内一个连续网格，没有页码栏。</li>
   <li><b>只一套蓝色、亮/暗两态</b>：状态色（绿/琥珀/红）固定语义，与主色分离。</li>
   <li><b>状态用按钮自身表达</b>：下载按钮显示 下载 / 已保存 / 无 JPEG / 文件过大，不再另写一行说明。</li>
   <li>仅 <code>values/</code> + <code>values-zh-rCN/</code> 两种文案；全部沿用 Material 3 与现有结构。</li>
  </ul>
  <h2>目录</h2>
  <table class="dense">
  <tr><td style="width:44px">2</td><td>现状核实（代码证据）</td><td style="width:44px">10</td><td>小/中/大 · 视图面板 · 角标</td></tr>
  <tr><td>3</td><td>按钮规则表 + 全界面检查</td><td>11</td><td>取消分页：影响与代价</td></tr>
  <tr><td>4</td><td>设备页新布局</td><td>12</td><td>预览页 + 滑动手势</td></tr>
  <tr><td>5–6</td><td>连接叠层分帧 / 失败 / 规格</td><td>13</td><td>蓝色亮/暗色板</td></tr>
  <tr><td>7</td><td>Ⓘ 极简版</td><td>14</td><td>暗色对照</td></tr>
  <tr><td>8</td><td>导航与断开语义</td><td>15</td><td>状态数据重排（v1 保留项）</td></tr>
  <tr><td>9</td><td>日期列表 · 网格 · 顶栏取舍</td><td>16–17</td><td>变更清单 / 待确认 / 拆分</td></tr>
  </table>
 </div>
</div>""")

# ============================================================ 2 verification
page("现状核实：代码里到底是什么样", "每条都读过源码；路径相对仓库根", f"""
<table class="dense">
<tr><th style="width:120px">主题</th><th style="width:470px">现状（文件:行）</th><th style="width:330px">结论</th><th>对 v2 的影响</th></tr>
<tr><td><b>分页</b></td><td><code>SonyImageTransfer.kt:456</code> <code>PAGE_SIZE = 60</code>；日期内由 <code>CatalogSnapshot.page(dateKey, offset)</code>（<code>WifiCatalogSnapshot.kt:99</code>）→ <code>cameraPhotoPage</code>（<code>WifiCameraCapture.kt:26-28</code>）切片；网格底部 <code>WifiPhotoPagerBar</code>（<code>WifiPhotoBrowser.kt:423</code>、<code>WifiPhotoPager.kt:48</code>：首页/上页/第 n/总页/下页 + 跳页对话框）；227 张 → <code>photoPageCount</code> = 4 页（<code>WifiPhotoPaging.kt:7</code>）</td>
 <td><span class="todo">仍在分页</span>，与截图“第 1/4 页”一致。<b>但目录整日已常驻内存</b>（<code>CatalogSnapshot.byDate</code>，“grouping costs no further camera traffic”），分页只是 UI 切片</td>
 <td>取消分页可行且零相机流量增量；连带 PAGE_SIZE、跨页选择、拖选、预览边界页、单测，代价见第 11 页</td></tr>
<tr><td><b>预览滑动</b></td><td><code>WifiPhotoViewer.kt:203-206</code> <code>HorizontalPager(userScrollEnabled = !changingPage)</code>；<code>:111-112</code> <code>changingPage = browser.loading &amp;&amp; 视图索引不在当前页窗口</code>；<code>:116-136</code> 滑到哪张就预取该张+邻张缩略图并 <code>viewPhotoAt</code>；<code>:261-291</code> 底部另有 ‹ › 按钮 <code>animateScrollToPage</code></td>
 <td><span class="ok">已确认支持左右滑动</span>（与用户核实一致），仅翻页加载中禁用；页首/页尾另有 <code>boundary:$page</code> 占位页用于跨页加载</td>
 <td>保留滑动；去掉 ‹ ›；取消分页后 <code>changingPage</code> 与边界占位页消失</td></tr>
<tr><td><b>动态色 / 主题</b></td><td><code>sharednew/.../ui/theme/Theme.kt:36</code> <code>CameraGpsTheme(darkTheme, dynamicColor: Boolean = true, …)</code>，<code>:43-46</code> 动态色分支已注释，参数<b>未使用</b>；亮色方案仅覆盖 secondary/tertiary，primary 仍是 M3 默认紫 <code>6750A4</code>；暗色 <code>primary = Purple40 = Color(0xADD8E6FF)</code>（ARGB 写法导致 alpha=0xAD，疑似笔误）；<code>Color.kt</code> 为 Purple/PurpleGrey/Pink 常量；iOS 调用 <code>StoreScreenshotViewController.kt:28</code></td>
 <td>未启用动态色（与用户核实一致），但<b>配色是紫色基调</b>，且遗留 <code>dynamicColor</code> 参数与注释分支需清理；另有硬编码颜色 <code>DeviceDetailScreen.kt:245-246</code>（0xFF2E7D32/0xFFF9A825）、<code>TransmissionDot.kt:36,62</code>（Color.Red/Green）</td>
 <td>第 13 页：蓝色亮/暗全量色板 + 状态色扩展；清理清单见第 17 页 M0</td></tr>
<tr><td><b>Ⓘ 对话框</b></td><td><code>DeviceInfoDialogs.kt</code>：标题「相机信息」+名称+蓝牙地址；保持活跃（Switch + 三行说明 <code>device_info_keep_active_hint</code>）；自动校时/自动区域「已启用（应用会保持开启）」；蓝牙 MTU；底部一排 TextButton 重命名 / 帮助 / 移除（红，<code>onDelete != null</code> 才出现，进 ConfirmDelete）；确认键 OK</td>
 <td>与截图一致：说明文字多、按钮挤在底部、危险操作与普通操作同排</td><td>第 7 页：说明全删、笔图标、✓ 图标、危险区</td></tr>
<tr><td><b>设备页</b></td><td><code>DeviceDetailScreen.kt</code>（sharednew）：<code>DeviceNameBlock</code>（「相机名称」+名称+状态点）→ 内容区 <code>OutlinedButton</code> 连接/断开（<code>deviceConnectionButton</code>）→ 底部整宽 88dp 按钮「Wi-Fi 照片传输」+ 说明 + chevron；Android 宿主顶栏：返回 + 标题 + Ⓘ（<code>app/.../DeviceDetailScreen.kt</code>），返回键仅 <code>onClose</code>，<b>不断开</b></td>
 <td>连接按钮与状态分离、Wi-Fi 入口钉在最底部；设备页返回键目前不触发断开（断开只靠按钮，<code>DeviceDetailViewModel.disconnect</code> 还会持久化 disabled、停 presence 观察）</td><td>第 4、8 页</td></tr>
<tr><td><b>连接过程页</b></td><td><code>WifiRemoteScreen.kt</code> 照片库未打开时的整页 <code>Scaffold</code>：标题 + <code>TextButton「返回」</code> 作 navigationIcon；按 <code>WifiRemotePhase</code>（PreparingCamera / AwaitingNetworkApproval / JoiningNetwork / OpeningSession / Closing / Failed）显示一行文字，其余空白；最底部 <code>if (busy) OutlinedButton(断开连接)</code></td>
 <td>与截图“整页空白 + 断开连接”一致；该页无任何有用内容</td><td>第 5–6 页：改为设备页上的叠层，整页删除</td></tr>
<tr><td><b>Wi-Fi 顶栏</b></td><td><code>WifiPhotoTopBar.kt</code>：浏览态 返回 + 标题 + refresh + link_off（<code>disconnectAndExit</code>）；选择态 关闭 + 全选（“本页全选”）+ download 图标</td><td>断开图标在每个子页重复；“全选”只作用于当前页</td><td>第 8–9 页</td></tr>
<tr><td><b>预览页</b></td><td><code>WifiPhotoViewer.kt:142-198</code> 顶栏：返回 + 文件名 + 「已拍摄：时间」（<code>wifi_remote_photo_captured</code>）+「6/227」+ 选择框；<code>:250-260</code> 图下方 <code>PhotoTransferStatus</code>（「正在保存 xxx…」+ 进度条 + 取消 TextButton）；<code>:261-291</code> ‹ 下载 JPEG（6.6 MB） ›；<code>PhotoDownloadAction</code>（<code>WifiPhotoBrowser.kt:529-558</code>）按钮文案含格式与大小，已保存/无 JPEG/过大时在按钮下另写一行</td><td>状态文字散在图下方；按钮文案过长</td><td>第 12 页</td></tr>
</table>
<div class="note" style="margin-top:8px"><b>两处与“用户核实”的补充</b>：① 动态色分支确为注释状态，但主色并非蓝色——Light 主色是 M3 默认紫，Dark 主色常量写成 <code>0xADD8E6FF</code>（实际是半透明浅蓝，几乎肯定是想写 <code>0xFFADD8E6</code>）。② 设备页 BLE 断开链路已存在（<code>ServiceCommandRouter.ACTION_DISCONNECT_DEVICE</code>），本方案只改“触发点”，不新增协议能力。</div>""")

# ============================================================ 3 rules
page("按钮规则：什么时候用图标，什么时候用文字", "B2 · 一套规则 + 对全部现有界面的检查", f"""
<div class="row">
 <div class="col" style="flex:1.05">
 <h2 style="margin-top:0">规则表</h2>
 <table class="dense">
 <tr><th style="width:150px">场景</th><th style="width:96px">形式</th><th>规则 / 示例</th></tr>
 <tr><td>导航：返回 / 关闭</td><td><b>纯图标</b></td><td>顶栏左：← ；选择态 ✕；对话框右上 ✕。永不用文字“返回”</td></tr>
 <tr><td>顶栏操作</td><td><b>纯图标</b></td><td>右侧最多 2 个：刷新、视图选项、全选、Ⓘ；必有 contentDescription + 长按提示</td></tr>
 <tr><td>就地编辑</td><td><b>纯图标</b></td><td>紧邻被编辑内容：名称右侧 ✎</td></tr>
 <tr><td>状态指示</td><td><b>图标/图示（不可点）</b></td><td>校时 ✓、信号格、电量、GPS 点；颜色只用语义色，不靠颜色单独表达（配图标形状）</td></tr>
 <tr><td>页面主操作</td><td><b>文字按钮（填充）</b></td><td>设备页「连接」、预览页「下载」、选择态「下载 N 张」；整宽或≥84dp；一屏最多 1 个</td></tr>
 <tr><td>导航入口</td><td><b>整块卡片</b></td><td>图标 + 文字 + ›（Wi-Fi 照片传输）；不做成底部按钮</td></tr>
 <tr><td>危险操作</td><td><b>文字 + 图标（描边红）</b></td><td>「移除相机」；放在页内“危险区”，与普通操作隔开；必二次确认；<b>永不放顶栏/左下角</b></td></tr>
 <tr><td>对话框决定</td><td><b>文字（Text 按钮）</b></td><td>取消 / 保存 / 重试 / 移除（移除用 error 色）</td></tr>
 <tr><td>叠层上的退出</td><td><b>文字</b></td><td>「取消」「重试」「关闭」；叠层不放图标按钮</td></tr>
 <tr><td>设置开关</td><td>Switch 行</td><td>左文右开关；不写说明</td></tr>
 <tr><td>缩略图角标</td><td>小图标（可点区 ≥48dp）</td><td>放大镜、选择框；锁/星/格式是只读标记</td></tr>
 <tr><td>断开连接</td><td><b>无按钮</b></td><td>只有“设备页左上返回”（第 8 页）</td></tr>
 </table>
 <div class="box" style="margin-top:8px;font-size:12.3px"><b>补充约束</b>：同一页同类动作同一形式；一屏文字按钮 ≤ 2 个（对话框除外）；标题统一左对齐（设备页由居中改左对齐，全局一致）；图标统一 Material 24dp 填充风格。</div>
 </div>
 <div class="col" style="flex:1.1">
 <h2 style="margin-top:0">按规则检查现有界面</h2>
 <table class="dense">
 <tr><th style="width:110px">界面</th><th style="width:200px">现状 → 违反点</th><th>v2 处置</th></tr>
 <tr><td>设备页</td><td>连接/断开 OutlinedButton 夹在内容中；Wi-Fi 入口 88dp 底部大按钮 + 说明小字</td><td>「连接」填充按钮进状态卡（仅未连接时出现）；无断开按钮；入口改卡片置于状态卡下</td></tr>
 <tr><td>Ⓘ 对话框</td><td>底部一排 3 个 TextButton（含红色“移除”）；OK</td><td>✎ 图标；帮助删；移除=危险区描边按钮；OK→右上 ✕</td></tr>
 <tr><td>重命名对话框</td><td>有说明小字</td><td>删说明；保存/取消保持文字</td></tr>
 <tr><td>移除确认</td><td>合规</td><td>不变（文字：取消 / 移除）</td></tr>
 <tr><td>Wi-Fi 连接页</td><td>TextButton「返回」当导航图标；「断开连接」OutlinedButton；整页文字</td><td>整页删除 → 设备页叠层，叠层仅「取消」</td></tr>
 <tr><td>日期列表</td><td>顶栏 刷新 + 断开 图标</td><td>只留刷新</td></tr>
 <tr><td>日期网格</td><td>顶栏 刷新 + 断开；底部 4 图标 + 文字分页栏；图下文字状态 + 取消</td><td>顶栏 刷新 + 视图选项；分页栏删；进度走叠层/底部条</td></tr>
 <tr><td>选择态</td><td>顶栏 ✕ + 全选 + 下载图标（批量规模藏在 contentDescription）</td><td>顶栏 ✕ + 全选；<b>下载 N 张</b>为底部文字主按钮</td></tr>
 <tr><td>预览页</td><td>‹ [下载 JPEG（6.6 MB）] ›；图下进度文字 + 取消；一行状态说明</td><td>底部仅「下载」；进度/取消居中叠层；状态由按钮文案表达</td></tr>
 <tr><td>批量下载 Sheet</td><td>文字按钮</td><td>合规，不变</td></tr>
 </table>
 </div>
</div>""")

# ============================================================ 4 device page
page("设备页：新布局", "B3 · 「连接」进状态卡，Wi-Fi 入口上移，底部不再钉按钮", f"""
<div class="row">
 <div class="phs">
  {phone("dev_old", 0.72, "现状（真机）<br>①连接/断开按钮漂在内容中 ②Wi-Fi 入口钉在最底部")}
  {phone("dev_ok", 0.72, "v2 · 已连接")}
  {phone("dev_off", 0.72, "v2 · 未连接（进入设备页会自动连接，失败/取消后停在此态）")}
 </div>
 <div class="col" style="min-width:420px">
  <h2 style="margin-top:0">布局（自上而下）</h2>
  <ul>
   <li>{n(1)} <b>状态卡</b>：相机图标 + 名称 + 连接状态（绿/灰点）。<b>「连接」填充按钮只在未连接时</b>出现在卡片右侧 —— 状态与操作同处一眼可见；已连接时没有任何断开按钮。</li>
   <li>{n(2)} 卡内三格：<b>校时</b>（绿 ✓ / 琥珀 ⚠ / 转圈）· <b>信号</b>（4 格）· <b>电量</b>（图示 + %）。每格只有图示/数字 + 一个字标签，无说明行。</li>
   <li>{n(3)} <b>Wi-Fi 照片传输</b>：整块卡片，紧贴状态卡，是页面主入口；未连接时置灰并写「连接相机后可用」。</li>
   <li>{n(4)} <b>GPS 位置传输</b>：已传输 / 已写入 / 最近写入 三数 + 状态胶囊（传输中 绿 · 延迟 琥珀 · 已停止 灰），v1 的脚注说明已删。</li>
   <li>{n(5)} 未连接时的「连接」按钮（B3 的核心：不再是底部那个别扭的按钮）。</li>
  </ul>
  <h2>状态 → 呈现</h2>
  <table class="dense">
  <tr><th style="width:96px">状态</th><th>呈现</th></tr>
  <tr><td>连接中</td><td>设备页上叠“正在连接相机…”叠层（第 6 页），状态卡不动</td></tr>
  <tr><td>已连接</td><td>如左；校时进行中该格转圈</td></tr>
  <tr><td>未连接</td><td>灰点 + 「连接」；三格置灰（电量保留上次值，灰）；GPS 胶囊「已停止」</td></tr>
  <tr><td>异常</td><td>校时 ⚠ 琥珀；信号弱 红；电量 ≤20% 红；GPS 延迟 琥珀（见暗色页示例）</td></tr>
  </table>
  <div class="note" style="margin-top:8px"><b>为什么 Wi-Fi 入口在状态卡下、不在底部</b>：它是本页唯一的下一步动作；放在底部既远离状态、又被误当“连接”按钮（正是用户觉得奇怪的原因）。卡片式入口也与“连接”主按钮形成清晰的主次：<b>连接 = 填充按钮；进入照片 = 导航卡片</b>。</div>
  <div class="small" style="margin-top:6px">数据来源与刷新（校时/信号/电量/GPS）沿用 v1，见第 15 页重排表。</div>
 </div>
</div>""")

# ============================================================ 5 overlay frames
fr = [("prepare", "① 启动相机 Wi-Fi"), ("approve", "② 等待系统授权"), ("join", "③ 连接相机"),
      ("catalog", "④ 读取目录"), ("ready", "⑤ 就绪")]
page("连接过程：叠在原界面上的动画叠层（分帧）", "B1 · 用户在设备页点「Wi-Fi 照片传输」后，全程不离开设备页", f"""
<div class="phs" style="justify-content:space-between">{"".join(phone("ov_" + k, 0.5, c) for k, c in fr)}</div>
<table class="dense" style="margin-top:8px">
<tr><th style="width:116px">帧</th><th style="width:196px">对应 <code>WifiRemotePhase</code></th><th style="width:190px">文案（zh / en）</th><th>动画</th><th style="width:140px">典型耗时</th><th style="width:156px">点「取消」</th></tr>
<tr><td>① 启动</td><td>PreparingCamera</td><td>正在启动相机 Wi-Fi / Starting camera Wi-Fi</td><td>圆环顺时针旋转（1.2 s/圈），相机图标居中静止；第 1 个步骤点高亮</td><td>2–5 s</td><td>停止启动请求，回到设备页</td></tr>
<tr><td>② 授权</td><td>AwaitingNetworkApproval</td><td>请允许连接相机 Wi-Fi / Allow camera Wi-Fi<br>副行：请在系统弹窗中点「连接」</td><td>圆环停转、Wi-Fi 波纹由内向外依次渐显循环（1.2 s）；叠层<b>不遮挡系统弹窗</b>（系统窗口在其上方）</td><td>取决于用户（无超时倒计时）</td><td>放弃本次请求，回到设备页</td></tr>
<tr><td>③ 连接</td><td>JoiningNetwork</td><td>正在连接相机 / Connecting to camera</td><td>圆环旋转 + Wi-Fi 图标；步骤点 3 高亮</td><td>2–8 s</td><td>断开本次 Wi-Fi 加入</td></tr>
<tr><td>④ 读目录</td><td>OpeningSession + <code>browseDates</code></td><td>正在读取照片目录 / Reading photo list</td><td>圆环旋转 + 相册图标；<b>无进度数字</b>（枚举无诚实进度，沿用现有注释）</td><td>1–10 s（视张数）</td><td>关闭会话，回到设备页</td></tr>
<tr><td>⑤ 就绪</td><td>Ready</td><td>已连接（仅图标，不读出额外文字）</td><td>圆环收拢为绿色 ✓（250 ms）→ 保持 300 ms → 卡片淡出 + 底层设备页以共享元素式过渡<b>推入日期列表</b></td><td>0.5 s</td><td>（无取消键）</td></tr>
</table>
<div class="row" style="margin-top:8px">
 <div class="col"><div class="box"><b>卡片规格</b>：宽 286 dp、高固定 252 dp（<b>五帧同尺寸，文案变化不引起跳动</b>）· 圆角 28 · 底色 surfaceContainerHigh · 背后 scrim 38%（暗色 60%）· 步骤点 4 个 · 底部分隔线 + 居中文字按钮。进入：scrim 淡入 150 ms + 卡片 0.92→1 缩放 200 ms；退出：淡出 150 ms。</div></div>
 <div class="col"><div class="box"><b>为什么是叠层不是新页面</b>：用户从设备页发起，等待期间需要的只有“进度 + 能取消”；叠层保留上下文（相机名、状态），取消后原地回到设备页；也消除了现状“整页空白 + 断开连接”的体验。实现上等于删除 <code>WifiRemoteScreen</code> 的整页 pre-browser <code>Scaffold</code>，由设备页宿主按 <code>WifiRemoteState.phase</code> 渲染 <code>ConnectOverlay</code>。</div></div>
</div>""")

# ============================================================ 6 overlay fail / ble / spec
page("叠层：失败、蓝牙连接、断开中 与规格", "B1 · 失败与取消的收尾，以及动画/无障碍规格", f"""
<div class="row">
 <div class="phs">
  {phone("ov_fail", 0.56, "Wi-Fi 连接失败<br>（取消 / 重试；帮助在此上下文出现）")}
  {phone("ov_ble", 0.56, "进入设备页自动连接（蓝牙）<br>同一叠层组件，无步骤点")}
  {phone("ov_ble_fail", 0.56, "蓝牙连接失败")}
  {phone("ov_closing", 0.56, "断开/结束会话中<br>不可取消，仅作占位")}
 </div>
 <div class="col" style="min-width:300px">
  <h2 style="margin-top:0">取消与失败语义</h2>
  <table class="dense">
  <tr><th style="width:92px">事件</th><th>结果</th></tr>
  <tr><td>点「取消」</td><td>仅取消“本次连接尝试”：叠层关闭，设备页原样（蓝牙连接不变）。不弹确认。系统返回键 = 取消。</td></tr>
  <tr><td>用户拒绝授权</td><td>失败叠层「未能连接相机 Wi-Fi / 未获授权」+ 取消 / 重试</td></tr>
  <tr><td>相机无响应/超时</td><td>失败叠层「相机无响应，请确认相机已开机」</td></tr>
  <tr><td>重试</td><td>回到帧 ①（不重新进入页面）</td></tr>
  <tr><td>帮助</td><td>打开现有 <code>WifiConnectionHelp</code>（含手动 IP 入口）。仅失败叠层出现一次（Q11）</td></tr>
  </table>
 </div>
</div>
<div class="row" style="margin-top:6px">
 <div class="col">
 <h2 style="margin-top:0">动画规格</h2>
 <table class="dense">
 <tr><th style="width:120px">项</th><th>规格</th></tr>
 <tr><td>时长/曲线</td><td>入场 200 ms emphasized-decelerate；出场 150 ms emphasized-accelerate；步骤点交叉淡入 200 ms</td></tr>
 <tr><td>圆环</td><td>外径 60 dp、线宽 4 dp、扫角 110°，匀速 1.2 s/圈（<code>rememberInfiniteTransition</code>）</td></tr>
 <tr><td>减少动态</td><td>系统“移除动画”开启时：圆环改不转的不确定态 + 文案切换用淡入淡出，不做缩放/波纹</td></tr>
 <tr><td>无障碍</td><td>卡片 <code>liveRegion = Polite</code>，每步文案朗读；取消键可聚焦；叠层出现时底层设备页 <code>clearAndSetSemantics</code>（不可点）</td></tr>
 <tr><td>触控</td><td>scrim 吞掉点击，<b>不</b>因点空白处关闭（避免误取消）</td></tr>
 </table>
 </div>
 <div class="col">
 <h2 style="margin-top:0">文案（zh-rCN / en，其余语言不新增）</h2>
 <table class="dense">
 <tr><th>zh-rCN</th><th>en</th></tr>
 <tr><td>正在启动相机 Wi-Fi</td><td>Starting camera Wi-Fi</td></tr>
 <tr><td>请允许连接相机 Wi-Fi · 请在系统弹窗中点「连接」</td><td>Allow camera Wi-Fi · Tap “Connect” in the system dialog</td></tr>
 <tr><td>正在连接相机 · 正在读取照片目录</td><td>Connecting to camera · Reading photo list</td></tr>
 <tr><td>未能连接相机 Wi-Fi · 相机无响应，请确认相机已开机</td><td>Couldn’t connect to camera Wi-Fi · No response — check the camera is on</td></tr>
 <tr><td>未能连接相机 · 请确认相机已开机且在附近</td><td>Couldn’t connect to camera · Make sure it’s on and nearby</td></tr>
 <tr><td>取消 · 重试 · 帮助 · 关闭</td><td>Cancel · Retry · Help · Close</td></tr>
 </table>
 </div>
</div>""")

# ============================================================ 7 info dialog
page("Ⓘ 相机信息：极简版", "A · 说明全删 · ✓ 图标 · ✎ 重命名 · 移除放进“危险区”", f"""
<div class="row">
 <div class="phs">
  {phone("info_old", 0.57, f"现状<br>{n(1)}三行说明 {n(2)}“已启用（应用会保持开启）” {n(3)}重命名 {n(4)}帮助 {n(5)}移除（左下一排）")}
  {phone("info_new", 0.57, "v2 · 已连接（Wi-Fi 会话后多出固件/序列号两行）")}
  {phone("rename", 0.57, "重命名（点 ✎）<br>说明小字已删")}
  {phone("remove", 0.57, "移除相机的二次确认")}
 </div>
 <div class="col" style="min-width:330px">
  <h2 style="margin-top:0">逐条落实</h2>
  <ul>
   <li><b>A1</b> 保持活跃下三行说明、自动校时/区域的「已启用（应用会保持开启）」全部删除；已启用 = {n(2)} <b>绿色 ✓ 图标</b>，无任何文字。</li>
   <li><b>A2</b> 重命名 = 名称行最右侧 {n(1)} <b>✎ 图标</b>（点击进入重命名对话框）。「移除」不再混在左下一排文字里，改为 {n(3)} 底部<b>“危险区”整宽描边红色按钮</b>（图标 + 「移除相机」），点击后<b>二次确认</b>。</li>
   <li><b>A3</b> 「帮助」按钮删除。</li>
   <li>{n(4)} 确认键 OK 改为右上 ✕（关闭）；更少的底部按钮。</li>
  </ul>
  <h2>移除按钮位置：取舍</h2>
  <table class="dense">
  <tr><th style="width:92px">方案</th><th>评价</th></tr>
  <tr><td class="ok">采用：Ⓘ 底部危险区</td><td>与普通设置隔开（分隔线 + 描边红色），离右上 ✕ 最远，离拇指自然区也有距离；带文字不会误触/看不懂；必二次确认</td></tr>
  <tr><td>现状：左下一排文字</td><td>与重命名/帮助同形同排，易误点，且用户明确不喜欢</td></tr>
  <tr><td>备选：设备页顶栏 ⋮</td><td>增加顶栏图标，违背“顶栏最多 2 个且是常用动作”</td></tr>
  <tr><td>备选：相机列表左滑/长按</td><td>最不打扰，但发现性差、需另做手势；可作为后续补充（Q10）</td></tr>
  </table>
  <h2>行规则</h2>
  <table class="dense">
  <tr><td style="width:92px">已开启</td><td>绿 ✓（success）</td></tr>
  <tr><td>系统不支持/未知</td><td>灰色「—」（不显示文字原因）</td></tr>
  <tr><td>关闭（应用会尝试打开）</td><td>琥珀 ⚠，点按重试开启</td></tr>
  <tr><td>固件 / 序列号</td><td>仅 Wi-Fi 会话读到后出现；序列号默认遮罩，点按 10 s 内显示</td></tr>
  </table>
 </div>
</div>""")

# ============================================================ 8 navigation / disconnect
page("导航与断开语义", "B4 · 不单独设计断开按钮；只有设备页左上返回是真断开", f"""
{wide("nav", 1330, 470, 1.0)}
<div class="row" style="margin-top:8px">
 <div class="col">
  <h2 style="margin-top:0">我的解读（请确认，见 Q1–Q3）</h2>
  <ul>
   <li>用户原话含两句：“不需要单独设计断开连接按钮”与“断开连接按钮放在右上角”。按<b>前一句为准</b>理解：<b>不为断开单独设计按钮</b>，照片浏览等所有子页面的顶栏<b>不放断开</b>（现有 link_off 图标全部移除）。后一句视为“若必须有，只能在右上角”的偏好，作为 <b>Q2</b> 待确认（如保留，唯一位置 = 设备页顶栏 Ⓘ 旁的一个 link_off 图标，仍不在子页）。</li>
   <li>“真正的断开” = <b>蓝牙主连接</b>：在设备页点左上返回键触发（沿用现有断开链路 <code>DeviceDetailViewModel.disconnect → ACTION_DISCONNECT_DEVICE</code>）；对应地<b>进入设备页（未连接时）自动连接</b>。</li>
   <li>Wi-Fi 传输会话是短暂通道：<b>从日期列表返回设备页即结束</b>（沿用 <code>navigateBack</code>/<code>AndroidWifiRemoteScreen.close</code> 现有行为），再次进入需重新走叠层（Q4）。</li>
  </ul>
 </div>
 <div class="col">
  <h2 style="margin-top:0">各页顶栏图标（规则落地）</h2>
  <table class="dense">
  <tr><th style="width:96px">页面</th><th style="width:100px">左</th><th>右（≤2 个）</th></tr>
  <tr><td>设备页</td><td>← （=断开 B）</td><td>Ⓘ</td></tr>
  <tr><td>日期列表</td><td>←</td><td>刷新</td></tr>
  <tr><td>日期网格</td><td>←</td><td>刷新 · 视图选项</td></tr>
  <tr><td>网格选择态</td><td>✕</td><td>全选本日（下载在底部）</td></tr>
  <tr><td>预览</td><td>←</td><td>位置 6/227 · 选择框</td></tr>
  </table>
  <div class="note" style="margin-top:6px"><b>风险提示</b>：现状“手动断开”会持久化 disabled 并停止 presence 观察（CDM 再出现不重连）。若“设备页返回 = 手动断开”，则每次看完设备页都会关闭后台自动连接。建议改为“软断开”（只关链路，不持久化 disabled）——见 <b>Q3</b>。</div>
 </div>
</div>""")

# ============================================================ 9 date list + grid
page("日期列表与日期网格：顶栏取舍", "C · 无断开图标、无分页栏；视图选项并入顶栏", f"""
<div class="row">
 <div class="phs">
  {phone("dates", 0.62, f"日期列表（Wi-Fi 就绪后）<br>{n(1)}信息条：卡容量/剩余/固件 {n(2)}顶栏仅刷新")}
  {phone("grid_s", 0.62, f"日期网格 · 小（默认，4 列）<br>{n(1)}视图选项 {n(2)}刷新 {n(3)}无页码栏，右侧快速滚动条")}
  {phone("grid_sel", 0.62, "选择态：顶栏 ✕ + 全选本日；<br>底部主按钮「下载 3 张」")}
 </div>
 <div class="col" style="min-width:400px">
  <h2 style="margin-top:0">顶栏方案取舍</h2>
  <table class="dense">
  <tr><th style="width:150px">方案</th><th>评价</th></tr>
  <tr><td class="ok">A（采用）：刷新 + 视图选项；保护/评分并入视图选项</td><td>2 个图标不拥挤；v1 的“星标开关”变成视图面板里的一行开关，与“格式/大小”同类（都是“tile 上显示什么”），概念统一</td></tr>
  <tr><td>B：刷新 + 视图 + 保护评分 ☆（3 个）</td><td>在 390dp 宽、标题带副标题（日期 + 张数）时偏挤，且 ☆ 与“视图”语义重叠</td></tr>
  <tr><td>C：只留视图，刷新改下拉刷新</td><td>最干净，但刷新在目录（常驻内存）场景下是少见动作，隐藏手势发现性差；可作补充而非替代</td></tr>
  </table>
  <h2>顶栏与标题</h2>
  <ul>
   <li>标题「11月9日」+ 副标题「227 张」（取代原分页栏里的“第 n/总页”信息）。</li>
   <li>选择态：左 ✕、标题「已选 N 张（含其它日期）」，右 全选本日；<b>下载 N 张</b> 移到底部整宽文字主按钮（规则：页面主操作 = 文字按钮），拇指可达；原顶栏下载图标删除。</li>
   <li>加载进度：目录读取走第 5 页叠层；缩略图加载沿用顶部 4 dp 细进度条，不再占一行文字；下载进度走居中叠层（第 12 页），批量下载沿用现有汇总条。</li>
   <li>日期列表的信息条（固件/卡容量）延续 v1 数据，但<b>只放日期列表页</b>，网格页不放（给照片腾空间）；序列号只在 Ⓘ。</li>
   <li>右侧快速滚动条替代翻页：单日张数多时拖动定位，滚动停止后淡出。</li>
  </ul>
 </div>
</div>""")

# ============================================================ 10 sizes + panel
page("小 / 中 / 大 与视图选项面板", "C1 · 小 = 当前 4 列；中/大 列数更少 → 方块更大", f"""
<div class="phs" style="justify-content:flex-start">
 {phone("grid_s2", 0.52, "小 · 4 列（默认）<br>格式 ✓ · 大小 ✗ · 保护评分 ✗")}
 {phone("grid_m", 0.52, "中 · 3 列<br>格式 ✓ · 大小 ✓")}
 {phone("grid_l", 0.52, "大 · 2 列<br>格式 ✓ · 大小 ✓ · 保护评分 ✓")}
 {phone("grid_panel", 0.52, "视图选项面板（点顶栏 ⚙ 图标）<br>锚定在图标下方，网格仍可见 → 实时预览")}
 <div class="col" style="min-width:300px;font-size:12.3px">
  <h2 style="margin-top:0">规格</h2>
  <table class="dense">
  <tr><th>档位</th><th>列数(360dp)</th><th><code>GridCells.Adaptive</code></th></tr>
  <tr><td>小</td><td>4（≈91 dp）</td><td>minSize 80 dp（即现状 <code>TileMinSize</code>）</td></tr>
  <tr><td>中</td><td>3（≈123 dp）</td><td>minSize 104 dp</td></tr>
  <tr><td>大</td><td>2（≈187 dp）</td><td>minSize 150 dp</td></tr>
  </table>
  <div class="small">横屏/平板按宽度自适应出更多列（沿用 Adaptive）；411 dp 机型同样得到 4/3/2。</div>
  <h3>面板内容</h3>
  <ul>
   <li>缩略图大小：分段按钮 小 / 中 / 大</li>
   <li>文件格式：开关（<b>默认开</b>，即现状）</li>
   <li>文件大小：开关（<b>默认关</b>；「小」档位自动隐藏并置灰）</li>
   <li>保护与评分：开关（<b>默认关</b>，即 v1 “基础态零变化”；相机不提供时置灰 + 「此相机不提供」）</li>
  </ul>
  <div class="small">偏好全局保存（DataStore），跨重启保留（Q9）。</div>
 </div>
</div>
<div style="margin-top:4px">{wide("anatomy", 1330, 330, 0.95)}</div>""")

# ============================================================ 11 paging
page("取消分页：现状核实、结论与代价", "C2 · 日期内应为连续滚动，底部不放翻页键 / 分页栏", f"""
{wide("paging", 1330, 330, 0.92)}
<div>
 <table class="dense">
 <tr><th style="width:118px">影响点</th><th style="width:330px">现状（分页）</th><th>v2（连续）/ 需改动</th></tr>
 <tr><td>分页常量与切片</td><td><code>PAGE_SIZE=60</code>；<code>cameraPhotoPage</code>/<code>photoPage*</code> 切片；<code>browseDate(dateKey, offset)</code></td><td>日期内一次取整日 captures；删分页栏、<code>WifiPhotoPager.kt</code>、<code>photoPageCount/Number/Offset/parsePageInput</code> 及跳页对话框与 4 个页码字符串</td></tr>
 <tr><td>选择</td><td><code>selectedPhotos</code> 已是跨页 map；“全选”仅本页</td><td>“全选”→<b>全选本日</b>；选择不再需“跨页”概念（跨日仍保留）</td></tr>
 <tr><td>长按拖选</td><td><code>RangeSelectionSession.pageIds</code>、<code>PageIdentity(offset)</code>；边缘自动滚动“不翻页”</td><td>范围覆盖整日，PageIdentity 简化为 (generation, dateKey, revision)；自动滚动速度需按整日长度重新标定</td></tr>
 <tr><td>预览滑动</td><td><code>viewPhotoAt</code> 越页时 <code>browseDate(offset±PAGE_SIZE)</code>；<code>preceding/hasMore</code> 边界占位页；<code>changingPage</code> 锁滑动</td><td>Pager 直接覆盖整日列表，边界页/锁滑动/跨页加载逻辑整体删除；返回网格时滚动到当前张</td></tr>
 <tr><td>缩略图加载</td><td><code>requestThumbnails(visible, margin)</code> 已按可见窗口按需加载</td><td><b>不变</b>（窗口加载与页无关）；相机流量无增量</td></tr>
 <tr><td>内存</td><td>每页最多 60 项缩略图常驻</td><td>滚动过的缩略图累积 → 需<b>LRU 上限</b>（估：160×120 ARGB≈77 KB/张，227 张≈17 MB，1000 张≈77 MB；建议上限约 300 张≈23 MB）</td></tr>
 <tr><td>非日期分组路径</td><td><code>browser.grouped=false</code> 的平铺列表仍分页（<code>browsePhotos(offset)</code>）</td><td>Q6：是否同改；建议同改以去掉整套分页代码</td></tr>
 <tr><td>测试</td><td><code>WifiCameraCaptureTest.kt:70</code> 断言 60；<code>SonyImageTransferTest</code>/<code>SonyContentCatalogTest</code>/<code>WifiCatalogSnapshotTest</code>/<code>WifiRemoteControllerTest</code> 多处用 PAGE_SIZE</td><td>协议层 <code>page(offset)</code> 可保留（PTP 枚举仍用）；UI/Controller 分页用例删改，补“整日连续 + 选择/拖选/预览整日”用例</td></tr>
 </table>
 </div>
<div class="row" style="margin-top:8px">
 <div class="col" style="flex:1.6">
  <div class="box"><b>结论</b>：<span class="ok">建议取消日期内分页</span>。代价是一次中等规模的 Controller/UI 简化（净删除代码为主）+ 缩略图内存上限；收益是预览可跨 60 张边界无缝滑动、选择语义变简单、无页码栏。<br><br>
  <b>需用户确认</b>：Q6（含非分组路径是否同改）；大日（&gt;1000 张）是否需要日期内再分段标题（建议不需要，快速滚动条足够）。</div>
  <div class="note">我不改应用代码：以上是评审“取消分页”可行性的依据，开发拆分见第 17 页 M4。</div>
 </div>
</div>""")

# ============================================================ 12 viewer
page("单张预览页", "D · 名称+大小同行、下方时间、底部只有「下载」、居中叠层、已保存置灰 · 左右滑动保留", f"""
<div class="row">
 <div class="phs">
  {phone("v_norm", 0.56, f"常态：{n(1)}时间在文件名下方（无“已拍摄”）{n(2)}位置 6/227 {n(3)}选择框 {n(4)}底部仅「下载」")}
  {phone("v_dl", 0.56, f"下载中：{n(5)}画面中央叠层（进度环 + 取消），按钮置灰")}
  {phone("v_saved", 0.56, "已保存：按钮置灰并改为「已保存」，无其它文字")}
  {phone("v_fail", 0.56, "失败：叠层「保存失败」+ 关闭 / 重试")}
 </div>
 <div class="col" style="min-width:340px">
  <h2 style="margin-top:0">逐条落实</h2>
  <ul>
   <li><b>D1</b> 顶栏文件名<b>下一行直接是时间</b>（<code>2026-11-09 14:32:08</code>），去掉“已拍摄”三字（删 <code>wifi_remote_photo_captured</code>）。</li>
   <li><b>D2</b> 文件大小在文件名同行右侧（次要色）。大小 = 「下载」将取的文件合计：JPG-only 显示 JPEG；Original 且 J&amp;R 显示 JPG+RAW 合计（Q15）。</li>
   <li><b>D3</b> 底部只有一个整宽「下载」按钮，去掉 ‹ ›（<code>chevron_*</code>、<code>previous/next_photo</code> 字符串可删）与格式/大小文案（<code>wifi_remote_download_format_size</code>）。</li>
   <li><b>D4</b> 下载在<b>画面中间</b>出叠层：进度环 + 百分比 + 「取消」；转换中（Converting）环转不定态；失败叠层「保存失败 / 关闭 / 重试」。完成：叠层消失，按钮置灰并改「已保存」，<b>不再另写“已将xxx保存到手机相册。”或“已保存”小字</b>。</li>
   <li>按钮文案即状态：<b>下载</b> · <b>已保存</b> · <b>无 JPEG</b> · <b>文件过大</b>（取代按钮下方的原因行）。</li>
   <li>下载期间<b>锁定左右滑动与选择框</b>（避免传输与预览加载在 PTP 上交叉，且叠层语义明确）；返回键 = 取消下载（Q12）。</li>
  </ul>
  <div class="note"><b>D5 核验</b>：<code>WifiPhotoViewer.kt:203</code> 使用 <code>HorizontalPager</code>，<code>userScrollEnabled = !changingPage</code>，<span class="ok">支持左右滑动</span>；v2 去掉箭头后<b>滑动是唯一的翻页手势</b>，位置由顶栏「6/227」指示。取消分页后整日可连续滑。</div>
 </div>
</div>
<div style="margin-top:2px">{wide("swipe", 1330, 330, 0.92)}</div>""")

# ============================================================ 13 palette
tokens = [
    ("primary", "primary", "主按钮、选中、链接、进度、开关"), ("onPrimary", "onPrimary", "主按钮文字/图标"),
    ("primaryContainer", "primaryC", "Wi-Fi 入口卡、相机图标底、选中背景"), ("onPrimaryContainer", "onPrimaryC", "primaryContainer 上的文字"),
    ("secondary", "secondary", "次要图标/文字"), ("secondaryContainer", "secC", "分段按钮选中、Tonal 按钮"),
    ("onSecondaryContainer", "onSecC", "secondaryContainer 上的文字"), ("tertiary（青）", "tertiary", "极少量强调（预留）"),
    ("tertiaryContainer", "tertiaryC", "预留"),
    ("error", "error", "错误文字/图标、移除按钮、信号弱/电量低"), ("onError", "onError", "error 上的文字"),
    ("errorContainer", "errorC", "失败图标底"), ("onErrorContainer", "onErrorC", "errorContainer 上文字"),
    ("background / surface", "bg", "页面底色"), ("surfaceContainerLowest", "surfLowest", "—"),
    ("surfaceContainerLow", "surfLow", "—"), ("surfaceContainer", "card", "状态卡/GPS 卡/信息条"),
    ("surfaceContainerHigh", "card2", "对话框、叠层卡片、面板"), ("surfaceContainerHighest", "hi", "占位底、停用胶囊"),
    ("onSurface", "onSurf", "主要文字"), ("onSurfaceVariant", "onSurfV", "次要文字、图标"),
    ("outline", "outline", "边框、停用图标"), ("outlineVariant", "outlineV", "分隔线、轨道"),
    ("inverseSurface", "inverse", "Snackbar 底"), ("inverseOnSurface", "inverseOn", "Snackbar 文字"),
    ("inversePrimary", "inversePrimary", "Snackbar 内按钮"), ("scrim", "scrim", f"叠层背景：亮 38% / 暗 60% 不透明度"),
]
ext = [("success", "green", "已连接点、校时 ✓、传输中、信号良好"), ("onSuccess", "onGreen", "—"),
       ("successContainer", "greenC", "传输中胶囊底"), ("onSuccessContainer", "onGreenC", "—"),
       ("warning（图标/点）", "amber", "校时失败 ⚠、连接中点"), ("warningText", "amberText", "延迟文字（亮色加深保证对比）"),
       ("warningContainer", "amberC", "延迟胶囊底"), ("onWarningContainer", "onAmberC", "—")]


def sw_row(name, key, use):
    return (f'<tr><td>{name}</td><td><span class="sw" style="background:{LIGHT[key]}"></span><code>{LIGHT[key]}</code></td>'
            f'<td><span class="sw" style="background:{DARK[key]}"></span><code>{DARK[key]}</code></td><td>{use}</td></tr>')


pairs = [("onPrimary / primary", "onPrimary", "primary"), ("onPrimaryContainer / primaryContainer", "onPrimaryC", "primaryC"),
         ("onSurface / surface", "onSurf", "bg"), ("onSurfaceVariant / surfaceContainer", "onSurfV", "card"),
         ("error / surface", "error", "bg"), ("success / surfaceContainer", "green", "card"),
         ("warningText / surfaceContainer", "amberText", "card"), ("outline / surface", "outline", "bg")]
ctab = "".join(f"<tr><td>{a_}</td><td>{cr(LIGHT[x], LIGHT[y]):.1f} : 1</td><td>{cr(DARK[x], DARK[y]):.1f} : 1</td></tr>" for a_, x, y in pairs)
rat = "".join(f'<span class="sw" style="background:{RATING[i]}"></span>{i} <code>{RATING[i]}</code>&nbsp; ' for i in range(1, 6))
page("蓝色主色调：完整亮 / 暗色板", "E · 不使用动态色 · 固定蓝色 · 仅亮/暗两套（seed ≈ #0B57D0）", f"""
<div class="row">
 <div class="col" style="flex:1.55">
 <h2 style="margin-top:0">Material 3 色角色（Compose <code>lightColorScheme / darkColorScheme</code> 全量显式给出）</h2>
 <div class="dense"><table>
 <tr><th style="width:150px">角色</th><th style="width:118px">亮</th><th style="width:118px">暗</th><th>用途</th></tr>
 {"".join(sw_row(*r) for r in tokens)}
 </table></div>
 </div>
 <div class="col" style="flex:1">
 <h2 style="margin-top:0">扩展语义色（<code>CameraGpsExtendedColors</code> + CompositionLocal）</h2>
 <div class="dense"><table>
 <tr><th>角色</th><th>亮</th><th>暗</th><th>用途</th></tr>
 {"".join(sw_row(*r) for r in ext)}
 </table></div>
 <h3>评分五色（亮暗通用；永远叠在黑 70% 胶囊上，并必带数字）</h3>
 <div class="small">{rat}</div>
 <h3>其它固定色</h3>
 <div class="small">预览页背景 <span class="sw" style="background:#000"></span><code>#000000</code>（亮/暗均黑，沿用 <code>PhotoViewerColors</code>，其 primary 改用暗色 primary <code>#A8C7FA</code>）；缩略图角标底 黑 55–70% + 白字。</div>
 <h3>对比度（WCAG，计算值）</h3>
 <div class="dense"><table><tr><th>配对</th><th>亮</th><th>暗</th></tr>{ctab}</table></div>
 <div class="note" style="margin-top:6px"><b>Theme.kt 清理清单</b>：① 删 <code>dynamicColor</code> 参数与注释分支（<code>Theme.kt:36,43-46</code>）及 iOS 调用无关参数；② 删 Purple/PurpleGrey/Pink 常量与笔误色 <code>0xADD8E6FF</code>；③ 亮暗各给完整 ColorScheme；④ 状态色用扩展色，替换 <code>DeviceDetailScreen.kt:245-246</code>、<code>TransmissionDot.kt:36,62</code> 硬编码；⑤ Android 12+ 不启用 <code>dynamicLightColorScheme</code>。</div>
 </div>
</div>""")

# ============================================================ 14 dark
page("暗色对照", "E · 同一套组件在暗色下的呈现（状态色改用亮色版本，缩略图角标恒黑底白字）", f"""
<div class="phs" style="justify-content:space-between">
 {phone("d_dev", 0.62, "设备页 · 异常态：校时 ⚠ · 信号弱 · 电量低 · GPS 延迟")}
 {phone("d_grid", 0.62, "日期网格 · 中 · 格式+大小+保护评分全开")}
 {phone("d_ov", 0.62, "连接叠层（帧 ③）")}
 {phone("d_info", 0.62, "Ⓘ 极简版")}
</div>
<div class="row" style="margin-top:6px">
 <div class="col"><div class="box"><b>暗色规则</b>：背景 <code>#101418</code>，卡片按 surfaceContainer 阶梯抬升（<code>#1D2125 → #272B30 → #32363B</code>）而非加阴影；主按钮/链接用浅蓝 <code>#A8C7FA</code> + 深蓝字 <code>#062E6F</code>；状态色用提亮版本（绿 <code>#7DDB93</code>、琥珀 <code>#FFC857</code>、红 <code>#FFB4AB</code>），对比度见上一页。</div></div>
 <div class="col"><div class="box"><b>不随主题变化</b>：预览页（恒黑）、缩略图上的黑底白字角标、评分星色、占位渐变；叠层 scrim 暗色用 60% 以保持层次。</div></div>
</div>""")

# ============================================================ 15 data re-home
page("状态数据重排（v1 保留项的新承载位置）", "原则：外面只留基本信息；细节进 Ⓘ；Ⓘ 里没有说明文字", f"""
<table class="dense">
<tr><th style="width:130px">数据项</th><th style="width:150px">v1 位置</th><th style="width:210px">v2 位置</th><th>来源 / 刷新（沿用 v1）</th><th style="width:230px">不可用/陈旧时</th></tr>
<tr><td>连接状态</td><td>设备页名称下</td><td><b>设备页 状态卡</b>（点 + 文字）</td><td><code>deviceConnectionStatus(session)</code> 实时</td><td>—</td></tr>
<tr><td>校时</td><td>设备页卡片行</td><td><b>设备页 状态卡 第 1 格</b>（✓/⚠/转圈）</td><td><code>CameraSession.timeSync</code>：<code>CameraTimeSyncController.sync()</code>（:154-215）写入；仅 CC0E applied=1 记“已校正”</td><td>未连接 灰钟；被拒 琥珀 ⚠（原因码进日志，不上屏）</td></tr>
<tr><td>信号 RSSI</td><td>设备页卡片行（dBm+说明）</td><td><b>设备页 第 2 格</b>（4 格，无数字）</td><td><code>readRemoteRssi</code>，页面可见且已连接时首读 + 每 5 s；分档 ≥−60/−75/−85</td><td>未连接 0 格灰</td></tr>
<tr><td>电量</td><td>设备页卡片行</td><td><b>设备页 第 3 格</b>（图示 + %）</td><td>Wi-Fi 会话 Ready 后读 1 次（0x5001/0xD218 待实机核实），应用级缓存</td><td>≤30 min 正常；30 min–24 h 灰；&gt;24 h/换机 「—」</td></tr>
<tr><td>GPS 已传输/已写入/最近写入</td><td>设备页 GPS 卡 + Ⓘ 明细</td><td><b>设备页 GPS 卡</b>（三数 + 胶囊）；<b>Ⓘ 不再放</b></td><td>手机侧计数：<code>sendToDevice</code> 入队 +1；<code>BleSessionCoordinator.kt:116-118</code> 写回调 +1；新鲜度 ≤15 s 绿 / 15–60 s 琥珀 / &gt;60 s 红</td><td>断开保留末值灰显；“重置计数”取消（不再放 Ⓘ，避免说明与按钮）</td></tr>
<tr><td>固件版本</td><td>Wi-Fi 页信息条</td><td><b>日期列表 信息条</b>（右上）+ Ⓘ 一行</td><td><code>PtpDeviceInfoParser</code> 解析 DeviceVersion（PtpDeviceInfo.kt:20-53）</td><td>读不到：该行不出现（无占位）</td></tr>
<tr><td>序列号</td><td>Wi-Fi 页信息条（遮罩）</td><td><b>仅 Ⓘ</b>（遮罩，点按 10 s 显示）</td><td>同上 SerialNumber；仅内存，不入日志</td><td>不出现</td></tr>
<tr><td>卡容量 / 剩余</td><td>Wi-Fi 页信息条</td><td><b>日期列表 信息条</b>（SD 图标 + 容量 + 剩余 + 进度条）</td><td>GetStorageInfo 0x1005，目录首屏后再发</td><td>失败：信息条隐藏，不影响列表</td></tr>
<tr><td>保护 / 评分</td><td>顶栏星标开关（默认关）</td><td><b>视图选项面板 一行开关</b>（默认关）</td><td>v4 catalog <code>SonyContentCatalog.kt:57</code> take(8)（布局待实机核实）；旧路径 ProtectionStatus</td><td>开关置灰 +「此相机不提供」</td></tr>
<tr><td>自动校时/区域、MTU、保持活跃</td><td>Ⓘ</td><td>Ⓘ（图标化、无说明）</td><td>现有 settings / session</td><td>✓ / 「—」/ ⚠</td></tr>
</table>
<div class="row" style="margin-top:8px">
 <div class="col"><div class="box"><b>为什么 GPS 明细不再进 Ⓘ</b>：用户要求 Ⓘ 极简（说明全删）。三个计数已是“基本信息”，直接留在设备页 GPS 卡；Ⓘ 只留身份与设置（名称、地址、保持活跃、自动项、MTU、固件、序列号、移除）。</div></div>
 <div class="col"><div class="box"><b>未变的 v1 约束</b>：每项独立失败、不阻塞 GPS/浏览/下载；信息类 PTP 请求排在目录首屏之后；序列号默认遮罩、不入日志不落盘；锁/评分只读、不增加点击目标；待实机核实项（电池码、catalog 8 字节布局、GetStorageInfo、RSSI 可用性等）与 v1 第 14 页一致。</div></div>
</div>""")

# ============================================================ 16 change list
page("变更清单", "相对 v1 设计 / 相对现有真机界面", f"""
<div class="row">
 <div class="col">
 <h2 style="margin-top:0">相对 v1 设计稿</h2>
 <table class="dense">
 <tr><th style="width:110px">项</th><th>v1</th><th>v2</th></tr>
 <tr><td>主题色</td><td>紫色 M3 默认</td><td>蓝色，完整亮/暗色板 + 扩展语义色</td></tr>
 <tr><td>设备页</td><td>名称块 + 连接按钮 + 两张卡 + 底部 Wi-Fi 入口</td><td>状态卡（含连接按钮）+ Wi-Fi 入口卡 + GPS 卡；无底部按钮、无断开按钮</td></tr>
 <tr><td>校时/信号/电量</td><td>卡内三行，各带说明行</td><td>三格，仅图示 + 一字标签</td></tr>
 <tr><td>Ⓘ</td><td>新增「实时状态」「GPS 明细」「重置计数」</td><td>极简：全部新增数据移出 Ⓘ；说明删光；✓ 图标；✎；危险区；✕</td></tr>
 <tr><td>保护/评分</td><td>顶栏星标开关</td><td>视图选项面板的一行开关</td></tr>
 <tr><td>Wi-Fi 信息条</td><td>网格顶部</td><td>日期列表顶部；序列号移入 Ⓘ</td></tr>
 <tr><td>顶栏</td><td>开关+刷新+断开</td><td>刷新 + 视图选项</td></tr>
 <tr><td>网格</td><td>4 列固定 + 分页</td><td>小/中/大 + 连续滚动 + 格式/大小开关</td></tr>
 <tr><td>连接过程</td><td>v1 第 12 页 9 步流程</td><td>设备页叠层 5 帧 + 失败/取消语义</td></tr>
 <tr><td>新增</td><td>—</td><td>按钮规则表、预览页改版、叠层规格、取消分页方案、断开语义</td></tr>
 </table>
 </div>
 <div class="col">
 <h2 style="margin-top:0">相对现有真机界面</h2>
 <table class="dense">
 <tr><th style="width:96px">界面</th><th>变化</th></tr>
 <tr><td>设备页</td><td>「连接」进状态卡；Wi-Fi 入口上移为卡片；新增校时/信号/电量/GPS；去掉「相机名称」小标题与说明小字；标题左对齐</td></tr>
 <tr><td>Ⓘ</td><td>删：三行说明、「已启用（应用会保持开启）」、帮助、OK；增：✎、绿 ✓、危险区移除、✕；重命名对话框删说明</td></tr>
 <tr><td>Wi-Fi 连接</td><td>整页（空白 + 断开连接）删除 → 设备页叠层 5 帧；仅「取消」</td></tr>
 <tr><td>日期列表</td><td>顶栏删断开图标；新增信息条</td></tr>
 <tr><td>日期网格</td><td>删断开图标、分页栏、跳页；新增副标题张数、视图选项（⚙）、小/中/大、格式/大小/保护评分开关、快速滚动条；选择态“下载”改底部主按钮，全选改“本日”</td></tr>
 <tr><td>预览页</td><td>删“已拍摄”、‹ ›、按钮内格式/大小、图下进度与状态文字、“已将…保存到手机相册”；增：大小同行、居中下载叠层、已保存置灰；保留：滑动、位置、选择框</td></tr>
 <tr><td>主题</td><td>紫 → 蓝；清理 dynamicColor；修 0xADD8E6FF；硬编码状态色改语义色</td></tr>
 <tr><td>导航语义</td><td>断开入口：断开按钮 → 设备页左上返回；进入设备页自动连接</td></tr>
 </table>
 <h2>新增/删除字符串（仅 en + zh-rCN）</h2>
 <div class="small"><b>新增</b>：叠层 7 条（第 6 页）、view_options / thumb_size_small|medium|large / show_format / show_size / show_protect_rating、download_saved_state（已保存按钮）、download_no_jpeg / download_too_large（按钮文案）、transfer_failed_title、camera_wifi_unavailable_hint。<br>
 <b>可删</b>：wifi_remote_photo_captured、viewer_previous_photo / next_photo、wifi_remote_browser_* 页码类（first/previous/next/page_of/jump_*）、wifi_remote_download_format_size、device_info_keep_active_hint、device 设置「已启用」说明、help 按钮文案引用、wifi_remote_close。<br>
 <b>图标新增</b>：edit、delete、tune、wifi、check、close（已有）、photo_library；<b>删除使用</b>：link_off、first_page、chevron（预览）。</div>
 </div>
</div>""")

# ============================================================ 17 questions + split
page("待确认问题与开发拆分", "请逐条回复；未回复的按“建议”默认推进", f"""
<div class="row">
 <div class="col" style="flex:1.35">
 <h2 style="margin-top:0">待确认（含两处原话解读 + 取消分页的代价）</h2>
 <table class="dense">
 <tr><th style="width:36px">#</th><th>问题</th><th style="width:230px">建议 / 默认</th></tr>
 <tr><td>Q1</td><td><b>解读 1</b>：“只有返回相机页面再点左上角返回键才是真正的断开” = 断开<b>蓝牙主连接</b>；Wi-Fi 传输会话只是子通道。是否如此？</td><td>是；进入设备页（未连接时）自动连接</td></tr>
 <tr><td>Q2</td><td><b>解读 2</b>：原话“不需要单独设计断开按钮”与“断开按钮放在右上角”并存，我以前一句为准：不设计断开按钮、子页不放断开。若仍想保留一个，唯一位置为设备页顶栏 Ⓘ 旁 link_off 图标。</td><td>不设计；若要，仅设备页右上</td></tr>
 <tr><td>Q3</td><td>设备页返回 = 现有“手动断开”（持久化 disabled、停 presence，CDM 再出现不重连）还是“软断开”（只关链路，仍可被后台自动连接）？</td><td>软断开（避免每次看完页面就关掉后台自动连接）</td></tr>
 <tr><td>Q4</td><td>从日期列表返回设备页时是否立即结束 Wi-Fi 会话（释放手机网络、省相机电）？</td><td>是（=现状）；再进入走叠层</td></tr>
 <tr><td>Q5</td><td>进入设备页未连接时自动连接（并弹叠层）？</td><td>是（沿用“直达连接”思路）</td></tr>
 <tr><td>Q6</td><td><b>取消分页</b>：代价=删分页栏/跳页、PAGE_SIZE 切片、跨页拖选与预览边界页、相关单测，新增缩略图 LRU（第 11 页）。是否取消？非日期分组的平铺路径是否同改？</td><td>取消；平铺路径同改</td></tr>
 <tr><td>Q7</td><td>“尺寸”指文件大小(MB，目录已有)还是像素尺寸(需额外请求)？</td><td>文件大小；像素尺寸暂不做</td></tr>
 <tr><td>Q8</td><td>视图选项默认值</td><td>小 / 格式开 / 大小关 / 保护评分关</td></tr>
 <tr><td>Q9</td><td>视图偏好范围与持久化</td><td>全局 + 跨重启保存</td></tr>
 <tr><td>Q10</td><td>移除相机放 Ⓘ 危险区；是否另加相机列表左滑/长按入口？</td><td>只做 Ⓘ 危险区</td></tr>
 <tr><td>Q11</td><td>失败叠层保留「帮助」（连接帮助 + 手动 IP）？</td><td>保留（唯一的帮助入口）</td></tr>
 <tr><td>Q12</td><td>预览下载中锁定滑动/选择、返回键=取消？已保存按钮是否加 ✓？</td><td>锁定；返回=取消；不加 ✓（纯文字）</td></tr>
 <tr><td>Q13</td><td>Ⓘ 去掉 OK 改右上 ✕？</td><td>是</td></tr>
 <tr><td>Q14</td><td>标题统一左对齐（设备页原居中）？</td><td>是</td></tr>
 <tr><td>Q15</td><td>预览大小显示口径：JPG-only 显示 JPEG；Original 的 J&amp;R 显示合计？</td><td>是</td></tr>
 <tr><td>Q16</td><td>蓝色 seed <code>#0B57D0</code> 是否认可（或有品牌色）？</td><td>认可</td></tr>
 </table>
 <div class="small" style="margin-top:4px">待实机核实（沿用 v1）：电池属性码、catalog 8 字节布局（评分/保护）、旧路径保护属性、GetStorageInfo、BLE Battery Service、readRemoteRssi 可用性、DeviceVersion/SerialNumber 格式、相机侧改评分后目录反映时机。</div>
 </div>
 <div class="col">
 <h2 style="margin-top:0">开发拆分建议（确认后派发）</h2>
 <table class="dense">
 <tr><th style="width:56px">里程碑</th><th>范围</th><th style="width:60px">风险</th></tr>
 <tr><td>M0 主题</td><td><code>Theme.kt</code>/<code>Color.kt</code> 蓝色亮暗 + 扩展色；删 dynamicColor 参数与 iOS 调用；替换硬编码状态色；<code>PhotoViewerColors</code></td><td>低</td></tr>
 <tr><td>M1 组件</td><td><code>ConnectOverlay</code>、规则化图标/文字按钮、左对齐 TopBar、Ⓘ 行组件</td><td>低</td></tr>
 <tr><td>M2 设备页 + Ⓘ</td><td>新布局；Ⓘ 极简/危险区；设备页返回=断开、进入=自动连接（依 Q3）；v1 M1（timeSync/RSSI/gpsStats）</td><td>中</td></tr>
 <tr><td>M3 Wi-Fi 叠层</td><td>删 <code>WifiRemoteScreen</code> 整页 pre-browser；按 phase 渲染叠层；失败/取消；帮助入口；去断开按钮</td><td>中</td></tr>
 <tr><td>M4 日期网格</td><td>删分页（依 Q6）；视图选项 + 偏好；小/中/大；格式/大小/保护评分；选择态底部按钮；快速滚动条；缩略图 LRU；单测</td><td>中–高</td></tr>
 <tr><td>M5 预览页</td><td>顶栏/底栏改版；下载叠层；按钮文案状态；锁滑动；删边界页与 changingPage</td><td>中</td></tr>
 <tr><td>M6 v1 数据</td><td>PTP：固件/序列号/卡容量/电量；保护/评分（先实机抓包）；日期列表信息条</td><td>中–高</td></tr>
 </table>
 <div class="note" style="margin-top:6px">建议顺序：M0 → M1 → M2 → M3 → M4 → M5 → M6；M0–M3 不依赖协议核实可先行。每个里程碑补单测与 en/zh-rCN 文案；不新增其它语言目录；仍遵守仓库 AGENTS.md 的待办/构建/语言纪律（本方案不入库、不 push）。</div>
 </div>
</div>""")

assert len(pages) == TOTAL, len(pages)
html = (f'<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>alpha-gps 界面改版 UI 设计方案 v2</title>'
        f'<style>{CSS}</style></head><body>{"".join(pages)}</body></html>')
with open(os.path.join(HERE, "index-v2.html"), "w", encoding="utf-8") as f:
    f.write(html)
out = os.path.join(os.path.dirname(HERE), "ui-status-enhancement-v2.pdf")
cmd = ["google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox", "--no-pdf-header-footer",
       "--allow-file-access-from-files", f"--print-to-pdf={out}", "file://" + os.path.join(HERE, "index-v2.html")]
r = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
print(r.returncode, r.stderr[-300:])
print("pages:", len(pages), "pdf:", out, os.path.getsize(out) if os.path.exists(out) else None)
