package com.freeyourtv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots. Configured via ThemeManager or config.xml. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private ImageView wallpaper;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private int ambientColor = -1;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnInputs;
    private TextView btnWifi;
    private TextView btnSettings;
    private TextView btnPower;
    private TextView festivalGreeting;
    private TextView weatherText;
    private WeatherManager weatherManager;
    private TextView shamsiDate;
    private boolean shamsi;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private float glassScrollShift;
    private boolean glassShown;
    private static int lastFocused = 0;
    private static String lastFocusedKey;
    private float density;

    private ThemeManager themeManager;
    private DockDecoration dockDecoration;
    private PopupWindow currentPopup;
    private boolean isActivityResumed = false;

    private LocalDate lastCheckedDate = null;
    private final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            recheckFestivalTheme();
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
            }
            if (adapter != null) {
                final String returnKey = lastFocusedKey;
                final int returnSlot = lastFocused;
                adapter.reload();
                grid.post(() -> {
                    int pos = adapter.positionOf(returnKey);
                    focusTile(pos >= 0 ? pos : returnSlot);
                });
            }
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };

    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            LocalDate now = DateProvider.KOLKATA.now();
            if (lastCheckedDate == null || !lastCheckedDate.equals(now)) {
                recheckFestivalTheme();
            }
            if (weatherManager != null && weatherManager.isEnabled()) {
                weatherManager.fetchWeather(false, new WeatherManager.WeatherCallback() {
                    @Override
                    public void onWeatherUpdated(WeatherManager.WeatherData data) {
                        updateWeatherView();
                    }

                    @Override
                    public void onWeatherError(Exception error) {
                        updateWeatherView();
                    }
                });
            }
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;

        themeManager = new ThemeManager(this);
        COLUMNS = themeManager.getDockSize(getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        wallpaper = findViewById(R.id.wallpaper);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        btnInputs = findViewById(R.id.btnInputs);
        btnSettings = findViewById(R.id.btnSettings);
        btnPower = findViewById(R.id.btnPower);
        festivalGreeting = findViewById(R.id.festivalGreeting);
        weatherText = findViewById(R.id.weatherText);
        weatherManager = new WeatherManager(this);
        if (weatherText != null) {
            weatherText.setOnClickListener(v -> showWeatherSettings());
        }

        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = getResources().getBoolean(R.bool.shamsi_date);
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = getString(R.string.vpn_app).trim();

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        dockDecoration = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(dockDecoration);
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));

        adapter = new AppAdapter(this, this, COLUMNS);
        adapter.setReducedMotion(themeManager.isReducedMotion());
        adapter.setLabelTextColor(themeManager.getCurrentTheme().primaryTextColor);
        grid.setAdapter(adapter);

        int rowsBelowDock = Math.max(0, getResources().getInteger(R.integer.rows_below_dock));
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
            int dockRow = itemH + (int) ((6 + 12) * density);
            int gridRows = itemH * rowsBelowDock;
            int want = Math.max(0, h - dockRow - gridRows - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
            }
        });

        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> showSettingsMenu());
        btnPower.setOnClickListener(v -> requestPowerDialog());

        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return v.getId() == R.id.btnInputs
                        || (v.getId() == R.id.btnWifi && btnInputs.getVisibility() != View.VISIBLE);
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnPower;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        btnPower.setOnKeyListener(pillKeys);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }

        applyTheme(themeManager.getCurrentTheme());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            dismissPopups();
            scrollToTopAndFocusDock();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            dismissPopups();
            scrollToTopAndFocusDock();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void scrollToTopAndFocusDock() {
        lastFocused = 0;
        lastFocusedKey = adapter.getItemCount() > 0 ? adapter.keyAt(0) : null;
        grid.scrollToPosition(0);
        grid.post(() -> focusTile(0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityResumed = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }

        IntentFilter dateFilter = new IntentFilter();
        dateFilter.addAction(Intent.ACTION_DATE_CHANGED);
        dateFilter.addAction(Intent.ACTION_TIME_CHANGED);
        dateFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dateChangeReceiver, dateFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dateChangeReceiver, dateFilter);
        }
        recheckFestivalTheme();

        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);

        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        if (!themeManager.isReducedMotion()) {
            grid.scheduleLayoutAnimation();
        }
        updateGreeting();
        updateFestivalGreeting();
        updateWeatherView();
        if (weatherManager != null && weatherManager.isEnabled()) {
            weatherManager.fetchWeather(false, new WeatherManager.WeatherCallback() {
                @Override
                public void onWeatherUpdated(WeatherManager.WeatherData data) {
                    updateWeatherView();
                }

                @Override
                public void onWeatherError(Exception error) {
                    updateWeatherView();
                }
            });
        }
        updateWifiLabel();
        updateInputsButton();
        handler.postDelayed(minuteTick, MINUTE_MS);

        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityResumed = false;
        dismissPopups();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(dateChangeReceiver);
        } catch (Exception ignored) {
        }
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            try {
                cm.unregisterNetworkCallback(vpnCallback);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y - glassScrollShift);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            showHero(position, r.top - dy);
        } else {
            hideHero();
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        int w = 480;
        int h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(
                w * 0.32f, h * 0.42f, w * 0.62f,
                new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
        drawable.setGravity(Gravity.FILL);
        ambient.setBackground(drawable);
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean online = false, wifi = false, ethernet = false;
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                btnWifi.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ethernet, 0, 0, 0);
            } else if (wifi) {
                btnWifi.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wifi, 0, 0, 0);
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        if (vpn) {
            String badge = getString(R.string.vpn);
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(new VpnBadge(getDrawable(R.drawable.ic_lock), density),
                    text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWifi.setText(label);
        } else {
            btnWifi.setText(text);
        }
    }

    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private void updateInputsButton() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean any = !liveTvPkg.isEmpty() && getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg) != null;
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private static final class InputEntry {
        final String title;
        final int statusRes;
        final boolean active;
        final int iconRes;
        final Runnable onSelect;

        InputEntry(String title, int statusRes, boolean active, int iconRes, Runnable onSelect) {
            this.title = title;
            this.statusRes = statusRes;
            this.active = active;
            this.iconRes = iconRes;
            this.onSelect = onSelect;
        }
    }

    private void showInputs() {
        dismissPopups();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<InputEntry> items = new ArrayList<>();
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput()) continue;
                String label = String.valueOf(info.loadLabel(this));
                int status = tim.getInputState(info.getId());
                int statusRes = status == TvInputManager.INPUT_STATE_CONNECTED ? R.string.connected
                        : status == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? R.string.standby
                        : R.string.not_connected;
                boolean active = status == TvInputManager.INPUT_STATE_CONNECTED;
                int iconRes = info.getType() == TvInputInfo.TYPE_COMPOSITE ? R.drawable.ic_av : R.drawable.ic_input;
                items.add(new InputEntry(label, statusRes, active, iconRes, () -> {
                    Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    open(i);
                }));
            }
        }
        if (!liveTvPkg.isEmpty()) {
            Intent liveTv = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
            if (liveTv == null) liveTv = getPackageManager().getLaunchIntentForPackage(liveTvPkg);
            if (liveTv != null) {
                final Intent launch = liveTv.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        .setPackage(liveTvPkg);
                items.add(new InputEntry(getString(R.string.live_tv), 0, false, R.drawable.ic_livetv, () -> open(launch)));
            }
        }
        if (items.isEmpty()) {
            items.add(new InputEntry(getString(R.string.no_inputs), 0, false, R.drawable.ic_input, null));
        }

        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(btnInputs.getWidth(), (int) (180 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            btnInputs.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.choose_input));
        addSeparator(rows);

        for (InputEntry item : items) {
            View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(item.iconRes);
            ((TextView) row.findViewById(R.id.title)).setText(item.title);
            TextView status = row.findViewById(R.id.status);
            View dot = row.findViewById(R.id.dot);
            if (item.statusRes != 0) {
                status.setText(item.statusRes);
                dot.setSelected(item.active);
            } else {
                status.setVisibility(View.GONE);
                dot.setVisibility(View.GONE);
            }
            if (item.onSelect != null) {
                row.setOnClickListener(v -> {
                    popup.dismiss();
                    item.onSelect.run();
                });
            } else {
                row.setFocusable(false);
            }
            rows.addView(row);
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        popup.showAsDropDown(btnInputs, 0, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        if (rows.getChildCount() > 2) {
            rows.getChildAt(2).requestFocus();
        }
    }

    private void addSeparator(LinearLayout rows) {
        View sep = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
        lp.setMargins((int) (10 * density), (int) (4 * density), (int) (10 * density), (int) (4 * density));
        sep.setLayoutParams(lp);
        sep.setBackgroundColor(0x1FFFFFFF);
        rows.addView(sep);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTileLongClick(int position, View card, AppAdapter.Entry entry) {
        showTileContextMenu(card, entry, position);
    }

    // ---- settings & themes ------------------------------------------------------------------------

    private void showSettingsMenu() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (220 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.launcher_settings));
        addSeparator(rows);

        // 1. Dock Size
        addMenuItem(rows, popup, R.drawable.ic_dock, getString(R.string.dock_size, adapter.getDockSize()), this::showDockSizePicker);

        // 2. Theme Selection
        boolean isAuto = themeManager.getFestivalEngine().isAutoEnabled() && !themeManager.getFestivalEngine().isManualOverride();
        String themeText = getString(themeManager.getCurrentTheme().nameRes) + (isAuto ? " (Auto)" : "");
        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.theme, themeText), this::showThemePicker);

        // 3. Festival Themes
        FestivalThemeEngine.EvaluationResult eval = themeManager.evaluateFestivalTheme();
        String festSummary;
        if (eval.mode == FestivalThemeEngine.Mode.DISABLED) {
            festSummary = getString(R.string.festival_status_disabled);
        } else if (eval.mode == FestivalThemeEngine.Mode.MANUAL_SUSPENDED) {
            festSummary = "Manual Override";
        } else if (eval.activeFestival != null) {
            if (eval.activeFestival.hasDedicatedTheme()) {
                festSummary = eval.activeFestival.name + " (Active)";
            } else {
                festSummary = "OLED Black (" + eval.activeFestival.name + ")";
            }
        } else if (eval.nextFestival != null) {
            festSummary = "Next: " + eval.nextFestival.name;
        } else {
            festSummary = "Auto";
        }
        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.festival_themes_title) + ": " + festSummary, this::showFestivalSettings);

        // 4. Weather Settings
        String weatherSummary;
        if (!weatherManager.isEnabled()) {
            weatherSummary = getString(R.string.festival_status_disabled);
        } else if (weatherManager.hasCachedWeather()) {
            weatherSummary = weatherManager.getCityName() + " (" + weatherManager.getWeatherDisplay() + ")";
        } else {
            weatherSummary = weatherManager.getCityName();
        }
        addMenuItem(rows, popup, R.drawable.ic_motion, getString(R.string.weather_title) + ": " + weatherSummary, this::showWeatherSettings);

        // 5. Default Home App (if available)
        Intent homeChooser = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (getPackageManager().resolveActivity(homeChooser, 0) != null) {
            addMenuItem(rows, popup, R.drawable.ic_home_settings, getString(R.string.home_settings), () -> open(homeChooser));
        }

        // 5. Device Settings
        addMenuItem(rows, popup, R.drawable.ic_settings, getString(R.string.tv_settings), () -> open(new Intent(Settings.ACTION_SETTINGS)));

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        if (rows.getChildCount() > 2) {
            rows.getChildAt(2).requestFocus();
        }
    }

    private void showDockSizePicker() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (200 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.dock_size_title));
        addSeparator(rows);

        int current = adapter.getDockSize();
        for (int size = 4; size <= 6; size++) {
            final int s = size;
            String text = getString(R.string.apps_count, s) + (s == current ? "  âœ“" : "");
            addMenuItem(rows, popup, R.drawable.ic_dock, text, () -> changeDockSize(s));
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        int focusIdx = (current >= 4 && current <= 6) ? (current - 4 + 2) : 2;
        if (rows.getChildCount() > focusIdx) {
            rows.getChildAt(focusIdx).requestFocus();
        }
    }

    private void changeDockSize(int newSize) {
        if (newSize < 4 || newSize > 6 || newSize == COLUMNS) return;
        COLUMNS = newSize;
        themeManager.setDockSize(newSize);

        GridLayoutManager glm = (GridLayoutManager) grid.getLayoutManager();
        if (glm != null) glm.setSpanCount(COLUMNS);

        if (dockDecoration != null) {
            dockDecoration.setDockSize(COLUMNS);
            grid.invalidateItemDecorations();
        }

        adapter.setDockSize(COLUMNS);
        grid.setAdapter(adapter);

        grid.scrollToPosition(0);
        grid.post(() -> focusTile(0));
    }

    private void showThemePicker() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (240 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.theme_title));
        addSeparator(rows);

        boolean isAuto = themeManager.getFestivalEngine().isAutoEnabled() && !themeManager.getFestivalEngine().isManualOverride();
        String autoLabel = getString(R.string.theme_auto) + (isAuto ? "  âœ“" : "");
        addMenuItem(rows, popup, R.drawable.ic_palette, autoLabel, () -> {
            themeManager.resumeFestivalAuto();
            applyTheme(themeManager.getCurrentTheme());
        });

        String currentId = themeManager.getCurrentTheme().id;
        for (ThemeManager.ThemeInfo t : themeManager.getStandardThemes()) {
            boolean isSelected = !isAuto && t.id.equals(currentId);
            String text = getString(t.nameRes) + (isSelected ? "  âœ“" : "");
            addMenuItem(rows, popup, R.drawable.ic_palette, text, () -> {
                themeManager.setManualTheme(t.id);
                applyTheme(themeManager.getCurrentTheme());
            });
        }

        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.theme_festival_collection) + "  â€º", this::showFestivalThemeGallery);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        if (rows.getChildCount() > 2) {
            rows.getChildAt(2).requestFocus();
        }
    }

    private void showFestivalThemeGallery() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (270 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.theme_festival_collection));
        addSeparator(rows);

        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.festival_season_spring) + "  â€º",
                () -> showSeasonPicker(R.string.festival_season_spring, themeManager.getSpringThemes()));

        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.festival_season_monsoon) + "  â€º",
                () -> showSeasonPicker(R.string.festival_season_monsoon, themeManager.getMonsoonThemes()));

        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.festival_season_autumn) + "  â€º",
                () -> showSeasonPicker(R.string.festival_season_autumn, themeManager.getAutumnThemes()));

        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.festival_season_winter) + "  â€º",
                () -> showSeasonPicker(R.string.festival_season_winter, themeManager.getWinterThemes()));

        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_cancel, "â€¹ " + getString(R.string.theme_title), this::showThemePicker);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                break;
            }
        }
    }

    private void showSeasonPicker(int titleRes, List<ThemeManager.ThemeInfo> seasonThemes) {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (270 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(titleRes));
        addSeparator(rows);

        boolean isAuto = themeManager.getFestivalEngine().isAutoEnabled() && !themeManager.getFestivalEngine().isManualOverride();
        String currentId = themeManager.getCurrentTheme().id;

        for (ThemeManager.ThemeInfo t : seasonThemes) {
            boolean isSelected = !isAuto && t.id.equals(currentId);
            String text = getString(t.nameRes) + (isSelected ? "  âœ“" : "");
            addMenuItem(rows, popup, R.drawable.ic_palette, text, () -> {
                themeManager.setManualTheme(t.id);
                applyTheme(themeManager.getCurrentTheme());
            });
        }

        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_cancel, "â€¹ " + getString(R.string.theme_festival_collection), this::showFestivalThemeGallery);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                break;
            }
        }
    }

    private void showFestivalSettings() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (270 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.festival_themes_title));
        addSeparator(rows);

        FestivalThemeEngine engine = themeManager.getFestivalEngine();
        FestivalThemeEngine.EvaluationResult eval = themeManager.evaluateFestivalTheme();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

        if (eval.mode == FestivalThemeEngine.Mode.DISABLED) {
            addMenuHeader(rows, getString(R.string.festival_status_disabled));
        } else if (eval.mode == FestivalThemeEngine.Mode.MANUAL_SUSPENDED) {
            String nextName = (eval.nextFestival != null) ? eval.nextFestival.name : "upcoming festival";
            addMenuHeader(rows, getString(R.string.festival_suspended_info, nextName));
        } else {
            if (eval.activeFestival != null) {
                String activeStr = getString(R.string.festival_status_active,
                        eval.activeFestival.name,
                        eval.activeFestival.getStartDate().format(fmt),
                        eval.activeFestival.getEndDate().format(fmt));
                addMenuHeader(rows, activeStr);
                ThemeManager.ThemeInfo activeThemeInfo = eval.activeFestival.hasDedicatedTheme()
                        ? themeManager.getTheme(eval.activeFestival.designatedThemeId)
                        : null;
                String themeName = (activeThemeInfo != null) ? getString(activeThemeInfo.nameRes) : "OLED Black";
                addMenuHeader(rows, "Theme: " + themeName);
            } else {
                addMenuHeader(rows, getString(R.string.festival_status_no_active));
            }

            if (eval.nextFestival != null) {
                String nextStr = getString(R.string.festival_status_next,
                        eval.nextFestival.name,
                        eval.nextFestival.getStartDate().format(fmt),
                        eval.nextFestival.getEndDate().format(fmt));
                addMenuHeader(rows, nextStr);
            }
        }
        addSeparator(rows);

        // 1. Toggle ON / OFF
        if (engine.isAutoEnabled()) {
            addMenuItem(rows, popup, R.drawable.ic_cancel, getString(R.string.festival_auto_toggle_on), () -> {
                themeManager.setFestivalAutoEnabled(false);
                showFestivalSettings();
            });
        } else {
            addMenuItem(rows, popup, R.drawable.ic_restart, getString(R.string.festival_auto_toggle_off), () -> {
                themeManager.setFestivalAutoEnabled(true);
                applyTheme(themeManager.getCurrentTheme());
                showFestivalSettings();
            });
        }

        // 2. Resume button if manual override is active
        if (engine.isAutoEnabled() && engine.isManualOverride()) {
            addMenuItem(rows, popup, R.drawable.ic_restart, getString(R.string.festival_resume_auto), () -> {
                themeManager.resumeFestivalAuto();
                applyTheme(themeManager.getCurrentTheme());
                showFestivalSettings();
            });
        }

        // 3. Browse all festival themes
        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_palette, getString(R.string.theme_festival_collection) + "  â€º", this::showFestivalThemeGallery);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                break;
            }
        }
    }

    private void recheckFestivalTheme() {
        lastCheckedDate = DateProvider.KOLKATA.now();
        ThemeManager.ThemeInfo before = themeManager.getCurrentTheme();
        themeManager.evaluateFestivalTheme();
        ThemeManager.ThemeInfo after = themeManager.getCurrentTheme();
        if (!before.id.equals(after.id)) {
            applyTheme(after);
        } else {
            updateFestivalGreeting();
        }
    }

    private void applyTheme(ThemeManager.ThemeInfo theme) {
        root.setBackgroundColor(theme.backgroundColor);

        if (wallpaper != null) {
            if (theme.wallpaperBitmap != null) {
                wallpaper.setImageBitmap(theme.wallpaperBitmap);
                wallpaper.setVisibility(View.VISIBLE);
            } else if (theme.wallpaperRes != 0) {
                wallpaper.setImageResource(theme.wallpaperRes);
                wallpaper.setVisibility(View.VISIBLE);
            } else {
                wallpaper.setImageDrawable(null);
                wallpaper.setVisibility(View.GONE);
            }
        }

        if (glass != null) {
            glass.setThemeColors(
                    theme.glassRimColor,
                    theme.glassGlowColor,
                    theme.glassSpecularColor,
                    theme.glassFillColor
            );
        }

        greeting.setTextColor(theme.secondaryTextColor);
        ((TextView) findViewById(R.id.clock)).setTextColor(theme.primaryTextColor);
        ((TextView) findViewById(R.id.ampm)).setTextColor(theme.secondaryTextColor);
        ((TextView) findViewById(R.id.date)).setTextColor(theme.secondaryTextColor);
        if (shamsiDate != null) shamsiDate.setTextColor(theme.primaryTextColor);

        View clockDivider = findViewById(R.id.clockDivider);
        if (clockDivider != null) {
            clockDivider.setBackgroundColor(theme.isLight ? 0x2E000000 : 0x33FFFFFF);
        }

        int bgRes = theme.isLight ? R.drawable.pill_bg_light : R.drawable.pill_bg;
        int padStart = (int) (16 * density);
        int padEnd = (int) (20 * density);
        for (TextView btn : new TextView[]{btnInputs, btnWifi, btnSettings}) {
            if (btn != null) {
                btn.setBackgroundResource(bgRes);
                btn.setPaddingRelative(padStart, 0, padEnd, 0);
            }
        }
        if (btnPower != null) {
            btnPower.setBackgroundResource(bgRes);
            btnPower.setPadding(0, (int) (11 * density), 0, 0);
        }

        int normalColor = theme.pillTextColor;
        int focusedColor = theme.isLight ? 0xFF111418 : 0xFF111111;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_focused},
                new int[]{}
        };
        int[] colors = new int[]{focusedColor, normalColor};
        ColorStateList pillColors = new ColorStateList(states, colors);

        btnInputs.setTextColor(pillColors);
        btnInputs.setCompoundDrawableTintList(pillColors);
        btnWifi.setTextColor(pillColors);
        btnWifi.setCompoundDrawableTintList(pillColors);
        btnSettings.setTextColor(pillColors);
        btnSettings.setCompoundDrawableTintList(pillColors);
        btnPower.setTextColor(pillColors);
        btnPower.setCompoundDrawableTintList(pillColors);

        heroName.setTextColor(theme.primaryTextColor);

        if (dockDecoration != null) {
            dockDecoration.setColors(theme.dockBandColor, theme.dockEdgeColor);
            grid.invalidateItemDecorations();
        }

        if (adapter != null) {
            adapter.setLabelTextColor(theme.primaryTextColor);
        }

        if (weatherText != null) {
            weatherText.setTextColor(theme.secondaryTextColor);
        }

        updateGreeting();
        updateFestivalGreeting();
    }

    // ---- power menu -------------------------------------------------------------------------------

    private void requestPowerDialog() {
        dismissPopups();
        if (HomeKeyService.triggerPowerDialog()) {
            return;
        }
        Toast.makeText(this, R.string.power_dialog_unsupported, Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    // ---- tile context menu ------------------------------------------------------------------------

    private void showTileContextMenu(View card, AppAdapter.Entry entry, int position) {
        dismissPopups();
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (210 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            card.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, entry.label);
        addSeparator(rows);

        String pkg = entry.pkg;
        boolean inDock = adapter.isInDock(pkg);

        if (inDock) {
            if (adapter.canMoveLeft(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_arrow_left, getString(R.string.move_left), () -> {
                    adapter.moveDockLeft(pkg);
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
            if (adapter.canMoveRight(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_arrow_right, getString(R.string.move_right), () -> {
                    adapter.moveDockRight(pkg);
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
            if (adapter.canRemoveFromDock(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_remove, getString(R.string.remove_from_dock), () -> {
                    adapter.removeFromDock(pkg);
                    Toast.makeText(this, getString(R.string.removed_from_dock, entry.label), Toast.LENGTH_SHORT).show();
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
        } else {
            addMenuItem(rows, popup, R.drawable.ic_add, getString(R.string.add_to_dock), () -> {
                adapter.addToDock(pkg);
                Toast.makeText(this, getString(R.string.added_to_dock, entry.label), Toast.LENGTH_SHORT).show();
                grid.post(() -> {
                    int newPos = adapter.positionOf(entry.component.flattenToShortString());
                    focusTile(newPos >= 0 ? newPos : 0);
                });
            });
            if (adapter.canMoveGridLeft(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_arrow_left, getString(R.string.move_left), () -> {
                    adapter.moveGridLeft(pkg);
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
            if (adapter.canMoveGridRight(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_arrow_right, getString(R.string.move_right), () -> {
                    adapter.moveGridRight(pkg);
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
            if (adapter.canMoveGridUp(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_arrow_up, getString(R.string.move_up), () -> {
                    adapter.moveGridUp(pkg);
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
            if (adapter.canMoveGridDown(pkg)) {
                addMenuItem(rows, popup, R.drawable.ic_arrow_down, getString(R.string.move_down), () -> {
                    adapter.moveGridDown(pkg);
                    grid.post(() -> {
                        int newPos = adapter.positionOf(entry.component.flattenToShortString());
                        focusTile(newPos >= 0 ? newPos : position);
                    });
                });
            }
        }

        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_info, getString(R.string.app_info), () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + entry.pkg));
            open(i);
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        int h = panel.getMeasuredHeight();
        popup.setWidth(w);
        popup.setHeight(h);

        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);

        int screenW = root.getWidth();
        int screenH = root.getHeight();
        int x = r.centerX() - w / 2;
        int margin = (int) (24 * density);
        x = Math.max(margin, Math.min(screenW - w - margin, x));

        int y;
        if (r.bottom + h + (int) (12 * density) > screenH) {
            y = r.top - h - (int) (12 * density);
        } else {
            y = r.bottom + (int) (12 * density);
        }
        y = Math.max(margin, Math.min(screenH - h - margin, y));

        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        if (rows.getChildCount() > 2) {
            rows.getChildAt(2).requestFocus();
        }
    }

    private void dismissPopups() {
        if (currentPopup != null) {
            try {
                if (currentPopup.isShowing()) currentPopup.dismiss();
            } catch (Exception ignored) {
            }
            currentPopup = null;
        }
    }

    private void addMenuHeader(LinearLayout rows, String title) {
        View header = getLayoutInflater().inflate(R.layout.item_menu_header, rows, false);
        ((TextView) header.findViewById(R.id.title)).setText(title);
        rows.addView(header);
    }

    private void addMenuItem(LinearLayout rows, PopupWindow popup, int iconRes, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ImageView icon = row.findViewById(R.id.icon);
        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- Weather & Festival Greeting -------------------------------------------------------------

    private void updateWeatherView() {
        if (weatherText == null || weatherManager == null) return;
        if (!weatherManager.isEnabled()) {
            weatherText.setVisibility(View.GONE);
            return;
        }
        String display = weatherManager.getWeatherDisplay();
        if (display != null && !display.isEmpty()) {
            weatherText.setText(display);
            weatherText.setVisibility(View.VISIBLE);
        } else {
            weatherText.setVisibility(View.GONE);
        }
    }

    private void updateFestivalGreeting() {
        if (festivalGreeting == null || themeManager == null) return;
        ThemeManager.ThemeInfo currentTheme = themeManager.getCurrentTheme();
        String greetingText = FestivalGreetingHelper.getGreetingForTheme(this, currentTheme.id);

        if (greetingText != null && !greetingText.isEmpty()) {
            if (FestivalGreetingHelper.isLongGreeting(greetingText)) {
                festivalGreeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
                festivalGreeting.setLetterSpacing(0.08f);
            } else {
                festivalGreeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f);
                festivalGreeting.setLetterSpacing(0.14f);
            }

            int auraColor = (currentTheme.ambientDefaultColor & 0x00FFFFFF) | 0x99000000;
            festivalGreeting.setShadowLayer(16f, 0f, 4f, auraColor);
            festivalGreeting.setTextColor(0xFFFFDF7A);

            if (festivalGreeting.getVisibility() != View.VISIBLE || !greetingText.contentEquals(festivalGreeting.getText())) {
                festivalGreeting.setText(greetingText);
                festivalGreeting.setAlpha(0f);
                festivalGreeting.setTranslationY(-10 * density);
                festivalGreeting.setVisibility(View.VISIBLE);
                festivalGreeting.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(350)
                        .setInterpolator(new DecelerateInterpolator(1.4f))
                        .start();
            }
        } else {
            festivalGreeting.setVisibility(View.GONE);
            festivalGreeting.setText(null);
        }
    }

    private void showWeatherSettings() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (280 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.weather_title));
        addSeparator(rows);

        if (weatherManager.isEnabled()) {
            String status = weatherManager.getWeatherDisplay();
            if (status != null) {
                addMenuHeader(rows, status);
            }
            addMenuHeader(rows, getString(R.string.weather_last_updated, weatherManager.getLastUpdatedDisplay()));
        } else {
            addMenuHeader(rows, getString(R.string.festival_status_disabled));
        }
        addSeparator(rows);

        if (weatherManager.isEnabled()) {
            addMenuItem(rows, popup, R.drawable.ic_cancel, getString(R.string.weather_enabled), () -> {
                weatherManager.setEnabled(false);
                updateWeatherView();
                showWeatherSettings();
            });
        } else {
            addMenuItem(rows, popup, R.drawable.ic_restart, getString(R.string.weather_disabled), () -> {
                weatherManager.setEnabled(true);
                weatherManager.fetchWeather(true, new WeatherManager.WeatherCallback() {
                    @Override
                    public void onWeatherUpdated(WeatherManager.WeatherData data) {
                        updateWeatherView();
                    }

                    @Override
                    public void onWeatherError(Exception error) {
                        updateWeatherView();
                    }
                });
                updateWeatherView();
                showWeatherSettings();
            });
        }

        String unitText = weatherManager.isFahrenheit()
                ? getString(R.string.weather_unit_f) + "  âœ“"
                : getString(R.string.weather_unit_c) + "  âœ“";
        addMenuItem(rows, popup, R.drawable.ic_motion, unitText, () -> {
            weatherManager.setFahrenheit(!weatherManager.isFahrenheit());
            updateWeatherView();
            showWeatherSettings();
        });

        String cityText = getString(R.string.weather_choose_city) + ": " + weatherManager.getCityName() + "  â€º";
        addMenuItem(rows, popup, R.drawable.ic_dock, cityText, this::showCityPicker);

        if (weatherManager.isEnabled()) {
            addMenuItem(rows, popup, R.drawable.ic_restart, getString(R.string.weather_refresh), () -> {
                Toast.makeText(this, "Refreshing weather...", Toast.LENGTH_SHORT).show();
                weatherManager.fetchWeather(true, new WeatherManager.WeatherCallback() {
                    @Override
                    public void onWeatherUpdated(WeatherManager.WeatherData data) {
                        updateWeatherView();
                        String disp = weatherManager.getWeatherDisplay();
                        Toast.makeText(MainActivity.this, getString(R.string.weather_updated, disp != null ? disp : data.cityName), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onWeatherError(Exception error) {
                        Toast.makeText(MainActivity.this, R.string.weather_unavailable, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_cancel, "â€¹ " + getString(R.string.launcher_settings), this::showSettingsMenu);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();

        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                break;
            }
        }
    }

    private void showCityPicker() {
        dismissPopups();
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (280 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (currentPopup == popup) currentPopup = null;
            anchor.requestFocus();
        });
        currentPopup = popup;

        addMenuHeader(rows, getString(R.string.weather_choose_city));
        addSeparator(rows);

        String currentCity = weatherManager.getCityName();
        for (WeatherManager.City city : WeatherManager.PRESET_CITIES) {
            boolean isSelected = city.name.equalsIgnoreCase(currentCity);
            String label = city.name + (isSelected ? "  âœ“" : "");
            addMenuItem(rows, popup, R.drawable.ic_dock, label, () -> {
                weatherManager.setCity(city);
                weatherManager.fetchWeather(true, new WeatherManager.WeatherCallback() {
                    @Override
                    public void onWeatherUpdated(WeatherManager.WeatherData data) {
                        updateWeatherView();
                    }

                    @Override
                    public void onWeatherError(Exception error) {
                        updateWeatherView();
                    }
                });
                updateWeatherView();
                showWeatherSettings();
            });
        }

        addSeparator(rows);
        addMenuItem(rows, popup, R.drawable.ic_cancel, "â€¹ " + getString(R.string.weather_title), this::showWeatherSettings);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = Math.max(width, panel.getMeasuredWidth());
        popup.setWidth(w);

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);

        int xOff = anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xOff, (int) (10 * density));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();

        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                break;
            }
        }
    }
}
