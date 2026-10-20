package com.litetv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {

    // 5 x (152 + 16) dp fits the 960dp-wide width of a 720p tvdpi screen.
    private static final int COLUMNS = 5;
    private static final int TILE_W = 152;
    private static final int TILE_H = 86;
    private static final int ACCENT = 0xFFFFB547;
    private static final int TEXT_DIM = 0xFF8A94A6;

    // Shown as header chips instead of grid tiles.
    private static final Set<String> EXCLUDED = new LinkedHashSet<>(Arrays.asList(
            "com.android.tv.settings", "com.tcl.suspension"));
    private static final String DEFAULT_ORDER = "com.google.android.youtube.tv,in.startv.hotstar,"
            + "ahaflix.tv,com.sonyliv,com.graymatrix.did,com.jio.media.stb.ondemand,"
            + "com.netflix.ninja,com.amazon.amazonvideo.livingroom,com.android.vending";
    private static final String DEFAULT_HIDDEN = "com.google.android.videos";

    private static final class App {
        String pkg;
        ComponentName component;
        CharSequence label;
        Drawable banner;
        Drawable icon;
        boolean system;
    }

    private SharedPreferences prefs;
    private ScrollView scroll;
    private GridLayout grid;
    private final List<App> visible = new ArrayList<>();
    private final List<App> hidden = new ArrayList<>();
    private boolean dirty = true;
    private String focusPkg;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dirty = true;
            if (hasWindowFocus()) refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        setContentView(buildLayout());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dirty) refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the first app.
        scroll.smoothScrollTo(0, 0);
        if (grid.getChildCount() > 0) grid.getChildAt(0).findViewWithTag("card").requestFocus();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Home screen has nowhere to go back to.
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(TEXT_DIM);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        clock.addView(time);
        clock.addView(date);
        header.addView(clock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        header.addView(chip("Inputs", v -> openInputs()));
        View gap = new View(this);
        header.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        header.addView(chip("Settings", v -> safeStart(new Intent(Settings.ACTION_SETTINGS))));
        root.addView(header);

        TextView title = new TextView(this);
        title.setText("APPS");
        title.setTextColor(TEXT_DIM);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setLetterSpacing(0.15f);
        title.setPadding(0, dp(24), 0, dp(8));
        root.addView(title);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(8), dp(10), dp(8), dp(40));
        scroll.setVerticalScrollBarEnabled(false);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.setClipChildren(false);
        return root;
    }

    private TextView chip(String text, View.OnClickListener click) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setTextColor(Color.WHITE);
        chip.setPadding(dp(20), dp(9), dp(20), dp(9));
        chip.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(0x22FFFFFF);
        chip.setBackground(bg);
        chip.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? Color.WHITE : 0x22FFFFFF);
            chip.setTextColor(focused ? Color.BLACK : Color.WHITE);
        });
        chip.setOnClickListener(click);
        return chip;
    }

    private void openInputs() {
        Intent inputs = new Intent("com.android.tv.action.VIEW_INPUTS");
        if (inputs.resolveActivity(getPackageManager()) != null) {
            safeStart(inputs);
        } else {
            safeStart(getPackageManager().getLaunchIntentForPackage("com.tcl.suspension"));
        }
    }

    private void safeStart(Intent intent) {
        if (intent == null) return;
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- data -------------------------------------------------------------

    private List<String> csv(String key, String def) {
        String raw = prefs.getString(key, def);
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private void saveCsv(String key, List<String> values) {
        prefs.edit().putString(key, String.join(",", values)).apply();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, App> byPkg = new HashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || EXCLUDED.contains(pkg) || byPkg.containsKey(pkg)) continue;
            App app = new App();
            app.pkg = pkg;
            app.component = new ComponentName(pkg, ri.activityInfo.name);
            app.label = ri.loadLabel(pm);
            app.banner = ri.activityInfo.loadBanner(pm);
            if (app.banner == null) app.icon = ri.loadIcon(pm);
            app.system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            byPkg.put(pkg, app);
        }

        List<String> order = csv("order", DEFAULT_ORDER);
        Set<String> hiddenSet = new LinkedHashSet<>(csv("hidden", DEFAULT_HIDDEN));
        List<App> ordered = new ArrayList<>();
        for (String pkg : order) if (byPkg.containsKey(pkg)) ordered.add(byPkg.remove(pkg));
        List<App> rest = new ArrayList<>(byPkg.values());
        rest.sort((a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        ordered.addAll(rest);

        visible.clear();
        hidden.clear();
        for (App app : ordered) (hiddenSet.contains(app.pkg) ? hidden : visible).add(app);
    }

    private void saveOrder() {
        List<String> order = new ArrayList<>();
        for (App app : visible) order.add(app.pkg);
        for (App app : hidden) order.add(app.pkg);
        saveCsv("order", order);
    }

    // ---- grid -------------------------------------------------------------

    private void refresh() {
        dirty = false;
        View focused = getCurrentFocus();
        if (focusPkg == null && focused != null && focused.getTag(R.id.pkg) != null) {
            focusPkg = (String) focused.getTag(R.id.pkg);
        }
        loadApps();
        grid.removeAllViews();
        View toFocus = null;
        for (App app : visible) {
            View cell = tile(app.label, app.banner, app.icon, v -> launch(app), v -> {
                showMenu(app);
                return true;
            });
            View card = cell.findViewWithTag("card");
            card.setTag(R.id.pkg, app.pkg);
            if (app.pkg.equals(focusPkg)) toFocus = card;
            grid.addView(cell);
        }
        if (!hidden.isEmpty()) {
            grid.addView(tile("Hidden apps (" + hidden.size() + ")", null, null, v -> showHidden(), null));
        }
        focusPkg = null;
        if (toFocus == null && grid.getChildCount() > 0) toFocus = grid.getChildAt(0).findViewWithTag("card");
        if (toFocus != null) toFocus.requestFocus();
    }

    private View tile(CharSequence label, Drawable banner, Drawable icon,
                      View.OnClickListener click, View.OnLongClickListener longClick) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(dp(8), dp(8), dp(8), dp(8));
        cell.setLayoutParams(lp);

        FrameLayout card = new FrameLayout(this);
        card.setTag("card");
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF1E2A3A);
        card.setBackground(bg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        card.setClipToOutline(true);
        card.setOutlineAmbientShadowColor(ACCENT);
        card.setOutlineSpotShadowColor(ACCENT);

        if (banner != null) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (icon != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(icon);
            card.addView(img, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        } else {
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setGravity(Gravity.CENTER);
            card.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), Color.WHITE);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        name.setTextColor(TEXT_DIM);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setPadding(0, dp(6), 0, 0);

        card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            v.setElevation(focused ? dp(14) : 0);
            card.setForeground(focused ? ring : null);
            name.setTextColor(focused ? Color.WHITE : TEXT_DIM);
            if (focused) {
                int top = cell.getTop();
                int bottom = cell.getBottom();
                if (top < scroll.getScrollY() || bottom > scroll.getScrollY() + scroll.getHeight()) {
                    scroll.smoothScrollTo(0, Math.max(0, top - dp(10)));
                }
            }
        });
        card.setOnClickListener(click);
        if (longClick != null) card.setOnLongClickListener(longClick);

        cell.addView(card, new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)));
        cell.addView(name, new LinearLayout.LayoutParams(dp(TILE_W), ViewGroup.LayoutParams.WRAP_CONTENT));
        return cell;
    }

    private void launch(App app) {
        Intent intent = Intent.makeMainActivity(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        safeStart(intent);
    }

    // ---- long-press menu --------------------------------------------------

    private void showMenu(App app) {
        int index = visible.indexOf(app);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (index > 0) {
            labels.add("Move left");
            actions.add(() -> move(app, index - 1));
            labels.add("Move to start");
            actions.add(() -> move(app, 0));
        }
        if (index < visible.size() - 1) {
            labels.add("Move right");
            actions.add(() -> move(app, index + 1));
        }
        labels.add("Hide");
        actions.add(() -> setHidden(app, true));
        labels.add("App info (force stop, clear cache)");
        actions.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.pkg))));
        if (!app.system) {
            labels.add("Uninstall");
            actions.add(() -> safeStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg))));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void move(App app, int to) {
        visible.remove(app);
        visible.add(to, app);
        saveOrder();
        focusPkg = app.pkg;
        refresh();
    }

    private void setHidden(App app, boolean hide) {
        List<String> list = csv("hidden", DEFAULT_HIDDEN);
        list.remove(app.pkg);
        if (hide) list.add(app.pkg);
        saveCsv("hidden", list);
        focusPkg = hide ? null : app.pkg;
        refresh();
    }

    private void showHidden() {
        CharSequence[] labels = new CharSequence[hidden.size()];
        for (int i = 0; i < hidden.size(); i++) labels[i] = hidden.get(i).label;
        List<App> snapshot = new ArrayList<>(hidden);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hidden apps")
                .setItems(labels, (d, which) -> {
                    App app = snapshot.get(which);
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(app.label)
                            .setItems(new CharSequence[]{"Open", "Show on home"}, (d2, w) -> {
                                if (w == 0) launch(app);
                                else setHidden(app, false);
                            })
                            .show();
                })
                .show();
    }
}
