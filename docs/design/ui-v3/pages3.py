"""alpha-gps UI design v3 — build entry: render mockups (SVG) → index.html → ui-design-v3.pdf.

Usage:  cd docs/design/ui-v3 && python3 pages3.py
Needs:  python3, google-chrome (headless print-to-pdf), CJK fonts (Noto / Source Han Sans).
"""
import os
import subprocess
import sys

import doc3 as D
import screens3 as A
import screens3b as B
from doc3 import shot

TOTAL = 30


def mockups():
    # ---- device list / onboarding / pairing
    shot("list_normal", lambda: A.device_list_screen("normal", callouts=True))
    shot("list_empty", lambda: A.device_list_screen("empty"))
    shot("list_perm", lambda: A.device_list_screen("perm"))
    shot("welcome", A.welcome_screen)
    shot("pair_prep", lambda: A.pairing_prep_screen("idle"))
    shot("pair_search", lambda: A.pairing_prep_screen("search"))
    shot("pair_fail", A.pairing_failed_dialog)
    # ---- device page + dialogs
    shot("dev_ok", lambda: A.device_screen("ok"))
    shot("dev_ok_c", lambda: A.device_screen("ok", callouts=True))
    shot("dev_off", lambda: A.device_screen("off"))
    shot("dev_warn", lambda: A.device_screen("warn"))
    shot("dlg_bt", A.bt_disconnect_dialog)
    shot("info_c", lambda: A.info_dialog(callouts=True))
    shot("dlg_rename", A.rename_dialog)
    shot("dlg_remove", A.remove_dialog)
    # ---- connection overlay
    shot("ov_ble", lambda: A.overlay_screen("ble", 5, 0, bg="off"))
    shot("ov_prepare", lambda: A.overlay_screen("prepare", 5, 1))
    shot("ov_approve", lambda: A.overlay_screen("approve", 5, 2))
    shot("ov_session", lambda: A.overlay_screen("session", 5, 3))
    shot("ov_catalog", lambda: A.overlay_screen("catalog", 5, 4))
    shot("ov_ready", lambda: A.overlay_screen("ready", 5, 5))
    shot("ov_fail", lambda: A.overlay_screen("fail", 5, 2))
    shot("ov_blefail", lambda: A.overlay_screen("ble_fail", 5, 0, bg="off"))
    shot("ov_perm", lambda: A.overlay_screen("perm_fail", 5, 2))
    shot("ov_closing", lambda: A.overlay_screen("closing", 5, 0))
    # ---- dates / grid
    shot("dates_c", lambda: B.date_list_screen("normal", callouts=True))
    shot("dates_empty", lambda: B.date_list_screen("empty"))
    shot("dates_err", lambda: B.date_list_screen("error"))
    shot("grid_s", lambda: B.grid_screen("S"))
    shot("grid_m", lambda: B.grid_screen("M", fmt=True, size=True, pr=True))
    shot("grid_l", lambda: B.grid_screen("L", fmt=True, size=True, pr=True))
    shot("grid_panel", lambda: B.grid_screen("M", fmt=True, size=True, pr=True, panel=True, callouts=True))
    shot("grid_sel", lambda: B.grid_screen("M", fmt=True, size=True, pr=True, selecting=3, pill="idle"))
    shot("grid_drag", lambda: B.grid_screen("M", selecting=5, sel_set=(1, 2, 4, 5, 7), pill="mini", drag=True, scrolled=True))
    shot("grid_dl", lambda: B.grid_screen("M", fmt=True, size=True, pr=True, selecting=3, pill="dl"))
    # ---- viewer (always dark inside)
    shot("view_land", lambda: B.viewer_screen("land"))
    shot("view_land_c", lambda: B.viewer_screen("land", callouts=True))
    shot("view_port", lambda: B.viewer_screen("port", card2=True))
    shot("view_full", lambda: B.viewer_screen("port", opts=("params", "lens", "hist")))
    shot("view_params", lambda: B.viewer_screen("land", opts=("params",)))
    shot("view_sheet", lambda: B.viewer_screen("land", sheet=True))
    shot("view_loading", lambda: B.viewer_screen("land", exif="loading", gps="loading", opts=("params",)))
    shot("view_fail", lambda: B.viewer_screen("land", exif="fail", gps="fail", opts=("params",)))
    shot("view_nogps", lambda: B.viewer_screen("land", gps="none"))
    shot("view_dl", lambda: B.viewer_screen("land", state="downloading"))
    shot("view_saved", lambda: B.viewer_screen("port", state="saved"))
    # ---- settings / help / logs
    shot("settings", B.settings_screen)
    shot("settings_sub", B.settings_sub_screen)
    shot("logs", B.logs_screen)
    shot("help_c", lambda: B.help_screen(callouts=True))
    shot("trouble", B.troubleshoot_screen)
    shot("about_c", lambda: B.about_screen(callouts=True))
    shot("licenses", B.licenses_screen)
    # ---- dark set
    shot("d_list", lambda: A.device_list_screen("normal"), "dark")
    shot("d_dev_ok", lambda: A.device_screen("ok"), "dark")
    shot("d_ov", lambda: A.overlay_screen("approve", 5, 2), "dark")
    shot("d_dates", lambda: B.date_list_screen("normal"), "dark")
    shot("d_grid_panel", lambda: B.grid_screen("M", fmt=True, size=True, pr=True, panel=True), "dark")
    shot("d_grid_sel", lambda: B.grid_screen("M", fmt=True, size=True, pr=True, selecting=3, pill="idle"), "dark")
    shot("d_info", A.info_dialog, "dark")
    shot("d_dlg_bt", A.bt_disconnect_dialog, "dark")
    shot("d_settings", B.settings_screen, "dark")
    shot("d_help", B.help_screen, "dark")


def optimize(path):
    """Lossless structural recompression (object streams + deflate); skipped when pymupdf is absent."""
    try:
        import pymupdf
    except ImportError:
        return
    tmp = path + ".tmp"
    doc = pymupdf.open(path)
    doc.save(tmp, garbage=4, deflate=True, deflate_images=True, deflate_fonts=True, use_objstms=1)
    doc.close()
    os.replace(tmp, path)


def main():
    mockups()
    import content3a
    import content3b
    content3a.build()
    content3b.build()
    assert len(D.pages) == TOTAL, f"expected {TOTAL} pages, got {len(D.pages)}"
    html = os.path.join(D.HERE, "index.html")
    with open(html, "w", encoding="utf-8") as f:
        f.write(D.render_html())
    out = os.path.join(D.HERE, "ui-design-v3.pdf")
    if "--html-only" in sys.argv:
        print("wrote", html)
        return
    subprocess.run(["google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox", "--no-pdf-header-footer",
                    "--allow-file-access-from-files", f"--print-to-pdf={out}", "file://" + html],
                   check=True, timeout=300, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    optimize(out)
    print("wrote", out, os.path.getsize(out), "bytes;", len(D.F), "mockup entries")


if __name__ == "__main__":
    main()
