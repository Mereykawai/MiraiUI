package com.mirai.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

class Weather {
    static String cCity = "", cTemp = "", cDesc = "", cRange = "";
    static long ts = 0;

    static void stale() { ts = 0; }

    static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder s = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) s.append(l);
        r.close();
        return s.toString();
    }

    static String desc(int c) {
        if (c == 0) return "☀ Ясно";
        if (c <= 2) return "⛅ Малооблачно";
        if (c == 3) return "☁ Пасмурно";
        if (c == 45 || c == 48) return "🌫 Туман";
        if (c >= 51 && c <= 57) return "🌦 Морось";
        if (c >= 61 && c <= 67) return "🌧 Дождь";
        if (c >= 71 && c <= 77) return "❄ Снег";
        if (c >= 80 && c <= 82) return "🌧 Ливень";
        if (c == 85 || c == 86) return "❄ Снегопад";
        if (c >= 95) return "⛈ Гроза";
        return "☁ Облачно";
    }

    static TextView mk(Activity a, String s, int size, int col) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(col);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    static void card(Activity a, LinearLayout c, String city) {
        float d = a.getResources().getDisplayMetrics().density;
        c.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        c.setPadding((int) (18 * d), 0, (int) (18 * d), 0);
        TextView t1 = mk(a, city.isEmpty() ? "Укажи город в настройках" : city, 14, 0xDDFFFFFF);
        TextView t2 = mk(a, "…", 48, Color.WHITE);
        TextView t3 = mk(a, "", 15, Color.WHITE);
        TextView t4 = mk(a, "", 13, 0xCCFFFFFF);
        c.addView(t1);
        c.addView(t2);
        c.addView(t3);
        c.addView(t4);
        if (city.isEmpty()) { t2.setText("☁"); return; }
        if (System.currentTimeMillis() - ts < 1800000 && city.equals(cCity)) {
            t2.setText(cTemp);
            t3.setText(cDesc);
            t4.setText(cRange);
            return;
        }
        new Thread(() -> {
            try {
                String g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name="
                        + URLEncoder.encode(city, "UTF-8"));
                JSONObject r = new JSONObject(g).getJSONArray("results").getJSONObject(0);
                String f = get("https://api.open-meteo.com/v1/forecast?latitude=" + r.getDouble("latitude")
                        + "&longitude=" + r.getDouble("longitude")
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto");
                JSONObject j = new JSONObject(f);
                JSONObject cur = j.getJSONObject("current"), dl = j.getJSONObject("daily");
                cTemp = Math.round(cur.getDouble("temperature_2m")) + "°";
                cDesc = desc(cur.getInt("weather_code"));
                cRange = Math.round(dl.getJSONArray("temperature_2m_min").getDouble(0)) + "° / "
                        + Math.round(dl.getJSONArray("temperature_2m_max").getDouble(0)) + "°";
                cCity = city;
                ts = System.currentTimeMillis();
                a.runOnUiThread(() -> { t2.setText(cTemp); t3.setText(cDesc); t4.setText(cRange); });
            } catch (Exception e) {
                a.runOnUiThread(() -> { t2.setText("–"); t3.setText("Нет данных"); });
            }
        }).start();
    }
}
