package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Installed Android games (native ports such as Ship of Harkinian or Sonic Mania) shown in the
 * Games tab next to the NES library. Apps that declare themselves games are picked up
 * automatically; anything else can be added by hand, and any entry can be hidden.
 */
final class AndroidGames {
    private static final String PREFS = "sva_android_games";
    private static final String KEY_ADDED = "added", KEY_HIDDEN = "hidden";

    private AndroidGames() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> set(Context context, String key) {
        return new HashSet<>(prefs(context).getStringSet(key, Collections.<String>emptySet()));
    }

    private static void put(Context context, String key, Set<String> value) {
        prefs(context).edit().putStringSet(key, value).apply();
    }

    @SuppressWarnings("deprecation")
    static boolean declaresGame(ApplicationInfo info) {
        return info.category == ApplicationInfo.CATEGORY_GAME || (info.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
    }

    /** Launchable game packages, sorted by label. */
    static List<String[]> games(Context context) {
        PackageManager manager = context.getPackageManager();
        Set<String> added = set(context, KEY_ADDED), hidden = set(context, KEY_HIDDEN);
        ArrayList<String[]> result = new ArrayList<>();
        for (ApplicationInfo info : manager.getInstalledApplications(0)) {
            if (info.packageName.equals(context.getPackageName()) || hidden.contains(info.packageName)) continue;
            if (!declaresGame(info) && !added.contains(info.packageName)) continue;
            if (MatchRepository.launchIntent(context, info.packageName) == null) continue;
            result.add(new String[]{String.valueOf(manager.getApplicationLabel(info)), info.packageName});
        }
        Collections.sort(result, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        return result;
    }

    static void add(Context context, String appPackage) {
        Set<String> added = set(context, KEY_ADDED), hidden = set(context, KEY_HIDDEN);
        added.add(appPackage);
        hidden.remove(appPackage);
        put(context, KEY_ADDED, added);
        put(context, KEY_HIDDEN, hidden);
    }

    static void hide(Context context, String appPackage) {
        Set<String> added = set(context, KEY_ADDED), hidden = set(context, KEY_HIDDEN);
        added.remove(appPackage);
        hidden.add(appPackage);
        put(context, KEY_ADDED, added);
        put(context, KEY_HIDDEN, hidden);
    }

    static void launch(Activity activity, String appPackage) {
        android.content.Intent intent = MatchRepository.launchIntent(activity, appPackage);
        if (intent == null) return;
        try { activity.startActivity(intent); } catch (Exception ignored) { }
    }

    /** Lets the viewer add any installed app to the Games tab. */
    static void pickToAdd(Activity activity, Runnable done) {
        List<String[]> apps = MatchRepository.launchableApps(activity);
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i)[0];
        new AlertDialog.Builder(activity, LiquidTheme.alertDialogTheme(activity))
                .setTitle("Añadir juego Android")
                .setItems(labels, (d, which) -> { add(activity, apps.get(which)[1]); done.run(); })
                .setNegativeButton("Cancelar", null).show();
    }

    static void confirmHide(Activity activity, String label, String appPackage, Runnable done) {
        new AlertDialog.Builder(activity, LiquidTheme.alertDialogTheme(activity))
                .setTitle(label)
                .setItems(new String[]{"Quitar de Juegos"}, (d, which) -> { hide(activity, appPackage); done.run(); })
                .setNegativeButton("Cancelar", null).show();
    }

    /** A card matching the NES game cards: TV banner when the app has one, icon otherwise. */
    static LinearLayout card(Activity activity, String label, String appPackage, android.view.View.OnClickListener click) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = GameUi.dp(activity, 8);
        card.setPadding(pad, pad, pad, pad);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(click);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                GameUi.shape(LiquidTheme.raisedSurface(activity), LiquidTheme.accent(activity), 3, 14));
        states.addState(new int[]{}, GameUi.shape(LiquidTheme.surface(activity), LiquidTheme.border(activity), 1, 14));
        card.setBackground(states);
        card.setOnFocusChangeListener(LiquidTheme::animateFocus);
        ImageView image = new ImageView(activity);
        image.setBackgroundColor(LiquidTheme.background(activity));
        Drawable art = null;
        PackageManager manager = activity.getPackageManager();
        try {
            art = manager.getApplicationBanner(appPackage);
            if (art == null) {
                art = manager.getApplicationIcon(appPackage);
                int inset = GameUi.dp(activity, 30);
                image.setPadding(inset, inset, inset, inset);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } catch (Exception ignored) { }
        if (art != null) image.setImageDrawable(art);
        else image.setImageResource(R.drawable.sva_image);
        card.addView(image, new LinearLayout.LayoutParams(-1, GameUi.dp(activity, 142)));
        TextView title = GameUi.text(activity, label, 14, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, GameUi.dp(activity, 48));
        titleParams.topMargin = GameUi.dp(activity, 6);
        card.addView(title, titleParams);
        return card;
    }

    /** The trailing "+" card that opens the app picker. */
    static LinearLayout addCard(Activity activity, Runnable done) {
        LinearLayout card = card(activity, "Añadir juego Android", activity.getPackageName(),
                v -> pickToAdd(activity, done));
        ImageView image = (ImageView) card.getChildAt(0);
        image.setImageDrawable(null);
        TextView plus = GameUi.text(activity, "+", 48, true);
        plus.setGravity(Gravity.CENTER);
        card.removeViewAt(0);
        card.addView(plus, 0, new LinearLayout.LayoutParams(-1, GameUi.dp(activity, 142)));
        return card;
    }
}
