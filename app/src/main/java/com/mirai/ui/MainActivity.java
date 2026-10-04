package com.mirai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    PackageManager pm;
    SharedPreferences sp;
    List<ResolveInfo> all = new ArrayList<>(), shown = new ArrayList<>(), home = new ArrayList<>();
    Map<String, ResolveInfo> byPkg = new HashMap<>();
    BaseAdapter drawerAd, homeAd;
    LinearLayout dock, drawer;
    EditText search;
    GestureDetector gd;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    View cell(ResolveInfo ri, boolean label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(ri.loadIcon(pm));
        ic.setClipToOutline(true);
        ic.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(15)); }
        });
        c.addView(ic, new LinearLayout.LayoutParams(dp(56), dp(56)));
        if (label) {
            TextView t = new TextView(this);
            t.setText(ri.loadLabel(pm));
            t.setTextColor(Color.WHITE);
            t.setTextSize(11);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(4, 0, 1, 0x99000000);
            t.setPadding(0, dp(4), 0, 0);
            c.addView(t);
        }
        c.setOnClickListener(v -> launch(ri));
        c.setOnLongClickListener(v -> { menu(ri); return true; });
        return c;
    }

    void launch(ResolveInfo ri) {
        Intent in = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
        if (in != null) startActivity(in);
    }

    List<String> get(String key) {
        String s = sp.getString(key, "");
        List<String> l = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty() && byPkg.containsKey(p)) l.add(p);
        return l;
    }

    void put(String key, List<String> l) {
        sp.edit().putString(key, android.text.TextUtils.join(",", l)).apply();
    }

    void menu(ResolveInfo ri) {
        String pkg = ri.activityInfo.packageName;
        String[] items = {"На рабочий стол", "В док", "Убрать с рабочего стола и дока", "О приложении"};
        new AlertDialog.Builder(this).setTitle(ri.loadLabel(pm)).setItems(items, (d, w) -> {
            List<String> h = get("home"), dk = get("dock");
            if (w == 0 && !h.contains(pkg)) h.add(pkg);
            if (w == 1 && !dk.contains(pkg)) { dk.add(pkg); if (dk.size() > 4) dk.remove(0); }
            if (w == 2) { h.remove(pkg); dk.remove(pkg); }
            if (w == 3) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                return;
            }
            put("home", h);
            put("dock", dk);
            reload();
        }).show();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        sp = getSharedPreferences("mirai", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);

        FrameLayout root = new FrameLayout(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(48), dp(16), dp(16));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(72);
        time.setTextColor(Color.WHITE);
        time.setGravity(Gravity.CENTER);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setShadowLayer(8, 0, 2, 0x66000000);
        page.addView(time, new LinearLayout.LayoutParams(-1, -2));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(16);
        date.setTextColor(0xE6FFFFFF);
        date.setGravity(Gravity.CENTER);
        date.setShadowLayer(6, 0, 1, 0x66000000);
        page.addView(date, new LinearLayout.LayoutParams(-1, -2));

        GridView homeGrid = new GridView(this);
        homeGrid.setNumColumns(4);
        homeGrid.setVerticalSpacing(dp(16));
        homeGrid.setSelector(android.R.color.transparent);
        homeAd = new BaseAdapter() {
            public int getCount() { return home.size(); }
            public Object getItem(int i) { return home.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) { return cell(home.get(i), true); }
        };
        homeGrid.setAdapter(homeAd);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1f);
        gp.topMargin = dp(32);
        page.addView(homeGrid, gp);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(round(0x33FFFFFF, 30));
        dock.setPadding(dp(12), dp(14), dp(12), dp(14));
        page.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        root.addView(page);

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xE60D0D12);
        drawer.setPadding(dp(16), dp(48), dp(16), dp(8));
        drawer.setVisibility(View.GONE);

        search = new EditText(this);
        search.setHint("Поиск приложений");
        search.setHintTextColor(0x99FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackground(round(0x22FFFFFF, 22));
        search.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1, -2);
        sp2.bottomMargin = dp(16);
        drawer.addView(search, sp2);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) { filter(); }
            public void afterTextChanged(Editable e) {}
        });

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(16));
        grid.setSelector(android.R.color.transparent);
        drawerAd = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) { return cell(shown.get(i), true); }
        };
        grid.setAdapter(drawerAd);
        drawer.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 != null && vy < -1200 && Math.abs(vy) > Math.abs(vx) && drawer.getVisibility() == View.GONE) openDrawer();
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    void openDrawer() {
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(getResources().getDisplayMetrics().heightPixels);
        drawer.animate().translationY(0).setDuration(220).start();
    }

    void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        search.setText("");
        drawer.animate().translationY(getResources().getDisplayMetrics().heightPixels).setDuration(200)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    void reload() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        all = pm.queryIntentActivities(q, 0);
        Collections.sort(all, (a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));
        byPkg.clear();
        for (ResolveInfo r : all) byPkg.put(r.activityInfo.packageName, r);

        if (!sp.contains("dock")) {
            List<String> d = new ArrayList<>();
            for (String k : new String[]{"dialer", "mms", "chrome", "camera"})
                for (ResolveInfo r : all)
                    if (r.activityInfo.packageName.contains(k)) { d.add(r.activityInfo.packageName); break; }
            put("dock", d);
        }
        home.clear();
        for (String p : get("home")) home.add(byPkg.get(p));
        homeAd.notifyDataSetChanged();

        dock.removeAllViews();
        for (String p : get("dock")) {
            View c = cell(byPkg.get(p), false);
            dock.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        filter();
    }

    void filter() {
        String s = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (ResolveInfo r : all)
            if (s.isEmpty() || r.loadLabel(pm).toString().toLowerCase().contains(s)) shown.add(r);
        drawerAd.notifyDataSetChanged();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        closeDrawer();
    }

    @Override
    public void onBackPressed() { closeDrawer(); }
}
