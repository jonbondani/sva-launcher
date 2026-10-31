package com.sva.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * Turns an installed app's own icon into a bare logo for the glass tiles, the way iOS tinted and
 * clear icons do: the icon's backdrop is dropped and only the mark stays, with the backdrop's colour
 * kept for the glow behind it. Nothing is bundled: every logo comes from the app on the device.
 *
 * Adaptive icons (Android 8+) already keep the mark on its own layer. Older, flat icons are cut out
 * by colour: whatever differs from the colour around the icon's edge is the mark. When that does not
 * give a clean result (photos, busy artwork) the whole icon is used instead.
 */
final class AppLogo {
    static final int SIZE = 160;
    /** Share of the icon's opaque pixels the mark must cover to be trusted as a cut-out. */
    static final double MIN_MARK = .03, MAX_MARK = .72;

    private AppLogo() { }

    static final class Art {
        final Bitmap logo, glow;
        /** The app's colour: its icon backdrop when that is colourful, else the mark's own colour. */
        final int color;
        /** True when the logo is the bare mark; false when it is the whole icon (rounded by the caller). */
        final boolean bare;

        Art(Bitmap logo, Bitmap glow, int color, boolean bare) {
            this.logo = logo;
            this.glow = glow;
            this.color = color;
            this.bare = bare;
        }
    }

    /** The result of cutting a mark out of a flat icon (pure, for tests). */
    static final class Cut {
        final int[] pixels;
        final int backdrop;
        final double coverage;

        Cut(int[] pixels, int backdrop, double coverage) {
            this.pixels = pixels;
            this.backdrop = backdrop;
            this.coverage = coverage;
        }

        boolean usable() { return coverage >= MIN_MARK && coverage <= MAX_MARK; }
    }

    /** Off the main thread: builds the tile art for {@code icon}, or null without an icon. */
    static Art of(Drawable icon) {
        if (icon == null) return null;
        try {
            if (icon instanceof AdaptiveIconDrawable) {
                Art adaptive = fromAdaptive((AdaptiveIconDrawable) icon);
                if (adaptive != null) return adaptive;
            }
            Bitmap whole = render(icon, SIZE, 1f);
            int[] px = pixels(whole);
            Cut cut = cut(px, SIZE, SIZE);
            if (cut.usable()) {
                Bitmap logo = trim(Bitmap.createBitmap(cut.pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888));
                return new Art(logo, glow(logo), glowColor(cut.backdrop, cut.pixels), true);
            }
            return new Art(whole, glow(whole), ShelfTile.dominantColor(px), false);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** The foreground layer is the mark; its backdrop layer gives the colour. */
    private static Art fromAdaptive(AdaptiveIconDrawable icon) {
        Drawable foreground = icon.getForeground(), background = icon.getBackground();
        if (foreground == null) return null;
        // Layers are 108 units with the visible part in the middle 72: draw 1.5x and keep the middle.
        Bitmap mark = render(foreground, SIZE, 1.5f);
        int[] px = pixels(mark);
        double covered = 0;
        for (int p : px) if ((p >>> 24) > 200) covered++;
        // A foreground that fills the icon is artwork, not a mark on a backdrop.
        if (covered / px.length > .8) return null;
        int backdrop = background == null ? 0 : ShelfTile.dominantColor(pixels(render(background, 24, 1.5f)));
        // Material icons carry a faint long shadow on the mark's layer: off the backdrop it shows
        // as a grey square, so faint pixels go and the edges of the mark stay soft.
        int[] clean = dropFaint(px);
        Bitmap logo = trim(Bitmap.createBitmap(clean, SIZE, SIZE, Bitmap.Config.ARGB_8888));
        return new Art(logo, glow(logo), glowColor(backdrop, clean), true);
    }

    static final int FAINT = 80;

    /** Pure: alpha below {@link #FAINT} becomes clear, the rest is stretched back to full range. */
    static int[] dropFaint(int[] argb) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int a = argb[i] >>> 24;
            if (a <= FAINT) continue;
            int stretched = Math.min(255, Math.round((a - FAINT) * 255f / (255 - FAINT)));
            out[i] = stretched << 24 | (argb[i] & 0x00FFFFFF);
        }
        return out;
    }

