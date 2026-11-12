<p align="left"><img width="96" src="docs/brand/alpha-gps-icon.svg" alt="AlphaGPS icon" /></p>

# AlphaGPS

**通过蓝牙向索尼 Alpha 相机写入 GPS 位置与时间，并通过相机 Wi-Fi 浏览、下载照片。**

AlphaGPS 是 [Saschl/alpha-gps](https://github.com/Saschl/alpha-gps)（GPL-3.0）的个人定制分支，
由 mfreceiver 自 2026-11 起维护。不通过任何应用市场分发，需自行构建安装。

## 功能

- **GPS 位置与时间写入** —— 在获得授权后读取手机位置，经蓝牙低功耗（BLE）写入配对的索尼相机，
  照片在相机端直接完成地理标记；时间与时区信息同步写入。
- **相机 Wi-Fi 照片浏览与下载** —— 连接相机 Wi-Fi 后浏览相机内照片：
  - 按日期分组（最新在前）的图库，缩略图懒加载；
  - 多选与长按拖动跨日区间选择；
  - 下载格式：**每次询问**（默认）、**JPG**、**RAW** 或 **JPG + RAW**；不含所选格式的照片自动跳过，
    并在结果汇总中计数。
- **后台保持与自动重连** —— 基于 companion device 的连接方式配合前台服务；可选“相机离开后待机”
  模式，保持服务就绪以自动重连。

仅支持英文与简体中文，仅支持 Android。

## 支持的相机

凡是支持与 Imaging Edge Mobile 或 Creators' App 蓝牙位置联动的索尼相机原则上均可使用。
以下型号的 GPS 地理标记已经上游用户实测确认：

| 型号 | 备注 | Issues 链接 |
| --- | --- | --- |
| A1 | — | — |
| [A7 III](https://github.com/Saschl/alpha-gps/issues/260) | — | [#260](https://github.com/Saschl/alpha-gps/issues/260) |
| [A7 IV](https://github.com/Saschl/alpha-gps/issues/301) | — | [#301](https://github.com/Saschl/alpha-gps/issues/301) |
| [A7 V](https://github.com/Saschl/alpha-gps/issues/79) | — | [#79](https://github.com/Saschl/alpha-gps/issues/79) |
| A7C II | 经邮件确认 | — |
| [A7R III](https://github.com/Saschl/alpha-gps/issues/95#issuecomment-5609520240) | 需在相机菜单启用 **Location Info. Link** | [#95](https://github.com/Saschl/alpha-gps/issues/95#issuecomment-5609520240) |
| [A7R V](https://github.com/Saschl/alpha-gps/issues/199) | — | [#199](https://github.com/Saschl/alpha-gps/issues/199) |
| [A99 II](https://github.com/Saschl/alpha-gps/issues/294) | GPS 已确认；报告中时间校正与区域调整被禁用 | [#294](https://github.com/Saschl/alpha-gps/issues/294) |
| [A6400](https://github.com/Saschl/alpha-gps/issues/195) | — | [#195](https://github.com/Saschl/alpha-gps/issues/195) |
| [A6500](https://github.com/Saschl/alpha-gps/issues/301) | 地理标记已确认；其余功能受相机能力限制 | [#301](https://github.com/Saschl/alpha-gps/issues/301) |
| [A6700](https://github.com/Saschl/alpha-gps/issues/294) | — | [#294](https://github.com/Saschl/alpha-gps/issues/294) |
| [ZV-1](https://github.com/Saschl/alpha-gps/issues/100) | — | [#100](https://github.com/Saschl/alpha-gps/issues/100) |
| [ZV-E10](https://github.com/Saschl/alpha-gps/issues/67#issuecomment-3678222970) | — | [#67](https://github.com/Saschl/alpha-gps/issues/67#issuecomment-3678222970) |

以上为用户报告，不构成保证。仅 Wi-Fi 联动的机型（如
[A6000](https://github.com/Saschl/alpha-gps/issues/236)）无法使用蓝牙地理标记。
欢迎到 [GitHub Issues](https://github.com/mfreceiver/alpha-gps/issues) 反馈实测结果。

## 系统要求

- Android 12（API 31）及以上，仅 **arm64**（`arm64-v8a`；不支持 32 位与 x86 设备）。
- 支持蓝牙位置联动的索尼相机（见上表）用于 GPS 写入；相机 Wi-Fi 用于照片浏览/下载。
- 仅 Android：iOS 应用与 Apple 相关源集已从本仓库移除。

## 构建

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

构建与发版细节见 [AGENTS.md](AGENTS.md) 与 `scripts/release_build.sh`。发版是手动确认的步骤。

## 来源与致谢

本项目是 [Saschl/alpha-gps](https://github.com/Saschl/alpha-gps)（GPL-3.0）的分支，许可证仍为 GPL-3.0。
上游项目又得益于：

- [anoulis/sony_camera_bluetooth_external_gps](https://github.com/anoulis/sony_camera_bluetooth_external_gps) ——
  弄清索尼蓝牙 GPS 协议的 Python 脚本。
- [mlapaglia/AlphaSync](https://github.com/mlapaglia/AlphaSync) —— 为使用 Creators' App（而非
  Imaging Edge）的设备启用该功能的经验。

如需支持**上游原作者（Saschl）**，见其 [Buy Me A Coffee](https://buymeacoffee.com/wj8tism4dq) 页面。

**源码：** [GitHub](https://github.com/mfreceiver/alpha-gps)
**反馈：** [GitHub Issues](https://github.com/mfreceiver/alpha-gps/issues)

## 商标声明

AlphaGPS 与索尼公司无关联，未获其认可或赞助。"Sony" 与 "Alpha" 是 Sony Group Corporation 的商标。
本项目是与其相机互操作的独立工具。

## 隐私

AlphaGPS 不收集、不上传任何数据，详见 [privacy.md](privacy.md)。
