package com.nitix.app;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Tela "Melhorar foto". */
final class PhotoScreen {
    private final MainActivity act;
    final ScrollView root;
    private final LinearLayout col;
    private final CompareView compare;
    private final TextView hint;
    private final TextView bwChip;
    private final TextView[] scaleChips = new TextView[3];
    private final TextView saveBtn;
    private final SeekBar sBright, sContrast, sSat, sSharp;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger version = new AtomicInteger();
    private final Runnable recompute = this::updatePreview;

    private Uri source;
    private Bitmap preview;
    private PhotoEngine.Params p = new PhotoEngine.Params();
    private int scale = 1;

    PhotoScreen(MainActivity a) {
        act = a;
        int pad = Ui.dp(a, 18);

        root = new ScrollView(a);
        root.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, Ui.dp(a, 16), pad, Ui.dp(a, 28));
        root.addView(col, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));

        col.addView(Ui.text(a, "Melhorar foto", 26, Ui.TXT, true));
        col.addView(Ui.text(a, "Arraste a barra no meio para ver antes e depois.", 14, Ui.MUTED, false),
                Ui.lp(a, Ui.WRAP, Ui.WRAP, 4));

        compare = new CompareView(a);
        compare.setBackground(Ui.outlined(a, Ui.CARD, 0x335B3DF0, 22));
        col.addView(compare, Ui.lp(a, Ui.MATCH, Ui.dp(a, 320), 14));

        hint = Ui.text(a, "Escolha uma foto da galeria para começar.", 13, Ui.LILAC, false);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        android.widget.Button pick = Ui.secondary(a, "Escolher foto da galeria");
        pick.setOnClickListener(v -> act.pickFile(MainActivity.REQ_PHOTO, "image/*"));
        col.addView(pick, Ui.lp(a, Ui.MATCH, Ui.WRAP, 12));

        // ---- ajustes
        sBright = Ui.slider(a, col, "Brilho", -1f, 1f, 0f, 100f, v -> {
            p.brightness = v;
            schedule();
        });
        sContrast = Ui.slider(a, col, "Contraste", -1f, 1f, 0f, 100f, v -> {
            p.contrast = v;
            schedule();
        });
        sSat = Ui.slider(a, col, "Cor (saturação)", 0f, 2f, 1f, 100f, v -> {
            p.saturation = v;
            schedule();
        });
        sSharp = Ui.slider(a, col, "Nitidez", 0f, 1f, 0f, 100f, v -> {
            p.sharpness = v;
            schedule();
        });

        // ---- botões de efeito
        LinearLayout row1 = new LinearLayout(a);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Button auto = Ui.primary(a, "Melhorar automático");
        auto.setOnClickListener(v -> {
            p.auto = true;
            updatePreview();
        });
        row1.addView(auto, new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f));
        android.widget.Button zero = Ui.secondary(a, "Zerar");
        zero.setOnClickListener(v -> resetAll());
        LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f);
        zp.leftMargin = Ui.dp(a, 10);
        row1.addView(zero, zp);
        col.addView(row1, Ui.lp(a, Ui.MATCH, Ui.WRAP, 20));

        LinearLayout row2 = new LinearLayout(a);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        bwChip = chip("Preto e branco");
        bwChip.setOnClickListener(v -> {
            p.bw = !p.bw;
            styleChip(bwChip, p.bw);
            schedule();
        });
        row2.addView(bwChip, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f));
        col.addView(row2, Ui.lp(a, Ui.MATCH, Ui.WRAP, 10));

        // ---- resolução (só vale ao salvar)
        col.addView(Ui.text(a, "Aumentar resolução ao salvar", 14, Ui.TXT, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 20));
        LinearLayout res = new LinearLayout(a);
        String[] labels = {"Normal", "2x", "4x (VIP)"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            scaleChips[i] = chip(labels[i]);
            scaleChips[i].setOnClickListener(v -> chooseScale(idx));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f);
            if (i > 0) sp.leftMargin = Ui.dp(a, 8);
            res.addView(scaleChips[i], sp);
        }
        col.addView(res, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));
        styleChip(scaleChips[0], true);

        saveBtn = Ui.primary(a, "Salvar foto melhorada");
        saveBtn.setOnClickListener(v -> save());
        col.addView(saveBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 24));

        Ui.enter(compare, 0);
    }

    // ------------------------------------------------------------------ escolha da foto
    void onPicked(final Uri u) {
        source = u;
        hint.setText("Carregando a foto...");
        bg.execute(() -> {
            try {
                final Bitmap b = PhotoEngine.decode(act.getContentResolver(), u, 1280);
                main.post(() -> {
                    preview = b;
                    compare.setBefore(b);
                    compare.setAfter(b);
                    hint.setText("Ajuste as barras ou toque em \"Melhorar automático\".");
                    updatePreview();
                });
            } catch (Exception e) {
                main.post(() -> act.toast("Não consegui abrir essa imagem."));
            }
        });
    }

    // ------------------------------------------------------------------ prévia
    private void schedule() {
        main.removeCallbacks(recompute);
        main.postDelayed(recompute, 90);
    }

    private void updatePreview() {
        final Bitmap src = preview;
        if (src == null) return;
        final int v = version.incrementAndGet();
        final PhotoEngine.Params snap = p.copy();
        bg.execute(() -> {
            Bitmap out = PhotoEngine.enhance(src, snap);
            main.post(() -> {
                if (v == version.get()) compare.setAfter(out);
            });
        });
    }

    private void resetAll() {
        p = new PhotoEngine.Params();
        setSlider(sBright, -1f, 1f, 0f);
        setSlider(sContrast, -1f, 1f, 0f);
        setSlider(sSat, 0f, 2f, 1f);
        setSlider(sSharp, 0f, 1f, 0f);
        styleChip(bwChip, false);
        chooseScale(0);
        updatePreview();
    }

    private static void setSlider(SeekBar bar, float min, float max, float v) {
        bar.setProgress(Math.round((v - min) / (max - min) * 1000f));
    }

    // ------------------------------------------------------------------ resolução
    private void chooseScale(int idx) {
        int value = idx == 0 ? 1 : idx == 1 ? 2 : 4;
        if (value > Store.maxPhotoScale(act)) {
            act.toast("A resolução 4x é do plano VIP.");
            return;
        }
        scale = value;
        for (int i = 0; i < scaleChips.length; i++) styleChip(scaleChips[i], i == idx);
    }

    private TextView chip(String s) {
        TextView t = Ui.text(act, s, 14, Ui.TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));
        styleChip(t, false);
        Ui.pressable(t);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? Ui.ON_SUN : Ui.TXT);
        t.setBackground(on ? Ui.box(act, Ui.SUN, 14) : Ui.outlined(act, Ui.CARD, Ui.VIOLET, 14));
    }

    // ------------------------------------------------------------------ salvar
    private void save() {
        if (source == null) {
            act.toast("Escolha uma foto primeiro.");
            return;
        }
        saveBtn.setEnabled(false);
        saveBtn.setText("Salvando...");
        final PhotoEngine.Params snap = p.copy();
        final int sc = scale;
        final boolean wm = Store.watermark(act);
        bg.execute(() -> {
            try {
                Bitmap base = PhotoEngine.decode(act.getContentResolver(), source, Store.MAX_SIDE);
                Bitmap big = PhotoEngine.upscale(base, sc, Store.MAX_SIDE);
                Bitmap out = PhotoEngine.enhance(big, snap);
                if (wm) out = PhotoEngine.watermark(out);
                MediaSaver.saveImage(act, out, "NITIX_" + System.currentTimeMillis());
                main.post(() -> finishSave("Foto salva na galeria, em Imagens > Nitix."));
            } catch (Throwable t) {
                main.post(() -> finishSave("Não consegui salvar. Tente a resolução normal."));
            }
        });
    }

    private void finishSave(String msg) {
        saveBtn.setEnabled(true);
        saveBtn.setText("Salvar foto melhorada");
        act.toast(msg);
    }
}
