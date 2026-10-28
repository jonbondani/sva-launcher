package com.sva.launcher;

import java.net.URI;
import java.util.Locale;

/** Checks for links handed over by ADB (playlist, guide). Only the host is ever shown on screen. */
final class SetupLinks {
    private SetupLinks() { }

    static boolean isWebLink(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return scheme != null && uri.getHost() != null
                    && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** "iptv-org.github.io" for the confirmation: never the path, which may carry a token. */
    static String host(String value) {
        try {
            String host = URI.create(value.trim()).getHost();
            return host == null ? "?" : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
