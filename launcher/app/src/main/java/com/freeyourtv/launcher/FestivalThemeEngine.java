package com.freeyourtv.launcher;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Core business engine managing automatic Indian festival theme selection,
 * activation windows, manual user overrides, suspension tracking, and offline operation.
 * Completely decoupled from Android UI classes for fast, deterministic unit testing.
 */
public final class FestivalThemeEngine {

    public static final String KEY_FESTIVAL_AUTO = "festival_auto_enabled";
    public static final String KEY_MANUAL_OVERRIDE = "festival_manual_override";
    public static final String KEY_OVERRIDE_ACTIVE_FESTIVAL_ID = "festival_override_active_festival_id";
    public static final String KEY_THEME = "current_theme";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public interface Store {
        boolean getBoolean(String key, boolean defValue);
        void putBoolean(String key, boolean value);
        String getString(String key, String defValue);
        void putString(String key, String value);
    }

    public enum Mode {
        /** Automatic festival calendar is actively determining the theme. */
        AUTO,
        /** Automation is suspended because user manually selected a theme; resumes at next festival. */
        MANUAL_SUSPENDED,
        /** Automation is permanently turned off by the user. */
        DISABLED
    }

    public static final class EvaluationResult {
        public final String themeId;
        public final Festival activeFestival;
        public final Festival nextFestival;
        public final Mode mode;
        public final String statusSummary;

        public EvaluationResult(String themeId, Festival activeFestival, Festival nextFestival, Mode mode, String statusSummary) {
            this.themeId = themeId;
            this.activeFestival = activeFestival;
            this.nextFestival = nextFestival;
            this.mode = mode;
            this.statusSummary = statusSummary;
        }

        @Override
        public String toString() {
            return "EvaluationResult{" +
                    "themeId='" + themeId + '\'' +
                    ", active=" + (activeFestival != null ? activeFestival.name : "none") +
                    ", next=" + (nextFestival != null ? nextFestival.name : "none") +
                    ", mode=" + mode +
                    ", summary='" + statusSummary + '\'' +
                    '}';
        }
    }

    private final Store store;
    private final DateProvider dateProvider;

