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

        Params copy() {
            Params q = new Params();
            q.brightness = brightness;
            q.contrast = contrast;
            q.saturation = saturation;
            q.sharpness = sharpness;
            q.bw = bw;
            q.auto = auto;
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

    /** Aplica brilho, contraste, cor, preto e branco, ajuste automático e nitidez. */
    static Bitmap enhance(Bitmap src, Params p) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        float[] gain = {1f, 1f, 1f};
        float[] off = {0f, 0f, 0f};
        float sat = p.bw ? 0f : p.saturation;
        if (p.auto) {
            autoLevels(px, gain, off);
            if (!p.bw && sat == 1f) sat = 1.08f;
        }
        float cf = 1f + p.contrast;
        float bright = p.brightness * 255f;

        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = c >>> 24;
            float r = ((c >> 16) & 255) * gain[0] + off[0];
            float g = ((c >> 8) & 255) * gain[1] + off[1];
            float b = (c & 255) * gain[2] + off[2];

            r = (r - 128f) * cf + 128f + bright;
            g = (g - 128f) * cf + 128f + bright;
            b = (b - 128f) * cf + 128f + bright;

            float gray = 0.299f * r + 0.587f * g + 0.114f * b;
            r = gray + (r - gray) * sat;
            g = gray + (g - gray) * sat;
            b = gray + (b - gray) * sat;

            px[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }

        if (p.sharpness > 0f) sharpen(px, w, h, p.sharpness);
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Ajusta cada canal entre o 0,5% mais escuro e o 0,5% mais claro. */
    private static void autoLevels(int[] px, float[] gain, float[] off) {
        int[][] hist = new int[3][256];
        for (int c : px) {
            hist[0][(c >> 16) & 255]++;
            hist[1][(c >> 8) & 255]++;
            hist[2][c & 255]++;
        }
        int cut = px.length / 200;
        for (int ch = 0; ch < 3; ch++) {
            int lo = 0, acc = 0;
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
                gain[ch] = 1f;
                off[ch] = 0f;
            } else {
                gain[ch] = 255f / (hi - lo);
                off[ch] = -lo * gain[ch];
            }
        }
    }

    /** Nitidez: soma a diferença entre a imagem e uma versão borrada dela (máscara de nitidez). */
    private static void sharpen(int[] px, int w, int h, float amount) {
        Bitmap orig = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap small = Bitmap.createScaledBitmap(orig, Math.max(1, w / 4), Math.max(1, h / 4), true);
        Bitmap blur = Bitmap.createScaledBitmap(small, w, h, true);
        int[] bl = new int[w * h];
        blur.getPixels(bl, 0, w, 0, 0, w, h);
        orig.recycle();
        small.recycle();
        blur.recycle();

        float k = amount * 1.2f;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int d = bl[i];
            int r = clamp(((c >> 16) & 255) + k * (((c >> 16) & 255) - ((d >> 16) & 255)));
            int g = clamp(((c >> 8) & 255) + k * (((c >> 8) & 255) - ((d >> 8) & 255)));
            int b = clamp((c & 255) + k * ((c & 255) - (d & 255)));
            px[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
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

    private static int clamp(float v) {
        return v < 0f ? 0 : v > 255f ? 255 : Math.round(v);
    }
}
