package com.freeyourtv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Compact Live-Weather Manager using Open-Meteo public API.
 * Provides caching, background async retrieval, throttling, and fail-safe fallbacks.
 */
public class WeatherManager {

    public static final String PREFS_NAME = "tvhome_prefs";
    public static final String KEY_WEATHER_ENABLED = "weather_enabled";
    public static final String KEY_WEATHER_UNIT_F = "weather_unit_f";
    public static final String KEY_WEATHER_CITY = "weather_city";
    public static final String KEY_WEATHER_LAT = "weather_lat";
    public static final String KEY_WEATHER_LON = "weather_lon";
    public static final String KEY_CACHED_TEMP = "weather_cached_temp";
    public static final String KEY_CACHED_CODE = "weather_cached_code";
    public static final String KEY_CACHED_TIME = "weather_cached_time";

    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000L; // 30 minutes
    private static final int TIMEOUT_MS = 5000;

    public static final class City {
        public final String name;
        public final double lat;
        public final double lon;

        public City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final List<City> PRESET_CITIES = Collections.unmodifiableList(Arrays.asList(
            new City("Delhi", 28.6139, 77.2090),
            new City("Mumbai", 19.0760, 72.8777),
            new City("Bengaluru", 12.9716, 77.5946),
            new City("Kolkata", 22.5726, 88.3639),
            new City("Chennai", 13.0827, 80.2707),
            new City("Hyderabad", 17.3850, 78.4867),
            new City("Ahmedabad", 23.0225, 72.5714),
            new City("Pune", 18.5204, 73.8567),
            new City("Jaipur", 26.9124, 75.7873),
            new City("Lucknow", 26.8467, 80.9462),
            new City("Chandigarh", 30.7333, 76.7794),
            new City("Kochi", 9.9312, 76.2673),
            new City("Bhopal", 23.2599, 77.4126),
            new City("Patna", 25.5941, 85.1376),
            new City("Indore", 22.7196, 75.8577),
            new City("Varanasi", 25.3176, 82.9739),
            new City("Guwahati", 26.1445, 91.7362),
            new City("Srinagar", 34.0837, 74.7973),
            new City("Goa", 15.4909, 73.8278),
            new City("Amritsar", 31.6340, 74.8723),
            new City("Bhubaneswar", 20.2961, 85.8245),
            new City("Visakhapatnam", 17.6868, 83.2185)
    ));

    public static final class WeatherData {
        public final double temperatureC;
        public final int weatherCode;
        public final String cityName;
        public final long timestampMillis;

        public WeatherData(double temperatureC, int weatherCode, String cityName, long timestampMillis) {
            this.temperatureC = temperatureC;
            this.weatherCode = weatherCode;
            this.cityName = cityName;
            this.timestampMillis = timestampMillis;
        }
    }

    public interface WeatherCallback {
        void onWeatherUpdated(WeatherData data);
        void onWeatherError(Exception error);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile WeatherData cachedData = null;

    public WeatherManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCache();
    }

    private void loadCache() {
        if (prefs.contains(KEY_CACHED_TEMP) && prefs.contains(KEY_CACHED_CODE)) {
            double temp = prefs.getFloat(KEY_CACHED_TEMP, 0f);
            int code = prefs.getInt(KEY_CACHED_CODE, 0);
            long time = prefs.getLong(KEY_CACHED_TIME, 0L);
            String city = getCityName();
            cachedData = new WeatherData(temp, code, city, time);
        }
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_WEATHER_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply();
    }

    public boolean isFahrenheit() {
        return prefs.getBoolean(KEY_WEATHER_UNIT_F, false);
    }

    public void setFahrenheit(boolean fahrenheit) {
        prefs.edit().putBoolean(KEY_WEATHER_UNIT_F, fahrenheit).apply();
    }

    public String getCityName() {
        return prefs.getString(KEY_WEATHER_CITY, "Delhi");
    }

    public double getLatitude() {
        return prefs.getFloat(KEY_WEATHER_LAT, 28.6139f);
    }

    public double getLongitude() {
        return prefs.getFloat(KEY_WEATHER_LON, 77.2090f);
    }

    public void setCity(City city) {
        if (city == null) return;
        prefs.edit()
                .putString(KEY_WEATHER_CITY, city.name)
                .putFloat(KEY_WEATHER_LAT, (float) city.lat)
                .putFloat(KEY_WEATHER_LON, (float) city.lon)
                .apply();
        fetchWeather(true, null);
    }

