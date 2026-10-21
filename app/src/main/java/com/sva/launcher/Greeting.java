package com.sva.launcher;

/** "Buenos días / Buenas tardes / Buenas noches, <name>" following Spanish customs for the time of day. */
final class Greeting {
    static final String DEFAULT_NAME = "jonbondani";

    private Greeting() { }

    /** 06:00-13:59 días, 14:00-20:59 tardes, 21:00-05:59 noches (meals mark the change in Spain). */
    static String forHour(int hour) {
        if (hour >= 6 && hour < 14) return "Buenos días";
        if (hour >= 14 && hour < 21) return "Buenas tardes";
        return "Buenas noches";
    }

    static String line(int hour, String name) {
        String who = name == null ? "" : name.trim();
        return who.isEmpty() ? forHour(hour) : forHour(hour) + ", " + who;
    }
}
