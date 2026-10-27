package com.freeyourtv.launcher;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Compact Live-Weather: temperature conversion, WMO symbols,
 * format strings, Open-Meteo JSON parsing, and fail-safe error handling.
 */
public class WeatherTest {

    @Test
    public void testTemperatureConversionToFahrenheit() {
        assertEquals(32, WeatherManager.toFahrenheit(0.0));
        assertEquals(212, WeatherManager.toFahrenheit(100.0));
        assertEquals(82, WeatherManager.toFahrenheit(28.0));
        assertEquals(77, WeatherManager.toFahrenheit(25.0));
        assertEquals(14, WeatherManager.toFahrenheit(-10.0));
    }

    @Test
    public void testWeatherSymbolMapping() {
        // Clear sky
        assertEquals("â˜€", WeatherManager.getWeatherSymbol(0));

        // Cloudy / Overcast
        assertEquals("â›…", WeatherManager.getWeatherSymbol(1));
        assertEquals("â›…", WeatherManager.getWeatherSymbol(2));
        assertEquals("â˜", WeatherManager.getWeatherSymbol(3));

        // Fog
        assertEquals("ðŸŒ«", WeatherManager.getWeatherSymbol(45));
        assertEquals("ðŸŒ«", WeatherManager.getWeatherSymbol(48));

        // Drizzle & Showers
        assertEquals("ðŸŒ¦", WeatherManager.getWeatherSymbol(51));
        assertEquals("ðŸŒ¦", WeatherManager.getWeatherSymbol(80));

        // Rain
        assertEquals("ðŸŒ§", WeatherManager.getWeatherSymbol(61));
        assertEquals("ðŸŒ§", WeatherManager.getWeatherSymbol(63));
        assertEquals("ðŸŒ§", WeatherManager.getWeatherSymbol(65));

        // Snow
        assertEquals("â„", WeatherManager.getWeatherSymbol(71));
        assertEquals("â„", WeatherManager.getWeatherSymbol(75));

        // Thunderstorm
        assertEquals("â›ˆ", WeatherManager.getWeatherSymbol(95));
        assertEquals("â›ˆ", WeatherManager.getWeatherSymbol(99));

        // Unknown code fallback
        assertEquals("â˜€", WeatherManager.getWeatherSymbol(999));
    }

    @Test
    public void testWeatherConditionDescriptions() {
        assertEquals("Clear", WeatherManager.getWeatherCondition(0));
        assertEquals("Partly Cloudy", WeatherManager.getWeatherCondition(1));
        assertEquals("Overcast", WeatherManager.getWeatherCondition(3));
        assertEquals("Fog", WeatherManager.getWeatherCondition(45));
        assertEquals("Rain", WeatherManager.getWeatherCondition(61));
        assertEquals("Thunderstorm", WeatherManager.getWeatherCondition(95));
    }

    @Test
    public void testFormatWeatherDisplay() {
        // Celsius
        String delhiC = WeatherManager.formatWeather(28.0, 0, "Delhi", false);
        assertEquals("â˜€ 28Â°C Â· Delhi", delhiC);

        // Fahrenheit
        String delhiF = WeatherManager.formatWeather(28.0, 0, "Delhi", true);
        assertEquals("â˜€ 82Â°F Â· Delhi", delhiF);

        // Rain in Mumbai
        String mumbaiRain = WeatherManager.formatWeather(24.4, 61, "Mumbai", false);
        assertEquals("ðŸŒ§ 24Â°C Â· Mumbai", mumbaiRain);

        // Thunderstorm in Bengaluru
        String blrStorm = WeatherManager.formatWeather(21.8, 95, "Bengaluru", false);
        assertEquals("â›ˆ 22Â°C Â· Bengaluru", blrStorm);
    }

    @Test
    public void testOpenMeteoJsonParsing() throws Exception {
        String sampleJson = "{\n" +
                "  \"latitude\": 28.625,\n" +
                "  \"longitude\": 77.25,\n" +
                "  \"current\": {\n" +
                "    \"time\": \"2026-10-20T21:00\",\n" +
                "    \"temperature_2m\": 26.4,\n" +
                "    \"weather_code\": 0\n" +
                "  }\n" +
                "}";

        WeatherManager.WeatherData data = WeatherManager.parseJson(sampleJson, "Delhi");
        assertNotNull(data);
        assertEquals("Delhi", data.cityName);
        assertEquals(26.4, data.temperatureC, 0.01);
        assertEquals(0, data.weatherCode);
        assertTrue(data.timestampMillis > 0);
    }

    @Test(expected = JSONException.class)
    public void testParseJsonMissingCurrentThrowsException() throws Exception {
        String invalidJson = "{\"latitude\": 28.625, \"longitude\": 77.25}";
        WeatherManager.parseJson(invalidJson, "Delhi");
    }

    @Test(expected = JSONException.class)
    public void testParseJsonMalformedThrowsException() throws Exception {
        String malformedJson = "{not-valid-json}";
        WeatherManager.parseJson(malformedJson, "Delhi");
    }
}
