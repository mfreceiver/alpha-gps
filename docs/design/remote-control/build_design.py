"""Generate the remote-control review deck; only writes beside this script."""
import sys, os, html
from pathlib import Path
sys.dont_write_bytecode = True
OUT = Path(__file__).resolve().parent
sys.path.insert(0, str(OUT.parent / 'ui-v3'))
import lib3 as L
from lib3 import *
import screens3 as S
(OUT / 'screens').mkdir(exist_ok=True)

def card(y, title, lines, h=130):
    s=glass(16,y,358,h,26,1)+t(34,y+30,title,16,600)
    for i,line_ in enumerate(lines): s+=t(34,y+58+i*23,line_,13,400,C['onSurfV'])
    return s

def base(title): return S.base()+top_bar(title)+nav_pill()
def tag(y,label): return info_tag(30,y,label,False)[0]
def buttons(y,a,b): return primary_btn(36,y,154,52,a)+tonal_btn(200,y,154,52,b)
def dialog(s,title,lines,left='取消',right='继续'):
    s+=scrim()+glass(24,270,342,280,28,3)+t(46,314,title,21,600)
    for i,l in enumerate(lines): s+=t(46,351+i*24,l,14,400,C['onSurfV'])
    return s+dlg_btns(44,480,302,left,right)

