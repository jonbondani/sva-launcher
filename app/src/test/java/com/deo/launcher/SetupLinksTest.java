package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SetupLinksTest {
    @Test public void onlyWebLinks() {
        assertTrue(SetupLinks.isWebLink("https://iptv-org.github.io/iptv/countries/es.m3u"));
        assertTrue(SetupLinks.isWebLink("http://192.168.1.5:8096/lista.m3u?token=abc"));
        assertFalse(SetupLinks.isWebLink("file:///sdcard/lista.m3u"));
        assertFalse(SetupLinks.isWebLink("lista.m3u"));
        assertFalse(SetupLinks.isWebLink("https://exa mple.com/x"));
    }

    @Test public void showsOnlyTheHostNeverTheToken() {
        assertEquals("iptv-org.github.io", SetupLinks.host("https://IPTV-ORG.github.io/iptv/es.m3u?token=secret"));
        assertEquals("192.168.1.5", SetupLinks.host("http://192.168.1.5:8096/lista.m3u"));
        assertEquals("?", SetupLinks.host("no es un enlace"));
    }
}