    /** Pure: drops the backdrop colour (sampled around the edge) and keeps the mark, with soft edges. */
    static Cut cut(int[] argb, int w, int h) {
        int backdrop = edgeColor(argb, w, h);
        // Every colour along the icon's outer edge counts as backdrop, not just the commonest:
        // gradients (Movistar+ grey to black, Prime Video's blues) then come out whole.
        int[] palette = edgePalette(argb, w, h);
        int[] out = new int[argb.length];
        double opaque = 0, kept = 0;
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int a = p >>> 24;
            if (a < 16) continue;
            opaque++;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            double best = Double.MAX_VALUE;
            int bg = 0;
            for (int c : palette) {
                int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
                double d = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);
                if (d < best) { best = d; bg = c; }
            }
            double d = palette.length == 0 ? 255 : Math.sqrt(best);
            // Within 30 of a backdrop colour is backdrop; beyond 70 is mark; in between fades.
            double t = Math.max(0, Math.min(1, (d - 30) / 40d));
            int alpha = (int) Math.round(a * t);
            if (alpha == 0) continue;
            if (alpha > 128) kept++;
            if (t < 1) {
                // Edge pixels are part mark, part backdrop: take the backdrop out of their colour
                // so a white logo stays white instead of keeping a blue or grey fringe.
                r = unmix(r, (bg >> 16) & 0xFF, t);
                g = unmix(g, (bg >> 8) & 0xFF, t);
                b = unmix(b, bg & 0xFF, t);
            }
            out[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
        }
        return new Cut(out, backdrop, opaque == 0 ? 0 : kept / opaque);
    }

    private static int unmix(int value, int backdrop, double t) {
        return (int) Math.max(0, Math.min(255, Math.round((value - (1 - t) * backdrop) / t)));
    }

    /**
     * Pure: the colours (to 32 levels a channel) of the outermost opaque pixels of every row and
     * column, two deep. That ring is backdrop on app icons, whatever their shape or gradient.
     */
    static int[] edgePalette(int[] argb, int w, int h) {
        java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
        for (int y = 0; y < h; y++) {
            int first = -1, last = -1;
            for (int x = 0; x < w; x++) if ((argb[y * w + x] >>> 24) >= 200) { if (first < 0) first = x; last = x; }
            if (first < 0) continue;
            for (int k = 0; k < 2; k++) {
                addQuantized(seen, argb[y * w + Math.min(first + k, w - 1)]);
                addQuantized(seen, argb[y * w + Math.max(last - k, 0)]);
            }
        }
        for (int x = 0; x < w; x++) {
            int first = -1, last = -1;
            for (int y = 0; y < h; y++) if ((argb[y * w + x] >>> 24) >= 200) { if (first < 0) first = y; last = y; }
            if (first < 0) continue;
            for (int k = 0; k < 2; k++) {
                addQuantized(seen, argb[Math.min(first + k, h - 1) * w + x]);
                addQuantized(seen, argb[Math.max(last - k, 0) * w + x]);
            }
        }
        int[] out = new int[seen.size()];
        int i = 0;
        for (int c : seen) out[i++] = c;
        return out;
    }

    private static void addQuantized(java.util.Set<Integer> set, int p) {
        if ((p >>> 24) < 200) return;
        int r = ((p >> 16) & 0xF8) + 4, g = ((p >> 8) & 0xF8) + 4, b = (p & 0xF8) + 4;
        set.add(0xFF000000 | r << 16 | g << 8 | b);
    }

    /**
     * Pure: the most common colour (to 16 levels a channel) among opaque pixels in the outer band
     * of the icon. Round and squircle icons have transparent corners, so the band is a fifth deep.
     */
    static int edgeColor(int[] argb, int w, int h) {
        java.util.Map<Integer, int[]> buckets = new java.util.HashMap<>();
        int band = Math.max(1, Math.min(w, h) / 5);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x >= band && x < w - band && y >= band && y < h - band) continue;
                int p = argb[y * w + x];
                if ((p >>> 24) < 200) continue;
                int key = ((p >> 20) & 0xF) << 8 | ((p >> 12) & 0xF) << 4 | ((p >> 4) & 0xF);
                int[] sum = buckets.get(key);
                if (sum == null) buckets.put(key, sum = new int[4]);
                sum[0]++;
                sum[1] += (p >> 16) & 0xFF;
                sum[2] += (p >> 8) & 0xFF;
                sum[3] += p & 0xFF;
            }
        }
        int[] best = null;
        for (int[] sum : buckets.values()) if (best == null || sum[0] > best[0]) best = sum;
        if (best == null) return 0xFF000000;
        return 0xFF000000 | (best[1] / best[0]) << 16 | (best[2] / best[0]) << 8 | (best[3] / best[0]);
    }

    /** Colourful backdrop (Netflix red, Movistar blue) wins; a white or black one gives way to the mark. */
    static int glowColor(int backdrop, int[] mark) {
        int r = (backdrop >> 16) & 0xFF, g = (backdrop >> 8) & 0xFF, b = backdrop & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        boolean colourful = (backdrop >>> 24) > 0 && max > 40 && (max - min) / (double) max > .3;
        return colourful ? backdrop | 0xFF000000 : ShelfTile.dominantColor(mark);
    }

    private static Bitmap render(Drawable drawable, int size, float zoom) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable copy = drawable.getConstantState() != null ? drawable.getConstantState().newDrawable().mutate() : drawable;
        // Keep the drawing's own shape: some Fire TV apps (Prime Video) use their 16:9 banner
        // as the icon, which squeezed into a square came out distorted.
        int iw = copy.getIntrinsicWidth(), ih = copy.getIntrinsicHeight();
        float full = size * zoom;
        float fw = full, fh = full;
        if (iw > 0 && ih > 0 && iw != ih) {
            if (iw > ih) fh = full * ih / iw; else fw = full * iw / ih;
        }
        int left = Math.round((size - fw) / 2f), top = Math.round((size - fh) / 2f);
        copy.setBounds(left, top, left + Math.round(fw), top + Math.round(fh));
        copy.draw(canvas);
        return bitmap;
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] out = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(out, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return out;
    }

    /** Crops to the mark and centres it on a square, so every logo fills its box the same way. */
    private static Bitmap trim(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int[] px = pixels(source);
        int[] box = bounds(px, w, h);
        if (box == null) return source;
        int bw = box[2] - box[0] + 1, bh = box[3] - box[1] + 1;
        // Keep the mark's own proportions: wide wordmarks ("prime video") can use the tile's width.
        return Bitmap.createBitmap(source, box[0], box[1], bw, bh);
    }

    /** Pure: {left, top, right, bottom} of pixels with some opacity, or null when empty. */
    static int[] bounds(int[] argb, int w, int h) {
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((argb[y * w + x] >>> 24) < 24) continue;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        return right < 0 ? null : new int[]{left, top, right, bottom};
    }

    static final int GLOW_SIZE = 64, GLOW_LOGO = 34, GLOW_RADIUS = 5;

    /**
     * A soft, blurred copy of the logo for the coloured light behind it: the logo drawn small in the
     * middle of a larger square and blurred, so the light spreads past the logo's edges.
     */
    private static Bitmap glow(Bitmap logo) {
        Bitmap canvasBitmap = Bitmap.createBitmap(GLOW_SIZE, GLOW_SIZE, Bitmap.Config.ARGB_8888);
        int offset = (GLOW_SIZE - GLOW_LOGO) / 2;
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        new Canvas(canvasBitmap).drawBitmap(logo, null,
                new android.graphics.Rect(offset, offset, offset + GLOW_LOGO, offset + GLOW_LOGO), paint);
        int[] px = blur(pixels(canvasBitmap), GLOW_SIZE, GLOW_SIZE, GLOW_RADIUS);
        return Bitmap.createBitmap(px, GLOW_SIZE, GLOW_SIZE, Bitmap.Config.ARGB_8888);
    }

    /**
     * Pure: three box-blur passes (close to a Gaussian) on premultiplied colour, so transparent
     * pixels do not darken the edges. Input and output are plain ARGB.
     */
    static int[] blur(int[] argb, int w, int h, int radius) {
        int n = argb.length;
        double[][] ch = new double[4][n];
        for (int i = 0; i < n; i++) {
            int p = argb[i];
            double a = (p >>> 24) / 255d;
            ch[0][i] = a;
            ch[1][i] = ((p >> 16) & 0xFF) * a;
            ch[2][i] = ((p >> 8) & 0xFF) * a;
            ch[3][i] = (p & 0xFF) * a;
        }
        double[] tmp = new double[n];
        for (double[] c : ch) {
            for (int pass = 0; pass < 3; pass++) {
                boxPass(c, tmp, w, h, radius, true);
                boxPass(tmp, c, w, h, radius, false);
            }
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double a = ch[0][i];
            if (a <= 1e-4) continue;
            int r = clamp(ch[1][i] / a), g = clamp(ch[2][i] / a), b = clamp(ch[3][i] / a);
            out[i] = clamp(a * 255) << 24 | r << 16 | g << 8 | b;
        }
        return out;
    }

    private static void boxPass(double[] in, double[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, length = horizontal ? w : h;
        double scale = 1d / (2 * r + 1);
        for (int line = 0; line < lines; line++) {
            double sum = 0;
            for (int k = -r; k <= r; k++) sum += at(in, line, k, length, w, horizontal);
            for (int k = 0; k < length; k++) {
                int index = horizontal ? line * w + k : k * w + line;
                out[index] = sum * scale;
                sum += at(in, line, k + r + 1, length, w, horizontal) - at(in, line, k - r, length, w, horizontal);
            }
        }
    }

    /** Outside the image counts as clear. */
    private static double at(double[] in, int line, int k, int length, int w, boolean horizontal) {
        if (k < 0 || k >= length) return 0;
        return in[horizontal ? line * w + k : k * w + line];
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
}
