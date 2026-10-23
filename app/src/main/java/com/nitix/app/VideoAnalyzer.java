package com.nitix.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

/**
 * Olha alguns quadros do vídeo e calcula as curvas de cor da melhoria automática
 * (a mesma lógica das fotos: níveis, equilíbrio de branco, gama e curva em S).
 */
final class VideoAnalyzer {
    private VideoAnalyzer() {}

    /** Devolve as curvas [3][256] (R, G, B), ou null se não conseguir ler o vídeo. */
    static int[][] analyze(Context c, Uri uri, long durationMs, float strength) {
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        int[][] hist = new int[3][256];
        long count = 0;
        try {
            m.setDataSource(c, uri);
            final int frames = 8;
            for (int k = 0; k < frames; k++) {
                long t = (long) ((k + 0.5) / frames * Math.max(1L, durationMs) * 1000L);
                Bitmap f = m.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (f == null) continue;
                float sc = 120f / Math.max(f.getWidth(), f.getHeight());
                Bitmap sm = Bitmap.createScaledBitmap(f,
                        Math.max(1, Math.round(f.getWidth() * sc)),
                        Math.max(1, Math.round(f.getHeight() * sc)), true);
                int[] px = new int[sm.getWidth() * sm.getHeight()];
                sm.getPixels(px, 0, sm.getWidth(), 0, 0, sm.getWidth(), sm.getHeight());
                for (int p : px) {
                    hist[0][(p >> 16) & 255]++;
                    hist[1][(p >> 8) & 255]++;
                    hist[2][p & 255]++;
                }
                count += px.length;
                if (sm != f) sm.recycle();
                f.recycle();
            }
        } catch (Exception e) {
            return null;
        } finally {
            try {
                m.release();
            } catch (Exception ignored) {
            }
        }
        if (count == 0) return null;
        return PhotoEngine.autoCurves(hist, count, strength, false);
    }
}
