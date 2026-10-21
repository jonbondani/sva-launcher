package com.sva.launcher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Estoy viendo" from Stremio: the account's library (the same cloud library Stremio's own apps sync
 * with, at api.strem.io) gives what is in progress and where; Cinemeta, Stremio's public catalogue,
 * gives the episode still and title. Only a session key is stored, never the password.
 *
 * The library endpoint is Stremio's internal API: it is not documented and may change. Everything
 * here fails quietly (empty list), so Home simply hides the row if that happens.
 */
final class Stremio {
    static final String API = "https://api.strem.io/api/";
    static final String CINEMETA = "https://v3-cinemeta.strem.io/meta/";
    static final String PACKAGE = "com.stremio.one";
    static final String PREF_AUTH = "stremioAuthKey";
    static final String PREF_EMAIL = "stremioEmail";
    /** Watched beyond this share of the runtime counts as finished and leaves the row. */
    static final double FINISHED = 0.92;

    private Stremio() { }

    static final class Item {
        String id, type, name, poster, videoId, episodeTitle, still;
        int season, episode;
        long offsetMs, durationMs, lastWatched;

        /** 0..1 of the runtime already watched, or -1 when unknown. */
        double progress() {
            return durationMs > 0 ? Math.min(1d, (double) offsetMs / durationMs) : -1d;
        }

        /** "T2 · E5" for an episode, "" for a film. */
        String episodeLabel() {
            return season > 0 && episode > 0 ? "T" + season + " · E" + episode : "";
        }
    }

    // ------------------------------------------------------------------ account

    static boolean signedIn(SharedPreferences prefs) {
        return !prefs.getString(PREF_AUTH, "").isEmpty();
    }

    /** Blocking. Signs in and keeps only the session key and the e-mail (for display). */
    static void signIn(SharedPreferences prefs, String email, String password) throws Exception {
        JSONObject body = new JSONObject().put("type", "Login").put("email", email).put("password", password);
        JSONObject result = call("login", body);
        String key = result.optString("authKey", "");
        if (key.isEmpty()) throw new IllegalStateException("Stremio no devolvió sesión");
        prefs.edit().putString(PREF_AUTH, key).putString(PREF_EMAIL, email).apply();
    }

    static void signOut(SharedPreferences prefs) {
        prefs.edit().remove(PREF_AUTH).remove(PREF_EMAIL).apply();
    }

    // ------------------------------------------------------------------ library

    /** Blocking. What the account is watching, most recent first, at most {@code limit}. */
    static List<Item> watching(String authKey, int limit) {
        try {
            JSONObject body = new JSONObject().put("authKey", authKey).put("collection", "libraryItem")
                    .put("ids", new JSONArray()).put("all", true);
            Object result = callRaw("datastoreGet", body);
            if (!(result instanceof JSONArray)) return new ArrayList<>();
            List<Item> items = parseLibrary((JSONArray) result, limit);
            for (Item item : items) enrich(item);
            return items;
        } catch (Exception unreachable) {
            return new ArrayList<>();
        }
    }