def scene(kind):
    if kind=='device':
        s=S.device_screen('ok')
        s+=tonal_btn(16,568,358,52,'遥控拍摄','camera')
        return s
    if kind=='choose':
        s=base('遥控拍摄')+card(116,'选择连接方式',['蓝牙快门 · 不传输画面','Wi-Fi 取景 · 耗电较高'],138)
        s+=card(280,'蓝牙快门',['用于三脚架自拍、避免手震'],160)+tonal_btn(34,373,322,48,'连接蓝牙快门','bluetooth')
        s+=card(466,'Wi-Fi 取景',['先连接蓝牙，再连接相机 Wi-Fi'],160)+primary_btn(34,559,322,48,'启动实时取景','wifi')
        return s+t(24,686,'仅进入本页不会自动连接。',13,400,C['onSurfV'])
    if kind.startswith('ble'):
        state=kind[4:] if len(kind)>3 else 'ready'
        titles={'ready':'蓝牙遥控已就绪','press':'正在发送拍摄请求','lost':'蓝牙连接已断开','disabled':'相机遥控未开启','gps':'位置联动已暂停'}
        s=base('蓝牙快门')+card(112,titles.get(state,titles['ready']),[('Alpha 6400' if state=='gps' else 'Alpha 7 IV')+' · 无实时画面','拍摄参数请在相机上调整'],130)
        s+=card(266,'位置传输',['GPS 写入状态以设备页为准','互斥机型需先关闭相机蓝牙遥控'],110)
        s+=glass(16,406,358,258,28,1)+t(195,445,'单次拍摄',17,600,anchor='middle')
        disabled=state in ('lost','disabled','press')
        s+=primary_btn(74,480,242,80,'发送中…' if state=='press' else '拍摄','camera',20,disabled=disabled)
        s+=t(195,605,'一次轻触，完成一次按下与释放',13,400,C['onSurfV'],'middle')
        if state in ('lost','disabled'): s+=tonal_btn(16,706,358,52,'重新连接' if state=='lost' else '重新检查')
        else: s+=tonal_btn(16,706,358,52,'切换到 Wi-Fi 取景','wifi')
        if state=='gps': s=dialog(s,'位置写入将暂停',['此机型不能同时使用位置联动','与蓝牙遥控。结束后请在相机','关闭蓝牙遥控以恢复位置写入。'],'取消','继续遥控')
        return s
    if kind=='focus':
        s=scene('ble_ready')
        s+=scrim()+glass(16,402,358,382,28,3)+t(36,440,'独立对焦 · 后续验证项',18,600)
        s+=t(36,477,'按住发送，松开立即释放',14)+buttons(504,'半按快门','AF-ON')
        s+=t(36,593,'当前：按住半按快门',15,600,C['primary'])
        s+=t(36,626,'发送中 ≠ 已合焦',14,400,C['onSurfV'])
        return s+dlg_btns(36,708,318,None,'关闭')
    if kind.startswith('connect'):
        s=scene('device')+scrim()+glass(28,230,334,374,28,3)
        state=kind.split('_')[-1]
        title={'approval':'等待系统连接许可','opening':'正在准备遥控会话','failed':'无法建立遥控会话'}.get(state,'正在连接相机 Wi-Fi')
        s+=(icon('warning',163,273,64,C['error']) if state=='failed' else spinner(195,305,34))+t(195,386,title,20,600,anchor='middle')
        s+=t(195,422,'蓝牙 → Wi-Fi → 遥控 → 首帧',13,400,C['onSurfV'],'middle')
        s+=t(195,448,'失败不改变照片传输入口的用途',13,400,C['onSurfV'],'middle')
        return s+dlg_btns(48,532,294,'返回' if state=='failed' else '取消','重试' if state=='failed' else None)
    if kind.startswith('wifi') or kind in ('params','iso','readonly','uncertain','transfer','leave'):
        s=base('Wi-Fi 遥控')+tag(108,'遥控会话 · 参数与画面为示意')
        s+=rect(16,144,358,239,'#182831',20)
        s+='<path d="M16 340 L110 204 L220 300 L290 226 L374 338 L374 383 L16 383Z" fill="#416F79"/>'
        s+=circle(300,193,20,'#D5E7E9')
        s+=t(32,171,'实时取景',12,600,'#fff')+t(32,366,'构图示意 · 非相机实拍',11,400,'#fff')
        s+=card(404,'曝光参数',['M    1/125 s    F5.6    ISO 400','−0.3 EV    WB 自动'],105)
        s+=tonal_btn(16,531,174,48,'参数设置')+tonal_btn(200,531,174,48,'照片传输')
        s+=primary_btn(82,620,226,80,'拍摄','camera',20)
        s+=t(195,748,'画面与拍摄能力分别判断',13,400,C['onSurfV'],'middle')
        if kind=='wifi_stale':
            s+=rect(16,144,358,239,'#000',20,op=.7)+t(195,250,'画面已暂停',21,600,'#fff','middle')+t(195,281,'最后一帧 · 5 秒前',14,400,'#fff','middle')+tonal_btn(105,308,180,48,'重试取景')
        if kind=='wifi_lost': s=dialog(s,'连接已断开',['拍摄与参数设置不可用。','请检查相机电源和 Wi-Fi。'],'返回','重新连接')
        if kind=='uncertain': s=dialog(s,'拍摄结果未确认',['请求可能已被相机执行。','请检查相机，避免重复拍摄。'],'返回','我知道了')
        if kind=='transfer': s=dialog(s,'切换到照片传输？',['将暂停取景并释放遥控。','旧模式相机会重新建立会话。'],'取消','切换')
        if kind=='leave': s=dialog(s,'结束遥控？',['将结束 Wi-Fi 遥控会话。','蓝牙连接与位置同步继续。'],'取消','结束')
        if kind in ('params','readonly','iso'):
            s+=scrim()+glass(16,330,358,464,28,3)+t(36,368,'拍摄参数 · 能力门控',19,600)
            for i,(a,b) in enumerate([('快门速度','1/125 s'),('光圈','F5.6'),('ISO','400'),('曝光补偿','−0.3 EV'),('白平衡','自动')]):
                y=394+i*58
                s+=t(36,y+29,a,15)+tonal_btn(215,y,137,48,b)
            s+=dlg_btns(36,724,318,None,'关闭')
            if kind=='readonly':
                s+=rect(24,390,342,304,C['bg'],20,op=.95)+t(40,439,'光圈  F5.6',18,600)+t(40,483,'相机当前模式下只读',14)+t(40,515,'请在相机上调整拍摄模式。',14)+t(40,559,'不支持的项目不出现。',14)
            if kind=='iso':
                s+=rect(24,390,342,320,C['bg'],20)+t(40,424,'ISO · 相机返回的合法档位',15,600)
                for j,v in enumerate(['自动','100','200','400','800','1600']):
                    x=36+(j%3)*108; y=450+(j//3)*70
                    s+=primary_btn(x,y,100,48,v) if v=='400' else tonal_btn(x,y,100,48,v)
                s+=t(40,622,'点选后应用，读回后更新数值。',13)+t(40,653,'失败保留上一次确认值。',13)
        return s
    return base('遥控拍摄')

pages=[]
def add(title,subtitle,phones,sections): pages.append((title,subtitle,phones,sections))
add('遥控拍摄：轻触快门，看见构图','01 / 方向 · 在 v3 上增加能力，而非重画整个应用',[('device','light'),('wifi','dark')],[('本轮交付','Android 12+ · 固定蓝色 · 玻璃层级沿用 v3。只研究与设计，不修改应用。'),('两条明确路径','蓝牙快门适合轻量触发；Wi-Fi 取景提供画面与能力门控参数。照片传输保持独立入口。'),('事实、目标、待验证','BLE 与 Wi-Fi 底层保留 ≠ UI 已恢复；曝光参数写入是新能力；示意画面与参数不构成实机支持证明。')])
add('能力边界先于按钮','02 / 技术事实 · 产品路径与协议能力分开',[('choose','light')],[('现有：可接回','BLE 全/半按与 AF-ON 命令；快门序列及释放。Wi-Fi PTP 单拍、HTTP 取景、浏览/下载与会话切换。'),('当前产品：browseOnly','Android 固定 browseOnly=true，只进入传输会话。并非协议 v4 天生没有 HTTP 取景。恢复要按入口指定会话用途。'),('后续：不能假装现成','曝光属性访问权限/档位解析、写入读回、独立 Wi-Fi 对焦尚需建设。录像、变焦、BULB、触摸对焦不纳入。'),('证据','research.md 第 3–5 节：逐项代码路径、行号及 Sony 官方机型限制。静态核查，未做相机测试。')])
add('入口不抢走照片传输','03 / 设备页 → 遥控选择 → 用户主动连接',[('device','light'),('choose','dark')],[('设备页增量','保留相机与 GPS 两张卡、底部照片传输主按钮。中部增加次级“遥控拍摄”，不占用 Ⓘ 与帮助。'),('连接意图','进入选择页不连接；点连接蓝牙快门或启动取景才触发。用户先前主动断开不被页面导航覆盖。'),('返回语义','选择页返回无网络副作用；遥控页返回释放按键与遥控会话，BLE/GPS 保留。明确“断开蓝牙”仍走原确认。')])
add('蓝牙快门 · 大按钮，少承诺','04 / Ready · 亮 / 暗一致',[('ble_ready','light'),('ble_ready','dark')],[('主操作','单次拍摄：按下与释放组成一个序列，不把按住等同连拍。提交即防重入，待释放后再允许新触发。'),('信息层级','相机与遥控状态在上，GPS 独立卡，底部切换 Wi-Fi。无实时画面时不放假取景框。'),('结果语言','写入入队只能说“请求已发送”；没有可靠捕获事件不显示“已拍摄”或“已保存”。'),('触控与朗读','主按钮 80dp；其余至少 48dp。TalkBack：“拍摄，单次蓝牙快门”；状态变化适度播报，不逐包通知。')])
add('蓝牙状态必须可以解释','05 / 发送中 · 相机未开启 · 断连',[('ble_press','light'),('ble_disabled','dark')],[('准备中 / 未开启','停用拍摄，提示相机菜单条件；“重新检查”重新探测，不循环弹连接框。特征缺失时说明不支持并提供返回。'),('发送中','按钮固定位置，忙态不改变页面高度；只保证已提交，不保证照片生成。超时退出等待，不伪造合焦成功。'),('失联','冻结操作，不自动重发；重连为显式动作。释放失败只能 best-effort，提示检查相机，不承诺相机端已松开。'),('探测约束','当前探测 500ms 后开始、每 3s；仅遥控页面前台需要。切 Wi-Fi 先取消并等待序列释放。')])
add('旧机 GPS 互斥：先告知，再选择','06 / α6400 官方限制，不泛化所有机型',[('ble_gps','light'),('ble_lost','dark')],[('明确代价','α6400 开启蓝牙遥控时不能使用手机位置联动，且不会进入省电模式。启用前确认，不把 GPS 失败藏在后台。'),('恢复路径','结束遥控并不会替用户改相机菜单。提示关闭相机蓝牙遥控，再根据真实位置写入恢复状态。'),('新机也不盲信','型号/固件验证矩阵 + 运行时结果。现有文案“较新机型支持”不足以构成兼容保证。'),('官方来源','Sony α6400 Help Guide / Bluetooth Rmt Ctrl；完整 URL 与引用边界见 research.md 第 5 节。')])
add('独立对焦 · 渐进开放','07 / 第二阶段目标，不与单次拍摄混淆',[('focus','light'),('focus','dark')],[('按住语义','半按快门与 AF-ON 分别匹配命令；一指按下，松开/移出/取消/导航/后台全部触发释放，禁止两路同时持有。'),('无障碍替代','提供“开始对焦 / 停止对焦”动作，明确保持状态；退出自动停止。不要求所有用户必须长按。'),('不虚构合焦','AF-S / AF-C / MF 结果各异。发送成功不是已合焦；Wi-Fi 当前 500ms 延时更不是合焦反馈。'),('上线门控','BLE 先验证按键生命周期；Wi-Fi 需新增独立对焦接口与能力。未验证时不显示可用操作。')])
add('Wi-Fi 连接 · 可取消的阶段链','08 / 连接叠层继承 v3 固定槽位',[('connect_approval','light'),('connect_failed','dark')],[('顺序与所有权','释放 BLE 按键 → claim Wi-Fi → 准备相机 → 系统连接许可 → 加入网络 → 遥控会话 → 首帧。'),('取消是贯穿的','每个阶段可取消。取消后清理网络请求、PTP、取景与所有者；迟到回调不能打开页面或恢复连接。'),('权限与错误','沿用 Android 分版本权限；被拒显示“去授权”，不是协议错误。另台相机占用时不抢占。系统许可短暂停顿与真正后台区分。'),('失败回退','Wi-Fi 失败允许返回蓝牙选择，但不自动补拍。重试只来自明确点击，未知相机不无限探测。')])
add('实时取景 · 画面优先，控制可达','09 / 拟议 Ready 画面 · 参数值仅为示例',[('wifi','light'),('wifi','dark')],[('取景区域','保持相机画面宽高比，留黑边不裁切；示意图为 3:2。界面不覆盖关键构图；首帧前只显示等待占位。'),('操作区域','参数卡与拍摄分离；照片传输是次级操作，进入前停取景。曝光参数只在描述符与读回能力完成后开放。'),('数据状态','controlReady、liveViewReady、propertyReady 独立；画面故障不自动断开仍可用的快门链。'),('布局适配','竖屏主方案；横屏把取景与控件左右分区。字体放大时控件区滚动，拍摄按钮不被系统导航覆盖。')])
add('取景失败，不等于拍摄失败','10 / 冻结提示 · 拍摄不确定',[('wifi_stale','dark'),('uncertain','light')],[('帧的新鲜度','建议 >2s 无新帧标“画面已暂停”（需实机校准）；保留最后帧但显著遮罩，不能让旧画面看起来仍实时。'),('重试边界','“重试取景”只重建图像流，控制能力有效时快门仍可用。当前实现 503 有界重试 3 次 / 15s，不扩展成无限重连。'),('拍摄结果','只有可信捕获事件显示相机确认。超时/失联后显示结果未确认；既不显示成功，也不自动重发。'),('本地与相机','相机确认拍摄不等于已下载手机。此页不显示“已保存到手机”；传输结果由照片下载页面报告。')])
add('曝光参数不是静态按钮表','11 / 参数面板 · 合法值来自相机',[('params','light'),('iso','dark')],[('能力描述符','每个参数需要类型、access、form、当前值、可选枚举/范围与会话版本。未知属性不写；不硬编码跨型号档位。'),('交互','点参数打开 G3 面板；候选值来自当前模式。点选提交，显示应用中，响应后读回才替换确认值。'),('串行与失效','一个参数写入期间禁冲突操作；切模式、切会话、相机物理操作后重新核实值/权限。过期回包不覆盖新状态。'),('现状缺口','当前属性解析消耗但丢弃 access 与枚举/范围。EXIF 拍摄参数不可当作实时参数编辑来源。')])
add('只读、未知、失败：不同的样子','12 / 不可写要说原因，而非到处灰按钮',[('readonly','dark'),('wifi_lost','light')],[('支持矩阵','Unsupported 隐藏；Unknown 显示 —；ReadOnly 显示值及原因，不弹可写菜单；Writable 才允许选择。'),('提交失败','保留旧确认值，提示“未能应用，仍为 ISO 400”；可手动重试，不把乐观值永久留在屏幕上。'),('连接断开','全部写操作不可用；清除按住状态，显示重连。重新建立会话后重新读取能力，不复用旧的写权限。'),('第一期边界','先恢复取景与快门，参数默认只读/不展示；完成单型号单属性闭环后再扩大白名单。')])
add('照片传输与遥控，共用一个所有者','13 / 切换前先释放，不同时抢控制',[('transfer','light'),('connect_opening','dark')],[('取景 → 传输','禁止新按键/参数写入，等待或取消现有事务，释放按键，停流，再打开浏览。分离模式需替换 PTP 会话。'),('传输 → 取景','下载进行中不静默抢占；明确取消并等待结束，或留在传输。会话切换后旧目录句柄按协议能力失效。'),('combined 不等于并发随意','v4 combined 代码路径仍可能有 HTTP 取景；产品仍串行化拍摄与传输以避免资源争用。是否无需重建须实机验证。'),('失败收敛','切换失败显示目标模式失败，释放旧资源；用户可返回设备页。不得同时显示“取景就绪”和“传输占用”。')])
add('退出与后台：Wi-Fi 结束，蓝牙常驻','14 / 用户确认采用 v3 · 遥控与照片传输一致',[('leave','light'),('device','dark')],[('页面返回','释放遥控按键与 Wi-Fi 会话，返回设备页。普通空闲返回直接收尾；有未决拍摄/写入时提示后果，不重发动作。'),('Home / 后台 / 锁屏','结束 Wi-Fi，停止取景和前台遥控监控；蓝牙+GPS 继续，互斥机依相机菜单能力。回到前台不自动再启动取景。'),('划卡 / 根页返回','同样结束 Wi-Fi，保留 BLE 前台位置服务。只有用户主动断开蓝牙才持久化断连意图。'),('需改的现状','AndroidWifiRemoteScreen 当前 ON_STOP 保留 Wi-Fi。必须改为上述语义，且遥控、照片传输统一，不宣称现有行为已满足。')])
add('统一组件与状态视觉','15 / 沿用 v3，不新增一套遥控主题',[('ble_ready','light'),('params','dark')],[('层级与色彩','G1 卡片 / G2 悬浮 / G3 面板；固定 #0B57D0，暗色 #A8C7FA。标签小方角不可点，操作胶囊有按下反馈。无分隔线。'),('操作规范','主动作图标+文字；关闭/返回等通用图标有描述与长按提示。对话框左取消右确认，48dp 等宽、10dp 间距，单动作全宽。'),('真实玻璃与可读性','SVG 仅静态模拟；实际使用 Haze。画面上文字配深色底，避免复杂取景透过控件损害对比度。'),('可访问与小屏','正文不靠颜色传达状态；大字体允许换行、滚动。按住控件提供替代动作，禁用状态附原因，反馈不高频打断读屏。')])
add('落地顺序与验收门槛','16 / 设计不代替实机验证',[('choose','dark')],[('R1 · BLE 单次恢复','接入口与前台监控，明确入队/确认语义，防重复、离页释放、GPS 互斥提示及双语文案。'),('R2 · Wi-Fi 取景与单拍','按用途建会话；取景/控制独立；取消和后台清理；传输切换。覆盖 separate / combined 两类实机。'),('R3 / R4 · 参数与独立对焦','描述符、写入读回、模式变化与超时回滚；半按/AF-ON 生命周期。未验证属性不得上线为可写。'),('必须通过','重复点击、触控取消、断连、系统许可拒绝、迟到回包、后台/划卡、下载占用、捕获事件缺失。记录型号/固件/Android/协议。'),('本次范围','仅本目录设计文件。未运行 Gradle、未修改应用、未提交。完整依据、风险与文件生成记录见 research.md / validation.md。')])

css='''@page{size:1400px 1000px;margin:0}*{box-sizing:border-box}body{margin:0;background:#dce3ed;font-family:"Source Han Sans CN","Noto Sans CJK SC",sans-serif;color:#191c20}.page{width:1400px;height:1000px;padding:40px 48px;background:#f3f6fc;break-after:page;position:relative;overflow:hidden}.page:last-child{break-after:auto}h1{font-size:34px;margin:0 0 8px;letter-spacing:-.5px}.sub{color:#53657d;font-size:17px;margin-bottom:26px}.content{display:flex;gap:30px;height:790px}.phones{display:flex;gap:18px;align-items:flex-start}.phone img{width:328px;height:710px}.phone small{display:block;text-align:center;color:#53657d;margin-top:8px;font-size:14px}.notes{flex:1;padding:4px 0}.box{padding:17px 20px;border-radius:20px;background:#fff9;margin:0 0 14px}.box h2{font-size:21px;margin:0 0 8px;color:#0b57d0}.box p{font-size:17px;line-height:1.65;margin:0}.footer{position:absolute;bottom:24px;left:48px;right:48px;font-size:13px;color:#65758a;display:flex;justify-content:space-between}@media screen{.page{margin:20px auto;box-shadow:0 3px 20px #19374d22}}'''
body=[]
for n,(title,sub,phones,sections) in enumerate(pages,1):
    imgs=[]
    for k,theme in phones:
        set_theme(theme)
        fn=f'{k}-{theme}.svg'
        (OUT/'screens'/fn).write_text(phone_frame(scene(k)),encoding='utf-8')
        imgs.append(f'<div class="phone"><img src="screens/{fn}" alt="{html.escape(k)} {theme}"><small>{"亮色" if theme=="light" else "暗色"} · 390 × 844 dp 示意</small></div>')
    notes=''.join(f'<section class="box"><h2>{html.escape(a)}</h2><p>{html.escape(b)}</p></section>' for a,b in sections)
    body.append(f'<article class="page"><h1>{title}</h1><div class="sub">{sub}</div><div class="content"><div class="phones">{"".join(imgs)}</div><div class="notes">{notes}</div></div><footer class="footer"><span>AlphaGPS · 遥控恢复设计 · v3 增补 · 2026-11-10 · 研究稿 / 未实现</span><span>{n} / {len(pages)}</span></footer></article>')
(OUT/'remote-control-design.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>AlphaGPS 遥控恢复设计</title><style>'+css+'</style><body>'+''.join(body)+'</body></html>',encoding='utf-8')
print('Generated',len(pages),'pages and',len(list((OUT/'screens').glob('*.svg'))),'SVG screens')
