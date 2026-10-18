package com.sva.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Football fixtures for the teams the viewer follows.
 *
 * Each team is a public iCalendar feed (FotMob, the club's own calendar, Google Calendar...).
 * Matches are merged across teams, tagged with a competition guessed from the event text and
 * opened in whichever app the viewer chose for that competition (or for that team).
 */
final class MatchRepository {
    static final String PREFS = "sva_matches";
    static final String OTHER = "Otros";
    private static final String KEY_TEAMS = "teams";
    private static final String KEY_CACHE = "cache";
    private static final String KEY_REFRESHED = "refreshedAt";
    private static final String KEY_ERRORS = "lastErrors";
    private static final String KEY_COMPETITION_APPS = "competitionApps";
    private static final String KEY_SEEDED = "seeded";
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long STALE_MS = 3L * HOUR_MS;
    private static final long DEFAULT_LENGTH_MS = 2L * HOUR_MS;
    private static final long LOOKAHEAD_MS = 60L * 24L * HOUR_MS;
    private static final int MAX_FEED_BYTES = 4 * 1024 * 1024;

    /** Competition label and the lower-case keywords that identify it, most specific first. */
    private static final String[][] COMPETITIONS = {
            {"Champions League", "champions"},
            {"Europa League", "europa league", "liga europa"},
            {"Conference League", "conference league"},
            {"Supercopa", "supercopa", "super cup", "supercup"},
            {"Copa del Rey", "copa del rey", "copa", "kings cup", "king's cup"},
            {"LaLiga Hypermotion", "hypermotion", "segunda", "laliga2", "la liga 2", "laliga 2"},
            {"LaLiga", "laliga", "la liga", "liga ea sports"},
            {"Mundial de Clubes", "club world cup", "mundial de clubes"},
            {"Amistoso", "friendly", "amistoso", "club friendlies"},
    };

    private MatchRepository() { }

    // ------------------------------------------------------------------ model

    static final class Team {
        final String name, url, appPackage;
        Team(String name, String url, String appPackage) {
            this.name = name == null ? "" : name.trim();
            this.url = url == null ? "" : url.trim();
            this.appPackage = appPackage == null ? "" : appPackage.trim();
        }
        boolean configured() { return !url.isEmpty(); }
    }

    static final class Match {
        final String uid, title, teams, competition, details;
        final long startMs, endMs;
        /** Only the day is known (kick-off time still to be confirmed). */
        final boolean timeTbc;
        Match(String uid, String title, String teams, String competition, String details,
              long startMs, long endMs, boolean timeTbc) {
            this.uid = uid; this.title = title; this.teams = teams; this.competition = competition;
            this.details = details; this.startMs = startMs; this.endMs = endMs; this.timeTbc = timeTbc;
        }
        boolean isLive(long nowMs) { return !timeTbc && startMs <= nowMs && nowMs < endMs; }
        boolean isOver(long nowMs) { return endMs <= nowMs; }
        /** The first team that produced this match, used for its default app. */
        String primaryTeam() {
            int separator = teams.indexOf(" · ");
            return separator < 0 ? teams : teams.substring(0, separator);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ teams

    static List<Team> teams(Context context) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.getBoolean(KEY_SEEDED, false)) {
            ArrayList<Team> defaults = new ArrayList<>();
            defaults.add(new Team("Real Madrid", "", ""));
            defaults.add(new Team("Málaga", "", ""));
            defaults.add(new Team("Sevilla", "", ""));
            saveTeams(context, defaults);
            preferences.edit().putBoolean(KEY_SEEDED, true).apply();
            return defaults;
        }
        ArrayList<Team> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_TEAMS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                Team team = new Team(item.optString("name"), item.optString("url"), item.optString("app"));
                if (!team.name.isEmpty()) result.add(team);
            }
        } catch (Exception ignored) { }
        return result;
    }

    static void saveTeams(Context context, List<Team> teams) {
        JSONArray array = new JSONArray();
        try {
            for (Team team : teams) {
                JSONObject item = new JSONObject();
                item.put("name", team.name);
                item.put("url", team.url);
                item.put("app", team.appPackage);
                array.put(item);
            }
        } catch (Exception ignored) { }
        prefs(context).edit().putString(KEY_TEAMS, array.toString()).putBoolean(KEY_SEEDED, true).apply();
    }

    /** Adds a team or replaces the feed of an existing one with the same name. */
    static String upsertTeam(Context context, String name, String rawUrl) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) return "Falta el nombre del equipo.";
        String url = rawUrl == null || rawUrl.trim().isEmpty() ? "" : normalizeUrl(rawUrl);
        if (url == null) return "El enlace debe ser un calendario público http(s) o webcal.";
        List<Team> current = teams(context);
        ArrayList<Team> next = new ArrayList<>();
        boolean replaced = false;
        for (Team team : current) {
            if (sameName(team.name, cleanName)) {
                next.add(new Team(team.name, url, team.appPackage));
                replaced = true;
            } else next.add(team);
        }
        if (!replaced) next.add(new Team(cleanName, url, ""));
        saveTeams(context, next);
        invalidate(context);
        return null;
    }

    static void removeTeam(Context context, String name) {
        ArrayList<Team> next = new ArrayList<>();
        for (Team team : teams(context)) if (!sameName(team.name, name)) next.add(team);
        saveTeams(context, next);
        invalidate(context);
    }

    static void setTeamApp(Context context, String name, String appPackage) {
        ArrayList<Team> next = new ArrayList<>();
        for (Team team : teams(context)) {
            next.add(sameName(team.name, name) ? new Team(team.name, team.url, appPackage) : team);
        }
        saveTeams(context, next);
    }

    private static boolean sameName(String a, String b) {
        return fold(a).equals(fold(b));
    }

    static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.regionMatches(true, 0, "webcal://", 0, 9)) value = "https://" + value.substring(9);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null || scheme == null) return null;
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
            return uri.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ apps

    static Map<String, String> competitionApps(Context context) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            JSONObject object = new JSONObject(prefs(context).getString(KEY_COMPETITION_APPS, "{}"));
            JSONArray names = object.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                result.put(key, object.optString(key));
            }
        } catch (Exception ignored) { }
        return result;
    }

    static void setCompetitionApp(Context context, String competition, String appPackage) {
        Map<String, String> apps = competitionApps(context);
        if (appPackage == null || appPackage.isEmpty()) apps.remove(competition);
        else apps.put(competition, appPackage);
        prefs(context).edit().putString(KEY_COMPETITION_APPS, new JSONObject(apps).toString()).apply();
    }

    static List<String> knownCompetitions() {
        ArrayList<String> result = new ArrayList<>();
        for (String[] competition : COMPETITIONS) result.add(competition[0]);
        result.add(OTHER);
        return result;
    }

    /** The installed app chosen for this match, or null when the viewer still has to pick one. */
    static String appFor(Context context, Match match) {
        String byCompetition = competitionApps(context).get(match.competition);
        if (isLaunchable(context, byCompetition)) return byCompetition;
        for (Team team : teams(context)) {
            if (sameName(team.name, match.primaryTeam()) && isLaunchable(context, team.appPackage)) return team.appPackage;
        }
        return null;
    }

    static boolean isLaunchable(Context context, String appPackage) {
        return appPackage != null && !appPackage.isEmpty() && launchIntent(context, appPackage) != null;
    }

    static Intent launchIntent(Context context, String appPackage) {
        if (appPackage == null || appPackage.isEmpty()) return null;
        PackageManager manager = context.getPackageManager();
        Intent intent = manager.getLeanbackLaunchIntentForPackage(appPackage);
        if (intent == null) intent = manager.getLaunchIntentForPackage(appPackage);
        if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    static String appLabel(Context context, String appPackage) {
        if (appPackage == null || appPackage.isEmpty()) return "";
        try {
            PackageManager manager = context.getPackageManager();
            return manager.getApplicationLabel(manager.getApplicationInfo(appPackage, 0)).toString();
        } catch (Exception ignored) {
            return appPackage;
        }
    }

    /** Launchable apps as {label, package}, sorted by label; TV launcher entries first. */
    static List<String[]> launchableApps(Context context) {
        PackageManager manager = context.getPackageManager();
        LinkedHashMap<String, String> found = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : manager.queryIntentActivities(query, 0)) {
                if (info.activityInfo == null) continue;
                String appPackage = info.activityInfo.packageName;
                if (appPackage.equals(context.getPackageName()) || found.containsKey(appPackage)) continue;
                found.put(appPackage, String.valueOf(info.loadLabel(manager)));
            }
        }
        ArrayList<String[]> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : found.entrySet()) result.add(new String[]{entry.getValue(), entry.getKey()});
        Collections.sort(result, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        return result;
    }

    // ------------------------------------------------------------------ cache

    static boolean isStale(Context context) {
        long refreshed = prefs(context).getLong(KEY_REFRESHED, 0L);
        long now = System.currentTimeMillis();
        return refreshed <= 0 || now - refreshed > STALE_MS || refreshed > now;
    }

    static long refreshedAt(Context context) { return prefs(context).getLong(KEY_REFRESHED, 0L); }

    static String lastErrors(Context context) { return prefs(context).getString(KEY_ERRORS, ""); }

    private static void invalidate(Context context) {
        prefs(context).edit().putLong(KEY_REFRESHED, 0L).apply();
    }

    static List<Match> cached(Context context) {
        ArrayList<Match> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_CACHE, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Match(item.optString("uid"), item.optString("title"), item.optString("teams"),
                        item.optString("competition", OTHER), item.optString("details"),
                        item.optLong("start"), item.optLong("end"), item.optBoolean("tbc")));
            }
        } catch (Exception ignored) { }
        return result;
    }

    /** Upcoming and live matches, soonest first. */
    static List<Match> upcoming(Context context, long nowMs) {
        ArrayList<Match> result = new ArrayList<>();
        for (Match match : cached(context)) if (!match.isOver(nowMs)) result.add(match);
        return result;
    }

    /** A live match, otherwise the next one starting within {@code withinMs}. */
    static Match forHome(Context context, long nowMs, long withinMs) {
        Match next = null;
        for (Match match : upcoming(context, nowMs)) {
            if (match.isLive(nowMs)) return match;
            if (next == null && match.startMs - nowMs <= withinMs) next = match;
        }
        return next;
    }

    static Match byUid(Context context, String uid) {
        if (uid == null) return null;
        for (Match match : cached(context)) if (uid.equals(match.uid)) return match;
        return null;
    }

    /** Downloads every configured feed. Blocking: call it off the main thread. */
    static String refresh(Context context) {
        long now = System.currentTimeMillis();
        LinkedHashMap<String, Match> merged = new LinkedHashMap<>();
        StringBuilder errors = new StringBuilder();
        int configured = 0;
        for (Team team : teams(context)) {
            if (!team.configured()) continue;
            configured++;
            try {
                for (Match match : parse(download(team.url), team.name, now)) {
                    String key = sameFixtureKey(merged, match, team.name);
                    if (key == null) key = dedupeKey(match);
                    Match existing = merged.get(key);
                    if (existing == null) merged.put(key, match);
                    else if (!existing.teams.contains(team.name)) {
                        merged.put(key, new Match(existing.uid, existing.title, existing.teams + " · " + team.name,
                                existing.competition, existing.details, existing.startMs, existing.endMs, existing.timeTbc));
                    }
                }
            } catch (Exception error) {
                if (errors.length() > 0) errors.append('\n');
                errors.append(team.name).append(": ").append(error.getMessage() == null ? "error de red" : error.getMessage());
            }
        }
        ArrayList<Match> matches = new ArrayList<>(merged.values());
        Collections.sort(matches, (a, b) -> Long.compare(a.startMs, b.startMs));
        JSONArray array = new JSONArray();
        try {
            for (Match match : matches) {
                JSONObject item = new JSONObject();
                item.put("uid", match.uid);
                item.put("title", match.title);
                item.put("teams", match.teams);
                item.put("competition", match.competition);
                item.put("details", match.details);
                item.put("start", match.startMs);
                item.put("end", match.endMs);
                item.put("tbc", match.timeTbc);
                array.put(item);
            }
        } catch (Exception ignored) { }
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_ERRORS, errors.toString())
                .putLong(KEY_REFRESHED, now);
        // Keep the previous fixtures when every feed failed (for example while offline).
        if (configured == 0 || matches.size() > 0 || errors.length() == 0) editor.putString(KEY_CACHE, array.toString());
        editor.apply();
        if (configured == 0) return "Añade el enlace del calendario de al menos un equipo.";
        return errors.length() == 0 ? null : errors.toString();
    }

    /**
     * Finds a fixture already merged from another team's feed: same kick-off minute and a title
     * that names this team (the feeds rarely agree on "vs", "-" or the club's full name).
     */
    private static String sameFixtureKey(Map<String, Match> merged, Match match, String teamName) {
        String team = fold(teamName);
        for (Map.Entry<String, Match> entry : merged.entrySet()) {
            Match existing = entry.getValue();
            if (existing.startMs / 60000L != match.startMs / 60000L) continue;
            if (team.isEmpty() || !fold(existing.title).contains(team)) continue;
            // ...and the new title must name a team that produced the existing fixture.
            String newTitle = fold(match.title);
            for (String other : existing.teams.split(" · ")) {
                if (!fold(other).isEmpty() && newTitle.contains(fold(other))) return entry.getKey();
            }
        }
        return null;
    }

    private static String dedupeKey(Match match) {
        // Two teams' feeds describe the same fixture with slightly different titles.
        return (match.startMs / 60000L) + "|" + fold(match.title).replaceAll("[^a-z0-9]", "");
    }

    static String download(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/calendar, text/plain;q=0.9, */*;q=0.5");
            connection.setRequestProperty("User-Agent", "SvaLauncher/1.0 (Android TV)");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("el calendario respondió HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0, count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_FEED_BYTES) throw new IllegalArgumentException("el calendario ocupa más de 4 MB");
                    output.write(buffer, 0, count);
                }
                String feed = new String(output.toByteArray(), StandardCharsets.UTF_8);
                if (!feed.contains("BEGIN:VCALENDAR")) throw new IllegalArgumentException("el enlace no es un calendario iCal");
                return feed;
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ------------------------------------------------------------------ iCalendar

    static List<Match> parse(String feed, String teamName, long nowMs) {
        ArrayList<Match> result = new ArrayList<>();
        if (feed == null) return result;
        ArrayList<String> lines = new ArrayList<>();
        for (String line : feed.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !lines.isEmpty()) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + line.substring(1));
            } else lines.add(line);
        }
        Map<String, String[]> event = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if ("BEGIN:VEVENT".equalsIgnoreCase(trimmed)) event = new HashMap<>();
            else if ("END:VEVENT".equalsIgnoreCase(trimmed)) {
                if (event != null) {
                    Match match = toMatch(event, teamName, nowMs);
                    if (match != null) result.add(match);
                }
                event = null;
            } else if (event != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String head = line.substring(0, colon);
                int semicolon = head.indexOf(';');
                String name = (semicolon < 0 ? head : head.substring(0, semicolon)).toUpperCase(Locale.ROOT);
                String parameters = semicolon < 0 ? "" : head.substring(semicolon + 1);
                if (!event.containsKey(name)) event.put(name, new String[]{parameters, line.substring(colon + 1)});
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.startMs, b.startMs));
        return result;
    }

    private static Match toMatch(Map<String, String[]> event, String teamName, long nowMs) {
        String[] start = event.get("DTSTART");
        if (start == null) return null;
        String status = value(event, "STATUS");
        if ("CANCELLED".equalsIgnoreCase(status)) return null;
        try {
            long startMs = parseDate(start[0], start[1]);
            boolean timeTbc = isDateOnly(start[0], start[1]);
            String[] end = event.get("DTEND");
            long endMs = end == null ? startMs + DEFAULT_LENGTH_MS : parseDate(end[0], end[1]);
            if (timeTbc) endMs = startMs + 24L * HOUR_MS;
            else if (endMs <= startMs || endMs - startMs > 12L * HOUR_MS) endMs = startMs + DEFAULT_LENGTH_MS;
            if (endMs <= nowMs - 6L * HOUR_MS || startMs > nowMs + LOOKAHEAD_MS) return null;
            String title = unescape(value(event, "SUMMARY")).trim();
            if (title.isEmpty()) title = "Partido de " + teamName;
            String description = unescape(value(event, "DESCRIPTION")).trim();
            String location = unescape(value(event, "LOCATION")).trim();
            String categories = unescape(value(event, "CATEGORIES")).trim();
            String competition = competition(title + " " + categories + " " + description);
            String details = location.isEmpty() ? firstLine(description) : location;
            String uid = value(event, "UID");
            if (uid.isEmpty()) uid = teamName + "-" + startMs;
            return new Match(uid, title, teamName, competition, details, startMs, endMs, timeTbc);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String competition(String text) {
        String folded = " " + fold(text) + " ";
        for (String[] competition : COMPETITIONS) {
            for (int i = 1; i < competition.length; i++) {
                if (folded.contains(competition[i])) return competition[0];
            }
        }
        return OTHER;
    }

    private static boolean isDateOnly(String parameters, String raw) {
        return raw.trim().length() == 8 || parameters.toUpperCase(Locale.ROOT).contains("VALUE=DATE;")
                || parameters.toUpperCase(Locale.ROOT).endsWith("VALUE=DATE");
    }

    private static long parseDate(String parameters, String raw) {
        String value = raw.trim();
        String tzid = null;
        boolean dateOnly = value.length() == 8;
        for (String parameter : parameters.split(";")) {
            int equals = parameter.indexOf('=');
            if (equals <= 0) continue;
            String key = parameter.substring(0, equals).toUpperCase(Locale.ROOT);
            String parameterValue = parameter.substring(equals + 1).replace("\"", "");
            if ("TZID".equals(key)) tzid = parameterValue;
            if ("VALUE".equals(key) && "DATE".equalsIgnoreCase(parameterValue)) dateOnly = true;
        }
        ZoneId zone = ZoneId.systemDefault();
        if (tzid != null) {
            try { zone = ZoneId.of(tzid); } catch (Exception ignored) { }
        }
        if (dateOnly) {
            return LocalDate.parse(value.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(zone).toInstant().toEpochMilli();
        }
        if (value.endsWith("Z")) {
            String pattern = value.length() == 14 ? "uuuuMMdd'T'HHmmX" : "uuuuMMdd'T'HHmmssX";
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
                    .atZoneSameInstant(ZoneOffset.UTC).toInstant().toEpochMilli();
        }
        String pattern = value.length() == 13 ? "uuuuMMdd'T'HHmm" : "uuuuMMdd'T'HHmmss";
        LocalDateTime local = LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
        return ZonedDateTime.of(local, zone).toInstant().toEpochMilli();
    }

    private static String value(Map<String, String[]> event, String key) {
        String[] property = event.get(key);
        return property == null ? "" : property[1];
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",")
                .replace("\\;", ";").replace("\\\\", "\\");
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        String line = newline < 0 ? value : value.substring(0, newline);
        return line.length() > 80 ? line.substring(0, 79) + "…" : line;
    }

    static String fold(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).trim();
    }
}
