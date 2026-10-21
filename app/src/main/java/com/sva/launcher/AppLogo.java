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
        Bitmap logo = trim(mark);
        return new Art(logo, glow(logo), glowColor(backdrop, px), true);
    }

    /** Pure: drops the backdrop colour (sampled around the edge) and keeps the mark, with soft edges. */
    static Cut cut(int[] argb, int w, int h) {
        int backdrop = edgeColor(argb, w, h);
        int[] out = new int[argb.length];
        double opaque = 0, kept = 0;
        int br = (backdrop >> 16) & 0xFF, bg = (backdrop >> 8) & 0xFF, bb = backdrop & 0xFF;
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int a = p >>> 24;
            if (a < 16) continue;
            opaque++;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            double d = Math.sqrt((r - br) * (r - br) + (g - bg) * (g - bg) + (b - bb) * (b - bb));
            // Within 40 of the backdrop is backdrop; beyond 90 is mark; in between fades.
            double t = Math.max(0, Math.min(1, (d - 40) / 50d));
            int alpha = (int) Math.round(a * t);
            if (alpha == 0) continue;
            if (alpha > 128) kept++;
            out[i] = (alpha << 24) | (p & 0x00FFFFFF);
        }
        return new Cut(out, backdrop, opaque == 0 ? 0 : kept / opaque);
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
        int full = Math.round(size * zoom), offset = (size - full) / 2;
        copy.setBounds(offset, offset, offset + full, offset + full);
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
        int bw = box[2] - box[0] + 1, bh = box[3] - box[1] + 1, side = Math.max(bw, bh);
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(Bitmap.createBitmap(source, box[0], box[1], bw, bh), (side - bw) / 2f, (side - bh) / 2f, null);
        return out;
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

    /** A soft, blurred copy of the logo for the coloured light behind it (shrunk and blown up). */
    private static Bitmap glow(Bitmap logo) {
        Bitmap tiny = Bitmap.createScaledBitmap(logo, 10, 10, true);
        Bitmap mid = Bitmap.createScaledBitmap(tiny, 28, 28, true);
        return Bitmap.createScaledBitmap(mid, 96, 96, true);
    }
}
