package com.freeyourtv.launcher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link #dockSize} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Called on long click or menu key to open the tile context menu. */
        void onTileLongClick(int position, View card, Entry entry);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private static final String PREFS_NAME = "tvhome_prefs";
    private static final String KEY_DOCK = "dock_packages";
    private static final String KEY_GRID = "grid_packages";

    private final Context ctx;
    private final TileListener listener;
    private int dockSize;
    private boolean reducedMotion = false;
    private int labelTextColor = 0xF2FFFFFF;
    /** Dock order, loaded from SharedPreferences or fallback to config.xml. */
    private final List<String> favourites = new ArrayList<>();
    /** Grid order, loaded from SharedPreferences. */
    private final List<String> gridOrder = new ArrayList<>();
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        this.favourites.addAll(loadFavourites());
        this.gridOrder.addAll(loadGridOrder());
        setHasStableIds(true);
    }

    public int getDockSize() {
        return dockSize;
    }

    public void setDockSize(int dockSize) {
        this.dockSize = dockSize;
        reload();
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    public int getLabelTextColor() {
        return labelTextColor;
    }

    public void setLabelTextColor(int color) {
        if (this.labelTextColor != color) {
            this.labelTextColor = color;
            notifyDataSetChanged();
        }
    }

    List<String> loadFavourites() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_DOCK, null);
        List<String> list = new ArrayList<>();
        if (saved != null) {
            String[] split = saved.split(",");
            for (String s : split) {
                String pkg = s.trim();
                if (!pkg.isEmpty() && !list.contains(pkg)) {
                    list.add(pkg);
                }
            }
        } else {
            String[] defaults = ctx.getResources().getStringArray(R.array.dock_apps);
            for (String s : defaults) {
                String pkg = s.trim();
                if (!pkg.isEmpty() && !list.contains(pkg)) {
                    list.add(pkg);
                }
            }
        }
        return list;
    }

    private void saveFavourites() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < favourites.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(favourites.get(i));
        }
        sp.edit().putString(KEY_DOCK, sb.toString()).apply();
    }

    List<String> loadGridOrder() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_GRID, null);
        List<String> list = new ArrayList<>();
        if (saved != null) {
            String[] split = saved.split(",");
            for (String s : split) {
                String pkg = s.trim();
                if (!pkg.isEmpty() && !list.contains(pkg)) {
                    list.add(pkg);
                }
            }
        }
        return list;
    }

    private void saveGridOrder() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gridOrder.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(gridOrder.get(i));
        }
        sp.edit().putString(KEY_GRID, sb.toString()).apply();
    }

    public int positionOfPackage(String pkg) {
        if (pkg == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).pkg.equals(pkg)) return i;
        }
        return -1;
    }

    public boolean isInDock(String pkg) {
        int pos = positionOfPackage(pkg);
        return pos >= 0 && pos < dockSize;
    }

    public boolean canMoveLeft(String pkg) {
        int pos = positionOfPackage(pkg);
        return pos > 0 && pos < dockSize;
    }

    public boolean canMoveRight(String pkg) {
        int pos = positionOfPackage(pkg);
        return pos >= 0 && pos < dockSize - 1 && pos + 1 < items.size();
    }

    public boolean canRemoveFromDock(String pkg) {
        int pos = positionOfPackage(pkg);
        return pos >= 0 && pos < dockSize && items.size() > 1;
    }

    public boolean moveDockLeft(String pkg) {
        int idx = favourites.indexOf(pkg);
        if (idx > 0 && idx < dockSize) {
            Collections.swap(favourites, idx, idx - 1);
            saveFavourites();
            reload();
            return true;
        }
        return false;
    }

    public boolean moveDockRight(String pkg) {
        int idx = favourites.indexOf(pkg);
        if (idx >= 0 && idx < Math.min(favourites.size() - 1, dockSize - 1)) {
            Collections.swap(favourites, idx, idx + 1);
            saveFavourites();
            reload();
            return true;
        }
        return false;
    }

    public boolean removeFromDock(String pkg) {
        if (!isInDock(pkg)) {
            return false;
        }
        favourites.remove(pkg);
        saveFavourites();
        gridOrder.remove(pkg);
        gridOrder.add(0, pkg);
        saveGridOrder();
        reload(pkg);
        return true;
    }

    public boolean addToDock(String pkg) {
        if (favourites.contains(pkg)) {
            favourites.remove(pkg);
        }
        gridOrder.remove(pkg);
        saveGridOrder();
        int insertIdx = Math.min(favourites.size(), dockSize - 1);
        favourites.add(insertIdx, pkg);
        saveFavourites();
        reload();
        return true;
    }

    public boolean canMoveGridLeft(String pkg) {
        int pos = positionOfPackage(pkg);
        int gridIdx = pos - dockSize;
        return pos >= dockSize && (pos % dockSize) > 0 && gridIdx > 0 && gridIdx < gridOrder.size();
    }

    public boolean canMoveGridRight(String pkg) {
        int pos = positionOfPackage(pkg);
        int gridIdx = pos - dockSize;
        return pos >= dockSize && (pos % dockSize) < (dockSize - 1) && (pos + 1 < items.size())
                && gridIdx >= 0 && (gridIdx + 1) < gridOrder.size();
    }

    public boolean canMoveGridUp(String pkg) {
        int pos = positionOfPackage(pkg);
        int gridIdx = pos - dockSize;
        return pos >= 2 * dockSize && gridIdx >= dockSize && gridIdx < gridOrder.size();
    }

    public boolean canMoveGridDown(String pkg) {
        int pos = positionOfPackage(pkg);
        int gridIdx = pos - dockSize;
        return pos >= dockSize && (pos + dockSize < items.size())
                && gridIdx >= 0 && (gridIdx + dockSize) < gridOrder.size();
    }

    public boolean moveGridLeft(String pkg) {
        int pos = positionOfPackage(pkg);
        int col = pos % dockSize;
        if (pos >= dockSize && col > 0) {
            int gridIdx = pos - dockSize;
            if (gridIdx > 0 && gridIdx < gridOrder.size()) {
                Collections.swap(gridOrder, gridIdx, gridIdx - 1);
                saveGridOrder();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean moveGridRight(String pkg) {
        int pos = positionOfPackage(pkg);
        int col = pos % dockSize;
        if (pos >= dockSize && col < (dockSize - 1) && (pos + 1 < items.size())) {
            int gridIdx = pos - dockSize;
            if (gridIdx >= 0 && gridIdx + 1 < gridOrder.size()) {
                Collections.swap(gridOrder, gridIdx, gridIdx + 1);
                saveGridOrder();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean moveGridUp(String pkg) {
        int pos = positionOfPackage(pkg);
        if (pos >= 2 * dockSize) {
            int gridIdx = pos - dockSize;
            if (gridIdx >= dockSize && gridIdx < gridOrder.size()) {
                Collections.swap(gridOrder, gridIdx, gridIdx - dockSize);
                saveGridOrder();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean moveGridDown(String pkg) {
        int pos = positionOfPackage(pkg);
        if (pos >= dockSize && (pos + dockSize < items.size())) {
            int gridIdx = pos - dockSize;
            if (gridIdx >= 0 && gridIdx + dockSize < gridOrder.size()) {
                Collections.swap(gridOrder, gridIdx, gridIdx + dockSize);
                saveGridOrder();
                reload();
                return true;
            }
        }
        return false;
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0 && ri.activityInfo.applicationInfo != null) {
            id = ri.activityInfo.applicationInfo.banner;
        }
        if (id != 0 && ri.activityInfo.applicationInfo != null) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                int densityDpi = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, densityDpi, null);
            } catch (Exception ignored) {
            }
        }
        Drawable d = ri.activityInfo.loadBanner(pm);
        if (d == null && ri.activityInfo.applicationInfo != null) {
            d = ri.activityInfo.applicationInfo.loadBanner(pm);
        }
        return d;
    }

    void reload() {
        reload(null);
    }

    void reload(String excludedFromDock) {
        favourites.clear();
        favourites.addAll(loadFavourites());
        gridOrder.clear();
        gridOrder.addAll(loadGridOrder());

        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);

        Map<String, Entry> available = new LinkedHashMap<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = loadBanner(pm, ri);
            Drawable icon = ri.loadIcon(pm);
            if ("io.gh.reisxd.tizentube.cobalt".equals(pkg)) {
                banner = ctx.getDrawable(R.drawable.banner_youtube);
            }
            String label = String.valueOf(ri.loadLabel(pm));
            available.put(pkg, new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }

        // Clean stale packages that are no longer installed from favourites
        boolean favouritesChanged = favourites.removeIf(pkg -> !available.containsKey(pkg));

        // 1. Build dock row from favourites
        List<Entry> dockEntries = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String pkg : favourites) {
            if (dockEntries.size() >= dockSize) break;
            if (excludedFromDock != null && excludedFromDock.equals(pkg)) continue;
            Entry e = available.get(pkg);
            if (e != null) {
                dockEntries.add(e);
                used.add(pkg);
            }
        }

        // 2. If dock row has fewer items than dockSize, fill from gridOrder (excluding excludedFromDock)
        if (dockEntries.size() < dockSize) {
            for (String pkg : new ArrayList<>(gridOrder)) {
                if (dockEntries.size() >= dockSize) break;
                if (excludedFromDock != null && excludedFromDock.equals(pkg)) continue;
                if (!used.contains(pkg) && available.containsKey(pkg)) {
                    Entry e = available.get(pkg);
                    dockEntries.add(e);
                    used.add(pkg);
                    if (!favourites.contains(pkg)) {
                        favourites.add(pkg);
                        favouritesChanged = true;
                    }
                }
            }
        }

        // 3. If dock row still has fewer items than dockSize, fill from remaining available apps alphabetically
        if (dockEntries.size() < dockSize) {
            List<Entry> remaining = new ArrayList<>();
            for (Entry e : available.values()) {
                if (!used.contains(e.pkg)) {
                    remaining.add(e);
                }
            }
            Collections.sort(remaining, (a, b) -> a.label.compareToIgnoreCase(b.label));
            for (Entry e : remaining) {
                if (dockEntries.size() >= dockSize) break;
                if (excludedFromDock != null && excludedFromDock.equals(e.pkg)) continue;
                dockEntries.add(e);
                used.add(e.pkg);
                if (!favourites.contains(e.pkg)) {
                    favourites.add(e.pkg);
                    favouritesChanged = true;
                }
            }
        }

        if (favouritesChanged) {
            saveFavourites();
        }

        // 4. Order grid apps using saved gridOrder, appending newly discovered apps alphabetically
        List<Entry> gridEntries = new ArrayList<>();
        for (String pkg : gridOrder) {
            if (available.containsKey(pkg) && !used.contains(pkg)) {
                gridEntries.add(available.get(pkg));
                used.add(pkg);
            }
        }
        List<Entry> newApps = new ArrayList<>();
        for (Entry e : available.values()) {
            if (!used.contains(e.pkg)) {
                newApps.add(e);
                used.add(e.pkg);
            }
        }
        Collections.sort(newApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        gridEntries.addAll(newApps);

        // Update and save current gridOrder (strictly excludes anything placed in the dock)
        gridOrder.clear();
        for (Entry e : gridEntries) {
            gridOrder.add(e.pkg);
        }
        saveGridOrder();

        items.clear();
        items.addAll(dockEntries);
        items.addAll(gridEntries);

        if (ctx.getResources().getBoolean(R.bool.show_home_settings_shortcut)
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            items.add(new Entry(ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings)));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, reducedMotion);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        h.name.setTextColor(labelTextColor);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);
        boolean focused = h.card.isFocused();
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onTileLongClick(pos, h.card, e);
                return true;
            }
            return false;
        });
        h.card.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onTileLongClick(pos, h.card, e);
                    return true;
                }
            }
            if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                    && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            int pos = h.getBindingAdapterPosition();
            return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
        });
    }

    private void launch(Entry e) {
        if (e.component.equals(new ComponentName(ctx, HomeSettingsActivity.class))) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final boolean reducedMotion;

        Holder(View v, TileListener listener, boolean reducedMotion) {
            super(v);
            this.reducedMotion = reducedMotion;
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().cancel();
                name.animate().cancel();
                if (reducedMotion) {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 120 : 80)
                            .setInterpolator(new DecelerateInterpolator())
                            .withLayer()
                            .start();
                    name.animate().alpha(focused ? 1f : 0f).setDuration(100).start();
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                            .withLayer()
                            .start();
                    name.animate().alpha(focused ? 1f : 0f)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
