package com.freeyourtv.launcher;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Provides the current date. Allows injecting fixed dates for deterministic testing.
 */
public interface DateProvider {

    LocalDate now();

    ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    /**
     * Standard provider operating in Indian Standard Time (Asia/Kolkata).
     */
    DateProvider KOLKATA = () -> LocalDate.now(ZONE_KOLKATA);
}
