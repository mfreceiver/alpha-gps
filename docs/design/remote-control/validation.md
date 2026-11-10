# 设计交付检查

日期：2026-11-10。仅文档生成与渲染验证，无应用构建或相机测试。

## 结果

- [设计 PDF](remote-control-design.pdf)：**16 页，3,830,576 bytes（约 3.65 MiB）**，小于 8MB；页面 1050 × 750 pt，对应 HTML 1400 × 1000px。
- [HTML 源稿](remote-control-design.html)：Chrome 无页眉页脚打印；标题、页码完整。SVG 为相对引用，查看时请保留 screens 目录。
- 26 个 SVG 均通过 XML 解析。文字、源稿未含本机用户目录绝对路径；PDF metadata 不含本机源路径，未嵌入外部图片或网络资源。
- 所有 16 页导出 PNG 并逐页目视检查：没有发现页面级裁切、文字超出纸面、正文重叠或空页；每页文本提取都包含对应页码。
- 对照 v3 第 10 页设备页、第 12 页连接叠层：复用 v3 原语，保持设备卡、蓝色、亮暗、玻璃层级及胶囊操作。
- 修正后重新导出并复查：第 6 页 GPS 互斥实例改为 Alpha 6400；第 7 页对焦按钮加入面板内边距并使半按选中与状态文字一致；第 8 页失败态用警告图标代替进度环。
- `page-01.png` 至 `page-16.png` 是最终 PDF 的检查渲染；`baseline-4.png`、`baseline-10.png`、`baseline-12.png` 是只读 v3 对照页导出。PNG 仅供复核，PDF/SVG 是正式矢量交付。

## 可重现导出

从仓库根执行（需要 Python、Chrome；检查渲染需要 PyMuPDF）：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 docs/design/remote-control/build_design.py
google-chrome --headless --no-sandbox --disable-gpu --no-pdf-header-footer \
  --print-to-pdf=docs/design/remote-control/remote-control-design.pdf \
  "file://$PWD/docs/design/remote-control/remote-control-design.html"
```

生成脚本只写本目录，读取 v3 图形库；不修改 v3 文件，不生成其 Python 缓存。字体使用系统 Source Han Sans CN / Noto Sans CJK，换机重建应安装同类字体并重新检查排版。

## 已确认决策与保留缺口

- 已纳入用户确认：Home / 后台 / 划卡 / 根页返回结束 Wi-Fi，BLE+GPS 常驻；遥控与照片传输一致。当前 Android ON_STOP 保留 Wi-Fi 明确列为待改差异。
- 未运行 Gradle、Kotlin 单测、Android 仪器测试；未声称 BLE/PTP/HTTP 协议或参数写入已经实机通过。
- 取景画面为原创几何示意，参数为交互示例，不代表 Alpha 7 IV 或其它机型已经支持全部功能。
- 未做 Android 真机大字体、TalkBack、横屏或 Haze 性能验证；它们已列入开发验收。
- 未修改应用代码、构建文件、待办、v3 定稿；未进行 git add/commit/push。并行工作可能改动其它文件，不应归因于本交付。
- 结束时复核待办 #13 仍未完成：本次只完成研究设计，尚未评审批准或实施，不移除该待办。既有退出断连的旧验收条目与已确认 v3 方向不一致，后续实施时统一调整；本次按范围限制不编辑待办。
