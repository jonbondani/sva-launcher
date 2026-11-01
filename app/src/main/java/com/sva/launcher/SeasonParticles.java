package com.sva.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Random;

/**
 * A few leaves, snowflakes or petals drifting over the Home wallpaper. Deliberately light for a
 * Fire TV Stick: a couple of dozen shapes, positions computed from the clock (so a slow frame never
 * speeds things up), about 30 frames a second, and nothing at all while stopped.
 */
final class SeasonParticles extends View {
    private static final int COUNT = 22;
    private static final long FRAME_MS = 33;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path();
    private final float[] x = new float[COUNT], y = new float[COUNT], size = new float[COUNT],
            speed = new float[COUNT], sway = new float[COUNT], phase = new float[COUNT], spin = new float[COUNT];
    private final int[] colour = new int[COUNT];
    private final Random random = new Random(7);
    private int kind = Season.PARTICLES_NONE;
    private boolean running;
    private long last;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    SeasonParticles(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // A leaf shape, unit length along x.
        leaf.moveTo(-1f, 0f);
        leaf.quadTo(0f, -.55f, 1f, 0f);
        leaf.quadTo(0f, .55f, -1f, 0f);
        leaf.close();
    }

    void setSeason(String season) {
        kind = Season.particles(season);
        int[] colours = Season.particleColours(season);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) {
            x[i] = random.nextFloat();
            y[i] = random.nextFloat();
            phase[i] = random.nextFloat() * 6.283f;
            colour[i] = colours[random.nextInt(colours.length)];
            switch (kind) {
                case Season.PARTICLES_SNOW:
                    size[i] = (1.6f + random.nextFloat() * 2.6f) * density;
                    speed[i] = .035f + random.nextFloat() * .04f;
                    sway[i] = 8 * density;
                    break;
                case Season.PARTICLES_SPARKLES:
                    size[i] = (1.2f + random.nextFloat() * 2f) * density;
                    speed[i] = -(.012f + random.nextFloat() * .015f);
                    sway[i] = 14 * density;
                    break;
                default:
                    size[i] = (6 + random.nextFloat() * 6) * density;
                    speed[i] = .03f + random.nextFloat() * .035f;
                    sway[i] = 26 * density;
            }
            spin[i] = (random.nextFloat() - .5f) * 2.4f;
        }
        setVisibility(kind == Season.PARTICLES_NONE ? GONE : VISIBLE);
        invalidate();
    }

    /** Starts or stops the drift. Stopped, the last frame stays still on screen. */
    void setRunning(boolean run) {
        if (kind == Season.PARTICLES_NONE) run = false;
        if (run == running) return;
        running = run;
        removeCallbacks(tick);
        if (run) {
            last = 0;
            post(tick);
        }
    }

    @Override protected void onDetachedFromWindow() {
        setRunning(false);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (kind == Season.PARTICLES_NONE) return;
        long now = android.os.SystemClock.uptimeMillis();
        float dt = last == 0 || !running ? 0 : Math.min(.1f, (now - last) / 1000f);
        last = now;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float t = now / 1000f;
        for (int i = 0; i < COUNT; i++) {
            y[i] += speed[i] * dt;
            if (y[i] > 1.05f) { y[i] = -.05f; x[i] = random.nextFloat(); }
            if (y[i] < -.05f) { y[i] = 1.05f; x[i] = random.nextFloat(); }
            float px = x[i] * w + (float) Math.sin(t * .8f + phase[i]) * sway[i];
            float py = y[i] * h;
            paint.setColor(colour[i]);
            switch (kind) {
                case Season.PARTICLES_SNOW:
                    paint.setAlpha(200);
                    canvas.drawCircle(px, py, size[i], paint);
                    break;
                case Season.PARTICLES_SPARKLES:
                    paint.setAlpha((int) (90 + 90 * (.5f + .5f * Math.sin(t * 2 + phase[i]))));
                    canvas.drawCircle(px, py, size[i], paint);
                    break;
                default:
                    paint.setAlpha(kind == Season.PARTICLES_PETALS ? 215 : 230);
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate((float) Math.toDegrees(phase[i] + t * spin[i]));
                    float s = size[i] * (kind == Season.PARTICLES_PETALS ? .75f : 1f);
                    canvas.scale(s, s * (.6f + .4f * Math.abs((float) Math.sin(t * 1.3f + phase[i]))));
                    canvas.drawPath(leaf, paint);
                    canvas.restore();
            }
        }
    }
}
