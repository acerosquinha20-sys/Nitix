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
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tela "Perfil": nome, foto, estilo da borda e estilo do nome. */
final class ProfileScreen {
    private final MainActivity act;
    final ScrollView root;

    private AvatarView bigAvatar;
    private NameView bigName;
    private TextView emailText;
    private TextView planText;
    private EditText nameField;
    private Button removePhoto;

    private final List<AvatarView> borderPreviews = new ArrayList<>();
    private final List<NameView> namePreviews = new ArrayList<>();
    private final LinearLayout[] borderCards = new LinearLayout[AvatarView.BORDERS.length];
    private final LinearLayout[] nameCards = new LinearLayout[NameView.STYLES.length];

    private Bitmap avatar;
    private int border = 1;
    private int nameStyle = 1;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    ProfileScreen(MainActivity a) {
        act = a;
        int pad = Ui.dp(a, 18);

        root = new ScrollView(a);
        root.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, Ui.dp(a, 16), pad, Ui.dp(a, 28));
        root.addView(col, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));

        col.addView(Ui.text(a, "Seu perfil", 26, Ui.TXT, true));

        // ---- cartão de prévia
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.outlined(a, Ui.CARD, 0x335B3DF0, 22));
        card.setPadding(Ui.dp(a, 16), Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 18));

        bigAvatar = new AvatarView(a);
        card.addView(bigAvatar, new LinearLayout.LayoutParams(Ui.dp(a, 140), Ui.dp(a, 140)));

        bigName = new NameView(a, 26);
        bigName.setGravity(Gravity.CENTER);
        card.addView(bigName, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        emailText = Ui.text(a, "", 13, Ui.MUTED, false);
        emailText.setGravity(Gravity.CENTER);
        card.addView(emailText, Ui.lp(a, Ui.MATCH, Ui.WRAP, 2));

        planText = Ui.text(a, "", 13, Ui.LILAC, true);
        planText.setGravity(Gravity.CENTER);
        card.addView(planText, Ui.lp(a, Ui.MATCH, Ui.WRAP, 6));
        col.addView(card, Ui.lp(a, Ui.MATCH, Ui.WRAP, 14));

        // ---- foto
        LinearLayout photoRow = new LinearLayout(a);
        Button pick = Ui.secondary(a, "Trocar foto");
        pick.setOnClickListener(v -> act.pickFile(MainActivity.REQ_AVATAR, "image/*"));
        photoRow.addView(pick, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        removePhoto = Ui.secondary(a, "Remover foto");
        removePhoto.setOnClickListener(v -> {
            Account.removeAvatar(act);
            refresh();
            act.refreshProfile();
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        rp.leftMargin = Ui.dp(a, 10);
        photoRow.addView(removePhoto, rp);
        col.addView(photoRow, Ui.lp(a, Ui.MATCH, Ui.WRAP, 12));

        // ---- nome
        col.addView(Ui.text(a, "Nome", 14, Ui.TXT, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 20));
        nameField = Ui.field(a, "Seu nome",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Account.MAX_NAME)});
        nameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                showName(e.toString().trim());
            }
        });
        col.addView(nameField, Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        // ---- estilo da borda
        col.addView(Ui.text(a, "Estilo da borda do perfil", 14, Ui.TXT, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 22));
        LinearLayout borderRow = new LinearLayout(a);
        for (int i = 0; i < AvatarView.BORDERS.length; i++) {
            final int idx = i;
            AvatarView av = new AvatarView(a);
            av.setStyle(i);
            borderPreviews.add(av);
            LinearLayout c = optionCard(av, 62, 62, AvatarView.BORDERS[i]);
            c.setOnClickListener(v -> {
                border = idx;
                Account.setBorder(act, idx);
                bigAvatar.setStyle(idx);
                markSelected();
                act.refreshProfile();
            });
            borderCards[i] = c;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(a, 92), Ui.WRAP);
            if (i > 0) cp.leftMargin = Ui.dp(a, 10);
            borderRow.addView(c, cp);
        }
        col.addView(scroller(a, borderRow), Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        // ---- estilo do nome
        col.addView(Ui.text(a, "Estilo do nome", 14, Ui.TXT, true), Ui.lp(a, Ui.WRAP, Ui.WRAP, 20));
        LinearLayout nameRow = new LinearLayout(a);
        for (int i = 0; i < NameView.STYLES.length; i++) {
            final int idx = i;
            NameView nv = new NameView(a, 16);
            nv.setStyle(i);
            nv.setGravity(Gravity.CENTER);
            namePreviews.add(nv);
            LinearLayout c = optionCard(nv, 112, 36, NameView.STYLES[i]);
            c.setOnClickListener(v -> {
                nameStyle = idx;
                Account.setNameStyle(act, idx);
                bigName.setStyle(idx);
                markSelected();
                act.refreshProfile();
            });
            nameCards[i] = c;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(a, 128), Ui.WRAP);
            if (i > 0) cp.leftMargin = Ui.dp(a, 10);
            nameRow.addView(c, cp);
        }
        col.addView(scroller(a, nameRow), Ui.lp(a, Ui.MATCH, Ui.WRAP, 8));

        // ---- salvar e sair
        Button save = Ui.primary(a, "Salvar perfil");
        save.setOnClickListener(v -> saveProfile());
        col.addView(save, Ui.lp(a, Ui.MATCH, Ui.dp(a, 60), 26));

        Button out = Ui.secondary(a, "Sair da conta");
        out.setTextColor(Ui.DANGER);
        out.setOnClickListener(v -> confirmLogout());
        col.addView(out, Ui.lp(a, Ui.MATCH, Ui.WRAP, 12));
    }

    // ------------------------------------------------------------------ montagem
    private HorizontalScrollView scroller(MainActivity a, LinearLayout row) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row, new ViewGroup.LayoutParams(Ui.WRAP, Ui.WRAP));
        return hs;
    }

    private LinearLayout optionCard(View preview, int wDp, int hDp, String label) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10));
        c.addView(preview, new LinearLayout.LayoutParams(Ui.dp(act, wDp), Ui.dp(act, hDp)));
        TextView t = Ui.text(act, label, 12, Ui.TXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, Ui.lp(act, Ui.WRAP, Ui.WRAP, 6));
        Ui.pressable(c);
        return c;
    }

    private void markSelected() {
        for (int i = 0; i < borderCards.length; i++) selectedBox(borderCards[i], i == border);
        for (int i = 0; i < nameCards.length; i++) selectedBox(nameCards[i], i == nameStyle);
    }

    private void selectedBox(LinearLayout c, boolean on) {
        GradientDrawable g = Ui.box(act, Ui.CARD, 18);
        g.setStroke(Ui.dp(act, on ? 2 : 1), on ? Ui.SUN : 0x335B3DF0);
        c.setBackground(g);
    }

    private void showName(String n) {
        String shown = n.isEmpty() ? "Seu nome" : n;
        bigName.setNameText(shown);
        for (NameView v : namePreviews) v.setNameText(shown);
    }

    // ------------------------------------------------------------------ dados
    /** Carrega do armazenamento e atualiza tudo na tela. */
    void refresh() {
        String name = Account.name(act);
        border = Account.border(act);
        nameStyle = Account.nameStyle(act);
        avatar = Account.loadAvatar(act);

        nameField.setText(name);
        nameField.setSelection(nameField.getText().length());
        showName(name);

        bigName.setStyle(nameStyle);
        bigAvatar.setStyle(border);
        bigAvatar.setPhoto(avatar);
        bigAvatar.setInitial(Account.initial(act));
        for (AvatarView v : borderPreviews) {
            v.setPhoto(avatar);
            v.setInitial(Account.initial(act));
        }
        String mail = Account.email(act);
        emailText.setText(mail == null ? "" : mail);
        planText.setText("Plano " + (Store.isVip(act) ? "VIP" : "Grátis"));
        removePhoto.setVisibility(avatar != null ? View.VISIBLE : View.GONE);
        markSelected();
    }

    private void saveProfile() {
        String n = nameField.getText().toString().trim();
        if (n.isEmpty()) {
            act.toast("Digite um nome.");
            return;
        }
        Account.setName(act, n);
        Ui.hideKeyboard(act);
        act.refreshProfile();
        act.toast("Perfil salvo.");
    }

    /** Foto escolhida na galeria: corta em quadrado e guarda no aparelho. */
    void onPicked(final Uri u) {
        bg.execute(() -> {
            try {
                Bitmap b = PhotoEngine.decode(act.getContentResolver(), u, 1024);
                int side = Math.min(b.getWidth(), b.getHeight());
                Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
                Bitmap fin = Bitmap.createScaledBitmap(sq, 512, 512, true);
                try (FileOutputStream os = new FileOutputStream(Account.avatarFile(act))) {
                    fin.compress(Bitmap.CompressFormat.JPEG, 92, os);
                }
                main.post(() -> {
                    refresh();
                    act.refreshProfile();
                });
            } catch (Throwable t) {
                main.post(() -> act.toast("Não consegui usar essa foto."));
            }
        });
    }

    private void confirmLogout() {
        new android.app.AlertDialog.Builder(act)
                .setTitle("Sair da conta?")
                .setMessage("Você poderá entrar de novo com seu e-mail e senha.")
                .setPositiveButton("Sair", (d, w) -> {
                    Account.logout(act);
                    Intent i = new Intent(act, AuthActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    act.startActivity(i);
                    act.finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
