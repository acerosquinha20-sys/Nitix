package com.nitix.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Abertura: o logo começa borrado e fica nítido, com slogan e barra curta. */
public class SplashActivity extends android.app.Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean went;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        boolean rm = Ui.reduceMotion(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout logoBox = new FrameLayout(this);
        ImageView sharp = new ImageView(this);
        sharp.setImageResource(R.drawable.nitix_logo);
        ImageView blur = new ImageView(this);
        blur.setImageResource(R.drawable.nitix_logo_blur);
        logoBox.addView(sharp, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        logoBox.addView(blur, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        col.addView(logoBox, new LinearLayout.LayoutParams(Ui.dp(this, 136), Ui.dp(this, 136)));

        TextView name = Ui.text(this, "Nitix", 38, Ui.TXT, true);
        name.setGravity(Gravity.CENTER);
        col.addView(name, Ui.lp(this, Ui.WRAP, Ui.WRAP, 22));

        TextView slogan = Ui.text(this, "Mais nitidez em cada pixel", 15, Ui.LILAC, false);
        slogan.setGravity(Gravity.CENTER);
        col.addView(slogan, Ui.lp(this, Ui.WRAP, Ui.WRAP, 6));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.SUN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.CARD));
        col.addView(bar, Ui.lp(this, Ui.dp(this, 180), Ui.dp(this, 6), 28));

        root.addView(col, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        setContentView(root);

        if (rm) {
            blur.setAlpha(0f);
            bar.setProgress(100);
            handler.postDelayed(this::goMain, 300);
            return;
        }

        // o borrado some e o logo nítido aparece
        blur.animate().alpha(0f).setStartDelay(150).setDuration(1100).start();
        sharp.setScaleX(1.06f);
        sharp.setScaleY(1.06f);
        sharp.animate().scaleX(1f).scaleY(1f).setDuration(1400).start();
        Ui.enter(name, 500);
        Ui.enter(slogan, 650);

        ObjectAnimator progress = ObjectAnimator.ofInt(bar, "progress", 0, 100);
        progress.setStartDelay(400);
        progress.setDuration(1800);
        progress.start();

        handler.postDelayed(this::goMain, 2300);
    }

    private void goMain() {
        if (went) return;
        went = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
