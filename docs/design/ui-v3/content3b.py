"""alpha-gps UI design v3 — pages 15–27 (selection, caching, viewer, settings, help, notifications, dark, semantics, data, diff, open questions)."""
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
  {ul(["<b>进入</b>：长按任一照片。顶栏变为 ✕ + “已选 n 张” + 右侧“全选”图标。",
       "<b>选中表现</b>：照片内缩 6dp，露出主色浅底 + 2.5dp 主色描边，左上选择圈变为实心 ✓。未选中的照片左上显示空心圈。",
       "<b>下载胶囊</b>：屏幕底部居中，56dp 高的主色胶囊，写明<b>张数与合计大小</b>（“下载 3 张 · 67.8 MB”）。这是本屏唯一的主操作。",
       "<b>全选 = 本日全部</b>（不再是“本页”，Q6）。再点一次取消全选。",
       "<b>拖选</b>：长按后不抬手拖动，经过的照片连续选中；拖到上下边缘自动滚动。取消分页后拖选可贯穿整日。"])}
  <div class="box b"><h3>胶囊如何不挡缩略图</h3>
  {ul(["<b>底部内边距</b>：选择态下网格 <code>contentPadding.bottom</code> = 胶囊 56 + 上下间距 32 + 系统导航条高度，最后一行可以完整滚到胶囊上方。",
       "<b>滚动收起</b>：手指向上滑（看更下面的内容）或拖选进行中 → 胶囊收成右下角的<b>小号</b>（图标 + 张数，84dp 宽）；停止 600 ms 或向下滑 → 展开回完整文案。",
       "<b>位置固定</b>：胶囊始终在同一水平线上，只变宽度不变高度，不产生跳动。",
       "胶囊背后有一层自下而上的暗化渐变（非模糊），保证在任何照片上可读。"], "small")}</div>
  <div class="box"><h3>下载中（Q12）</h3>
  {ul(["胶囊变为 G2 玻璃进度条：环形进度 + “正在下载 2 / 3” + 当前文件名与百分比 + 右侧“取消”。",
       "下载中<b>锁定选择与滚动之外的操作</b>：不能增减选择，顶栏 ✕ 与返回 = 取消下载（先弹“取消下载？”确认）。",
       "完成后胶囊短暂显示“已保存 3 张”1.5 秒，随后自动退出选择态。已保存的照片在网格上不加 ✓。",
       "超过大小上限或没有可下载格式的照片，在选择时即从合计中剔除，并在胶囊文案后注明“1 张不可下载”。"], "small")}</div>
 </div>
