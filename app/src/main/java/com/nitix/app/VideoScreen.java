package com.nitix.app;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tela "Melhorar vídeo". */
final class VideoScreen {
    private final MainActivity act;
    final ScrollView root;
    private final VideoView input;
    private final VideoView output;
    private final TextView info;
    private final TextView status;
    private final ProgressBar bar;
    private final Button genBtn;
    private final Button saveBtn;
    private final TextView res720;
    private final TextView res1080;
    private final VideoEngine engine;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Uri source;
    private File result;
    private int height = 720;
    private long durationMs;
    private boolean autoOn = true;
    private TextView autoChip;
    private CompareView compare;
    private Bitmap frame;
    private int[][] curves;
    private int previewVersion;
    private final Runnable previewTask = this::updateVideoPreview;
    private float brightness;
    private float contrast;
    private float saturation = 1f;

    VideoScreen(MainActivity a) {
        act = a;
        engine = new VideoEngine(a);
        int pad = Ui.dp(a, 18);

        root = new ScrollView(a);
        root.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, Ui.dp(a, 16), pad, Ui.dp(a, 28));
        root.addView(col, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));

        col.addView(Ui.text(a, "Melhorar vídeo", 26, Ui.TXT, true));
        col.addView(Ui.text(a, "No plano grátis, vídeos de até 30 segundos, em 720p.", 14, Ui.MUTED, false),
                Ui.lp(a, Ui.WRAP, Ui.WRAP, 4));

        Button pick = Ui.secondary(a, "Escolher vídeo da galeria");
        pick.setOnClickListener(v -> act.pickFile(MainActivity.REQ_VIDEO, "video/*"));
        col.addView(pick, Ui.lp(a, Ui.MATCH, Ui.WRAP, 16));

        col.addView(Ui.text(a, "Original", 13, Ui.LILAC, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 14));
        input = new VideoView(a);
        col.addView(input, Ui.lp(a, Ui.MATCH, Ui.dp(a, 220), 6));

        info = Ui.text(a, "Nenhum vídeo escolhido.", 13, Ui.MUTED, false);
        col.addView(info, Ui.lp(a, Ui.WRAP, Ui.WRAP, 6));

        col.addView(Ui.text(a, "Prévia do efeito (arraste a barra)", 13, Ui.LILAC, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 16));
        compare = new CompareView(a);
        compare.setBackground(Ui.outlined(a, Ui.CARD, 0x335B3DF0, 22));
        col.addView(compare, Ui.lp(a, Ui.MATCH, Ui.dp(a, 220), 6));
        col.addView(Ui.text(a, "É um quadro do vídeo com o efeito aplicado. O resultado final pode variar um pouco.", 12, Ui.MUTED, false),
                Ui.lp(a, Ui.WRAP, Ui.WRAP, 6));

        Ui.slider(a, col, "Brilho", -1f, 1f, 0f, 100f, v -> {
            brightness = v;
            scheduleVideoPreview();
        });
        Ui.slider(a, col, "Contraste", -1f, 1f, 0f, 100f, v -> {
            contrast = v;
            scheduleVideoPreview();
        });
        Ui.slider(a, col, "Cor (saturação)", 0f, 2f, 1f, 100f, v -> {
            saturation = v;
            scheduleVideoPreview();
        });

        autoChip = chip("Melhoria automática: LIGADA");
        autoChip.setPadding(Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14));
        styleChip(autoChip, true);
        autoChip.setOnClickListener(v -> {
            autoOn = !autoOn;
            autoChip.setText(autoOn ? "Melhoria automática: LIGADA" : "Melhoria automática: desligada");
            styleChip(autoChip, autoOn);
            scheduleVideoPreview();
        });
        col.addView(autoChip, Ui.lp(a, Ui.MATCH, Ui.WRAP, 18));
        col.addView(Ui.text(a, "Analisa o vídeo e ajusta luz, cor e equilíbrio de branco sozinho.", 12, Ui.MUTED, false),
                Ui.lp(a, Ui.WRAP, Ui.WRAP, 6));

        col.addView(Ui.text(a, "Resolução", 14, Ui.TXT, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 18));
        LinearLayout resRow = new LinearLayout(a);
        res720 = chip("720p");
        res1080 = chip("1080p (VIP)");
        res720.setOnClickListener(v -> chooseHeight(720));
        res1080.setOnClickListener(v -> chooseHeight(1080));
        resRow.addView(res720, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f);
        r2.leftMargin = Ui.dp(a, 8);
        resRow.addView(res1080, r2);
        col.addView(resRow, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));
        styleChip(res720, true);
        styleChip(res1080, false);

        genBtn = Ui.primary(a, "Gerar vídeo melhorado");
        genBtn.setOnClickListener(v -> generate());
        col.addView(genBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 22));

        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setVisibility(android.view.View.GONE);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.SUN));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.CARD));
        col.addView(bar, Ui.lp(a, Ui.MATCH, Ui.WRAP, 14));

        status = Ui.text(a, "", 13, Ui.LILAC, false);
        status.setGravity(Gravity.CENTER);
        col.addView(status, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        output = new VideoView(a);
        output.setVisibility(android.view.View.GONE);
        col.addView(output, Ui.lp(a, Ui.MATCH, Ui.dp(a, 220), 12));

        saveBtn = Ui.primary(a, "Salvar vídeo");
        saveBtn.setVisibility(android.view.View.GONE);
        saveBtn.setOnClickListener(v -> save());
        col.addView(saveBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 12));

        Ui.enter(pick, 0);
    }

    // ------------------------------------------------------------------ escolha do vídeo
    void onPicked(Uri u) {
        long ms = 0;
        int h = 0;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(act, u);
            ms = parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            h = (int) parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (Exception e) {
            act.toast("Não consegui abrir esse vídeo.");
            return;
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }
        if (!Store.isVip(act) && ms > Store.FREE_VIDEO_MS) {
            act.toast("No plano grátis, o vídeo pode ter até 30 segundos. Veja o plano VIP.");
            return;
        }
        source = u;
        durationMs = ms;
        result = null;
        output.setVisibility(android.view.View.GONE);
        saveBtn.setVisibility(android.view.View.GONE);
        input.setVideoURI(u);
        input.start();
        info.setText("Duração: " + Math.round(ms / 1000f) + " s  •  Altura original: " + h + " px");
        status.setText("");
        loadFrame(u, ms);
    }

    private static long parseLong(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ resolução
    private void chooseHeight(int h) {
        if (h > Store.maxVideoHeight(act)) {
            act.toast("A resolução 1080p é do plano VIP.");
            return;
        }
        height = h;
        styleChip(res720, h == 720);
        styleChip(res1080, h == 1080);
    }

    private TextView chip(String s) {
        TextView t = Ui.text(act, s, 14, Ui.TXT, true);
        t.setGravity(Gravity.CENTER);
        Ui.pressable(t);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? Ui.ON_SUN : Ui.TXT);
        t.setBackground(on ? Ui.box(act, Ui.SUN, 14) : Ui.outlined(act, Ui.CARD, Ui.VIOLET, 14));
    }

    // ------------------------------------------------------------------ prévia do efeito
    /** Pega um quadro do meio do vídeo e já analisa as cores, para mostrar a prévia e acelerar a exportação. */
    private void loadFrame(final Uri u, final long ms) {
        frame = null;
        curves = null;
        bg.execute(() -> {
            Bitmap f = null;
            MediaMetadataRetriever m = new MediaMetadataRetriever();
            try {
                m.setDataSource(act, u);
                Bitmap raw = m.getFrameAtTime(Math.max(1L, ms / 2) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (raw != null) {
                    float sc = Math.min(1f, 720f / Math.max(raw.getWidth(), raw.getHeight()));
                    f = sc < 1f
                            ? Bitmap.createScaledBitmap(raw, Math.round(raw.getWidth() * sc), Math.round(raw.getHeight() * sc), true)
                            : raw;
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    m.release();
                } catch (Exception ignored) {
                }
            }
            final Bitmap ff = f;
            final int[][] cv = VideoAnalyzer.analyze(act, u, ms, 0.9f);
            main.post(() -> {
                if (source != u) return;
                frame = ff;
                curves = cv;
                if (ff != null) {
                    compare.setBefore(ff);
                    compare.setAfter(ff);
                }
                updateVideoPreview();
            });
        });
    }

    private void scheduleVideoPreview() {
        main.removeCallbacks(previewTask);
        main.postDelayed(previewTask, 120);
    }

    private void updateVideoPreview() {
        final Bitmap f = frame;
        if (f == null) return;
        final int v = ++previewVersion;
        final int[][] cv = autoOn ? curves : null;
        final float b = brightness;
        final float c = contrast;
        final float s = saturation + (cv != null ? 0.10f : 0f);
        bg.execute(() -> {
            Bitmap a = PhotoEngine.applyCurves(f, cv);
            PhotoEngine.Params q = new PhotoEngine.Params();
            q.brightness = b;
            q.contrast = c;
            q.saturation = s;
            final Bitmap out = PhotoEngine.enhance(a, q);
            main.post(() -> {
                if (v == previewVersion) compare.setAfter(out);
            });
        });
    }

    // ------------------------------------------------------------------ gerar
    private void generate() {
        if (source == null) {
            act.toast("Escolha um vídeo primeiro.");
            return;
        }
        if (engine.isBusy()) return;
        genBtn.setEnabled(false);
        bar.setProgress(0);
        bar.setVisibility(android.view.View.VISIBLE);
        status.setText("Melhorando o vídeo... não feche o app.");
        if (autoOn && curves != null) {
            export(curves);
        } else if (autoOn) {
            status.setText("Analisando o vídeo...");
            final Uri src = source;
            final long dur = durationMs;
            bg.execute(() -> {
                final int[][] curves = VideoAnalyzer.analyze(act, src, dur, 0.9f);
                main.post(() -> export(curves));
            });
        } else {
            export(null);
        }
    }

    private void export(int[][] curves) {
        status.setText("Melhorando o vídeo... não feche o app.");
        float sat = saturation + (curves != null ? 0.10f : 0f);
        engine.start(source, brightness, contrast, sat, height, Store.watermark(act), curves,
                new VideoEngine.Callback() {
                    @Override
                    public void onProgress(int percent) {
                        bar.setProgress(percent);
                        status.setText("Melhorando o vídeo... " + percent + "%");
                    }

                    @Override
                    public void onDone(File out) {
                        result = out;
                        genBtn.setEnabled(true);
                        bar.setProgress(100);
                        status.setText("Pronto! Confira a prévia abaixo.");
                        output.setVideoPath(out.getAbsolutePath());
                        output.setVisibility(android.view.View.VISIBLE);
                        saveBtn.setVisibility(android.view.View.VISIBLE);
                        output.start();
                    }

                    @Override
                    public void onError(String message) {
                        genBtn.setEnabled(true);
                        bar.setVisibility(android.view.View.GONE);
                        status.setText("Não deu certo: " + message);
                    }
                });
    }

    // ------------------------------------------------------------------ salvar
    private void save() {
        final File f = result;
        if (f == null) return;
        saveBtn.setEnabled(false);
        saveBtn.setText("Salvando...");
        bg.execute(() -> {
            try {
                MediaSaver.saveVideo(act, f, "NITIX_" + System.currentTimeMillis());
                main.post(() -> finishSave("Vídeo salvo na galeria, em Vídeos > Nitix."));
            } catch (Exception e) {
                main.post(() -> finishSave("Não consegui salvar o vídeo."));
            }
        });
    }

    private void finishSave(String msg) {
        saveBtn.setEnabled(true);
        saveBtn.setText("Salvar vídeo");
        act.toast(msg);
    }

    // ------------------------------------------------------------------ ciclo de vida
    void onHide() {
        input.pause();
        output.pause();
    }
}
