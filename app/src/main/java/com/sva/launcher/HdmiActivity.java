package com.sva.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Shows one of the TV's own inputs (an HDMI port) full screen inside Sva.
 *
 * On Fire OS 8 televisions Amazon keeps input switching to itself (its player needs a permission
 * only Amazon's apps hold), so asking the system to open an input just lands in Live TV settings.
 * MediaTek's own input service, though, lets any app show an input with a standard TvView. Seen
 * working on an Insignia Fire TV with Fire OS 8.1.8.0 (Insignia Clean Home, MIT, after CleanYourTV).
 * Back returns to Sva. If the input cannot be shown the screen says so; nothing on the TV changes.
 */
public final class HdmiActivity extends Activity {
    static final String EXTRA_INPUT_ID = "input_id";
    static final String EXTRA_LABEL = "label";
    private TvView video;
    private TextView status;
    private String label = "HDMI";

    /** Inputs this screen can show: MediaTek's TV input service (Fire TV televisions on MediaTek). */
    static boolean canShow(String inputId) {
        return inputId != null && inputId.startsWith("com.mediatek.tis/");
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String given = getIntent().getStringExtra(EXTRA_LABEL);
        if (given != null && !given.trim().isEmpty()) label = given.trim();
        // Opaque black: a translucent window would leave the picture behind Sva after Back.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new TvView(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(22);
        status.setTypeface(LiquidTheme.font(this));
        status.setShadowLayer(8, 0, 1, 0xCC000000);
        int pad = LiquidTheme.dp(this, 36);
        status.setPadding(pad, pad, pad, pad);
        status.setText(label + " · conectando…");
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        video.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String inputId) { status.setVisibility(View.GONE); }

            @Override public void onVideoUnavailable(String inputId, int reason) {
                status.setVisibility(View.VISIBLE);
                status.setText(label + " · sin imagen. ¿Está encendido el aparato? Atrás para volver.");
            }

            @Override public void onConnectionFailed(String inputId) { unavailable(); }

            @Override public void onDisconnected(String inputId) { unavailable(); }
        });
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        String id = getIntent().getStringExtra(EXTRA_INPUT_ID);
        if (!canShow(id)) { finish(); return; }
        try {
            video.tune(id, Uri.EMPTY);
            video.setStreamVolume(1f);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            unavailable();
        }
    }

    @Override protected void onPause() {
        try { video.reset(); } catch (RuntimeException ignored) { }
        super.onPause();
    }

    private void unavailable() {
        status.setVisibility(View.VISIBLE);
        status.setText(label + " no se puede mostrar aquí. Usa el menú de entradas de la tele. Atrás para volver.");
    }
}
