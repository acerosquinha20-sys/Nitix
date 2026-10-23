package com.nitix.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/** Melhoria de foto feita 100% no aparelho, com Bitmap e matemática de pixel. */
final class PhotoEngine {
    private PhotoEngine() {}

    static final class Params {
        float brightness;      // -1 .. 1
        float contrast;        // -1 .. 1
        float saturation = 1f; // 0 .. 2
        float sharpness;       // 0 .. 1
        boolean bw;
        boolean auto;
        float autoStrength = 1f; // 0 .. 1 (intensidade da melhoria automática)
        int scale = 1;           // fator de resolução (1, 2 ou 4), usado para calibrar a nitidez

        Params copy() {
            Params q = new Params();
            q.brightness = brightness;
            q.contrast = contrast;
            q.saturation = saturation;
            q.sharpness = sharpness;
            q.bw = bw;
            q.auto = auto;
            q.autoStrength = autoStrength;
            q.scale = scale;
            return q;
        }
    }

    /** Abre a imagem reduzindo se for maior que maxSide, e corrige a rotação do EXIF. */
    static Bitmap decode(ContentResolver cr, Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        try (InputStream in = open(cr, uri)) {
            BitmapFactory.decodeStream(in, null, probe);
        }
        int s = 1;
        while (Math.max(probe.outWidth, probe.outHeight) / s > maxSide) s *= 2;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = open(cr, uri)) {
            b = BitmapFactory.decodeStream(in, null, o);
        }
        if (b == null) throw new IOException("Imagem inválida");

