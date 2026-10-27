package com.freeyourtv.launcher;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Bundled offline Pan-India Festival Calendar covering verified dates for 2025, 2026, and 2027.
 *
 * Sourced and cross-verified from:
 * 1. Ministry of Personnel, Public Grievances and Pensions (Department of Personnel and Training - DoPT),
 *    Government of India Gazetted Holiday schedules.
 * 2. Positional Astronomy Centre, India Meteorological Department (IMD) / National Indian Calendar.
 * 3. Drik Panchang Hindu Lunisolar Astronomical Tables.
 *
 * Theme mappings:
 * - Dedicated custom artwork and coordinated color palettes for all 22 Pan-India festivals.
 * - Outside festival activation windows, the launcher defaults cleanly to ThemeManager.THEME_DARK (OLED Black).
 */
public final class FestivalCalendar {

    private static final List<Festival> FESTIVALS;

    static {
        List<Festival> list = new ArrayList<>();

        // -----------------------------------------------------------------------------------------
        // 2025 FESTIVALS (Officially verified against DoPT F.No.12/2/2023-JCA dated 09.07.2024)
        // -----------------------------------------------------------------------------------------
        list.add(new Festival("makar_sankranti", "Makar Sankranti / Pongal", LocalDate.of(2025, 1, 14), ThemeManager.THEME_MAKAR_SANKRANTI, 50));
        list.add(new Festival("republic_day", "Republic Day", LocalDate.of(2025, 1, 26), ThemeManager.THEME_REPUBLIC_DAY, 80));
        list.add(new Festival("maha_shivratri", "Maha Shivratri", LocalDate.of(2025, 2, 26), ThemeManager.THEME_MAHA_SHIVRATRI, 60));
        list.add(new Festival("holi", "Holi", LocalDate.of(2025, 3, 14), ThemeManager.THEME_HOLI, 70));
        list.add(new Festival("ugadi", "Ugadi / Gudi Padwa", LocalDate.of(2025, 3, 30), ThemeManager.THEME_UGADI, 50));
        list.add(new Festival("ram_navami", "Ram Navami", LocalDate.of(2025, 4, 6), ThemeManager.THEME_RAM_NAVAMI, 60));
        list.add(new Festival("mahavir_jayanti", "Mahavir Jayanti", LocalDate.of(2025, 4, 10), ThemeManager.THEME_MAHAVIR_JAYANTI, 50));
        list.add(new Festival("baisakhi", "Baisakhi / Vishu / Puthandu", LocalDate.of(2025, 4, 14), ThemeManager.THEME_BAISAKHI, 55));
        list.add(new Festival("buddha_purnima", "Buddha Purnima", LocalDate.of(2025, 5, 12), ThemeManager.THEME_BUDDHA_PURNIMA, 50));
        list.add(new Festival("rath_yatra", "Rath Yatra", LocalDate.of(2025, 6, 27), ThemeManager.THEME_RATH_YATRA, 55));
        list.add(new Festival("independence_day", "Independence Day", LocalDate.of(2025, 8, 15), ThemeManager.THEME_INDEPENDENCE_DAY, 80));
        list.add(new Festival("raksha_bandhan", "Raksha Bandhan", LocalDate.of(2025, 8, 9), ThemeManager.THEME_RAKSHA_BANDHAN, 65));
        // Delhi observance: Central Govt holiday in Delhi was Saturday, 16 Aug 2025 (DoPT Annexure I #11)
        list.add(new Festival("krishna_janmashtami", "Krishna Janmashtami", LocalDate.of(2025, 8, 16), ThemeManager.THEME_KRISHNA_JANMAASHTAMI, 70));
        list.add(new Festival("ganesh_chaturthi", "Ganesh Chaturthi", LocalDate.of(2025, 8, 27), ThemeManager.THEME_GANESH_CHATURTHI, 70));
        list.add(new Festival("onam", "Onam (Thiruvonam)", LocalDate.of(2025, 9, 5), ThemeManager.THEME_ONAM, 60));
        list.add(new Festival("navratri", "Sharad Navratri", LocalDate.of(2025, 9, 22), ThemeManager.THEME_NAVRATRI, 65));
        list.add(new Festival("dussehra", "Dussehra / Vijayadashami", LocalDate.of(2025, 10, 2), ThemeManager.THEME_DUSSEHRA, 90));
        list.add(new Festival("karwa_chauth", "Karwa Chauth", LocalDate.of(2025, 10, 10), ThemeManager.THEME_KARWA_CHAUTH, 50));
        list.add(new Festival("diwali", "Diwali", LocalDate.of(2025, 10, 20), ThemeManager.THEME_DIWALI, 100));
        list.add(new Festival("govardhan_puja", "Govardhan Puja", LocalDate.of(2025, 10, 22), ThemeManager.THEME_GOVARDHAN_PUJA, 45));
        list.add(new Festival("bhai_dooj", "Bhai Dooj", LocalDate.of(2025, 10, 23), ThemeManager.THEME_BHAI_DOOJ, 45));
        list.add(new Festival("chhath_puja", "Chhath Puja", LocalDate.of(2025, 10, 28), ThemeManager.THEME_CHHATH_PUJA, 60));
        list.add(new Festival("guru_nanak_jayanti", "Guru Nanak Jayanti", LocalDate.of(2025, 11, 5), ThemeManager.THEME_GURU_NANAK_JAYANTI, 60));
        list.add(new Festival("christmas", "Christmas", LocalDate.of(2025, 12, 25), ThemeManager.THEME_CHRISTMAS, 50));

        // -----------------------------------------------------------------------------------------
        // 2026 FESTIVALS (Current Year; Officially verified against DoPT F.No.12/2/2023-JCA dated 03.07.2025)
        // -----------------------------------------------------------------------------------------
        list.add(new Festival("makar_sankranti", "Makar Sankranti / Pongal", LocalDate.of(2026, 1, 14), ThemeManager.THEME_MAKAR_SANKRANTI, 50));
        list.add(new Festival("republic_day", "Republic Day", LocalDate.of(2026, 1, 26), ThemeManager.THEME_REPUBLIC_DAY, 80));
        list.add(new Festival("maha_shivratri", "Maha Shivratri", LocalDate.of(2026, 2, 15), ThemeManager.THEME_MAHA_SHIVRATRI, 60));
        list.add(new Festival("holi", "Holi", LocalDate.of(2026, 3, 4), ThemeManager.THEME_HOLI, 70));
        list.add(new Festival("ugadi", "Ugadi / Gudi Padwa", LocalDate.of(2026, 3, 19), ThemeManager.THEME_UGADI, 50));
        // Delhi observance: Thursday, 26 Mar 2026 (DoPT Gazetted Annexure I #4)
        list.add(new Festival("ram_navami", "Ram Navami", LocalDate.of(2026, 3, 26), ThemeManager.THEME_RAM_NAVAMI, 60));
        list.add(new Festival("mahavir_jayanti", "Mahavir Jayanti", LocalDate.of(2026, 3, 31), ThemeManager.THEME_MAHAVIR_JAYANTI, 50));
        list.add(new Festival("baisakhi", "Baisakhi / Vishu / Puthandu", LocalDate.of(2026, 4, 14), ThemeManager.THEME_BAISAKHI, 55));
        list.add(new Festival("buddha_purnima", "Buddha Purnima", LocalDate.of(2026, 5, 1), ThemeManager.THEME_BUDDHA_PURNIMA, 50));
        list.add(new Festival("rath_yatra", "Rath Yatra", LocalDate.of(2026, 7, 16), ThemeManager.THEME_RATH_YATRA, 55));
        list.add(new Festival("independence_day", "Independence Day", LocalDate.of(2026, 8, 15), ThemeManager.THEME_INDEPENDENCE_DAY, 80));
        list.add(new Festival("onam", "Onam (Thiruvonam)", LocalDate.of(2026, 8, 26), ThemeManager.THEME_ONAM, 60));
        list.add(new Festival("raksha_bandhan", "Raksha Bandhan", LocalDate.of(2026, 8, 28), ThemeManager.THEME_RAKSHA_BANDHAN, 65));
        list.add(new Festival("krishna_janmashtami", "Krishna Janmashtami", LocalDate.of(2026, 9, 4), ThemeManager.THEME_KRISHNA_JANMAASHTAMI, 70));
        list.add(new Festival("ganesh_chaturthi", "Ganesh Chaturthi", LocalDate.of(2026, 9, 14), ThemeManager.THEME_GANESH_CHATURTHI, 70));
        list.add(new Festival("navratri", "Sharad Navratri", LocalDate.of(2026, 10, 11), ThemeManager.THEME_NAVRATRI, 65));
        list.add(new Festival("dussehra", "Dussehra / Vijayadashami", LocalDate.of(2026, 10, 20), ThemeManager.THEME_DUSSEHRA, 90));
        list.add(new Festival("karwa_chauth", "Karwa Chauth", LocalDate.of(2026, 10, 29), ThemeManager.THEME_KARWA_CHAUTH, 50));
        list.add(new Festival("diwali", "Diwali", LocalDate.of(2026, 11, 8), ThemeManager.THEME_DIWALI, 100));
        // Delhi observance: Monday, 9 Nov 2026 (DoPT Annexure II)
        list.add(new Festival("govardhan_puja", "Govardhan Puja", LocalDate.of(2026, 11, 9), ThemeManager.THEME_GOVARDHAN_PUJA, 45));
        list.add(new Festival("bhai_dooj", "Bhai Dooj", LocalDate.of(2026, 11, 11), ThemeManager.THEME_BHAI_DOOJ, 45));
        list.add(new Festival("chhath_puja", "Chhath Puja", LocalDate.of(2026, 11, 15), ThemeManager.THEME_CHHATH_PUJA, 60));
        list.add(new Festival("guru_nanak_jayanti", "Guru Nanak Jayanti", LocalDate.of(2026, 11, 24), ThemeManager.THEME_GURU_NANAK_JAYANTI, 60));
        list.add(new Festival("christmas", "Christmas", LocalDate.of(2026, 12, 25), ThemeManager.THEME_CHRISTMAS, 50));

        // -----------------------------------------------------------------------------------------
        // 2027 FESTIVALS (Cross-verified against Drik Panchang Delhi / National Indian Calendar)
        // -----------------------------------------------------------------------------------------
        list.add(new Festival("makar_sankranti", "Makar Sankranti / Pongal", LocalDate.of(2027, 1, 15), ThemeManager.THEME_MAKAR_SANKRANTI, 50));
        list.add(new Festival("republic_day", "Republic Day", LocalDate.of(2027, 1, 26), ThemeManager.THEME_REPUBLIC_DAY, 80));
        list.add(new Festival("maha_shivratri", "Maha Shivratri", LocalDate.of(2027, 3, 6), ThemeManager.THEME_MAHA_SHIVRATRI, 60));
        list.add(new Festival("holi", "Holi", LocalDate.of(2027, 3, 22), ThemeManager.THEME_HOLI, 70));
        list.add(new Festival("ugadi", "Ugadi / Gudi Padwa", LocalDate.of(2027, 4, 7), ThemeManager.THEME_UGADI, 50));
        list.add(new Festival("ram_navami", "Ram Navami", LocalDate.of(2027, 4, 15), ThemeManager.THEME_RAM_NAVAMI, 60));
        list.add(new Festival("mahavir_jayanti", "Mahavir Jayanti", LocalDate.of(2027, 4, 19), ThemeManager.THEME_MAHAVIR_JAYANTI, 50));
        list.add(new Festival("baisakhi", "Baisakhi / Vishu / Puthandu", LocalDate.of(2027, 4, 14), ThemeManager.THEME_BAISAKHI, 55));
        list.add(new Festival("buddha_purnima", "Buddha Purnima", LocalDate.of(2027, 5, 20), ThemeManager.THEME_BUDDHA_PURNIMA, 50));
        list.add(new Festival("rath_yatra", "Rath Yatra", LocalDate.of(2027, 7, 5), ThemeManager.THEME_RATH_YATRA, 55));
        list.add(new Festival("independence_day", "Independence Day", LocalDate.of(2027, 8, 15), ThemeManager.THEME_INDEPENDENCE_DAY, 80));
        list.add(new Festival("raksha_bandhan", "Raksha Bandhan", LocalDate.of(2027, 8, 17), ThemeManager.THEME_RAKSHA_BANDHAN, 65));
        list.add(new Festival("krishna_janmashtami", "Krishna Janmashtami", LocalDate.of(2027, 8, 25), ThemeManager.THEME_KRISHNA_JANMAASHTAMI, 70));
        list.add(new Festival("ganesh_chaturthi", "Ganesh Chaturthi", LocalDate.of(2027, 9, 4), ThemeManager.THEME_GANESH_CHATURTHI, 70));
        list.add(new Festival("onam", "Onam (Thiruvonam)", LocalDate.of(2027, 9, 12), ThemeManager.THEME_ONAM, 60));
        list.add(new Festival("navratri", "Sharad Navratri", LocalDate.of(2027, 9, 30), ThemeManager.THEME_NAVRATRI, 65));
        list.add(new Festival("dussehra", "Dussehra / Vijayadashami", LocalDate.of(2027, 10, 9), ThemeManager.THEME_DUSSEHRA, 90));
        list.add(new Festival("karwa_chauth", "Karwa Chauth", LocalDate.of(2027, 10, 18), ThemeManager.THEME_KARWA_CHAUTH, 50));
        list.add(new Festival("diwali", "Diwali", LocalDate.of(2027, 10, 29), ThemeManager.THEME_DIWALI, 100));
        list.add(new Festival("govardhan_puja", "Govardhan Puja", LocalDate.of(2027, 10, 30), ThemeManager.THEME_GOVARDHAN_PUJA, 45));
        list.add(new Festival("bhai_dooj", "Bhai Dooj", LocalDate.of(2027, 10, 31), ThemeManager.THEME_BHAI_DOOJ, 45));
        list.add(new Festival("chhath_puja", "Chhath Puja", LocalDate.of(2027, 11, 4), ThemeManager.THEME_CHHATH_PUJA, 60));
        list.add(new Festival("guru_nanak_jayanti", "Guru Nanak Jayanti", LocalDate.of(2027, 11, 14), ThemeManager.THEME_GURU_NANAK_JAYANTI, 60));
        list.add(new Festival("christmas", "Christmas", LocalDate.of(2027, 12, 25), ThemeManager.THEME_CHRISTMAS, 50));

        FESTIVALS = Collections.unmodifiableList(list);
    }

