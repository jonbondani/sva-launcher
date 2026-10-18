package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Partidos": upcoming fixtures for the followed teams. Selecting a match opens the app
 * chosen for its competition; the first time, the viewer picks that app from a list.
 *
 * Teams can also be managed over ADB, which is easier than typing a long URL with a remote:
 *   adb shell am start -n com.sva.launcher/.MatchesActivity --es add_team "Real Madrid|https://..."
 *   adb shell am start -n com.sva.launcher/.MatchesActivity --es remove_team "Sevilla"
 */
public final class MatchesActivity extends Activity {
    static final String EXTRA_OPEN_UID = "open_uid";
    static final String EXTRA_ADD_TEAM = "add_team";
    static final String EXTRA_REMOVE_TEAM = "remove_team";
    private static final int MATCHES = 0, TEAMS = 1, APPS = 2;
    private static final Locale SPANISH = new Locale("es", "ES");

    private LinearLayout content;
    private int screen = MATCHES;
    private boolean refreshing;

    static Intent intent(Context context) { return new Intent(context, MatchesActivity.class); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout page = SettingsUi.page(this, "Partidos",
                "Tus equipos en un solo sitio. Pulsa un partido para abrir la app donde lo ves; mantén pulsado para cambiarla.",
                this::onBackPressed);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        addTab(tabs, "Próximos", () -> show(MATCHES));
        addTab(tabs, "Equipos", () -> show(TEAMS));
        addTab(tabs, "Apps", () -> show(APPS));
        addTab(tabs, "Actualizar", () -> refresh(true));
        page.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        content = SettingsUi.scroll(page);
        setContentView(page);
        handleIntent(getIntent());
        render();
        if (MatchRepository.isStale(this)) refresh(false);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        render();
    }

    @Override public void onBackPressed() {
        if (screen != MATCHES) { show(MATCHES); return; }
        super.onBackPressed();
    }

