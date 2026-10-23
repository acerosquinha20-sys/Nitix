package com.nitix.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/** Foto (ou inicial) do perfil, com borda animada. */
final class AvatarView extends View {

    static final String[] BORDERS = {"Clássico", "Arco-íris", "Pulsante", "Neon", "Dourado"};

    private static final int[] RAINBOW = {0xFFFF5470, 0xFFFFD166, 0xFF7DF0B0, 0xFF5CE1FF,
            0xFF8B6CFF, 0xFFFF5CD6, 0xFFFF5470};
    private static final int[] GOLD = {0xFFFFB400, 0xFFFFF3C4, 0xFFFFD166, 0xFFFFE9A0,
            0xFFE09A00, 0xFFFFD166, 0xFFFFB400};
    private static final int[] NEON = {0x225B3DF0, 0xFF5B3DF0, 0xFF7DF0FF, 0xFFFFFFFF, 0x225B3DF0};
    private static final float[] NEON_POS = {0f, 0.45f, 0.8f, 0.92f, 1f};

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private int style = 1;
    private Bitmap photo;
    private BitmapShader photoShader;
    private String initial = "N";
    private float phase;
    private ValueAnimator anim;

    private Shader sweep;
    private Shader backdrop;
    private int builtKey = -1;
    private int builtStyle = -1;

    AvatarView(Context c) {
        super(c);
        letter.setTextAlign(Paint.Align.CENTER);
        letter.setTypeface(Typeface.DEFAULT_BOLD);
        letter.setColor(Ui.TXT);
        // o brilho do estilo "Neon" precisa de desenho por software
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void setStyle(int s) {
        style = Math.max(0, Math.min(BORDERS.length - 1, s));
        invalidate();
    }

    void setInitial(String s) {
        initial = (s == null || s.isEmpty()) ? "N" : s;
        invalidate();
    }

    void setPhoto(Bitmap b) {
        photo = b;
        photoShader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        builtKey = -1;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Ui.reduceMotion(getContext())) {
            anim = Ui.loop(3600, v -> {
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
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        float pad = size * 0.07f;
        float stroke = size * 0.055f;
        float outerR = size / 2f - pad - stroke / 2f;
        float imgR = outerR - stroke / 2f - size * 0.025f;
        double ang = phase * Math.PI * 2.0;

        ensure(cx, cy, imgR, (int) w * 10000 + (int) h);

        // ---------- anel
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setShader(null);
        ring.clearShadowLayer();
        ring.setAlpha(255);

        switch (style) {
            case 0:
                ring.setColor(Ui.LILAC);
                canvas.drawCircle(cx, cy, outerR, ring);
                break;
            case 1:
            case 4:
                canvas.save();
                canvas.rotate(phase * 360f * (style == 4 ? 0.5f : 1f), cx, cy);
                ring.setShader(sweep);
                canvas.drawCircle(cx, cy, outerR, ring);
                canvas.restore();
                if (style == 4) {
                    // brilho dourado em volta
                    ring.setShader(null);
                    ring.setColor(0x33FFD166);
                    ring.setStrokeWidth(stroke * 2.2f);
                    canvas.drawCircle(cx, cy, outerR, ring);
                }
                break;
            case 2: {
                float pulse = (float) (0.5 + 0.5 * Math.sin(ang * 2));
                float rip = (phase * 2f) % 1f;
                ring.setColor(Ui.SUN);
                ring.setAlpha((int) (140 * (1f - rip)));
                ring.setStrokeWidth(stroke * 0.5f);
                canvas.drawCircle(cx, cy, outerR + pad * rip * 0.9f, ring);
                ring.setColor(Ui.SUN);
                ring.setAlpha(255);
                ring.setStrokeWidth(stroke * (0.8f + 0.5f * pulse));
                canvas.drawCircle(cx, cy, outerR, ring);
                break;
            }
            default: {
                ring.setColor(0x445B3DF0);
                canvas.drawCircle(cx, cy, outerR, ring);
                canvas.save();
                canvas.rotate(phase * 360f, cx, cy);
                ring.setShader(sweep);
                ring.setShadowLayer(size * 0.05f, 0f, 0f, 0xFF7DF0FF);
                canvas.drawCircle(cx, cy, outerR, ring);
                canvas.restore();
                ring.clearShadowLayer();
                break;
            }
        }

        // ---------- foto ou inicial
        fill.setStyle(Paint.Style.FILL);
        if (photo != null && photoShader != null) {
            fill.setShader(photoShader);
            canvas.drawCircle(cx, cy, imgR, fill);
        } else {
            fill.setShader(backdrop);
            canvas.drawCircle(cx, cy, imgR, fill);
            letter.setTextSize(imgR * 1.05f);
            float y = cy - (letter.ascent() + letter.descent()) / 2f;
            canvas.drawText(initial, cx, y, letter);
        }
    }

    /** Recria os shaders só quando o tamanho ou o estilo mudam. */
    private void ensure(float cx, float cy, float imgR, int key) {
        if (key != builtKey || style != builtStyle) {
            builtKey = key;
            builtStyle = style;
            if (style == 1) sweep = new SweepGradient(cx, cy, RAINBOW, null);
            else if (style == 4) sweep = new SweepGradient(cx, cy, GOLD, null);
            else if (style == 3) sweep = new SweepGradient(cx, cy, NEON, NEON_POS);
            else sweep = null;

            backdrop = new LinearGradient(cx - imgR, cy - imgR, cx + imgR, cy + imgR,
                    0xFF7B5CFF, 0xFF3B2AA8, Shader.TileMode.CLAMP);
            if (photo != null && photoShader != null) {
                float sc = Math.max(2f * imgR / photo.getWidth(), 2f * imgR / photo.getHeight());
                matrix.reset();
                matrix.setScale(sc, sc);
                matrix.postTranslate(cx - photo.getWidth() * sc / 2f, cy - photo.getHeight() * sc / 2f);
                photoShader.setLocalMatrix(matrix);
            }
        }
    }
}