    private FestivalCalendar() {}

    /**
     * Returns an unmodifiable list of all bundled festival entries across supported years.
     */
    public static List<Festival> getAllFestivals() {
        return FESTIVALS;
    }

    /**
     * Returns all festivals for a specific year, ordered chronologically.
     */
    public static List<Festival> getFestivalsForYear(int year) {
        List<Festival> result = new ArrayList<>();
        for (Festival f : FESTIVALS) {
            if (f.date.getYear() == year) {
                result.add(f);
            }
        }
        result.sort(Comparator.comparing(f -> f.date));
        return result;
    }

    /**
     * Comparator defining deterministic precedence when multiple festival windows overlap:
     * 1. Dedicated custom visual theme (e.g. Diwali / Dussehra) beats standard fallback theme.
     * 2. Proximity to festival day (smaller non-negative daysUntilFestival beats larger).
     * 3. Priority integer (higher beats lower).
     * 4. Alphabetical tie-break on festival ID.
     */
    public static Comparator<Festival> createActiveFestivalComparator(LocalDate targetDate) {
        return (a, b) -> {
            boolean aTheme = a.hasDedicatedTheme();
            boolean bTheme = b.hasDedicatedTheme();
            if (aTheme != bTheme) {
                return aTheme ? -1 : 1;
            }

            long aDist = a.daysUntilFestival(targetDate);
            long bDist = b.daysUntilFestival(targetDate);
            if (aDist != bDist) {
                return Long.compare(aDist, bDist);
            }

            if (a.priority != b.priority) {
                return Integer.compare(b.priority, a.priority);
            }

            return a.id.compareTo(b.id);
        };
    }