    public WeatherData getCachedData() {
        return cachedData;
    }

    public boolean hasCachedWeather() {
        return cachedData != null;
    }

    public City getSelectedCity() {
        String name = getCityName();
        double lat = getLatitude();
        double lon = getLongitude();
        return new City(name, lat, lon);
    }

    public String getWeatherDisplay() {
        if (!isEnabled()) return null;
        if (cachedData == null) return null;
        return formatWeather(cachedData.temperatureC, cachedData.weatherCode, cachedData.cityName, isFahrenheit());
    }

    public String getLastUpdatedDisplay() {
        if (cachedData == null || cachedData.timestampMillis <= 0) return "Never";
        long elapsedMinutes = (System.currentTimeMillis() - cachedData.timestampMillis) / 60000L;
        if (elapsedMinutes < 1) return "Just now";
        if (elapsedMinutes < 60) return elapsedMinutes + "m ago";
        long elapsedHours = elapsedMinutes / 60L;
        return elapsedHours + "h ago";
    }

    public static int toFahrenheit(double celsius) {
        return (int) Math.round(celsius * 9.0 / 5.0 + 32.0);
    }

    public static String getWeatherSymbol(int code) {
        switch (code) {
            case 0: return "â˜€"; // Clear sky
            case 1:
            case 2: return "â›…"; // Partly cloudy
            case 3: return "â˜"; // Overcast
            case 45:
            case 48: return "ðŸŒ«"; // Fog
            case 51: case 53: case 55:
            case 56: case 57:
            case 80: case 81: case 82: return "ðŸŒ¦"; // Showers / Drizzle
            case 61: case 63: case 65:
            case 66: case 67: return "ðŸŒ§"; // Rain
            case 71: case 73: case 75:
            case 77: case 85: case 86: return "â„"; // Snow
            case 95: case 96: case 99: return "â›ˆ"; // Thunderstorm
            default: return "â˜€";
        }
    }

    public static String getWeatherCondition(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1:
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51: case 53: case 55:
            case 56: case 57: return "Drizzle";
            case 61: case 63: case 65:
            case 66: case 67:
            case 80: case 81: case 82: return "Rain";
            case 71: case 73: case 75:
            case 77: case 85: case 86: return "Snow";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Clear";
        }
    }

    public static String formatWeather(double tempC, int code, String city, boolean useFahrenheit) {
        String symbol = getWeatherSymbol(code);
        int tempVal = useFahrenheit ? toFahrenheit(tempC) : (int) Math.round(tempC);
        String unit = useFahrenheit ? "Â°F" : "Â°C";
        return symbol + " " + tempVal + unit + " Â· " + city;
    }

    public static WeatherData parseJson(String jsonStr, String city) throws JSONException {
        JSONObject root = new JSONObject(jsonStr);
        JSONObject current = root.getJSONObject("current");
        double temp = current.getDouble("temperature_2m");
        int code = current.getInt("weather_code");
        return new WeatherData(temp, code, city, System.currentTimeMillis());
    }

    public void fetchWeather(boolean force, WeatherCallback callback) {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        if (!force && cachedData != null && (now - cachedData.timestampMillis) < REFRESH_INTERVAL_MS) {
            if (callback != null) callback.onWeatherUpdated(cachedData);
            return;
        }

        final double lat = getLatitude();
        final double lon = getLongitude();
        final String city = getCityName();

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat +
                        "&longitude=" + lon +
                        "&current=temperature_2m,weather_code&timezone=auto";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "FreeYourTV-Launcher/2.0");

                int respCode = conn.getResponseCode();
                if (respCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();

                    WeatherData data = parseJson(sb.toString(), city);
                    cachedData = data;

                    prefs.edit()
                            .putFloat(KEY_CACHED_TEMP, (float) data.temperatureC)
                            .putInt(KEY_CACHED_CODE, data.weatherCode)
                            .putLong(KEY_CACHED_TIME, data.timestampMillis)
                            .apply();

                    mainHandler.post(() -> {
                        if (callback != null) callback.onWeatherUpdated(data);
                    });
                } else {
                    throw new Exception("HTTP error code: " + respCode);
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onWeatherError(e);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}
