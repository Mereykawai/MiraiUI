package com.mirai.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG = Color.parseColor("#0D0D12"), ACCENT = Color.parseColor("#FF3B5C");
    PackageManager pm;
    List<ResolveInfo> all = new ArrayList<>(), shown = new ArrayList<>();
    BaseAdapter adapter;
    EditText search;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(40), dp(16), dp(8));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(64);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        root.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(16);
        date.setTextColor(ACCENT);
        root.addView(date);

        search = new EditText(this);
        search.setHint("Поиск приложений");
        search.setHintTextColor(Color.GRAY);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackgroundColor(Color.parseColor("#1A1A22"));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, dp(20), 0, dp(12));
        root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) { filter(); }
            public void afterTextChanged(Editable e) {}
        });

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(12));
        grid.setSelector(android.R.color.transparent);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                ResolveInfo ri = shown.get(i);
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageDrawable(ri.loadIcon(pm));
                cell.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
                TextView t = new TextView(MainActivity.this);
                t.setText(ri.loadLabel(pm));
                t.setTextColor(Color.WHITE);
                t.setTextSize(11);
                t.setSingleLine(true);
                t.setGravity(Gravity.CENTER);
                cell.addView(t);
                return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, i, id) -> {
            String pkg = shown.get(i).activityInfo.packageName;
            Intent in = pm.getLaunchIntentForPackage(pkg);
            if (in != null) startActivity(in);
        });
        grid.setOnItemLongClickListener((p, v, i, id) -> {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + shown.get(i).activityInfo.packageName)));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        all = pm.queryIntentActivities(q, 0);
        Collections.sort(all, (a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));
        filter();
    }

    void filter() {
        String s = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (ResolveInfo r : all)
            if (s.isEmpty() || r.loadLabel(pm).toString().toLowerCase().contains(s)) shown.add(r);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        search.setText("");
    }

    @Override
    public void onBackPressed() { /* лаунчер не закрывается */ }
}
