# -*- coding: utf-8 -*-
"""Dev-only contact sheet: python3 preview2.py <group>  -> /tmp/prev_<group>.png"""
import sys, subprocess, os
import lib2
from lib2 import set_theme, phone_frame
import screens2 as S

G = sys.argv[1] if len(sys.argv) > 1 else "a"
set_theme("light")
ph = lambda body, sc=0.9: phone_frame(body, scale=sc)
items = []
if G == "a":
    items = [ph(S.legacy_device_screen()), ph(S.device_screen("ok", callouts=True)), ph(S.device_screen("off")), ph(S.device_screen("warn"))]
elif G == "b":
    items = [ph(S.device_screen("ok", overlay=S.ov_card(k))) for k in ("prepare", "approve", "join", "catalog", "ready")]
elif G == "b2":
    items = [ph(S.device_screen("ok", overlay=S.ov_card(k))) for k in ("fail", "ble", "ble_fail", "closing")]
elif G == "c":
    items = [ph(S.info_legacy()), ph(S.info_dialog(True, True)), ph(S.rename_dialog()), ph(S.remove_dialog())]
elif G == "d":
    items = [ph(S.date_list_screen(callouts=True)), ph(S.grid_screen("S", callouts=True)), ph(S.grid_screen("S", selecting=3)), ph(S.grid_screen("M", size=True, panel=True))]
elif G == "e":
    items = [ph(S.grid_screen("S")), ph(S.grid_screen("M", size=True)), ph(S.grid_screen("L", size=True, ext=True))]
elif G == "f":
    items = [ph(S.viewer_screen("normal", callouts=True)), ph(S.viewer_screen("downloading", callouts=True)), ph(S.viewer_screen("saved")), ph(S.viewer_screen("failed")), ph(S.viewer_screen("swipe"))]
elif G == "g":
    set_theme("dark")
    items = [ph(S.device_screen("warn")), ph(S.grid_screen("M", size=True, ext=True)), ph(S.device_screen("ok", overlay=S.ov_card("join"))), ph(S.info_dialog(True))]
html = '<html><body style="margin:0;background:#888;display:flex;gap:8px;padding:8px">' + "".join(items) + "</body></html>"
p = f"/tmp/prev_{G}.html"
open(p, "w").write(html)
n = len(items)
subprocess.run(["google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
                f"--window-size={n * 360 + 20},780", f"--screenshot=/tmp/prev_{G}.png", "file://" + p],
               capture_output=True, timeout=60)
print("ok", G)
