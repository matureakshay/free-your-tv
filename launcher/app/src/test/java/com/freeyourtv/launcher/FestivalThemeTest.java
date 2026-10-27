package com.freeyourtv.launcher;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Comprehensive unit tests for Indian Festival Calendar and Automatic Theme Selection.
 */
public class FestivalThemeTest {

    private static class InMemoryStore implements FestivalThemeEngine.Store {
        private final Map<String, Object> data = new HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = data.get(key);
            return (v instanceof Boolean) ? (Boolean) v : defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            data.put(key, value);
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = data.get(key);
            return (v instanceof String) ? (String) v : defValue;
        }

        @Override
        public void putString(String key, String value) {
            data.put(key, value);
        }
    }

    private InMemoryStore store;

    @Before
    public void setUp() {
        store = new InMemoryStore();
    }

    private FestivalThemeEngine createEngine(LocalDate fixedDate) {
        return new FestivalThemeEngine(store, () -> fixedDate);
    }

    // 1. Three-day-before activation
    @Test
    public void testThreeDaysBeforeActivation() {
        // Diwali 2026 is on November 8, 2026.
        // D - 3 is November 5, 2026.
        LocalDate beforeWindow = LocalDate.of(2026, 11, 4);
        LocalDate startWindow = LocalDate.of(2026, 11, 5);

        FestivalThemeEngine engineBefore = createEngine(beforeWindow);
        FestivalThemeEngine.EvaluationResult resBefore = engineBefore.evaluate(beforeWindow);
        assertEquals(ThemeManager.THEME_DARK, resBefore.themeId);
        assertNull(resBefore.activeFestival);

        FestivalThemeEngine engineStart = createEngine(startWindow);
        FestivalThemeEngine.EvaluationResult resStart = engineStart.evaluate(startWindow);
        assertEquals(ThemeManager.THEME_DIWALI, resStart.themeId);
        assertNotNull(resStart.activeFestival);
        assertEquals("diwali", resStart.activeFestival.id);
    }

    // 2. Active festival day
    @Test
    public void testActiveFestivalDay() {
        // Dussehra 2026 is on October 20, 2026.
        LocalDate dussehraDay = LocalDate.of(2026, 10, 20);
        FestivalThemeEngine engine = createEngine(dussehraDay);
        FestivalThemeEngine.EvaluationResult res = engine.evaluate(dussehraDay);

        assertEquals(ThemeManager.THEME_DUSSEHRA, res.themeId);
        assertNotNull(res.activeFestival);
        assertEquals("dussehra", res.activeFestival.id);
        assertEquals(0, res.activeFestival.daysUntilFestival(dussehraDay));
    }

    // 3. Return to OLED Black on D + 1 day
    @Test
    public void testReturnToOledBlackFollowingDay() {
        // Dussehra 2026 is October 20. On October 21 (D + 1), it ends.
        LocalDate postDussehra = LocalDate.of(2026, 10, 21);
        FestivalThemeEngine engine = createEngine(postDussehra);
        FestivalThemeEngine.EvaluationResult res = engine.evaluate(postDussehra);

        assertEquals(ThemeManager.THEME_DARK, res.themeId);
        assertNull(res.activeFestival);
        assertNotNull(res.nextFestival);
        assertEquals("karwa_chauth", res.nextFestival.id);
    }

    // 4. Month boundary handling
    @Test
    public void testMonthBoundaryHandling() {
        // Buddha Purnima 2026 is on May 1, 2026.
        // D - 3 is April 28, 2026 (crosses month boundary from May to April).
        LocalDate apr27 = LocalDate.of(2026, 4, 27);
        LocalDate apr28 = LocalDate.of(2026, 4, 28);
        LocalDate may1 = LocalDate.of(2026, 5, 1);
        LocalDate may2 = LocalDate.of(2026, 5, 2);

        Festival bp = new Festival("buddha_purnima", "Buddha Purnima", may1, null, 50);
        assertFalse(bp.isActiveOn(apr27));
        assertTrue(bp.isActiveOn(apr28));
        assertTrue(bp.isActiveOn(may1));
        assertFalse(bp.isActiveOn(may2));
    }

    // 5. Year boundary handling
    @Test
    public void testYearBoundaryHandling() {
        // Festival on January 2, 2028. D - 3 is December 30, 2027.
        Festival newYearFest = new Festival("test_fest", "Test Fest", LocalDate.of(2028, 1, 2), "custom", 50);

        LocalDate dec29 = LocalDate.of(2027, 12, 29);
        LocalDate dec30 = LocalDate.of(2027, 12, 30);
        LocalDate dec31 = LocalDate.of(2027, 12, 31);
        LocalDate jan1 = LocalDate.of(2028, 1, 1);
        LocalDate jan2 = LocalDate.of(2028, 1, 2);
        LocalDate jan3 = LocalDate.of(2028, 1, 3);

        assertFalse(newYearFest.isActiveOn(dec29));
        assertTrue(newYearFest.isActiveOn(dec30));
        assertTrue(newYearFest.isActiveOn(dec31));
        assertTrue(newYearFest.isActiveOn(jan1));
        assertTrue(newYearFest.isActiveOn(jan2));
        assertFalse(newYearFest.isActiveOn(jan3));
    }

    // 6. Leap-year date handling
    @Test
    public void testLeapYearDateHandling() {
        // 2028 is a leap year (February 29 exists).
        LocalDate feb29_2028 = LocalDate.of(2028, 2, 29);
        assertTrue(feb29_2028.isLeapYear());

        // A festival on March 2, 2028: D - 3 must be February 28 (Feb 28, Feb 29, Mar 1, Mar 2)
        Festival mar2Fest = new Festival("leap_test", "Leap Test", LocalDate.of(2028, 3, 2), null, 50);
        assertEquals(LocalDate.of(2028, 2, 28), mar2Fest.getStartDate());
        assertTrue(mar2Fest.isActiveOn(LocalDate.of(2028, 2, 28)));
        assertTrue(mar2Fest.isActiveOn(LocalDate.of(2028, 2, 29)));
        assertTrue(mar2Fest.isActiveOn(LocalDate.of(2028, 3, 1)));
        assertTrue(mar2Fest.isActiveOn(LocalDate.of(2028, 3, 2)));
        assertFalse(mar2Fest.isActiveOn(LocalDate.of(2028, 3, 3)));
    }

    // 7. Overlapping festivals precedence
    @Test
    public void testOverlappingFestivalsPrecedence() {
        // In 2026:
        // Diwali is on November 8, 2026 (Window: Nov 5 - Nov 8, has dedicated theme THEME_DIWALI)
        // Govardhan Puja is on November 10, 2026 (Window: Nov 7 - Nov 10, standard fallback)
        // On November 7 and November 8, both windows overlap!
        LocalDate nov8 = LocalDate.of(2026, 11, 8);
        List<Festival> activeNov8 = FestivalCalendar.getActiveFestivals(nov8);
        assertTrue(activeNov8.size() >= 2);

        // Precedence: Dedicated theme (Diwali) must win over generic fallback (Govardhan Puja)
        Festival primaryNov8 = FestivalCalendar.getPrimaryActiveFestival(nov8);
        assertNotNull(primaryNov8);
        assertEquals("diwali", primaryNov8.id);
        assertEquals(ThemeManager.THEME_DIWALI, primaryNov8.designatedThemeId);

        // On November 9: Diwali window [Nov 5..8] is over. Govardhan Puja [Nov 7..10] is active.
        LocalDate nov9 = LocalDate.of(2026, 11, 9);
        Festival primaryNov9 = FestivalCalendar.getPrimaryActiveFestival(nov9);
        assertNotNull(primaryNov9);
        assertEquals("govardhan_puja", primaryNov9.id);
    }

    // 8. Manual theme override and resuming automation
    @Test
    public void testManualThemeOverrideAndResumingAutomation() {
        LocalDate dussehra = LocalDate.of(2026, 10, 20);
        FestivalThemeEngine engine = createEngine(dussehra);

        // Initially in Auto mode on Dussehra:
        FestivalThemeEngine.EvaluationResult initRes = engine.evaluate(dussehra);
        assertEquals(ThemeManager.THEME_DUSSEHRA, initRes.themeId);
        assertEquals(FestivalThemeEngine.Mode.AUTO, initRes.mode);

        // User manually overrides with Aurora:
        FestivalThemeEngine.EvaluationResult overrideRes = engine.setManualTheme(ThemeManager.THEME_AURORA, dussehra);
        assertEquals(ThemeManager.THEME_AURORA, overrideRes.themeId);
        assertEquals(FestivalThemeEngine.Mode.MANUAL_SUSPENDED, overrideRes.mode);

        // During non-festival period (e.g., Oct 25), Aurora remains:
        LocalDate oct25 = LocalDate.of(2026, 10, 25);
        FestivalThemeEngine.EvaluationResult midRes = engine.evaluate(oct25);
        assertEquals(ThemeManager.THEME_AURORA, midRes.themeId);
        assertEquals(FestivalThemeEngine.Mode.MANUAL_SUSPENDED, midRes.mode);

        // When the NEXT festival window arrives (Diwali window starts Nov 5):
        LocalDate nov5 = LocalDate.of(2026, 11, 5);
        FestivalThemeEngine.EvaluationResult nextFestRes = engine.evaluate(nov5);
        assertEquals(ThemeManager.THEME_DIWALI, nextFestRes.themeId);
        assertEquals(FestivalThemeEngine.Mode.AUTO, nextFestRes.mode);
        assertFalse(engine.isManualOverride());

        // Now user overrides again with Light Silver:
        engine.setManualTheme(ThemeManager.THEME_LIGHT, nov5);
        assertEquals(ThemeManager.THEME_LIGHT, engine.evaluate(nov5).themeId);

        // User explicitly resumes auto:
        FestivalThemeEngine.EvaluationResult resumed = engine.resumeAuto(nov5);
        assertEquals(ThemeManager.THEME_DIWALI, resumed.themeId);
        assertEquals(FestivalThemeEngine.Mode.AUTO, resumed.mode);
    }

    // 9. Automation disabled
    @Test
    public void testAutomationDisabled() {
        LocalDate dussehra = LocalDate.of(2026, 10, 20);
        FestivalThemeEngine engine = createEngine(dussehra);

        // Turn off automation:
        engine.setAutoEnabled(false, dussehra);
        assertFalse(engine.isAutoEnabled());

        // Even though today is Dussehra, theme remains user's choice and doesn't auto-switch
        store.putString(FestivalThemeEngine.KEY_THEME, ThemeManager.THEME_LIGHT);
        FestivalThemeEngine.EvaluationResult res = engine.evaluate(dussehra);
        assertEquals(ThemeManager.THEME_LIGHT, res.themeId);
        assertEquals(FestivalThemeEngine.Mode.DISABLED, res.mode);
    }

    // 10. Unknown or unsupported festival dates
    @Test
    public void testUnknownOrUnsupportedDates() {
        // Date outside supported dataset (e.g. year 2035)
        LocalDate year2035 = LocalDate.of(2035, 6, 15);
        FestivalThemeEngine engine = createEngine(year2035);
        FestivalThemeEngine.EvaluationResult res = engine.evaluate(year2035);

        assertEquals(ThemeManager.THEME_DARK, res.themeId);
        assertNull(res.activeFestival);
        assertNull(res.nextFestival);
        assertEquals(FestivalThemeEngine.Mode.AUTO, res.mode);
    }

    // 11. Offline operation using bundled dataset
    @Test
    public void testOfflineBundledDataset() {
        List<Festival> all = FestivalCalendar.getAllFestivals();
        assertNotNull(all);
        assertTrue("Dataset should contain all 24 festivals for 3 years (72 entries)", all.size() >= 72);

        // Check 2025, 2026, 2027 years are populated
        List<Festival> f2025 = FestivalCalendar.getFestivalsForYear(2025);
        List<Festival> f2026 = FestivalCalendar.getFestivalsForYear(2026);
        List<Festival> f2027 = FestivalCalendar.getFestivalsForYear(2027);

        assertEquals(24, f2025.size());
        assertEquals(24, f2026.size());
        assertEquals(24, f2027.size());

        // Confirm all 22 festivals have dedicated theme mappings
        for (Festival f : f2026) {
            assertTrue("Festival " + f.id + " must have a dedicated theme mapping", f.hasDedicatedTheme());
            assertNotNull("Festival " + f.id + " designated theme ID must not be null", f.designatedThemeId);
        }

        boolean foundDiwali = false;
        boolean foundDussehra = false;
        for (Festival f : f2026) {
            if ("diwali".equals(f.id)) {
                assertEquals(ThemeManager.THEME_DIWALI, f.designatedThemeId);
                foundDiwali = true;
            }
            if ("dussehra".equals(f.id)) {
                assertEquals(ThemeManager.THEME_DUSSEHRA, f.designatedThemeId);
                foundDussehra = true;
            }
        }
        assertTrue(foundDiwali);
        assertTrue(foundDussehra);
    }

    // 12. Verified DoPT Delhi Gazetted Dates
    @Test
    public void testCorrectedAndSubstantiatedDates() {
        // Janmashtami 2025: DoPT Gazetted Annexure I #11 is August 16, 2025
        Festival jm2025 = FestivalCalendar.getFestivalsForYear(2025).stream()
                .filter(f -> "krishna_janmashtami".equals(f.id)).findFirst().orElse(null);
        assertNotNull(jm2025);
        assertEquals(LocalDate.of(2025, 8, 16), jm2025.date);

        // Ram Navami 2026: DoPT Gazetted Annexure I #4 is March 26, 2026
        Festival rn2026 = FestivalCalendar.getFestivalsForYear(2026).stream()
                .filter(f -> "ram_navami".equals(f.id)).findFirst().orElse(null);
        assertNotNull(rn2026);
        assertEquals(LocalDate.of(2026, 3, 26), rn2026.date);

        // Govardhan Puja 2026: DoPT Restricted Holiday Annexure II is November 9, 2026
        Festival gp2026 = FestivalCalendar.getFestivalsForYear(2026).stream()
                .filter(f -> "govardhan_puja".equals(f.id)).findFirst().orElse(null);
        assertNotNull(gp2026);
        assertEquals(LocalDate.of(2026, 11, 9), gp2026.date);
    }

    // 13. Verified 2027 festivals substantiated with Drik Panchang Delhi
    @Test
    public void testVerified2027FestivalsActivation() {
        for (Festival f : FestivalCalendar.getFestivalsForYear(2027)) {
            assertTrue("2027 festival " + f.id + " must be marked verified", f.verified);
            assertTrue("Verified festival " + f.id + " must be active on its own date", f.isActiveOn(f.date));
            assertTrue("Verified festival " + f.id + " must be active on D-3", f.isActiveOn(f.date.minusDays(3)));
            assertFalse("Verified festival " + f.id + " must NOT be active on D-4", f.isActiveOn(f.date.minusDays(4)));
            assertFalse("Verified festival " + f.id + " must NOT be active on D+1", f.isActiveOn(f.date.plusDays(1)));
        }

        // On Diwali 2027 date (Oct 29, 2027): engine must evaluate to THEME_DIWALI
        LocalDate diwali2027 = LocalDate.of(2027, 10, 29);
        FestivalThemeEngine engine = createEngine(diwali2027);
        FestivalThemeEngine.EvaluationResult res = engine.evaluate(diwali2027);
        assertEquals(ThemeManager.THEME_DIWALI, res.themeId);
        assertNotNull(res.activeFestival);
        assertEquals("diwali", res.activeFestival.id);
    }

    // 14. Manual override preserved across non-festival gaps and resumes at next festival
    @Test
    public void testManualOverridePreservedAcrossGapsAndResumesAtNextFestival() {
        // User manually sets Aurora on Dussehra 2026 (Oct 20, 2026)
        LocalDate dussehra = LocalDate.of(2026, 10, 20);
        FestivalThemeEngine engine = createEngine(dussehra);
        engine.setManualTheme(ThemeManager.THEME_AURORA, dussehra);

        // During non-festival gap (Oct 23, 2026):
        // Manual override must NOT be silently replaced with OLED Black!
        LocalDate oct23 = LocalDate.of(2026, 10, 23);
        FestivalThemeEngine.EvaluationResult resGap = engine.evaluate(oct23);
        assertEquals(ThemeManager.THEME_AURORA, resGap.themeId);
        assertEquals(FestivalThemeEngine.Mode.MANUAL_SUSPENDED, resGap.mode);

        // When the next festival arrives (Karwa Chauth window starts Oct 26..29):
        // Dedicated visual theme for Karwa Chauth resumes automation
        LocalDate karwaChauth = LocalDate.of(2026, 10, 29);
        FestivalThemeEngine.EvaluationResult resKarwa = engine.evaluate(karwaChauth);
        assertEquals(ThemeManager.THEME_KARWA_CHAUTH, resKarwa.themeId);
        assertEquals(FestivalThemeEngine.Mode.AUTO, resKarwa.mode);

        // When Diwali (Nov 5..8, 2026) arrives with its dedicated visual theme:
        LocalDate diwaliWindow = LocalDate.of(2026, 11, 5);
        FestivalThemeEngine.EvaluationResult resDiwali = engine.evaluate(diwaliWindow);
        assertEquals(ThemeManager.THEME_DIWALI, resDiwali.themeId);
        assertEquals(FestivalThemeEngine.Mode.AUTO, resDiwali.mode);
    }

    // 15. All 24 festivals have dedicated theme IDs and valid mappings
    @Test
    public void testAll24FestivalThemesDedicatedAndValid() {
        List<Festival> f2026 = FestivalCalendar.getFestivalsForYear(2026);
        assertEquals(24, f2026.size());

        for (Festival f : f2026) {
            assertTrue("Festival " + f.id + " must have dedicated theme", f.hasDedicatedTheme());
            assertNotNull("Theme ID must not be null for " + f.id, f.designatedThemeId);
            assertFalse("Theme ID must not be empty for " + f.id, f.designatedThemeId.trim().isEmpty());
        }

        // Test seasonal category distributions (8 Spring, 7 Monsoon, 4 Autumn, 5 Winter)
        java.util.Set<String> springIds = new java.util.HashSet<>(java.util.Arrays.asList(
                "makar_sankranti", "republic_day", "maha_shivratri", "holi", "ugadi", "ram_navami", "mahavir_jayanti", "baisakhi"));
        java.util.Set<String> monsoonIds = new java.util.HashSet<>(java.util.Arrays.asList(
                "buddha_purnima", "rath_yatra", "independence_day", "onam", "raksha_bandhan", "krishna_janmashtami", "ganesh_chaturthi"));
        java.util.Set<String> autumnIds = new java.util.HashSet<>(java.util.Arrays.asList(
                "navratri", "dussehra", "karwa_chauth", "diwali"));
        java.util.Set<String> winterIds = new java.util.HashSet<>(java.util.Arrays.asList(
                "govardhan_puja", "bhai_dooj", "chhath_puja", "guru_nanak_jayanti", "christmas"));

        assertEquals(8, springIds.size());
        assertEquals(7, monsoonIds.size());
        assertEquals(4, autumnIds.size());
        assertEquals(5, winterIds.size());

        for (Festival f : f2026) {
            boolean categorized = springIds.contains(f.id) || monsoonIds.contains(f.id) ||
                    autumnIds.contains(f.id) || winterIds.contains(f.id);
            assertTrue("Festival " + f.id + " must belong to a seasonal gallery category", categorized);
        }
    }

    // 16. Comprehensive verification of all 72 bundled festival occurrences
    @Test
    public void testAll72FestivalOccurrencesActivationAndWindows() {
        List<Festival> allOccurrences = FestivalCalendar.getAllFestivals();
        assertEquals("Expected exactly 72 festival occurrences (24 festivals * 3 years)", 72, allOccurrences.size());

        for (Festival f : allOccurrences) {
            // 1. Verify mapping to expected theme
            assertNotNull("Festival " + f.id + " (" + f.date + ") must have a designated theme ID", f.designatedThemeId);
            assertFalse("Festival " + f.id + " (" + f.date + ") theme ID must not be empty", f.designatedThemeId.trim().isEmpty());
            assertTrue("Festival " + f.id + " (" + f.date + ") must report hasDedicatedTheme() == true", f.hasDedicatedTheme());
            assertTrue("Festival " + f.id + " (" + f.date + ") must be marked verified", f.verified);

            LocalDate dMinus4 = f.date.minusDays(4);
            LocalDate dMinus3 = f.date.minusDays(3);
            LocalDate d = f.date;
            LocalDate dPlus1 = f.date.plusDays(1);

            // 2. Verified occurrences: must activate within [D-3, D] and never outside [D-3, D]
            assertFalse("Verified festival " + f.id + " (" + f.date + ") must NOT be active on D - 4 (" + dMinus4 + ")",
                    f.isActiveOn(dMinus4));
            assertTrue("Verified festival " + f.id + " (" + f.date + ") MUST be active on start date D - 3 (" + dMinus3 + ")",
                    f.isActiveOn(dMinus3));
            assertTrue("Verified festival " + f.id + " (" + f.date + ") MUST be active on festival date D (" + d + ")",
                    f.isActiveOn(d));
            assertFalse("Verified festival " + f.id + " (" + f.date + ") must NOT be active on D + 1 (" + dPlus1 + ")",
                    f.isActiveOn(dPlus1));

            assertEquals(dMinus3, f.getStartDate());
            assertEquals(d, f.getEndDate());
        }
    }

    // 17. Date provider injection in FestivalThemeEngine for deterministic testing
    @Test
    public void testDateProviderInjection() {
        // Create engine with date provider returning Dussehra 2026 (Oct 20, 2026)
        LocalDate dussehra2026 = LocalDate.of(2026, 10, 20);
        FestivalThemeEngine engine = createEngine(dussehra2026);
        FestivalThemeEngine.EvaluationResult resDussehra = engine.evaluate(null);
        assertEquals(ThemeManager.THEME_DUSSEHRA, resDussehra.themeId);
        assertNotNull(resDussehra.activeFestival);
        assertEquals("dussehra", resDussehra.activeFestival.id);

        // Create engine with date provider returning Diwali 2027 (Oct 29, 2027)
        LocalDate diwali2027 = LocalDate.of(2027, 10, 29);
        FestivalThemeEngine engineDiwali = createEngine(diwali2027);
        FestivalThemeEngine.EvaluationResult resDiwali = engineDiwali.evaluate(null);
        assertEquals(ThemeManager.THEME_DIWALI, resDiwali.themeId);
        assertNotNull(resDiwali.activeFestival);
        assertEquals("diwali", resDiwali.activeFestival.id);

        // Direct date injection into evaluate(date) overrides provider
        LocalDate offFestival = LocalDate.of(2027, 6, 15);
        FestivalThemeEngine.EvaluationResult resOff = engine.evaluate(offFestival);
        assertEquals(ThemeManager.THEME_DARK, resOff.themeId);
        assertNull(resOff.activeFestival);
    }

    // 18. Multi-Year Festival Matrix: Key dates across 2026 and 2027
    @Test
    public void testMultiYearFestivalMatrix() {
        // 2026 Key Festivals
        FestivalThemeEngine engine = createEngine(LocalDate.of(2026, 1, 1));

        // Dussehra 2026 (Oct 20)
        assertEquals(ThemeManager.THEME_DUSSEHRA, engine.evaluate(LocalDate.of(2026, 10, 20)).themeId);
        // Diwali 2026 (Nov 8)
        assertEquals(ThemeManager.THEME_DIWALI, engine.evaluate(LocalDate.of(2026, 11, 8)).themeId);

        // 2027 Seasonal Key Festivals
        // Spring
        assertEquals(ThemeManager.THEME_MAKAR_SANKRANTI, engine.evaluate(LocalDate.of(2027, 1, 15)).themeId);
        assertEquals(ThemeManager.THEME_MAHA_SHIVRATRI, engine.evaluate(LocalDate.of(2027, 3, 6)).themeId);
        assertEquals(ThemeManager.THEME_HOLI, engine.evaluate(LocalDate.of(2027, 3, 22)).themeId);
        assertEquals(ThemeManager.THEME_UGADI, engine.evaluate(LocalDate.of(2027, 4, 7)).themeId);
        assertEquals(ThemeManager.THEME_RAM_NAVAMI, engine.evaluate(LocalDate.of(2027, 4, 15)).themeId);
        assertEquals(ThemeManager.THEME_BAISAKHI, engine.evaluate(LocalDate.of(2027, 4, 14)).themeId);
        assertEquals(ThemeManager.THEME_MAHAVIR_JAYANTI, engine.evaluate(LocalDate.of(2027, 4, 19)).themeId);

        // Monsoon
        assertEquals(ThemeManager.THEME_BUDDHA_PURNIMA, engine.evaluate(LocalDate.of(2027, 5, 20)).themeId);
        assertEquals(ThemeManager.THEME_RATH_YATRA, engine.evaluate(LocalDate.of(2027, 7, 5)).themeId);
        assertEquals(ThemeManager.THEME_RAKSHA_BANDHAN, engine.evaluate(LocalDate.of(2027, 8, 17)).themeId);
        assertEquals(ThemeManager.THEME_KRISHNA_JANMAASHTAMI, engine.evaluate(LocalDate.of(2027, 8, 25)).themeId);

        // Autumn
        assertEquals(ThemeManager.THEME_GANESH_CHATURTHI, engine.evaluate(LocalDate.of(2027, 9, 4)).themeId);
        assertEquals(ThemeManager.THEME_ONAM, engine.evaluate(LocalDate.of(2027, 9, 12)).themeId);
        assertEquals(ThemeManager.THEME_NAVRATRI, engine.evaluate(LocalDate.of(2027, 9, 30)).themeId);
        assertEquals(ThemeManager.THEME_DUSSEHRA, engine.evaluate(LocalDate.of(2027, 10, 9)).themeId);
        assertEquals(ThemeManager.THEME_KARWA_CHAUTH, engine.evaluate(LocalDate.of(2027, 10, 18)).themeId);
        assertEquals(ThemeManager.THEME_DIWALI, engine.evaluate(LocalDate.of(2027, 10, 29)).themeId);
        assertEquals(ThemeManager.THEME_GOVARDHAN_PUJA, engine.evaluate(LocalDate.of(2027, 10, 30)).themeId);
        assertEquals(ThemeManager.THEME_BHAI_DOOJ, engine.evaluate(LocalDate.of(2027, 10, 31)).themeId);

        // Winter
        assertEquals(ThemeManager.THEME_CHHATH_PUJA, engine.evaluate(LocalDate.of(2027, 11, 4)).themeId);
        assertEquals(ThemeManager.THEME_GURU_NANAK_JAYANTI, engine.evaluate(LocalDate.of(2027, 11, 14)).themeId);
        assertEquals(ThemeManager.THEME_CHRISTMAS, engine.evaluate(LocalDate.of(2027, 12, 25)).themeId);
    }

    // 19. Boundary conditions for 2027 festivals
    @Test
    public void test2027FestivalWindowBoundaries() {
        FestivalThemeEngine engine = createEngine(LocalDate.of(2027, 1, 1));

        // Holi 2027 is March 22. D-3 is March 19. D-4 is March 18. D+1 is March 23.
        assertEquals(ThemeManager.THEME_DARK, engine.evaluate(LocalDate.of(2027, 3, 18)).themeId);
        assertEquals(ThemeManager.THEME_HOLI, engine.evaluate(LocalDate.of(2027, 3, 19)).themeId);
        assertEquals(ThemeManager.THEME_HOLI, engine.evaluate(LocalDate.of(2027, 3, 22)).themeId);
        assertEquals(ThemeManager.THEME_DARK, engine.evaluate(LocalDate.of(2027, 3, 23)).themeId);

        // Christmas 2027 is Dec 25. D-3 is Dec 22. D-4 is Dec 21. D+1 is Dec 26.
        assertEquals(ThemeManager.THEME_DARK, engine.evaluate(LocalDate.of(2027, 12, 21)).themeId);
        assertEquals(ThemeManager.THEME_CHRISTMAS, engine.evaluate(LocalDate.of(2027, 12, 22)).themeId);
        assertEquals(ThemeManager.THEME_CHRISTMAS, engine.evaluate(LocalDate.of(2027, 12, 25)).themeId);
        assertEquals(ThemeManager.THEME_DARK, engine.evaluate(LocalDate.of(2027, 12, 26)).themeId);
    }

    // 20. Republic Day window tests for 2026 and 2027 (D-4, D-3, D, D+1)
    @Test
    public void testRepublicDayWindows2026And2027() {
        // Republic Day 2026: Jan 26. Window is Jan 23 to Jan 26.
        FestivalThemeEngine engine2026 = createEngine(LocalDate.of(2026, 1, 1));
        assertEquals(ThemeManager.THEME_DARK, engine2026.evaluate(LocalDate.of(2026, 1, 22)).themeId); // D-4
        assertEquals(ThemeManager.THEME_REPUBLIC_DAY, engine2026.evaluate(LocalDate.of(2026, 1, 23)).themeId); // D-3
        assertEquals(ThemeManager.THEME_REPUBLIC_DAY, engine2026.evaluate(LocalDate.of(2026, 1, 25)).themeId); // D-1
        assertEquals(ThemeManager.THEME_REPUBLIC_DAY, engine2026.evaluate(LocalDate.of(2026, 1, 26)).themeId); // D
        assertEquals(ThemeManager.THEME_DARK, engine2026.evaluate(LocalDate.of(2026, 1, 27)).themeId); // D+1

        // Republic Day 2027: Jan 26. Window is Jan 23 to Jan 26.
        FestivalThemeEngine engine2027 = createEngine(LocalDate.of(2027, 1, 1));
        assertEquals(ThemeManager.THEME_DARK, engine2027.evaluate(LocalDate.of(2027, 1, 22)).themeId); // D-4
        assertEquals(ThemeManager.THEME_REPUBLIC_DAY, engine2027.evaluate(LocalDate.of(2027, 1, 23)).themeId); // D-3
        assertEquals(ThemeManager.THEME_REPUBLIC_DAY, engine2027.evaluate(LocalDate.of(2027, 1, 25)).themeId); // D-1
        assertEquals(ThemeManager.THEME_REPUBLIC_DAY, engine2027.evaluate(LocalDate.of(2027, 1, 26)).themeId); // D
        assertEquals(ThemeManager.THEME_DARK, engine2027.evaluate(LocalDate.of(2027, 1, 27)).themeId); // D+1
    }

    // 21. Independence Day window tests for 2026 and 2027 (D-4, D-3, D, D+1)
    @Test
    public void testIndependenceDayWindows2026And2027() {
        // Independence Day 2026: Aug 15. Window is Aug 12 to Aug 15.
        FestivalThemeEngine engine2026 = createEngine(LocalDate.of(2026, 8, 1));
        assertEquals(ThemeManager.THEME_DARK, engine2026.evaluate(LocalDate.of(2026, 8, 11)).themeId); // D-4
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine2026.evaluate(LocalDate.of(2026, 8, 12)).themeId); // D-3
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine2026.evaluate(LocalDate.of(2026, 8, 14)).themeId); // D-1
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine2026.evaluate(LocalDate.of(2026, 8, 15)).themeId); // D
        assertEquals(ThemeManager.THEME_DARK, engine2026.evaluate(LocalDate.of(2026, 8, 16)).themeId); // D+1

        // Independence Day 2027: Aug 15. Window is Aug 12 to Aug 15.
        FestivalThemeEngine engine2027 = createEngine(LocalDate.of(2027, 8, 1));
        assertEquals(ThemeManager.THEME_DARK, engine2027.evaluate(LocalDate.of(2027, 8, 11)).themeId); // D-4
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine2027.evaluate(LocalDate.of(2027, 8, 12)).themeId); // D-3
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine2027.evaluate(LocalDate.of(2027, 8, 14)).themeId); // D-1
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine2027.evaluate(LocalDate.of(2027, 8, 15)).themeId); // D
    }

    // 22. Independence Day vs Raksha Bandhan 2027 Overlap Precedence
    @Test
    public void testIndependenceDayRakshaBandhan2027Precedence() {
        // Independence Day 2027: Aug 15 (Window: Aug 12 - Aug 15, Priority 80)
        // Raksha Bandhan 2027: Aug 17 (Window: Aug 14 - Aug 17, Priority 65)
        FestivalThemeEngine engine = createEngine(LocalDate.of(2027, 8, 1));

        // Aug 12: Independence Day D-3 (Raksha Bandhan not yet active)
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine.evaluate(LocalDate.of(2027, 8, 12)).themeId);
        // Aug 13: Independence Day D-2
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, engine.evaluate(LocalDate.of(2027, 8, 13)).themeId);
        // Aug 14: Overlap day! Independence Day (D-1, Priority 80) vs Raksha Bandhan (D-3, Priority 65) -> Independence Day
        FestivalThemeEngine.EvaluationResult resAug14 = engine.evaluate(LocalDate.of(2027, 8, 14));
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, resAug14.themeId);
        assertEquals("independence_day", resAug14.activeFestival.id);
        // Aug 15: Overlap day! Independence Day (D, Priority 80) vs Raksha Bandhan (D-2, Priority 65) -> Independence Day
        FestivalThemeEngine.EvaluationResult resAug15 = engine.evaluate(LocalDate.of(2027, 8, 15));
        assertEquals(ThemeManager.THEME_INDEPENDENCE_DAY, resAug15.themeId);
        assertEquals("independence_day", resAug15.activeFestival.id);
        // Aug 16: Independence Day window has ended (D+1). Raksha Bandhan is D-1 -> Raksha Bandhan
        FestivalThemeEngine.EvaluationResult resAug16 = engine.evaluate(LocalDate.of(2027, 8, 16));
        assertEquals(ThemeManager.THEME_RAKSHA_BANDHAN, resAug16.themeId);
        assertEquals("raksha_bandhan", resAug16.activeFestival.id);
        // Aug 17: Raksha Bandhan festival day (D)
        FestivalThemeEngine.EvaluationResult resAug17 = engine.evaluate(LocalDate.of(2027, 8, 17));
        assertEquals(ThemeManager.THEME_RAKSHA_BANDHAN, resAug17.themeId);
        assertEquals("raksha_bandhan", resAug17.activeFestival.id);
        // Aug 18: Raksha Bandhan window ends (D+1) -> OLED Black
        assertEquals(ThemeManager.THEME_DARK, engine.evaluate(LocalDate.of(2027, 8, 18)).themeId);
    }
}
