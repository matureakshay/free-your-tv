package com.freeyourtv.cleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for CleanerEngine: package protection, genuine memory reclamation calculation,
 * truthful non-misleading reporting, and byte formatting.
 */
public class CleanerEngineTest {

    private static class MockMemoryProvider implements CleanerEngine.SystemMemoryProvider {
        long initialAvail;
        long finalAvail;
        long total;
        List<String> installed = new ArrayList<>();
        List<String> killed = new ArrayList<>();

        MockMemoryProvider(long initialAvail, long finalAvail, long total) {
            this.initialAvail = initialAvail;
            this.finalAvail = finalAvail;
            this.total = total;
        }

        private boolean firstCall = true;

        @Override
        public long getAvailableMemoryBytes() {
            if (firstCall) {
                firstCall = false;
                return initialAvail;
            }
            return finalAvail;
        }

        @Override
        public long getTotalMemoryBytes() {
            return total;
        }

        @Override
        public List<String> getInstalledPackages() {
            return installed;
        }

        @Override
        public void killBackgroundProcesses(String packageName) {
            killed.add(packageName);
        }
    }

    @Test
    public void testProtectedPackagesWhitelist() {
        MockMemoryProvider mock = new MockMemoryProvider(100, 100, 1000);
        CleanerEngine engine = new CleanerEngine(mock);

        // Core system & critical launcher components must be protected
        assertTrue("Custom launcher must be protected", engine.isPackageProtected("com.freeyourtv.launcher"));
        assertTrue("Cleaner self must be protected", engine.isPackageProtected("com.freeyourtv.cleaner"));
        assertFalse("Obsolete launcher package must not be protected", engine.isPackageProtected("com.gathin.tvhome"));
        assertFalse("Obsolete cleaner package must not be protected", engine.isPackageProtected("com.gathin.tvcleaner"));
        assertTrue("Button Mapper must be protected", engine.isPackageProtected("flar2.homebutton"));
        assertTrue("System framework must be protected", engine.isPackageProtected("android"));
        assertTrue("System UI must be protected", engine.isPackageProtected("com.android.systemui"));
        assertTrue("Settings must be protected", engine.isPackageProtected("com.android.settings"));
        assertTrue("TV Settings must be protected", engine.isPackageProtected("com.android.tv.settings"));
        assertTrue("Settings provider must be protected", engine.isPackageProtected("com.android.providers.settings"));
        assertTrue("MediaTek TV service must be protected", engine.isPackageProtected("com.mediatek.tv.service"));

        // Null or empty safe fallback
        assertTrue(engine.isPackageProtected(null));
        assertTrue(engine.isPackageProtected(""));
        assertTrue(engine.isPackageProtected("   "));
    }

    @Test
    public void testNonProtectedThirdPartyPackagesAllowed() {
        MockMemoryProvider mock = new MockMemoryProvider(100, 100, 1000);
        CleanerEngine engine = new CleanerEngine(mock);

        // Ordinary user/third-party apps can be targeted for background process killing
        assertFalse(engine.isPackageProtected("com.netflix.ninja"));
        assertFalse(engine.isPackageProtected("in.startv.hotstar"));
        assertFalse(engine.isPackageProtected("com.spotify.tv.android"));
        assertFalse(engine.isPackageProtected("com.lagradost.cloudstream3.prerelease"));
        assertFalse(engine.isPackageProtected("io.gh.reisxd.tizentube.cobalt"));
    }

    @Test
    public void testCleanupWithGenuineMemoryReclamation() {
        long MB = 1024 * 1024L;
        long initialAvail = 300 * MB;
        long finalAvail = 348 * MB; // 48 MB freed
        long totalMem = 1024 * MB;

        MockMemoryProvider mock = new MockMemoryProvider(initialAvail, finalAvail, totalMem);
        mock.installed.addAll(Arrays.asList(
                "com.freeyourtv.launcher",       // Protected
                "flar2.homebutton",              // Protected
                "com.netflix.ninja",             // Candidate 1
                "in.startv.hotstar",             // Candidate 2
                "com.spotify.tv.android"         // Candidate 3
        ));

        CleanerEngine engine = new CleanerEngine(mock);
        CleanerEngine.CleanResult result = engine.performCleanup();

        assertEquals(48 * MB, result.freedBytes);
        assertEquals(348 * MB, result.finalAvailableBytes);
        assertEquals(3, result.killedCandidateCount);
        assertEquals("Freed 48 MB RAM", result.title);
        assertTrue(result.subtitle.contains("348 MB Available"));
        assertTrue(result.subtitle.contains("1.0 GB Total"));

        // Confirm protected packages were NOT killed
        assertFalse(mock.killed.contains("com.freeyourtv.launcher"));
        assertFalse(mock.killed.contains("flar2.homebutton"));

        // Confirm candidates were killed
        assertTrue(mock.killed.contains("com.netflix.ninja"));
        assertTrue(mock.killed.contains("in.startv.hotstar"));
        assertTrue(mock.killed.contains("com.spotify.tv.android"));
    }

    @Test
    public void testCleanupWhenMemoryAlreadyOptimal() {
        long MB = 1024 * 1024L;
        long initialAvail = 420 * MB;
        long finalAvail = 420 * MB; // No memory freed
        long totalMem = 1024 * MB;

        MockMemoryProvider mock = new MockMemoryProvider(initialAvail, finalAvail, totalMem);
        mock.installed.add("com.netflix.ninja");

        CleanerEngine engine = new CleanerEngine(mock);
        CleanerEngine.CleanResult result = engine.performCleanup();

        assertEquals(0, result.freedBytes);
        assertEquals("RAM Already Optimal", result.title);
        assertTrue(result.subtitle.contains("420 MB Available"));
        assertFalse("Must never claim memory freed when zero was freed", result.title.contains("Freed"));
    }

    @Test
    public void testCleanupWhenMemorySlightlyDecreasedDueToReallocation() {
        long MB = 1024 * 1024L;
        long initialAvail = 420 * MB;
        long finalAvail = 418 * MB; // Slight kernel variation
        long totalMem = 1024 * MB;

        MockMemoryProvider mock = new MockMemoryProvider(initialAvail, finalAvail, totalMem);
        CleanerEngine engine = new CleanerEngine(mock);
        CleanerEngine.CleanResult result = engine.performCleanup();

        assertEquals(0, result.freedBytes);
        assertEquals("RAM Already Optimal", result.title);
        assertTrue(result.subtitle.contains("418 MB Available"));
    }

    @Test
    public void testFormatBytes() {
        long MB = 1024 * 1024L;
        assertEquals("0 MB", CleanerEngine.formatBytes(0));
        assertEquals("0 MB", CleanerEngine.formatBytes(-50));
        assertEquals("48 MB", CleanerEngine.formatBytes(48 * MB));
        assertEquals("512 MB", CleanerEngine.formatBytes(512 * MB));
        assertEquals("1.0 GB", CleanerEngine.formatBytes(1024 * MB));
        assertEquals("1.5 GB", CleanerEngine.formatBytes(1536 * MB));
    }
}
