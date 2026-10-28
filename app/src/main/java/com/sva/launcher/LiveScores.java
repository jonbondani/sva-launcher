package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Today's scores (live minute, result, crests) from ESPN's public scoreboard feed for the
 * competitions Spanish teams play in, shown as Google-style result cards. Opening a card
 * launches the app chosen for that competition in "Partidos".
 */
final class LiveScores {
    /** ESPN league slug → competition label used by MatchRepository's app mapping. */
    private static final String[][] LEAGUES = {
            {"esp.1", "LaLiga"},
            {"esp.2", "LaLiga Hypermotion"},
            {"uefa.champions", "Champions League"},
            {"uefa.europa", "Europa League"},
            {"uefa.europa.conf", "Conference League"},
            {"esp.copa_del_rey", "Copa del Rey"},
            {"esp.super_cup", "Supercopa"},
    };
    private static final String PREFS = "sva_live_scores";
    private static final long LIVE_REFRESH_MS = 45_000L, IDLE_REFRESH_MS = 10L * 60_000L;

    private LiveScores() { }

    static final class Game {
        final String id, competition, state, clock, detail;
        final String homeName, awayName, homeScore, awayScore, homeLogo, awayLogo;
        final long startMs;
        Game(String id, String competition, String state, String clock, String detail, long startMs,
             String homeName, String homeScore, String homeLogo, String awayName, String awayScore, String awayLogo) {
            this.id = id; this.competition = competition; this.state = state; this.clock = clock; this.detail = detail;
            this.startMs = startMs; this.homeName = homeName; this.homeScore = homeScore; this.homeLogo = homeLogo;
            this.awayName = awayName; this.awayScore = awayScore; this.awayLogo = awayLogo;
        }
        boolean live() { return "in".equals(state); }
        boolean finished() { return "post".equals(state); }
        boolean upcoming() { return "pre".equals(state); }
        String title() { return homeName + " - " + awayName; }
        String scoreLine() { return homeName + " " + homeScore + " - " + awayScore + " " + awayName; }

        /** "67'", "Descanso", "Final" or the kick-off time. */
        String status() {
            if (live()) {
                String lower = detail.toLowerCase(Locale.ROOT);
                if (lower.contains("half") && !lower.contains("2nd") && !lower.contains("1st")) return "Descanso";
                return clock.isEmpty() ? "En juego" : clock;
            }
            if (finished()) return "Final";
            return new SimpleDateFormat("HH:mm", Tr.formatLocale()).format(new Date(startMs));
        }

        boolean involves(String team) {
            String wanted = MatchRepository.fold(team);
            return !wanted.isEmpty() && (sameTeam(homeName, wanted) || sameTeam(awayName, wanted));
        }

        private static boolean sameTeam(String name, String wanted) {
            String folded = MatchRepository.fold(name);
            // "Real Madrid" must not match "Real Madrid Castilla", but "Málaga" matches "Málaga CF".
            return folded.equals(wanted) || folded.equals(wanted + " cf") || folded.equals(wanted + " fc")
                    || (folded.startsWith(wanted + " ") && folded.length() - wanted.length() <= 3);
        }
    }

