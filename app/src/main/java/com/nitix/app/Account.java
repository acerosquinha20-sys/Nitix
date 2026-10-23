package com.nitix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Patterns;

import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Conta e perfil, guardados só neste aparelho.
 * A senha nunca é salva: guardamos apenas um hash (PBKDF2) com sal aleatório.
 * O login fica salvo até a pessoa tocar em "Sair da conta".
 */
final class Account {
    private Account() {}

    private static final int ITERATIONS = 100_000;
    static final int MIN_PASSWORD = 6;
    static final int MAX_NAME = 20;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nitix_account", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ validação
    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static boolean validEmail(String email) {
        return !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    // ------------------------------------------------------------------ sessão
    /** E-mail da conta que está logada, ou null. */
    static String email(Context c) {
        SharedPreferences p = prefs(c);
        String e = p.getString("session", null);
        return (e != null && p.contains("u." + e + ".hash")) ? e : null;
    }

    static boolean isLoggedIn(Context c) {
        return email(c) != null;
    }

    static void logout(Context c) {
        prefs(c).edit().remove("session").commit();
    }

    // ------------------------------------------------------------------ cadastro e login
    /** Cria a conta e já entra. Devolve null se deu certo, ou a mensagem de erro. */
    static String register(Context c, String rawEmail, String pass, String name) {
        String email = normalize(rawEmail);
        if (!validEmail(email)) return "Digite um e-mail válido.";
        if (pass == null || pass.length() < MIN_PASSWORD) {
            return "A senha precisa ter pelo menos " + MIN_PASSWORD + " caracteres.";
        }
        SharedPreferences p = prefs(c);
        if (p.contains("u." + email + ".hash")) {
            return "Esse e-mail já tem conta. Toque em \"Entrar\".";
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String shown = clean(name);
        if (shown.isEmpty()) shown = defaultName(email);
        p.edit()
                .putString("u." + email + ".salt", b64(salt))
                .putString("u." + email + ".hash", b64(hash(pass, salt)))
                .putString("u." + email + ".name", shown)
                .putInt("u." + email + ".border", 1)
                .putInt("u." + email + ".nameStyle", 1)
                .putString("session", email)
                .commit();
        return null;
    }

    /** Entra numa conta que já existe. Devolve null se deu certo, ou a mensagem de erro. */
    static String login(Context c, String rawEmail, String pass) {
        String email = normalize(rawEmail);
        SharedPreferences p = prefs(c);
        String salt = p.getString("u." + email + ".salt", null);
        String stored = p.getString("u." + email + ".hash", null);
        String wrong = "E-mail ou senha incorretos.";
        if (salt == null || stored == null || pass == null) return wrong;
        byte[] got = hash(pass, Base64.decode(salt, Base64.NO_WRAP));
        byte[] want = Base64.decode(stored, Base64.NO_WRAP);
        if (!MessageDigest.isEqual(got, want)) return wrong;
        p.edit().putString("session", email).commit();
        return null;
    }

    private static byte[] hash(String pass, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return h;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    // ------------------------------------------------------------------ perfil
    private static String key(Context c, String field) {
        return "u." + email(c) + "." + field;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.trim().replaceAll("\\s+", " ");
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    private static String defaultName(String email) {
        String n = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (n.isEmpty()) return "Nitix";
        return clean(Character.toUpperCase(n.charAt(0)) + n.substring(1));
    }

    static String name(Context c) {
        String e = email(c);
        if (e == null) return "Nitix";
        return prefs(c).getString(key(c, "name"), defaultName(e));
    }

    static void setName(Context c, String name) {
        String n = clean(name);
        if (n.isEmpty() || email(c) == null) return;
        prefs(c).edit().putString(key(c, "name"), n).apply();
    }

    static String initial(Context c) {
        String n = name(c);
        return n.isEmpty() ? "N" : n.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    static int border(Context c) {
        if (email(c) == null) return 1;
        return prefs(c).getInt(key(c, "border"), 1);
    }

    static void setBorder(Context c, int style) {
        if (email(c) != null) prefs(c).edit().putInt(key(c, "border"), style).apply();
    }

    static int nameStyle(Context c) {
        if (email(c) == null) return 1;
        return prefs(c).getInt(key(c, "nameStyle"), 1);
    }

    static void setNameStyle(Context c, int style) {
        if (email(c) != null) prefs(c).edit().putInt(key(c, "nameStyle"), style).apply();
    }

    // ------------------------------------------------------------------ foto do perfil
    static File avatarFile(Context c) {
        return new File(c.getFilesDir(), "avatar_" + Integer.toHexString(String.valueOf(email(c)).hashCode()) + ".jpg");
    }

    static Bitmap loadAvatar(Context c) {
        if (email(c) == null) return null;
        File f = avatarFile(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    static void removeAvatar(Context c) {
        File f = avatarFile(c);
        if (f.exists()) f.delete();
    }
}
