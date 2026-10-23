package com.nitix.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Tela "Melhorar foto". */
final class PhotoScreen {
    private static final String[] CROPS = {"Original", "1:1", "4:5", "16:9", "9:16"};
    private static final String[] TEXT_POS = {"Topo", "Centro", "Base"};
    private static final String[] STICKERS = {"😀", "❤️", "⭐", "🔥", "😎", "✨", "🎉", "💯"};
    private static final String[] STICKER_POS = {"↖", "↗", "●", "↙", "↘"};

    private final MainActivity act;
    final ScrollView root;
    private LinearLayout col;
    private CompareView compare;
    private TextView hint;
    private TextView bwChip;
    private TextView mirrorChip;
    private EditText textField;
    private android.widget.Button saveBtn;
    private android.widget.Button shareBtn;
    private SeekBar sBright, sContrast, sSat, sSharp, sAuto, sVig, sEdge, sDen;

    private final TextView[] scaleChips = new TextView[3];
    private final TextView[] filterChips = new TextView[PhotoEngine.FILTERS.length];
    private final TextView[] cropChips = new TextView[CROPS.length];
    private final TextView[] posChips = new TextView[TEXT_POS.length];
    private final TextView[] colorChips = new TextView[PhotoEngine.TEXT_COLORS.length];
    private final TextView[] stickerChips = new TextView[STICKERS.length];
    private final TextView[] spChips = new TextView[STICKER_POS.length];

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger version = new AtomicInteger();
    private final AtomicInteger geoVersion = new AtomicInteger();
    private final Runnable recompute = this::updatePreview;

    private Uri source;
    private Uri lastSaved;
    private Bitmap previewBase;   // foto reduzida, sem girar nem cortar
    private Bitmap preview;       // previewBase depois de girar e cortar
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