    /**
     * Returns all festivals whose active window [D - 3, D] covers targetDate,
     * sorted by precedence (highest priority first).
     */
    public static List<Festival> getActiveFestivals(LocalDate targetDate) {
        if (targetDate == null) return Collections.emptyList();
        List<Festival> active = new ArrayList<>();
        for (Festival f : FESTIVALS) {
            if (f.isActiveOn(targetDate)) {
                active.add(f);
            }
        }
        if (active.size() > 1) {
            active.sort(createActiveFestivalComparator(targetDate));
        }
        return active;
    }

    /**
     * Returns the highest-precedence active festival on targetDate, or null if no festival is active.
     */
    public static Festival getPrimaryActiveFestival(LocalDate targetDate) {
        List<Festival> active = getActiveFestivals(targetDate);
        return active.isEmpty() ? null : active.get(0);
    }

    /**
     * Returns the next upcoming festival scheduled after targetDate.
     * If a festival is currently active, returns the next festival whose start date is after targetDate.
     * If no festival is active, returns the nearest upcoming festival whose start date is on or after targetDate.
     */
    public static Festival getNextFestival(LocalDate targetDate) {
        if (targetDate == null) return null;
        Festival active = getPrimaryActiveFestival(targetDate);
        Festival nearest = null;

        for (Festival f : FESTIVALS) {
            if (!f.verified) continue;
            boolean eligible;
            if (active != null) {
                // Must be a festival starting strictly after today
                eligible = f.getStartDate().isAfter(targetDate);
            } else {
                // Must be upcoming (start date today or later, or date today or later)
                eligible = !f.getStartDate().isBefore(targetDate);
            }

            if (eligible) {
                if (nearest == null || f.getStartDate().isBefore(nearest.getStartDate())) {
                    nearest = f;
                } else if (f.getStartDate().isEqual(nearest.getStartDate()) && f.priority > nearest.priority) {
                    nearest = f;
                }
            }
        }
        return nearest;
    }
}
