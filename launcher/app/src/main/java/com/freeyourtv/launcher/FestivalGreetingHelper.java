package com.freeyourtv.launcher;

import android.content.Context;
import androidx.annotation.StringRes;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides explicit, friendly English greetings for all 24 Pan-India festivals.
 */
public final class FestivalGreetingHelper {

    private static final Map<String, Integer> GREETINGS = new HashMap<>();

    static {
        // 24 Festival Mappings
        GREETINGS.put("makar_sankranti", R.string.greeting_fest_makar_sankranti);
        GREETINGS.put("republic_day", R.string.greeting_fest_republic_day);
        GREETINGS.put("maha_shivratri", R.string.greeting_fest_maha_shivratri);
        GREETINGS.put("holi", R.string.greeting_fest_holi);
        GREETINGS.put("ugadi", R.string.greeting_fest_ugadi);
        GREETINGS.put("baisakhi", R.string.greeting_fest_baisakhi);
        GREETINGS.put("ram_navami", R.string.greeting_fest_ram_navami);
        GREETINGS.put("mahavir_jayanti", R.string.greeting_fest_mahavir_jayanti);
        GREETINGS.put("buddha_purnima", R.string.greeting_fest_buddha_purnima);
        GREETINGS.put("rath_yatra", R.string.greeting_fest_rath_yatra);
        GREETINGS.put("independence_day", R.string.greeting_fest_independence_day);
        GREETINGS.put("onam", R.string.greeting_fest_onam);
        GREETINGS.put("raksha_bandhan", R.string.greeting_fest_raksha_bandhan);
        GREETINGS.put("krishna_janmashtami", R.string.greeting_fest_krishna_janmashtami);
        GREETINGS.put("ganesh_chaturthi", R.string.greeting_fest_ganesh_chaturthi);
        GREETINGS.put("navratri", R.string.greeting_fest_navratri);
        GREETINGS.put("dussehra", R.string.greeting_fest_dussehra);
        GREETINGS.put("karwa_chauth", R.string.greeting_fest_karwa_chauth);
        GREETINGS.put("diwali", R.string.greeting_fest_diwali);
        GREETINGS.put("govardhan_puja", R.string.greeting_fest_govardhan_puja);
        GREETINGS.put("bhai_dooj", R.string.greeting_fest_bhai_dooj);
        GREETINGS.put("chhath_puja", R.string.greeting_fest_chhath_puja);
        GREETINGS.put("guru_nanak_jayanti", R.string.greeting_fest_guru_nanak_jayanti);
        GREETINGS.put("christmas", R.string.greeting_fest_christmas);
    }

    private FestivalGreetingHelper() {}

    @StringRes
    public static int getGreetingRes(String festivalId) {
        if (festivalId == null) return 0;
        Integer res = GREETINGS.get(festivalId);
        return res != null ? res : 0;
    }

    public static String getGreeting(Context context, String festivalId) {
        if (context == null || festivalId == null) return null;
        int res = getGreetingRes(festivalId);
        return res != 0 ? context.getString(res) : null;
    }

    public static String getGreetingForTheme(Context context, String themeId) {
        return getGreeting(context, themeId);
    }

    public static boolean isLongGreeting(String text) {
        return text != null && text.length() > 18;
    }
}