    private void addTab(LinearLayout tabs, String label, Runnable click) {
        Button button = SettingsUi.action(this, label, click);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, SettingsUi.dp(this, 56), 1);
        params.rightMargin = SettingsUi.dp(this, 8);
        params.bottomMargin = SettingsUi.dp(this, 10);
        tabs.addView(button, params);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String add = intent.getStringExtra(EXTRA_ADD_TEAM);
        if (add != null) {
            int separator = add.indexOf('|');
            String name = separator < 0 ? add : add.substring(0, separator);
            String url = separator < 0 ? "" : add.substring(separator + 1);
            String error = MatchRepository.upsertTeam(this, name, url);
            toast(error == null ? "Equipo guardado: " + name.trim() : error);
            if (error == null) refresh(false);
            intent.removeExtra(EXTRA_ADD_TEAM);
        }
        String remove = intent.getStringExtra(EXTRA_REMOVE_TEAM);
        if (remove != null) {
            MatchRepository.removeTeam(this, remove);
            toast("Equipo eliminado: " + remove.trim());
            intent.removeExtra(EXTRA_REMOVE_TEAM);
        }
        String uid = intent.getStringExtra(EXTRA_OPEN_UID);
        if (uid != null) {
            intent.removeExtra(EXTRA_OPEN_UID);
            MatchRepository.Match match = MatchRepository.byUid(this, uid);
            if (match != null) watch(match);
        }
    }

    private void show(int next) { screen = next; render(); }

    private void render() {
        content.removeAllViews();
        switch (screen) {
            case TEAMS: renderTeams(); break;
            case APPS: renderApps(); break;
            default: renderMatches(); break;
        }
        if (content.getChildCount() > 0) content.getChildAt(0).requestFocus();
    }

    // ------------------------------------------------------------------ matches

    private void renderMatches() {
        long now = System.currentTimeMillis();
        List<MatchRepository.Match> matches = MatchRepository.upcoming(this, now);
        boolean anyFeed = false;
        for (MatchRepository.Team team : MatchRepository.teams(this)) anyFeed |= team.configured();
        if (!anyFeed) {
            SettingsUi.hint(content, "Todavía no hay calendarios. Entra en «Equipos» y añade el enlace iCal de cada equipo.");
            SettingsUi.add(content, SettingsUi.action(this, "Configurar equipos", () -> show(TEAMS)));
            return;
        }
        if (refreshing && matches.isEmpty()) {
            SettingsUi.hint(content, "Buscando partidos…");
            return;
        }
        String errors = MatchRepository.lastErrors(this);
        if (!errors.isEmpty()) SettingsUi.hint(content, "No se pudo actualizar:\n" + errors);
        if (matches.isEmpty()) {
            SettingsUi.hint(content, "No hay partidos en los próximos 60 días.");
            return;
        }
        String lastDay = null;
        for (MatchRepository.Match match : matches) {
            String day = dayLabel(match.startMs, now);
            if (!day.equals(lastDay)) {
                SettingsUi.heading(content, day);
                lastDay = day;
            }
            SettingsUi.add(content, matchRow(match, now));
        }
        long refreshed = MatchRepository.refreshedAt(this);
        if (refreshed > 0) SettingsUi.hint(content, "Actualizado " + timeLabel(refreshed) + ".");
    }

    private View matchRow(MatchRepository.Match match, long now) {
        String app = MatchRepository.appFor(this, match);
        StringBuilder second = new StringBuilder();
        second.append(match.isLive(now) ? "EN DIRECTO" : match.timeTbc ? "Hora por confirmar" : timeLabel(match.startMs));
        second.append(" · ").append(match.competition);
        second.append(" · ").append(app == null ? "elige app" : MatchRepository.appLabel(this, app));
        Button row = SettingsUi.action(this, match.title + "\n" + second, () -> watch(match));
        if (match.isLive(now)) row.setTextColor(LiquidTheme.accent(this));
        row.setOnLongClickListener(v -> { chooseApp(match, false); return true; });
        return row;
    }

    private void watch(MatchRepository.Match match) {
        String app = MatchRepository.appFor(this, match);
        if (app == null) { chooseApp(match, true); return; }
        Intent launch = MatchRepository.launchIntent(this, app);
        if (launch == null) { chooseApp(match, true); return; }
        try {
            startActivity(launch);
        } catch (Exception error) {
            toast("No se pudo abrir " + MatchRepository.appLabel(this, app));
        }
    }

    /** Lets the viewer pick an app for the match's competition (or team when unknown). */
    private void chooseApp(MatchRepository.Match match, boolean launchAfter) {
        final boolean byTeam = MatchRepository.OTHER.equals(match.competition);
        final String target = byTeam ? match.primaryTeam() : match.competition;
        pickApp("¿Con qué app ves " + target + "?", app -> {
            if (byTeam) MatchRepository.setTeamApp(this, target, app);
            else MatchRepository.setCompetitionApp(this, target, app);
            render();
            if (launchAfter && app != null) watch(match);
        });
    }

    // ------------------------------------------------------------------ teams

    private void renderTeams() {
        SettingsUi.hint(content, "Cada equipo usa un calendario público iCal (por ejemplo, el de FotMob: "
                + "página del equipo › Sincronizar calendario). También puedes añadirlos desde el ordenador con ADB.");
        SettingsUi.add(content, SettingsUi.action(this, "Añadir equipo", () -> editTeam(null)));
        for (MatchRepository.Team team : MatchRepository.teams(this)) {
            String state = team.configured() ? hostOf(team.url) : "sin calendario";
            if (!team.appPackage.isEmpty()) state += " · " + MatchRepository.appLabel(this, team.appPackage);
            SettingsUi.add(content, SettingsUi.action(this, team.name + "\n" + state, () -> teamOptions(team)));
        }
    }

    private void teamOptions(MatchRepository.Team team) {
        String[] options = {"Cambiar calendario", "App por defecto del equipo", "Eliminar equipo"};
        dialog().setTitle(team.name).setItems(options, (d, which) -> {
            if (which == 0) editTeam(team);
            else if (which == 1) pickApp("App por defecto para " + team.name, app -> {
                MatchRepository.setTeamApp(this, team.name, app);
                render();
            });
            else {
                MatchRepository.removeTeam(this, team.name);
                render();
            }
        }).setNegativeButton("Cancelar", null).show();
    }

    private void editTeam(MatchRepository.Team team) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = SettingsUi.dp(this, 20);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText name = new EditText(this);
        name.setHint("Nombre (p. ej. Real Madrid)");
        name.setSingleLine(true);
        SettingsUi.styleInput(name, false);
        EditText url = new EditText(this);
        url.setHint("Enlace del calendario (https:// o webcal://)");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        SettingsUi.styleInput(url, false);
        if (team != null) {
            name.setText(team.name);
            name.setEnabled(false);
            url.setText(team.url);
        }
        form.addView(name);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
        gap.topMargin = SettingsUi.dp(this, 8);
        form.addView(url, gap);
        dialog().setTitle(team == null ? "Añadir equipo" : team.name).setView(form)
                .setPositiveButton("Guardar", (d, which) -> {
                    String error = MatchRepository.upsertTeam(this, name.getText().toString(), url.getText().toString());
                    if (error != null) toast(error);
                    else refresh(false);
                    render();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    // ------------------------------------------------------------------ apps

    private void renderApps() {
        SettingsUi.hint(content, "La app que se abre al pulsar un partido de cada competición. "
                + "Los partidos sin competición reconocida usan la app por defecto del equipo.");
        Map<String, String> chosen = MatchRepository.competitionApps(this);
        for (String competition : MatchRepository.knownCompetitions()) {
            if (MatchRepository.OTHER.equals(competition)) continue;
            String app = chosen.get(competition);
            String label = app == null || app.isEmpty() ? "sin elegir" : MatchRepository.appLabel(this, app);
            SettingsUi.add(content, SettingsUi.action(this, competition + "\n" + label,
                    () -> pickApp("¿Con qué app ves " + competition + "?", picked -> {
                        MatchRepository.setCompetitionApp(this, competition, picked);
                        render();
                    })));
        }
    }

    // ------------------------------------------------------------------ helpers

    private interface AppPicked { void picked(String appPackage); }

    private void pickApp(String title, AppPicked done) {
        List<String[]> apps = MatchRepository.launchableApps(this);
        ArrayList<String> labels = new ArrayList<>();
        labels.add("Ninguna (preguntar la próxima vez)");
        for (String[] app : apps) labels.add(app[0]);
        dialog().setTitle(title).setItems(labels.toArray(new String[0]), (d, which) ->
                done.picked(which == 0 ? null : apps.get(which - 1)[1]))
                .setNegativeButton("Cancelar", null).show();
    }

    private void refresh(boolean manual) {
        if (refreshing) return;
        refreshing = true;
        if (manual) toast("Actualizando partidos…");
        if (screen == MATCHES) render();
        final Context app = getApplicationContext();
        new Thread(() -> {
            String error = MatchRepository.refresh(app);
            runOnUiThread(() -> {
                refreshing = false;
                if (isFinishing() || isDestroyed()) return;
                if (manual) toast(error == null ? "Partidos actualizados" : "Actualizado con avisos");
                render();
            });
        }, "sva-matches").start();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this));
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private static String hostOf(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? url : host;
        } catch (Exception ignored) {
            return url;
        }
    }

    static String dayLabel(long startMs, long nowMs) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startMs);
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(nowMs);
        int dayIndex = day.get(Calendar.YEAR) * 400 + day.get(Calendar.DAY_OF_YEAR);
        int todayIndex = today.get(Calendar.YEAR) * 400 + today.get(Calendar.DAY_OF_YEAR);
        if (dayIndex == todayIndex) return "Hoy";
        today.add(Calendar.DAY_OF_YEAR, 1);
        if (dayIndex == today.get(Calendar.YEAR) * 400 + today.get(Calendar.DAY_OF_YEAR)) return "Mañana";
        String label = new SimpleDateFormat("EEEE d 'de' MMMM", SPANISH).format(new Date(startMs));
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    static String timeLabel(long ms) {
        return new SimpleDateFormat("HH:mm", SPANISH).format(new Date(ms));
    }
}
