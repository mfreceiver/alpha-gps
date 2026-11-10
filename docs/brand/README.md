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

本项目以 GPL-3.0 授权。这些图标由 mfreceiver 为 AlphaGPS 原创绘制（手写 SVG 路径），不取自上游旧 `icon.svg`（已随 W1 清理删除）或任何第三方素材，随本项目以 GPL-3.0 发布。

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

## Android 启动器图标：已接入

本套图标已于提交 323d827 接入 Android 启动器图标（含独立 monochrome 层）；本目录仅保留设计源与导出方法，无需再改 app 代码。
