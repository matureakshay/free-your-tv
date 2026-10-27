package com.freeyourtv.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for central festival greeting mapping, typography thresholding, and non-festival fallback.
 */
public class FestivalGreetingTest {

    private static final List<String> ALL_24_FESTIVALS = Arrays.asList(
            "makar_sankranti",
            "republic_day",
            "maha_shivratri",
            "holi",
            "ugadi",
            "baisakhi",
            "ram_navami",
            "mahavir_jayanti",
            "buddha_purnima",
            "rath_yatra",
            "independence_day",
            "onam",
            "raksha_bandhan",
            "krishna_janmashtami",
            "ganesh_chaturthi",
            "navratri",
            "dussehra",
            "karwa_chauth",
            "diwali",
            "govardhan_puja",
            "bhai_dooj",
            "chhath_puja",
            "guru_nanak_jayanti",
            "christmas"
    );

    @Test
    public void testAll24FestivalsHaveExplicitGreetingResource() {
        assertEquals("Must verify all 24 Pan-India festivals", 24, ALL_24_FESTIVALS.size());

        for (String festivalId : ALL_24_FESTIVALS) {
            int resId = FestivalGreetingHelper.getGreetingRes(festivalId);
            assertNotEquals("Festival '" + festivalId + "' must have a non-zero greeting string resource", 0, resId);
        }
    }

    @Test
    public void testNonFestivalThemesReturnZeroGreeting() {
        // Standard themes should have no festival greetings
        assertEquals(0, FestivalGreetingHelper.getGreetingRes(ThemeManager.THEME_DARK));
        assertEquals(0, FestivalGreetingHelper.getGreetingRes(ThemeManager.THEME_AURORA));
        assertEquals(0, FestivalGreetingHelper.getGreetingRes(ThemeManager.THEME_LIGHT));
        assertEquals(0, FestivalGreetingHelper.getGreetingRes(ThemeManager.THEME_CUSTOM));
        assertEquals(0, FestivalGreetingHelper.getGreetingRes("unknown_theme"));
        assertEquals(0, FestivalGreetingHelper.getGreetingRes(null));
        assertEquals(0, FestivalGreetingHelper.getGreetingRes(""));
    }

    @Test
    public void testLongGreetingThreshold() {
        // Greetings longer than 18 characters should be flagged as long greetings for typography scaling
        assertTrue(FestivalGreetingHelper.isLongGreeting("HAPPY INDEPENDENCE DAY")); // 22 chars
        assertTrue(FestivalGreetingHelper.isLongGreeting("HAPPY UGADI & GUDI PADWA")); // 24 chars
        assertTrue(FestivalGreetingHelper.isLongGreeting("HAPPY GURU NANAK JAYANTI")); // 24 chars
        assertTrue(FestivalGreetingHelper.isLongGreeting("HAPPY GANESH CHATURTHI")); // 22 chars
        assertTrue(FestivalGreetingHelper.isLongGreeting("HAPPY KRISHNA JANMASHTAMI")); // 25 chars

        // Normal / short greetings should not be flagged as long
        assertFalse(FestivalGreetingHelper.isLongGreeting("HAPPY DIWALI")); // 12 chars
        assertFalse(FestivalGreetingHelper.isLongGreeting("HAPPY HOLI")); // 10 chars
        assertFalse(FestivalGreetingHelper.isLongGreeting("HAPPY DUSSEHRA")); // 14 chars
        assertFalse(FestivalGreetingHelper.isLongGreeting("HAPPY ONAM")); // 10 chars
        assertFalse(FestivalGreetingHelper.isLongGreeting("MERRY CHRISTMAS")); // 15 chars
        assertFalse(FestivalGreetingHelper.isLongGreeting(null));
        assertFalse(FestivalGreetingHelper.isLongGreeting(""));
    }
}
