"""Draws Sva's seasonal wallpapers (no third-party artwork).

    python3 tools/seasons/make_wallpapers.py app/src/main/res/drawable-nodpi

Every scene is painted from noise, gradients and simple shapes with a fixed seed, so the output
is reproducible. The Home puts its glass shelf and dock over the lower half, so the interest of
each scene sits in the upper middle.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1600, 900


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def lerp(a, b, t):
    return a + (b - a) * t


def gradient(stops):
    """Vertical sky: stops = [(y 0..1, '#hex'), ...]."""
    ys = np.linspace(0, 1, H)
    img = np.zeros((H, W, 3), np.float32)
    for i in range(len(stops) - 1):
        y0, c0 = stops[i]
        y1, c1 = stops[i + 1]
        m = (ys >= y0) & (ys <= y1)
        t = ((ys[m] - y0) / max(1e-6, y1 - y0))[:, None]
        t = t * t * (3 - 2 * t)
        img[m] = lerp(rgb(c0), rgb(c1), t)[:, None, :]
    return img


def ridge(rng, base, amp, rough=0.55, octaves=7, freq=2.0):
    """1D fractal noise heights (pixels from top) across the width."""
    x = np.linspace(0, 1, W)
    y = np.zeros(W)
    a = 1.0
    f = freq
    for _ in range(octaves):
        phase = rng.uniform(0, 2 * math.pi, 3)
        y += a * (np.sin(x * f * 2 * math.pi + phase[0]) * .6
                  + np.sin(x * f * 1.7 * 2 * math.pi + phase[1]) * .4)
        a *= rough
        f *= 2.1
    y = y / np.abs(y).max()
    return base * H - y * amp * H


def paint_layer(img, top, color, shade=0.0, snow=None):
    """Fills everything below the ridge line `top` with colour, darker towards the bottom."""
    ys = np.arange(H)[:, None]
    mask = ys >= top[None, :]
    depth = np.clip((ys - top[None, :]) / (H * .5), 0, 1)
    col = rgb(color)[None, None, :] * (1 - shade * depth[..., None])
    if snow is not None:
        # Snow covers everything above an altitude that wanders a little, like a real snowline.
        level, scolor = snow
        x = np.linspace(0, 1, W)
        wander = (np.sin(x * 23.0) * .5 + np.sin(x * 61.0 + 1.3) * .3 + np.sin(x * 137.0) * .2) * 10
        line = level + wander
        s = np.clip((line[None, :] - ys) / 6.0, 0, 1)
        s = np.maximum(s, np.clip(1 - (ys - top[None, :]) / 5.0, 0, 1) * .6)
        s *= (ys >= top[None, :])
        col = lerp(col, rgb(scolor)[None, None, :], s[..., None] * .92)
    img[mask] = col[mask] if col.shape[:2] == mask.shape else np.broadcast_to(col, img.shape)[mask]


def glow(img, cx, cy, r, color, strength=1.0):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.hypot(xx - cx, yy - cy)
    g = np.exp(-(d / r) ** 2)[..., None] * strength
    img[:] = img + (rgb(color)[None, None, :] - img) * np.clip(g, 0, 1)


def disc(img, cx, cy, r, color, soft=1.5):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.hypot(xx - cx, yy - cy)
    a = np.clip((r - d) / soft, 0, 1)[..., None]
    img[:] = img * (1 - a) + rgb(color)[None, None, :] * a


def stars(img, rng, n, ymax, bright=1.0):
    for _ in range(n):
        x, y = rng.integers(0, W), rng.integers(0, int(H * ymax))
        b = rng.uniform(.3, 1) * bright
        img[y, x] = lerp(img[y, x], np.array([255, 250, 235.]), b)
        if b > .85 and 1 <= x < W - 1 and 1 <= y < H - 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[y + dy, x + dx] = lerp(img[y + dy, x + dx], np.array([255, 250, 235.]), b * .4)


def fog(img, y, height, color, alpha):
    ys = np.arange(H)
    a = np.exp(-((ys - y * H) / (height * H)) ** 2)[:, None, None] * alpha
    img[:] = img * (1 - a) + rgb(color)[None, None, :] * a


def to_image(img):
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def overlay(base, draw_fn, blur=0):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer), layer)
    if blur:
        layer = layer.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(layer)


def pine(d, x, ground, h, color, w_ratio=.32):
    tiers = 4
    for i in range(tiers):
        t0 = ground - h * (i / tiers) * .85
        wid = h * w_ratio * (1 - i / tiers * .7)
        top = t0 - h * .42
        d.polygon([(x - wid, t0), (x + wid, t0), (x, top)], fill=color)
    d.rectangle([x - h * .03, ground - h * .05, x + h * .03, ground + 4], fill=color)


def leafy_tree(d, rng, x, ground, h, trunk, colours):
    d.line([(x, ground), (x, ground - h * .55)], fill=trunk, width=max(3, int(h * .06)))
    for _ in range(26):
        ang = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0, h * .28)
        cx = x + math.cos(ang) * rr * 1.2
        cy = ground - h * .62 + math.sin(ang) * rr * .8
        r = rng.uniform(h * .1, h * .18)
        c = colours[rng.integers(0, len(colours))]
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c)


def leaf(d, x, y, s, ang, color):
    pts = []
    for k in range(12):
        t = k / 11 * math.pi
        rx = math.cos(t) * s
        ry = math.sin(t) * s * .45
        pts.append((rx, ry))
    pts += [(rx, -ry) for rx, ry in reversed(pts)]
    ca, sa = math.cos(ang), math.sin(ang)
    d.polygon([(x + px * ca - py * sa, y + px * sa + py * ca) for px, py in pts], fill=color)


def bat(d, x, y, s, color):
    d.polygon([(x, y), (x - s * .5, y - s * .35), (x - s * .8, y - s * .1), (x - s * 1.1, y - s * .45),
               (x - s * 1.25, y + s * .05), (x - s * .6, y + s * .1), (x - s * .25, y + s * .3),
               (x, y + s * .15), (x + s * .25, y + s * .3), (x + s * .6, y + s * .1),
               (x + s * 1.25, y + s * .05), (x + s * 1.1, y - s * .45), (x + s * .8, y - s * .1),
               (x + s * .5, y - s * .35)], fill=color)
    d.ellipse([x - s * .14, y - s * .2, x + s * .14, y + s * .25], fill=color)


def pumpkin(d, x, ground, s, body, glow_col, face):
    for k, off in enumerate((-.42, .42, -.2, .2, 0)):
        d.ellipse([x + off * s - s * .45, ground - s * .9, x + off * s + s * .45, ground], fill=body)
    d.rectangle([x - s * .06, ground - s * 1.05, x + s * .06, ground - s * .82], fill="#3b4a1f")
    if face:
        d.polygon([(x - s * .3, ground - s * .55), (x - s * .14, ground - s * .55), (x - s * .22, ground - s * .7)], fill=glow_col)
        d.polygon([(x + s * .3, ground - s * .55), (x + s * .14, ground - s * .55), (x + s * .22, ground - s * .7)], fill=glow_col)
        d.polygon([(x - s * .32, ground - s * .38), (x + s * .32, ground - s * .38), (x + s * .2, ground - s * .2),
                   (x + s * .07, ground - s * .3), (x - s * .07, ground - s * .2), (x - s * .2, ground - s * .3)],
                  fill=glow_col)


def halloween(rng):
    img = gradient([(0, "#140c2e"), (.35, "#3a1d4f"), (.62, "#a2416a"), (.78, "#f08a3c"), (1, "#f8b25a")])
    stars(img, rng, 380, .45, .9)
    glow(img, W * .66, H * .27, 260, "#ffcf8a", .55)
    disc(img, W * .66, H * .27, 92, "#ffe4b0")
    # Moon craters, faint.
    for cx, cy, r in ((-25, -20, 18), (30, 15, 12), (5, 40, 9), (-40, 30, 7)):
        disc(img, W * .66 + cx, H * .27 + cy, r, "#f4d39c", 3)
    paint_layer(img, ridge(rng, .58, .09), "#5b2a5c", .2)
    fog(img, .6, .05, "#f0a070", .28)
    paint_layer(img, ridge(rng, .66, .06, freq=3), "#3a1940", .3)
    paint_layer(img, ridge(rng, .76, .035, freq=4), "#1c0d22", .4)
    im = to_image(img).convert("RGBA")

    def scene(d, _):
        for x in list(range(-20, W + 40, 46)):
            h = rng.uniform(90, 170)
            pine(d, x + rng.uniform(-14, 14), H * .80 + rng.uniform(-8, 10), h, "#120818")
        d.rectangle([0, H * .84, W, H], fill="#0d0612")
        for (bx, by, s) in ((W * .60, H * .2, 26), (W * .72, H * .18, 18), (W * .66, H * .33, 14),
                            (W * .31, H * .14, 20), (W * .82, H * .29, 12), (W * .43, H * .24, 10)):
            bat(d, bx, by, s, "#1a0b20")

    overlay(im, scene)

    def lights(d, _):
        for x, s in ((W * .14, 70), (W * .22, 52), (W * .86, 64), (W * .5, 44)):
            d.ellipse([x - s * 1.4, H * .93 - s * 1.2, x + s * 1.4, H * .93 + s * .3], fill=(255, 140, 40, 90))

    overlay(im, lights, blur=28)

    def pumpkins(d, _):
        for x, s in ((W * .14, 70), (W * .22, 52), (W * .86, 64), (W * .5, 44)):
            pumpkin(d, x, H * .965, s, "#e2661b", "#ffd25a", True)

    overlay(im, pumpkins)
    return im


def autumn(rng):
    img = gradient([(0, "#4f6f9a"), (.3, "#9fa9c0"), (.55, "#f2c48d"), (.72, "#f19a5a"), (1, "#c86a3a")])
    glow(img, W * .30, H * .52, 340, "#ffe2b0", .7)
    disc(img, W * .30, H * .52, 60, "#fff1d0", 2)
    paint_layer(img, ridge(rng, .55, .12), "#b98a7f", .1)
    fog(img, .57, .05, "#f6d2ac", .35)
    paint_layer(img, ridge(rng, .62, .08, freq=2.6), "#a8584a", .15)
    fog(img, .66, .04, "#f2c39a", .25)
    paint_layer(img, ridge(rng, .70, .05, freq=3.4), "#7d3a2d", .25)
    im = to_image(img).convert("RGBA")
    palette = ["#d9481c", "#e8792b", "#f2a33a", "#c23b22", "#e0b040", "#a8321c"]

    def trees(d, _):
        for x in range(-30, W + 60, 70):
            leafy_tree(d, rng, x + rng.uniform(-20, 20), H * .83 + rng.uniform(-10, 10),
                       rng.uniform(150, 240), "#3a1d14", palette)
        d.rectangle([0, H * .86, W, H], fill="#4a2214")

    overlay(im, trees)

    def leaves(d, _):
        for _ in range(70):
            leaf(d, rng.uniform(0, W), rng.uniform(H * .05, H * .95), rng.uniform(7, 15),
                 rng.uniform(0, math.pi), palette[rng.integers(0, len(palette))])

    overlay(im, leaves)
    return im


def christmas(rng):
    img = gradient([(0, "#06122e"), (.4, "#13285a"), (.7, "#2c4a85"), (1, "#5a76ad")])
    stars(img, rng, 520, .6, 1.0)
    glow(img, W * .5, H * .12, 70, "#fff6d0", .9)
    paint_layer(img, ridge(rng, .52, .12), "#5f7bb0", .1, snow=(H * .47, "#e8f0ff"))
    fog(img, .58, .04, "#a7bce6", .25)
    paint_layer(img, ridge(rng, .63, .06, freq=3), "#9fb5df", .05, snow=(H * .64, "#eef4ff"))
    im = to_image(img).convert("RGBA")
    # A four-point star.

    def star(d, _):
        cx, cy, r = W * .5, H * .12, 26
        d.polygon([(cx, cy - r), (cx + r * .22, cy - r * .22), (cx + r, cy), (cx + r * .22, cy + r * .22),
                   (cx, cy + r), (cx - r * .22, cy + r * .22), (cx - r, cy), (cx - r * .22, cy - r * .22)],
                  fill="#fff8dc")

    overlay(im, star)
    bulbs = ["#ff4d4d", "#ffd84d", "#4dd2ff", "#7dff7a", "#ff9de2"]
    spots = []

    def forest(d, _):
        for x in range(-30, W + 60, 54):
            h = rng.uniform(130, 220)
            gx = x + rng.uniform(-14, 14)
            gy = H * .82 + rng.uniform(-10, 8)
            pine(d, gx, gy, h, "#123b2c")
            # Snow on the tiers.
            for i in range(4):
                t0 = gy - h * (i / 4) * .85
                wid = h * .32 * (1 - i / 4 * .7)
                d.polygon([(gx - wid * .7, t0 - h * .14), (gx, t0 - h * .42), (gx + wid * .5, t0 - h * .2)],
                          fill="#e8f2ff")
            for _ in range(7):
                spots.append((gx + rng.uniform(-h * .22, h * .22), gy - rng.uniform(h * .1, h * .8),
                              bulbs[rng.integers(0, len(bulbs))]))
        d.rectangle([0, H * .85, W, H], fill="#e4eefc")

    overlay(im, forest)

    def halo(d, _):
        for x, y, c in spots:
            d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=c + "88")

    overlay(im, halo, blur=6)

    def bulbs_layer(d, _):
        for x, y, c in spots:
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=c)
        for _ in range(240):
            x, y, r = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(1.5, 3.8)
            d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, int(rng.uniform(140, 230))))

    overlay(im, bulbs_layer)
    return im


def winter(rng):
    img = gradient([(0, "#9fbbd9"), (.45, "#d4e2f0"), (.62, "#eef3f8"), (1, "#c9d9ea")])
    glow(img, W * .72, H * .3, 300, "#ffffff", .6)
    disc(img, W * .72, H * .3, 46, "#fbfdff", 2)
    paint_layer(img, ridge(rng, .5, .17, rough=.6), "#8aa5c4", .05, snow=(H * .44, "#f4f8fc"))
    fog(img, .56, .05, "#f3f7fb", .5)
    paint_layer(img, ridge(rng, .6, .09, freq=2.8), "#7591b3", .08, snow=(H * .56, "#eef4fa"))
    fog(img, .66, .04, "#eef4fa", .4)
    # Frozen lake.
    lake = H * .74
    img[int(lake):] = lerp(img[int(lake):], rgb("#b7cde3")[None, None, :], .8)
    for k in range(0, int(H - lake), 3):
        img[int(lake) + k] = lerp(img[int(lake) + k], rgb("#e6eff8"), .12 * (1 + math.sin(k * .4)))
    im = to_image(img).convert("RGBA")

    def shore(d, _):
        for x in range(-30, W + 60, 40):
            h = rng.uniform(60, 120)
            pine(d, x + rng.uniform(-10, 10), lake + rng.uniform(-4, 4), h, "#3c5874")
        d.rectangle([0, lake, W, lake + 6], fill="#f2f7fb")

    overlay(im, shore)

    def flakes(d, _):
        for _ in range(160):
            x, y, r = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(1.2, 3)
            d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, int(rng.uniform(150, 230))))

    overlay(im, flakes)
    return im


def spring(rng):
    img = gradient([(0, "#8fc4ef"), (.45, "#cfe6f7"), (.62, "#f6f0e8"), (1, "#e8f1d9")])
    glow(img, W * .24, H * .2, 260, "#fffbe8", .6)
    paint_layer(img, ridge(rng, .52, .1), "#9cbcd0", .05, snow=(H * .47, "#f5fafc"))
    fog(img, .57, .05, "#f3f7f3", .4)
    paint_layer(img, ridge(rng, .62, .07, freq=2.2), "#8cc271", .15)
    paint_layer(img, ridge(rng, .72, .05, freq=2.8), "#5fa553", .2)
    im = to_image(img).convert("RGBA")
    pinks = ["#f7b6cf", "#f39ac0", "#fbd0e0", "#f5a6c6", "#ffe0ec"]

    def trees(d, _):
        for x in range(-40, W + 80, 110):
            leafy_tree(d, rng, x + rng.uniform(-30, 30), H * .84 + rng.uniform(-8, 8),
                       rng.uniform(150, 230), "#5a3d33", pinks)
        d.rectangle([0, H * .87, W, H], fill="#4f9448")
        for _ in range(90):
            x, y = rng.uniform(0, W), rng.uniform(H * .88, H)
            c = ["#ffffff", "#ffe066", "#f7b6cf", "#c9a7ff"][rng.integers(0, 4)]
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=c)

    overlay(im, trees)

    def petals(d, _):
        for _ in range(60):
            leaf(d, rng.uniform(0, W), rng.uniform(H * .05, H * .9), rng.uniform(5, 9),
                 rng.uniform(0, math.pi), pinks[rng.integers(0, len(pinks))])

    overlay(im, petals)
    return im


def summer(rng):
    img = gradient([(0, "#2f8fd8"), (.35, "#7cc4ee"), (.55, "#d6eef8"), (.58, "#3aa0c8"), (.8, "#1f7fae"), (1, "#f2dcae")])
    sea = H * .58
    glow(img, W * .5, H * .3, 320, "#fff7d6", .65)
    disc(img, W * .5, H * .3, 64, "#fffbea", 2)
    # Islands on the horizon.
    isl = ridge(rng, .58, .05, freq=1.6)
    isl = np.where(np.abs(np.linspace(-1, 1, W)) > .45, isl, sea)
    ys = np.arange(H)[:, None]
    mask = (ys >= isl[None, :]) & (ys < sea)
    img[mask] = rgb("#5f93a8")
    # Sea: glitter under the sun.
    for y in range(int(sea), int(H * .86)):
        t = (y - sea) / (H * .28)
        img[y] = lerp(rgb("#4fb3d9"), rgb("#1c79a8"), t)
        if rng.uniform() < .9:
            spread = 60 + t * 260
            for _ in range(int(3 + t * 10)):
                x = int(W * .5 + rng.normal(0, spread))
                if 0 <= x < W - 6:
                    img[y, x:x + int(4 + t * 10)] = lerp(img[y, x:x + int(4 + t * 10)], np.array([255, 251, 230.]), .8)
    # Beach with a wet edge.
    beach = H * .86
    for y in range(int(beach), H):
        t = (y - beach) / (H - beach)
        img[y] = lerp(rgb("#c9b48a"), rgb("#f1dfb3"), min(1, t * 1.6))
    im = to_image(img).convert("RGBA")

    def foam(d, _):
        pts = [(x, beach + 4 * math.sin(x / 40) - 2) for x in range(0, W + 10, 10)]
        d.line(pts, fill=(255, 255, 255, 220), width=5)

    overlay(im, foam)

    def palms(d, _):
        for bx, h, lean in ((W * .08, 360, .25), (W * .93, 320, -.22)):
            top = (bx + lean * h, H - h)
            steps = 18
            for k in range(steps):
                t0, t1 = k / steps, (k + 1) / steps
                x0 = bx + lean * h * t0 ** 1.5
                x1 = bx + lean * h * t1 ** 1.5
                y0, y1 = H - h * t0, H - h * t1
                wdt = 16 - 8 * t0
                d.polygon([(x0 - wdt, y0), (x0 + wdt, y0), (x1 + wdt * .9, y1), (x1 - wdt * .9, y1)], fill="#4a3624")
            for ang in (-170, -140, -110, -70, -40, -10, 20, 200):
                a = math.radians(ang)
                pts = []
                for k in range(16):
                    t = k / 15
                    pts.append((top[0] + math.cos(a) * 190 * t, top[1] + math.sin(a) * 50 * t + 110 * t * t))
                for k in range(15):
                    t = k / 15
                    d.line([pts[k], pts[k + 1]], fill="#1f5a34", width=max(2, int(26 * (1 - t) * math.sin(math.pi * (t * .8 + .2)))))
                    # Leaflets hanging off the frond.
                    if k % 2 == 0 and k > 1:
                        px, py = pts[k]
                        ln = 34 * (1 - t) + 8
                        d.line([(px, py), (px + math.cos(a) * 6, py + ln)], fill="#1f5a34", width=4)
            d.ellipse([top[0] - 14, top[1] - 10, top[0] + 14, top[1] + 12], fill="#3b2a1c")

    overlay(im, palms)
    return im


SCENES = {
    "season_autumn": autumn,
    "season_halloween": halloween,
    "season_christmas": christmas,
    "season_winter": winter,
    "season_spring": spring,
    "season_summer": summer,
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    only = sys.argv[2:]
    os.makedirs(out, exist_ok=True)
    for i, (name, fn) in enumerate(SCENES.items()):
        if only and name not in only:
            continue
        rng = np.random.default_rng(20261101 + i)
        im = fn(rng).convert("RGB")
        path = os.path.join(out, name + ".jpg")
        im.save(path, quality=86, optimize=True, progressive=False)
        print(path, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    main()