    /** Pure: library items that are started and not finished, most recently watched first. */
    static List<Item> parseLibrary(JSONArray library, int limit) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < library.length(); i++) {
            JSONObject raw = library.optJSONObject(i);
            // "temp" items (watched without adding to the library) do appear in Stremio's own
            // Continue Watching, so only removed ones are skipped.
            if (raw == null || raw.optBoolean("removed", false) && !raw.optBoolean("temp", false)) continue;
            JSONObject state = raw.optJSONObject("state");
            if (state == null) continue;
            Item item = new Item();
            item.id = raw.optString("_id", "");
            item.type = raw.optString("type", "");
            item.name = raw.optString("name", "");
            item.poster = raw.optString("poster", "");
            item.offsetMs = state.optLong("timeOffset", 0);
            item.durationMs = state.optLong("duration", 0);
            item.videoId = state.optString("video_id", state.optString("videoId", ""));
            item.lastWatched = parseTime(state.opt("lastWatched"));
            if (item.id.isEmpty() || item.offsetMs <= 0) continue;
            if (item.durationMs > 0 && (double) item.offsetMs / item.durationMs >= FINISHED) continue;
            int[] se = seasonEpisode(item.videoId);
            item.season = se[0];
            item.episode = se[1];
            out.add(item);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastWatched, a.lastWatched));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** "tt0903747:2:5" gives {2, 5}; anything else gives {0, 0}. */
    static int[] seasonEpisode(String videoId) {
        if (videoId == null) return new int[]{0, 0};
        String[] parts = videoId.split(":");
        if (parts.length < 3) return new int[]{0, 0};
        try {
            return new int[]{Integer.parseInt(parts[parts.length - 2]), Integer.parseInt(parts[parts.length - 1])};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    /** Accepts epoch millis or an ISO-8601 string; 0 when unknown. */
    static long parseTime(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String && !((String) value).isEmpty()) {
            try {
                java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT);
                iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = iso.parse(((String) value).substring(0, Math.min(19, ((String) value).length())));
                return date == null ? 0 : date.getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    /** Episode still and title from Cinemeta (public); the film's backdrop otherwise. */
    static void enrich(Item item) {
        try {
            JSONObject meta = new JSONObject(get(CINEMETA + item.type + "/" + item.id + ".json")).optJSONObject("meta");
            if (meta == null) return;
            applyMeta(item, meta);
        } catch (Exception ignored) { }
    }

    /** Pure part of {@link #enrich}. */
    static void applyMeta(Item item, JSONObject meta) {
        if (item.name == null || item.name.isEmpty()) item.name = meta.optString("name", "");
        item.still = meta.optString("background", "");
        JSONArray videos = meta.optJSONArray("videos");
        if (videos == null || item.videoId == null || item.videoId.isEmpty()) return;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.optJSONObject(i);
            if (video == null || !item.videoId.equals(video.optString("id"))) continue;
            String thumb = video.optString("thumbnail", "");
            if (!thumb.isEmpty()) item.still = thumb;
            item.episodeTitle = video.optString("name", video.optString("title", ""));
            return;
        }
    }

    // ------------------------------------------------------------------ opening

    /** Opens Stremio on that title (and episode); falls back to Stremio's home. */
    static boolean open(Context context, Item item) {
        String path = "stremio:///detail/" + item.type + "/" + item.id
                + (item.videoId == null || item.videoId.isEmpty() ? "" : "/" + item.videoId);
        Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse(path)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(deep);
            return true;
        } catch (ActivityNotFoundException | SecurityException notSupported) {
            Intent launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGE);
            if (launch == null) launch = context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
            if (launch == null) return false;
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
    }

    /** Sample items for emulator screenshots (real Cinemeta titles, made-up progress). */
    static List<Item> samples() {
        List<Item> out = new ArrayList<>();
        out.add(sample("tt0903747", "series", "Breaking Bad", "tt0903747:2:5", 1_200_000, 2_820_000));
        out.add(sample("tt2861424", "series", "Rick y Morty", "tt2861424:3:7", 600_000, 1_320_000));
        out.add(sample("tt0816692", "movie", "Interstellar", "tt0816692", 4_100_000, 10_140_000));
        for (Item item : out) enrich(item);
        return out;
    }

    private static Item sample(String id, String type, String name, String videoId, long offset, long duration) {
        Item item = new Item();
        item.id = id;
        item.type = type;
        item.name = name;
        item.videoId = videoId;
        item.offsetMs = offset;
        item.durationMs = duration;
        int[] se = seasonEpisode(videoId);
        item.season = se[0];
        item.episode = se[1];
        return item;
    }

    // ------------------------------------------------------------------ http

    private static JSONObject call(String method, JSONObject body) throws Exception {
        Object result = callRaw(method, body);
        if (!(result instanceof JSONObject)) throw new IllegalStateException("Respuesta inesperada de Stremio");
        return (JSONObject) result;
    }

    private static Object callRaw(String method, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API + method).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            InputStream stream = connection.getResponseCode() < 400 ? connection.getInputStream() : connection.getErrorStream();
            JSONObject reply = new JSONObject(read(stream));
            if (reply.has("error")) {
                Object error = reply.get("error");
                String message = error instanceof JSONObject ? ((JSONObject) error).optString("message", "error") : String.valueOf(error);
                throw new IllegalStateException(message);
            }
            return reply.opt("result");
        } finally {
            connection.disconnect();
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        try {
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "{}";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > 8 * 1024 * 1024) throw new IllegalStateException("Respuesta demasiado grande");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
