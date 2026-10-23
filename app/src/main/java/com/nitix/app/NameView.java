package com.nitix.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.widget.TextView;

/** Nome do perfil com estilo animado (brilho, arco-íris, neon, dourado). */
final class NameView extends TextView {

    static final String[] STYLES = {"Normal", "Brilho", "Arco-íris", "Neon", "Dourado"};

    private static final int[] RAINBOW = {0xFFFF5470, 0xFFFFD166, 0xFF7DF0B0, 0xFF5CE1FF,
            0xFF8B6CFF, 0xFFFF5CD6, 0xFFFF5470};
    private static final int[] GOLD = {0xFFFFB400, 0xFFFFF3C4, 0xFFFFD166, 0xFFFFE9A0,
            0xFFE09A00, 0xFFFFD166, 0xFFFFB400};
    private static final int[] SHINE = {0xFFB7A9FF, 0xFFFFFFFF, 0xFFB7A9FF};

    private int style = 1;
    private float phase;
    private float period = 1f;
    private Shader shader;
    private ValueAnimator anim;
    private final Matrix matrix = new Matrix();

    NameView(Context c, float sp) {
        super(c);
        setTextSize(sp);
        setTypeface(Typeface.DEFAULT_BOLD);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        rebuild();
    }

    void setStyle(int s) {
        style = Math.max(0, Math.min(STYLES.length - 1, s));
        rebuild();
    }

    void setNameText(String s) {
        setText(s);
        rebuild();
    }

    private void rebuild() {
        getPaint().setShader(null);
        setShadowLayer(0f, 0f, 0f, 0);
        shader = null;
        if (style == 0) {
            setTextColor(Ui.TXT);
            return;
        }
        if (style == 3) {
            setTextColor(0xFFEAFBFF);
            setShadowLayer(Ui.dp(getContext(), 8), 0f, 0f, 0xFF5CE1FF);
            return;
        }
        setTextColor(0xFFFFFFFF);
        float tw = Math.max(1f, getPaint().measureText(String.valueOf(getText())));
        if (style == 1) {
            period = tw * 1.8f;
            shader = new LinearGradient(0f, 0f, tw * 0.9f, 0f, SHINE, null, Shader.TileMode.MIRROR);
        } else if (style == 2) {
            period = tw;
            shader = new LinearGradient(0f, 0f, tw, 0f, RAINBOW, null, Shader.TileMode.REPEAT);
        } else {
            period = tw * 1.3f;
            shader = new LinearGradient(0f, 0f, tw * 1.3f, 0f, GOLD, null, Shader.TileMode.REPEAT);
        }
        matrix.setTranslate(phase * period, 0f);
        shader.setLocalMatrix(matrix);
        getPaint().setShader(shader);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Ui.reduceMotion(getContext())) {
            anim = Ui.loop(2800, v -> {
                phase = v;
                if (style == 3) {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(v * Math.PI * 2.0));
                    int color = pulse > 0.5f ? 0xFF5CE1FF : 0xFF8B6CFF;
                    setShadowLayer(Ui.dp(getContext(), 4 + 8 * pulse), 0f, 0f, color);
                } else if (shader != null) {
                    matrix.setTranslate(phase * period, 0f);
                    shader.setLocalMatrix(matrix);
                    invalidate();
                }
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
}
