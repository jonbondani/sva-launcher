package com.sva.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spanish UI without touching every literal: the English texts stay in the code and a
 * dictionary (res/raw/es_strings.tsv) is applied to whatever TextViews a window shows.
 *
 * Exact matches are replaced first; texts built at runtime are split on " · " and new lines and
 * each part is matched exactly, then by known prefixes ("Next · ") and suffixes (" minutes").
 * Edit fields are never touched, so user input is safe.
 */
final class Tr {
    static final String PREF_LANGUAGE = "uiLanguage";
    static final String SYSTEM = "system", SPANISH = "es", ENGLISH = "en";
    private static final int TAG_KEY = R.id.tr_attached;
    private static Map<String, String> exact;
    private static List<String[]> prefixes, suffixes;
    private static Boolean enabledCache;

    private Tr() { }

    /** Language chosen in Appearance; "system" follows the TV's language. */
    static String language(Context context) {
        return prefs(context).getString(PREF_LANGUAGE, SYSTEM);
    }

    static void setLanguage(Context context, String value) {
        prefs(context).edit().putString(PREF_LANGUAGE, value).apply();
        enabledCache = null;
        Locale.setDefault(SPANISH.equals(value) ? SPAIN : DEVICE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Spain's Spanish. */
    static final Locale SPAIN = new Locale("es", "ES");
    /** The TV's own language, kept to go back to it. */
    private static final Locale DEVICE = Locale.getDefault();

    /**
     * Spanish from Spain unless the viewer picked another language: set once on a fresh install,
     * and dates and numbers follow it ("miércoles, 28 de octubre") even on an English TV.
     */
    static void applyLocale(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(PREF_LANGUAGE)) {
            prefs.edit().putString(PREF_LANGUAGE, SPANISH).apply();
            enabledCache = null;
        }
        if (SPANISH.equals(language(context))) Locale.setDefault(SPAIN);
    }

    static boolean enabled(Context context) {
        if (enabledCache == null) {
            String choice = language(context);
            enabledCache = SPANISH.equals(choice)
                    || (SYSTEM.equals(choice) && "es".equals(Locale.getDefault().getLanguage()));
        }
        return enabledCache;
    }

    private static synchronized void load(Context context) {
        if (exact != null) return;
        HashMap<String, String> map = new HashMap<>();
        ArrayList<String[]> starts = new ArrayList<>(), ends = new ArrayList<>();
        try (InputStream input = context.getResources().openRawResource(R.raw.es_strings);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String english = line.substring(0, tab).replace("\\n", "\n");
                String spanish = line.substring(tab + 1).replace("\\n", "\n");
                if (english.isEmpty() || spanish.isEmpty()) continue;
                map.put(english, spanish);
                // Fragments concatenated at runtime: "Next · " + value, value + " minutes".
                if (english.endsWith(" ") || english.endsWith(": ")) starts.add(new String[]{english, spanish});
                if (english.startsWith(" ")) ends.add(new String[]{english, spanish});
            }
        } catch (Exception ignored) { }
        // Longest fragments first so "Search failed · " wins over "Search ".
        java.util.Collections.sort(starts, (a, b) -> b[0].length() - a[0].length());
        java.util.Collections.sort(ends, (a, b) -> b[0].length() - a[0].length());
        exact = map;
        prefixes = starts;
        suffixes = ends;
    }

    /** Spanish for {@code text} when Spanish is active and a translation is known; otherwise the input. */
    static CharSequence t(Context context, CharSequence text) {
        if (text == null || text.length() == 0 || !enabled(context)) return text;
        load(context);
        String value = text.toString();
        String direct = exact.get(value);
        if (direct != null) return direct;
        // Known fragments first ("Next · " + programme), then compound texts part by part.
        String fragment = fragment(value);
        if (!fragment.equals(value)) return fragment;
        if (value.contains("\n")) return joinTranslated(value, "\n");
        if (value.contains(" · ")) return joinTranslated(value, " · ");
        return value;
    }

    static String s(Context context, String text) {
        CharSequence result = t(context, text);
        return result == null ? null : result.toString();
    }

    static String[] all(Context context, String[] texts) {
        if (!enabled(context)) return texts;
        String[] result = new String[texts.length];
        for (int i = 0; i < texts.length; i++) result[i] = s(context, texts[i]);
        return result;
    }

    private static String joinTranslated(String value, String separator) {
        String[] parts = value.split(java.util.regex.Pattern.quote(separator), -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(separator);
            String part = parts[i];
            String direct = exact.get(part);
            out.append(direct != null ? direct : fragment(part));
        }
        return out.toString();
    }

    private static String fragment(String value) {
        for (String[] prefix : prefixes) {
            if (value.startsWith(prefix[0]) && value.length() > prefix[0].length()) {
                return prefix[1] + value.substring(prefix[0].length());
            }
        }
        for (String[] suffix : suffixes) {
            if (value.endsWith(suffix[0]) && value.length() > suffix[0].length()) {
                return value.substring(0, value.length() - suffix[0].length()) + suffix[1];
            }
        }
        return value;
    }

    // ------------------------------------------------------------------ windows

    static void attach(Activity activity) {
        if (activity != null) attach(activity.getWindow());
    }

    /** Returns the dialog so it can wrap {@code builder.show()}. */
    static <T extends Dialog> T attach(T dialog) {
        if (dialog != null) attach(dialog.getWindow());
        return dialog;
    }

    static void attach(Window window) {
        if (window == null) return;
        View root = window.getDecorView();
        if (!enabled(root.getContext()) || root.getTag(TAG_KEY) != null) return;
        Runnable pass = () -> translateTree(root);
        root.setTag(TAG_KEY, pass);
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            // Layout passes come in bursts (clock, media progress); translate once per burst.
            root.removeCallbacks(pass);
            root.postDelayed(pass, 120);
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        root.post(pass);
    }

    private static void translateTree(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) translateTree(group.getChildAt(i));
            return;
        }
        if (!(view instanceof TextView) || view instanceof EditText) {
            if (view instanceof EditText) {
                EditText field = (EditText) view;
                CharSequence hint = field.getHint();
                CharSequence translatedHint = t(view.getContext(), hint);
                if (hint != null && translatedHint != hint && !translatedHint.toString().equals(hint.toString())) field.setHint(translatedHint);
            }
            return;
        }
        TextView text = (TextView) view;
        CharSequence current = text.getText();
        if (current == null || current.length() == 0) return;
        CharSequence translated = t(view.getContext(), current);
        if (translated != current && !translated.toString().contentEquals(current)) text.setText(translated);
        CharSequence description = text.getContentDescription();
        if (description != null) {
            CharSequence translatedDescription = t(view.getContext(), description);
            if (!translatedDescription.toString().contentEquals(description)) text.setContentDescription(translatedDescription);
        }
    }

    // ------------------------------------------------------------------ toasts

    static Toast toast(Context context, CharSequence text, int duration) {
        return Toast.makeText(context, t(context, text), duration);
    }
}
