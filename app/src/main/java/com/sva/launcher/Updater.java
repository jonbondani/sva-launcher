package com.sva.launcher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Self-update for the Fire TV build: the "firetv-latest" GitHub pre-release publishes the APK
 * and a version.json; when its versionCode is newer than the installed one, the APK is streamed
 * into a PackageInstaller session and Android asks the viewer to confirm the update.
 *
 * Updating in place only works when every build is signed with the same key (repository secret
 * FIRETV_DEBUG_KEYSTORE_BASE64); otherwise Android refuses and Sva must be reinstalled once.
 */
final class Updater {
    static final String RELEASE = "https://github.com/jonbondani/sva-launcher/releases/download/firetv-latest/";
    private static final String ACTION_STATUS = "com.sva.launcher.UPDATE_STATUS";
    private static final int MAX_APK_BYTES = 120 * 1024 * 1024;

    private Updater() { }

    static final class Release {
        final int versionCode;
        final String versionName;
        Release(int versionCode, String versionName) { this.versionCode = versionCode; this.versionName = versionName; }
    }

    interface Progress { void update(String message); }

    static int installedCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) info.getLongVersionCode() : legacyCode(info);
        } catch (Exception ignored) {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private static int legacyCode(PackageInfo info) { return info.versionCode; }

    static String installedName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "?";
        }
    }

    /** Android 8+: Sva needs the per-app "install unknown apps" permission to update itself. */
    static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermission(Context context) {
        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception notOnThisDevice) {
            // Fire OS: Settings › My Fire TV › Developer options › Install unknown apps.
            try {
                context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    /** Blocking: reads version.json from the release. */
    static Release latest() throws Exception {
        String body = new String(download(RELEASE + "version.json", 64 * 1024, null), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(body);
        return new Release(json.getInt("versionCode"), json.optString("versionName", String.valueOf(json.getInt("versionCode"))));
    }

    /** Blocking: downloads the APK and hands it to the system installer (which asks to confirm). */
    static void install(Context context, Progress progress) throws Exception {
        Context app = context.getApplicationContext();
        PackageInstaller installer = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (OutputStream out = session.openWrite("sva-firetv.apk", 0, -1)) {
                downloadTo(RELEASE + "sva-firetv.apk", out, progress);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            // The installer fills in status extras, so the PendingIntent must stay mutable on 12+.
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            Intent status = new Intent(app, StatusReceiver.class).setAction(ACTION_STATUS);
            PendingIntent sender = PendingIntent.getBroadcast(app, sessionId, status, flags);
            progress.update("Instalando…");
            session.commit(sender.getIntentSender());
        } catch (Exception error) {
            try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            throw error;
        }
    }

    private static void downloadTo(String url, OutputStream out, Progress progress) throws Exception {
        HttpURLConnection connection = open(url);
        try {
            long total = connection.getContentLengthLong();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int lastPercent = -1, count;
                while ((count = input.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    done += count;
                    if (done > MAX_APK_BYTES) throw new IllegalStateException("La descarga es demasiado grande.");
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        if (percent != lastPercent && percent % 5 == 0) {
                            lastPercent = percent;
                            progress.update("Descargando… " + percent + " %");
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] download(String url, int limit, Progress progress) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0, count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IllegalStateException("Respuesta demasiado grande.");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    /** GitHub release downloads redirect to another https host; HttpURLConnection follows that. */
    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SvaLauncher-Updater");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("GitHub respondió HTTP " + code);
        }
        return connection;
    }

    /** Receives the installer's result; asks the viewer to confirm when Android requires it. */
    public static final class StatusReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { context.startActivity(confirm); } catch (Exception ignored) { }
                }
                return;
            }
            if (status == PackageInstaller.STATUS_SUCCESS) return;   // Sva restarts as the new version.
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String text = status == PackageInstaller.STATUS_FAILURE_CONFLICT
                    || (message != null && message.contains("signatures"))
                    ? "No se pudo actualizar: esta versión está firmada con otra clave. Desinstala Sva e instálala de nuevo una vez."
                    : "No se pudo actualizar" + (message == null ? "." : ": " + message);
            Tr.toast(context, text, android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