</div>"""
    page("选择态与悬浮下载", "悬浮胶囊 · 避让 · 拖选 · 批量下载", body)

    # ------------------------------------------------------------------ 16 no paging / cache
    rows = [["缩略图（网格）", "可见窗口 + 前 2 屏 / 后 3 屏（顺滚动方向）", "位图 LRU ≈ 300 张", "并发 ≤ 2；快速滑动时取消未开始的请求；停稳 150 ms 后才预取"],
            ["预览图（单张）", "当前 ± 1", "LRU 5 张", "切换时取消更远的请求；ARW 走内嵌 JPEG"],
            ["EXIF / GPS", "仅当前张，停留 ≥ 250 ms 才请求", "结果 LRU 200 条", "失败不自动重试；再次进入该张时重试 1 次"],
            ["直方图", "预览图解码后本地计算", "随预览图缓存", "零通信；256 级亮度，后台线程"],
            ["胶片条", "复用网格缩略图缓存，当前 ± 4", "同缩略图 LRU", "缓存未命中才请求"],
            ["元数据（名称/大小/格式/时间）", "整卡，连接时一次读入", "整个会话常驻内存", "现状已如此，无变化"]]
    aff = [["<code>SonyImageTransfer.kt:456</code> PAGE_SIZE=60；<code>:140,:190,:194-196,:217</code> pagePhotos 限定", "缩略图/预览只应答“当前页”句柄 → 改为应答“当前日期”全部句柄"],
           ["<code>WifiCatalogSnapshot.kt:99</code> <code>page(dateKey, offset)</code>；<code>WifiCameraCapture.kt:26-28</code>；<code>SonyContentCatalog.kt:151-153</code>", "按日期返回整日列表，去掉 offset"],
           ["<code>WifiPhotoBrowser.kt:423</code>、<code>WifiPhotoPager.kt:48</code>、<code>WifiPhotoPaging.kt:7</code>", "删除翻页条与页数计算"],
           ["<code>WifiPhotoViewer.kt:203-206</code> Pager、<code>:111-136</code> 预取、<code>:261-291</code> ‹ › 按钮", "Pager 页数 = 当日总数；删除跨页切换逻辑与 ‹ › 按钮"],
           ["<code>WifiRangeSelection.kt</code>、<code>WifiPhotoTopBar.kt</code> 全选（本页）", "拖选与全选范围 = 本日"],
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
  {ul(["一个日期 = 一条连续的 <code>LazyVerticalGrid</code>；右侧显示快速滚动条，拖动时浮出时间气泡（“14:32”）。",
       "滚动位置按日期记忆；从预览返回时定位到最后查看的照片。",
       "单日照片很多（例：2000 张）时，内存里只有 ≈ 300 张缩略图位图 + 全部元数据（每条约 200 字节）。",
       "通道优先级：<b>下载 &gt; 当前预览图 &gt; EXIF &gt; 可见缩略图 &gt; 预取</b>。用户发起的永远插队。"])}
  <h3>受影响的现有实现</h3>
  {tbl(["位置", "调整"], aff, "xd", ["52%", "48%"])}
  <div class="box"><h3>回退</h3>若实机发现某机型在连续缩略图请求下不稳定：把并发降为 1、预取范围减半；界面不变。这两个值做成内部常量，不提供设置项。</div>
 </div>
</div>"""
    page("取消分页：缓存与惰性加载", "整日连续列表 · 可见窗口 ± 余量 · LRU", body)

    # ------------------------------------------------------------------ 17 viewer landscape
    body = f"""
<div class="row">
 {phone("view_land_c", 0.74, "① 横图 3:2（最常见）")}{phone("view_sheet", 0.74, "② 上滑展开“详细信息”")}
 <div class="col" style="flex:0 0 420px">{wide("vzones", E.viewer_zones())}
  <div class="box small"><b>手势</b>：左右滑=上/下一张（整日范围）· 单击照片=隐藏/显示全部信息（沉浸）· 双击/捏合=缩放 · 下空白区上滑或点把手=展开详情 · 下滑=收起 · 系统返回=回网格。</div>
 </div>
 <div class="col">
  <h2>单张预览：把上下空白用起来</h2>
  <p>竖屏手机看 3:2 横图时，照片只占屏幕中间约 31% 的高度。v3 把上下两块黑色空白变成信息区：</p>
  {ul([f"{n(1)}<b>顶栏</b>（沿用 v2）：文件名 + 大小同一行，下一行拍摄时间；右侧“6 / 227”。",
       f"{n(2)}<b>拍摄参数卡</b>：光圈、快门、ISO、焦距四格大字；下一行镜头与曝光补偿。",
       f"{n(3)}<b>定位状态</b>：绿色“已定位 + 坐标”或琥珀“未含位置”。这是本应用最核心的反馈——<b>位置到底写进照片没有</b>。",
       f"{n(4)}同一行右侧：保护与评分合并 tag（与缩略图一致）。",
       f"{n(5)}<b>文件信息卡</b>：左侧亮度直方图；右侧 JPG / RAW 各自大小、像素尺寸与百万像素、保护评分、已保存状态。顶部有把手，可上滑展开。",
       f"{n(6)}<b>胶片条</b>：前后各 4 张缩略图，当前张放大带白框；轻触跳转，横向拖动快速浏览。",
       f"{n(7)}<b>下载</b>：底部居中的主按钮，位置固定。"])}
  <div class="box b"><h3>信息分三层</h3>
  {ul(["<b>常驻</b>：顶栏、定位状态、胶片条、下载。", "<b>有空白才显示</b>：拍摄参数卡、文件信息卡（横图时放在上下空白里；竖图时收敛，见下页）。",
       "<b>展开才显示</b>：详细信息面板（②）——完整坐标与海拔、GPS 时间、6 项拍摄参数、镜头、RGB 直方图、文件明细。"], "small")}</div>
  <div class="small">详情面板展开时照片上移并缩到面板上方；胶片条隐藏；下载缩为面板右上角的圆形按钮，功能不变。</div>
 </div>
</div>"""
    page("单张预览（横图）", "拍摄参数 · 定位状态 · 直方图 · 文件信息 · 胶片条", body)

    # ------------------------------------------------------------------ 18 viewer portrait + states
    body = f"""
<div class="row" style="gap:12px">
 {phone("view_port_c", 0.54, "① 竖图 2:3")}{phone("view_loading", 0.54, "② 信息读取中")}{phone("view_fail", 0.54, "③ 读取失败")}
 {phone("view_nogps", 0.54, "④ 没有位置")}{phone("view_dl", 0.54, "⑤ 下载中")}{phone("view_saved", 0.54, "⑥ 已保存")}
</div>
<div class="row" style="margin-top:8px">
 <div class="col"><div class="box"><h3>竖图如何收敛</h3>
 {ul(["竖图几乎铺满顶栏到胶片条之间，上下没有空白。信息收成<b>压在照片底边的一条玻璃</b>（40dp 高）：",
      f"{n(1)}最左是定位状态图标（绿=已定位 / 琥珀=未含位置 / 灰=未知），随后一行参数“f/1.8 · 1/500 s · ISO 100 · 85 mm”。",
      f"{n(2)}最右的 ⌃ 与整条玻璃都可上滑/点击，展开与横图相同的详细信息面板。",
      "直方图、文件明细、保护评分在竖图下只出现在详情面板里。",
      "顶栏下方加一段自上而下的黑色渐变，保证文件名在浅色照片上可读。",
      "判定：照片显示高度 &gt; 可用高度的 70% 即按“竖图布局”处理（含 1:1、4:3 竖拍）。"], "small")}</div></div>
 <div class="col"><div class="box"><h3>读取中 / 失败 / 无位置</h3>
 {ul(["<b>读取中</b>（②）：参数格显示浅色占位条，定位显示“读取位置…”。照片、直方图、文件信息不受影响，先行显示。",
      "<b>读取失败</b>（③）：参数显示“—”与一行灰字“拍摄参数不可用（未能读取文件头）”；定位显示灰色“位置信息不可用”。<b>不弹错误、不显示重试按钮</b>；再次滑回这张时自动重试一次。",
      "<b>没有位置</b>（④）：琥珀色“未含位置 · 拍摄时没有写入 GPS”。这与“读取失败”严格区分——前者是确定的结论，后者是不知道。",
      "相机不支持按范围读取（极少数旧机型）：参数卡与定位整体不显示，其余布局上移，不留空壳。"], "small")}</div></div>
 <div class="col"><div class="box"><h3>下载中 / 已保存（Q12）</h3>
 {ul(["<b>下载中</b>（⑤）：按钮变为 G 玻璃进度胶囊（环形进度 + 百分比 + 已传/总大小 + “取消”）。<b>锁定左右滑动</b>，胶片条变暗不可点；返回 = 取消下载。",
      "<b>已保存</b>（⑥）：按钮置灰显示“✓ 已保存”，不可点；文件信息里同步显示绿色“已保存 JPG”。",
      "同一次拍摄有 JPG+RAW：下载按钮下载的格式遵循现有下载格式设置；顶栏大小口径沿用 Q15（仅 JPG 时显示 JPEG 大小，原始格式的 J+R 显示合计）。",
      "失败：按钮恢复为“下载”，按钮上方出现一行红字原因 3 秒（如“存储空间不足”）。"], "small")}</div></div>
</div>"""
    page("单张预览（竖图与各状态）", "一行收敛 · 读取中 · 失败 · 无位置 · 下载中 · 已保存", body)

    # ------------------------------------------------------------------ 19 viewer data / EXIF feasibility
    rows = [["① 文件信息：格式、JPG/RAW 各自大小、已保存", "目录元数据 + 本地下载记录", Z, G, "始终可用"],
            ["① 保护、评分", "目录字段（新路径 8 字节，现被跳过）/ 旧路径 ObjectInfo", Z, V, "读不到 → 不显示该 tag"],
            ["② 拍摄参数：光圈/快门/ISO/焦距/镜头/曝光补偿", "EXIF 子目录（ExifIFD）", "约 64 KB / 张", T + " " + V, "“—” + 灰字说明"],
            ["③ 定位状态与坐标、海拔、GPS 时间", "EXIF 的 GPS 子目录（GPS IFD）", "与 ② 同一次请求", T + " " + V, "区分“未含位置”与“不可用”"],
            ["④ 直方图", "由已下载的预览图本地计算", Z, T, "预览图失败则不显示"],
            ["⑤ 像素尺寸、百万像素", "EXIF PixelXDimension/YDimension（或 ARW 主目录宽高）", "与 ② 同一次请求", T + " " + V, "读不到 → 该行隐藏"],
            ["⑥ 胶片条缩略图", "复用网格缩略图缓存", Z + "（未命中才请求）", T, "占位色块"]]
    dep = [["新目录路径（v4，多数新机型）", "<code>SonyContentCatalog.read</code> 操作码 <code>0x923d</code>，64 位偏移（<code>SonyContentCatalog.kt:172-191</code>）", G + " 下载与 ARW 预览已在用"],
           ["旧路径", "<code>GET_PARTIAL_LARGE_OBJECT 0x9211</code>，不支持时退回标准 PTP <code>GetPartialObject 0x101b</code>（32 位偏移）（<code>SonyImageTransfer.kt:430-448</code>）", G + " 下载已在用"],
           ["读文件头的先例", "<code>SonyArwPreview.read</code> 已读取 ARW 前 64 KB 并遍历 TIFF 主目录（<code>SonyArwPreview.kt:42-91</code>）", G],
           ["JPEG 的 EXIF 解析", "<code>SonyJpeg.kt:50-59</code> 已有宽松的 APP1/IFD 遍历，但只取方向；需扩展到 ExifIFD（0x8769）与 GPS IFD（0x8825）", T],
           ["JPG：EXIF 是否在前 64 KB", "APP1 段上限 64 KB 且紧跟文件头，理论上必然在内；Sony 机型的 MakerNote 较大，需确认 GPS IFD 偏移未超出", V],
           ["ARW：子目录是否在前 64 KB", "主目录在头部已验证；ExifIFD / GPS IFD 的实际偏移未验证，超出时需按偏移再读一次", V],
           ["HEIF：EXIF 位置", "EXIF 是 meta/iloc 指向的独立项，不保证在文件头；目前只有本地文件的 <code>HeifDecoder.readExif</code>（<code>AndroidHeifConverter.kt:44-47</code>）", V + " 可能需两次读取；首版可对 HEIF 只显示①④⑥"],
           ["相机返回的预览/缩略图 JPEG 是否自带完整 EXIF", "已知带方向（<code>SonyImageTransfer.kt:206,246</code>）；若同时带 GPS 与曝光参数，则 ②③⑤ 可<b>零额外请求</b>", V + " 优先验证这一条"],
           ["写入 GPS 的坐标与手机发送值是否一致", "相机写入的是它收到的最近一次定位，可能滞后数秒", V + " 只展示照片内的值，不做比对"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1.05">
  <h2>预览页六类信息：来源与成本</h2>
  {tbl(["信息", "来源", "通信成本", "状态", "读不到时"], rows, "dense", ["30%", "27%", "15%", "14%", "14%"])}
  <div class="box b"><h3>读取策略</h3>
  {ul(["<b>仅预览页触发</b>；网格与日期列表不读 EXIF。", "停留 ≥ 250 ms 才发请求，快速连续滑动时不为路过的照片读取。",
       "一次读取文件前 64 KB，解析出 ②③⑤ 全部字段；结果按照片缓存（LRU 200 条），再次查看零通信。",
       "优先级低于下载与预览图；下载进行中暂停 EXIF 读取。",
       "解析采用宽松策略：任何越界/异常字段只让该字段为空，不让整张照片失败（与现有方向解析一致）。",
       "是否“每张自动读”还是“展开面板才读”，见 Q21（建议自动读）。"], "small")}</div>
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
 {phone("settings", 0.74, "① 设置")}{phone("settings_sub", 0.74, "② 子页示例：电池优化")}{phone("logs", 0.74, "③ 日志")}
 <div class="col">
  <h2>设置页</h2>
  {ul(["三组玻璃卡，组内各行之间<b>不画分隔线</b>，靠行高分隔。",
       "<b>位置同步</b>总开关单独成卡，置顶：关闭后不连接任何相机、后台服务停止（现有 <code>isAppEnabled</code> 语义不变）。",
       "<b>连接与定位</b>：位置来源、提示音、电池优化——右侧灰字显示当前值，点击进入子页。",
       "<b>通用</b>：语言、日志级别、更新日志、崩溃报告（仅在该构建提供时显示）。",
       "底部一行版本号与包名（信息，不可点；连点 5 次仍可打开调试面板，保持现状）。"])}
  <div class="box b"><h3>相对现状的变化</h3>
  {ul(["<b>删除“Debug: Donation Dialog”调试卡</b>（<code>DebugDonationDialogCard.kt</code>，<code>SettingsScreen.kt:177</code>）。",
       "日志级别、语言由“卡片内直接展开”改为与其它项一致的“行 + 当前值 → 子页/选择对话框”。",
       "子页统一结构：顶部状态卡（图标 + 一句结论 + 说明）+ 操作行；外部跳转用 ↗ 图标。",
       "<b>不新增</b>“玻璃效果/透明度”开关；视图偏好（缩略图大小与标签）只在网格的视图选项里改，不在设置页重复。"], "small")}</div>
  <div class="box"><h3>日志页</h3>
  {ul(["入口：设备清单顶栏、排障指南底部。", "顶部筛选胶囊（全部 / 警告 / 错误）是操作控件——选中为主色填充；每行左侧的 I / W / E 是信息 tag。",
       "顶栏右侧：导出、清空（清空需确认）。等宽时间戳 + 级别 + 消息，错误行整行红字。"], "small")}</div>
  <div class="small">现有实现：<code>SettingsScreen.kt:147-178</code>（Overview 顺序）、<code>AppControlsCard.kt</code>、<code>LogLevelSettingsCard.kt</code>、
  <code>SharedLanguageSettingsCard.kt</code>、<code>BatteryOptimizationCard.kt</code>、<code>SharedLogViewerScreen.kt</code>。</div>
 </div>
</div>"""
    page("设置与日志", "分组玻璃卡 · 子页结构 · 移除赞助调试卡", body)

    # ------------------------------------------------------------------ 21 help
    keep = [["设备清单顶栏“帮助”图标", "<code>DevicesScreen.kt:95-121</code>", '<span class="ok">保留（唯一入口）</span>'],
            ["设备页 → Ⓘ 内“帮助”文字按钮", "<code>DeviceInfoDialogs.kt:91,153-154</code>；<code>CameraDeviceManager.kt:366</code>", '<span class="todo">移除</span>'],
            ["连接失败时的帮助对话框", "<code>WifiConnectionHelp.kt:23-52</code>；<code>WifiRemoteScreen.kt:739</code>", '<span class="todo">移除</span>（权限场景改为叠层内“去授权”）'],
            ["设备清单底部“需要帮助？”", "<code>DeviceListLayout.kt:26,45</code>", '<span class="todo">移除</span>（与顶栏重复）'],
            ["错误行上的“打开指南”", "<code>SharedDeviceList.kt:382-394</code>", '<span class="todo">移除</span>（原因直接写在行内）'],
            ["配对准备页底部引导链接", "<code>SharedPairingPreparationScreen.kt:56,107-113</code>", '<span class="warn">保留为流程内引导</span>（Q22）'],
            ["配对失败对话框“打开指南”", "<code>CameraDeviceManager.kt:381-422</code>", '<span class="warn">改为“重试 / 取消”</span>，正文写清原因（Q22）']]
    body = f"""
<div class="row">
 {phone("help_c", 0.74, "① 帮助")}{phone("trouble", 0.74, "② 排障指南")}
 <div class="col">
  <h2>帮助页</h2>
  {ul(["<b>唯一入口：设备清单顶栏的帮助图标</b>（Q11）。",
       "顶部“关于”卡（主色浅底玻璃）→ “排障指南”入口卡 → 常见问题（可展开的玻璃行，默认展开第一条）。",
       f"{n(1)}排障指南：按“相机端 / 手机端 / 仍然不行”三步清单，末尾“查看日志”。",
       f"{n(2)}<b>“请作者喝杯咖啡”</b>：保留为第一条问答里的一个<b>手动链接</b>（紫色心形图标 + ↗），与“代码仓库”“发送邮件”并列。用户不点就永远不会看到任何赞助相关内容。"])}
  <div class="box b"><h3>赞助提醒：全面移除自动触发，仅保留手动链接</h3>
  {ul(["删除：安装 30 天后的自动弹窗、“下次启动强制弹出”的调试路径、设置页调试卡，以及 iOS 上对应的三处逻辑。",
       "保留：帮助页内的 buymeacoffee 链接（Android <code>HelpScreen.kt:274-287</code>，iOS <code>IosHelpScreen.kt:225</code>）。",
       "涉及文件与行号见第 26 页。"], "small")}</div>
  <h3>帮助 / 引导入口清单（现状 → v3）</h3>
  {tbl(["现有入口", "位置", "v3"], keep, "xd", ["28%", "40%", "32%"])}
  <div class="small">“去授权”“系统电池设置”这类按钮是完成任务所必需的操作，不属于帮助入口，按需保留。</div>
 </div>
</div>"""
    page("帮助与排障", "唯一入口 · 手动赞助链接 · 入口清单", body)

    # ------------------------------------------------------------------ 22 notifications
    rows = [["待命（静默）", "等待相机", "相机不在附近，回到范围后自动连接", "低 · 无声", "灰色图标；“保持待命”开且无连接时"],
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
       "同步中的常驻通知由“传输”渠道（高重要性、有声）改到<b>静默</b>渠道：它是状态而非事件；声音提示继续由提示音设置负责。此项改变渠道行为，列入差异清单。",
       "从最近任务划掉应用后通知仍在；用户在设备页确认“断开蓝牙”后，该相机从通知中消失，全部断开且未开“保持待命”时通知撤销。"], "small")}</div>
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
       "主色换为 <code>#A8C7FA</code>，主按钮上的文字为深蓝 <code>#062E6F</code>；绿、琥珀、红均换为暗色版本（第 4 页）。",
       "遮罩加深到 52%，保证对话框从暗背景中分离出来。",
       "照片上的信息 tag、预览页的全部面板在两种主题下<b>完全相同</b>（都是深色），因此预览页不单独列暗色稿。",
       "跟随系统深浅色切换，不提供应用内主题开关（现状如此，不变）。"])}
  <div class="note">对比度实测见第 3 页：暗色 G1 上正文、次要文字、主色、对话框内错误色均 ≥ AA。</div>
 </div>
</div>"""
    page("暗色对照", "同一套布局 · 层级改用亮度与描边表达", body)

    # ------------------------------------------------------------------ 24 semantics
    sem = [["Home / 切到其它应用", "保持", "保持（现状：一直保持）", "保持", "<b>见 Q17</b>：建议下载中保持到完成，否则 30 s 后结束", '<span class="tag z">蓝牙部分无改动</span>'],
           ["根页（设备清单）返回", "<b>断开</b>并停止服务", "结束", "<b>保持</b>", "结束", '<span class="tag r">需改</span>'],
           ["从最近任务划掉", "<b>断开</b>并停止服务", "结束", "<b>保持</b>（前台服务续跑）", "结束", '<span class="tag r">需改</span>'],
           ["Activity 被销毁（isFinishing）", "<b>断开</b>并停止服务", "结束", "<b>保持</b>", "结束", '<span class="tag r">需改</span>'],
           ["设备页返回 → 设备清单", "保持", "—", "保持", "—", '<span class="tag z">无改动</span>'],
           ["日期列表返回 → 设备页", "保持", "结束", "保持", "结束（叠层“正在断开…”）", '<span class="tag z">行为不变，界面改为叠层</span>'],
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
  <h2>后台常驻语义：逐项对照（现状经代码核实）</h2>
  {tbl(["用户动作", "现状 · 蓝牙+GPS", "现状 · Wi‑Fi", "v3 · 蓝牙+GPS", "v3 · Wi‑Fi", "改动"], sem, "xd", ["19%", "14%", "8%", "18%", "19%", "22%"])}
 </div>
 <div class="col">
  <div class="box b"><h3>一句话定义</h3><b>离开应用界面（Home、切后台、根页返回、从最近任务划掉）都不断开蓝牙，后台服务继续写入位置；只有 Wi‑Fi 会话随界面离开而结束。
  蓝牙只在用户于设备页点“断开蓝牙”并确认后才断开。</b></div>
  <h3>蓝牙自动连接（Q5）</h3>
  {tbl(["情形", "行为"], auto, "xd", ["46%", "54%"])}
  <div class="small"><b>核实结论：</b>Home/后台本来就保持蓝牙（<code>LocationSenderService.kt:164-169</code> 注释）。会触发“退出即断开”的只有三个入口，
  全部汇到 <code>AppServices.disconnectAllOnExit</code>（<code>AppServices.kt:89-91</code>）——真实改动面就是这三处，见第 26 页。</div>
 </div>
</div>"""
    page("导航与断开语义", "蓝牙 / Wi‑Fi / 离开应用 · 后台常驻语义", body)

    # ------------------------------------------------------------------ 25 data sources
    rows = [["相机时间（校时）", "设备页相机卡", "蓝牙：校时写入的应答（仅 applied=1 视为已校正）", "每次蓝牙连接后 1 次", "未校正=琥珀；未知=“—”", T],
            ["信号强度", "设备页相机卡", "蓝牙 RSSI", "连接后首读 + 每 5 s（仅设备页可见时）", "“—”", T],
            ["电量", "设备页相机卡", "Wi‑Fi 会话就绪后读 1 次，缓存", "每次照片传输会话", "≤30 分钟正常；≤24 小时变灰带时间；更久“—”", T + " " + V],
            ["最近写入时间、坐标、精度", "设备页位置卡", "本机定位 + 蓝牙写入结果", "每次写入（界面每秒刷新相对时间）", "未连接时显示上次写入时间", T],
            ["已写入 / 失败 / 本次连接时长", "设备页位置卡", "本机计数（会话内）", "每次写入", "未连接时保留上次计数、变灰", T],
            ["型号、固件、序列号", "Ⓘ；日期列表 tag", "Wi‑Fi：设备信息", "每次会话就绪", "未读过=“连接照片传输后可见”", T],
            ["卡容量 / 剩余", "Ⓘ；日期列表 tag", "Wi‑Fi：存储信息", "每次会话就绪 + 刷新", "同上", T],
            ["蓝牙配对状态、MTU", "Ⓘ", "蓝牙连接参数", "连接时", "未连接=“—”", G],
            ["照片目录（名称/大小/格式/时间）", "日期列表、网格、预览", "Wi‑Fi：整卡目录", "连接时 + 手动刷新", "读取中断页", G],
            ["保护、评分", "网格 tag、预览", "目录字段 / 旧路径对象属性", "随目录", "读不到=不显示，开关置灰", T + " " + V],
            ["缩略图", "网格、胶片条、日期封面", "Wi‑Fi：缩略图请求", "可见窗口惰性", "占位色块", G + "（需解除页限定）"],
            ["预览图", "预览页", "Wi‑Fi：预览请求 / ARW 内嵌 JPEG", "当前 ± 1", "缩略图放大 + “预览不可用”", G],
            ["拍摄参数、定位、像素尺寸", "预览页", "Wi‑Fi：文件前 64 KB 的 EXIF", "仅当前张，停留 250 ms 后", "“—” / “不可用”，区分“未含位置”", T + " " + V],
            ["直方图", "预览页", "本地由预览图计算", "预览图就绪后", "不显示", T + " " + Z],
            ["已保存状态", "预览页", "本机下载记录", "下载完成时", "—", G],
            ["视图偏好（档位、三个标签开关）", "网格", "本机偏好存储", "修改即存，全局生效", "默认：小、全关", T]]
    body = f"""
<div class="row">
 <div class="col">
  {tbl(["数据", "显示位置", "来源", "刷新时机", "回退显示", "状态"], rows, "dense", ["17%", "12%", "23%", "18%", "19%", "11%"])}
  <div class="row" style="margin-top:6px">
   <div class="col"><div class="box"><h3>图例</h3>{G} 现有代码已提供 &nbsp; {T} 需新增 &nbsp; {V} 依赖相机行为，须真机确认 &nbsp; {Z} 不产生相机通信</div></div>
   <div class="col" style="flex:2"><div class="note">v1 数据层规格（<code>docs/design/history/ui-status-enhancement/SPEC.md</code>）中的字段定义、阈值与写入位置继续有效：
   校时状态、RSSI 读取、GPS 统计、电量缓存、设备信息、存储信息、保护评分。本版新增最后四类：EXIF/GPS、直方图、胶片条复用、视图偏好。</div></div>
  </div>
 </div>
</div>"""
    page("数据来源与刷新", "每一项状态从哪来、何时刷新、读不到时显示什么", body)

    # ------------------------------------------------------------------ 26 diff
    d1 = [["<b>离开应用不再断开蓝牙</b><br>（根页返回 / 划卡 / Activity 销毁）",
           "<code>MainActivity.kt:73-86</code> onDestroy(isFinishing) → disconnectAllOnExit + stopService<br><code>MainActivity.kt:124-138</code> 根页返回 finishAndRemoveTask()<br>"
           "<code>LocationSenderService.kt:170-181</code> onTaskRemoved → disconnectAllOnExit + stopSelf<br><code>AppServices.kt:76-80,89-91</code>；<code>AppExitCoordinator.kt:21-33</code>；<code>AppExitGate.kt:121-137</code>",
           "三个入口都改为<b>只结束 Wi‑Fi</b>（保留 <code>wifiRemote.closeAndJoin()</code>），去掉“停会话 / 断蓝牙 / 停服务”。根页返回改为普通回桌面（Q19）。"
           "AppExitGate 的属主/代际逻辑只为退出拆除而存在，可大幅简化；todo P3#8 的残余风险随之基本消失。"],
          ["重新打开应用时的自动重连", "<code>MainActivity.kt:164-189</code>（注释：退出会断开，所以打开要重连）", "保留作为兜底，但只针对“已启用且非用户主动断开”的相机；注释与测试同步更新。"],
          ["<b>保持不变</b>的路径", "Home/后台（<code>LocationSenderService.kt:164-169</code>）；断开单台 <code>ServiceCommandRouter</code> / <code>DeviceDisconnectCoordinator</code>；移除 <code>DeviceRemovalCoordinator</code>；"
           "开机重连 <code>ServiceShutdownCoordinator.kt:28-48</code>；服务真正销毁 <code>LocationSenderService.kt:184-201</code>；总开关 <code>isAppEnabled</code>", "不改。"],
          ["Home 时的 Wi‑Fi", "<code>AndroidWifiRemoteScreen.kt:87-96</code>（现状：后台保持 Wi‑Fi，离开该页才结束）", "按 Q17 的结论增加“离开前台超时结束”；下载中不结束。"],
          ["“保持活跃”语义", "<code>ServicePresenceCoordinator.kt:47-67</code>；<code>ConnectionDemandGate.kt:20-24</code>；<code>LocationSenderService.kt:77-109</code>", "逻辑不变；改名与默认值见 Q18；删除说明文字 <code>device_info_keep_active_hint</code>。"]]
    d2 = [["<b>移除自动赞助提醒</b>", "Android：<code>CameraDeviceManager.kt:45-48,82-83,143,178-214,252,424-449</code>；<code>MainActivity.kt:200-204,275-278</code>；"
           "<code>PreferencesManager.kt:23-26,148-195</code>；<code>DebugDonationDialogCard.kt</code>（整文件）；<code>SettingsScreen.kt:177</code><br>"
           "iOS：<code>IosAppDialogHost.kt:25-28,276-297</code>；<code>IosAppDialogState.kt:23,30-46,77-78,142-146</code>；<code>IosAppPreferences.kt:17-19,106-146</code>；<code>IosSettingsScreen.kt:275-280</code><br>"
           "字符串 <code>donation_dialog_*</code>（en + zh-rCN）", "全部删除。<b>保留</b> <code>HelpScreen.kt:274-287</code>、<code>IosHelpScreen.kt:225</code> 的手动链接。"],
          ["<b>帮助入口收敛</b>", "见第 21 页清单（<code>DeviceInfoDialogs.kt:91,153-154</code>、<code>WifiConnectionHelp.kt</code>、<code>DeviceListLayout.kt:26,45</code>、<code>SharedDeviceList.kt:382-394</code> 等）", "只留设备清单顶栏入口。"],
          ["<b>取消日期内分页</b>", "见第 16 页清单（<code>SonyImageTransfer.kt:456</code> 等）", "整日列表 + 惰性加载 + LRU。"],
          ["<b>主题蓝色化 + 玻璃</b>", "<code>Theme.kt:36,43-46</code>；<code>Color.kt</code>；<code>DeviceDetailScreen.kt:245-246</code>；<code>TransmissionDot.kt:36,62</code>", "固定蓝色色板；新增 <code>Glass.kt</code> 与 <code>GlassSurface</code>；API&lt;31 降级。"],
          ["设备页重排与断开入口", "<code>ui/device/DeviceDetailScreen.kt</code>（shared 306 行）；<code>DeviceDetailViewModel.disconnect</code>", "两卡 + 底部主入口；连接/断开的描边大按钮 → 卡内“连接”与淡化小图标 + 确认框；增加“连接中可取消”。"],
          ["Ⓘ 精简", "<code>DeviceInfoDialogs.kt</code>（222 行）", "✕ 关闭、✎ 重命名、去掉帮助与 OK、底部红色描边“移除相机”。"],
          ["设备清单", "<code>SharedDeviceList.kt:251-310</code> 左滑删除；<code>:314-416</code> DeviceCard", "去掉左滑删除；玻璃行；“添加相机”悬浮主按钮。"],
          ["连接过程改叠层", "<code>WifiRemoteScreen.kt</code>（757 行，按 WifiRemotePhase 整页切换）", "设备页之上的固定槽位叠层；蓝牙未连时前置蓝牙阶段。"],
          ["照片浏览顶栏", "<code>WifiPhotoTopBar.kt</code>（刷新 + 断开；选择态 全选本页 + 下载）", "去掉“断开”图标（返回即结束）；下载移到悬浮胶囊；全选=本日。"],
          ["视图选项与标签", "<code>WifiPhotoBrowser.kt</code>（632 行）", "小/中/大、三个开关、合并标签、全局持久化。"],
          ["预览页", "<code>WifiPhotoViewer.kt</code>（314 行）", "信息面板、胶片条、直方图、EXIF/GPS；删 ‹ › 按钮。"],
          ["通知", "<code>NotificationsHelper.kt:31-168</code>", "强调色、文案带相机名、同步中改静默渠道、按状态换小图标。"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1">
  <h2>A. 后台常驻语义（行为变更，改动面已核实）</h2>
  {tbl(["差异", "现有实现（文件:行）", "需要的调整"], d1, "xd", ["18%", "45%", "37%"])}
  <div class="note"><b>关于“推翻 v0.1.3 的退出断开”：</b>准确地说只改三条路径——根页返回、从最近任务划掉、Activity 结束时的销毁。Home/切后台一直就保持蓝牙与位置写入，本版只是把这一状态确立为规范并推广到另外三条路径。
  同时需更新 <code>docs/todo.md</code> P1#9 中“退出应用仍断开”的冒烟预期，以及相关单元测试（AppExitGate / AppExitCoordinator）。</div>
  <div class="box"><h3>字符串</h3>
  新增（en + zh-rCN）：断开蓝牙确认三句、照片传输入口与说明、叠层各阶段、“轻触任意处返回”、视图选项与三个开关、下载胶囊文案、预览页各信息标签与状态、通知文案。
  删除：<code>donation_dialog_*</code>、分页相关 <code>wifi_remote_browser_*</code>、<code>viewer_previous_photo/next_photo</code>、<code>device_info_keep_active_hint</code>、<code>wifi_connection_help_*</code>、<code>wifi_remote_close</code>。</div>
 </div>
 <div class="col" style="flex:1.12">
  <h2>B. 界面与功能</h2>
  {tbl(["差异", "现有实现（文件:行）", "需要的调整"], d2, "xd", ["17%", "50%", "33%"])}
  <div class="small">路径前缀：共享界面在 <code>sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/</code>，Android 壳在 <code>app/src/main/java/com/saschl/cameragps/</code>，
  iOS 在 <code>sharednew/src/iosMain/…/sharednew/</code>（iOS 目标默认禁用，源码需同步修改以保持可编译）。行号基于 HEAD 587f510。</div>
 </div>
</div>"""
    page("与现有实现的差异", "需开发调整的清单（文件:行）", body)

    # ------------------------------------------------------------------ 27 open questions + split
    qs = [["Q17", "Home / 切后台时 Wi‑Fi 会话何时结束？现状是一直保持。", "下载进行中保持到完成；否则离开前台 30 s 后结束（短暂切出、系统弹窗不受影响）；根页返回与划卡立即结束。"],
          ["Q18", "“保持活跃”是否改名为“相机离开后保持待命”？默认值？", "改名；默认开；仍不加说明文字。"],
          ["Q19", "根页返回的实现：结束界面回桌面，还是仅把应用移到后台？", "结束界面（不从最近任务移除）；服务与蓝牙常驻。两者对用户观感相同。"],
          ["Q20", "背景模糊的实现：引入 Haze 库（新依赖，跨平台）还是自研（仅 API 31+）？", "先自研最小实现（G3 浮层与对话框），G1/G2 用降级样式验证观感后再决定是否引入。"],
          ["Q21", "EXIF/GPS：每张预览自动读取（约 64 KB），还是展开详情时才读？", "自动读取 + 250 ms 防抖 + 缓存；定位状态是核心反馈，应当一眼可见。"],
          ["Q22", "配对准备页的引导链接、配对失败对话框的引导是否保留？", "准备页保留为流程内引导；失败对话框去掉“打开指南”，改为写清原因 + 重试。"],
          ["Q23", "日期列表是否显示封面缩略图（每个日期多 1 次缩略图请求）？", "显示，惰性加载；若实机偏慢则退回日历图标。"],
          ["Q24", "连接失败叠层的自动消退时长（5 秒）与“设备页保留一条失败原因”是否合适？", "按本稿。"],
          ["Q25", "同步中的常驻通知改到静默渠道（不再每次连接响一声由通知承担）是否接受？", "接受；声音完全交给“提示音”设置。"]]
    ms = [["M0", "主题与玻璃基础", "蓝色色板、背景光、<code>GlassSurface</code> 与 token、API&lt;31 降级、对比度测试；清理旧主题代码", "无"],
          ["M1", "后台常驻语义 + 移除赞助 + 帮助入口收敛", "三个退出入口改为只结束 Wi‑Fi；删除赞助自动弹窗（含 iOS）；帮助入口只留一处；更新测试与 todo 冒烟预期", "Q17 Q19 Q22"],
          ["M2", "设备清单、配对、设备页、Ⓘ", "新布局；断开蓝牙小图标 + 确认；连接中可取消；Ⓘ 精简；去左滑删除", "M0 · Q18"],
          ["M3", "状态数据层", "校时、RSSI、GPS 统计、电量缓存、固件/序列号、卡容量（v1 规格）", "部分待实机核实"],
          ["M4", "连接叠层", "固定槽位卡、分段环、蓝牙前置阶段、失败/断开态、自动消退", "M2 · Q24"],
          ["M5", "取消分页 + 网格", "整日列表、惰性加载与 LRU、小/中/大、视图选项浮层、合并标签、偏好持久化", "M0"],
          ["M6", "选择态与悬浮下载", "下载胶囊（展开/收起）、拖选贯穿整日、全选=本日、批量下载进度", "M5"],
          ["M7", "预览页（零通信部分）", "新布局、横/竖两态、胶片条、直方图、文件信息、详情面板、下载中/已保存", "M5"],
          ["M8", "EXIF / GPS 读取", "① 真机样张核实偏移 ② 解析器（JPG/ARW，HEIF 视核实结果）③ 接入预览页与缓存", "M7 · Q21 · 待实机核实"],
          ["M9", "设置、帮助、日志、通知 + 走查", "重绘；通知强调色与文案；暗色与 API 26/30/34 三档真机冒烟", "M0 · Q25"]]
    body = f"""
<div class="row">
 <div class="col" style="flex:1">
  <h2>待确认问题（仅列真正未决；编号接 v2 的 Q1–Q16）</h2>
  {tbl(["编号", "问题", "建议（无回复时按此推进）"], qs, "dense", ["7%", "43%", "50%"])}
  <div class="box"><h3>已定、无需再确认</h3>
  Q1–Q16 全部按最终结论落实：蓝牙/Wi‑Fi 断开语义区分、设备页返回不断蓝牙、仅蓝牙自动连接且尊重用户主动断开、点传输自动先连蓝牙、取消分页、大小口径、默认小且标签全关、
  视图偏好全局持久化、移除仅在 Ⓘ、帮助唯一入口与移除自动赞助、下载中锁定、Ⓘ 用 ✕、标题左对齐、蓝色主色。</div>
  <div class="note"><b>待实机核实（不是待用户决定）：</b>保护/评分字段、电量属性码、EXIF/GPS 子目录偏移（JPG/ARW/HEIF）、预览图是否自带完整 EXIF、连续缩略图请求下的稳定并发数。
  每一项在界面上都已有“读不到”的降级显示，核实结果只影响数据是否出现，不影响布局。</div>
 </div>
 <div class="col" style="flex:1.1">
  <h2>开发拆分建议</h2>
  {tbl(["里程碑", "主题", "内容", "依赖"], ms, "dense", ["8%", "22%", "50%", "20%"])}
  <div class="box b"><h3>顺序建议</h3>
  M0 → M1 可以先行且彼此独立（M1 是纯行为改动，风险集中、改动面小，适合单独发一个版本验证后台常驻）。
  M2–M4 是设备侧一条线，M5–M8 是照片侧一条线，两条线可并行。M9 收尾。</div>
  <div class="box"><h3>与 docs/todo.md 的关系</h3>
  本拆分<b>不复制进</b> <code>docs/todo.md</code>。开始开发某个里程碑时，再按待办纪律把当期要做的条目写入（要做什么 / 验收标准 / 依据位置），完成即删。
  可能因本设计而过期的现有条目：P1#9 的“退出仍断开”预期、P3#8（退出拆除的迟到投递风险）、P3#11（连接中无取消，M2 覆盖）。</div>
 </div>
</div>"""
    page("待确认与开发拆分", "Q17–Q25 · M0–M9", body)


def E_notif(dark):
    import screens3b as B
    import lib3 as L
    s, w, h = B.notif_shade(dark)
    return L.svg_wrap(w, h, s), w, h
