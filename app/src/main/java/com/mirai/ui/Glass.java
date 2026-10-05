package com.mirai.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.LinearLayout;
import java.util.*;

class Glass extends LinearLayout {
    static Bitmap blur;
    static boolean on = true;
    static final Set<Glass> ALL = Collections.newSetFromMap(new WeakHashMap<Glass, Boolean>());
    final float r;
    final int tint;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glass(Context c, int radiusDp, int tint) {
        super(c);
        this.r = radiusDp * c.getResources().getDisplayMetrics().density;
        this.tint = tint;
        setWillNotDraw(false);
        ALL.add(this);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        Path pa = new Path();
        pa.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
        cv.save();
        cv.clipPath(pa);
        if (on && blur != null) {
            int[] l = new int[2];
            getLocationOnScreen(l);
            cv.drawBitmap(blur, -l[0], -l[1], null);
        }
        cv.drawColor(tint);
        if (on) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, h, 0x44FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
        cv.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(getResources().getDisplayMetrics().density);
        p.setColor(0x66FFFFFF);
        cv.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, p);
    }

    static void refresh() { for (Glass g : ALL) g.invalidate(); }
}

class ClockView extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final boolean dark;
    final float d;

    ClockView(Context c, boolean dark) {
        super(c);
        this.dark = dark;
        d = c.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = w / 2;
        int fg = dark ? 0xFFFFFFFF : 0xFF1A1A1A;
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark ? 0xFF1C1C1E : Color.WHITE);
        cv.drawRoundRect(0, 0, w, h, 30 * d, 30 * d, p);
        p.setColor(fg);
        p.setTextSize(22 * d);
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("12", cx, 36 * d, p);
        cv.drawText("6", cx, h - 16 * d, p);
        cv.drawText("3", w - 22 * d, cy + 8 * d, p);
        cv.drawText("9", 22 * d, cy + 8 * d, p);
        Calendar c = Calendar.getInstance();
        float s = c.get(Calendar.SECOND), m = c.get(Calendar.MINUTE) + s / 60, hr = c.get(Calendar.HOUR) + m / 60;
        hand(cv, cx, cy, hr * 30, r * 0.45f, 5 * d, fg);
        hand(cv, cx, cy, m * 6, r * 0.7f, 5 * d, fg);
        hand(cv, cx, cy, s * 6, r * 0.75f, 2 * d, dark ? 0xFFFF9F0A : 0xFFFF3B30);
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
