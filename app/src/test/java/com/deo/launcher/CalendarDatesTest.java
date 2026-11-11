package com.sva.launcher;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CalendarDatesTest {
    @Test public void includesEventsOverlappingLocalDayAndUsesExclusiveEnd() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate day = LocalDate.of(2026, 9, 28);
        long beforeMidnight = day.minusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli();
        long afterMidnight = day.atTime(0, 30).atZone(zone).toInstant().toEpochMilli();
        PublicCalendarRepository.Event crossing = new PublicCalendarRepository.Event(
                "Late show", beforeMidnight, afterMidnight);
        assertTrue(CalendarDates.includes(crossing, day, zone));
        assertTrue(CalendarDates.includes(crossing, day.minusDays(1), zone));

        long tomorrow = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        PublicCalendarRepository.Event allDay = new PublicCalendarRepository.Event(
                "Festival", day.atStartOfDay(zone).toInstant().toEpochMilli(), tomorrow, true);
        assertTrue(CalendarDates.includes(allDay, day, zone));
        assertFalse(CalendarDates.includes(allDay, day.plusDays(1), zone));
    }

    @Test public void monthNavigationStopsAtTwelveMonthsEitherSide() {
        YearMonth current = YearMonth.of(2026, 9);
        assertTrue(CalendarDates.inRange(current.minusMonths(12), current));
        assertTrue(CalendarDates.inRange(current.plusMonths(12), current));
        assertFalse(CalendarDates.inRange(current.minusMonths(13), current));
        assertFalse(CalendarDates.inRange(current.plusMonths(13), current));
    }

    @Test public void homeSkipsMeetingsThatAreOver() {
        long h = 3_600_000L, day = 1_794_000_000_000L;
        PublicCalendarRepository.Event morning = new PublicCalendarRepository.Event("Reunión 11:00", day + 11 * h, day + 12 * h);
        PublicCalendarRepository.Event evening = new PublicCalendarRepository.Event("Cena", day + 21 * h, day + 23 * h);
        PublicCalendarRepository.Event tomorrow = new PublicCalendarRepository.Event("Médico", day + 33 * h, day + 34 * h);
        java.util.List<PublicCalendarRepository.Event> list = java.util.Arrays.asList(morning, evening, tomorrow);
        org.junit.Assert.assertSame(morning, CalendarDates.forHome(list, day + 10 * h));      // before it
        org.junit.Assert.assertSame(morning, CalendarDates.forHome(list, day + 11 * h + 30 * 60_000L)); // during it
        org.junit.Assert.assertSame(evening, CalendarDates.forHome(list, day + 18 * h));      // 18:00: the 11:00 is over
        org.junit.Assert.assertSame(tomorrow, CalendarDates.forHome(list, day + 23 * h + 1)); // all of today is over
        org.junit.Assert.assertNull(CalendarDates.forHome(list, day + 40 * h));
    }

    @Test public void eventWithoutEndCountsAsOneHour() {
        PublicCalendarRepository.Event noEnd = new PublicCalendarRepository.Event("Llamada", 1000L, 0L);
        java.util.List<PublicCalendarRepository.Event> list = java.util.Collections.singletonList(noEnd);
        org.junit.Assert.assertSame(noEnd, CalendarDates.forHome(list, 1000L + 30 * 60_000L));
        org.junit.Assert.assertNull(CalendarDates.forHome(list, 1000L + 3_600_000L));
    }
}
