package com.freeyourtv.launcher;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents a single festival occurrence on a specific calendar date D.
 * The festival window activates on (D - 3) calendar days and remains active through D inclusive.
 * On (D + 1), the festival window ends.
 */
public final class Festival {

    public final String id;
    public final String name;
    public final LocalDate date;
    public final String designatedThemeId; // null or ThemeManager.THEME_*
    public final int priority;
    public final boolean verified;

    public Festival(String id, String name, LocalDate date, String designatedThemeId, int priority, boolean verified) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.designatedThemeId = designatedThemeId;
        this.priority = priority;
        this.verified = verified;
    }

    public Festival(String id, String name, LocalDate date, String designatedThemeId, int priority) {
        this(id, name, date, designatedThemeId, priority, true);
    }

    /**
     * Start of activation window: D - 3 calendar days.
     */
    public LocalDate getStartDate() {
        return date.minusDays(3);
    }

    /**
     * End of activation window: D (inclusive).
     */
    public LocalDate getEndDate() {
        return date;
    }

    /**
     * Checks if targetDate falls within [D - 3, D] inclusive.
     * Unverified festivals never activate automatically.
     */
    public boolean isActiveOn(LocalDate targetDate) {
        if (!verified || targetDate == null) return false;
        LocalDate start = getStartDate();
        LocalDate end = getEndDate();
        return !targetDate.isBefore(start) && !targetDate.isAfter(end);
    }

    /**
     * Days remaining until the main festival day D.
     * Can be negative if targetDate is after D.
     */
    public long daysUntilFestival(LocalDate targetDate) {
        if (targetDate == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(targetDate, date);
    }

    /**
     * True if this festival has a designated dedicated visual theme (e.g., Diwali or Dussehra),
     * rather than falling back to the standard OLED Black theme.
     */
    public boolean hasDedicatedTheme() {
        return designatedThemeId != null && !designatedThemeId.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Festival)) return false;
        Festival festival = (Festival) o;
        return priority == festival.priority &&
                id.equals(festival.id) &&
                date.equals(festival.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, date, priority);
    }

    @Override
    public String toString() {
        return "Festival{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", date=" + date +
                ", theme='" + designatedThemeId + '\'' +
                ", window=[" + getStartDate() + ".." + getEndDate() + "]" +
                '}';
    }
}
