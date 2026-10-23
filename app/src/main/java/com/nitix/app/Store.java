package com.nitix.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Plano (Grátis ou VIP) e limites de cada plano. Tudo fica salvo só no aparelho. */
final class Store {
    private Store() {}

    static final int MAX_SIDE = 4096;          // limite de resolução da foto
    static final long FREE_VIDEO_MS = 30_000L; // vídeo grátis: até 30 segundos

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nitix", Context.MODE_PRIVATE);
    }

    static boolean isVip(Context c) {
        return prefs(c).getBoolean("vip", false);
    }

    static void setVip(Context c, boolean vip) {
        prefs(c).edit().putBoolean("vip", vip).apply();
    }

    static int maxPhotoScale(Context c) {
        return isVip(c) ? 4 : 2;
    }

    static int maxVideoHeight(Context c) {
        return isVip(c) ? 1080 : 720;
    }

    static boolean watermark(Context c) {
        return !isVip(c);
    }
}