    public FestivalThemeEngine(Store store, DateProvider dateProvider) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.dateProvider = Objects.requireNonNull(dateProvider, "dateProvider must not be null");
    }

    public boolean isAutoEnabled() {
        return store.getBoolean(KEY_FESTIVAL_AUTO, true);
    }

    public boolean isManualOverride() {
        return store.getBoolean(KEY_MANUAL_OVERRIDE, false);
    }

    public String getOverrideActiveFestivalId() {
        return store.getString(KEY_OVERRIDE_ACTIVE_FESTIVAL_ID, "");
    }

    /**
     * Resolves the effective date: explicit test date > real date.
     */
    public LocalDate resolveEffectiveDate(LocalDate testDate) {
        if (testDate != null) return testDate;
        return dateProvider.now();
    }

    /**
     * Evaluates the appropriate theme for the provided date (or today/simulated if null).
     */
    public EvaluationResult evaluate(LocalDate testDate) {
        LocalDate today = resolveEffectiveDate(testDate);

        boolean autoEnabled = store.getBoolean(KEY_FESTIVAL_AUTO, true);
        Festival active = FestivalCalendar.getPrimaryActiveFestival(today);
        Festival next = FestivalCalendar.getNextFestival(today);

        if (!autoEnabled) {
            String current = store.getString(KEY_THEME, ThemeManager.THEME_DARK);
            return new EvaluationResult(current, active, next, Mode.DISABLED, "Festival automation disabled");
        }

        boolean manualOverride = store.getBoolean(KEY_MANUAL_OVERRIDE, false);
        if (manualOverride) {
            String activeAtOverride = store.getString(KEY_OVERRIDE_ACTIVE_FESTIVAL_ID, "");

            // Has the NEXT festival window with a dedicated theme arrived?
            // Un-themed festivals (which fall back to OLED Black) do not silently overwrite manual user themes.
            boolean nextWindowArrived = false;
            if (activeAtOverride.isEmpty()) {
                // User overrode when no festival was active; arrival of a festival with dedicated artwork resumes auto
                if (active != null && active.hasDedicatedTheme()) {
                    nextWindowArrived = true;
                }
            } else {
                // User overrode during a festival; arrival of a DIFFERENT festival with dedicated artwork resumes auto
                if (active != null && active.hasDedicatedTheme() && !active.id.equals(activeAtOverride)) {
                    nextWindowArrived = true;
                }
            }

            if (nextWindowArrived) {
                // Clear suspension and resume automatic mode
                store.putBoolean(KEY_MANUAL_OVERRIDE, false);
                store.putString(KEY_OVERRIDE_ACTIVE_FESTIVAL_ID, "");
                manualOverride = false;
            } else {
                // Still suspended on manual theme
                String current = store.getString(KEY_THEME, ThemeManager.THEME_DARK);

                // Find next upcoming festival with a dedicated theme
                Festival nextThemed = null;
                for (Festival f : FestivalCalendar.getAllFestivals()) {
                    if (!f.verified || !f.hasDedicatedTheme()) continue;
                    if (f.getStartDate().isAfter(today)) {
                        if (nextThemed == null || f.getStartDate().isBefore(nextThemed.getStartDate())) {
                            nextThemed = f;
                        }
                    }
                }

                String resumeInfo = (nextThemed != null)
                        ? "Resumes on " + nextThemed.name + " (" + nextThemed.getStartDate().format(DATE_FMT) + ")"
                        : (next != null ? "Resumes next festival (" + next.name + ")" : "Resumes next festival");
                return new EvaluationResult(current, active, next, Mode.MANUAL_SUSPENDED,
                        "Manual theme override active. " + resumeInfo);
            }
        }

        // Automatic mode active
        String targetThemeId;
        String summary;
        if (active != null) {
            if (active.hasDedicatedTheme()) {
                targetThemeId = active.designatedThemeId;
                summary = "Active festival: " + active.name + " (" +
                        active.getStartDate().format(DATE_FMT) + " â€“ " + active.getEndDate().format(DATE_FMT) + ")";
            } else {
                // Standard fallback outside festivals or for festivals without dedicated artwork
                targetThemeId = ThemeManager.THEME_DARK;
                summary = "Active festival: " + active.name + " (" +
                        active.getStartDate().format(DATE_FMT) + " â€“ " + active.getEndDate().format(DATE_FMT) + ") [Theme: OLED Black]";
            }
        } else {
            targetThemeId = ThemeManager.THEME_DARK;
            if (next != null) {
                summary = "Next: " + next.name + " (" +
                        next.getStartDate().format(DATE_FMT) + " â€“ " + next.getEndDate().format(DATE_FMT) + ")";
            } else {
                summary = "No upcoming festivals scheduled this year";
            }
        }

        store.putString(KEY_THEME, targetThemeId);
        return new EvaluationResult(targetThemeId, active, next, Mode.AUTO, summary);
    }

    /**
     * User explicitly chose a manual theme.
     * If auto mode is enabled, this suspends auto changes until the next festival window.
     */
    public EvaluationResult setManualTheme(String themeId, LocalDate today) {
        LocalDate date = resolveEffectiveDate(today);
        if (isAutoEnabled()) {
            store.putBoolean(KEY_MANUAL_OVERRIDE, true);
            Festival active = FestivalCalendar.getPrimaryActiveFestival(date);
            store.putString(KEY_OVERRIDE_ACTIVE_FESTIVAL_ID, active != null ? active.id : "");
        }
        store.putString(KEY_THEME, themeId);
        return evaluate(date);
    }

    /**
     * User explicitly requested to resume automatic festival theme selection.
     */
    public EvaluationResult resumeAuto(LocalDate today) {
        LocalDate date = resolveEffectiveDate(today);
        store.putBoolean(KEY_FESTIVAL_AUTO, true);
        store.putBoolean(KEY_MANUAL_OVERRIDE, false);
        store.putString(KEY_OVERRIDE_ACTIVE_FESTIVAL_ID, "");
        return evaluate(date);
    }

    /**
     * User toggled festival automation ON or OFF.
     */
    public EvaluationResult setAutoEnabled(boolean enabled, LocalDate today) {
        LocalDate date = resolveEffectiveDate(today);
        store.putBoolean(KEY_FESTIVAL_AUTO, enabled);
        if (enabled) {
            store.putBoolean(KEY_MANUAL_OVERRIDE, false);
            store.putString(KEY_OVERRIDE_ACTIVE_FESTIVAL_ID, "");
        }
        return evaluate(date);
    }
}
