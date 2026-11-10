"""AlphaGPS UI design v3 — second half of the pages (selection, caching, viewer, settings, help, notifications, dark, semantics, data, diff, open questions)."""
import extras3 as E
from doc3 import phone, wide, n, page, tbl, ul

G = '<span class="tag g">已有</span>'
T = '<span class="tag b">待开发</span>'
V = '<span class="tag y">待实机核实</span>'
Z = '<span class="tag g">零通信</span>'


def build():
    # ------------------------------------------------------------------ 15 selection
    body = f"""
<div class="row">
 {phone("grid_sel", 0.66, "① 选择态 + 悬浮下载胶囊")}{phone("grid_drag", 0.66, "② 拖选中（胶囊收起为小号）")}{phone("grid_dl", 0.66, "③ 批量下载中")}
 <div class="col">
  <h2>选择态与悬浮下载</h2>
  {ul(["<b>进入</b>：长按任一照片。顶栏变为 ✕ + “已选 n 张”；<b>没有“全选”</b>（“全选本页”随分页一并删除）。",
       "<b>选中表现</b>：照片内缩 6dp，露出主色浅底 + 2.5dp 主色描边，左上选择圈变为实心 ✓。未选中的照片左上显示空心圈。",
       "<b>下载胶囊</b>：屏幕底部居中，56dp 高的主色胶囊，写明<b>张数与合计大小</b>（“下载 3 张 · 67.8 MB”）。这是本屏唯一的主操作；点击后<b>直接按设置里的“默认下载格式”下载</b>，不再弹格式选择。",
       "<b>选择跨日期保留</b>：换一个日期继续选，回到列表后一次下载全部已选。",
       "<b>拖选</b>：长按后不抬手拖动，经过的照片连续选中；拖到上下边缘自动滚动。拖选范围 = 整日。"])}
  <div class="box b"><h3>胶囊如何不挡缩略图</h3>
  {ul(["<b>底部内边距</b>：选择态下网格 <code>contentPadding.bottom</code> = 胶囊 56 + 上下间距 32 + 系统导航条高度，最后一行可以完整滚到胶囊上方。",
       "<b>滚动收起</b>：手指向上滑（看更下面的内容）或拖选进行中 → 胶囊收成右下角的<b>小号</b>（图标 + 张数，84dp 宽）；停止 600 ms 或向下滑 → 展开回完整文案。",
       "<b>位置固定</b>：胶囊始终在同一水平线上，只变宽度不变高度，不产生跳动。",
       "胶囊背后有一层自下而上的暗化渐变（非模糊），保证在任何照片上可读。"], "small")}</div>
  <div class="box"><h3>下载中（Q12）</h3>
  {ul(["胶囊变为 G2 玻璃进度条：环形进度 + “正在下载 2 / 3” + 当前文件名与百分比 + 右侧“取消”。",
       "下载中<b>锁定选择与滚动之外的操作</b>：不能增减选择；进度条右侧“取消”随时可用。",
       "完成后显示汇总“<b>已下载 N、跳过 M</b>”，<b>不自动退出选择态</b>（由用户点 ✕ 退出）。已保存的照片在网格上不加 ✓。",
       "默认格式为“仅 JPG”时，没有 JPG 的拍摄组<b>跳过并计入汇总</b>，不中断整批。",
       "退到后台时已接受的下载继续，结束后自动结束 Wi‑Fi（后台最长 30 分钟）；划卡 / 根页返回立即取消（见第 30 页）。"], "small")}</div>
 </div>
</div>"""
    page("选择态与悬浮下载", "悬浮胶囊 · 避让 · 拖选 · 批量下载", body)

    # ------------------------------------------------------------------ 16 no paging / cache
    rows = [["缩略图（网格）", "视口驱动：可见行 ± 两行余量", "缓存上限 300 张", "串行，每批 12 张；滑走的未开始请求丢弃"],
            ["预览图（单张）", "当前 ± 1", "LRU 5 张", "切换时取消更远的请求；ARW 走内嵌 JPEG"],
            ["EXIF / GPS", "仅当前张，停留 ≥ 250 ms 才请求", "结果 LRU 200 条", "失败不自动重试；再次进入该张时重试 1 次"],
            ["直方图", "预览图解码后本地计算", "随预览图缓存", "零通信；256 级亮度，后台线程"],
            ["胶片条", "复用网格缩略图缓存，当前 ± 4", "同缩略图 LRU", "缓存未命中才请求"],
            ["元数据（名称/大小/格式/时间）", "整卡，连接时一次读入", "整个会话常驻内存", "现状已如此，无变化"]]
    aff = [["<code>SonyImageTransfer.kt:456</code> PAGE_SIZE=60；<code>:140,:190,:194-196,:217</code> pagePhotos 限定", "缩略图/预览只应答“当前页”句柄 → 改为应答“当前日期”全部句柄"],
           ["<code>WifiCatalogSnapshot.kt:99</code> <code>page(dateKey, offset)</code>；<code>WifiCameraCapture.kt:26-28</code>；<code>SonyContentCatalog.kt:151-153</code>", "按日期返回整日列表，去掉 offset"],
           ["<code>WifiPhotoBrowser.kt:423</code>、<code>WifiPhotoPager.kt:48</code>、<code>WifiPhotoPaging.kt:7</code>", "删除翻页条与页数计算"],
           ["<code>WifiPhotoViewer.kt:203-206</code> Pager、<code>:111-136</code> 预取、<code>:261-291</code> ‹ › 按钮", "Pager 页数 = 当日总数；删除跨页切换逻辑与 ‹ › 按钮"],
           ["<code>WifiRangeSelection.kt</code>、<code>WifiPhotoTopBar.kt</code> 全选（本页）", "拖选范围 = 整日；删除“全选本页”"],
           ["字符串 <code>wifi_remote_browser_*</code> 分页相关、<code>viewer_previous_photo/next_photo</code>", "删除"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:0 0 760px">
  {wide("cache", E.cache_diagram())}
  <h2>各类数据的加载与回收</h2>
  {tbl(["数据", "加载范围", "保留上限", "规则"], rows, "dense", ["19%", "30%", "17%", "34%"])}
  <div class="note"><b>为什么取消分页是可行的：</b>整卡目录本来就在内存里（<code>CatalogSnapshot.byDate</code>），分页只是界面层的切片（每页 60）。
  真正的成本是缩略图请求——每张一次、串行走同一条相机通道——所以必须按“可见窗口”惰性加载并能取消，而不是一次要整日。</div>
 </div>
 <div class="col">
  <h2>取消日期内分页（Q6 已确认）</h2>
  {ul(["一个日期 = <b>一个连续网格</b>（<code>LazyVerticalGrid</code>）：没有上一页 / 下一页 / 跳页。快速滚动条与时间气泡 0.2.0 未做。",
       "滚动位置按日期记忆；从预览返回时定位到最后查看的照片。",
       "单日照片很多（例：2000 张）时，内存里最多 300 张缩略图位图 + 全部元数据（每条约 200 字节）。",
       "通道优先级：<b>下载 &gt; 当前预览图 &gt; EXIF &gt; 可见缩略图 &gt; 预取</b>。用户发起的永远插队。"])}
  <h3>受影响的现有实现</h3>
  {tbl(["位置", "调整"], aff, "xd", ["52%", "48%"])}
  <div class="box"><h3>回退</h3>若实机发现某机型在连续缩略图请求下不稳定：把每批张数与余量行数调小；界面不变。这两个值是内部常量，不提供设置项。</div>
 </div>
</div>"""
    page("取消分页：缓存与惰性加载", "整日一个网格 · 可见行 ± 两行 · 上限 300", body)

    # ------------------------------------------------------------------ 20 viewer: status row + info strip
    body = f"""
<div class="row">
 {phone("view_land_c", 0.66, "① 横图 3:2（仅 JPG 下载）")}{phone("view_port", 0.66, "② 竖图 2:3（同一布局）")}{phone("view_orig", 0.66, "③ 原始文件下载：大小 = 合计")}{phone("view_sheet", 0.66, "④ 上滑“详细信息”")}
 <div class="col">
  <h2>单张预览：上方状态行，下方信息条</h2>
  <p class="small">第三轮修订：<b>文件大小只出现一处</b>；定位与锁星移到上方；顶栏只剩文件名、时间、序号。横图、竖图同一布局——照片按比例居中铺满，部件半透明叠在上下两端，照片够高就交叠，不够高就落在黑底上。</p>
  {ul([f"{n(1)}<b>顶栏</b>：返回、文件名、下一行拍摄时间、右侧“6 / 227”。<b>不再显示大小。</b>",
       f"{n(2)}<b>状态行</b>（顶栏下方，高 24dp）：<b>定位图标</b>（绿 = 已定位，只表示“有”，坐标在详情里）+ <b>锁与星</b>合并 tag；待实机验证后再加“卡 2”tag。全是信息，不可点。",
       f"{n(3)}<b>信息条</b>（底部半透明玻璃，高 36dp）：<b>格式</b> tag + <b>分辨率</b>，右端<b>文件大小</b>。",
       f"{n(4)}信息条右端 ⌃ 与整条都可<b>上滑 / 点击</b>展开详情（④）。",
       f"{n(5)}<b>胶片条</b>：38dp 半透明缩略图一行，当前张带白框；轻触跳转、横向拖动快速浏览。",
       f"{n(6)}<b>下载</b>：底部居中的胶囊按钮，位置固定。"], "small")}
  <div class="box b"><h3>文件大小的口径（只此一处）</h3>
  {ul(["显示的是<b>按当前下载策略将要下载的大小</b>：仅 JPG → JPG 的大小（①，6.6 MB）；原始文件 → 各文件之和（③，54.6 MB）。数字与点“下载”后实际落盘的量一致。",
       "各格式各自多大，只在<b>详情</b>“文件”区分列：JPG 6.6 MB、RAW 48.0 MB、合计 54.6 MB。",
       "此前顶栏写当前文件大小、信息条写合计，两个数字口径不同——本轮取消顶栏那一处。"], "small")}</div>
  <div class="box small"><b>详情面板</b>（样式沿用）始终包含全部内容：坐标、海拔与 GPS 时间、六项拍摄参数、镜头、直方图、各格式大小、像素尺寸、保护评分、已保存状态；<b>不受设置影响</b>。展开时胶片条隐藏、下载缩为面板右上角圆钮。<br>
  <b>手势</b>：左右滑 = 上 / 下一张 · 单击 = 隐藏 / 显示全部叠加 · 双击 / 捏合 = 缩放 · 上滑信息条 = 详情 · 返回 = 回网格。</div>
 </div>
</div>"""
    page("单张预览", "状态行（定位 · 锁星）· 信息条（格式 · 分辨率 · 大小）· 胶片条 · 下载", body)

    # ------------------------------------------------------------------ 21 viewer: options, states, window shapes
    body = f"""
<div class="row" style="gap:10px">
 {phone("view_params", 0.46, "① 信息条 + 拍摄参数")}{phone("view_full", 0.46, "② 三项全开")}{phone("view_loading", 0.46, "③ 读取中")}{phone("view_fail", 0.46, "④ 读取失败")}
 {phone("view_nogps", 0.46, "⑤ 没有位置")}{phone("view_dl", 0.46, "⑥ 下载中")}{phone("view_saved", 0.46, "⑦ 已保存")}
</div>
<div class="row" style="margin-top:8px">
 <div class="col" style="flex:0 0 700px">{wide("aspect", E.aspect_diagram())}
  <div class="small">断点取自 Android 的窗口尺寸分级（宽 600dp、高 480dp），按<b>窗口</b>而非设备判断，分屏与折叠 / 展开时即时切换；切换只移动部件，不重建页面，当前照片与缩放保持。四个部件 = 状态行、信息条、胶片条、下载。</div>
 </div>
 <div class="col"><div class="box b small"><b>信息条的三个可选项</b>（①②；设置 → 照片 → 预览信息条，第 23 页）：拍摄参数、镜头信息、直方图。<b>默认全关</b>；开启后以紧凑的数值样式加在信息条第二、三行。详情面板不受影响。</div>
 <div class="box"><h3>读取中 / 失败 / 无位置</h3>
 {ul(["<b>读取中</b>（③）：状态行的定位图标为灰色，信息条的分辨率与已开启的参数显示占位条；照片、锁星、格式、大小先行显示。",
      "<b>读取失败</b>（④）：定位图标保持灰色，参数显示“—”。<b>不弹错误、不给重试按钮</b>；再次滑回这张时自动重试一次。",
      "<b>没有位置</b>（⑤）：定位图标换成<b>琥珀色带斜线</b>。与“读取失败”（灰色）严格区分——前者是确定的结论，后者是不知道。",
      "相机不支持按范围读取：定位图标与分辨率不显示，其余照常。"], "small")}</div>
 <div class="box"><h3>下载中 / 已保存（Q12）</h3>
 {ul(["<b>下载中</b>（⑥）：按钮变为进度胶囊（环形进度 + 百分比 + “取消”）；<b>锁定左右滑动</b>，胶片条变暗不可点；返回 = 取消下载。",
      "<b>已保存</b>（⑦）：按钮置灰显示“✓ 已保存”，不可点。",
      "失败时按钮恢复为“下载”，上方一行红字原因 3 秒。大小口径见第 20 页。"], "small")}</div>
 </div>
</div>"""
    page("单张预览：可选项、状态与窗口适配", "信息条可选项 · 读取中 / 失败 / 无位置 · 下载 · 横屏 / 折叠屏 / 近方形", body)

    # ------------------------------------------------------------------ 19 viewer data / EXIF feasibility
    rows = [["① 文件信息：格式、JPG/RAW 各自大小、已保存", "目录元数据 + 本地下载记录", Z, G, "始终可用"],
            ["① 保护、评分", "目录字段（新路径 8 字节，现被跳过）/ 旧路径 ObjectInfo", Z, V, "读不到 → 不显示该 tag"],
            ["② 拍摄参数：光圈/快门/ISO/焦距/镜头/曝光补偿", "EXIF 子目录（ExifIFD）", "约 64 KB / 张", T + " " + V, "“—” + 灰字说明"],
            ["③ 定位状态与坐标、海拔、GPS 时间", "EXIF 的 GPS 子目录（GPS IFD）", "与 ② 同一次请求", T + " " + V, "区分“未含位置”与“不可用”"],
            ["④ 直方图", "由已下载的预览图本地计算", Z, T, "预览图失败则不显示"],
            ["⑤ 像素尺寸、百万像素", "EXIF PixelXDimension/YDimension（或 ARW 主目录宽高）", "与 ② 同一次请求", T + " " + V, "读不到 → 该行隐藏"],
            ["⑥ 胶片条缩略图", "复用网格缩略图缓存", Z + "（未命中才请求）", T, "占位色块"],
            ["⑦ 所在存储卡（卡 2 小标）", "目录句柄里的卡槽号 / 旧路径的存储 ID", Z, T + " " + V, "不显示小标（见第 17 页）"]]
    dep = [["新目录路径（v4，多数新机型）", "<code>SonyContentCatalog.read</code> 操作码 <code>0x923d</code>，64 位偏移（<code>SonyContentCatalog.kt:172-191</code>）", G + " 下载与 ARW 预览已在用"],
           ["旧路径", "<code>GET_PARTIAL_LARGE_OBJECT 0x9211</code>，不支持时退回标准 PTP <code>GetPartialObject 0x101b</code>（32 位偏移）（<code>SonyImageTransfer.kt:430-448</code>）", G + " 下载已在用"],
           ["读文件头的先例", "<code>SonyArwPreview.read</code> 已读取 ARW 前 64 KB 并遍历 TIFF 主目录（<code>SonyArwPreview.kt:42-91</code>）", G],
           ["JPEG 的 EXIF 解析", "<code>SonyJpeg.kt:50-59</code> 已有宽松的 APP1/IFD 遍历，但只取方向；需扩展到 ExifIFD（0x8769）与 GPS IFD（0x8825）", T],
           ["JPG：EXIF 是否在前 64 KB", "APP1 段上限 64 KB 且紧跟文件头，理论上必然在内；Sony 机型的 MakerNote 较大，需确认 GPS IFD 偏移未超出", V],
           ["ARW：子目录是否在前 64 KB", "主目录在头部已验证；ExifIFD / GPS IFD 的实际偏移未验证，超出时需按偏移再读一次", V],
           ["HEIF：EXIF 位置", "EXIF 是 meta/iloc 指向的独立项，不保证在文件头；目前只有本地文件的 <code>HeifDecoder.readExif</code>（<code>AndroidHeifConverter.kt:44-47</code>）", V + " 可能需两次读取；首版可对 HEIF 只显示①④⑥⑦"],
           ["相机返回的预览/缩略图 JPEG 是否自带完整 EXIF", "已知带方向（<code>SonyImageTransfer.kt:206,246</code>）；若同时带 GPS 与曝光参数，则 ②③⑤ 可<b>零额外请求</b>", V + " 优先验证这一条"],
           ["写入 GPS 的坐标与手机发送值是否一致", "相机写入的是它收到的最近一次定位，可能滞后数秒", V + " 只展示照片内的值，不做比对"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1.05">
  <h2>预览页各类信息：来源与成本</h2>
  {tbl(["信息", "来源", "通信成本", "状态", "读不到时"], rows, "dense", ["30%", "27%", "15%", "14%", "14%"])}
  <div class="box b"><h3>读取策略</h3>
  {ul(["<b>仅预览页触发</b>；网格与日期列表不读 EXIF。", "停留 ≥ 250 ms 才发请求，快速连续滑动时不为路过的照片读取。",
       "一次读取文件前 64 KB，解析出 ②③⑤ 全部字段；结果按照片缓存（LRU 200 条），再次查看零通信。",
       "优先级低于下载与预览图；下载进行中暂停 EXIF 读取。",
       "解析采用宽松策略：任何越界/异常字段只让该字段为空，不让整张照片失败（与现有方向解析一致）。",
       "<b>Q21 已确认</b>：进入预览页或横向切换后自动读取；缩略图列表里不读、不显示。"], "small")}</div>
  <div class="note"><b>隐私提示：</b>坐标只在本机显示，不上传、不写日志（日志只记“已定位 / 未含位置 / 读取失败”）。坐标文字可长按复制；不内嵌地图（避免引入联网依赖）。</div>
 </div>
 <div class="col" style="flex:1.2">
  <h2>EXIF / GPS 读取的协议依赖与待实机核实项</h2>
  {tbl(["项", "依据 / 现状", "结论"], dep, "dense", ["22%", "50%", "28%"])}
  <div class="box"><h3>结论</h3>
  {ul(["<b>按范围读文件头在两条协议路径上都已具备且在产品中使用</b>（下载分块、ARW 预览），所以“读前 64 KB”不需要新的协议能力。",
       "<b>不确定的是文件格式侧</b>：各格式的 EXIF/GPS 子目录是否落在前 64 KB、相机回传的预览图是否自带 EXIF。这些必须用真机样张验证后才能定稿读取长度与是否需要二次读取。",
       "因此开发拆分里把“EXIF 读取”单独列为 M8，并以一个<b>核实任务</b>开头：对 JPG / ARW / HEIF 各取样张，记录 ExifIFD、GPS IFD 的偏移与大小。",
       "核实前，界面按“读不到”的降级设计即可完整工作（①④⑥ 不依赖 EXIF）。"], "small")}</div>
 </div>
</div>"""
    page("预览页的数据与可行性", "信息来源 · 通信成本 · EXIF/GPS 协议依赖 · 待实机核实", body)

    # ------------------------------------------------------------------ 20 settings
    body = f"""
<div class="row">
 {phone("settings", 0.74, "① 设置")}{phone("logs", 0.74, "② 日志")}
 <div class="col">
  <h2>设置页</h2>
  {ul(["四组玻璃卡，组内各行之间<b>不画分隔线</b>，靠行高分隔。",
       "<b>位置同步</b>总开关单独成卡，置顶：关闭后不连接任何相机、后台服务停止（现有 <code>isAppEnabled</code> 语义不变）。",
       "<b>连接与定位</b>：位置来源、提示音、电池优化——右侧灰字显示当前值，点击进入子页。",
       "<b>照片传输</b>：<b>默认下载格式</b>（网格下载胶囊与预览页下载都按它执行，不再弹格式选择）+ 三个<b>预览页信息</b>开关：直方图、拍摄参数、镜头信息，<b>默认全关</b>；开启任意一项后进入预览会多读一次照片头部数据。",
       "<b>通用</b>：语言（仅 English / 简体中文）、日志级别、更新日志。<b>崩溃上报一项已删除</b>（第三轮：Sentry 完全移除，同意对话框一并删除，见第 33 页）。",
       "底部一行版本号与包名（信息，不可点；连点 5 次仍可打开调试面板，保持现状）。"])}
  <div class="box b"><h3>相对现状的变化</h3>
  {ul(["<b>删除“Debug: Donation Dialog”调试卡</b>（<code>DebugDonationDialogCard.kt</code>，<code>SettingsScreen.kt:177</code>）。",
       "日志级别、语言由“卡片内直接展开”改为与其它项一致的“行 + 当前值 → 子页/选择对话框”。",
       "子页统一结构：顶部状态卡（图标 + 一句结论 + 说明）+ 操作行；外部跳转用 ↗ 图标。",
       "<b>不新增</b>“玻璃效果/透明度”开关；网格的视图偏好（缩略图大小与标签）只在网格的视图选项里改，不在设置页重复。"], "small")}</div>
  <div class="box"><h3>日志页</h3>
  {ul(["入口：设备清单顶栏、排障指南底部。", "每行左侧的 I / W / E 是信息 tag。<b>筛选胶囊与导出 0.2.0 未做</b>（见第 35 页）。",
       "顶栏右侧两个图标：<b>复制</b>、<b>清空</b>（清空需二次确认）。等宽时间戳 + 级别 + 消息，错误行整行红字。",
       "<b>复制</b> = 把日志以纯文本放入剪贴板，底部提示“已复制 n 条日志”。超过 2000 条只复制<b>最新 2000 条</b>并在提示里说明。不做“复制选中行”。"], "small")}</div>
  <div class="small">现有实现：<code>SettingsScreen.kt:147-178</code>（Overview 顺序）、<code>AppControlsCard.kt</code>、<code>LogLevelSettingsCard.kt</code>、
  <code>SharedLanguageSettingsCard.kt</code>、<code>BatteryOptimizationCard.kt</code>、<code>SharedLogViewerScreen.kt</code>。</div>
 </div>
</div>"""
    page("设置与日志", "分组玻璃卡 · 照片传输分组 · 日志复制与清空", body)

    # ------------------------------------------------------------------ 21 help
    keep = [["设备清单顶栏“帮助”图标", "<code>DevicesScreen.kt:95-121</code>", '<span class="ok">保留（唯一入口）</span>'],
            ["设备页 → Ⓘ 内“帮助”文字按钮", "<code>DeviceInfoDialogs.kt:91,153-154</code>；<code>CameraDeviceManager.kt:366</code>", '<span class="todo">移除</span>'],
            ["连接失败时的帮助对话框", "<code>WifiConnectionHelp.kt:23-52</code>；<code>WifiRemoteScreen.kt:739</code>", '<span class="todo">移除</span>（权限场景改为叠层内“去授权”）'],
            ["设备清单底部“需要帮助？”", "<code>DeviceListLayout.kt:26,45</code>", '<span class="todo">移除</span>（与顶栏重复）'],
            ["错误行上的“打开指南”", "<code>SharedDeviceList.kt:382-394</code>", '<span class="todo">移除</span>（原因直接写在行内）'],
            ["配对准备页底部引导链接", "<code>SharedPairingPreparationScreen.kt:56,107-113</code>", '<span class="ok">保留为流程内引导</span>（Q22 已确认）'],
            ["配对失败对话框“打开指南”", "<code>CameraDeviceManager.kt:381-422</code>", '<span class="warn">改为“重试 / 取消”</span>，正文写清原因（Q22 已确认）']]
    body = f"""
<div class="row">
 {phone("help_c", 0.74, "① 帮助")}{phone("trouble", 0.74, "② 排障指南")}
 <div class="col">
  <h2>帮助页</h2>
  {ul(["<b>唯一入口：设备清单顶栏的帮助图标</b>（Q11）。",
       f"{n(3)}顶部<b>“关于”入口卡</b>（主色浅底玻璃）→ 关于页（下一页）；其下“排障指南”入口卡 → 常见问题（可展开的玻璃行，默认展开第一条）。",
       f"{n(1)}排障指南：按“相机端 / 手机端 / 仍然不行”三步清单，末尾“查看日志”。",
       f"{n(2)}第一条问答里的链接只有两个：<b>代码仓库（GitHub）、反馈问题（GitHub Issues）</b>。不再有邮箱；<b>赞助链接不在帮助页</b>，全应用只在关于页“支持上游作者”一处（Q28）。"])}
  <div class="box b"><h3>赞助提醒：全面移除自动触发，仅保留手动链接</h3>
  {ul(["删除：安装 30 天后的自动弹窗、“下次启动强制弹出”的调试路径、设置页调试卡。",
       "保留唯一一处手动链接：关于页“支持上游作者”，标明受益人是 Saschl。帮助页原有的链接（<code>HelpScreen.kt:274-287</code>）移到关于页。",
       "<b>Tip jar</b>：Android 从未有界面入口；原先只被 iOS 代码引用，iOS 目标已整体删除，<code>tip_jar*</code> 文案随文案整理一并删除。",
       "涉及文件与行号见第 32 页。"], "small")}</div>
  <h3>帮助 / 引导入口清单（现状 → v3）</h3>
  {tbl(["现有入口", "位置", "v3"], keep, "xd", ["28%", "40%", "32%"])}
  <div class="small">“去授权”“系统电池设置”这类按钮是完成任务所必需的操作，不属于帮助入口，按需保留。</div>
 </div>
</div>"""
    page("帮助与排障", "唯一入口 · 关于入口 · 赞助只留关于页一处 · 入口清单", body)


    # ------------------------------------------------------------------ about + licences
    lic = [["本应用 AlphaGPS", "GPL‑3.0（与上游相同）", "仓库根 <code>LICENSE</code>", '<span class="ok">无冲突</span>'],
           ["libheif 1.23.5 / libde265 1.1.3", "LGPL‑3.0‑or‑later", "子模块源码构建、独立 .so；声明已随包 <code>heif/src/main/assets/licenses/</code>", '<span class="ok">兼容</span>'],
           ["Kotlin、kotlinx、AndroidX / Compose、Room、Material、Accompanist", "Apache‑2.0", "<code>gradle/libs.versions.toml</code>", '<span class="ok">兼容</span>'],
           ["应用内功能图标（Material Icons / Material Symbols）", "Apache‑2.0", "抽查 <code>ic_gps_fixed.xml</code>、<code>welcome_ready_24px.xml</code> 与官方图标同形（父会话盘点）", '<span class="ok">在许可页补来源声明</span>'],
           ["Haze（待引入）", "Apache‑2.0（按已知信息）", "引入时以其仓库 LICENSE 为准", '<span class="warn">引入时核对</span>'],
           ["Timber、com.diamondedge:logging", "各自许可", "未逐一核对许可文本", '<span class="warn">开发时核对</span>'],
           ["Sentry SDK、应用内评价组件", "—", "Sentry 完全移除；不做任何应用市场分发，单一构建", '<span class="ok">已移除</span>'],
           ["应用图标", "GPL‑3.0，自绘", "<code>docs/brand/alpha-gps-icon.svg</code>（提交 1c6c7be）", '<span class="ok">已替换上游图标</span>'],
           ["上游素材与第三方徽标", "见第 27 页", "逐项清单与处置建议", '<span class="warn">待用户确认</span>']]
    body = f"""
<div class="row">
 {phone("about_c", 0.74, "① 关于")}{phone("licenses", 0.74, "② 开源组件许可")}
 <div class="col">
  <h2>关于页</h2>
  {ul([f"入口：帮助页顶部“关于 AlphaGPS”卡。顶部是新图标与规范名称 <b>AlphaGPS</b>。{n(1)}<b>上游项目</b>：github.com/Saschl/alpha-gps，作者 Saschl；本应用基于它修改。",
       f"{n(2)}<b>修改声明</b>（GPLv3 第 5 条）：由 mfreceiver 修改，自 2026 年 11 月起。{n(3)}<b>许可证</b>：GPL‑3.0，与上游相同；点击查看全文。",
       f"{n(4)}<b>源码与反馈</b>只写 GitHub 一处：github.com/mfreceiver/alpha-gps，问题反馈为其 Issues（/issues）。<b>应用与代码里不出现任何私有部署仓库的地址</b>。",
       f"{n(5)}<b>隐私说明</b> → 第 26 页。{n(6)}<b>支持上游作者</b>：Buy Me a Coffee，标明受益人是 Saschl；<b>全应用仅此一处</b>（Q28）。",
       f"{n(7)}每个地址行右侧两个图标：<b>复制</b>（提示“已复制”）与<b>外部打开</b>；行本身不可点，避免误触跳出应用。",
       "页脚：商标免责（与 Sony 无关联，Sony、Alpha（α）为其所有者的商标）+ <b>图标素材来源说明</b>。",
       "开源组件许可页（②）：逐组件一行（含 Material Icons / Symbols、Haze），点开看<b>随包的 Apache‑2.0 / GPL‑3.0 / LGPL‑3.0 全文</b>；未逐库附 NOTICE（见第 35 页）。无 Sentry。"], "small")}
  <h3>许可核查结论</h3>
  {tbl(["对象", "许可", "依据", "结论"], lic, "xd", ["27%", "17%", "34%", "22%"])}
  <div class="box b small"><b>GPL‑3.0 下的义务与现状</b>
  {ul(["保留上游 LICENSE 与版权声明：已保留。", "标明已修改及日期：<b>已做</b>（关于页 + README：mfreceiver 自 2026‑11 起修改）。",
       "分发时提供对应源码：GitHub 公开仓库满足；发布 tag 须与 APK 对应。", "不附加额外限制条款。"], "xs")}</div>
 </div>
</div>"""
    page("关于与许可", "AlphaGPS · 来源 · 修改声明 · GPL‑3.0 · 源码地址 · 许可核查", body)

    # ------------------------------------------------------------------ 26 privacy + docs rewrite
    pv = [["发布者", "mfreceiver（AlphaGPS）。源码：github.com/mfreceiver/alpha-gps（仅此一处）。"],
          ["一句话结论", "AlphaGPS 不收集、不上传任何数据。没有账号、统计、崩溃上报、广告，也没有向第三方发送数据的 SDK。"],
          ["位置", "仅在你授权后读取手机定位，只通过蓝牙写入<b>你自己的相机</b>；不上传、不留历史。"],
          ["蓝牙与附近设备", "用于发现、配对并连接你的相机；不扫描或记录其它设备。"],
          ["相机 Wi‑Fi 与本地网络", "只用于连接相机自身的热点浏览和下载照片；照片保存到本机相册，不上传。"],
          ["通知", "显示连接与位置同步状态。"],
          ["日志", "只保存在本机；复制后是否分享由你自行决定。<b>定稿前核对</b>现有日志是否记录坐标：若记录，改为不记录或在此如实写明。"],
          ["权限一览", "定位（含后台）、蓝牙 / 附近设备、附近 Wi‑Fi 设备 / 本地网络、通知——逐项写明用途与“拒绝后的影响”。"],
          ["数据保留与删除", "应用数据都在本机；移除相机或卸载应用即清除。已下载的照片属于你的相册。"],
          ["联系", "GitHub Issues：github.com/mfreceiver/alpha-gps/issues；<b>不留个人邮箱</b>（Q27）。"],
          ["变更", "本文随版本更新，改动记在仓库提交历史里。"]]
    rd = [["名称与定位", "<b>AlphaGPS</b>：把手机位置写入 Sony 相机的照片，并通过相机 Wi‑Fi 浏览、下载照片。"],
          ["删除", "遥控拍摄、Sentry、商店徽章与链接（Google Play / F‑Droid / App Store / Obtainium）、外链截图、赞助图片、website 引用。"],
          ["保留并改名", "构建、签名与发版说明（包名 <code>cn.vectory.alphagps</code>、仓库名 <code>alphagps</code> 保持小写）。"],
          ["致谢与来源", "fork 自 Saschl/alpha-gps，GPL‑3.0；修改者 mfreceiver，自 2026‑11。保留原致谢：anoulis/sony_camera_bluetooth_external_gps、mlapaglia/AlphaSync。"],
          ["新增", "商标声明；隐私说明链接；系统要求 Android 12（API 31）及以上、仅 arm64；仅 English / 简体中文；单一构建。"],
          ["赞助", "README 不放赞助链接；“支持上游作者”只在应用的关于页。"]]
    body = f"""
<div class="row">
 {phone("privacy", 0.74, "① 关于 → 隐私说明")}
 <div class="col" style="flex:1.25">
  <h2>隐私说明：全部重写（不是改名换姓）</h2>
  <p class="small">前提是第三轮定案 <b>Sentry 完全移除</b>：移除之后才能如实写“无崩溃上报”。所以 <code>privacy.md</code> 的定稿与应用内页面要<b>在 W2“移除 Sentry”合入之后</b>发布。</p>
  {tbl(["小节", "新版 privacy.md 草稿要点"], pv, "xd", ["20%", "80%"])}
  <div class="box b small"><b>删除的上游模板内容</b>：服务提供者 Saschl 与其邮箱（<code>privacy.md:4,97</code>）、EU Sentry 段、Google Play services 同意段、儿童条款模板、生成器署名等。<br>
  <b>应用内页面</b>（①）：顶部绿色结论卡 + 五项用途 + 发布者与联系；是 privacy.md 的摘要，全文随包内置、离线可读。欢迎页底部“继续即表示同意隐私说明”指向此页。</div>
 </div>
 <div class="col">
  <h2>README 重写提纲</h2>
  {tbl(["部分", "内容"], rd, "xd", ["24%", "76%"])}
  <div class="box small"><b>名称规范</b>：产品名一律 <b>AlphaGPS</b>（无连字符）。保持小写的只有包名 <code>cn.vectory.alphagps</code>、仓库名 <code>alphagps</code>、文件名 <code>alpha-gps-*</code>；上游仓库名 Saschl/alpha-gps 与 GitHub 分叉 mfreceiver/alpha-gps 是地址，原样书写。</div>
  <div class="note small"><b>同步清理</b>：<code>docs/wifi-architecture.md:87-89</code> 的旧系统路径描述；strings 里的 <code>sentry_*</code>、<code>tip_jar*</code>、遥控拍摄引导等过时文案（英文与简体中文两处同步）；FAQ 里提到崩溃报告与邮箱的答案。</div>
 </div>
</div>"""
    page("隐私说明与文档重写", "privacy.md 草稿要点 · 关于 → 隐私页 · README 提纲 · 名称规范", body)

    # ------------------------------------------------------------------ 27 asset inventory
    A_ = '<span class="tag g">A 保留</span>'; B_ = '<span class="tag b">B 重绘</span>'; C_ = '<span class="tag r">C 删除</span>'
    assets = [[C_, "Apple App Store 徽章", "仓库根 <code>Download_on_the_App_Store_Badge_*.svg</code>", "外部品牌素材；不做应用市场分发"],
              [C_, "F‑Droid 徽章、Sentry 标志", "<code>website/public/branding/</code>", "F‑Droid 徽章为 CC‑BY‑SA‑3.0 外部素材；Sentry 已移除"],
              [C_, "README 的商店徽章、外链截图、Buy Me a Coffee 外链图", "<code>README.md</code>", "不做市场分发；赞助只留关于页一处"],
              [C_, "<code>website/</code> 整个目录", "含 <code>icon.svg</code>、<code>og-image.svg</code>", "上游宣传站，下载链接全指向上游商店"],
              [C_, "<code>fastlane/</code> 整个目录", "上游截图、图标、旧更新说明", "商店元数据，本项目不用"],
              [C_, "<code>.github/FUNDING.yml</code>", "指向上游赞助账号", "避免分叉仓库显示上游赞助按钮"],
              [B_, "根目录 <code>icon.svg</code>", "上游图标", "用 <code>docs/brand/alpha-gps-icon.svg</code> 取代"],
              [B_, "启动图标前景 / 背景", "<code>drawable/ic_launcher_foreground.xml</code>、<code>ic_launcher_background.xml</code>（上游 2025‑08）", "由新图标三层（前景 / 背景 / 单色）转换取代，见 W2"],
              [C_, "iOS AppIcon <code>icon.png</code>", "与上游 fastlane 图标同源", "iOS 目标已整体删除，随之删除"],
              [A_, "应用内功能图标", "<code>app/res/drawable</code> 13 个 + <code>sharednew composeResources/drawable</code> 22 个", "与 Material Icons / Symbols 同形，Apache‑2.0；在许可页补来源声明，不重绘"],
              [C_, "iOS <code>AccessoryCamera/camera.svg</code>", "上游自绘的通用相机图形", "iOS 目标已整体删除，随之删除"],
              [A_, "<code>docs/design</code> 全部、<code>docs/brand</code> 全部", "项目自绘", "保留"],
              [A_, "heif 子模块内 <code>libde265.png</code>、<code>shopify.svg</code>", "子模块自带的文档素材", "不打进 APK，原样不动"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1.75">
  <h2>素材来源清单与处置 <span class="tag y">待用户确认</span></h2>
  {tbl(["处置", "素材", "位置 / 来源", "理由"], assets, "dense", ["8%", "27%", "33%", "32%"])}
  <div class="small">事实依据来自父会话的仓库盘点（首提交作者、文件内许可声明、图形比对）；我自己核对到的是：<code>icon.svg</code>、fastlane、<code>website/</code> 内都没有独立的许可或作者声明，仓库根只有 GPL‑3.0 的 <code>LICENSE</code>。</div>
 </div>
 <div class="col">
  <div class="box b"><h3>判断原则</h3>
  {ul(["<b>优先自生成</b>（Q26）：凡是代表品牌的图形都换成自绘。", "<b>外部品牌素材一律删除</b>：它们受各自品牌条款约束，且本项目不做应用市场分发，没有使用理由。",
       "<b>上游自绘素材</b>文件内没有独立许可声明，默认随仓库 GPL‑3.0，法律上可保留；为避免混淆来源，品牌类仍重绘。",
       "<b>通用功能图标</b>属 Apache‑2.0 的 Material 图标，按“来源声明”处理即可。"], "small")}</div>
  <div class="note"><b>请逐项确认</b>
  {ul(["C 类六项是否全部删除（尤其 <code>fastlane/</code> 与 <code>website/</code> 整目录）。", "B 类按新图标替换（iOS 目标已删除，其图标不再处理）。",
       "A 类功能图标是否接受“只补来源声明、不重绘”。若要求全部自绘，另开一项，约 35 个图标。"], "small")}
  确认后归入 <b>W1 文档与素材线</b>（第 34 页）执行；确认前不动任何文件。</div>
  <div class="box small"><b>上游原文案</b>不在此表：README、<code>privacy.md</code>、strings 里的长文案按第 26 页重写，不是素材处置问题。</div>
 </div>
</div>"""
    page("素材来源清单与处置", "A 保留 · B 重绘 · C 删除 · 待用户确认", body)

    # ------------------------------------------------------------------ 22 notifications
    rows = [["待命（静默）", "等待相机", "相机不在附近，回到范围后自动连接", "低 · 无声", "灰色图标；“保持活跃”开且无连接时"],
            ["同步中 · 单台（静默）", "Alpha 7 IV · 位置同步中", "已连接，正在向相机写入位置", "低 · 无声", "标题带相机名，一眼可辨"],
            ["同步中 · 多台（静默）", "2 台相机 · 位置同步中", "Alpha 7 IV、ZV-E10 II", "低 · 无声", "正文列出名称"],
            ["已断开（提醒）", "相机已断开", "Alpha 7 IV · 14:02 断开，位置写入已停止", "默认 · 有声（可在提示音设置里关）", "<b>用户主动断开时不发</b>；可滑走"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:0 0 430px">{wide("notif_l", E_notif(False))}</div>
 <div class="col" style="flex:0 0 430px">{wide("notif_d", E_notif(True))}</div>
 <div class="col">
  <h2>通知栏</h2>
  <div class="note">通知由系统绘制，<b>应用无法给它套玻璃样式</b>。可控的只有：小图标、强调色、标题、正文、渠道重要性、是否可滑走。</div>
  {ul(["<b>强调色</b>：统一设为主色（亮 <code>#0B57D0</code> / 暗由系统自动提亮），取代目前的默认灰。",
       "<b>小图标</b>：沿用定位图标；待命用“同步”图标、断开用“蓝牙断开”图标，三种状态一眼可分。",
       "<b>点击</b>：打开应用；只有一台相机在同步时直接进入其设备页。",
       "<b>不加操作按钮</b>：不在通知上放“断开”——断开蓝牙是罕用且有代价的操作，只在设备页卡片上、且需二次确认。",
       "<b>正文不放实时数字</b>（写入次数、秒数）：避免每几秒刷新通知带来的耗电与抖动。实时状态在设备页看。"])}
 </div>
</div>
<div class="row" style="margin-top:8px">
 <div class="col" style="flex:1.4">
  <h2>四种通知</h2>
  {tbl(["类型", "标题", "正文", "渠道", "说明"], rows, "dense", ["17%", "20%", "28%", "17%", "18%"])}
 </div>
 <div class="col">
  <div class="box b"><h3>与后台常驻语义的关系</h3>
  {ul(["离开应用后蓝牙与位置写入继续，<b>常驻通知是用户知道“它还在工作”的唯一途径</b>，所以标题必须带相机名与“位置同步中”。",
       "同步中的常驻通知由“传输”渠道（高重要性、有声）改到<b>静默</b>渠道：它是状态而非事件；声音提示继续由提示音设置负责。此项改变渠道行为（Q25 已确认），列入差异清单。",
       "从最近任务划掉应用后通知仍在；用户在设备页确认“断开蓝牙”后，该相机从通知中消失，全部断开且未开“保持活跃”时通知撤销。"], "small")}</div>
  <div class="small">现状：<code>NotificationsHelper.kt:31-66</code> 三个渠道（待命 低 / 传输 高+声 / 断开 默认+声）；构建函数 <code>:76-168</code>，均无操作按钮、未设置强调色。</div>
 </div>
</div>"""
    page("通知栏", "系统绘制 · 强调色与文案 · 不加操作按钮", body)

    # ------------------------------------------------------------------ 23 dark
    body = f"""
<div class="row" style="gap:12px;justify-content:space-between">
 {phone("d_list", 0.54, "设备清单")}{phone("d_dev_ok", 0.54, "设备页")}{phone("d_ov", 0.54, "连接叠层")}{phone("d_dates", 0.54, "日期列表")}
 {phone("d_grid_panel", 0.54, "视图选项")}{phone("d_grid_sel", 0.54, "选择态")}
</div>
<div class="row" style="margin-top:8px;gap:12px">
 {phone("d_info", 0.42)}{phone("d_dlg_bt", 0.42)}{phone("d_settings", 0.42)}{phone("d_help", 0.42)}
 <div class="col">
  <h2>暗色下的玻璃</h2>
  {ul(["<b>层级表达反过来</b>：亮色靠阴影与“更白”，暗色靠<b>更亮的填充与更亮的顶部描边</b>。G1 = 白 7%，G2 = <code>#20252B</code> 74%，G3 = <code>#272C33</code> 86%。",
       "背景基色 <code>#0E1217</code>，三处光斑降到 26–42% 不透明度：能看出蓝紫青的气氛，但不干扰文字。",
       "主色换为 <code>#A8C7FA</code>，主按钮上的文字为深蓝 <code>#062E6F</code>；绿、琥珀、红均换为暗色版本（第 6 页）。",
       "遮罩加深到 52%，保证对话框从暗背景中分离出来。",
       "照片上的信息 tag、预览页的全部面板在两种主题下<b>完全相同</b>（都是深色），因此预览页不单独列暗色稿。",
       "跟随系统深浅色切换，不提供应用内主题开关（现状如此，不变）。"])}
  <div class="note">对比度实测见第 5 页：暗色 G1 上正文、次要文字、主色、对话框内错误色均 ≥ AA。</div>
 </div>
</div>"""
    page("暗色对照", "同一套布局 · 层级改用亮度与描边表达", body)

    # ------------------------------------------------------------------ 24 semantics
    sem = [["Home / 锁屏 / 切到其它应用", "保持", "保持（旧：一直保持）", "保持", "<b>无下载→立即结束</b>；有已接受的下载→保留到下载结束后自动结束（后台最长 30 分钟）", '<span class="tag g">0.2.0 已实现</span>'],
           ["根页（设备清单）返回", "<b>断开</b>并停止服务", "结束", "<b>保持</b>（服务续跑）", "<b>立即结束</b>（下载中也取消）", '<span class="tag g">0.2.0 已实现</span>'],
           ["从最近任务划掉", "<b>断开</b>并停止服务", "结束", "<b>保持</b>（前台服务续跑）", "<b>立即结束</b>（下载中也取消）", '<span class="tag g">0.2.0 已实现</span>'],
           ["Activity 被销毁（isFinishing）", "<b>断开</b>并停止服务", "结束", "<b>保持</b>", "结束", '<span class="tag g">0.2.0 已实现</span>'],
           ["设备页返回 → 设备清单", "保持", "—", "保持", "—", '<span class="tag z">无改动</span>'],
           ["日期列表返回 → 设备页", "保持", "结束", "保持", "结束（保蓝牙）", '<span class="tag z">行为不变</span>'],
           ["设备页“断开蓝牙”并确认", "断开（原为页面上的按钮）", "结束", "断开，记为用户主动断开", "结束", '<span class="tag b">入口改为小图标 + 确认</span>'],
           ["Ⓘ “移除相机”并确认", "断开并解除配对", "结束", "同左", "同左", '<span class="tag z">无改动</span>'],
           ["设置“位置同步”总开关关闭", "全部断开、服务停止", "结束", "同左", "同左", '<span class="tag z">无改动</span>'],
           ["手机重启", "自动重连已启用的相机", "—", "同左；用户主动断开的不重连", "—", '<span class="tag z">无改动</span>']]
    auto = [["此前处于连接状态（含离开应用期间一直连着）", "保持 / 断线后自动重连"],
            ["从未连接过（刚配对完成除外）", "不自动连；等用户点“连接”或“照片传输”"],
            ["用户上次在设备页主动断开", "<b>不自动连</b>，包括重新打开应用、手机重启、相机回到范围"],
            ["用户点“照片传输”而蓝牙未连", "自动先连蓝牙 → 启动 Wi‑Fi → 读取目录；视为用户主动触发，此后恢复自动连接"]]
    body = f"""
{wide("nav", E.nav_diagram(), 1.0)}
<div class="row" style="margin-top:8px">
 <div class="col" style="flex:1.75">
  <h2>后台与退出语义：逐项对照（“现状”列 = v0.1.5 旧实现）</h2>
  {tbl(["用户动作", "现状 · 蓝牙+GPS", "现状 · Wi‑Fi", "v3 · 蓝牙+GPS", "v3 · Wi‑Fi", "改动"], sem, "xd", ["19%", "14%", "8%", "18%", "19%", "22%"])}
 </div>
 <div class="col">
  <div class="box b"><h3>一句话定义</h3><b>蓝牙 + GPS 常驻：Home、锁屏、切应用、划卡、根页返回都不断开；只有用户在设备页主动“断开”并确认才断蓝牙。
  Wi‑Fi 照片传输只在应用可见时保留：退后台无下载即结束，有已接受的下载（单张 / 批量）保留到下载结束（后台最长 30 分钟）；划卡与根页返回立即结束，即使下载中。</b>
  <div class="xs">缩略图、预览、结果汇总不算下载；回前台只取消“待结束”，不会自动重开已结束的会话。</div></div>
  <h3>蓝牙自动连接（Q5）</h3>
  {tbl(["情形", "行为"], auto, "xd", ["46%", "54%"])}
  <div class="small"><b>0.2.0 已按此实现</b>（用户最终决定，取代此前“离开前台延时结束”的方案）；生命周期与真机限制见 <code>docs/wifi-architecture.md</code>。</div>
 </div>
</div>"""
    page("导航与断开语义", "蓝牙 / Wi‑Fi / 离开应用 · 后台常驻语义", body)

    # ------------------------------------------------------------------ 25 data sources
    rows = [["相机时间（校时）", "设备页连接卡状态行的副行", "蓝牙：校时写入的应答（仅 applied=1 视为已校正）", "每次蓝牙连接后 1 次", "未校正=琥珀；未知=“—”", T],
            ["信号强度", "设备页位置卡三格", "蓝牙 RSSI", "连接后首读 + 每 5 s（仅设备页可见时）", "“—”", T],
            ["电量", "设备页连接卡（信息 tag）", "Wi‑Fi 会话就绪后读 1 次，缓存", "每次照片传输会话", "≤30 分钟只显示数值；30 分钟–24 小时变灰并加一行缓存时间；更久“—”", T + " " + V],
            ["GPS 定位状态、手机经纬度与误差", "<b>0.2.0 不显示</b>（见第 35 页）", "手机定位服务（已有的位置来源）", "—", "—", '<span class="tag z">未显示</span>'],
            ["最近写入时间", "设备页位置卡三格", "蓝牙写入结果", "每次写入（界面每秒刷新相对时间）", "未连接时显示上次写入时间", T],
            ["已写入次数（失败次数不显示）", "设备页位置卡三格", "本机计数（会话内）", "每次写入", "未连接时保留上次计数、变灰", T],
            ["蓝牙地址（型号不显示）", "Ⓘ", "配对记录", "打开 Ⓘ 时", "—", G],
            ["固件、序列号", "<b>0.2.0 不显示</b>（Ⓘ 与日期页均无）", "Wi‑Fi：设备信息", "—", "—", '<span class="tag z">未显示</span>'],
            ["卡容量 / 剩余", "<b>0.2.0 不显示</b>（日期页无容量）", "Wi‑Fi：每张卡各一份存储信息", "—", "—", '<span class="tag z">未显示</span>'],
            ["照片所在存储卡（待实机验证后落地）", "网格、预览状态行的“卡 2”小标", "目录句柄卡槽号 / 旧路径存储 ID", "随目录", "读不到或只有一张卡有内容=不显示", T + " " + V],
            ["蓝牙配对状态", "Ⓘ", "系统配对记录", "打开 Ⓘ 时", "—", G],
            ["照片目录（名称/大小/格式/时间）", "日期列表、网格、预览", "Wi‑Fi：整卡目录", "连接时 + 手动刷新", "读取中断页", G],
            ["保护、评分", "网格 tag、预览状态行", "目录字段 / 旧路径对象属性", "随目录", "读不到=不显示，开关置灰", T + " " + V],
            ["缩略图", "网格、胶片条（日期封面未做）", "Wi‑Fi：缩略图请求", "可见行 ± 两行，串行 12 张/批", "占位色块", G],
            ["预览图", "预览页", "Wi‑Fi：预览请求 / ARW 内嵌 JPEG", "当前 ± 1", "缩略图放大 + “预览不可用”", G],
            ["拍摄参数、定位、像素尺寸", "预览状态行 / 信息条 / 详情", "Wi‑Fi：文件前 64 KB 的 EXIF", "仅当前张，停留 250 ms 后", "“—” / “不可用”，区分“未含位置”", T + " " + V],
            ["直方图", "预览页", "本地由预览图计算", "预览图就绪后", "不显示", T + " " + Z],
            ["文件大小（按下载策略）", "预览信息条（唯一一处）", "目录元数据 + 下载格式偏好", "随目录", "—", G],
            ["已保存状态", "预览页", "本机下载记录", "下载完成时", "—", G],
            ["视图偏好（档位、三个标签开关）", "网格", "本机偏好存储", "修改即存，全局生效", "默认：小、全关", T],
            ["预览页信息的三个开关 + 默认下载格式", "设置 → 照片传输", "本机偏好存储", "修改即存", "默认全关", T]]
    body = f"""
<div class="row">
 <div class="col">
  {tbl(["数据", "显示位置", "来源", "刷新时机", "回退显示", "状态"], rows, "dense", ["17%", "12%", "23%", "18%", "19%", "11%"])}
  <div class="row" style="margin-top:6px">
   <div class="col"><div class="box"><h3>图例</h3>{G} 现有代码已提供 &nbsp; {T} 需新增 &nbsp; {V} 依赖相机行为，须真机确认 &nbsp; {Z} 不产生相机通信</div></div>
   <div class="col" style="flex:2"><div class="note">v1 数据层规格（<code>docs/design/history/ui-status-enhancement/SPEC.md</code>）中的字段定义、阈值与写入位置继续有效：
   校时状态、RSSI 读取、GPS 统计、电量缓存、设备信息、存储信息、保护评分。本版新增：EXIF/GPS、直方图、胶片条复用、视图偏好、存储卡归属、信息条可选项。</div></div>
  </div>
 </div>
</div>"""
    page("数据来源与刷新", "每一项状态从哪来、何时刷新、读不到时显示什么", body)

    # ------------------------------------------------------------------ 26 diff
    d1 = [["<b>离开应用不再断开蓝牙</b><br>（根页返回 / 划卡 / Activity 销毁）",
           "<code>MainActivity.kt:73-86</code> onDestroy(isFinishing) → disconnectAllOnExit + stopService<br><code>MainActivity.kt:124-138</code> 根页返回 finishAndRemoveTask()<br>"
           "<code>LocationSenderService.kt:170-181</code> onTaskRemoved → disconnectAllOnExit + stopSelf<br><code>AppServices.kt:76-80,89-91</code>；<code>AppExitCoordinator.kt:21-33</code>；<code>AppExitGate.kt:121-137</code>",
           "三个入口都改为<b>只结束 Wi‑Fi</b>（下载中也立即取消），去掉“停会话 / 断蓝牙 / 停服务”；不加任何蓝牙或前台服务关闭回调。"
           "<code>AppExitGate</code> 的属主 / 代际与退休快照反例保留。"],
          ["重新打开应用时的自动重连", "<code>MainActivity.kt:164-189</code>（注释：退出会断开，所以打开要重连）", "保留作为兜底，但只针对“已启用且非用户主动断开”的相机；注释与测试同步更新。"],
          ["<b>保持不变</b>的路径", "Home/后台（<code>LocationSenderService.kt:164-169</code>）；断开单台 <code>ServiceCommandRouter</code> / <code>DeviceDisconnectCoordinator</code>；移除 <code>DeviceRemovalCoordinator</code>；"
           "开机重连 <code>ServiceShutdownCoordinator.kt:28-48</code>；服务真正销毁 <code>LocationSenderService.kt:184-201</code>；总开关 <code>isAppEnabled</code>", "不改。"],
          ["退后台时的 Wi‑Fi", "<code>AndroidWifiRemoteScreen.kt:87-96</code>（旧：后台保持 Wi‑Fi，离开该页才结束）", "退后台<b>无下载→立即结束</b>；有已接受的下载→保留到下载结束后自动结束（后台最长 30 分钟）；根页返回与划卡立即结束（Q17 最终决定）。"],
          ["“保持活跃”语义", "<code>ServicePresenceCoordinator.kt:47-67</code>；<code>ConnectionDemandGate.kt:20-24</code>；<code>LocationSenderService.kt:77-109</code>", "逻辑不变。0.2.0 沿用名称“保持活跃”、默认关（未按 Q18 改名，见第 35 页）。"]]
    d2 = [["<b>移除自动赞助提醒</b>", "Android：<code>CameraDeviceManager.kt:45-48,82-83,143,178-214,252,424-449</code>；<code>MainActivity.kt:200-204,275-278</code>；"
           "<code>PreferencesManager.kt:23-26,148-195</code>；<code>DebugDonationDialogCard.kt</code>（整文件）；<code>SettingsScreen.kt:177</code><br>"
           "字符串 <code>donation_dialog_*</code>（en + zh-rCN）", "全部删除。<code>HelpScreen.kt:274-287</code> 的手动链接移到关于页“支持上游作者”，全应用仅此一处。"],
          ["<b>帮助入口收敛</b>", "见第 24 页清单（<code>DeviceInfoDialogs.kt:91,153-154</code>、<code>WifiConnectionHelp.kt</code>、<code>DeviceListLayout.kt:26,45</code>、<code>SharedDeviceList.kt:382-394</code> 等）", "只留设备清单顶栏入口。"],
          ["<b>取消日期内分页</b>", "见第 19 页清单（<code>SonyImageTransfer.kt:456</code> 等）", "整日一个网格 + 视口驱动加载 + 缓存上限 300。"],
          ["<b>主题蓝色化 + 玻璃</b>", "<code>Theme.kt:36,43-46</code>；<code>Color.kt</code>；<code>DeviceDetailScreen.kt:245-246</code>；<code>TransmissionDot.kt:36,62</code>", "固定蓝色色板；新增 <code>Glass.kt</code> 与 <code>GlassSurface</code>；引入 Haze（<code>gradle/libs.versions.toml</code> 新依赖）；minSdk 31，无降级分支；不启用动态色。"],
          ["设备页重排与断开入口", "<code>ui/device/DeviceDetailScreen.kt</code>（shared 306 行）；<code>DeviceDetailViewModel.disconnect</code>", "连接卡（状态行 + 右侧固定槽位：断开小图标 + 确认 / 取消 / 连接）+ 位置卡（一行三格）+ 底部主入口“照片传输”。"],
          ["Ⓘ 精简", "<code>DeviceInfoDialogs.kt</code>（222 行）", "✕ 关闭、✎ 重命名、去掉帮助与 OK、<b>去掉 MTU</b>、<b>不放卡容量、固件、序列号</b>、底部红色描边“移除相机”。"],
          ["设备清单", "<code>SharedDeviceList.kt:251-310</code> 左滑删除；<code>:314-416</code> DeviceCard", "去掉左滑删除；玻璃行；“添加相机”悬浮主按钮。"],
          ["连接过程改叠层", "<code>WifiRemoteScreen.kt</code>（757 行，按 WifiRemotePhase 整页切换）", "设备页之上的固定槽位叠层；蓝牙未连时前置蓝牙阶段。"],
          ["照片浏览顶栏", "<code>WifiPhotoTopBar.kt</code>（刷新 + 断开；选择态 全选本页 + 下载）", "去掉“断开”图标（返回即结束）；下载移到悬浮胶囊；删除“全选本页”。"],
          ["视图选项与标签", "<code>WifiPhotoBrowser.kt</code>（632 行）", "入口图标换为 <code>grid_view</code>；小/中/大、三个开关、合并标签、全局持久化。"],
          ["预览页", "<code>WifiPhotoViewer.kt</code>（314 行）", "顶栏去掉大小；状态行（定位 · 锁星）+ 信息条（格式 · 分辨率 · 大小）+ 胶片条 + 下载、上滑详情、三档窗口适配、EXIF/GPS；删 ‹ › 按钮。"],
          ["<b>对话框按钮</b>", "各处 <code>AlertDialog</code>（<code>DeviceInfoDialogs.kt</code>、<code>SharedDeviceList.kt:88-111,251-310</code>、<code>CameraDeviceManager.kt:381-422</code> 等）", "统一为自绘 G3 面板 + 底部并排胶囊（一个共用组件）。"],
          ["<b>多存储卡</b>", "<code>SonyContentCatalog.kt:230-241</code>（只取卡槽 1）；<code>SonyImageTransfer.kt:169-172</code>（合并但不记归属）", "读取两个卡槽并记录归属；容量按卡；见第 17 页。<b>待实机核实后</b>才开工。"],
          ["设置、日志、关于", "<code>SettingsScreen.kt:147-178</code>；<code>SharedLogViewerScreen.kt</code>；<code>HelpScreen.kt</code>", "新增“照片传输”分组（默认下载格式 + 三个预览信息开关）；删“崩溃报告”项；日志加复制；新增关于页、许可页、隐私说明页。"],
          ["通知", "<code>NotificationsHelper.kt:31-168</code>", "强调色、文案带相机名、同步中改静默渠道、按状态换小图标。"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1">
  <h2>A. 后台常驻语义（行为变更，改动面已核实）</h2>
  {tbl(["差异", "现有实现（文件:行）", "需要的调整"], d1, "xd", ["18%", "45%", "37%"])}
  <div class="note"><b>关于“推翻 v0.1.3 的退出断开”：</b>准确地说只改三条路径——根页返回、从最近任务划掉、Activity 结束时的销毁。Home/切后台一直就保持蓝牙与位置写入，本版只是把这一状态确立为规范并推广到另外三条路径。
  <b>本页是开工前（v0.1.5）的差异清单，保留以便追溯；0.2.0 的落地结果与偏差见第 35 页。</b></div>
  <div class="box"><h3>字符串</h3>
  新增（en + zh-rCN）：断开蓝牙确认两句、关于页与许可页、预览信息条设置、日志复制提示、“卡 2”小标、照片传输入口与说明、叠层各阶段、“轻触任意处返回”、视图选项与三个开关、下载胶囊文案、预览页各信息标签与状态、通知文案。
  删除：<code>donation_dialog_*</code>、<code>sentry_settings</code> / <code>enable_sentry*</code> / <code>sentry_consent_*</code>、<code>tip_jar*</code>、遥控拍摄引导文案、分页相关 <code>wifi_remote_browser_*</code>、<code>viewer_previous_photo/next_photo</code>、<code>device_info_keep_active_hint</code>、<code>wifi_connection_help_*</code>、<code>wifi_remote_close</code>。</div>
 </div>
 <div class="col" style="flex:1.12">
  <h2>B. 界面与功能</h2>
  {tbl(["差异", "现有实现（文件:行）", "需要的调整"], d2, "xd", ["17%", "50%", "33%"])}
  <div class="small">路径前缀：共享界面在 <code>sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/</code>，Android 壳在 <code>app/src/main/java/com/saschl/cameragps/</code>。
  iOS 目标已整体删除（仅 Android）。行号基于 v0.1.5（HEAD 587f510）；第 33 页的清理清单行号来自父会话的盘点，开工前复核。</div>
 </div>
</div>"""
    page("与现有实现的差异", "需开发调整的清单（文件:行）", body)

    # ------------------------------------------------------------------ 33 cleanup lists
    del31 = [["minSdk 三处", "<code>app/build.gradle.kts:22-26,48-55</code>；<code>sharednew/build.gradle.kts:268-276</code>；<code>heif/build.gradle.kts:5-12</code>"],
             ["旧蓝牙 / 定位权限分支", "<code>PermissionBox.kt:101-135,424-447</code>；<code>DevicesScreen.kt:71-87</code>（&lt;31 的 BLUETOOTH / ADMIN、FINE_LOCATION + BLUETOOTH 申请）"],
             ["清单里的旧权限", "<code>AndroidManifest.xml:33-38</code> BLUETOOTH / ADMIN（maxSdk 30）；<code>:9-11</code> WRITE_EXTERNAL_STORAGE（maxSdk 28）"],
             ["Android 8–9 手动 Wi‑Fi 路径", "<code>AndroidWifiRemote.kt:21-28</code>"],
             ["旧存储写入与其权限申请", "<code>AndroidCameraImageStore.kt:23-77</code>；<code>AndroidWifiRemoteScreen.kt:133-143</code>"],
             ["文档与测试", "<code>docs/wifi-architecture.md:87-89</code>；<code>LocationPermissionBoxTest.kt:40-45,81</code> 的 &lt;31 用例"]]
    keep31 = ["API 33 <code>POST_NOTIFICATIONS</code>、<code>NEARBY_WIFI_DEVICES</code>；API 37 <code>ACCESS_LOCAL_NETWORK</code>。",
              "配对（CDM）的 33 / 36 分支：<code>DeviceAssociationUtils</code>、<code>CameraDeviceCompanionService</code>、<code>PairingManager</code>。",
              "蓝牙的 API 33（特征读写回调）与 API 36（connectGatt 设置 / MTU）分支。",
              "前台服务类型声明、通知渠道、MediaStore 写入。"]
    sentry = [["依赖与插件", "<code>gradle/libs.versions.toml</code> 的 sentry 条目；<code>app/build.gradle.kts:97-107,161-166,209-224</code>"],
              ["初始化与清单", "<code>CameraGpsApplication.kt:34-43</code>；<code>AndroidManifest.xml:111-134</code>（DSN / meta-data）"],
              ["构建变体", "两个构建变体里的 <code>CrashReporting</code>；变体本身一并取消，<b>只保留单一构建</b>（等价于原无商店组件的那个）"],
              ["界面", "设置页“崩溃报告”行与子页（<code>SentrySettingsCard.kt</code>、<code>SharedSentrySettings.kt</code>）；启动时的同意对话框（<code>SentryConsentDialog.kt</code>，<code>MainActivity.kt:191-199,295-301</code>）"],
              ["文案", "<code>sentry_settings</code>、<code>enable_sentry*</code>、<code>sentry_consent_*</code>（英文与简体中文）；README、privacy、FAQ 里的相关说明"],
              ["iOS 源码", "iOS 目标已整体删除，其中的 Sentry 引用随之消失"]]
    icon = [["启动图标", "<code>app/src/main/res/mipmap-anydpi/ic_launcher.xml</code> 指向新的前景 / 背景，并<b>新增单色层</b>（Android 13 主题图标）"],
            ["矢量资源", "由 <code>docs/brand/alpha-gps-icon-foreground / -background / -monochrome.svg</code> 转成 VectorDrawable，替换 <code>ic_launcher_foreground.xml</code>、<code>ic_launcher_background.xml</code>"],
            ["应用内", "欢迎页、关于页顶部使用同一图标"],
            ["不做", "iOS 图标（iOS 目标已整体删除）"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1.15">
  <h2>minSdk 26 → 31：可删除的代码 <span class="tag b">W2</span></h2>
  {tbl(["项", "位置"], del31, "xd", ["26%", "74%"])}
  <div class="box b small"><b>必须保留</b>（高于 31 的分支，与本次无关）
  {ul(keep31, "xs")}</div>
  <div class="note small">升到 31 本身<b>不会自动带来</b>动态色或模糊：模糊靠 Haze 落地；动态色本设计不启用。影响面：Android 8–11 的设备不能再安装新版本（现有 arm64 限制不变）。</div>
  <h2>接入新图标 <span class="tag b">W2</span></h2>
  {tbl(["项", "做法"], icon, "xd", ["18%", "82%"])}
 </div>
 <div class="col">
  <h2>移除 Sentry（崩溃上报）<span class="tag b">W2</span></h2>
  {tbl(["项", "位置"], sentry, "xd", ["20%", "80%"])}
  <div class="small">移除后隐私说明才能如实写“无崩溃上报”（第 26 页）。日志仍由 Timber 在本机记录，不受影响。</div>
  <h2>文档与素材 <span class="tag g">W1</span></h2>
  {ul(["<b>删除</b>：<code>website/</code>、<code>fastlane/</code>、<code>.github/FUNDING.yml</code>、仓库根 App Store 徽章、README 的商店徽章与外链图片——<b>待第 27 页逐项确认后执行</b>。",
       "<b>重写</b>：<code>README.md</code>、<code>privacy.md</code>（提纲与要点见第 26 页）；关于页与许可页文案；<code>AGENTS.md</code> 中涉及的名称与发版说明。",
       "<b>替换</b>：根目录 <code>icon.svg</code> 换为新图标。",
       "<b>文案整理</b>（W2，随代码）：删除 <code>sentry_*</code>、<code>tip_jar*</code>、遥控拍摄引导等过时文案，英文与简体中文同步；应用显示名统一为 <b>AlphaGPS</b>。"], "small")}
  <div class="box small"><b>依据说明</b>：本页的文件与行号来自父会话转交的盘点结果，我没有逐条打开核对；<code>CameraGpsApplication.kt</code>、清单与 Gradle 的行号开工前须复核。Tip jar 无 Android 入口是我自己核实的（第 24 页）。</div>
 </div>
</div>"""
    page("清理与接入清单", "minSdk 31 清理 · 移除 Sentry · 接入图标 · 文档与素材", body)

    # ------------------------------------------------------------------ final: question status + split
    qs = [["Q17", "Home / 切后台时 Wi‑Fi 何时结束", "<b>最终决定</b>：无下载→立即结束；有已接受的下载→保留到下载结束（后台最长 30 分钟）；根页返回与划卡立即结束（下载中也结束）"],
          ["Q18", "“保持活跃”改名与默认值", "原定改名“相机离开后保持待命”、默认开；<b>0.2.0 未改</b>（仍为“保持活跃”，默认关），见第 35 页"],
          ["Q19", "根页返回的实现", "服务与蓝牙 + GPS 常驻；Wi‑Fi 立即结束"],
          ["Q20", "背景模糊的实现与平台", "<b>引入 Haze</b>；只做 Android；<b>minSdk 31，无降级</b>（第三轮）"],
          ["Q21", "EXIF/GPS 读取时机", "进入预览页或横向切换后自动读（250 ms 防抖 + 缓存）；缩略图列表不读"],
          ["Q22", "配对流程内的引导", "准备页保留；失败对话框去掉“打开指南”"],
          ["Q23", "日期列表封面缩略图", "显示，惰性加载——<b>0.2.0 未做</b>，已记入待办"],
          ["Q24", "失败叠层自动消退", "5 秒或轻触即回设备页"],
          ["Q25", "同步常驻通知改静默渠道", "接受"],
          ["Q26", "上游图标与素材", "<b>优先自生成</b>：图标已重绘（<code>docs/brand</code>）；其余按第 27 页清单处置"],
          ["Q27", "隐私说明的联系方式", "GitHub Issues（github.com/mfreceiver/alpha-gps/issues），不留个人邮箱"],
          ["Q28", "“支持上游作者”链接", "保留，标明受益人 Saschl；全应用仅关于页一处"]]
    oq = [["Q29", "第 27 页素材清单的逐项处置（C 删除六项、B 重绘三项、A 功能图标只补来源声明）", "等用户逐项回复；回复前 W1 的删除与替换不执行"]]
    w1 = [["W1‑a", "素材清理", "按第 27 页确认结果删除 website/、fastlane/、FUNDING、徽标；替换根 <code>icon.svg</code>", "Q29"],
          ["W1‑b", "README 重写", "按第 26 页提纲；修改声明与致谢", "—"],
          ["W1‑c", "privacy.md 重写", "按第 26 页要点；<b>在 W2‑b 合入后定稿发布</b>", "W2‑b"],
          ["W1‑d", "关于 / 许可 / 隐私页文案", "中英两份；补 Material 图标来源声明", "—"]]
    ms = [["W2‑a", "minSdk 26→31 清理", "第 33 页清单；保留 33 / 36 / 37 分支", "—"],
          ["W2‑b", "移除 Sentry", "第 33 页清单", "—"],
          ["W2‑c", "接入新图标 + 显示名 AlphaGPS", "启动图标三层、欢迎页与关于页", "—"],
          ["W2‑d", "文案整理", "删 <code>sentry_*</code>、<code>tip_jar*</code>、遥控拍摄引导等；英中同步", "W2‑b"],
          ["M0", "主题与玻璃基础", "蓝色色板、背景光、<code>GlassSurface</code> + Haze、对比度测试、对话框胶囊按钮组件；清理旧主题代码", "W2‑a；先锁定 Haze 版本"],
          ["M1", "后台常驻语义 + 移除赞助 + 帮助入口收敛", "三个退出入口只结束 Wi‑Fi；退后台无下载即结束、有下载保留到结束；删赞助自动弹窗；更新测试与冒烟预期", "—"],
          ["M2", "设备清单、配对、设备页、Ⓘ", "连接卡（状态行 + 固定槽位）+ 位置卡（三格）；断开小图标 + 确认；连接中可取消；Ⓘ 精简", "M0"],
          ["M3", "状态数据层", "校时、RSSI、GPS 统计、定位状态与误差、电量缓存、固件 / 序列号缓存、卡容量", "部分待实机核实"],
          ["M4", "连接叠层", "固定槽位卡、分段环、蓝牙前置阶段、失败 / 断开态、自动消退", "M2"],
          ["M5", "取消分页 + 网格", "整日列表、惰性加载与 LRU、小 / 中 / 大、视图选项浮层、合并标签、偏好持久化", "M0"],
          ["M6", "选择态与悬浮下载", "下载胶囊（按默认格式直接下载）、拖选整日、批量进度与汇总；无全选", "M5"],
          ["M7", "预览页（零通信部分）", "状态行、信息条、胶片条、详情面板、三档窗口适配、三个可选项、下载中 / 已保存", "M5"],
          ["M8", "EXIF / GPS 读取", "① 真机样张核实偏移 ② 解析器 ③ 接入预览页与缓存", "M7 · 待实机核实"],
          ["M9", "设置、帮助、关于、隐私、日志、通知 + 走查", "重绘；日志复制；三个新页面；通知强调色与文案；Android 12 / 14 / 最新版真机冒烟", "M0 · W1‑d"],
          ["M5b", "多存储卡", "<b>先实机核实</b>新目录路径能否读卡槽 2；通过后再做归属、“卡 2”小标、同名落盘", "实机核实之后；<b>不阻塞其它</b>"],
          ["M10", "双源发布", "见下。只改发版脚本与文档", "独立"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1">
  <h2>已确认（保留以便追溯）</h2>
  {tbl(["编号", "问题", "结论"], qs, "xd", ["7%", "31%", "62%"])}
  <h2>仍待确认（仅此一项）</h2>
  {tbl(["编号", "问题", "处理"], oq, "xd", ["7%", "55%", "38%"])}
  <h2>W1 文档与素材线</h2>
  {tbl(["项", "主题", "内容", "依赖"], w1, "xd", ["8%", "22%", "54%", "16%"])}
  <div class="note xs"><b>待实机核实（不是待用户决定）：</b>保护 / 评分字段、电量属性码、EXIF / GPS 子目录偏移（JPG / ARW / HEIF）、预览图是否自带完整 EXIF、连续缩略图请求下的稳定并发数、<b>新目录路径能否读取卡槽 2</b>。每一项在界面上都已有“读不到”的显示。</div>
  <div class="box xs"><b>与 docs/todo.md 的关系</b>：本拆分<b>不复制进</b> <code>docs/todo.md</code>；开始某一项时再按待办纪律写入。可能因本设计而过期的现有条目：P1#9 的“退出仍断开”预期、P3#8、P3#11（M2 覆盖）。</div>
 </div>
 <div class="col" style="flex:1.2">
  <h2>W2 代码线</h2>
  {tbl(["项", "主题", "内容", "依赖"], ms, "xd", ["7%", "22%", "50%", "21%"])}
  <div class="box b xs"><b>M10 双源发布：设计要点（只写设计，不改脚本）</b>
  {ul(["<b>主次</b>：主仓库（私有部署，<b>地址仅存本机配置、不入库</b>）为主。现有主仓库发布（上传 + 资产对账，失败即中止）不变；<b>成功后</b>才执行 GitHub 步骤（mfreceiver/alpha-gps）。",
       "<b>参数外置</b>：主仓库的主机、仓库路径、远端名等放到不入库的 <code>scripts/release.local.env</code>（加入 .gitignore）；脚本、文档与应用里不出现私有地址，缺该文件时主仓库步骤直接中止并提示。",
       "<b>失败语义</b>：GitHub 失败不回滚主仓库；脚本非零退出并报告“主仓库已成功、GitHub 失败”。",
       "<b>可单独重跑</b>：<code>--publish-github-only</code>；同 tag 同名且 sha256 相同的资产跳过，同名不同 sha 中止。",
       "<b>推送与凭据</b>：GitHub 步骤<b>默认只推 tag</b>（<code>--github-tag-only</code> 为默认），推分支需显式指定；执行前提示“<b>该 tag 可达的全部提交历史将对公开可见</b>”。<code>git credential fill</code> 取 host=github.com，沿用 15 秒超时（<code>release_build.sh:106-107</code>）。",
       "<b>对账</b>：现脚本 <code>--no-location</code> 不跟随重定向（<code>:151-154</code>），而 GitHub 资产下载会 302。方案是<b>手动走一跳</b>：首次请求不跟随并读取 Location，校验为 https 且主机在白名单；第二次请求<b>不带凭据</b>、仍不跟随；再次重定向或主机不在白名单即中止。",
       "<b>白名单</b>：API 与上传只允许 api.github.com / uploads.github.com；下载域名（objects.githubusercontent.com 等）以开发时 GitHub 实际使用的为准，须核对。",
       "同步更新 <code>AGENTS.md</code> 与 README 的发版说明（同样不写私有地址）。"], "xs")}</div>
  <div class="xs"><b>顺序</b>：W2‑a / W2‑b / W2‑c 与 M1 互不依赖，可先行并单独发版；M0 在 W2‑a 之后。M2–M4 设备侧、M5–M8 照片侧可并行；M9 收尾；M5b 等实机核实；M10 随时可做。</div>
 </div>
</div>"""
    page("问题状态与开发拆分", "Q17–Q28 已确认 · Q29 待确认 · W1 文档素材线 · W2 代码线 · 落地结果见第 35 页", body)

    # ------------------------------------------------------------------ 35 deviations in 0.2.0
    OKT = '<span class="tag g">已改正文</span>'; DEV = '<span class="tag y">偏差</span>'; LATER = '<span class="tag b">待办 / 后续</span>'
    dv1 = [["后台与退出", "<b>蓝牙 + GPS 常驻</b>：Home / 锁屏 / 切应用 / 划卡 / 根页返回都不断；仅设备页主动“断开”才断蓝牙。<b>Wi‑Fi</b>：退后台无下载→立即结束；有已接受的下载（单张 / 批量）→保留到下载结束后自动结束（后台最长 30 分钟）；划卡与根页返回→立即结束（即使下载中）。取代此前“离开前台延时结束”“根页返回只结束界面”的写法。", "30 · 32 · 34", OKT],
           ["设备页", "标题 = 设备名；连接卡（状态行 + 副行时间校正文案 + 右侧固定槽位：断开小图标 / 取消 / 连接）；<b>一张</b>位置卡三格（信号 / 已写入 / 最近写入）；Hero“照片传输”。未连蓝牙点击先连蓝牙（30 秒超时）；失败红图标无底色。<b>无</b>型号、上次连接时间、坐标 / 已定位行、失败次数、警告横幅。主动断开后不自动重连。", "10", OKT + " 重绘"],
           ["Ⓘ", "✕、✎、蓝牙绿 ✓、MAC、保持活跃开关、移除相机；另有位置写入、自动校时 / 区域调整的只读状态行。<b>无 MTU / 型号 / 固件 / 序列号 / 帮助</b>。", "11", OKT + " 重绘"],
           ["“保持活跃”", "未按 Q18 改名“相机离开后保持待命”，沿用原名；默认关；保留一句说明。", "11 · 28 · 34", DEV],
           ["连接叠层", "卡片固定 310×320、三固定槽位；失败红图标无底色，无“帮助 / 重试”，5 秒或轻触回设备页；网络权限被拒显示“去授权”且不自动关闭；日期列表返回 = 结束 Wi‑Fi、保蓝牙。设备页不保留失败提示条。", "12 · 13", OKT],
           ["日期列表", "行内为日期 · 星期 · 今天 / 昨天 · 张数，未知日期置末；<b>不显示固件 / 序列号 / 容量</b>。<b>封面缩略图未做</b>（已记待办）。", "14 · 17 · 31", OKT + " 重绘 " + LATER],
           ["日期网格", "<b>取消日期内分页</b>：整日一个网格，无上一页 / 下一页 / 跳页。缩略图视口驱动：可见行 ± 两行余量，串行 12 张 / 批，缓存上限 300。视图选项持久化：小 / 中 / 大 = 4 / 3 / 2 列（默认小）；格式、大小、保护三个 tag 开关默认关。快速滚动条与时间气泡未做。", "15 · 16 · 19", OKT]]
    dv2 = [["选择与下载", "删除“全选本页”与 Original 格式底部弹窗；悬浮按钮“下载 N 张 · x MB”<b>直接按设置的默认格式下载</b>；“仅 JPG”时无 JPG 的拍摄组跳过并计入汇总（“已下载 N、跳过 M”）；拖选范围 = 整日；<b>下载完成后不自动退出选择</b>。", "18", OKT],
           ["帮助 / 赞助", "帮助唯一入口在设备清单；赞助链接<b>仅在关于页</b>，标明受益人为上游作者 Saschl。", "8 · 24", OKT],
           ["关于页", "fork 自 Saschl/alpha-gps、GPL‑3.0、修改声明（mfreceiver 自 2026‑11 起）、源码与 Issues 仅 GitHub、商标免责、图标素材来源说明。", "25", OKT],
           ["设置页", "“照片传输”分组 = 默认下载格式 + 三个预览信息开关（直方图 / 拍摄参数 / 镜头，默认关），直接在分组内，无“预览信息条”子页。顶栏、开关等细节以实现为准。", "23", OKT + " 重绘"],
           ["日志页", "只有“复制（最新 2000 条）”与“清空（二次确认）”；<b>筛选胶囊、导出未做</b>。", "23", OKT + " 重绘 " + LATER],
           ["许可页", "统一随包 Apache‑2.0 / GPL‑3.0 / LGPL‑3.0 全文；<b>未逐库附 NOTICE</b>。", "25", DEV],
           ["预览页", "<b>设计保持不变</b>（第 20–22 页）。预览页以 M7 实现为准，完成后再对齐本文档。", "20–22", LATER],
           ["平台与构建", "<b>仅 Android</b>（iOS 目标已删除）；<b>单一构建</b>（等价于原先不含商店组件的那个，无应用市场分发）；<b>仅 arm64</b>；<b>仅 English + 简体中文</b>。", "1 · 24 · 26 · 27 · 33", OKT]]
    body = f"""
<div class="row">
 <div class="col">
  <h2>与 0.2.0 实现的对照（一）</h2>
  {tbl(["主题", "0.2.0 的实现 / 最终决定", "涉及页", "处理"], dv1, "xd", ["12%", "60%", "12%", "16%"])}
  <div class="note small"><b>怎么读本页</b>：{OKT} = 该页正文（及标注“重绘”的 mockup）已改为实现的样子；{DEV} = 设计曾另有决定，实现未采纳，以实现为准；{LATER} = 尚未实现或仍在开发，设计保留。
  本页与其它页冲突时<b>以本页为准</b>；后台与退出语义的权威描述同时见 <code>docs/wifi-architecture.md</code>。</div>
 </div>
 <div class="col">
  <h2>与 0.2.0 实现的对照（二）</h2>
  {tbl(["主题", "0.2.0 的实现 / 最终决定", "涉及页", "处理"], dv2, "xd", ["12%", "58%", "13%", "17%"])}
  <div class="box small"><b>mockup 与历史页说明</b>
  {ul(["第 2、3 页（第二、三轮修订对照）保留当时的意见与落实记录，其中“位置传输卡与相机卡同构”“容量按卡分行放日期列表顶部”“复制当前筛选下的日志”已被本页取代。",
       "第 12、13 页叠层背后的设备页，以及第 29 页暗色稿，随设备页一并重绘；选择态 mockup 仅去掉“全选”图标。",
       "第 17 页多存储卡仍待实机核实；其中的“容量分行”不再适用。",
       "第 32–34 页是开工前（v0.1.5）的差异与拆分清单，行号不再对应当前代码，仅作追溯。"], "xs")}</div>
  <div class="box b small"><b>公开地址</b>：源码与问题反馈只写 https://github.com/mfreceiver/alpha-gps （Issues：/issues）；上游为 Saschl/alpha-gps。</div>
 </div>
</div>"""
    page("0.2.0 实现偏差与决定", "设计稿与 0.2.0 实现逐项对照 · 冲突时以本页为准", body)


def E_notif(dark):
    import screens3b as B
    import lib3 as L
    s, w, h = B.notif_shade(dark)
    return L.svg_wrap(w, h, s), w, h
