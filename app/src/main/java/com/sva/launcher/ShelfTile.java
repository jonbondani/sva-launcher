package com.sva.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Home shelf tiles in the Liquid Glass style (after frxnnk/webos-liquid-glass-launcher, MIT):
 * a rounded square of clear glass with a soft glow of the app's own colour at the bottom, the
 * logo in the middle, and on focus a lift with a white ring. Sizes follow that launcher at TV
 * scale (glass 100 dp, radius 23 dp, slot 114 dp).
 */
final class ShelfTile {
    static final int GLASS_DP = 100, RADIUS_DP = 23, SLOT_DP = 114, LOGO_PERCENT = 62;
    static final String GLASS_TAG = "shelf-glass";
    private static final int DEFAULT_COLOR = 0xFF9FB4D6;

    private ShelfTile() { }

    /** The slot's glass square: add the logo (or any content) to it. */
    static FrameLayout glass(Context context, int color, boolean moving) {
        FrameLayout glass = new FrameLayout(context);
        glass.setTag(GLASS_TAG);
        glass.setDuplicateParentStateEnabled(true);
        Glass drawable = new Glass(context, color, moving);
        glass.setBackground(drawable);
        glass.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), LiquidTheme.dp(context, RADIUS_DP));
            }
        });
        glass.setElevation(LiquidTheme.dp(context, 4));
        return glass;
    }

    /** Puts {@code glass} centred in {@code slot} (the focusable view the rest of Home works with). */
    static void mount(View slotView, FrameLayout glass) {
        ViewGroup slot = (ViewGroup) slotView;
        slot.setClipChildren(false);
        slot.setClipToPadding(false);
        int size = LiquidTheme.dp(slot.getContext(), GLASS_DP);
        if (slot instanceof FrameLayout) slot.addView(glass, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        else slot.addView(glass, new android.widget.LinearLayout.LayoutParams(size, size));
        slot.setOnFocusChangeListener((v, focused) -> animate(glass, focused));
    }

    /** Logo at 62 % of the glass, centred. */
    static FrameLayout.LayoutParams logoParams(Context context) {
        int logo = LiquidTheme.dp(context, GLASS_DP * LOGO_PERCENT / 100f);
        return new FrameLayout.LayoutParams(logo, logo, Gravity.CENTER);
    }

    /** tvOS / Liquid Glass focus: up a little and 10 % bigger, with a springy settle. */
    static void animate(View glass, boolean focused) {
        glass.animate().cancel();
        float scale = focused ? 1.1f : 1f;
        glass.animate().scaleX(scale).scaleY(scale)
                .translationY(focused ? -LiquidTheme.dp(glass.getContext(), 6) : 0)
                .translationZ(focused ? LiquidTheme.dp(glass.getContext(), 10) : 0)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 260 : 0).start();
    }

    /** The app's colour: average of the logo's saturated pixels, or a soft blue-grey. */
    static int dominantColor(Drawable logo) {
        if (logo == null) return DEFAULT_COLOR;
        try {
            Bitmap small = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(small);
            Drawable copy = logo.getConstantState() != null ? logo.getConstantState().newDrawable().mutate() : logo;
            copy.setBounds(0, 0, 24, 24);
            copy.draw(canvas);
            int color = dominantColor(pixels(small));
            small.recycle();
            return color;
        } catch (RuntimeException e) {
            return DEFAULT_COLOR;
        }
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] out = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(out, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return out;
    }

    /** Pure: weighted average of opaque, saturated pixels; grey-only logos give the default. */
    static int dominantColor(int[] argb) {
        double r = 0, g = 0, b = 0, weight = 0;
        for (int p : argb) {
            if ((p >>> 24) < 128) continue;
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
            double saturation = max == 0 ? 0 : (max - min) / (double) max;
            if (saturation < .25 || max < 40) continue;
            double w = saturation * saturation;
            r += pr * w; g += pg * w; b += pb * w; weight += w;
        }
        if (weight < 1e-6) return DEFAULT_COLOR;
        return 0xFF000000 | ((int) Math.round(r / weight) << 16) | ((int) Math.round(g / weight) << 8)
                | (int) Math.round(b / weight);
    }

    /** Clear glass, app-colour glow, top sheen; white ring when focused (accent ring while moving). */
    static final class Glass extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int color, ringColor;
        private final float radius, ring, ringGap, onePx;
        private boolean focused;
        private final boolean moving;

        Glass(Context context, int color, boolean moving) {
            this.color = color;
            this.moving = moving;
            this.ringColor = moving ? LiquidTheme.accent(context) : Color.WHITE;
            this.radius = LiquidTheme.dp(context, RADIUS_DP);
            this.ring = LiquidTheme.dp(context, 2.5f);
            this.ringGap = LiquidTheme.dp(context, 5);
            this.onePx = Math.max(1f, context.getResources().getDisplayMetrics().density * .75f);
        }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int s : state) if (s == android.R.attr.state_focused || s == android.R.attr.state_pressed) now = true;
            if (now == focused) return false;
            focused = now;
            invalidateSelf();
            return true;
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            rect.set(b);
            float w = rect.width(), h = rect.height();
            paint.setStyle(Paint.Style.FILL);
            paint.setMaskFilter(null);
            // Body: clear glass, lighter at the top-left and bottom-right edges.
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{0x3DFFFFFF, 0x14FFFFFF, 0x0AFFFFFF, 0x21FFFFFF}, new float[]{0f, .44f, .62f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, paint);
            // The app's colour glowing up from the bottom (stronger when focused).
            int tintAlpha = Math.round(255 * (focused || moving ? .7f : .34f));
            paint.setShader(new RadialGradient(rect.centerX(), rect.bottom, Math.max(1f, h * .85f),
                    new int[]{(color & 0x00FFFFFF) | (tintAlpha << 24), color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, paint);
            // Sheen on the upper part.
            canvas.save();
            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + h * .6f);
            paint.setShader(new RadialGradient(rect.left + w * .26f, rect.top, Math.max(1f, w * .7f),
                    new int[]{0x57FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, paint);
            canvas.restore();
            paint.setShader(null);
            // Rims: a bright top edge and a faint outline.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(onePx);
            paint.setColor(0x1AFFFFFF);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.top + h * .5f,
                    new int[]{0x99FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
            if (focused || moving) {
                // Ring just outside the glass, with a soft glow.
                RectF outer = new RectF(rect);
                outer.inset(-ringGap, -ringGap);
                float outerRadius = radius + ringGap;
                paint.setStrokeWidth(ring * 3);
                paint.setColor((ringColor & 0x00FFFFFF) | 0x55000000);
                paint.setMaskFilter(new BlurMaskFilter(ring * 3, BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(outer, outerRadius, outerRadius, paint);
                paint.setMaskFilter(null);
                paint.setStrokeWidth(ring);
                paint.setColor(ringColor);
                canvas.drawRoundRect(outer, outerRadius, outerRadius, paint);
            }
        }

        @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
