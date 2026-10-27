package com.freeyourtv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.DisplayMetrics;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages UI themes, coordinated wallpapers, dock styles, and persistent preferences.
 * Settings persist in SharedPreferences ("tvhome_prefs") so user choices survive reboots.
 */
public final class ThemeManager {

    public static final String PREFS_NAME = "tvhome_prefs";
    public static final String KEY_THEME = "current_theme";
    public static final String KEY_DOCK_SIZE = "dock_size";
    public static final String KEY_REDUCED_MOTION = "reduced_motion";

    public static final String THEME_DARK = "dark";
    public static final String THEME_AURORA = "aurora";
    public static final String THEME_DIWALI = "diwali";
    public static final String THEME_DUSSEHRA = "dussehra";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_CUSTOM = "custom";

    // 22 Dedicated Pan-India Festival Theme IDs
    public static final String THEME_MAKAR_SANKRANTI = "makar_sankranti";
    public static final String THEME_MAHA_SHIVRATRI = "maha_shivratri";
    public static final String THEME_HOLI = "holi";
    public static final String THEME_UGADI = "ugadi";
    public static final String THEME_RAM_NAVAMI = "ram_navami";
    public static final String THEME_MAHAVIR_JAYANTI = "mahavir_jayanti";
    public static final String THEME_BAISAKHI = "baisakhi";
    public static final String THEME_BUDDHA_PURNIMA = "buddha_purnima";
    public static final String THEME_RATH_YATRA = "rath_yatra";
    public static final String THEME_ONAM = "onam";
    public static final String THEME_RAKSHA_BANDHAN = "raksha_bandhan";
    public static final String THEME_KRISHNA_JANMAASHTAMI = "krishna_janmashtami";
    public static final String THEME_GANESH_CHATURTHI = "ganesh_chaturthi";
    public static final String THEME_NAVRATRI = "navratri";
    public static final String THEME_KARWA_CHAUTH = "karwa_chauth";
    public static final String THEME_GOVARDHAN_PUJA = "govardhan_puja";
    public static final String THEME_BHAI_DOOJ = "bhai_dooj";
    public static final String THEME_CHHATH_PUJA = "chhath_puja";
    public static final String THEME_GURU_NANAK_JAYANTI = "guru_nanak_jayanti";
    public static final String THEME_CHRISTMAS = "christmas";
    public static final String THEME_REPUBLIC_DAY = "republic_day";
    public static final String THEME_INDEPENDENCE_DAY = "independence_day";

    public static final class ThemeInfo {
        public final String id;
        public final int nameRes;
        public final int backgroundColor;
        public final int ambientDefaultColor;
        public final int dockBandColor;
        public final int dockEdgeColor;
        public final int primaryTextColor;
        public final int secondaryTextColor;
        public final int pillTextColor;
        public final boolean isLight;
        public final int wallpaperRes;
        public final Bitmap wallpaperBitmap;
        public final int glassRimColor;
        public final int glassGlowColor;
        public final int glassSpecularColor;
        public final int glassFillColor;

        public ThemeInfo(String id, int nameRes, int backgroundColor, int ambientDefaultColor,
                         int dockBandColor, int dockEdgeColor, int primaryTextColor, int secondaryTextColor,
                         int pillTextColor, boolean isLight, int wallpaperRes, Bitmap wallpaperBitmap,
                         int glassRimColor, int glassGlowColor, int glassSpecularColor, int glassFillColor) {
            this.id = id;
            this.nameRes = nameRes;
            this.backgroundColor = backgroundColor;
            this.ambientDefaultColor = ambientDefaultColor;
            this.dockBandColor = dockBandColor;
            this.dockEdgeColor = dockEdgeColor;
            this.primaryTextColor = primaryTextColor;
            this.secondaryTextColor = secondaryTextColor;
            this.pillTextColor = pillTextColor;
            this.isLight = isLight;
            this.wallpaperRes = wallpaperRes;
            this.wallpaperBitmap = wallpaperBitmap;
            this.glassRimColor = glassRimColor;
            this.glassGlowColor = glassGlowColor;
            this.glassSpecularColor = glassSpecularColor;
            this.glassFillColor = glassFillColor;
        }
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Map<String, ThemeInfo> themes = new LinkedHashMap<>();
    private final FestivalThemeEngine festivalEngine;

