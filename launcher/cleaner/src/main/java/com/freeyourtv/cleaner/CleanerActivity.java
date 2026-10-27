package com.freeyourtv.cleaner;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV RAM Cleaner Activity.
 * Runs one-click automatic memory optimization, displays verified before/after metrics,
 * and automatically returns to the home launcher upon completion.
 */
public class CleanerActivity extends Activity {

    private static final long RESULT_DISPLAY_DURATION_MS = 1800L;

    private ProgressBar cleanerProgress;
    private ImageView cleanerCheck;
    private TextView cleanerTitle;
    private TextView cleanerSubtitle;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isCleaningFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cleaner);

        cleanerProgress = findViewById(R.id.cleanerProgress);
        cleanerCheck = findViewById(R.id.cleanerCheck);
        cleanerTitle = findViewById(R.id.cleanerTitle);
        cleanerSubtitle = findViewById(R.id.cleanerSubtitle);

        executeCleanup();
    }

    private void executeCleanup() {
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final PackageManager pm = getPackageManager();

        CleanerEngine.SystemMemoryProvider provider = new CleanerEngine.SystemMemoryProvider() {
            @Override
            public long getAvailableMemoryBytes() {
                if (am == null) return 0L;
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.availMem;
            }

            @Override
            public long getTotalMemoryBytes() {
                if (am == null) return 0L;
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.totalMem;
            }

            @Override
            public List<String> getInstalledPackages() {
                List<String> pkgs = new ArrayList<>();
                if (pm != null) {
                    try {
                        List<PackageInfo> installed = pm.getInstalledPackages(0);
                        for (PackageInfo pi : installed) {
                            if (pi.packageName != null) {
                                pkgs.add(pi.packageName);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                return pkgs;
            }

            @Override
            public void killBackgroundProcesses(String packageName) {
                if (am != null && packageName != null) {
                    try {
                        am.killBackgroundProcesses(packageName);
                    } catch (Exception ignored) {
                    }
                }
            }
        };

        final CleanerEngine engine = new CleanerEngine(provider);

        executor.execute(() -> {
            try {
                System.gc();
                System.runFinalization();
            } catch (Exception ignored) {
            }

            // Perform background process termination
            final CleanerEngine.CleanResult result = engine.performCleanup();

            // Settle time for kernel memory counters
            try {
                Thread.sleep(350);
            } catch (InterruptedException ignored) {
            }

            handler.post(() -> showResultAndReturnHome(result));
        });
    }

    private void showResultAndReturnHome(CleanerEngine.CleanResult result) {
        if (isFinishing() || isDestroyed()) return;

        isCleaningFinished = true;
        if (cleanerProgress != null) cleanerProgress.setVisibility(View.GONE);
        if (cleanerCheck != null) cleanerCheck.setVisibility(View.VISIBLE);

        if (cleanerTitle != null) {
            cleanerTitle.setText(result.title);
        }
        if (cleanerSubtitle != null) {
            cleanerSubtitle.setText(result.subtitle);
        }

        handler.postDelayed(this::returnToHome, RESULT_DISPLAY_DURATION_MS);
    }

    private void returnToHome() {
        if (isFinishing() || isDestroyed()) return;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception ignored) {
        }
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            handler.removeCallbacksAndMessages(null);
            returnToHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
