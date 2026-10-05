package com.mirai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    static final String[][] DEF = {
        {"shape", "Форма иконок", "Оригинал,Скруглённые,Круглые,Квадрат"},
        {"size", "Размер иконок", "Малый,Средний,Большой"},
        {"cols", "Колонок на столе", "4,5,6"},
        {"dcols", "Колонок в списке", "4,5,6"},
        {"hl", "Подписи на столе", "Выкл,Вкл"},
        {"blur", "Размытие фона", "Выкл,Слабое,Среднее,Сильное"},
        {"glass", "Стекло на виджетах", "Выкл,Вкл"},
        {"clock", "Стиль часов", "ColorOS,iOS,HyperOS,OriginOS,Скрыть"},
        {"card", "Карточка рядом с часами", "Дата,Батарея,Скрыть,Погода"},
        {"swipe", "Свайп вверх", "Список приложений,Выкл"},
        {"dtap", "Двойной тап", "Выкл,Настройки,Список приложений"},
        {"dock", "Док", "Показать,Скрыть"}};
    PackageManager pm;
    SharedPreferences sp;
    List<String> all = new ArrayList<>(), shown = new ArrayList<>(), home = new ArrayList<>();
    Set<String> known = new HashSet<>();
    Map<String, String> labels = new ConcurrentHashMap<>();
    Map<String, Bitmap> bmps = new ConcurrentHashMap<>();
    BaseAdapter drawerAd, homeAd;
    Glass dock, drawer, panel;
    LinearLayout top, box;
    GridView homeGrid, grid;
    EditText search;
    GestureDetector gd;
    Dialog folderDlg;
    TextView batTv, batSub;
    int isz;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    int g(String k) { return sp.getInt("o_" + k, (k.equals("shape") || k.equals("size") || k.equals("glass")) ? 1 : 0); }

    LinearLayout.LayoutParams lp(int w, int h, float wt) { return new LinearLayout.LayoutParams(w, h, wt); }

    TextView tv(String s, int size, int col) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(col);
        return t;
    }

    TextClock tc(String f, int size, int col) {
        TextClock t = new TextClock(this);
        t.setFormat24Hour(f);
        t.setFormat12Hour(f);
        t.setTextSize(size);
        t.setTextColor(col);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return t;
    }

    void prefs() {
        int[] s = {52, 60, 68};
        isz = dp(s[g("size")]);
        homeGrid.setNumColumns(4 + g("cols"));
        grid.setNumColumns(4 + g("dcols"));
    }

    Bitmap rounded(Drawable d) {
        int s = isz, shape = g("shape");
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (shape == 0) { d.setBounds(0, 0, s, s); d.draw(c); return b; }
        float r = shape == 2 ? s / 2f : shape == 3 ? s * 0.12f : s * 0.28f;
        Path p = new Path();
        p.addRoundRect(0, 0, s, s, r, r, Path.Direction.CW);
        c.clipPath(p);
        if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int o = s / 4;
            if (a.getBackground() != null) { a.getBackground().setBounds(-o, -o, s + o, s + o); a.getBackground().draw(c); }
            if (a.getForeground() != null) { a.getForeground().setBounds(-o, -o, s + o, s + o); a.getForeground().draw(c); }
        } else {
            c.drawColor(Color.WHITE);
            int i = s / 7;
            d.setBounds(i, i, s - i, s - i);
            d.draw(c);
        }
        return b;
    }

    View newCell() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(new ImageView(this), new LinearLayout.LayoutParams(isz, isz));
        TextView t = tv("", 11, Color.WHITE);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(4, 0, 1, 0x99000000);
        t.setPadding(0, dp(4), 0, 0);
        c.addView(t);
        return c;
    }

    View bind(View v, String pkg, boolean label) {
        LinearLayout c = (LinearLayout) v;
        ((ImageView) c.getChildAt(0)).setImageBitmap(bmps.get(pkg));
        TextView t = (TextView) c.getChildAt(1);
        t.setVisibility(label ? View.VISIBLE : View.GONE);
        t.setText(labels.get(pkg));
        c.setOnClickListener(x -> launch(pkg));
        c.setOnLongClickListener(x -> { menu(pkg); return true; });
        c.setOnTouchListener((x, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(.9f).scaleY(.9f).setDuration(90).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            return false;
        });
        return c;
    }

    View appCell(String pkg, boolean label) { return bind(newCell(), pkg, label); }

    int fid(String it) { return Integer.parseInt(it.split(":", 3)[1]); }

    List<String> fpk(String it) {
        List<String> l = new ArrayList<>();
        for (String p : it.split(":", 3)[2].split("\\|")) if (!p.isEmpty() && known.contains(p)) l.add(p);
        return l;
    }

    View folderCell(String it) {
        List<String> pk = fpk(it);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        Glass box = new Glass(this, 18, 0x22FFFFFF);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                ImageView iv = new ImageView(this);
                if (i < pk.size()) iv.setImageBitmap(bmps.get(pk.get(i)));
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(isz * 2 / 5, isz * 2 / 5);
                l.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(iv, l);
            }
            box.addView(row);
        }
        wrap.addView(box, new LinearLayout.LayoutParams(isz, isz));
        wrap.setOnClickListener(v -> openFolder(it));
        wrap.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setItems(new String[]{"Расформировать папку"}, (d, w) -> {
                home.remove(it);
                home.addAll(fpk(it));
                saveHome();
                apply(all);
            }).show();
            return true;
        });
        return wrap;
    }

    void openFolder(String it) {
        GridView gv = new GridView(this);
        gv.setNumColumns(3);
        gv.setVerticalSpacing(dp(14));
        gv.setPadding(dp(16), dp(20), dp(16), dp(20));
        final List<String> pk = fpk(it);
        gv.setAdapter(new BaseAdapter() {
            public int getCount() { return pk.size(); }
            public Object getItem(int i) { return pk.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) { return appCell(pk.get(i), true); }
        });
        Glass wrap = new Glass(this, 32, 0xAA181820);
        wrap.addView(gv, new LinearLayout.LayoutParams(-1, -2));
        folderDlg = new AlertDialog.Builder(this).setView(wrap).create();
        folderDlg.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        folderDlg.show();
        wrap.setScaleX(.8f);
        wrap.setScaleY(.8f);
        wrap.setAlpha(0f);
        wrap.animate().scaleX(1f).scaleY(1f).alpha(1f).setInterpolator(new OvershootInterpolator(1.1f)).setDuration(260).start();
    }

    void launch(String pkg) {
        if (folderDlg != null) folderDlg.dismiss();
        Intent in = pm.getLaunchIntentForPackage(pkg);
        if (in != null) startActivity(in);
    }

    void saveHome() { sp.edit().putString("home", TextUtils.join(",", home)).apply(); }

    List<String> getDock() {
        List<String> l = new ArrayList<>();
        for (String p : sp.getString("dock", "").split(",")) if (known.contains(p)) l.add(p);
        return l;
    }

    void strip(String pkg) {
        for (int i = home.size() - 1; i >= 0; i--) {
            String it = home.get(i);
            if (it.equals(pkg)) home.remove(i);
            else if (it.startsWith("f:")) {
                List<String> l = fpk(it);
                if (l.remove(pkg)) {
                    if (l.isEmpty()) home.remove(i);
                    else home.set(i, "f:" + fid(it) + ":" + TextUtils.join("|", l));
                }
            }
        }
    }

    void menu(String pkg) {
        String[] items = {"На рабочий стол", "В док", "В папку", "Убрать", "О приложении"};
        new AlertDialog.Builder(this).setTitle(labels.get(pkg)).setItems(items, (d, w) -> {
            if (folderDlg != null) folderDlg.dismiss();
            List<String> dk = getDock();
            if (w == 0) { strip(pkg); home.add(pkg); }
            if (w == 1) { if (!dk.contains(pkg)) dk.add(pkg); if (dk.size() > 4) dk.remove(0); }
            if (w == 2) { chooseFolder(pkg); return; }
            if (w == 3) { strip(pkg); dk.remove(pkg); }
            if (w == 4) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                return;
            }
            sp.edit().putString("dock", TextUtils.join(",", dk)).apply();
            saveHome();
            apply(all);
        }).show();
    }

    void chooseFolder(String pkg) {
        List<Integer> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int max = 0;
        for (String it : home) if (it.startsWith("f:")) {
            ids.add(fid(it));
            names.add("Папка " + fid(it));
            max = Math.max(max, fid(it));
        }
        names.add("Новая папка");
        final int next = max + 1;
        new AlertDialog.Builder(this).setTitle("В папку").setItems(names.toArray(new String[0]), (d, w) -> {
            int id = w < ids.size() ? ids.get(w) : next;
            strip(pkg);
            int pos = -1;
            for (int i = 0; i < home.size(); i++)
                if (home.get(i).startsWith("f:") && fid(home.get(i)) == id) pos = i;
            if (pos >= 0) {
                List<String> l = fpk(home.get(pos));
                l.add(pkg);
                home.set(pos, "f:" + id + ":" + TextUtils.join("|", l));
            } else home.add("f:" + id + ":" + pkg);
            saveHome();
            apply(all);
        }).show();
    }

    // ---------- виджеты ----------
    void buildTop() {
        top.removeAllViews();
        int ck = g("clock"), cd = g("card"), h = dp(160);
        if (ck == 3) {
            Glass w = new Glass(this, 30, 0x40FFFFFF);
            w.setOrientation(LinearLayout.VERTICAL);
            w.setGravity(Gravity.CENTER_VERTICAL);
            w.setPadding(dp(24), 0, dp(24), 0);
            w.addView(tc("HH:mm", 52, Color.WHITE));
            w.addView(tc("EEEE, d MMMM", 15, 0xDDFFFFFF));
            top.addView(w, lp(-1, dp(120), 0));
            return;
        }
        View a = null;
        if (ck == 0 || ck == 1) a = new ClockView(this, ck == 1);
        else if (ck == 2) {
            Glass c = new Glass(this, 30, 0x40FFFFFF);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.addView(tc("HH", 50, Color.WHITE));
            c.addView(tc("mm", 50, 0xFFFF6A3D));
            c.addView(tc("EEE, d MMM", 13, 0xCCFFFFFF));
            a = c;
        }
        if (a != null) {
            LinearLayout.LayoutParams l = lp(0, h, 1f);
            l.rightMargin = cd == 2 ? 0 : dp(8);
            top.addView(a, l);
        }
        if (cd < 2) {
            Glass c = new Glass(this, 30, 0x40FFFFFF);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            if (cd == 0) {
                c.addView(tc("d", 56, Color.WHITE));
                c.addView(tc("EEEE", 16, 0xE6FFFFFF));
                c.addView(tc("MMMM", 14, 0xB3FFFFFF));
            } else {
                batTv = tv("", 44, Color.WHITE);
                batSub = tv("", 15, 0xCCFFFFFF);
                batTv.setGravity(Gravity.CENTER);
                batSub.setGravity(Gravity.CENTER);
                c.addView(batTv);
                c.addView(batSub);
                bat();
            }
            LinearLayout.LayoutParams l = lp(0, h, 1f);
            l.leftMargin = a != null ? dp(8) : 0;
            top.addView(c, l);
        }
    }

    void bat() {
        if (batTv == null) return;
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return;
        int l = i.getIntExtra("level", 0), sc = Math.max(1, i.getIntExtra("scale", 100));
        batTv.setText((l * 100 / sc) + "%");
        batSub.setText(i.getIntExtra("status", 0) == 2 ? "Заряжается" : "Батарея");
    }

    void loadBlur() {
        new Thread(() -> {
            try {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                Drawable d = WallpaperManager.getInstance(this).getDrawable();
                if (d == null) return;
                int W = dm.widthPixels, H = dm.heightPixels, sw = Math.max(1, W / 14), sh = Math.max(1, H / 14);
                Bitmap t = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(t);
                float iw = Math.max(1, d.getIntrinsicWidth()), ih = Math.max(1, d.getIntrinsicHeight());
                float sc = Math.max(sw / iw, sh / ih);
                c.translate((sw - iw * sc) / 2, (sh - ih * sc) / 2);
                c.scale(sc, sc);
                d.setBounds(0, 0, (int) iw, (int) ih);
                d.draw(c);
                Glass.blur = Bitmap.createScaledBitmap(t, W, H, true);
                runOnUiThread(Glass::refresh);
            } catch (Throwable e) { }
        }).start();
    }

    void applyBlur() {
        int lv = g("blur");
        getWindow().setBackgroundDrawable(new ColorDrawable(lv > 0 ? 0x22000000 : 0));
        if (Build.VERSION.SDK_INT >= 31) {
            try { getWindow().setBackgroundBlurRadius(new int[]{0, 25, 60, 120}[lv]); } catch (Throwable t) { }
        }
    }

    // ---------- настройки ----------
    TextView link(String s, View.OnClickListener l) {
        TextView t = tv(s, 16, 0xFFFFFFFF);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        t.setOnClickListener(l);
        return t;
    }

    void fill() {
        box.removeAllViews();
        for (String[] d : DEF) {
            String[] o = d[2].split(",");
            LinearLayout row = new LinearLayout(this);
            row.setPadding(dp(8), dp(14), dp(8), dp(14));
            row.addView(tv(d[1], 16, Color.WHITE), lp(0, -2, 1f));
            row.addView(tv(o[g(d[0])], 16, 0xFFFF9F0A));
            row.setOnClickListener(v -> {
                sp.edit().putInt("o_" + d[0], (g(d[0]) + 1) % o.length).apply();
                onPrefs();
            });
            box.addView(row);
        }
        box.addView(link("Скрытые приложения", v -> hiddenDlg()));
        box.addView(link("Сбросить рабочий стол и док", v -> {
            sp.edit().remove("home").remove("dock").apply();
            onPrefs();
        }));
        box.addView(link("Закрыть", v -> hide(panel)));
    }

    void onPrefs() {
        prefs();
        Glass.on = g("glass") == 1;
        applyBlur();
        homeGrid.setAdapter(homeAd);
        grid.setAdapter(drawerAd);
        bmps.clear();
        labels.clear();
        reload();
        buildTop();
        dock.setVisibility(g("dock") == 0 ? View.VISIBLE : View.GONE);
        Glass.refresh();
        fill();
    }

    void hiddenDlg() {
        Set<String> hid = new HashSet<>(Arrays.asList(sp.getString("hid", "").split(",")));
        String[] n = new String[all.size()];
        boolean[] c = new boolean[n.length];
        for (int i = 0; i < n.length; i++) { n[i] = labels.get(all.get(i)); c[i] = hid.contains(all.get(i)); }
        new AlertDialog.Builder(this).setTitle("Скрытые приложения")
                .setMultiChoiceItems(n, c, (d, i, ch) -> c[i] = ch)
                .setPositiveButton("OK", (d, w) -> {
                    List<String> l = new ArrayList<>();
                    for (int i = 0; i < n.length; i++) if (c[i]) l.add(all.get(i));
                    sp.edit().putString("hid", TextUtils.join(",", l)).apply();
                    filter();
                }).show();
    }

    // ---------- создание ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        sp = getSharedPreferences("mirai", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);

        FrameLayout root = new FrameLayout(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(56), dp(18), dp(16));

        top = new LinearLayout(this);
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        homeGrid = new GridView(this);
        homeGrid.setVerticalSpacing(dp(22));
        homeGrid.setSelector(android.R.color.transparent);
        homeAd = new BaseAdapter() {
            public int getCount() { return home.size(); }
            public Object getItem(int i) { return home.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                String it = home.get(i);
                return it.startsWith("f:") ? folderCell(it) : appCell(it, g("hl") == 1);
            }
        };
        homeGrid.setAdapter(homeAd);
        LinearLayout.LayoutParams gp = lp(-1, 0, 1f);
        gp.topMargin = dp(28);
        page.addView(homeGrid, gp);

        Glass pill = new Glass(this, 24, 0x40000000);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(30), dp(10), dp(30), dp(10));
        pill.addView(tv("Search", 15, Color.WHITE));
        pill.setOnClickListener(v -> openDrawer());
        pill.setOnLongClickListener(v -> { openPanel(); return true; });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.bottomMargin = dp(16);
        page.addView(pill, pl);

        dock = new Glass(this, 34, 0x33FFFFFF);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(10), dp(12), dp(10), dp(12));
        page.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        root.addView(page);

        drawer = new Glass(this, 0, 0xB3101018);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(16), dp(56), dp(16), dp(8));
        drawer.setVisibility(View.GONE);
        Glass sb = new Glass(this, 22, 0x22FFFFFF);
        search = new EditText(this);
          search.setHint("Поиск приложений");
        search.setHintTextColor(0x99FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setPadding(dp(18), dp(12), dp(18), dp(12));
        sb.addView(search, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.bottomMargin = dp(16);
        drawer.addView(sb, sl);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) { filter(); }
            public void afterTextChanged(Editable e) {}
        });
        grid = new GridView(this);
        grid.setVerticalSpacing(dp(16));
        grid.setSelector(android.R.color.transparent);
        drawerAd = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                if (v == null) v = newCell();
                return bind(v, shown.get(i), true);
            }
        };
        grid.setAdapter(drawerAd);
        drawer.addView(grid, lp(-1, 0, 1f));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        panel = new Glass(this, 0, 0xD0101018);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(56), dp(20), dp(16));
        panel.setVisibility(View.GONE);
        panel.addView(tv("Настройки MiraiUi", 22, Color.WHITE));
        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sv.addView(box);
        panel.addView(sv, lp(-1, 0, 1f));
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));

        prefs();
        Glass.on = g("glass") == 1;
        setContentView(root);
        applyBlur();
        buildTop();
        dock.setVisibility(g("dock") == 0 ? View.VISIBLE : View.GONE);
        fill();
        loadBlur();

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 != null && g("swipe") == 0 && vy < -1200 && Math.abs(vy) > Math.abs(vx)) openDrawer();
                return false;
            }
            public boolean onDoubleTap(MotionEvent e) {
                int a = g("dtap");
                if (a == 1) openPanel();
                if (a == 2) openDrawer();
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    void show(View v) {
        v.setVisibility(View.VISIBLE);
        v.setTranslationY(dp(120));
        v.setAlpha(0f);
        v.animate().translationY(0).alpha(1f).setInterpolator(new DecelerateInterpolator(2f)).setDuration(260).start();
    }

    void hide(View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        v.animate().translationY(dp(120)).alpha(0f).setDuration(200).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    void openDrawer() {
        if (drawer.getVisibility() == View.GONE && panel.getVisibility() == View.GONE) show(drawer);
    }

    void openPanel() {
        if (panel.getVisibility() == View.GONE) { fill(); show(panel); }
    }

    void closeAll() {
        search.setText("");
        hide(drawer);
        hide(panel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        bat();
    }

    void reload() {
        new Thread(() -> {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<String> pk = new ArrayList<>();
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                String p = r.activityInfo.packageName;
                if (pk.contains(p)) continue;
                pk.add(p);
                if (!labels.containsKey(p)) {
                    labels.put(p, r.loadLabel(pm).toString());
                    bmps.put(p, rounded(r.loadIcon(pm)));
                }
            }
            Collections.sort(pk, (a, c) -> labels.get(a).compareToIgnoreCase(labels.get(c)));
            runOnUiThread(() -> apply(pk));
        }).start();
    }

    void apply(List<String> pk) {
        all = pk;
        known = new HashSet<>(pk);
        if (!sp.contains("dock")) {
            List<String> d = new ArrayList<>();
            for (String k : new String[]{"dialer", "contacts", "mms", "camera"})
                for (String p : all)
                    if (p.contains(k)) { d.add(p); break; }
            sp.edit().putString("dock", TextUtils.join(",", d)).apply();
        }
        home.clear();
        for (String it : sp.getString("home", "").split(",")) {
            if (it.startsWith("f:") ? !fpk(it).isEmpty() : known.contains(it)) home.add(it);
        }
        homeAd.notifyDataSetChanged();
        dock.removeAllViews();
        for (String p : getDock())
            dock.addView(appCell(p, false), new LinearLayout.LayoutParams(0, -2, 1f));
        filter();
    }

    void filter() {
        String s = search.getText().toString().trim().toLowerCase();
        Set<String> hid = new HashSet<>(Arrays.asList(sp.getString("hid", "").split(",")));
        shown.clear();
        for (String p : all)
            if (!hid.contains(p) && (s.isEmpty() || labels.get(p).toLowerCase().contains(s))) shown.add(p);
        drawerAd.notifyDataSetChanged();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (folderDlg != null) folderDlg.dismiss();
        closeAll();
    }

    @Override
    public void onBackPressed() {
        if (folderDlg != null && folderDlg.isShowing()) folderDlg.dismiss();
        else closeAll();
    }
}
