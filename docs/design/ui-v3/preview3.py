"""Quick contact-sheet preview: python3 preview3.py out.png name=expr ...  (dev helper)"""
import subprocess, sys, os
import lib3 as L
def sheet(items, out, scale=0.62, theme="light", cols=6):
    L.set_theme(theme)
    h = "<html><body style='margin:8px;background:#ddd;display:flex;flex-wrap:wrap;gap:8px'>"
    for body in items:
        h += L.phone_frame(body(), scale=scale)
    h += "</body></html>"
    p = "/tmp/prev3.html"; open(p, "w").write(h)
    n = len(items); rows = (n + cols - 1) // cols
    w = int(cols * (390 * scale + 8) + 16); hh = int(rows * (844 * scale + 12) + 16)
    subprocess.run(["google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox", f"--window-size={w},{hh}",
                    f"--screenshot={out}", "file://" + p], capture_output=True, timeout=120)
