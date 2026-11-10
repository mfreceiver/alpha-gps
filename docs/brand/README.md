# AlphaGPS 项目图标

AlphaGPS 的原创项目图标：**取景框四角**（相机取景）+ **定位针**（GPS 位置）+ **针头内的相机镜头**，右上一道琥珀色**信号弧**表示位置正在发送给相机。背景为主色蓝色渐变，叠一层极淡的经纬网格。纯矢量，无位图、无文字/字体，不使用任何索尼商标或 Alpha 标志。

## 文件

| 文件 | 尺寸 | 用途 |
| --- | --- | --- |
| `alpha-gps-icon.svg` | 1024×1024，圆角方形（rx 228），含背景 | 文档、README、关于页、商店素材 |
| `alpha-gps-icon-foreground.svg` | viewBox 108×108，透明底 | Android 自适应图标前景层；所有图形位于中心 r ≤ 33 的 66dp 安全圆内（取景角 32–76，含描边约 30.3–77.7） |
| `alpha-gps-icon-background.svg` | viewBox 108×108，铺满 | Android 自适应图标背景层（渐变 + 经纬网格） |
| `alpha-gps-icon-monochrome.svg` | viewBox 108×108，纯 `#000` 形状、透明底 | Android 13+ 主题图标（monochrome 层，系统按壁纸着色） |

主图标与前景层是同一套图形：主图标中前景按 108 单位坐标绘制，以 `translate(-190 -190) scale(13)` 放大居中。

## 配色 token

| Token | 色值 | 用在 |
| --- | --- | --- |
| primary（亮） | `#0B57D0` | 背景渐变中段、镜头玻璃中段 |
| primary（暗） | `#A8C7FA` | 镜头玻璃高光 |
| primary-light | `#2F7BF0` | 背景渐变起点（左上） |
| deep blue | `#062E6F` | 背景渐变终点、镜头外圈、光圈、定位针投影 |
| primary-container | `#D3E3FD` | 定位针底部渐变 |
| white | `#FFFFFF` | 取景角、定位针、镜头反光 |
| amber accent | `#FFC857` | 信号弧 |
| cyan accent | `#7FE3F0` | 镜头内的小反光点 |

## 原创声明

本项目以 GPL-3.0 授权。这些图标由 mfreceiver 为 AlphaGPS 原创绘制（手写 SVG 路径），不取自仓库现有的 `icon.svg` 或任何第三方素材，随本项目以 GPL-3.0 发布。

## 导出 PNG

本机已验证可用的是 Python `cairosvg`（`rsvg-convert`、`inkscape` 未安装，有的话也可以用）：

```bash
# cairosvg（本机已验证）
python3 -c "import cairosvg; cairosvg.svg2png(url='docs/brand/alpha-gps-icon.svg', write_to='/tmp/alpha-gps-icon-512.png', output_width=512, output_height=512)"

# 批量导出常用尺寸
for s in 48 96 192 512 1024; do
  python3 -c "import cairosvg,sys; cairosvg.svg2png(url='docs/brand/alpha-gps-icon.svg', write_to=f'/tmp/alpha-gps-icon-{sys.argv[1]}.png', output_width=int(sys.argv[1]), output_height=int(sys.argv[1]))" $s
done

# librsvg / Inkscape（如已安装）
rsvg-convert -w 512 -h 512 docs/brand/alpha-gps-icon.svg -o /tmp/alpha-gps-icon-512.png
inkscape docs/brand/alpha-gps-icon.svg --export-type=png --export-width=512 --export-filename=/tmp/alpha-gps-icon-512.png
```

自适应图标预览：把背景层与前景层按同一尺寸叠加，再裁出中心 72/108 区域并套圆形/圆角方形遮罩，就能模拟桌面上的效果。

## 待办：接入 Android 启动器图标（开发项，本目录不改 app 代码）

当前 app 的启动器图标仍然用的是 `app/src/main/res/mipmap-anydpi/ic_launcher.xml`，引用 `@drawable/ic_launcher_background` / `@drawable/ic_launcher_foreground`，monochrome 层也直接复用了前景层。要接入本套图标：

1. 用 Android Studio「Vector Asset」或 `svg2vectordrawable` 把三个 108×108 SVG 转成 VectorDrawable XML，替换 `drawable/ic_launcher_background.xml`、`drawable/ic_launcher_foreground.xml`，另外新增 `drawable/ic_launcher_monochrome.xml`。转换后检查渐变是否保留（VectorDrawable 支持 `<gradient>`，需 `aapt:attr` 内联写法）。
2. 把 `mipmap-anydpi/ic_launcher.xml` 的 `<monochrome>` 改为引用 `@drawable/ic_launcher_monochrome`。minSdk 是 26，因此 `mipmap-anydpi` 与 `mipmap-anydpi-v26` 效果相同。
3. 视需要同步更新 `fastlane/metadata/android/en-US/images/icon.png`（用 `alpha-gps-icon.svg` 导出 512px）。
4. 跑 `AGENTS.md` 中的验证命令，并在真机上检查圆形、圆角方形和主题图标三种形态。

开始做这项工作时，按 `AGENTS.md` 的待办纪律写进 `docs/todo.md`。
