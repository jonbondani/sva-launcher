package com.sva.launcher;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Seasonal themes: a wallpaper, an accent colour and a light animation that follow the calendar
 * (Spain), or one the user picks. Pure Java so the calendar rules can be tested off the device.
 */
final class Season {
    static final String PREF = "seasonTheme";
    static final String PREF_ANIMATION = "seasonAnimation";

    static final String AUTO = "auto", CLASSIC = "classic", AUTUMN = "autumn", HALLOWEEN = "halloween",
            CHRISTMAS = "christmas", WINTER = "winter", SPRING = "spring", SUMMER = "summer";

    /** What the settings list offers, in order. */
    static final String[] CHOICES = {AUTO, CLASSIC, AUTUMN, HALLOWEEN, CHRISTMAS, WINTER, SPRING, SUMMER};
    static final String[] LABELS = {"Automático (según la fecha)", "Clásico (montaña)", "Otoño", "Halloween",
            "Navidad", "Invierno", "Primavera", "Verano"};

    /** Falling things drawn over the wallpaper. */
    static final int PARTICLES_NONE = 0, PARTICLES_LEAVES = 1, PARTICLES_SNOW = 2, PARTICLES_PETALS = 3,
            PARTICLES_SPARKLES = 4;

    private Season() { }

    /**
     * The season for a date (month 1..12). Halloween runs 25 Oct to 2 Nov (Todos los Santos
     * included), Christmas 1 Dec to 6 Jan (Reyes); the rest follows the astronomical seasons.
     */
    static String forDate(int month, int day) {
        int md = month * 100 + day;
        if (md >= 1025 && md <= 1102) return HALLOWEEN;
        if (md >= 1201 || md <= 106) return CHRISTMAS;
        if (md <= 319) return WINTER;
        if (md <= 620) return SPRING;
        if (md <= 922) return SUMMER;
        return AUTUMN;
    }

    /** The season actually shown: the user's pick, or the date's when the pick is automatic. */
    static String resolve(String choice, int month, int day) {
        String normal = normalize(choice);
        if (normal == null || AUTO.equals(normal)) return forDate(month, day);
        return normal;
    }

    /**
     * Turns what a person types (Spanish or English, with or without accents) into a choice,
     * or null when it is not one. Used by the settings intent from the computer.
     */
    static String normalize(String raw) {
        if (raw == null) return null;
        String key = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z]", "");
        switch (key) {
            case "auto": case "automatico": case "automatic": return AUTO;
            case "classic": case "clasico": case "montana": case "ninguno": case "off": return CLASSIC;
            case "autumn": case "otono": case "fall": return AUTUMN;
            case "halloween": return HALLOWEEN;
            case "christmas": case "navidad": case "xmas": return CHRISTMAS;
            case "winter": case "invierno": return WINTER;
            case "spring": case "primavera": return SPRING;
            case "summer": case "verano": return SUMMER;
            default: return null;
        }
    }

    static String label(String season) {
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i].equals(season)) return LABELS[i];
        return LABELS[1];
    }

    static int index(String choice) {
        String normal = normalize(choice);
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i].equals(normal)) return i;
        return 0;
    }

    /** Accent for focus and selection, or 0 to keep Sva's blue. */
    static int accent(String season, boolean light) {
        switch (season) {
            case AUTUMN: return light ? 0xFFD2601C : 0xFFF08A3C;
            case HALLOWEEN: return light ? 0xFFE0620F : 0xFFFF8A2A;
            case CHRISTMAS: return light ? 0xFFD62F3A : 0xFFFF5A5F;
            case WINTER: return light ? 0xFF2A7FC4 : 0xFF63B6F2;
            case SPRING: return light ? 0xFFD94C8E : 0xFFF27DB5;
            case SUMMER: return light ? 0xFF0089B8 : 0xFF2EC4E6;
            default: return 0;
        }
    }

    static int particles(String season) {
        switch (season) {
            case AUTUMN: case HALLOWEEN: return PARTICLES_LEAVES;
            case CHRISTMAS: case WINTER: return PARTICLES_SNOW;
            case SPRING: return PARTICLES_PETALS;
            case SUMMER: return PARTICLES_SPARKLES;
            default: return PARTICLES_NONE;
        }
    }

    /** Colours for the falling things of a season. */
    static int[] particleColours(String season) {
        switch (season) {
            case HALLOWEEN: return new int[]{0xFFE8792B, 0xFFD9481C, 0xFF8A3B1E, 0xFFF2A33A};
            case AUTUMN: return new int[]{0xFFD9481C, 0xFFE8792B, 0xFFF2A33A, 0xFFC23B22, 0xFFE0B040};
            case SPRING: return new int[]{0xFFF7B6CF, 0xFFF39AC0, 0xFFFBD0E0, 0xFFFFFFFF};
            case SUMMER: return new int[]{0xFFFFF6C8, 0xFFFFFFFF};
            default: return new int[]{0xFFFFFFFF, 0xFFEAF2FF};
        }
    }
}
