package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class CalendarFeedErrorTest {
    @Test public void privateCalendarThroughThePublicAddress() {
        assertTrue(PublicCalendarRepository.feedError(404,
                "https://calendar.google.com/calendar/ical/x%40gmail.com/public/basic.ics").contains("no es público"));
    }

    @Test public void secretAddressNotFound() {
        assertTrue(PublicCalendarRepository.feedError(404,
                "https://calendar.google.com/calendar/ical/x%40gmail.com/private-abc/basic.ics").contains("no encuentra"));
    }

    @Test public void otherErrorsKeepTheCode() {
        assertTrue(PublicCalendarRepository.feedError(500, "https://calendar.google.com/calendar/ical/x/public/basic.ics")
                .contains("HTTP 500"));
    }
}
