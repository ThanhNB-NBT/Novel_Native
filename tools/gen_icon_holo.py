"""Sinh icon app kiểu sticker hologram từ chữ GT có sẵn; một bản nền tối dùng cho cả giao diện sáng lẫn tối.

Chạy: python tools/gen_icon_holo.py  → ghi 2 PNG 432px (108dp @xxxhdpi) vào res/drawable-nodpi.
Màu óng ánh dùng cùng công thức với shader Holo.kt: RGB = cosin của độ dày màng mỏng.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
OUT = 432
S = 1296  # vẽ to gấp 3 rồi thu nhỏ cho mịn
rng = np.random.default_rng(7)


def value_noise(n, cells, seed):
    g = np.random.default_rng(seed).random((cells + 1, cells + 1))
    x = np.linspace(0, cells, n, endpoint=False)
    i = x.astype(int); f = x - i; u = f * f * (3 - 2 * f)
    a = g[np.ix_(i, i)]; b = g[np.ix_(i, i + 1)]; c = g[np.ix_(i + 1, i)]; d = g[np.ix_(i + 1, i + 1)]
    ux = u[None, :]; uy = u[:, None]
    return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy


def fbm(n, seed, base=3):
    return sum(value_noise(n, base * 2 ** k, seed + k) * 0.5 ** (k + 1) for k in range(4))


def film(n, seed, shift=0.0):
    """Màu màng mỏng 0..1, shape (n,n,3); fbm làm độ dày gồ ghề như khuấy sơn."""
    yy, xx = np.mgrid[0:n, 0:n] / n
    th = 0.45 + 1.5 * fbm(n, seed, base=4) + 0.9 * (xx + yy)  # trôi chéo → nhiều dải màu chạy theo đường chéo
    # biên độ 0,38 quanh 0,6 → óng ánh dịu (tím oải hương, ngọc, hồng) thay vì RGB neon
    return 0.6 + 0.38 * np.cos(6.2832 * (th[..., None] * np.array([1.0, 1.18, 1.37]) + shift))


def to_img(rgb, alpha=None):
    a = np.ones(rgb.shape[:2]) if alpha is None else alpha
    arr = np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)])
    return Image.fromarray((arr * 255).astype(np.uint8), "RGBA")


def scratches(img, colors, count, width, alpha):
    """Nét cào như tranh cào: đường cong mảnh, màu lấy từ lớp hologram bên dưới."""
    d = ImageDraw.Draw(img, "RGBA")
    for _ in range(count):
        x, y = rng.uniform(0, S, 2); ang = rng.uniform(-0.9, -0.3)  # đa số chéo lên phải
        pts = []
        for _ in range(int(rng.integers(6, 14))):
            pts.append((x, y)); ang += rng.normal(0, 0.12)
            step = rng.uniform(25, 60); x += np.cos(ang) * step; y += np.sin(ang) * step
        cx, cy = (int(min(max(pts[0][0], 0), S - 1)), int(min(max(pts[0][1], 0), S - 1)))
        r, g, b = (colors[cy, cx] * 255).astype(int)
        d.line(pts, fill=(r, g, b, int(alpha * rng.uniform(.5, 1))), width=width, joint="curve")


def sparkle(d, x, y, r, col):
    """Sao lấp lánh 4 cánh."""
    w = r * 0.18
    d.polygon([(x, y - r), (x + w, y - w), (x + r, y), (x + w, y + w), (x, y + r), (x - w, y + w), (x - r, y), (x - w, y - w)], fill=col)


def gt_mask():
    src = Image.open(RES / "drawable-nodpi/ic_launcher_fg.png").getchannel("A")
    m = src.resize((S, S), Image.LANCZOS)
    # chữ GT gốc chiếm ~50% khung; phóng lên ~1,06 lần quanh tâm cho đậm hơn nhưng vẫn trong vùng an toàn 66dp
    k = 1.06; big = m.resize((int(S * k), int(S * k)), Image.LANCZOS)
    o = (big.width - S) // 2
    return big.crop((o, o, o + S, o + S))


def make():
    holo = film(S, 11)
    yy, xx = np.mgrid[0:S, 0:S] / S
    vign = np.clip(1 - 0.55 * ((xx - .5) ** 2 + (yy - .45) ** 2) * 2.2, 0, 1)[..., None]

    # nền: giấy cào đen ánh tím
    base = np.array([0.055, 0.06, 0.12]) * vign + np.array([0.03, 0.0, 0.06]) * (1 - vign)
    bg = to_img(base + 0.10 * holo * fbm(S, 3)[..., None])
    scratches(bg, holo, 110, 3, 95)
    d = ImageDraw.Draw(bg, "RGBA")
    for _ in range(40):  # bụi sao nền
        x, y = rng.uniform(0, S, 2); r = rng.uniform(2, 5)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, int(rng.uniform(60, 160))))

    # chữ GT: khắc foil hologram rực + quầng sáng
    m = np.asarray(gt_mask(), dtype=float) / 255
    glow = np.asarray(gt_mask().filter(ImageFilter.GaussianBlur(18)), dtype=float) / 255
    foil = np.clip(holo * 1.1 + 0.12, 0, 1)
    fg = to_img(foil, np.maximum(m, glow * 0.3))
    d = ImageDraw.Draw(fg, "RGBA")
    for x, y, r in [(.70, .33, 46), (.33, .66, 30), (.64, .70, 20)]:  # vài sao lóe quanh chữ
        sparkle(d, x * S, y * S, r, (255, 255, 255, 235))

    out = RES / "drawable-nodpi"
    bg.convert("RGB").resize((OUT, OUT), Image.LANCZOS).save(out / "ic_launcher_holo_bg.png", optimize=True)
    fg.resize((OUT, OUT), Image.LANCZOS).save(out / "ic_launcher_holo_fg.png", optimize=True)
    return out


if __name__ == "__main__":
    o = make()
    for f in ("ic_launcher_holo_bg.png", "ic_launcher_holo_fg.png"):
        im = Image.open(o / f)
        assert im.size == (OUT, OUT), (f, im.size)
    print("ok", o)
