package com.sva.launcher;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WeatherRepositoryTest {
    @Test public void parsesCurrentTodayAndFollowingDays() throws Exception {
        JSONObject root = new JSONObject("{\"current\":{\"time\":\"2026-09-28T19:15\",\"temperature_2m\":28.4,\"apparent_temperature\":30.1,\"weather_code\":2,\"is_day\":1,\"wind_speed_10m\":12.5},"
                + "\"daily\":{\"time\":[\"2026-09-28\",\"2026-09-29\",\"2026-09-30\",\"2026-10-01\"],"
                + "\"weather_code\":[2,61,3,95],\"temperature_2m_max\":[30,29,31,28],"
                + "\"temperature_2m_min\":[22,21,23,20],\"precipitation_probability_max\":[10,70,20,90]},"
                + "\"hourly\":{\"time\":[\"2026-09-28T18:00\",\"2026-09-28T19:00\",\"2026-09-28T20:00\"],"
                + "\"temperature_2m\":[29,28,27],\"weather_code\":[1,2,61],\"precipitation_probability\":[0,10,60],\"is_day\":[1,1,0]}}");
        WeatherRepository.Forecast forecast = WeatherRepository.parseForecast(root, "Pune", 1234L);
        assertEquals("Pune", forecast.location);
        assertEquals(28.4, forecast.temperature, .001);
        // Today first: the week view shows every day, the home card still uses current values.
        assertEquals(4, forecast.days.size());
        assertEquals("2026-09-28", forecast.days.get(0).date);
        assertEquals("2026-09-29", forecast.days.get(1).date);
        assertEquals(70, forecast.days.get(1).precipitation);
        // Hours before the current one are dropped.
        assertEquals(2, forecast.hours.size());
        assertEquals("2026-09-28T19:00", forecast.hours.get(0).time);
        assertFalse(forecast.hours.get(1).day);
    }

    @Test public void cacheRoundTrips() throws Exception {
        java.util.ArrayList<WeatherRepository.Day> days = new java.util.ArrayList<>();
        days.add(new WeatherRepository.Day("2026-09-29", 61, 20, 30, 75));
        WeatherRepository.Forecast original = new WeatherRepository.Forecast("Mumbai", 99, 29, 33, 8, 61, true, days);
        WeatherRepository.Forecast restored = WeatherRepository.Forecast.fromJson(original.toJson());
        assertEquals(original.location, restored.location);
        assertEquals(original.temperature, restored.temperature, .001);
        assertEquals(75, restored.days.get(0).precipitation);
        assertEquals(0, restored.hours.size());
        original.hours.add(new WeatherRepository.Hour("2026-09-29T10:00", 3, 21.5, 40, true));
        restored = WeatherRepository.Forecast.fromJson(original.toJson());
        assertEquals(1, restored.hours.size());
        assertEquals(40, restored.hours.get(0).precipitation);
    }

    @Test public void mapsConditionsAndConversions() {
        assertEquals("Clear", WeatherRepository.condition(0));
        assertEquals("Rain", WeatherRepository.condition(82));
        assertEquals("Thunderstorm", WeatherRepository.condition(95));
        assertEquals(32, WeatherRepository.fahrenheit(0), .001);
        assertEquals(62.1371, WeatherRepository.milesPerHour(100), .001);
        assertTrue(WeatherRepository.validCoordinates(18.52, 73.85));
        assertFalse(WeatherRepository.validCoordinates(100, 73.85));
    }
}