    public ThemeManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        registerBuiltInThemes();

        FestivalThemeEngine.Store store = new FestivalThemeEngine.Store() {
            @Override
            public boolean getBoolean(String key, boolean defValue) {
                return prefs.getBoolean(key, defValue);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                prefs.edit().putBoolean(key, value).apply();
            }

            @Override
            public String getString(String key, String defValue) {
                return prefs.getString(key, defValue);
            }

            @Override
            public void putString(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }
        };
        this.festivalEngine = new FestivalThemeEngine(store, DateProvider.KOLKATA);
    }

    private void registerBuiltInThemes() {
        // 1. OLED Black (Minimalist Apple TV pure black, solid color, no wallpaper)
        themes.put(THEME_DARK, new ThemeInfo(
                THEME_DARK,
                R.string.theme_dark,
                0xFF000000,
                0xFF2B5CFF,
                0xFF141416,
                0x1FFFFFFF,
                0xFFFFFFFF,
                0x8CFFFFFF,
                0xFFFFFFFF,
                false,
                0,
                null,
                0xCCFFFFFF,
                0x22FFFFFF,
                0x66FFFFFF,
                0x3DFFFFFF
        ));

        // 2. Aurora (Upstream CleanYourTV cosmic aurora artwork, celestial cyan accents)
        themes.put(THEME_AURORA, new ThemeInfo(
                THEME_AURORA,
                R.string.theme_aurora,
                0xFF070A14,
                0xFF00B4D8,
                0xFF0C1322,
                0x3300E5FF,
                0xFFF0F6FC,
                0x8CA0B8D0,
                0xFFF0F6FC,
                false,
                R.drawable.wallpaper_aurora,
                null,
                0xCC00E5FF,
                0x3300E5FF,
                0x5500E5FF,
                0x2900E5FF
        ));

        // 3. Diwali Festival (Deep midnight-indigo night sky, glowing Diyas, gold & amber radiance)
        themes.put(THEME_DIWALI, new ThemeInfo(
                THEME_DIWALI,
                R.string.theme_diwali,
                0xFF0B0616,
                0xFFFFB300,
                0xFF150A26,
                0x66FFD700,
                0xFFFFF8E7,
                0xB3FFD700,
                0xFFFFF8E7,
                false,
                R.drawable.wallpaper_diwali,
                null,
                0xE6FFD700,
                0x40FFB300,
                0x80FFD700,
                0x26FFC107
        ));

        // 4. Dussehra Victory (Terracotta/crimson dusk, festive floral Toran & marigolds, saffron accents)
        themes.put(THEME_DUSSEHRA, new ThemeInfo(
                THEME_DUSSEHRA,
                R.string.theme_dussehra,
                0xFF140602,
                0xFFFF6D00,
                0xFF240A04,
                0x66FFA000,
                0xFFFFF3E0,
                0xB3FFA726,
                0xFFFFF3E0,
                false,
                R.drawable.wallpaper_dussehra,
                null,
                0xE6FFA726,
                0x40FF6D00,
                0x80FFB74D,
                0x26FF6D00
        ));

        // 5. Light Silver (Studio aluminum artwork, crisp dark charcoal typography)
        themes.put(THEME_LIGHT, new ThemeInfo(
                THEME_LIGHT,
                R.string.theme_light,
                0xFFEFF2F6,
                0xFF78909C,
                0xFFD8DDE6,
                0x33000000,
                0xFF111418,
                0x99111418,
                0xFF111418,
                true,
                R.drawable.wallpaper_light,
                null,
                0x4D000000,
                0x14000000,
                0x33000000,
                0x14000000
        ));

        // 6. Custom Theme (can load config.json and wallpaper.jpg overlay from app storage)
        ThemeInfo customTheme = loadCustomTheme();
        themes.put(THEME_CUSTOM, customTheme);

        // -----------------------------------------------------------------------------------------
        // 22 DEDICATED PAN-INDIA FESTIVAL THEMES
        // -----------------------------------------------------------------------------------------

        // 1. Makar Sankranti / Pongal
        themes.put(THEME_MAKAR_SANKRANTI, new ThemeInfo(
                THEME_MAKAR_SANKRANTI,
                R.string.theme_makar_sankranti,
                0xFF1A0E07,
                0xFFFF8F00,
                0xFF261208,
                0x66FFA000,
                0xFFFFF8E1,
                0xB3FFB74D,
                0xFFFFF8E1,
                false,
                R.drawable.wallpaper_makar_sankranti,
                null,
                0xE6FFB74D,
                0x40FF8F00,
                0x80FFE082,
                0x26FF8F00
        ));

        // 2. Maha Shivratri
        themes.put(THEME_MAHA_SHIVRATRI, new ThemeInfo(
                THEME_MAHA_SHIVRATRI,
                R.string.theme_maha_shivratri,
                0xFF080C16,
                0xFF5C6BC0,
                0xFF101626,
                0x667986CB,
                0xFFF0F4FF,
                0xB39FA8DA,
                0xFFF0F4FF,
                false,
                R.drawable.wallpaper_maha_shivratri,
                null,
                0xE69FA8DA,
                0x405C6BC0,
                0x80C5CAE9,
                0x263F51B5
        ));

        // 3. Holi
        themes.put(THEME_HOLI, new ThemeInfo(
                THEME_HOLI,
                R.string.theme_holi,
                0xFF140818,
                0xFFE91E63,
                0xFF220C28,
                0x66FF4081,
                0xFFFFF0F5,
                0xB3FF80AB,
                0xFFFFF0F5,
                false,
                R.drawable.wallpaper_holi,
                null,
                0xE6FF4081,
                0x40E91E63,
                0x80FF80AB,
                0x26E91E63
        ));

        // 4. Ugadi / Gudi Padwa
        themes.put(THEME_UGADI, new ThemeInfo(
                THEME_UGADI,
                R.string.theme_ugadi,
                0xFF0A160C,
                0xFF43A047,
                0xFF122415,
                0x6666BB6A,
                0xFFF1F8E9,
                0xB3A5D6A7,
                0xFFF1F8E9,
                false,
                R.drawable.wallpaper_ugadi,
                null,
                0xE681C784,
                0x4043A047,
                0x80C8E6C9,
                0x262E7D32
        ));

        // 5. Ram Navami
        themes.put(THEME_RAM_NAVAMI, new ThemeInfo(
                THEME_RAM_NAVAMI,
                R.string.theme_ram_navami,
                0xFF180A04,
                0xFFFF6F00,
                0xFF281206,
                0x66FFA000,
                0xFFFFF8E1,
                0xB3FFB74D,
                0xFFFFF8E1,
                false,
                R.drawable.wallpaper_ram_navami,
                null,
                0xE6FFB74D,
                0x40FF6F00,
                0x80FFE082,
                0x26FF6F00
        ));

        // 6. Mahavir Jayanti
        themes.put(THEME_MAHAVIR_JAYANTI, new ThemeInfo(
                THEME_MAHAVIR_JAYANTI,
                R.string.theme_mahavir_jayanti,
                0xFF0E1015,
                0xFFC5A059,
                0xFF181C24,
                0x55D4AF37,
                0xFFF8F9FA,
                0xB3D4C49E,
                0xFFF8F9FA,
                false,
                R.drawable.wallpaper_mahavir_jayanti,
                null,
                0xE6D4AF37,
                0x33C5A059,
                0x80FFF8E7,
                0x20C5A059
        ));

        // 7. Baisakhi / Vishu / Puthandu
        themes.put(THEME_BAISAKHI, new ThemeInfo(
                THEME_BAISAKHI,
                R.string.theme_baisakhi,
                0xFF181105,
                0xFFFFB300,
                0xFF281C08,
                0x66FFC107,
                0xFFFFFDE7,
                0xB3FFE082,
                0xFFFFFDE7,
                false,
                R.drawable.wallpaper_baisakhi,
                null,
                0xE6FFD54F,
                0x40FFB300,
                0x80FFF59D,
                0x26FF8F00
        ));

        // 8. Buddha Purnima
        themes.put(THEME_BUDDHA_PURNIMA, new ThemeInfo(
                THEME_BUDDHA_PURNIMA,
                R.string.theme_buddha_purnima,
                0xFF061118,
                0xFF26A69A,
                0xFF0E1E28,
                0x664DB6AC,
                0xFFE0F2F1,
                0xB380CBC4,
                0xFFE0F2F1,
                false,
                R.drawable.wallpaper_buddha_purnima,
                null,
                0xE680CBC4,
                0x4026A69A,
                0x80B2DFDB,
                0x2600695C
        ));

        // 9. Rath Yatra
        themes.put(THEME_RATH_YATRA, new ThemeInfo(
                THEME_RATH_YATRA,
                R.string.theme_rath_yatra,
                0xFF1A0606,
                0xFFD32F2F,
                0xFF2A0C0C,
                0x66E53935,
                0xFFFFEBEE,
                0xB3EF9A9A,
                0xFFFFEBEE,
                false,
                R.drawable.wallpaper_rath_yatra,
                null,
                0xE6EF9A9A,
                0x40D32F2F,
                0x80FFCDD2,
                0x26B71C1C
        ));

        // 10. Onam (Thiruvonam)
        themes.put(THEME_ONAM, new ThemeInfo(
                THEME_ONAM,
                R.string.theme_onam,
                0xFF0A150A,
                0xFF2E7D32,
                0xFF122213,
                0x664CAF50,
                0xFFF1F8E9,
                0xB3A5D6A7,
                0xFFF1F8E9,
                false,
                R.drawable.wallpaper_onam,
                null,
                0xE681C784,
                0x402E7D32,
                0x80C8E6C9,
                0x261B5E20
        ));

        // 11. Raksha Bandhan
        themes.put(THEME_RAKSHA_BANDHAN, new ThemeInfo(
                THEME_RAKSHA_BANDHAN,
                R.string.theme_raksha_bandhan,
                0xFF180710,
                0xFFC2185B,
                0xFF260D1B,
                0x66E91E63,
                0xFFFCE4EC,
                0xB3F48FB1,
                0xFFFCE4EC,
                false,
                R.drawable.wallpaper_raksha_bandhan,
                null,
                0xE6F06292,
                0x40C2185B,
                0x80F8BBD0,
                0x26880E4F
        ));

        // 12. Krishna Janmashtami
        themes.put(THEME_KRISHNA_JANMAASHTAMI, new ThemeInfo(
                THEME_KRISHNA_JANMAASHTAMI,
                R.string.theme_krishna_janmashtami,
                0xFF060B1A,
                0xFF1E88E5,
                0xFF0C142A,
                0x6642A5F5,
                0xFFE3F2FD,
                0xB390CAF9,
                0xFFE3F2FD,
                false,
                R.drawable.wallpaper_janmashtami,
                null,
                0xE664B5F6,
                0x401E88E5,
                0x80BBDEFB,
                0x260D47A1
        ));

        // 13. Ganesh Chaturthi
        themes.put(THEME_GANESH_CHATURTHI, new ThemeInfo(
                THEME_GANESH_CHATURTHI,
                R.string.theme_ganesh_chaturthi,
                0xFF1C0A04,
                0xFFE65100,
                0xFF2A1008,
                0x66FF9800,
                0xFFFFF3E0,
                0xB3FFB74D,
                0xFFFFF3E0,
                false,
                R.drawable.wallpaper_ganesh_chaturthi,
                null,
                0xE6FFA726,
                0x40E65100,
                0x80FFE0B2,
                0x26BF360C
        ));

        // 14. Sharad Navratri
        themes.put(THEME_NAVRATRI, new ThemeInfo(
                THEME_NAVRATRI,
                R.string.theme_navratri,
                0xFF180614,
                0xFFAB47BC,
                0xFF260B21,
                0x66BA68C8,
                0xFFF3E5F5,
                0xB3CE93D8,
                0xFFF3E5F5,
                false,
                R.drawable.wallpaper_navratri,
                null,
                0xE6BA68C8,
                0x40AB47BC,
                0x80E1BEE7,
                0x266A1B9A
        ));

        // 15. Karwa Chauth
        themes.put(THEME_KARWA_CHAUTH, new ThemeInfo(
                THEME_KARWA_CHAUTH,
                R.string.theme_karwa_chauth,
                0xFF16060E,
                0xFFAD1457,
                0xFF240A18,
                0x66D81B60,
                0xFFFCE4EC,
                0xB3F06292,
                0xFFFCE4EC,
                false,
                R.drawable.wallpaper_karwa_chauth,
                null,
                0xE6F06292,
                0x40AD1457,
                0x80F48FB1,
                0x26880E4F
        ));

        // 16. Govardhan Puja
        themes.put(THEME_GOVARDHAN_PUJA, new ThemeInfo(
                THEME_GOVARDHAN_PUJA,
                R.string.theme_govardhan_puja,
                0xFF0A140A,
                0xFF558B2F,
                0xFF122212,
                0x667CB342,
                0xFFF1F8E9,
                0xB3AED581,
                0xFFF1F8E9,
                false,
                R.drawable.wallpaper_govardhan_puja,
                null,
                0xE69CCC65,
                0x40558B2F,
                0x80DCEDC8,
                0x2633691E
        ));

        // 17. Bhai Dooj
        themes.put(THEME_BHAI_DOOJ, new ThemeInfo(
                THEME_BHAI_DOOJ,
                R.string.theme_bhai_dooj,
                0xFF140718,
                0xFF8E24AA,
                0xFF220C28,
                0x66AB47BC,
                0xFFF3E5F5,
                0xB3CE93D8,
                0xFFF3E5F5,
                false,
                R.drawable.wallpaper_bhai_dooj,
                null,
                0xE6BA68C8,
                0x408E24AA,
                0x80E1BEE7,
                0x264A148C
        ));

        // 18. Chhath Puja
        themes.put(THEME_CHHATH_PUJA, new ThemeInfo(
                THEME_CHHATH_PUJA,
                R.string.theme_chhath_puja,
                0xFF140815,
                0xFFFF8F00,
                0xFF220E22,
                0x66FFA000,
                0xFFFFF8E1,
                0xB3FFB74D,
                0xFFFFF8E1,
                false,
                R.drawable.wallpaper_chhath_puja,
                null,
                0xE6FFB74D,
                0x40FF8F00,
                0x80FFE082,
                0x26FF6F00
        ));

        // 19. Guru Nanak Jayanti
        themes.put(THEME_GURU_NANAK_JAYANTI, new ThemeInfo(
                THEME_GURU_NANAK_JAYANTI,
                R.string.theme_guru_nanak_jayanti,
                0xFF070C18,
                0xFFF5C542,
                0xFF0E182E,
                0x66E6A817,
                0xFFFFF8E1,
                0xB3FFE082,
                0xFFFFF8E1,
                false,
                R.drawable.wallpaper_guru_nanak_jayanti,
                null,
                0xE6F5C542,
                0x40E6A817,
                0x80FFF8E1,
                0x26D4AF37
        ));

        // 20. Christmas
        themes.put(THEME_CHRISTMAS, new ThemeInfo(
                THEME_CHRISTMAS,
                R.string.theme_christmas,
                0xFF06120E,
                0xFF2D6A4F,
                0xFF0E221A,
                0x6652B788,
                0xFFF0FFF4,
                0xB395D5B2,
                0xFFF0FFF4,
                false,
                R.drawable.wallpaper_christmas,
                null,
                0xE674C69D,
                0x402D6A4F,
                0x80D8F3DC,
                0x261B4332
        ));

        // 21. Republic Day (26 January)
        themes.put(THEME_REPUBLIC_DAY, new ThemeInfo(
                THEME_REPUBLIC_DAY,
                R.string.theme_republic_day,
                0xFF060C1D,
                0xFF0D47A1,
                0x260D47A1,
                0x66FFD54F,
                0xFFFFF8E1,
                0xB3FFF8E1,
                0xFFFFD54F,
                false,
                R.drawable.wallpaper_republic_day,
                null,
                0xE6FFD54F,
                0x40FF9933,
                0x99FFFFFF,
                0x1A0A1128
        ));

        // 22. Independence Day (15 August)
        themes.put(THEME_INDEPENDENCE_DAY, new ThemeInfo(
                THEME_INDEPENDENCE_DAY,
                R.string.theme_independence_day,
                0xFF040D07,
                0xFF1B5E20,
                0x261B5E20,
                0x6681C784,
                0xFFF1F8E9,
                0xB3F1F8E9,
                0xFF81C784,
                false,
                R.drawable.wallpaper_independence_day,
                null,
                0xE681C784,
                0x40138808,
                0x99FFFFFF,
                0x1A040D07
        ));
    }

