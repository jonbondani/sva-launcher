package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Interface language. English text in the code is the lookup key; translations live in
 * assets/i18n/<language>.tsv as "English<TAB>Translation" lines. Missing entries fall back
 * to English, so an untranslated string is never an error.
 */
final class I18n {
    static final String PREF_LANGUAGE = "interfaceLanguage";
    static final String SYSTEM = "system", SPANISH = "es", ENGLISH = "en";
    private static final String PREFS = "launcher";

    private static volatile Map<String, String> table = new HashMap<>();
    private static volatile String active = ENGLISH;

    private I18n() { }

    /** Loads the language chosen in Settings (or the device language). Call once at startup. */
    static synchronized void init(Context context) {
        String chosen = preference(context);
        String language = SYSTEM.equals(chosen) ? Locale.getDefault().getLanguage() : chosen;
        HashMap<String, String> loaded = new HashMap<>();
        if (!ENGLISH.equals(language)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    context.getAssets().open("i18n/" + language + ".tsv"), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    loaded.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
                }
            } catch (Exception ignored) {
                language = ENGLISH; // No translation file for this language.
            }
        }
        table = loaded;
        active = loaded.isEmpty() ? ENGLISH : language;
    }

    static String preference(Context context) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.getString(PREF_LANGUAGE, SYSTEM);
    }

    static void setPreference(Context context, String language) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_LANGUAGE, language).apply();
        init(context);
    }

    static boolean isSpanish() { return SPANISH.equals(active); }

    /** Locale for dates and numbers that matches the interface language. */
    static Locale locale() {
        return isSpanish() ? new Locale("es", "ES") : SYSTEM.equals(active) ? Locale.getDefault() : Locale.getDefault();
    }

    /** Translates an English interface string; unknown strings are returned unchanged. */
    static String t(String english) {
        if (english == null) return null;
        String translated = table.get(english);
        return translated == null ? english : translated;
    }

    /** Translates every entry of an options array. */
    static String[] t(String[] english) {
        String[] result = new String[english.length];
        for (int i = 0; i < english.length; i++) result[i] = t(english[i]);
        return result;
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t");
    }
}
