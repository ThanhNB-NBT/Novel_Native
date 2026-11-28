"""Sinh các kiểu logo để người dùng chọn trong app (Tài khoản → Màu sắc → Biểu tượng app).

Chạy: python tools/gen_icon_styles.py → ghi res/drawable-nodpi/ic_logo_<kiểu>.webp (432px = cả khung 108dp,
chữ GT nằm trong vùng an toàn 66dp) và res/mipmap-anydpi-v26/ic_launcher_<kiểu>.xml.
Không sinh ở đây: "holo" (mặc định, gen_icon_holo.py) và "classic" (gradient XML ic_launcher_bg + chữ GT trắng gốc).
Danh sách kiểu phải khớp iconStyles trong AppIcon.kt và các activity-alias trong AndroidManifest.xml.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import gen_icon_holo as g

S, OUT, RES = g.S, g.OUT, g.RES
rng = np.random.default_rng(3)
yy, xx = np.mgrid[0:S, 0:S] / S
hexc = lambda h: np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)]) / 255

# màu lấy từ Theme.kt: giấy dó, mực, và màu nhấn (accents) Son/Ngọc/Chàm/Hoàng
PAPER, INK, SEAL, GOLD = hexc("F3EBDD"), hexc("2B2118"), hexc("B8322A"), hexc("D6A85E")
ACCENTS = {"son": "9A3B26", "ngoc": "2E6B55", "cham": "34508A", "hoang": "8A5A12"}

m_img = g.gt_mask()
m = np.asarray(m_img, dtype=float) / 255


def paper():
    """Giấy dó: vân sợi mịn + xơ giấy ngắn, sẫm nhẹ ở rìa."""
    tex = g.fbm(S, 21, base=6) - 0.5
    vign = ((xx - .5) ** 2 + (yy - .5) ** 2)[..., None]
    im = g.to_img(PAPER * (1 + 0.06 * tex[..., None]) - 0.10 * vign)
    d = ImageDraw.Draw(im, "RGBA")
    for _ in range(260):
        x, y = rng.uniform(0, S, 2); a = rng.uniform(0, np.pi); L = rng.uniform(15, 55)
        d.line((x, y, x + np.cos(a) * L, y + np.sin(a) * L), fill=(120, 95, 60, 26), width=2)
    return im


def seal(im, cx, cy, size, color):
    """Ấn triện: ô vuông, chữ 閣 khắc âm, mực ấn loang lổ."""
    s = Image.new("L", (S, S), 0); d = ImageDraw.Draw(s); h = size / 2
    d.rounded_rectangle((cx - h, cy - h, cx + h, cy + h), size * 0.08, fill=255)
    f = ImageFont.truetype("C:/Windows/Fonts/simsun.ttc", int(size * 0.78))
    d.text((cx, cy + size * 0.02), "閣", font=f, anchor="mm", fill=0, stroke_width=int(size * 0.025), stroke_fill=0)
    a = np.asarray(s, dtype=float) / 255 * np.clip((g.fbm(S, 33, base=40) - 0.16) * 5, 0, 1)
    im.alpha_composite(g.to_img(np.zeros((S, S, 3)) + color, a * 0.92))


def foil(warm):
    """Foil hologram pha vàng nhũ cho hợp tông giấy/son, không neon."""
    return np.clip(g.film(S, 11, 0.1) * (1 - warm) + GOLD * warm, 0, 1)


def stamp(im, rgb, shadow_col, shadow_a, hi):
    """Ép chữ GT: bóng chìm lệch xuống, phủ màu/foil, viền sáng mép trên."""
    sh = np.roll(np.asarray(m_img.filter(ImageFilter.GaussianBlur(5)), dtype=float) / 255, 7, axis=0)
    im.alpha_composite(g.to_img(np.zeros((S, S, 3)) + shadow_col, sh * shadow_a))
    im.alpha_composite(g.to_img(rgb, m))
    im.alpha_composite(g.to_img(np.ones((S, S, 3)), np.clip(m - np.roll(m, 5, axis=0), 0, 1) * hi))


def sparkles(im, col, halo=None):
    d = ImageDraw.Draw(im, "RGBA")
    for x, y, r in [(.70, .33, 34), (.34, .67, 20), (.64, .70, 14)]:
        if halo: g.sparkle(d, x * S, y * S, r * 1.25, halo)
        g.sparkle(d, x * S, y * S, r, col)


def pearl():
    """Hologram ngọc trai: nền be phớt óng ánh, chữ mực tím than viền foil."""
    holo = g.film(S, 11, shift=0.15)
    vign = np.clip(1 - 0.55 * ((xx - .5) ** 2 + (yy - .45) ** 2) * 2.2, 0, 1)[..., None]
    im = g.to_img(np.array([0.965, 0.95, 0.93]) * (0.9 + 0.1 * vign) * 0.7 + 0.3 * holo)
    g.scratches(im, holo, 45, 2, 55)
    rim = np.clip(np.asarray(m_img.filter(ImageFilter.MaxFilter(13)), dtype=float) / 255 - m, 0, 1)
    ink = np.array([0.13, 0.12, 0.24]); fo = np.clip(holo * 1.1 + 0.12, 0, 1)
    im.alpha_composite(g.to_img(ink * m[..., None] + fo * 0.85 * (1 - m)[..., None], np.maximum(m, rim)))
    sparkles(im, (255, 255, 255, 255), (120, 100, 200, 90))
    return im


def paper_seal():
    """Giấy dó + chữ foil + ấn son."""
    im = paper()
    stamp(im, foil(0.25) * 0.78, INK, 0.35, 0.25)
    seal(im, S * 0.655, S * 0.665, S * 0.14, SEAL)
    sparkles(im, (255, 248, 230, 240))
    return im


def band():
    """Giấy dó + chữ son, dải foil chéo hẹp lướt qua chữ."""
    im = paper()
    b = np.exp(-((xx + yy - 0.95) ** 2) * 40)[..., None]
    stamp(im, hexc(ACCENTS["son"]) * (1 - b) + foil(0.15) * b, INK, 0.3, 0.2)
    sparkles(im, (255, 248, 230, 240))
    return im


def cover(hx):
    """Bìa màu nhấn của app, chữ GT ép foil hologram."""
    base = hexc(hx)
    rgb = base + 0.05 * (g.fbm(S, 5, base=6)[..., None] - .5) - 0.10 * ((xx - .5) ** 2 + (yy - .5) ** 2)[..., None]
    im = g.to_img(rgb)
    stamp(im, foil(0.2), base * 0.25, 0.55, 0.3)
    sparkles(im, (255, 245, 225, 240))
    return im


STYLES = {"pearl": pearl, "paper": paper_seal, "band": band} | {k: (lambda h=h: cover(h)) for k, h in ACCENTS.items()}

XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- sinh bởi tools/gen_icon_styles.py -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_logo_{k}" />
    <foreground android:drawable="@android:color/transparent" />
    <monochrome android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
"""

if __name__ == "__main__":
    for k, fn in STYLES.items():
        p = RES / f"drawable-nodpi/ic_logo_{k}.webp"
        fn().convert("RGB").resize((OUT, OUT), Image.LANCZOS).save(p, quality=90, method=6)
        assert Image.open(p).size == (OUT, OUT)
        (RES / f"mipmap-anydpi-v26/ic_launcher_{k}.xml").write_bytes(XML.format(k=k).encode())
        print("ok", k, p.stat().st_size // 1024, "KB")
