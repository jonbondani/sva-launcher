package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class GreetingTest {
    @Test public void followsTheTimeOfDay() {
        assertEquals("Buenas noches", Greeting.forHour(5));
        assertEquals("Buenos días", Greeting.forHour(6));
        assertEquals("Buenos días", Greeting.forHour(13));
        assertEquals("Buenas tardes", Greeting.forHour(14));
        assertEquals("Buenas tardes", Greeting.forHour(20));
        assertEquals("Buenas noches", Greeting.forHour(21));
        assertEquals("Buenas noches", Greeting.forHour(0));
    }

    @Test public void addsTheName() {
        assertEquals("Buenos días, jonbondani", Greeting.line(9, "jonbondani"));
        assertEquals("Buenas tardes", Greeting.line(16, "  "));
    }
}