        int rot = rotation(cr, uri);
        if (rot != 0) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        }
        return b;
    }

    private static InputStream open(ContentResolver cr, Uri uri) throws IOException {
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new IOException("Não foi possível abrir a imagem");
        return in;
    }

    private static int rotation(ContentResolver cr, Uri uri) {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface e = new ExifInterface(in);
            int o = e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * Aumenta a resolução (2x ou 4x), mas nunca passa de maxSide.
     * Ex.: foto de 3000 px no 4x vira 4096 px (1x de fator inteiro, sem estourar o limite).
     */
    static Bitmap upscale(Bitmap src, int factor, int maxSide) {
        int longSide = Math.max(src.getWidth(), src.getHeight());
        int f = Math.max(1, Math.min(factor, maxSide / Math.max(1, longSide)));
        if (f <= 1) return src;
        return Bitmap.createScaledBitmap(src, src.getWidth() * f, src.getHeight() * f, true);
    }

    /**
     * Aplica a melhoria automática (se ligada), depois brilho, contraste, cor,
     * preto e branco e nitidez.
     */
    static Bitmap enhance(Bitmap src, Params p) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        float s = p.auto ? Math.max(0f, Math.min(1f, p.autoStrength)) : 0f;
        if (s > 0f) autoTone(px, w, h, p.bw, s);

        float cf = 1f + p.contrast;
        float bright = p.brightness * 255f;
        float sat = p.bw ? 0f : p.saturation;
        float vib = (s > 0f && !p.bw) ? 0.30f * s : 0f;   // vibrance: realça mais as cores apagadas

        boolean neutral = Math.abs(cf - 1f) < 0.001f && Math.abs(bright) < 0.01f
                && Math.abs(sat - 1f) < 0.001f && vib == 0f;
        if (!neutral) {
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int a = c >>> 24;
                float r = (c >> 16) & 255;
                float g = (c >> 8) & 255;
                float b = c & 255;

                r = (r - 128f) * cf + 128f + bright;
                g = (g - 128f) * cf + 128f + bright;
                b = (b - 128f) * cf + 128f + bright;

                float gray = 0.299f * r + 0.587f * g + 0.114f * b;
                r = gray + (r - gray) * sat;
                g = gray + (g - gray) * sat;
                b = gray + (b - gray) * sat;

                if (vib > 0f) {
                    float mx = Math.max(r, Math.max(g, b));
                    float mn = Math.min(r, Math.min(g, b));
                    float sv = Math.max(0f, Math.min(1f, (mx - mn) / Math.max(mx, 1f)));
                    float f = 1f + vib * (1f - sv);
                    float g2 = 0.299f * r + 0.587f * g + 0.114f * b;
                    r = g2 + (r - g2) * f;
                    g = g2 + (g - g2) * f;
                    b = g2 + (b - g2) * f;
                }

                px[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }

        // nitidez: a do usuário ou a automática (maior quando a foto foi ampliada)
        float autoSharp = s > 0f ? (0.25f + 0.10f * (Math.max(1, p.scale) - 1)) * s : 0f;
        float sharp = Math.max(p.sharpness, autoSharp);
        if (sharp > 0f) sharpen(px, w, h, sharp);

        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    // ------------------------------------------------------------------ melhoria automática

    /**
     * Curvas automáticas por canal (0..255 para 0..255), calculadas a partir do histograma:
     * 1) níveis: estica entre o 0,3% mais escuro e o 0,3% mais claro de cada canal;
     * 2) equilíbrio de branco suave (corrige cor dominante, sem exagerar);
     * 3) gama adaptativa: clareia fotos escuras e escurece um pouco as muito claras;
     * 4) curva em S leve, para dar profundidade.
     * Serve para fotos e para vídeos. s = intensidade de 0 a 1.
     */
    static int[][] autoCurves(int[][] hist, long count, float s, boolean bw) {
        long cut = count * 3L / 1000L;
        float[] gain = new float[3];
        float[] off = new float[3];
        float[] mean = new float[3];
        for (int ch = 0; ch < 3; ch++) {
            int lo = 0;
            long acc = 0;
            while (lo < 255 && acc + hist[ch][lo] <= cut) {
                acc += hist[ch][lo];
                lo++;
            }
            int hi = 255;
            acc = 0;
            while (hi > 0 && acc + hist[ch][hi] <= cut) {
                acc += hist[ch][hi];
                hi--;
            }
            if (hi - lo < 16) {
                lo = 0;
                hi = 255;
            }
            float lo2 = lo * s;
            float hi2 = 255f - (255f - hi) * s;
            gain[ch] = 255f / Math.max(1f, hi2 - lo2);
            off[ch] = -lo2 * gain[ch];

            double sum = 0;
            for (int v = 0; v < 256; v++) {
                sum += (double) hist[ch][v] * clampF(gain[ch] * v + off[ch], 0f, 255f);
            }
            mean[ch] = (float) (sum / Math.max(1L, count));
        }

        float[] wb = {1f, 1f, 1f};
        if (!bw) {
            float t = (mean[0] + mean[1] + mean[2]) / 3f;
            for (int ch = 0; ch < 3; ch++) {
                float r = clampF(t / Math.max(mean[ch], 1f), 0.88f, 1.14f);
                wb[ch] = 1f + (r - 1f) * 0.6f * s;
            }
        }

        float my = (0.299f * mean[0] * wb[0] + 0.587f * mean[1] * wb[1] + 0.114f * mean[2] * wb[2]) / 255f;
        my = clampF(my, 0.05f, 0.95f);
        float gamma = (float) (Math.log(0.46) / Math.log(my));
        gamma = clampF(1f + (gamma - 1f) * 0.75f * s, 0.7f, 1.35f);

        int[][] out = new int[3][256];
        for (int ch = 0; ch < 3; ch++) {
            for (int v = 0; v < 256; v++) {
                float x = clampF((gain[ch] * v + off[ch]) * wb[ch] / 255f, 0f, 1f);
                x = (float) Math.pow(x, gamma);
                float sc = x * x * (3f - 2f * x);
                x += 0.18f * s * (sc - x);
                out[ch][v] = Math.round(clampF(x, 0f, 1f) * 255f);
            }
        }
        return out;
    }

    /** Curvas + realce local de contraste (CLAHE na luz), que recupera sombras sem estourar o céu. */
    private static void autoTone(int[] px, int w, int h, boolean bw, float s) {
        int n = px.length;
        int[][] hist = new int[3][256];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            hist[0][(c >> 16) & 255]++;
            hist[1][(c >> 8) & 255]++;
            hist[2][c & 255]++;
        }
        int[][] cv = autoCurves(hist, n, s, bw);

        byte[] y = new byte[n];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            int r = cv[0][(c >> 16) & 255];
            int g = cv[1][(c >> 8) & 255];
            int b = cv[2][c & 255];
            px[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            y[i] = (byte) ((77 * r + 150 * g + 29 * b) >> 8);
        }

        final int tiles = 8;
        int tw = (w + tiles - 1) / tiles;
        int th = (h + tiles - 1) / tiles;
        int[][] lut = claheLuts(y, w, h, tiles, 2.5f);
        float blend = 0.55f * s;

        int[] xa = new int[w];
        int[] xb = new int[w];
        float[] xf = new float[w];
        for (int x = 0; x < w; x++) {
            float fx = (x + 0.5f) / tw - 0.5f;
            int i0 = (int) Math.floor(fx);
            xf[x] = fx - i0;
            xa[x] = clampI(i0, 0, tiles - 1);
            xb[x] = clampI(i0 + 1, 0, tiles - 1);
        }

        for (int yy = 0; yy < h; yy++) {
            float fy = (yy + 0.5f) / th - 0.5f;
            int j0 = (int) Math.floor(fy);
            float wy = fy - j0;
            int ja = clampI(j0, 0, tiles - 1) * tiles;
            int jb = clampI(j0 + 1, 0, tiles - 1) * tiles;
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                int idx = row + xx;
                int v = y[idx] & 255;
                float wx = xf[xx];
                int la = lut[ja + xa[xx]][v];
                int lb = lut[ja + xb[xx]][v];
                int lc = lut[jb + xa[xx]][v];
                int ld = lut[jb + xb[xx]][v];
                float top = la + (lb - la) * wx;
                float bot = lc + (ld - lc) * wx;
                float mapped = top + (bot - top) * wy;
                float ny = v + blend * (mapped - v);

                float ratio = v < 2 ? 1f : ny / v;
                ratio = clampF(ratio, 0.6f, 3f);

                int c = px[idx];
                int r = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                int mx = Math.max(r, Math.max(g, b));
                if (mx > 0 && ratio * mx > 255f) ratio = 255f / mx;
                px[idx] = (c & 0xFF000000) | (clamp(r * ratio) << 16) | (clamp(g * ratio) << 8) | clamp(b * ratio);
            }
        }
    }

    /** Uma tabela de equalização (com limite de contraste) para cada bloco da imagem. */
    private static int[][] claheLuts(byte[] y, int w, int h, int tiles, float clip) {
        int tw = (w + tiles - 1) / tiles;
        int th = (h + tiles - 1) / tiles;
        int[][] lut = new int[tiles * tiles][256];
        int[] hist = new int[256];
        for (int j = 0; j < tiles; j++) {
            for (int i = 0; i < tiles; i++) {
                int x0 = i * tw, x1 = Math.min(w, x0 + tw);
                int y0 = j * th, y1 = Math.min(h, y0 + th);
                int[] table = lut[j * tiles + i];
                if (x0 >= x1 || y0 >= y1) {
                    for (int k = 0; k < 256; k++) table[k] = k;
                    continue;
                }
                java.util.Arrays.fill(hist, 0);
                for (int yy = y0; yy < y1; yy++) {
                    int row = yy * w;
                    for (int xx = x0; xx < x1; xx++) hist[y[row + xx] & 255]++;
                }
                int area = (x1 - x0) * (y1 - y0);
                int limit = Math.max(1, (int) (clip * area / 256f));
                int excess = 0;
                for (int k = 0; k < 256; k++) {
                    if (hist[k] > limit) {
                        excess += hist[k] - limit;
                        hist[k] = limit;
                    }
                }
                int add = excess / 256;
                int cum = 0;
                for (int k = 0; k < 256; k++) {
                    cum += hist[k] + add;
                    table[k] = Math.min(255, Math.round(cum * 255f / area));
                }
            }
        }
        return lut;
    }

    // ------------------------------------------------------------------ nitidez

    /**
     * Máscara de nitidez só na luz (não cria cores falsas nem franjas coloridas).
     * Ignora diferenças minúsculas, para não realçar o ruído.
     */
    private static void sharpen(int[] px, int w, int h, float amount) {
        int n = px.length;
        byte[] y = new byte[n];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            y[i] = (byte) ((77 * ((c >> 16) & 255) + 150 * ((c >> 8) & 255) + 29 * (c & 255)) >> 8);
        }
        byte[] blur = new byte[n];
        int radius = Math.max(1, Math.round(Math.max(w, h) / 1100f));
        boxBlur(y, blur, w, h, radius);

        float k = amount * 1.6f;
        for (int i = 0; i < n; i++) {
            int d = (y[i] & 255) - (blur[i] & 255);
            float ad = Math.abs(d);
            float gate = ad < 2f ? ad / 2f : 1f;
            float delta = clampF(k * d * gate, -60f, 60f);
            int c = px[i];
            int r = clamp(((c >> 16) & 255) + delta);
            int g = clamp(((c >> 8) & 255) + delta);
            int b = clamp((c & 255) + delta);
            px[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
    }

    /** Desfoque em caixa (rápido): passa na horizontal e depois na vertical. */
    private static void boxBlur(byte[] src, byte[] dst, int w, int h, int r) {
        byte[] tmp = new byte[w * h];
        int win = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sum = 0;
            for (int k = -r; k <= r; k++) sum += src[row + clampI(k, 0, w - 1)] & 255;
            for (int x = 0; x < w; x++) {
                tmp[row + x] = (byte) (sum / win);
                int add = Math.min(w - 1, x + r + 1);
                int rem = Math.max(0, x - r);
                sum += (src[row + add] & 255) - (src[row + rem] & 255);
            }
        }
        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int k = -r; k <= r; k++) sum += tmp[clampI(k, 0, h - 1) * w + x] & 255;
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = (byte) (sum / win);
                int add = Math.min(h - 1, y + r + 1);
                int rem = Math.max(0, y - r);
                sum += (tmp[add * w + x] & 255) - (tmp[rem * w + x] & 255);
            }
        }
    }

    /** Marca d'água "NITIX" no canto (plano grátis). Devolve uma cópia. */
    static Bitmap watermark(Bitmap src) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        float size = Math.max(out.getWidth(), out.getHeight()) * 0.045f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xDDFFFFFF);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size);
        p.setShadowLayer(size * 0.12f, 0, 0, 0x99000000);
        String t = "NITIX";
        float x = out.getWidth() - p.measureText(t) - size * 0.7f;
        float y = out.getHeight() - size * 0.7f;
        c.drawText(t, x, y, p);
        return out;
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static int clamp(float v) {
        return v < 0f ? 0 : v > 255f ? 255 : Math.round(v);
    }
}
