package com.mirai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    PackageManager pm;
    SharedPreferences sp;
    List<String> all = new ArrayList<>(), shown = new ArrayList<>(), home = new ArrayList<>();
    Set<String> known = new HashSet<>();
    Map<String, String> labels = new ConcurrentHashMap<>();
    Map<String, Bitmap> bmps = new ConcurrentHashMap<>();
    BaseAdapter drawerAd, homeAd;
    LinearLayout dock, drawer;
    GridView homeGrid, grid;
    EditText search;
    GestureDetector gd;
    Dialog folderDlg;
    int isz;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    GradientDrawable glass(int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66FFFFFF, 0x26FFFFFF});
        g.setCornerRadius(dp(r));
        g.setStroke(dp(1), 0x55FFFFFF);
        return g;
    }

    void prefs() {
        int[] s = {52, 60, 68};
        isz = dp(s[sp.getInt("size", 1)]);
        int cols = sp.getInt("cols", 4);
        homeGrid.setNumColumns(cols);
        grid.setNumColumns(cols);
    }

    Bitmap rounded(Drawable d) {
        int s = isz, shape = sp.getInt("shape", 1);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (shape == 0) {
            d.setBounds(0, 0, s, s);
            d.draw(c);
            return b;
        }
        float r = shape == 2 ? s / 2f : s * 0.28f;
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

    class ClockView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ClockView(Context c) { super(c); }
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = w / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            cv.drawRoundRect(0, 0, w, h, dp(30), dp(30), p);
            p.setColor(0xFF1A1A1A);
            p.setTextSize(dp(22));
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("12", cx, dp(36), p);
            cv.drawText("6", cx, h - dp(16), p);
            cv.drawText("3", w - dp(22), cy + dp(8), p);
            cv.drawText("9", dp(22), cy + dp(8), p);
            Calendar c = Calendar.getInstance();
            float s = c.get(Calendar.SECOND), m = c.get(Calendar.MINUTE) + s / 60, hr = c.get(Calendar.HOUR) + m / 60;
            hand(cv, cx, cy, hr * 30, r * 0.45f, dp(5), 0xFF1A1A1A);
            hand(cv, cx, cy, m * 6, r * 0.7f, dp(5), 0xFF1A1A1A);
            hand(cv, cx, cy, s * 6, r * 0.75f, dp(2), 0xFFFF3B30);
            postInvalidateDelayed(1000);
        }
        void hand(Canvas cv, float cx, float cy, float deg, float len, float wd, int col) {
            p.setColor(col);
            p.setStrokeWidth(wd);
            p.setStrokeCap(Paint.Cap.ROUND);
            double a = Math.toRadians(deg - 90);
            cv.drawLine(cx, cy, (float) (cx + len * Math.cos(a)), (float) (cy + len * Math.sin(a)), p);
        }
    }

    View newCell() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(new ImageView(this), new LinearLayout.LayoutParams(isz, isz));
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(11);
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
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(glass(18));
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                ImageView iv = new ImageView(this);
                if (i < pk.size()) iv.setImageBitmap(bmps.get(pk.get(i)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(isz * 2 / 5, isz * 2 / 5);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(iv, lp);
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
        GridView g = new GridView(this);
        g.setNumColumns(3);
        g.setVerticalSpacing(dp(14));
        g.setPadding(dp(16), dp(20), dp(16), dp(20));
        final List<String> pk = fpk(it);
        g.setAdapter(new BaseAdapter() {
            public int getCount() { return pk.size(); }
            public Object getItem(int i) { return pk.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) { return appCell(pk.get(i), true); }
        });
        folderDlg = new AlertDialog.Builder(this).setView(g).create();
        folderDlg.getWindow().setBackgroundDrawable(round(0xDD20202A, 32));
        folderDlg.show();
        blurWin(folderDlg.getWindow(), true);
        g.setScaleX(.8f);
        g.setScaleY(.8f);
        g.setAlpha(0f);
        g.animate().scaleX(1f).scaleY(1f).alpha(1f).setInterpolator(new OvershootInterpolator(1.1f)).setDuration(260).start();
    }

    void launch(String pkg) {
        if (folderDlg != null) folderDlg.dismiss();
        Intent in = pm.getLaunchIntentForPackage(pkg);
        if (in != null) startActivity(in);
    }

    void blurWin(Window w, boolean on) {
        if (Build.VERSION.SDK_INT < 31 || !sp.getBoolean("blur", false)) return;
        try {
            WindowManager.LayoutParams lp = w.getAttributes();
            if (on) w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            lp.setBlurBehindRadius(on ? 50 : 0);
            w.setAttributes(lp);
        } catch (Throwable t) { }
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

    void settings() {
        String[] shapes = {"Оригинал", "Скруглённые", "Круглые"};
        int[] sizes = {52, 60, 68};
        String[] it = {
                "Форма иконок: " + shapes[sp.getInt("shape", 1)],
                "Размер иконок: " + sizes[sp.getInt("size", 1)],
                "Колонок: " + sp.getInt("cols", 4),
                "Размытие: " + (sp.getBoolean("blur", false) ? "вкл" : "выкл"),
                "Подписи на столе: " + (sp.getBoolean("hl", false) ? "вкл" : "выкл")};
        new AlertDialog.Builder(this).setTitle("MiraiUi").setItems(it, (d, w) -> {
            SharedPreferences.Editor e = sp.edit();
            if (w == 0) e.putInt("shape", (sp.getInt("shape", 1) + 1) % 3);
            if (w == 1) e.putInt("size", (sp.getInt("size", 1) + 1) % 3);
            if (w == 2) e.putInt("cols", sp.getInt("cols", 4) == 4 ? 5 : 4);
            if (w == 3) e.putBoolean("blur", !sp.getBoolean("blur", false));
            if (w == 4) e.putBoolean("hl", !sp.getBoolean("hl", false));
            e.apply();
            prefs();
            homeGrid.setAdapter(homeAd);
            grid.setAdapter(drawerAd);
            bmps.clear();
            labels.clear();
            reload();
            settings();
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
        page.setPadding(dp(18), dp(56), dp(18), dp(16));

        LinearLayout top = new LinearLayout(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(160), 1f);
        cp.rightMargin = dp(8);
        top.addView(new ClockView(this), cp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(glass(30));
        String[][] f = {{"d", "56", "FFFFFFFF"}, {"EEEE", "16", "E6FFFFFF"}, {"MMMM", "14", "B3FFFFFF"}};
        for (String[] x : f) {
            TextClock t = new TextClock(this);
            t.setFormat24Hour(x[0]);
            t.setFormat12Hour(x[0]);
            t.setTextSize(Integer.parseInt(x[1]));
            t.setTextColor((int) Long.parseLong(x[2], 16));
            t.setGravity(Gravity.CENTER);
            card.addView(t);
        }
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(0, dp(160), 1f);
        dp2.leftMargin = dp(8);
        top.addView(card, dp2);
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
                return it.startsWith("f:") ? folderCell(it) : appCell(it, sp.getBoolean("hl", false));
            }
        };
        homeGrid.setAdapter(homeAd);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1f);
        gp.topMargin = dp(28);
        page.addView(homeGrid, gp);

        TextView pill = new TextView(this);
        pill.setText("🔍  Search");
        pill.setTextColor(Color.WHITE);
        pill.setTextSize(15);
        pill.setPadding(dp(26), dp(10), dp(26), dp(10));
        pill.setBackground(glass(24));
        pill.setOnClickListener(v -> openDrawer());
        pill.setOnLongClickListener(v -> { settings(); return true; });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.bottomMargin = dp(16);
        page.addView(pill, pl);

        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(glass(34));
        dock.setPadding(dp(10), dp(12), dp(10), dp(12));
        page.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        root.addView(page);

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xE60D0D12);
        drawer.setPadding(dp(16), dp(56), dp(16), dp(8));
        drawer.setVisibility(View.GONE);
        search = new EditText(this);
        search.setHint("Поиск приложений");
        search.setHintTextColor(0x99FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackground(glass(22));
        search.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1, -2);
        sp2.bottomMargin = dp(16);
        drawer.addView(search, sp2);
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
        drawer.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        prefs();
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
        int h = getResources().getDisplayMetrics().heightPixels;
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(h / 3f);
        drawer.setAlpha(0f);
        drawer.animate().translationY(0).alpha(1f).setInterpolator(new DecelerateInterpolator(2f)).setDuration(260).start();
        blurWin(getWindow(), true);
    }

    void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        search.setText("");
        blurWin(getWindow(), false);
        drawer.animate().translationY(getResources().getDisplayMetrics().heightPixels / 3f).alpha(0f)
                .setDuration(200).withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
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
        shown.clear();
        for (String p : all)
            if (s.isEmpty() || labels.get(p).toLowerCase().contains(s)) shown.add(p);
        drawerAd.notifyDataSetChanged();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (folderDlg != null) folderDlg.dismiss();
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (folderDlg != null && folderDlg.isShowing()) folderDlg.dismiss();
        else closeDrawer();
    }
}
