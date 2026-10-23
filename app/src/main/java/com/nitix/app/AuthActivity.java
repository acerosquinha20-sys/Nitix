package com.nitix.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Entrar e criar conta com e-mail e senha. */
public class AuthActivity extends android.app.Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean signup;
    private boolean passVisible;

    private TextView tabIn;
    private TextView tabUp;
    private TextView error;
    private TextView showPass;
    private EditText fName;
    private EditText fEmail;
    private EditText fPass;
    private EditText fPass2;
    private Button submit;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 24);
        col.setPadding(pad, Ui.dp(this, 32), pad, Ui.dp(this, 32));
        sv.addView(col, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nitix_logo);
        col.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 92)));

        TextView title = Ui.text(this, "Nitix", 32, Ui.TXT, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, Ui.lp(this, Ui.WRAP, Ui.WRAP, 10));

        TextView sub = Ui.text(this, "Entre ou crie sua conta para continuar", 14, Ui.LILAC, false);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, Ui.lp(this, Ui.WRAP, Ui.WRAP, 4));

        // ---- abas
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackground(Ui.outlined(this, Ui.CARD, 0x335B3DF0, 16));
        tabs.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        tabIn = tab("Entrar");
        tabUp = tab("Criar conta");
        tabIn.setOnClickListener(v -> setMode(false));
        tabUp.setOnClickListener(v -> setMode(true));
        tabs.addView(tabIn, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        tabs.addView(tabUp, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        col.addView(tabs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 26));

        // ---- campos
        fName = Ui.field(this, "Seu nome (como vai aparecer)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fEmail = Ui.field(this, "E-mail",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        fPass = Ui.field(this, "Senha",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fPass2 = Ui.field(this, "Repita a senha",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(fName, Ui.lp(this, Ui.MATCH, Ui.WRAP, 14));
        col.addView(fEmail, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12));
        col.addView(fPass, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12));
        col.addView(fPass2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12));

        showPass = Ui.text(this, "Mostrar senha", 13, Ui.LILAC, true);
        showPass.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        showPass.setOnClickListener(v -> togglePassword());
        col.addView(showPass, Ui.lp(this, Ui.WRAP, Ui.WRAP, 4));
        ((LinearLayout.LayoutParams) showPass.getLayoutParams()).gravity = Gravity.START;

        error = Ui.text(this, "", 13, Ui.DANGER, true);
        error.setVisibility(View.GONE);
        col.addView(error, Ui.lp(this, Ui.MATCH, Ui.WRAP, 6));

        submit = Ui.primary(this, "Entrar");
        submit.setOnClickListener(v -> submit());
        col.addView(submit, Ui.lp(this, Ui.MATCH, Ui.dp(this, 60), 16));

        TextView note = Ui.text(this,
                "Sua conta e seus dados ficam salvos só neste aparelho. A senha é guardada de forma protegida.",
                12, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        col.addView(note, Ui.lp(this, Ui.MATCH, Ui.WRAP, 18));

        setContentView(sv);
        setMode(false);

        Ui.enter(logo, 0);
        Ui.enter(title, 80);
        Ui.enter(sub, 140);
        Ui.enter(tabs, 220);
    }

    private TextView tab(String s) {
        TextView t = Ui.text(this, s, 15, Ui.MUTED, true);
        t.setGravity(Gravity.CENTER);
        Ui.pressable(t);
        return t;
    }

    private void setMode(boolean up) {
        signup = up;
        styleTab(tabIn, !up);
        styleTab(tabUp, up);
        fName.setVisibility(up ? View.VISIBLE : View.GONE);
        fPass2.setVisibility(up ? View.VISIBLE : View.GONE);
        submit.setText(up ? "Criar conta" : "Entrar");
        error.setVisibility(View.GONE);
    }

    private void styleTab(TextView t, boolean on) {
        t.setTextColor(on ? Ui.ON_SUN : Ui.MUTED);
        t.setBackground(on ? Ui.box(this, Ui.SUN, 12) : null);
    }

    private void togglePassword() {
        passVisible = !passVisible;
        int type = InputType.TYPE_CLASS_TEXT | (passVisible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD);
        for (EditText f : new EditText[]{fPass, fPass2}) {
            f.setInputType(type);
            f.setTypeface(Typeface.DEFAULT);
            f.setSelection(f.getText().length());
        }
        showPass.setText(passVisible ? "Esconder senha" : "Mostrar senha");
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
        Ui.enter(error, 0);
    }

    private void submit() {
        Ui.hideKeyboard(this);
        final String email = fEmail.getText().toString();
        final String pass = fPass.getText().toString();
        final String name = fName.getText().toString();
        final boolean isSignup = signup;

        if (!Account.validEmail(Account.normalize(email))) {
            showError("Digite um e-mail válido.");
            return;
        }
        if (isSignup) {
            if (pass.length() < Account.MIN_PASSWORD) {
                showError("A senha precisa ter pelo menos " + Account.MIN_PASSWORD + " caracteres.");
                return;
            }
            if (!pass.equals(fPass2.getText().toString())) {
                showError("As senhas não são iguais.");
                return;
            }
        } else if (pass.isEmpty()) {
            showError("Digite sua senha.");
            return;
        }

        error.setVisibility(View.GONE);
        submit.setEnabled(false);
        submit.setText(isSignup ? "Criando conta..." : "Entrando...");
        new Thread(() -> {
            final String err = isSignup
                    ? Account.register(this, email, pass, name)
                    : Account.login(this, email, pass);
            main.post(() -> {
                if (isFinishing()) return;
                submit.setEnabled(true);
                submit.setText(signup ? "Criar conta" : "Entrar");
                if (err != null) showError(err);
                else goMain();
            });
        }).start();
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("welcome", true);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
