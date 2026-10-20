package com.sva.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Safety net for the Home override: every crash of Sva is written down, and after the third one within five minutes
 * the override stops reopening Sva and switches itself off, so the TV's own home screen stays usable. Turning the
 * override on again (firetv-optimizer.sh sva-inicio) starts the count over. Idea adapted from Free My TV (MIT).
 */
final class CrashWatch {
    static final long WINDOW_MS = 5 * 60_000L;
    static final int LIMIT = 3;
    private static boolean installed;

    private CrashWatch() { }

    static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { record(app, System.currentTimeMillis()); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** True when Sva crashed too often just now: the override must not keep reopening it. */
    static boolean tooManyCrashes(Context context) {
        return tripped(read(context), System.currentTimeMillis());
    }

    static void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        file(context).delete();
    }

    /** Pure rule, kept separate so it can be unit tested. */
    static boolean tripped(List<Long> crashes, long now) {
        int recent = 0;
        for (long at : crashes) if (now - at >= 0 && now - at <= WINDOW_MS) recent++;
        return recent >= LIMIT;
    }

    private static void record(Context context, long now) throws Exception {
        List<Long> crashes = read(context);
        crashes.add(now);
        StringBuilder out = new StringBuilder();
        for (long at : crashes) if (now - at <= WINDOW_MS) out.append(at).append('\n');
        try (FileOutputStream stream = new FileOutputStream(file(context))) {
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<Long> read(Context context) {
        List<Long> crashes = new ArrayList<>();
        File f = file(context);
        if (!f.exists()) return crashes;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) Math.min(f.length(), 4096)];
            int n = in.read(data);
            if (n <= 0) return crashes;
            for (String line : new String(data, 0, n, StandardCharsets.UTF_8).split("\n")) {
                try { crashes.add(Long.parseLong(line.trim())); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        return crashes;
    }

    private static File file(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "home-crashes.txt");
    }
}