    private ThemeInfo loadCustomTheme() {
        int bg = 0xFF0D1117;
        int ambient = 0xFF00A39A;
        int dock = 0xFF161B22;
        int edge = 0x33388BFD;
        int text = 0xFFE6EDF3;
        int textDim = 0x8C8B949E;
        Bitmap customBmp = null;
        try {
            File dir = ctx.getExternalFilesDir(null);
            File file = dir != null ? new File(dir, "config.json") : null;
            if (file != null && file.isFile()) {
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buf = new byte[(int) file.length()];
                    int n = 0, r;
                    while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                    String textJson = new String(buf, 0, n, StandardCharsets.UTF_8);
                    if (textJson.startsWith("\uFEFF")) textJson = textJson.substring(1);
                    JSONObject json = new JSONObject(textJson);
                    if (json.has("background_color")) {
                        bg = Color.parseColor(json.getString("background_color")) | 0xFF000000;
                    }
                    if (json.has("ambient_color")) {
                        ambient = Color.parseColor(json.getString("ambient_color")) | 0xFF000000;
                    }
                }
            }
            File wallFile = dir != null ? new File(dir, "wallpaper.jpg") : null;
            if (wallFile != null && wallFile.isFile()) {
                try {
                    DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(wallFile.getPath(), opts);
                    int sample = 1;
                    while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) {
                        sample *= 2;
                    }
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    customBmp = BitmapFactory.decodeFile(wallFile.getPath(), opts);
                } catch (Exception | OutOfMemoryError ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return new ThemeInfo(
                THEME_CUSTOM,
                R.string.theme_custom,
                bg,
                ambient,
                dock,
                edge,
                text,
                textDim,
                text,
                false,
                0,
                customBmp,
                0xCC388BFD,
                0x22388BFD,
                0x55388BFD,
                0x29388BFD
        );
    }

    /**
     * Returns the 6 standard top-level themes (OLED Black, Aurora, Diwali, Dussehra, Light Silver, Custom).
     */
    public List<ThemeInfo> getStandardThemes() {
        List<ThemeInfo> list = new ArrayList<>(6);
        addIfPresent(list, THEME_DARK);
        addIfPresent(list, THEME_AURORA);
        addIfPresent(list, THEME_DIWALI);
        addIfPresent(list, THEME_DUSSEHRA);
        addIfPresent(list, THEME_LIGHT);
        addIfPresent(list, THEME_CUSTOM);
        return list;
    }

    /**
     * Default list of themes displayed in the standard theme picker (6 standard themes).
     */
    public List<ThemeInfo> getAvailableThemes() {
        return getStandardThemes();
    }

    /**
     * All 22 Indian festival themes in chronological festive order.
     */
    public List<ThemeInfo> getFestivalThemes() {
        List<ThemeInfo> list = new ArrayList<>(22);
        addIfPresent(list, THEME_MAKAR_SANKRANTI);
        addIfPresent(list, THEME_MAHA_SHIVRATRI);
        addIfPresent(list, THEME_HOLI);
        addIfPresent(list, THEME_UGADI);
        addIfPresent(list, THEME_RAM_NAVAMI);
        addIfPresent(list, THEME_MAHAVIR_JAYANTI);
        addIfPresent(list, THEME_BAISAKHI);
        addIfPresent(list, THEME_BUDDHA_PURNIMA);
        addIfPresent(list, THEME_RATH_YATRA);
        addIfPresent(list, THEME_ONAM);
        addIfPresent(list, THEME_RAKSHA_BANDHAN);
        addIfPresent(list, THEME_KRISHNA_JANMAASHTAMI);
        addIfPresent(list, THEME_GANESH_CHATURTHI);
        addIfPresent(list, THEME_NAVRATRI);
        addIfPresent(list, THEME_DUSSEHRA);
        addIfPresent(list, THEME_KARWA_CHAUTH);
        addIfPresent(list, THEME_DIWALI);
        addIfPresent(list, THEME_GOVARDHAN_PUJA);
        addIfPresent(list, THEME_BHAI_DOOJ);
        addIfPresent(list, THEME_CHHATH_PUJA);
        addIfPresent(list, THEME_GURU_NANAK_JAYANTI);
        addIfPresent(list, THEME_CHRISTMAS);
        return list;
    }

    public List<ThemeInfo> getSpringThemes() {
        List<ThemeInfo> list = new ArrayList<>(8);
        addIfPresent(list, THEME_MAKAR_SANKRANTI);
        addIfPresent(list, THEME_REPUBLIC_DAY);
        addIfPresent(list, THEME_MAHA_SHIVRATRI);
        addIfPresent(list, THEME_HOLI);
        addIfPresent(list, THEME_UGADI);
        addIfPresent(list, THEME_RAM_NAVAMI);
        addIfPresent(list, THEME_MAHAVIR_JAYANTI);
        addIfPresent(list, THEME_BAISAKHI);
        return list;
    }

    public List<ThemeInfo> getMonsoonThemes() {
        List<ThemeInfo> list = new ArrayList<>(7);
        addIfPresent(list, THEME_BUDDHA_PURNIMA);
        addIfPresent(list, THEME_RATH_YATRA);
        addIfPresent(list, THEME_INDEPENDENCE_DAY);
        addIfPresent(list, THEME_ONAM);
        addIfPresent(list, THEME_RAKSHA_BANDHAN);
        addIfPresent(list, THEME_KRISHNA_JANMAASHTAMI);
        addIfPresent(list, THEME_GANESH_CHATURTHI);
        return list;
    }

    public List<ThemeInfo> getAutumnThemes() {
        List<ThemeInfo> list = new ArrayList<>(4);
        addIfPresent(list, THEME_NAVRATRI);
        addIfPresent(list, THEME_DUSSEHRA);
        addIfPresent(list, THEME_KARWA_CHAUTH);
        addIfPresent(list, THEME_DIWALI);
        return list;
    }

    public List<ThemeInfo> getWinterThemes() {
        List<ThemeInfo> list = new ArrayList<>(5);
        addIfPresent(list, THEME_GOVARDHAN_PUJA);
        addIfPresent(list, THEME_BHAI_DOOJ);
        addIfPresent(list, THEME_CHHATH_PUJA);
        addIfPresent(list, THEME_GURU_NANAK_JAYANTI);
        addIfPresent(list, THEME_CHRISTMAS);
        return list;
    }

    public List<ThemeInfo> getAllThemes() {
        return new ArrayList<>(themes.values());
    }

    public ThemeInfo getTheme(String id) {
        return themes.get(id);
    }

    private void addIfPresent(List<ThemeInfo> target, String id) {
        ThemeInfo t = themes.get(id);
        if (t != null) target.add(t);
    }

    public ThemeInfo getCurrentTheme() {
        if (festivalEngine != null && festivalEngine.isAutoEnabled() && !festivalEngine.isManualOverride()) {
            festivalEngine.evaluate(null);
        }
        String id = prefs.getString(KEY_THEME, THEME_DARK);
        ThemeInfo info = themes.get(id);
        return info != null ? info : themes.get(THEME_DARK);
    }

    public FestivalThemeEngine getFestivalEngine() {
        return festivalEngine;
    }

    public FestivalThemeEngine.EvaluationResult evaluateFestivalTheme() {
        return festivalEngine.evaluate(null);
    }

    public FestivalThemeEngine.EvaluationResult setManualTheme(String id) {
        if (!themes.containsKey(id)) return null;
        return festivalEngine.setManualTheme(id, null);
    }

    public FestivalThemeEngine.EvaluationResult resumeFestivalAuto() {
        return festivalEngine.resumeAuto(null);
    }

    public FestivalThemeEngine.EvaluationResult setFestivalAutoEnabled(boolean enabled) {
        return festivalEngine.setAutoEnabled(enabled, null);
    }



    public void setTheme(String id) {
        if (!themes.containsKey(id)) return;
        setManualTheme(id);
    }

    public int getDockSize(int defaultColumns) {
        int size = prefs.getInt(KEY_DOCK_SIZE, defaultColumns);
        if (size < 4 || size > 6) size = 4;
        return size;
    }

    public void setDockSize(int size) {
        if (size < 4 || size > 6) return;
        prefs.edit().putInt(KEY_DOCK_SIZE, size).apply();
    }

    public boolean isReducedMotion() {
        return prefs.getBoolean(KEY_REDUCED_MOTION, false);
    }

    public void setReducedMotion(boolean reduced) {
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, reduced).apply();
    }
}
