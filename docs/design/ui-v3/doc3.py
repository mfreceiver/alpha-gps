"""AlphaGPS UI design v3 — document framework: mockup registry, HTML helpers, page list, CSS."""
import os

import lib3 as L

HERE = os.path.dirname(os.path.abspath(__file__))
MOCK = os.path.join(HERE, "mockups")
os.makedirs(MOCK, exist_ok=True)
F = {}
pages = []
DOC = "AlphaGPS · 界面重设计 UI 设计方案 v3（第三轮修订 · 定稿）"


def save(key, svg):
    with open(os.path.join(MOCK, key + ".svg"), "w", encoding="utf-8") as f:
        f.write(svg)
    F[key] = "mockups/" + key + ".svg"


def shot(key, builder, theme="light"):
    """Render one phone screen into mockups/<key>.svg under the given theme."""
    L.set_theme(theme)
    save(key, L.phone_frame(builder()))
    L.set_theme("light")


def phone(key, scale=0.74, cap=None):
    w, h = 390 * scale, 844 * scale
    c = f"<figcaption>{cap}</figcaption>" if cap else ""
    return f'<figure class="ph"><img src="{F[key]}" width="{w:.0f}" height="{h:.0f}">{c}</figure>'


def wide(key, tup=None, scale=1.0, cap=None):
    """tup = (svg, w, h) → saved under key; returns <figure>."""
    if tup is not None:
        save(key, tup[0])
        F[key + "_wh"] = (tup[1], tup[2])
    w, h = F[key + "_wh"]
    c = f"<figcaption>{cap}</figcaption>" if cap else ""
    return f'<figure class="wd"><img src="{F[key]}" width="{w * scale:.0f}" height="{h * scale:.0f}">{c}</figure>'


def n(i):
    return f'<span class="n">{i}</span>'


def page(title, sub, body):
    pages.append((title, sub, body))


def tbl(head, rows, cls="", widths=None):
    cg = ""
    if widths:
        cg = "<colgroup>" + "".join(f'<col style="width:{w}">' for w in widths) + "</colgroup>"
    h = "".join(f"<th>{x}</th>" for x in head)
    b = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    return f'<table class="{cls}">{cg}<tr>{h}</tr>{b}</table>'


def ul(items, cls=""):
    return f'<ul class="{cls}">' + "".join(f"<li>{x}</li>" for x in items) + "</ul>"


CSS = """
@page { size: 1400px 1000px; margin: 0 }
* { box-sizing: border-box }
html, body { margin: 0; padding: 0 }
body { font-family: 'Source Han Sans CN','Noto Sans CJK SC','Noto Sans CJK JP',sans-serif; color: #191C20; font-size: 13px; line-height: 1.5 }
.page { width: 1400px; height: 1000px; padding: 24px 36px 16px; page-break-after: always; overflow: hidden; position: relative;
        background: linear-gradient(135deg, #F7F9FE 0%, #FFFFFF 45%, #F4F1FD 100%) }
.hd { display: flex; align-items: baseline; gap: 14px; border-bottom: 2px solid #0B57D0; padding-bottom: 7px; margin-bottom: 12px }
.hd h1 { font-size: 23px; margin: 0; color: #0B57D0; font-weight: 700; white-space: nowrap }
.hd .sub { color: #42474E; font-size: 13px; flex: 1 }
.hd .no { color: #72777F; font-size: 11.5px; white-space: nowrap }
.row { display: flex; gap: 16px; align-items: flex-start }
.col { display: flex; flex-direction: column; gap: 10px; flex: 1; min-width: 0 }
h2 { font-size: 15px; margin: 0 0 4px; color: #041E49 }
h3 { font-size: 13.5px; margin: 0 0 3px; color: #0B57D0 }
p { margin: 0 0 5px }
ul { margin: 0; padding-left: 17px } li { margin: 1.5px 0 }
table { border-collapse: collapse; width: 100%; font-size: 12px; table-layout: fixed }
th { background: #0B57D0; color: #fff; text-align: left; padding: 5px 7px; font-weight: 600; font-size: 12px }
td { border-bottom: 1px solid #D9DEE7; padding: 4px 7px; vertical-align: top; word-break: break-word }
tr:nth-child(odd) td { background: rgba(237,241,247,.55) }
table.dense td, table.dense th { padding: 3px 6px; font-size: 11.3px; line-height: 1.38 }
table.xd td, table.xd th { padding: 2.5px 5px; font-size: 10.6px; line-height: 1.34 }
.tag { display: inline-block; padding: 0 6px; border-radius: 4px; font-size: 10.8px; font-weight: 600; line-height: 17px; white-space: nowrap }
.tag.g { background: #C8F0D2; color: #00210D } .tag.r { background: #FFDAD6; color: #410002 }
.tag.b { background: #D3E3FD; color: #041E49 } .tag.y { background: #FFE9B3; color: #261900 } .tag.z { background: #E1E6ED; color: #42474E }
figure { margin: 0 } figure.ph, figure.wd { text-align: center }
figcaption { font-size: 11.5px; color: #42474E; margin-top: 4px; line-height: 1.35 }
.note { background: #FFF6DA; border-left: 4px solid #F9A825; padding: 6px 10px; border-radius: 6px; font-size: 12px }
.box { background: rgba(255,255,255,.72); border: 1px solid #D9DEE7; padding: 8px 11px; border-radius: 12px; font-size: 12.2px;
       box-shadow: 0 2px 10px rgba(11,87,208,.06) }
.box.b { background: rgba(211,227,253,.5); border-color: #A8C7FA }
.ok { color: #1B6E3A; font-weight: 600 } .todo { color: #BA1A1A; font-weight: 600 } .warn { color: #7A5200; font-weight: 600 }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: 10.8px; background: rgba(225,230,237,.8); padding: 0 3px; border-radius: 3px; overflow-wrap: anywhere }
.small { font-size: 11.3px; color: #42474E } .xs { font-size: 10.6px; color: #42474E }
.n { display: inline-block; width: 17px; height: 17px; border-radius: 50%; background: #E5322D; color: #fff; text-align: center;
     font-size: 11px; font-weight: 700; line-height: 17px; margin-right: 3px }
.sw { display: inline-block; width: 54px; height: 30px; border-radius: 7px; border: 1px solid rgba(0,0,0,.12); vertical-align: middle }
.cover h1 { font-size: 40px; color: #0B57D0; margin: 4px 0 2px } .cover .lead { font-size: 15px; color: #42474E }
"""


def render_html():
    total = len(pages)
    out = [f'<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>{DOC}</title><style>{CSS}</style></head><body>']
    for i, (title, sub, body) in enumerate(pages, 1):
        out.append(f'<section class="page"><div class="hd"><h1>{title}</h1><div class="sub">{sub}</div>'
                   f'<div class="no">{DOC} · {i} / {total}</div></div>{body}</section>')
    out.append("</body></html>")
    return "".join(out)
