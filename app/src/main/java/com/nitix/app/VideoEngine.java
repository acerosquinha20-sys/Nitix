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

    VideoEngine(Context c) {
        app = c.getApplicationContext();
    }

    boolean isBusy() {
        return transformer != null;
    }

    /**
     * brightness: -1..1 · contrast: -1..1 · saturation: 0..2 (1 = normal) · height: 720 ou 1080.
     * watermark: coloca "NITIX" no vídeo (plano grátis).
     */
    void start(Uri input, float brightness, float contrast, float saturation, int height,
               boolean watermark, Callback callback) {
        cancel();
        cb = callback;
        File out = new File(app.getCacheDir(), "nitix_export.mp4");
        if (out.exists()) out.delete();

        List<Effect> fx = new ArrayList<>();
        if (Math.abs(brightness) > 0.001f) fx.add(new Brightness(brightness));
        if (Math.abs(contrast) > 0.001f) fx.add(new Contrast(contrast));
        if (Math.abs(saturation - 1f) > 0.001f) {
            fx.add(new HslAdjustment.Builder().adjustSaturation((saturation - 1f) * 100f).build());
        }
        fx.add(Presentation.createForHeight(height));
        if (watermark) {
            OverlaySettings settings = new OverlaySettings.Builder()
                    .setScale(0.3f, 0.3f)
                    .setBackgroundFrameAnchor(0.9f, -0.9f)
                    .setOverlayFrameAnchor(1f, -1f)
                    .build();
            fx.add(new OverlayEffect(ImmutableList.of(
                    BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap(), settings))));
        }

        Effects effects = new Effects(Collections.<AudioProcessor>emptyList(), fx);
        EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(input))
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
                        Callback c = cb;
                        if (c != null) c.onError(e == null ? "erro desconhecido" : e.getMessage());
                    }
                })
                .build();
        transformer.start(item, out.getAbsolutePath());
        polling = true;
        main.post(poll);
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
