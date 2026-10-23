package com.nitix.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Comparador antes/depois: arraste a barra para ver a diferença. */
final class CompareView extends View {
    private Bitmap before, after;
    private float split = 0.5f;
    private final RectF dst = new RectF();
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    CompareView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(2f * density);
        knob.setColor(Ui.SUN);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(12f * density);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setShadowLayer(4f * density, 0, 0, 0xAA000000);
    }

    void setBefore(Bitmap b) {
        before = b;
        invalidate();
    }

    void setAfter(Bitmap a) {
        after = a;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Bitmap ref = before != null ? before : after;
        if (ref == null) return;

        float vw = getWidth(), vh = getHeight();
        float s = Math.min(vw / ref.getWidth(), vh / ref.getHeight());
        float w = ref.getWidth() * s, h = ref.getHeight() * s;
        float l = (vw - w) / 2f, t = (vh - h) / 2f;
        dst.set(l, t, l + w, t + h);
        float x = l + w * split;

        if (before != null) {
            c.save();
            c.clipRect(l, t, x, t + h);
            c.drawBitmap(before, null, dst, bmp);
            c.restore();
        }
        if (after != null) {
            c.save();
            c.clipRect(x, t, l + w, t + h);
            c.drawBitmap(after, null, dst, bmp);
            c.restore();
        }

        c.drawLine(x, t, x, t + h, line);
        c.drawCircle(x, t + h / 2f, 17f * density, knob);

        float pad = 10f * density;
        c.drawText("ANTES", l + pad, t + h - pad, label);
        float dw = label.measureText("DEPOIS");
        c.drawText("DEPOIS", l + w - pad - dw, t + h - pad, label);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (dst.width() <= 0f) return super.onTouchEvent(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            split = clamp((e.getX() - dst.left) / dst.width());
            invalidate();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
