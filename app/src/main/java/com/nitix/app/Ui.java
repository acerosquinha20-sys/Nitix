package com.nitix.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Cores, formas, botões e animações do Nitix. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0E0B24;
    static final int CARD = 0xFF1A1540;
    static final int VIOLET = 0xFF5B3DF0;
    static final int LILAC = 0xFFB7A9FF;
    static final int SUN = 0xFFFFD166;
    static final int TXT = 0xFFF4F1FF;
    static final int MUTED = 0xFF9D95CC;
    static final int DANGER = 0xFFFF7A8A;
    static final int OK = 0xFF7DF0B0;
    static final int ON_SUN = 0xFF1A1200;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    interface Setter {
        void set(float v);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Respeita "Remover animações" do Android (escala de animação = 0). */
    static boolean reduceMotion(Context c) {
        try {
            ContentResolver cr = c.getContentResolver();
            return Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    static GradientDrawable box(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable outlined(Context c, int fill, int line, float radiusDp) {
        GradientDrawable g = box(c, fill, radiusDp);
        g.setStroke(dp(c, 1), line);
        return g;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Botão principal: amarelo-sol, texto escuro. */
    static Button primary(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(ON_SUN);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 56));
        b.setBackground(box(c, SUN, 18));
        pressable(b);
        return b;
    }

    /** Botão secundário: cartão com contorno violeta. */
    static Button secondary(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(TXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 52));
        b.setBackground(outlined(c, CARD, VIOLET, 18));
        pressable(b);
        return b;
    }

    /** Encolhe um pouco ao tocar, como um botão de verdade. */
    static void pressable(final View v) {
        v.setOnTouchListener((view, e) -> {
            long d = reduceMotion(view.getContext()) ? 0 : 110;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(d).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(d).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Entrada suave: aparece e sobe um pouco. */
    static void enter(View v, long delayMs) {
        if (reduceMotion(v.getContext())) return;
        float d = v.getResources().getDisplayMetrics().density;
        v.setAlpha(0f);
        v.setTranslationY(14f * d);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /**
     * Barra deslizante com rótulo e valor em %.
     * A barra vai de min a max; o valor mostrado é v * mul.
     */
    static SeekBar slider(Context c, LinearLayout parent, String label, final float min, final float max,
                          float init, final float mul, final Setter setter) {
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(c, label, 14, TXT, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView value = text(c, Math.round(init * mul) + "%", 13, LILAC, true);
        head.addView(value);
        parent.addView(head, lp(c, MATCH, WRAP, 14));

        final SeekBar bar = new SeekBar(c);
        bar.setMax(1000);
        bar.setProgress(Math.round((init - min) / (max - min) * 1000f));
        bar.setProgressTintList(ColorStateList.valueOf(VIOLET));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(CARD));
        bar.setThumbTintList(ColorStateList.valueOf(SUN));
        parent.addView(bar, lp(c, MATCH, WRAP, 2));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int prog, boolean fromUser) {
                float v = min + (max - min) * prog / 1000f;
                value.setText(Math.round(v * mul) + "%");
                setter.set(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        return bar;
    }
}