    // ------------------------------------------------------------------ data

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<Game> cached(Context context) {
        ArrayList<Game> games = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("games", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject g = array.getJSONObject(i);
                games.add(new Game(g.optString("id"), g.optString("competition"), g.optString("state"),
                        g.optString("clock"), g.optString("detail"), g.optLong("start"),
                        g.optString("homeName"), g.optString("homeScore"), g.optString("homeLogo"),
                        g.optString("awayName"), g.optString("awayScore"), g.optString("awayLogo")));
            }
        } catch (Exception ignored) { }
        return games;
    }

    /** Followed teams' games first (live, then upcoming, then finished), then other live games. */
    static List<Game> forTeams(Context context, boolean includeOtherLive) {
        List<MatchRepository.Team> teams = MatchRepository.teams(context);
        ArrayList<Game> mine = new ArrayList<>(), others = new ArrayList<>();
        for (Game game : cached(context)) {
            boolean followed = false;
            for (MatchRepository.Team team : teams) followed |= game.involves(team.name);
            if (followed) mine.add(game);
            else if (includeOtherLive && game.live()) others.add(game);
        }
        Collections.sort(mine, (a, b) -> {
            int ra = a.live() ? 0 : a.upcoming() ? 1 : 2, rb = b.live() ? 0 : b.upcoming() ? 1 : 2;
            return ra != rb ? ra - rb : Long.compare(a.startMs, b.startMs);
        });
        mine.addAll(others);
        return mine;
    }

    /**
     * Today's games of the followed teams for the Home widget: ESPN scores where ESPN covers the
     * competition, plus calendar fixtures it does not cover (Primera Federación, for example),
     * shown with their kick-off time and an "En juego" state but no score.
     */
    static List<Game> todayForHome(Context context, long nowMs) {
        ArrayList<Game> games = new ArrayList<>();
        java.util.Calendar today = java.util.Calendar.getInstance();
        today.setTimeInMillis(nowMs);
        int day = today.get(java.util.Calendar.DAY_OF_YEAR), year = today.get(java.util.Calendar.YEAR);
        for (Game game : forTeams(context, false)) if (sameDay(game.startMs, day, year)) games.add(game);
        for (MatchRepository.Match match : MatchRepository.upcoming(context, nowMs)) {
            if (match.timeTbc || !sameDay(match.startMs, day, year)) continue;
            boolean covered = false;
            for (Game game : games) {
                if (Math.abs(game.startMs - match.startMs) > 3L * 3_600_000L) continue;
                for (String team : match.teams.split(" · ")) covered |= game.involves(team);
            }
            if (covered) continue;
            String[] sides = splitTitle(match.title);
            String state = match.isLive(nowMs) ? "in" : match.startMs > nowMs ? "pre" : "post";
            games.add(new Game(match.uid, match.competition, state, "", "", match.startMs,
                    sides[0], "", "", sides[1], "", ""));
        }
        Collections.sort(games, (a, b) -> {
            int ra = a.live() ? 0 : a.upcoming() ? 1 : 2, rb = b.live() ? 0 : b.upcoming() ? 1 : 2;
            return ra != rb ? ra - rb : Long.compare(a.startMs, b.startMs);
        });
        return games;
    }

    private static boolean sameDay(long ms, int dayOfYear, int year) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(java.util.Calendar.DAY_OF_YEAR) == dayOfYear && c.get(java.util.Calendar.YEAR) == year;
    }

    /** "Málaga CF - Racing", "Juventus vs Real Madrid", "Sevilla v Betis" → the two sides. */
    static String[] splitTitle(String title) {
        String clean = title.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
        for (String separator : new String[]{" - ", " – ", " vs. ", " vs ", " v "}) {
            int at = clean.indexOf(separator);
            if (at > 0) return new String[]{clean.substring(0, at).trim(), clean.substring(at + separator.length()).trim()};
        }
        return new String[]{clean, ""};
    }

    /** The followed team's game being played right now, if any. */
    static Game liveForTeams(Context context) {
        for (Game game : forTeams(context, false)) if (game.live()) return game;
        return null;
    }

    static boolean isStale(Context context) {
        long last = prefs(context).getLong("fetchedAt", 0);
        boolean anyLive = false;
        for (Game game : cached(context)) anyLive |= game.live();
        long now = System.currentTimeMillis();
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(now));
        if (!today.equals(prefs(context).getString("day", ""))) return true;
        return now - last > (anyLive ? LIVE_REFRESH_MS : IDLE_REFRESH_MS) || last > now;
    }

    /** Blocking: downloads today's scoreboard of every league. Returns false when all failed. */
    static boolean refresh(Context context) {
        long now = System.currentTimeMillis();
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
        day.setTimeZone(TimeZone.getDefault());
        String today = day.format(new Date(now));
        JSONArray all = new JSONArray();
        int ok = 0;
        for (String[] league : LEAGUES) {
            try {
                String body = download("https://site.api.espn.com/apis/site/v2/sports/soccer/" + league[0]
                        + "/scoreboard?dates=" + today, 3 * 1024 * 1024);
                parse(new JSONObject(body), league[1], all);
                ok++;
            } catch (Exception ignored) { }
        }
        if (ok == 0) return false;
        prefs(context).edit().putString("games", all.toString()).putLong("fetchedAt", now).putString("day", today).apply();
        return true;
    }

    static void parse(JSONObject root, String competition, JSONArray out) throws Exception {
        JSONArray events = root.optJSONArray("events");
        if (events == null) return;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            JSONObject status = event.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            JSONArray comps = event.optJSONArray("competitions");
            if (type == null || comps == null || comps.length() == 0) continue;
            JSONArray competitors = comps.getJSONObject(0).optJSONArray("competitors");
            if (competitors == null || competitors.length() < 2) continue;
            JSONObject home = null, away = null;
            for (int c = 0; c < competitors.length(); c++) {
                JSONObject team = competitors.getJSONObject(c);
                if ("home".equals(team.optString("homeAway"))) home = team; else away = team;
            }
            if (home == null || away == null) continue;
            long start = 0;
            try { start = iso.parse(event.optString("date")).getTime(); } catch (Exception ignored) { }
            out.put(new JSONObject()
                    .put("id", event.optString("id")).put("competition", competition)
                    .put("state", type.optString("state")).put("detail", type.optString("shortDetail"))
                    .put("clock", status.optString("displayClock")).put("start", start)
                    .put("homeName", teamName(home)).put("homeScore", home.optString("score", ""))
                    .put("homeLogo", logo(home))
                    .put("awayName", teamName(away)).put("awayScore", away.optString("score", ""))
                    .put("awayLogo", logo(away)));
        }
    }

    private static String teamName(JSONObject competitor) {
        JSONObject team = competitor.optJSONObject("team");
        if (team == null) return "?";
        String name = team.optString("shortDisplayName", "");
        return name.isEmpty() ? team.optString("displayName", "?") : name;
    }

    private static String logo(JSONObject competitor) {
        JSONObject team = competitor.optJSONObject("team");
        return team == null ? "" : team.optString("logo", "");
    }

    private static String download(String url, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "SvaLauncher/1.0 (Android TV)");
        try {
            if (connection.getResponseCode() / 100 != 2) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384];
                int total = 0, count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) throw new IllegalStateException("too large");
                    out.write(buffer, 0, count);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    // ------------------------------------------------------------------ crests

    /** Loaded on first use only, so the data code above stays usable in plain JVM unit tests. */
    private static final class Crests {
        static final LruCache<String, Bitmap> CACHE = new LruCache<>(48);
        static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
        static final Handler MAIN = new Handler(Looper.getMainLooper());
    }

    private static void loadCrest(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        final String sized = url;
        view.setTag(sized);
        Bitmap cached = Crests.CACHE.get(sized);
        if (cached != null) { view.setImageBitmap(cached); return; }
        Crests.WORKER.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(sized).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                try (InputStream input = connection.getInputStream()) {
                    // ESPN crests are 500 px; a quarter is plenty for a 28 dp badge.
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4;
                    Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
                    if (bitmap == null) return;
                    Crests.CACHE.put(sized, bitmap);
                    Crests.MAIN.post(() -> { if (sized.equals(view.getTag())) view.setImageBitmap(bitmap); });
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) { }
        });
    }

    // ------------------------------------------------------------------ Google-style card

    /** A result card modelled on Google's sports cards: competition, two team rows, status. */
    static View card(Activity activity, Game game, View.OnClickListener click) {
        boolean light = LiquidTheme.isLight(activity);
        int text = light ? 0xFF202124 : 0xFFE8EAED, muted = light ? 0xFF5F6368 : 0xFF9AA0A6;
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        card.setPadding(pad, dp(activity, 12), pad, dp(activity, 12));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(click);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(light ? Color.WHITE : 0xFF303134,
                LiquidTheme.accent(activity), dp(activity, 3), dp(activity, 16)));
        states.addState(new int[]{}, shape(light ? Color.WHITE : 0xFF303134,
                light ? 0xFFDADCE0 : 0xFF3C4043, dp(activity, 1), dp(activity, 16)));
        card.setBackground(states);
        card.setOnFocusChangeListener(LiquidTheme::animateFocus);

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label(activity, game.competition, 13, muted, false), new LinearLayout.LayoutParams(0, -2, 1));
        if (game.live()) {
            TextView dot = label(activity, "●", 12, 0xFFD93025, false);
            dot.setPadding(0, 0, dp(activity, 6), 0);
            top.addView(dot);
            top.addView(label(activity, "En directo · " + game.status(), 13, 0xFFD93025, true));
        } else {
            top.addView(label(activity, game.finished() ? "Final" : "Hoy " + game.status(), 13, muted, game.finished()));
        }
        card.addView(top);
        card.addView(teamRow(activity, game.homeName, game.homeScore, game.homeLogo, game, true, text, muted));
        card.addView(teamRow(activity, game.awayName, game.awayScore, game.awayLogo, game, false, text, muted));
        card.setContentDescription(game.upcoming() ? game.title() + ", " + game.status() : game.scoreLine() + ", " + game.status());
        return card;
    }

    private static View teamRow(Activity activity, String name, String score, String logo, Game game, boolean home,
                                int text, int muted) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(activity, 8), 0, 0);
        ImageView crest = new ImageView(activity);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadCrest(crest, logo);
        row.addView(crest, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
        boolean winner = game.finished() && score.length() > 0 && compare(game, home) > 0;
        TextView team = label(activity, name, 18, text, winner);
        team.setPadding(dp(activity, 12), 0, 0, 0);
        team.setSingleLine(true);
        row.addView(team, new LinearLayout.LayoutParams(0, -2, 1));
        if (!game.upcoming()) {
            TextView value = label(activity, score, 22, game.finished() && !winner ? muted : text, true);
            row.addView(value);
        }
        return row;
    }

    private static int compare(Game game, boolean home) {
        try {
            int h = Integer.parseInt(game.homeScore), a = Integer.parseInt(game.awayScore);
            return home ? h - a : a - h;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static TextView label(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(LiquidTheme.font(context), bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private static GradientDrawable shape(int fill, int stroke, int width, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(width, stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Context context, int value) { return LiquidTheme.dp(context, value); }
}
