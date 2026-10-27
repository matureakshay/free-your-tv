package com.freeyourtv.cleaner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Core cleanup decision and memory reclamation logic.
 * Decoupled from Android UI classes for fast, deterministic unit testing.
 */
public final class CleanerEngine {

    public interface SystemMemoryProvider {
        long getAvailableMemoryBytes();
        long getTotalMemoryBytes();
        List<String> getInstalledPackages();
        void killBackgroundProcesses(String packageName);
    }

    public static final class CleanResult {
        public final long initialAvailableBytes;
        public final long finalAvailableBytes;
        public final long totalBytes;
        public final long freedBytes;
        public final int killedCandidateCount;
        public final String title;
        public final String subtitle;

        public CleanResult(long initialAvailableBytes, long finalAvailableBytes, long totalBytes,
                           long freedBytes, int killedCandidateCount, String title, String subtitle) {
            this.initialAvailableBytes = initialAvailableBytes;
            this.finalAvailableBytes = finalAvailableBytes;
            this.totalBytes = totalBytes;
            this.freedBytes = freedBytes;
            this.killedCandidateCount = killedCandidateCount;
            this.title = title;
            this.subtitle = subtitle;
        }

        @Override
        public String toString() {
            return "CleanResult{" +
                    "freed=" + freedBytes +
                    ", finalAvailable=" + finalAvailableBytes +
                    ", candidates=" + killedCandidateCount +
                    ", title='" + title + '\'' +
                    '}';
        }
    }

    /**
     * Protected packages that must never be terminated.
     * Includes the custom launcher, the cleaner itself, Button Mapper, and essential system components.
     */
    private static final Set<String> PROTECTED_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.freeyourtv.launcher",       // Custom Android TV launcher
            "com.freeyourtv.cleaner",        // Self (RAM cleaner app)
            "flar2.homebutton",              // Button Mapper service
            "android",                       // Android core framework
            "com.android.systemui",          // TV System UI
            "com.android.settings",          // Device Settings
            "com.android.tv.settings",       // TV Settings
            "com.android.providers.settings",// Settings provider
            "com.android.inputmethod.latin", // Keyboard / IME
            "com.google.android.inputmethod.latin",
            "com.google.android.gms",        // Google Play Services
            "com.google.android.gsf",        // Google Services Framework
            "com.google.android.katniss",    // TV Voice search / Assistant
            "com.mediatek.tv.service",       // MediaTek TV backend services
            "com.mediatek.wwtv.tvcenter"     // TV Input / Tuner core
    )));

    private final SystemMemoryProvider provider;

    public CleanerEngine(SystemMemoryProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("SystemMemoryProvider must not be null");
        }
        this.provider = provider;
    }

    /**
     * Checks if a package is protected against background process termination.
     */
    public boolean isPackageProtected(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return true;
        }
        String pkg = packageName.trim();
        if (PROTECTED_PACKAGES.contains(pkg)) {
            return true;
        }
        // Protect system framework, input method services, and hardware daemon prefixes
        if (pkg.startsWith("android.") || pkg.startsWith("com.android.server.") ||
                pkg.startsWith("com.android.keyguard") || pkg.startsWith("com.android.bluetooth")) {
            return true;
        }
        return false;
    }

    /**
     * Executes background process termination for all non-protected installed packages
     * and calculates genuine memory delta.
     */
    public CleanResult performCleanup() {
        long initialAvail = provider.getAvailableMemoryBytes();
        long total = provider.getTotalMemoryBytes();

        List<String> installed = provider.getInstalledPackages();
        int candidatesCount = 0;

        if (installed != null) {
            for (String pkg : installed) {
                if (!isPackageProtected(pkg)) {
                    provider.killBackgroundProcesses(pkg);
                    candidatesCount++;
                }
            }
        }

        // Measure post-cleanup available memory
        long finalAvail = provider.getAvailableMemoryBytes();
        long freed = finalAvail - initialAvail;

        String title;
        String subtitle;

        if (freed > 0) {
            title = "Freed " + formatBytes(freed) + " RAM";
            subtitle = formatBytes(finalAvail) + " Available • " + formatBytes(total) + " Total";
        } else {
            // Truthful non-misleading report when RAM was already optimal or no cached memory was freed
            title = "RAM Already Optimal";
            subtitle = formatBytes(finalAvail) + " Available • " + formatBytes(total) + " Total";
        }

        return new CleanResult(initialAvail, finalAvail, total, Math.max(0, freed), candidatesCount, title, subtitle);
    }

    /**
     * Formats bytes to MB or GB without excessive precision.
     */
    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        long mb = bytes / (1024 * 1024);
        if (mb >= 1000) {
            double gb = (double) bytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.ENGLISH, "%.1f GB", gb);
        }
        return mb + " MB";
    }
}
