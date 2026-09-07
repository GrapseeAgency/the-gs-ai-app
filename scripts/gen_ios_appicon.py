#!/usr/bin/env python3
"""Generate the iOS AppIcon (1024x1024) — the GS aurora orb on the Aeruo
obsidian canvas, mirroring the Android adaptive icon. Rendered at 2x and
downsampled for clean edges. Output: ios/App/Assets.xcassets/AppIcon.appiconset/AppIcon.png
"""
from PIL import Image, ImageDraw, ImageFilter

SCALE = 2                    # render at 2048, downsample to 1024
S = 1024 * SCALE
OBSIDIAN_TOP = (10, 13, 18)  # #0A0D12
OBSIDIAN_BOT = (13, 17, 25)  # subtle vertical lift
ACCENT = (45, 212, 168)      # #2DD4A8
ACCENT_DEEP = (15, 163, 126) # #0FA37E

img = Image.new("RGB", (S, S), OBSIDIAN_TOP)
px = img.load()

# --- background: gentle vertical gradient -----------------------------------
for y in range(S):
    t = y / (S - 1)
    r = int(OBSIDIAN_TOP[0] + (OBSIDIAN_BOT[0] - OBSIDIAN_TOP[0]) * t)
    g = int(OBSIDIAN_TOP[1] + (OBSIDIAN_BOT[1] - OBSIDIAN_TOP[1]) * t)
    b = int(OBSIDIAN_TOP[2] + (OBSIDIAN_BOT[2] - OBSIDIAN_TOP[2]) * t)
    for x in range(0, S, 1):
        px[x, y] = (r, g, b)

# --- orb: concentric radial-gradient discs (teal -> deep) --------------------
center = S // 2
orb_r = int(S * 0.26)        # same 52/108 share of canvas as the Android orb
steps = 360
for i in range(steps, 0, -1):
    t = i / steps            # 1 = outer edge, 0 = core
    r = int(ACCENT[0] + (ACCENT_DEEP[0] - ACCENT[0]) * t)
    g = int(ACCENT[1] + (ACCENT_DEEP[1] - ACCENT[1]) * t)
    b = int(ACCENT[2] + (ACCENT_DEEP[2] - ACCENT[2]) * t)
    rad = int(orb_r * i / steps)
    bbox = [center - rad, center - rad, center + rad, center + rad]
    ImageDraw.Draw(img).ellipse(bbox, fill=(r, g, b))

img = img.convert("RGBA")
overlay = Image.new("RGBA", (S, S), (0, 0, 0, 0))
od = ImageDraw.Draw(overlay)

# --- specular highlight, upper-left of the orb, softly blurred --------------
hl_r = int(orb_r * 0.28)
hx, hy = center - int(orb_r * 0.42), center - int(orb_r * 0.42)
od.ellipse([hx - hl_r, hy - hl_r, hx + hl_r, hy + hl_r], fill=(255, 255, 255, 110))
overlay = overlay.filter(ImageFilter.GaussianBlur(int(orb_r * 0.14)))

# --- faint halo ring ---------------------------------------------------------
ring_r = int(S * 0.335)      # matches the Android 33.5/108 ring
lw = max(2, int(S * 0.0016))
od2 = ImageDraw.Draw(overlay)
od2.ellipse([center - ring_r, center - ring_r, center + ring_r, center + ring_r],
            outline=(45, 212, 168, 64), width=lw)

img = Image.alpha_composite(img, overlay).convert("RGB")
img = img.resize((1024, 1024), Image.LANCZOS)
out = "/home/z/my-project/ios/App/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
img.save(out, "PNG")
print("wrote", out, img.size)
