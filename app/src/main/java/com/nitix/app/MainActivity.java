package com.nitix.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends android.app.Activity {

    static final int REQ_PHOTO = 21;
    static final int REQ_VIDEO = 22;

    private static final int TAB_HOME = 0;
    private static final int TAB_PHOTO = 1;
    private static final int TAB_VIDEO = 2;
    private static final int TAB_VIP = 3;
    private static final String[] TAB_NAMES = {"Início", "Foto", "Vídeo", "VIP"};

    private FrameLayout content;
    private final TextView[] tabs = new TextView[4];
    private int current = -1;

    private PhotoScreen photo;
    private VideoScreen video;
    private View home;
    private View vip;
    private TextView planText;
    private TextView vipStatus;
    private Button vipTest;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.CARD);
        setContentView(buildRoot());

        photo = new PhotoScreen(this);
        video = new VideoScreen(this);
        home = buildHome();
        vip = buildVip();
        showTab(TAB_HOME);

        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
    }

    // ------------------------------------------------------------------ estrutura e menu
    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setBackground(Ui.outlined(this, Ui.CARD, 0x335B3DF0, 0));
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 14));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TAB_NAMES[i], 14, Ui.MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            t.setOnClickListener(v -> showTab(idx));
            Ui.pressable(t);
            tabs[i] = t;
            nav.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        }
        root.addView(nav, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        return root;
    }

    void showTab(int i) {
        if (i == current) return;
        if (current == TAB_VIDEO && video != null) video.onHide();
        current = i;

        content.removeAllViews();
        View page = i == TAB_HOME ? home
                : i == TAB_PHOTO ? photo.root
                : i == TAB_VIDEO ? video.root
                : vip;
        if (i == TAB_VIP) refreshVip();
        if (i == TAB_HOME) refreshPlan();
        content.addView(page, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Ui.enter(page, 0);

        for (int t = 0; t < tabs.length; t++) {
            boolean on = t == i;
            tabs[t].setTextColor(on ? Ui.SUN : Ui.MUTED);
            tabs[t].setBackground(on ? Ui.box(this, 0x335B3DF0, 14) : null);
        }
    }

    // ------------------------------------------------------------------ Início
    private View buildHome() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        col.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 28));
        sv.addView(col, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nitix_logo);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.text(this, "Nitix", 28, Ui.TXT, true));
        names.addView(Ui.text(this, "Mais nitidez em cada pixel", 14, Ui.LILAC, false));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        np.leftMargin = Ui.dp(this, 14);
        head.addView(names, np);
        col.addView(head, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0));

        Button bp = Ui.primary(this, "Melhorar uma foto");
        bp.setOnClickListener(v -> showTab(TAB_PHOTO));
        col.addView(bp, Ui.lp(this, Ui.MATCH, Ui.dp(this, 64), 26));

        Button bv = Ui.secondary(this, "Melhorar um vídeo");
        bv.setOnClickListener(v -> showTab(TAB_VIDEO));
        col.addView(bv, Ui.lp(this, Ui.MATCH, Ui.dp(this, 60), 12));

        planText = Ui.text(this, "", 13, Ui.LILAC, true);
        col.addView(planText, Ui.lp(this, Ui.WRAP, Ui.WRAP, 16));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(this, Ui.CARD, 0x335B3DF0, 22));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        card.addView(Ui.text(this, "O que o Nitix faz", 17, Ui.TXT, true));
        String[] items = {
                "Melhoria automática de luz e cor",
                "Nitidez, brilho, contraste e cor",
                "Aumento de resolução em 2x e 4x",
                "Foto em preto e branco",
                "Vídeos em 720p ou 1080p, com o som original",
                "Tudo no seu aparelho: nada é enviado para a internet"
        };
        for (String s : items) {
            TextView t = Ui.text(this, "✓  " + s, 14, Ui.TXT, false);
            t.setPadding(0, Ui.dp(this, 9), 0, 0);
            card.addView(t);
        }
        col.addView(card, Ui.lp(this, Ui.MATCH, Ui.WRAP, 22));
        return sv;
    }

    private void refreshPlan() {
        if (planText == null) return;
        planText.setText("Seu plano: " + (Store.isVip(this) ? "VIP" : "Grátis"));
    }

    // ------------------------------------------------------------------ VIP
    private View buildVip() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        col.setPadding(pad, Ui.dp(this, 16), pad, Ui.dp(this, 28));
        sv.addView(col, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));

        col.addView(Ui.text(this, "Plano VIP", 26, Ui.TXT, true));
        col.addView(Ui.text(this, "Compare os planos e escolha o seu.", 14, Ui.MUTED, false),
                Ui.lp(this, Ui.WRAP, Ui.WRAP, 4));

        vipStatus = Ui.text(this, "", 14, Ui.LILAC, true);
        col.addView(vipStatus, Ui.lp(this, Ui.WRAP, Ui.WRAP, 12));

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setBackground(Ui.outlined(this, Ui.CARD, 0x335B3DF0, 22));
        table.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        table.addView(tableRow("", "Grátis", "VIP", true));
        table.addView(tableRow("Melhoria de fotos", "✓", "✓", false));
        table.addView(tableRow("Resolução das fotos", "até 2x", "até 4x", false));
        table.addView(tableRow("Vídeos", "até 30 s, 720p", "longos, Full HD", false));
        table.addView(tableRow("Marca d'água", "Sim", "Não", false));
        table.addView(tableRow("Anúncios", "Sim", "Não", false));
        col.addView(table, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16));

        TextView price = Ui.text(this,
                "Assinatura semanal ou mensal, cobrada pela Google Play. Os preços aparecem na loja.",
                12, Ui.MUTED, false);
        col.addView(price, Ui.lp(this, Ui.MATCH, Ui.WRAP, 14));

        Button buy = Ui.primary(this, "Assinar VIP");
        buy.setOnClickListener(v -> toast("A compra será ligada à Google Play quando o app estiver publicado."));
        col.addView(buy, Ui.lp(this, Ui.MATCH, Ui.dp(this, 60), 18));

        vipTest = Ui.secondary(this, "Ativar VIP (modo teste)");
        vipTest.setOnClickListener(v -> {
            Store.setVip(this, !Store.isVip(this));
            refreshVip();
            toast(Store.isVip(this) ? "Modo teste: VIP ativado." : "Modo teste: plano Grátis.");
        });
        col.addView(vipTest, Ui.lp(this, Ui.MATCH, Ui.dp(this, 52), 12));
        return sv;
    }

    private LinearLayout tableRow(String label, String free, String vipText, boolean header) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

        TextView l = Ui.text(this, label, 13.5f, Ui.TXT, header);
        r.addView(l, new LinearLayout.LayoutParams(0, Ui.WRAP, 1.5f));

        TextView f = Ui.text(this, free, header ? 13 : 13.5f, header ? Ui.MUTED : Ui.MUTED, header);
        f.setGravity(Gravity.CENTER);
        r.addView(f, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));

        TextView v = Ui.text(this, vipText, header ? 14 : 13.5f, header ? Ui.SUN : Ui.LILAC, true);
        v.setGravity(Gravity.CENTER);
        r.addView(v, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        return r;
    }

    private void refreshVip() {
        if (vipStatus == null) return;
        boolean isVip = Store.isVip(this);
        vipStatus.setText("Seu plano agora: " + (isVip ? "VIP" : "Grátis"));
        if (vipTest != null) vipTest.setText(isVip ? "Desativar VIP (modo teste)" : "Ativar VIP (modo teste)");
    }

    // ------------------------------------------------------------------ ligação com as telas
    void pickFile(int req, String mime) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_PHOTO) photo.onPicked(u);
        else if (req == REQ_VIDEO) video.onPicked(u);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (current > TAB_HOME) showTab(TAB_HOME);
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null) video.onHide();
    }
}
