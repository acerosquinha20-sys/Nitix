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
        int filter;              // 0 = original, 1.. = filtros prontos
        int rotation;            // quartos de volta (0..3)
        boolean flip;            // espelhar
        int crop;                // 0 original, 1 = 1:1, 2 = 4:5, 3 = 16:9, 4 = 9:16
        float vignette;          // 0 .. 1
        float edgeBlur;          // 0 .. 1 (desfoque nas bordas, foco no centro)
        float denoise;           // 0 .. 1
        String text = "";
        int textPos = 2;         // 0 topo, 1 centro, 2 base
        int textColor;           // índice em TEXT_COLORS
        String sticker = "";
        int stickerPos = 1;      // 0 sup. esq., 1 sup. dir., 2 centro, 3 inf. esq., 4 inf. dir.

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
            q.filter = filter;
            q.rotation = rotation;
            q.flip = flip;
            q.crop = crop;
            q.vignette = vignette;
            q.edgeBlur = edgeBlur;
            q.denoise = denoise;
            q.text = text;
            q.textPos = textPos;
            q.textColor = textColor;
            q.sticker = sticker;
            q.stickerPos = stickerPos;
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
        if (p.denoise > 0f) denoise(px, w, h, p.denoise);
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

        if (p.filter > 0 && !p.bw) applyFilter(px, p.filter);
        if (p.vignette > 0f || p.edgeBlur > 0f) radial(px, w, h, p.vignette, p.edgeBlur);

        // nitidez: a do usuário ou a automática (maior quando a foto foi ampliada)
        float autoSharp = s > 0f ? (0.25f + 0.10f * (Math.max(1, p.scale) - 1)) * s : 0f;
        float sharp = Math.max(p.sharpness, autoSharp);
        if (sharp > 0f) sharpen(px, w, h, sharp);

        return decorate(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888), p);
    }

    // ------------------------------------------------------------------ girar, cortar, curvas

    /** Gira, espelha e corta (do centro) conforme os parâmetros. */
    static Bitmap geometry(Bitmap src, Params p) {
        Bitmap b = src;
        int turns = ((p.rotation % 4) + 4) % 4;
        if (turns != 0 || p.flip) {
            Matrix m = new Matrix();
            if (p.flip) m.postScale(-1f, 1f);
            m.postRotate(90f * turns);
            b = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        }
        if (p.crop > 0) {
            float ratio = p.crop == 1 ? 1f : p.crop == 2 ? 4f / 5f : p.crop == 3 ? 16f / 9f : 9f / 16f;
            int w = b.getWidth(), h = b.getHeight();
            int cw = w, ch = h;
            if ((float) w / h > ratio) cw = Math.round(h * ratio);
            else ch = Math.round(w / ratio);
            cw = Math.max(1, Math.min(w, cw));
            ch = Math.max(1, Math.min(h, ch));
            if (cw != w || ch != h) b = Bitmap.createBitmap(b, (w - cw) / 2, (h - ch) / 2, cw, ch);
        }
        return b;
    }

    /** Aplica curvas por canal (as mesmas do vídeo) a uma imagem. */
    static Bitmap applyCurves(Bitmap src, int[][] cv) {
        if (cv == null) return src;
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            px[i] = (c & 0xFF000000) | (cv[0][(c >> 16) & 255] << 16) | (cv[1][(c >> 8) & 255] << 8) | cv[2][c & 255];
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    // ------------------------------------------------------------------ filtros

    static final String[] FILTERS = {"Original", "Cinema", "Vintage", "Vívido", "Quente", "Frio", "Drama", "Suave"};

    // contraste, saturação, clareamento dos pretos, multiplicador R/G/B, tom nas sombras R/G/B, tom nas luzes R/G/B
    private static final float[][] FILTER_TABLE = {
            {},
            {1.10f, 0.90f, 4f, 1f, 1f, 1f, -10f, 0f, 14f, 16f, 4f, -14f},
            {0.92f, 0.78f, 24f, 1.04f, 1f, 0.92f, 6f, 4f, 0f, 12f, 6f, -8f},
            {1.12f, 1.40f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f},
            {1.04f, 1.05f, 0f, 1.08f, 1f, 0.90f, 6f, 0f, 0f, 10f, 4f, -8f},
            {1.04f, 1.00f, 0f, 0.92f, 0.99f, 1.10f, -4f, 0f, 10f, -6f, 0f, 8f},
            {1.28f, 0.82f, 0f, 1f, 1f, 1f, -8f, -8f, -4f, 6f, 6f, 6f},
            {0.88f, 0.95f, 18f, 1.02f, 1.02f, 1.02f, 8f, 6f, 10f, 0f, 0f, 0f},
    };

    private static void applyFilter(int[] px, int f) {
        if (f <= 0 || f >= FILTER_TABLE.length) return;
        float[] t = FILTER_TABLE[f];
        float k = (255f - t[2]) / 255f;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            float r = (c >> 16) & 255;
            float g = (c >> 8) & 255;
            float b = c & 255;
            float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
            float sh = (1f - lum) * (1f - lum);
            float hi = lum * lum;
            r += t[6] * sh + t[9] * hi;
            g += t[7] * sh + t[10] * hi;
            b += t[8] * sh + t[11] * hi;
            r *= t[3];
            g *= t[4];
            b *= t[5];
            r = (r - 128f) * t[0] + 128f;
            g = (g - 128f) * t[0] + 128f;
            b = (b - 128f) * t[0] + 128f;
            float gray = 0.299f * r + 0.587f * g + 0.114f * b;
            r = gray + (r - gray) * t[1];
            g = gray + (g - gray) * t[1];
            b = gray + (b - gray) * t[1];
            r = t[2] + r * k;
            g = t[2] + g * k;
            b = t[2] + b * k;
            px[i] = (c & 0xFF000000) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }
    }

    // ------------------------------------------------------------------ vinheta, foco no centro, ruído

    /** Escurece as bordas (vinheta) e/ou borra as bordas deixando o centro nítido. */
    private static void radial(int[] px, int w, int h, float vig, float edge) {
        int[] blur = null;
        if (edge > 0f) {
            Bitmap cur = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            int cw = w, ch = h;
            while (cw > Math.max(2, w / 14)) {
                cw = Math.max(1, cw / 2);
                ch = Math.max(1, ch / 2);
                Bitmap next = Bitmap.createScaledBitmap(cur, cw, ch, true);
                cur.recycle();
                cur = next;
            }
            Bitmap up = Bitmap.createScaledBitmap(cur, w, h, true);
            blur = new int[w * h];
            up.getPixels(blur, 0, w, 0, 0, w, h);
            cur.recycle();
            up.recycle();
        }
        float cx = w / 2f, cy = h / 2f;
        for (int y = 0; y < h; y++) {
            float dy = (y - cy) / cy;
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / cx;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / 1.4142f;
                int i = y * w + x;
                int c = px[i];
                float r = (c >> 16) & 255;
                float g = (c >> 8) & 255;
                float b = c & 255;
                if (blur != null) {
                    float m = smooth((d - 0.28f) / 0.5f) * edge;
                    int q = blur[i];
                    r += (((q >> 16) & 255) - r) * m;
                    g += (((q >> 8) & 255) - g) * m;
                    b += ((q & 255) - b) * m;
                }
                if (vig > 0f) {
                    float f = 1f - vig * 0.7f * smooth((d - 0.35f) / 0.65f);
                    r *= f;
                    g *= f;
                    b *= f;
                }
                px[i] = (c & 0xFF000000) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
    }

    private static float smooth(float t) {
        t = clampF(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Reduz o ruído só onde a diferença para os vizinhos é pequena, para não borrar os detalhes. */
    private static void denoise(int[] px, int w, int h, float amount) {
        int n = px.length;
        byte[] src = new byte[n];
        byte[] blur = new byte[n];
        int radius = Math.max(1, Math.round(Math.max(w, h) / 900f));
        for (int ch = 0; ch < 3; ch++) {
            int shift = 16 - 8 * ch;
            for (int i = 0; i < n; i++) src[i] = (byte) (px[i] >> shift);
            boxBlur(src, blur, w, h, radius);
            for (int i = 0; i < n; i++) {
                int v = src[i] & 255;
                int bl = blur[i] & 255;
                float wgt = amount * Math.max(0f, 1f - Math.abs(v - bl) / 28f);
                int nv = Math.round(v + (bl - v) * wgt);
                px[i] = (px[i] & ~(255 << shift)) | (nv << shift);
            }
        }
    }

    // ------------------------------------------------------------------ texto e adesivo

    static final int[] TEXT_COLORS = {0xFFFFFFFF, 0xFFFFD166, 0xFF7DF0FF, 0xFFFF5CD6, 0xFF000000};

    /** Escreve o texto e o adesivo (emoji) na foto. Tamanhos relativos, iguais na prévia e no arquivo final. */
    static Bitmap decorate(Bitmap src, Params p) {
        boolean hasText = p.text != null && !p.text.trim().isEmpty();
        boolean hasSticker = p.sticker != null && !p.sticker.isEmpty();
        if (!hasText && !hasSticker) return src;

        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas cv = new Canvas(out);
        int w = out.getWidth(), h = out.getHeight();
        float base = Math.min(w, h);

        if (hasSticker) {
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setTextSize(base * 0.24f);
            sp.setTextAlign(Paint.Align.CENTER);
            float m = base * 0.04f;
            float half = base * 0.12f;
            float cx = w / 2f, cy = h / 2f;
            switch (p.stickerPos) {
                case 0:
                    cx = m + half;
                    cy = m + half;
                    break;
                case 1:
                    cx = w - m - half;
                    cy = m + half;
                    break;
                case 3:
                    cx = m + half;
                    cy = h - m - half;
                    break;
                case 4:
                    cx = w - m - half;
                    cy = h - m - half;
                    break;
                default:
                    break;
            }
            cv.drawText(p.sticker, cx, cy - (sp.ascent() + sp.descent()) / 2f, sp);
        }

        if (hasText) {
            String t = p.text.trim();
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(TEXT_COLORS[Math.max(0, Math.min(TEXT_COLORS.length - 1, p.textColor))]);
            float size = base * 0.09f;
            tp.setTextSize(size);
            while (tp.measureText(t) > w * 0.9f && size > base * 0.03f) {
                size *= 0.92f;
                tp.setTextSize(size);
            }
            tp.setShadowLayer(size * 0.10f, 0f, size * 0.04f, 0xCC000000);
            float y;
            if (p.textPos == 0) y = size * 1.6f;
            else if (p.textPos == 1) y = h / 2f - (tp.ascent() + tp.descent()) / 2f;
            else y = h - base * 0.10f;
            cv.drawText(t, w / 2f, y, tp);
        }
        return out;
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
