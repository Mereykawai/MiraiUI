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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    PackageManager pm;
    SharedPreferences sp;
    List<ResolveInfo> all = new ArrayList<>(), shown = new ArrayList<>();
    Map<String, ResolveInfo> byPkg = new HashMap<>();
    List<String> home = new ArrayList<>();
    BaseAdapter drawerAd, homeAd;
    LinearLayout dock, drawer;
    EditText search;
    GestureDetector gd;
    Dialog folderDlg;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
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

    ImageView icon(ResolveInfo ri) {
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(ri.loadIcon(pm));
        ic.setClipToOutline(true);
        ic.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(17)); }
        });
        return ic;
    }

    View appCell(ResolveInfo ri, boolean label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(icon(ri), new LinearLayout.LayoutParams(dp(60), dp(60)));
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

    int fid(String it) { return Integer.parseInt(it.split(":", 3)[1]); }

    List<String> fpk(String it) {
        List<String> l = new ArrayList<>();
        for (String p : it.split(":", 3)[2].split("\\|")) if (!p.isEmpty() && byPkg.containsKey(p)) l.add(p);
        return l;
    }

    View folderCell(String it) {
        List<String> pk = fpk(it);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(0x55FFFFFF, 18));
        box.setPadding(dp(5), dp(5), dp(5), dp(5));
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                ImageView iv = new ImageView(this);
                if (i < pk.size()) iv.setImageDrawable(byPkg.get(pk.get(i)).loadIcon(pm));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(21), dp(21));
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(iv, lp);
            }
            box.addView(row);
        }
        wrap.addView(box, new LinearLayout.LayoutParams(dp(60), dp(60)));
        wrap.setOnClickListener(v -> openFolder(it));
        wrap.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setItems(new String[]{"Расформировать папку"}, (d, w) -> {
                home.remove(it);
                home.addAll(fpk(it));
                saveHome();
                reload();
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
            public View getView(int i, View v, ViewGroup p) { return appCell(byPkg.get(pk.get(i)), true); }
        });
        folderDlg = new AlertDialog.Builder(this).setView(g).create();
        folderDlg.getWindow().setBackgroundDrawable(round(0xCC20202A, 30));
        folderDlg.show();
        setBlur(true);
        folderDlg.setOnDismissListener(d -> setBlur(false));
    }

    void launch(ResolveInfo ri) {
        if (folderDlg != null) folderDlg.dismiss();
        Intent in = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
        if (in != null) startActivity(in);
    }

    void setBlur(boolean on) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            lp.setBlurBehindRadius(on ? 60 : 0);
            getWindow().setAttributes(lp);
        } catch (Throwable t) { }
    }

    void saveHome() { sp.edit().putString("home", TextUtils.join(",", home)).apply(); }

    List<String> getDock() {
        List<String> l = new ArrayList<>();
        for (String p : sp.getString("dock", "").split(",")) if (byPkg.containsKey(p)) l.add(p);
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

    void menu(ResolveInfo ri) {
        String pkg = ri.activityInfo.packageName;
        String[] items = {"На рабочий стол", "В док", "В папку", "Убрать", "О приложении"};
        new AlertDialog.Builder(this).setTitle(ri.loadLabel(pm)).setItems(items, (d, w) -> {
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
            reload();
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
        page.setPadding(dp(18), dp(56), dp(18), dp(16));

        LinearLayout top = new LinearLayout(this);
        ClockView clock = new ClockView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(160), 1f);
        cp.rightMargin = dp(8);
        top.addView(clock, cp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(round(0x88506070, 30));
        TextClock dd = new TextClock(this);
        dd.setFormat24Hour("d");
        dd.setFormat12Hour("d");
        dd.setTextSize(56);
        dd.setTextColor(Color.WHITE);
        dd.setGravity(Gravity.CENTER);
        card.addView(dd);
        TextClock wd = new TextClock(this);
        wd.setFormat24Hour("EEEE");
        wd.setFormat12Hour("EEEE");
        wd.setTextSize(16);
        wd.setTextColor(0xE6FFFFFF);
        wd.setGravity(Gravity.CENTER);
        card.addView(wd);
        TextClock mm = new TextClock(this);
        mm.setFormat24Hour("MMMM");
        mm.setFormat12Hour("MMMM");
        mm.setTextSize(14);
        mm.setTextColor(0xB3FFFFFF);
        mm.setGravity(Gravity.CENTER);
        card.addView(mm);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(0, dp(160), 1f);
        dp2.leftMargin = dp(8);
        top.addView(card, dp2);
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        GridView homeGrid = new GridView(this);
        homeGrid.setNumColumns(4);
        homeGrid.setVerticalSpacing(dp(22));
        homeGrid.setSelector(android.R.color.transparent);
        homeAd = new BaseAdapter() {
            public int getCount() { return home.size(); }
            public Object getItem(int i) { return home.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                String it = home.get(i);
                return it.startsWith("f:") ? folderCell(it) : appCell(byPkg.get(it), false);
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
        pill.setBackground(round(0x66000000, 24));
        pill.setOnClickListener(v -> openDrawer());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.bottomMargin = dp(16);
        page.addView(pill, pl);

        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        page.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        root.addView(page);

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xB30D0D12);
        drawer.setPadding(dp(16), dp(56), dp(16), dp(8));
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
            public View getView(int i, View v, ViewGroup p) { return appCell(shown.get(i), true); }
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
        setBlur(true);
    }

    void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        search.setText("");
        setBlur(false);
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
            for (String k : new String[]{"dialer", "contacts", "mms", "camera"})
                for (ResolveInfo r : all)
                    if (r.activityInfo.packageName.contains(k)) { d.add(r.activityInfo.packageName); break; }
            sp.edit().putString("dock", TextUtils.join(",", d)).apply();
        }
        home.clear();
        for (String it : sp.getString("home", "").split(",")) {
            if (it.startsWith("f:") ? !fpk(it).isEmpty() : byPkg.containsKey(it)) home.add(it);
        }
        homeAd.notifyDataSetChanged();

        dock.removeAllViews();
        for (String p : getDock())
            dock.addView(appCell(byPkg.get(p), false), new LinearLayout.LayoutParams(0, -2, 1f));
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
        if (folderDlg != null) folderDlg.dismiss();
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (folderDlg != null && folderDlg.isShowing()) folderDlg.dismiss();
        else closeDrawer();
    }
}
