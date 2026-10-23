package com.nitix.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.SingleColorLut;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exporta o vídeo melhorado com o Media3 Transformer (o áudio original é mantido). */
final class VideoEngine {

    interface Callback {
        void onProgress(int percent);

        void onDone(File out);

        void onError(String message);
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Transformer transformer;
    private Callback cb;
    private boolean polling;

    // dados do trabalho atual (guardados para poder tentar de novo sem a curva de cor)
    private Uri jobInput;
    private float jobBrightness;
    private float jobContrast;
    private float jobSaturation;
    private int jobHeight;
    private boolean jobWatermark;
    private int[][] jobCurves;
    private boolean retried;

    VideoEngine(Context c) {
        app = c.getApplicationContext();
    }

    boolean isBusy() {
        return transformer != null;
    }

    /**
     * brightness: -1..1 · contrast: -1..1 · saturation: 0..2 (1 = normal) · height: 720 ou 1080.
     * watermark: coloca "NITIX" no vídeo (plano grátis).
     * curves: curvas da melhoria automática (null = sem melhoria automática).
     */
    void start(Uri input, float brightness, float contrast, float saturation, int height,
               boolean watermark, int[][] curves, Callback callback) {
        cancel();
        cb = callback;
        jobInput = input;
        jobBrightness = brightness;
        jobContrast = contrast;
        jobSaturation = saturation;
        jobHeight = height;
        jobWatermark = watermark;
        jobCurves = curves;
        retried = false;
        run(curves != null);
    }

    private void run(final boolean useLut) {
        final File out = new File(app.getCacheDir(), "nitix_export.mp4");
        if (out.exists()) out.delete();

        List<Effect> fx = new ArrayList<>();
        if (useLut) {
            try {
                fx.add(lut(jobCurves));
            } catch (Exception ignored) {
                // sem a curva de cor, o resto continua funcionando
            }
        }
        if (Math.abs(jobBrightness) > 0.001f) fx.add(new Brightness(jobBrightness));
        if (Math.abs(jobContrast) > 0.001f) fx.add(new Contrast(jobContrast));
        if (Math.abs(jobSaturation - 1f) > 0.001f) {
            fx.add(new HslAdjustment.Builder().adjustSaturation((jobSaturation - 1f) * 100f).build());
        }
        fx.add(Presentation.createForHeight(jobHeight));
        if (jobWatermark) {
            OverlaySettings settings = new OverlaySettings.Builder()
                    .setScale(0.3f, 0.3f)
                    .setBackgroundFrameAnchor(0.9f, -0.9f)
                    .setOverlayFrameAnchor(1f, -1f)
                    .build();
            fx.add(new OverlayEffect(ImmutableList.of(
                    BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap(), settings))));
        }

        Effects effects = new Effects(Collections.<AudioProcessor>emptyList(), fx);
        EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(jobInput))
                .setEffects(effects)
                .build();

        transformer = new Transformer.Builder(app)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult result) {
                        stopPolling();
                        transformer = null;
                        Callback c = cb;
                        if (c != null) c.onDone(out);
                    }

                    @Override
                    public void onError(Composition composition, ExportResult result, ExportException e) {
                        stopPolling();
                        transformer = null;
                        if (useLut && !retried && cb != null) {
                            // alguns aparelhos não aceitam a curva de cor: tenta de novo sem ela
                            retried = true;
                            run(false);
                            return;
                        }
                        Callback c = cb;
                        if (c != null) c.onError(e == null ? "erro desconhecido" : e.getMessage());
                    }
                })
                .build();
        transformer.start(item, out.getAbsolutePath());
        polling = true;
        main.post(poll);
    }

    /** Monta a tabela de cores 3D (33x33x33) a partir das curvas por canal. */
    private static SingleColorLut lut(int[][] cv) {
        final int n = 33;
        int[][][] cube = new int[n][n][n];
        for (int r = 0; r < n; r++) {
            int vr = cv[0][r * 255 / (n - 1)];
            for (int g = 0; g < n; g++) {
                int vg = cv[1][g * 255 / (n - 1)];
                for (int b = 0; b < n; b++) {
                    int vb = cv[2][b * 255 / (n - 1)];
                    cube[r][g][b] = 0xFF000000 | (vr << 16) | (vg << 8) | vb;
                }
            }
        }
        return SingleColorLut.createFromCube(cube);
    }

    /** Cancela a exportação em andamento, se houver. */
    void cancel() {
        stopPolling();
        cb = null;
        if (transformer != null) {
            transformer.cancel();
            transformer = null;
        }
    }

    private void stopPolling() {
        polling = false;
        main.removeCallbacks(poll);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!polling || transformer == null) return;
            ProgressHolder h = new ProgressHolder();
            transformer.getProgress(h);
            Callback c = cb;
            if (c != null) c.onProgress(h.progress);
            main.postDelayed(this, 300);
        }
    };

    private static Bitmap watermarkBitmap() {
        Bitmap b = Bitmap.createBitmap(640, 160, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xDDFFFFFF);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(110f);
        c.drawText("NITIX", 20f, 125f, p);
        return b;
    }
}
