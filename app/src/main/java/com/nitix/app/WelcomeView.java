package com.nitix.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** "Seja bem-vindo" com animação, depois do login. Toque para pular. */
final class WelcomeView extends FrameLayout {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean closing;

    WelcomeView(Context c, String name, int border, int nameStyle, Bitmap photo, String initial) {
        super(c);
        setBackgroundColor(Ui.BG);
        setClickable(true);
        boolean rm = Ui.reduceMotion(c);

        Sparkles sparkles = new Sparkles(c);
        addView(sparkles, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(c, 24), 0, Ui.dp(c, 24), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
        addView(col, cp);

        AvatarView avatar = new AvatarView(c);
        avatar.setStyle(border);
        avatar.setPhoto(photo);
        avatar.setInitial(initial);
        col.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(c, 150), Ui.dp(c, 150)));

        TextView hello = Ui.text(c, "Seja bem-vindo,", 20, Ui.LILAC, false);
        hello.setGravity(Gravity.CENTER);
        col.addView(hello, Ui.lp(c, Ui.WRAP, Ui.WRAP, 20));

        NameView nameView = new NameView(c, 34);
        nameView.setStyle(nameStyle);
        nameView.setNameText(name);
        nameView.setGravity(Gravity.CENTER);
        col.addView(nameView, Ui.lp(c, Ui.MATCH, Ui.WRAP, 4));

        TextView sub = Ui.text(c, "Vamos deixar suas fotos e vídeos incríveis", 14, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, Ui.lp(c, Ui.WRAP, Ui.WRAP, 14));

        setOnClickListener(v -> close());

        if (rm) {
            handler.postDelayed(this::close, 1200);
            return;
        }

        avatar.setAlpha(0f);
        avatar.setScaleX(0.2f);
        avatar.setScaleY(0.2f);
        avatar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(750)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();

        Ui.enter(hello, 450);

        nameView.setAlpha(0f);
        nameView.setScaleX(0.6f);
        nameView.setScaleY(0.6f);
        nameView.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(650).setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();

        Ui.enter(sub, 1150);

        handler.postDelayed(this::close, 3200);
    }

    private void close() {
        if (closing) return;
        closing = true;
        handler.removeCallbacksAndMessages(null);
        animate().alpha(0f).setDuration(450).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    /** Pontinhos de luz subindo, só para dar clima. */
    private static final class Sparkles extends View {
        private static final int COUNT = 36;
        private static final int[] COLORS = {Ui.SUN, Ui.LILAC, 0xFF7DF0FF, 0xFFFFFFFF};
        private final float[] x = new float[COUNT];
        private final float[] off = new float[COUNT];
        private final float[] size = new float[COUNT];
        private final float[] twinkle = new float[COUNT];
        private final int[] cycles = new int[COUNT];
        private final int[] color = new int[COUNT];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase;
        private ValueAnimator anim;

        Sparkles(Context c) {
            super(c);
            Random r = new Random(7);
            for (int i = 0; i < COUNT; i++) {
                x[i] = r.nextFloat();
                off[i] = r.nextFloat();
                size[i] = Ui.dp(c, 1) + r.nextFloat() * Ui.dp(c, 3);
                twinkle[i] = r.nextFloat() * 6.2832f;
                cycles[i] = 1 + r.nextInt(2);
                color[i] = COLORS[r.nextInt(COLORS.length)];
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!Ui.reduceMotion(getContext())) {
                anim = Ui.loop(6000, v -> {
                    phase = v;
                    invalidate();
                });
                anim.start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) {
                anim.cancel();
                anim = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            for (int i = 0; i < COUNT; i++) {
                float t = (phase * cycles[i] + off[i]) % 1f;
                float y = h * (1f - t);
                float a = (float) (0.5 + 0.5 * Math.sin(phase * 6.2832f * 3f + twinkle[i]));
                float fade = Math.min(1f, Math.min(t, 1f - t) * 5f);
                paint.setColor(color[i]);
                paint.setAlpha((int) (200 * a * fade));
                canvas.drawCircle(x[i] * w, y, size[i], paint);
            }
        }
    }
}