        // ---------------------------------------------------------------- melhoria automática
        section("Melhoria automática");
        LinearLayout row1 = new LinearLayout(a);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Button auto = Ui.primary(a, "Melhorar automático");
        auto.setOnClickListener(v -> {
            if (previewBase == null) {
                act.toast("Escolha uma foto primeiro.");
                return;
            }
            p.auto = true;
            hint.setText("Melhoria automática aplicada. Ajuste a intensidade abaixo.");
            updatePreview();
        });
        row1.addView(auto, new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f));
        android.widget.Button zero = Ui.secondary(a, "Zerar");
        zero.setOnClickListener(v -> resetAll());
        LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f);
        zp.leftMargin = Ui.dp(a, 10);
        row1.addView(zero, zp);
        col.addView(row1, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        sAuto = Ui.slider(a, col, "Intensidade da melhoria automática", 0f, 1f, 1f, 100f, v -> {
            p.autoStrength = v;
            if (p.auto) schedule();
        });

        // ---------------------------------------------------------------- filtros
        section("Filtros");
        LinearLayout fr = new LinearLayout(a);
        for (int i = 0; i < PhotoEngine.FILTERS.length; i++) {
            final int idx = i;
            filterChips[i] = scrollChip(fr, PhotoEngine.FILTERS[i], i > 0);
            filterChips[i].setOnClickListener(v -> {
                p.filter = idx;
                mark(filterChips, idx);
                schedule();
            });
        }
        col.addView(hscroll(fr), Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));
        mark(filterChips, 0);

        // ---------------------------------------------------------------- ajustes
        section("Ajustes");
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
        sDen = Ui.slider(a, col, "Reduzir ruído", 0f, 1f, 0f, 100f, v -> {
            p.denoise = v;
            schedule();
        });
        sVig = Ui.slider(a, col, "Vinheta (bordas escuras)", 0f, 1f, 0f, 100f, v -> {
            p.vignette = v;
            schedule();
        });
        sEdge = Ui.slider(a, col, "Foco no centro (borra as bordas)", 0f, 1f, 0f, 100f, v -> {
            p.edgeBlur = v;
            schedule();
        });

        bwChip = chip("Preto e branco");
        bwChip.setOnClickListener(v -> {
            p.bw = !p.bw;
            styleChip(bwChip, p.bw);
            schedule();
        });
        col.addView(bwChip, Ui.lp(a, Ui.MATCH, Ui.dp(a, 48), 18));

        // ---------------------------------------------------------------- girar e cortar
        section("Girar e cortar");
        LinearLayout gr = new LinearLayout(a);
        TextView rotate = chip("Girar ↻");
        rotate.setOnClickListener(v -> {
            p.rotation = (p.rotation + 1) % 4;
            geometryChanged();
        });
        gr.addView(rotate, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f));
        mirrorChip = chip("Espelhar");
        mirrorChip.setOnClickListener(v -> {
            p.flip = !p.flip;
            styleChip(mirrorChip, p.flip);
            geometryChanged();
        });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f);
        mp.leftMargin = Ui.dp(a, 8);
        gr.addView(mirrorChip, mp);
        col.addView(gr, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        LinearLayout cr = new LinearLayout(a);
        for (int i = 0; i < CROPS.length; i++) {
            final int idx = i;
            cropChips[i] = scrollChip(cr, CROPS[i], i > 0);
            cropChips[i].setOnClickListener(v -> {
                p.crop = idx;
                mark(cropChips, idx);
                geometryChanged();
            });
        }
        col.addView(hscroll(cr), Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));
        mark(cropChips, 0);

        // ---------------------------------------------------------------- texto e adesivo
        section("Texto e adesivo");
        textField = Ui.field(a, "Escreva um texto na foto",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        textField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                p.text = e.toString();
                schedule();
            }
        });
        col.addView(textField, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        LinearLayout pr = new LinearLayout(a);
        for (int i = 0; i < TEXT_POS.length; i++) {
            final int idx = i;
            posChips[i] = chip(TEXT_POS[i]);
            posChips[i].setOnClickListener(v -> {
                p.textPos = idx;
                mark(posChips, idx);
                schedule();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(a, 8);
            pr.addView(posChips[i], lp);
        }
        col.addView(pr, Ui.lp(a, Ui.MATCH, Ui.WRAP, 10));
        mark(posChips, 2);

        LinearLayout cc = new LinearLayout(a);
        for (int i = 0; i < PhotoEngine.TEXT_COLORS.length; i++) {
            final int idx = i;
            TextView t = new TextView(a);
            colorChips[i] = t;
            t.setOnClickListener(v -> {
                p.textColor = idx;
                markColors(idx);
                schedule();
            });
            Ui.pressable(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 38), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(a, 8);
            cc.addView(t, lp);
        }
        col.addView(cc, Ui.lp(a, Ui.MATCH, Ui.WRAP, 10));
        markColors(0);

        col.addView(Ui.text(a, "Adesivo", 13, Ui.MUTED, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 14));
        LinearLayout sr = new LinearLayout(a);
        for (int i = 0; i < STICKERS.length; i++) {
            final int idx = i;
            stickerChips[i] = scrollChip(sr, STICKERS[i], i > 0);
            stickerChips[i].setTextSize(22);
            stickerChips[i].setOnClickListener(v -> {
                boolean same = STICKERS[idx].equals(p.sticker);
                p.sticker = same ? "" : STICKERS[idx];
                mark(stickerChips, same ? -1 : idx);
                schedule();
            });
        }
        col.addView(hscroll(sr), Ui.lp(a, Ui.MATCH, Ui.WRAP, 6));
        mark(stickerChips, -1);

        LinearLayout spr = new LinearLayout(a);
        for (int i = 0; i < STICKER_POS.length; i++) {
            final int idx = i;
            spChips[i] = chip(STICKER_POS[i]);
            spChips[i].setOnClickListener(v -> {
                p.stickerPos = idx;
                mark(spChips, idx);
                schedule();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(a, 8);
            spr.addView(spChips[i], lp);
        }
        col.addView(spr, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));
        mark(spChips, 1);

        // ---------------------------------------------------------------- resolução (só vale ao salvar)
        section("Aumentar resolução ao salvar");
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

        shareBtn = Ui.secondary(a, "Compartilhar a foto salva");
        shareBtn.setVisibility(View.GONE);
        shareBtn.setOnClickListener(v -> share());
        col.addView(shareBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 10));

        Ui.enter(compare, 0);
    }

    // ------------------------------------------------------------------ montagem
    private void section(String title) {
        col.addView(Ui.text(act, title, 16, Ui.TXT, true), Ui.lp(act, Ui.WRAP, Ui.WRAP, 24));
    }

    private HorizontalScrollView hscroll(LinearLayout row) {
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row, new ViewGroup.LayoutParams(Ui.WRAP, Ui.WRAP));
        return hs;
    }

    private TextView scrollChip(LinearLayout row, String label, boolean gap) {
        TextView t = chip(label);
        t.setPadding(Ui.dp(act, 16), Ui.dp(act, 10), Ui.dp(act, 16), Ui.dp(act, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(act, 44));
        if (gap) lp.leftMargin = Ui.dp(act, 8);
        row.addView(t, lp);
        return t;
    }

    private static void setSlider(SeekBar bar, float min, float max, float v) {
        bar.setProgress(Math.round((v - min) / (max - min) * 1000f));
    }

    private void mark(TextView[] chips, int selected) {
        for (int i = 0; i < chips.length; i++) styleChip(chips[i], i == selected);
    }

    private void markColors(int selected) {
        for (int i = 0; i < colorChips.length; i++) {
            GradientDrawable g = Ui.box(act, PhotoEngine.TEXT_COLORS[i], 12);
            g.setStroke(Ui.dp(act, i == selected ? 3 : 1), i == selected ? 0xFFFFFFFF : 0x665B3DF0);
            colorChips[i].setBackground(g);
        }
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

    // ------------------------------------------------------------------ escolha da foto
    void onPicked(final Uri u) {
        source = u;
        lastSaved = null;
        shareBtn.setVisibility(View.GONE);
        hint.setText("Carregando a foto...");
        bg.execute(() -> {
            try {
                final Bitmap b = PhotoEngine.decode(act.getContentResolver(), u, 1280);
                main.post(() -> {
                    previewBase = b;
                    // foto nova: começa sem girar nem cortar e já com a melhoria automática ligada
                    p.rotation = 0;
                    p.flip = false;
                    p.crop = 0;
                    p.auto = true;
                    styleChip(mirrorChip, false);
                    mark(cropChips, 0);
                    hint.setText("Melhoria automática aplicada. Use os filtros e ajustes à vontade.");
                    geometryChanged();
                });
            } catch (Exception e) {
                main.post(() -> act.toast("Não consegui abrir essa imagem."));
            }
        });
    }

    // ------------------------------------------------------------------ prévia
    /** Girou, espelhou ou cortou: refaz a foto-base da prévia e depois os efeitos. */
    private void geometryChanged() {
        final Bitmap base = previewBase;
        if (base == null) return;
        final int v = geoVersion.incrementAndGet();
        final PhotoEngine.Params snap = p.copy();
        bg.execute(() -> {
            final Bitmap g = PhotoEngine.geometry(base, snap);
            main.post(() -> {
                if (v != geoVersion.get()) return;
                preview = g;
                compare.setBefore(g);
                compare.setAfter(g);
                updatePreview();
            });
        });
    }

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
        setSlider(sAuto, 0f, 1f, 1f);
        setSlider(sDen, 0f, 1f, 0f);
        setSlider(sVig, 0f, 1f, 0f);
        setSlider(sEdge, 0f, 1f, 0f);
        styleChip(bwChip, false);
        styleChip(mirrorChip, false);
        mark(filterChips, 0);
        mark(cropChips, 0);
        mark(posChips, 2);
        mark(stickerChips, -1);
        mark(spChips, 1);
        markColors(0);
        textField.setText("");
        chooseScale(0);
        geometryChanged();
    }

    // ------------------------------------------------------------------ resolução
    private void chooseScale(int idx) {
        int value = idx == 0 ? 1 : idx == 1 ? 2 : 4;
        if (value > Store.maxPhotoScale(act)) {
            act.toast("A resolução 4x é do plano VIP.");
            return;
        }
        scale = value;
        mark(scaleChips, idx);
    }

    // ------------------------------------------------------------------ salvar e compartilhar
    private void save() {
        if (source == null) {
            act.toast("Escolha uma foto primeiro.");
            return;
        }
        saveBtn.setEnabled(false);
        saveBtn.setText("Salvando...");
        final PhotoEngine.Params snap = p.copy();
        final int sc = scale;
        snap.scale = sc;
        final boolean wm = Store.watermark(act);
        bg.execute(() -> {
            try {
                Bitmap base = PhotoEngine.decode(act.getContentResolver(), source, Store.MAX_SIDE);
                Bitmap geo = PhotoEngine.geometry(base, snap);
                Bitmap big = PhotoEngine.upscale(geo, sc, Store.MAX_SIDE);
                Bitmap out = PhotoEngine.enhance(big, snap);
                if (wm) out = PhotoEngine.watermark(out);
                final Uri saved = MediaSaver.saveImage(act, out, "NITIX_" + System.currentTimeMillis());
                main.post(() -> {
                    lastSaved = saved;
                    shareBtn.setVisibility(View.VISIBLE);
                    finishSave("Foto salva na galeria, em Imagens > Nitix.");
                });
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

    private void share() {
        if (lastSaved == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/jpeg");
        i.putExtra(Intent.EXTRA_STREAM, lastSaved);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        act.startActivity(Intent.createChooser(i, "Compartilhar foto"));
    }
}
