package com.sva.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Full-screen forecast in the spirit of the iOS Weather app: current conditions on the left,
 * the next 24 hours as a strip and the week as rows with a temperature range bar on the right.
 */
final class WeatherForecastDialog {
    private final Activity activity;
    private final WeatherRepository.Forecast forecast;
    private final boolean fahrenheit, mph;
    private final Locale locale;
    private HorizontalScrollView hoursStrip;

    private WeatherForecastDialog(Activity activity, WeatherRepository.Forecast forecast, boolean fahrenheit, boolean mph) {
        this.activity = activity;
        this.forecast = forecast;
        this.fahrenheit = fahrenheit;
        this.mph = mph;
        this.locale = Tr.enabled(activity) ? new Locale("es", "ES") : Locale.getDefault();
    }

    static void show(Activity activity, WeatherRepository.Forecast forecast, boolean fahrenheit, boolean mph) {
        new WeatherForecastDialog(activity, forecast, fahrenheit, mph).build();
    }

    private int dp(int value) { return LiquidTheme.dp(activity, value); }

    private long temp(double celsius) { return Math.round(fahrenheit ? WeatherRepository.fahrenheit(celsius) : celsius); }

    private String condition(int code) { return Tr.s(activity, WeatherRepository.condition(code)); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(bold ? LiquidTheme.bold(activity) : LiquidTheme.font(activity));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable panel() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x26FFFFFF);
        shape.setCornerRadius(dp(22));
        shape.setStroke(dp(1), 0x22FFFFFF);
        return shape;
    }

    /** Sky colours by weather and daylight, like the iOS backgrounds. */
    private int[] sky() {
        int code = forecast.code;
        if (!forecast.day) return new int[]{0xFF0B1630, 0xFF1C2B4F, 0xFF2C3A5E};
        if (code >= 95) return new int[]{0xFF2B3240, 0xFF46505F, 0xFF5A6474};
        if (code >= 51) return new int[]{0xFF3E5468, 0xFF5D7387, 0xFF7C91A4};
        if (code >= 3) return new int[]{0xFF4F6A86, 0xFF7590AA, 0xFF9AB0C4};
        return new int[]{0xFF1E6FC8, 0xFF4A95DE, 0xFF7DB8EE};
    }

    private void build() {
        Dialog dialog = new Dialog(activity, R.style.SvaFullScreenDialogTheme);
        FrameLayout root = new FrameLayout(activity);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, sky()));
        LinearLayout columns = new LinearLayout(activity);
        columns.setPadding(dp(56), dp(40), dp(56), dp(32));
        root.addView(columns, new FrameLayout.LayoutParams(-1, -1));

        // — Current conditions ————————————————————————————————————————
        LinearLayout now = new LinearLayout(activity);
        now.setOrientation(LinearLayout.VERTICAL);
        now.setGravity(Gravity.CENTER_HORIZONTAL);
        now.setPadding(0, dp(24), dp(40), 0);
        now.addView(text(forecast.location, 26, Color.WHITE, false));
        TextView big = text(temp(forecast.temperature) + "°", 96, Color.WHITE, false);
        big.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        LinearLayout.LayoutParams bigParams = new LinearLayout.LayoutParams(-2, -2);
        bigParams.topMargin = dp(6);
        now.addView(big, bigParams);
        now.addView(text(condition(forecast.code), 22, 0xEEFFFFFF, false));
        if (!forecast.days.isEmpty()) {
            WeatherRepository.Day today = forecast.days.get(0);
            TextView range = text("Máx. " + temp(today.high) + "°   Mín. " + temp(today.low) + "°", 20, Color.WHITE, false);
            LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(-2, -2);
            rangeParams.topMargin = dp(6);
            now.addView(range, rangeParams);
        }
        LinearLayout facts = new LinearLayout(activity);
        facts.setOrientation(LinearLayout.VERTICAL);
        facts.setPadding(dp(20), dp(16), dp(20), dp(16));
        facts.setBackground(panel());
        long wind = Math.round(mph ? WeatherRepository.milesPerHour(forecast.windSpeed) : forecast.windSpeed);
        addFact(facts, "Sensación térmica", temp(forecast.apparentTemperature) + "°");
        addFact(facts, "Viento", wind + (mph ? " mph" : " km/h"));
        if (forecast.humidity >= 0) addFact(facts, "Humedad", forecast.humidity + " %");
        addFact(facts, "Actualizado", new SimpleDateFormat("HH:mm", locale).format(new Date(forecast.fetchedAt)));
        LinearLayout.LayoutParams factsParams = new LinearLayout.LayoutParams(dp(300), -2);
        factsParams.topMargin = dp(28);
        now.addView(facts, factsParams);
        columns.addView(now, new LinearLayout.LayoutParams(0, -1, 4));

        // — Hours and week ————————————————————————————————————————————
        ScrollView rightScroll = new ScrollView(activity);
        rightScroll.setVerticalScrollBarEnabled(false);
        LinearLayout right = new LinearLayout(activity);
        right.setOrientation(LinearLayout.VERTICAL);
        rightScroll.addView(right);
        columns.addView(rightScroll, new LinearLayout.LayoutParams(0, -1, 6));

        if (!forecast.hours.isEmpty()) {
            LinearLayout hoursPanel = new LinearLayout(activity);
            hoursPanel.setOrientation(LinearLayout.VERTICAL);
            hoursPanel.setBackground(panel());
            hoursPanel.setPadding(dp(18), dp(14), dp(18), dp(14));
            hoursPanel.addView(text("PRÓXIMAS 24 HORAS", 13, 0xB3FFFFFF, true));
            HorizontalScrollView strip = new HorizontalScrollView(activity);
            hoursStrip = strip;
            strip.setHorizontalScrollBarEnabled(false);
            strip.setFocusable(true);
            strip.setFocusableInTouchMode(false);
            LinearLayout row = new LinearLayout(activity);
            for (int i = 0; i < forecast.hours.size(); i++) {
                WeatherRepository.Hour hour = forecast.hours.get(i);
                LinearLayout cell = new LinearLayout(activity);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                String label = i == 0 ? "Ahora" : hour.time.length() >= 13 ? hour.time.substring(11, 13) : hour.time;
                cell.addView(text(label, 16, Color.WHITE, i == 0));
                TextView symbol = text(WeatherRepository.symbol(hour.code, hour.day), 26, symbolColor(hour.code, hour.day), false);
                symbol.setPadding(0, dp(8), 0, dp(4));
                cell.addView(symbol);
                cell.addView(text(hour.precipitation >= 30 ? hour.precipitation + " %" : " ", 12, 0xFF9FD8FF, false));
                cell.addView(text(temp(hour.temperature) + "°", 18, Color.WHITE, true));
                row.addView(cell, new LinearLayout.LayoutParams(dp(62), -2));
            }
            strip.addView(row);
            LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(-1, -2);
            stripParams.topMargin = dp(12);
            hoursPanel.addView(strip, stripParams);
            right.addView(hoursPanel, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout week = new LinearLayout(activity);
        week.setOrientation(LinearLayout.VERTICAL);
        week.setBackground(panel());
        week.setPadding(dp(18), dp(14), dp(18), dp(8));
        week.addView(text("PREVISIÓN DE " + forecast.days.size() + " DÍAS", 13, 0xB3FFFFFF, true));
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (WeatherRepository.Day day : forecast.days) { min = Math.min(min, day.low); max = Math.max(max, day.high); }
        for (int i = 0; i < forecast.days.size(); i++) week.addView(dayRow(forecast.days.get(i), min, max));
        LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(-1, -2);
        weekParams.topMargin = dp(16);
        right.addView(week, weekParams);
        TextView hint = text("← → horas  ·  ↑ ↓ semana  ·  Atrás para cerrar", 13, 0xB3FFFFFF, false);
        hint.setPadding(dp(4), dp(14), 0, dp(8));
        right.addView(hint);

        dialog.setContentView(root);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { rightScroll.smoothScrollBy(0, dp(120)); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { rightScroll.smoothScrollBy(0, -dp(120)); return true; }
            if (hoursStrip != null && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { hoursStrip.smoothScrollBy(dp(250), 0); return true; }
            if (hoursStrip != null && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { hoursStrip.smoothScrollBy(-dp(250), 0); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { d.dismiss(); return true; }
            return false;
        });
        BackNavigation.install(dialog, dialog::dismiss);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        root.requestFocus();
    }

    private void addFact(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(text(label, 16, 0xCCFFFFFF, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(value, 16, Color.WHITE, true));
        parent.addView(row);
    }

    private View dayRow(WeatherRepository.Day day, double min, double max) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(text(weekday(day.date), 19, Color.WHITE, false), new LinearLayout.LayoutParams(dp(110), -2));
        LinearLayout icon = new LinearLayout(activity);
        icon.setOrientation(LinearLayout.VERTICAL);
        icon.setGravity(Gravity.CENTER_HORIZONTAL);
        icon.addView(text(WeatherRepository.symbol(day.code, true), 24, symbolColor(day.code, true), false));
        if (day.precipitation >= 30) icon.addView(text(day.precipitation + " %", 11, 0xFF9FD8FF, false));
        row.addView(icon, new LinearLayout.LayoutParams(dp(64), -2));
        TextView low = text(temp(day.low) + "°", 19, 0xB3FFFFFF, false);
        low.setGravity(Gravity.END);
        row.addView(low, new LinearLayout.LayoutParams(dp(52), -2));
        row.addView(rangeBar(day, min, max), new LinearLayout.LayoutParams(0, dp(6), 1));
        TextView high = text(temp(day.high) + "°", 19, Color.WHITE, true);
        row.addView(high, new LinearLayout.LayoutParams(dp(52), -2));
        ((LinearLayout.LayoutParams) row.getChildAt(3).getLayoutParams()).setMargins(dp(14), 0, dp(14), 0);
        return row;
    }

    /** A track spanning the week's range with this day's low→high segment coloured cool→warm. */
    private View rangeBar(WeatherRepository.Day day, double min, double max) {
        FrameLayout track = new FrameLayout(activity);
        GradientDrawable trackShape = new GradientDrawable();
        trackShape.setColor(0x33FFFFFF);
        trackShape.setCornerRadius(dp(3));
        track.setBackground(trackShape);
        View segment = new View(activity);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{tempColor(day.low), tempColor(day.high)});
        fill.setCornerRadius(dp(3));
        segment.setBackground(fill);
        double span = Math.max(1, max - min);
        final float start = (float) ((day.low - min) / span), end = (float) ((day.high - min) / span);
        track.addView(segment, new FrameLayout.LayoutParams(0, -1));
        track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = r - l;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) segment.getLayoutParams();
            int left = Math.round(width * start), right = Math.max(left + dp(6), Math.round(width * end));
            if (params.leftMargin != left || params.width != right - left) {
                params.leftMargin = left;
                params.width = right - left;
                segment.post(() -> segment.setLayoutParams(params));
            }
        });
        return track;
    }

    private static int tempColor(double celsius) {
        if (celsius < 5) return 0xFF7FD4FF;
        if (celsius < 12) return 0xFF8BE0C9;
        if (celsius < 18) return 0xFFB9E37A;
        if (celsius < 24) return 0xFFF5D35B;
        if (celsius < 30) return 0xFFF7A54A;
        return 0xFFF2674A;
    }

    private static int symbolColor(int code, boolean day) {
        if (code == 0 && day) return 0xFFFFD34D;
        if (code == 0) return 0xFFE6ECFF;
        if (code >= 95) return 0xFFFFE27A;
        return Color.WHITE;
    }

    private String weekday(String isoDate) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (isoDate.equals(today)) return "Hoy";
            String label = new SimpleDateFormat("EEEE", locale).format(date);
            return Character.toUpperCase(label.charAt(0)) + label.substring(1);
        } catch (Exception ignored) {
            return isoDate;
        }
    }
}
