package com.example.robloxpianoauto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class FirebaseSync {
    public interface Callback { void done(boolean ok, String message); }

    private static final String API_KEY = "AIzaSyBMdf8-4fPWhuaMCruRYLQeslgt86QF3mY";
    private static final String PROJECT_ID = "jjsap-375da";
    private static final String[] KEYS = {"1","2","3","4","5","6","7","8","9","0","Q","E","R","T","Y","U","P"};

    public static class User {
        private final String email;
        private final String uid;
        User(String email, String uid) { this.email = email; this.uid = uid; }
        public String getEmail() { return email; }
        public String getUid() { return uid; }
    }

    public static User user() {
        // This method needs a Context to read SharedPreferences, so MainActivity calls are supported
        // through the cached fields after login in this process. For display, isLoggedIn() also checks prefs.
        return cachedUser;
    }

    private static User cachedUser;

    public static boolean isLoggedIn() {
        return cachedUser != null;
    }

    public static boolean isLoggedIn(Context ctx) {
        restoreCachedUser(ctx);
        return cachedUser != null;
    }

    public static String currentEmail(Context ctx) {
        restoreCachedUser(ctx);
        if (cachedUser != null) return cachedUser.getEmail();
        return "";
    }

    public static void restoreCachedUser(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String email = p.getString("user_email", "");
        String uid = p.getString("firebase_uid", "");
        String token = p.getString("firebase_id_token", "");
        if (!email.trim().isEmpty() && !uid.trim().isEmpty() && !token.trim().isEmpty()) cachedUser = new User(email, uid);
    }

    public static void signIn(Context ctx, String email, String password, Callback cb) {
        new Thread(() -> {
            try {
                JSONObject result = authRequest("accounts:signInWithPassword", email, password);
                finishAuth(ctx, result, email, cb, false);
            } catch (Exception e) {
                cb.done(false, friendlyError(e));
            }
        }).start();
    }

    public static void createAccount(Context ctx, String email, String password, Callback cb) {
        new Thread(() -> {
            try {
                JSONObject result = authRequest("accounts:signUp", email, password);
                finishAuth(ctx, result, email, cb, true);
            } catch (Exception e) {
                cb.done(false, friendlyError(e));
            }
        }).start();
    }

    // Mantido apenas para compatibilidade com versões antigas do código.
    // A tela nova usa Entrar e Criar conta separados para evitar criar conta por acidente
    // quando o usuário digita um e-mail errado.
    public static void loginOrCreate(Context ctx, String email, String password, Callback cb) {
        signIn(ctx, email, password, cb);
    }

    private static void finishAuth(Context ctx, JSONObject result, String typedEmail, Callback cb, boolean newAccount) throws Exception {
        String idToken = result.getString("idToken");
        String uid = result.getString("localId");
        String realEmail = result.optString("email", typedEmail);
        SharedPreferences p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        p.edit()
                .putString("user_email", realEmail)
                .putString("firebase_uid", uid)
                .putString("firebase_id_token", idToken)
                .apply();
        cachedUser = new User(realEmail, uid);
        restoreOrCreateBackup(ctx, (ok, msg) -> {
            if (ok && newAccount) cb.done(true, "Conta criada com sucesso. Backup inicial preparado.");
            else cb.done(ok, msg);
        });
    }

    public static void signOut(Context ctx) {
        cachedUser = null;
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .remove("user_email")
                .remove("firebase_uid")
                .remove("firebase_id_token")
                .apply();
    }

    public static void sendPasswordReset(String email, Callback cb) {
        new Thread(() -> {
            try {
                if (email == null || !email.contains("@") || email.trim().length() < 5) {
                    if (cb != null) cb.done(false, "Digite um e-mail válido.");
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("requestType", "PASSWORD_RESET");
                body.put("email", email.trim());
                String url = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + API_KEY;
                httpJson("POST", url, null, body);
                if (cb != null) cb.done(true, "Enviamos um e-mail de redefinição. Abra seu e-mail e toque no link para criar uma nova senha.");
            } catch (Exception e) {
                if (cb != null) cb.done(false, friendlyError(e));
            }
        }).start();
    }

    public static void restoreOrCreateBackup(Context ctx, Callback cb) {
        restoreCachedUser(ctx);
        new Thread(() -> {
            try {
                JSONObject doc = firestoreGet(ctx);
                if (doc != null) restoreFromDocument(ctx, doc, cb);
                else saveAll(ctx, (ok, msg) -> cb.done(ok, ok ? "Login feito. Backup inicial salvo." : msg));
            } catch (Exception e) {
                saveAll(ctx, (ok, msg) -> cb.done(ok, ok ? "Login feito. Backup inicial salvo." : msg));
            }
        }).start();
    }

    public static void saveAll(Context ctx, Callback cb) {
        restoreCachedUser(ctx);
        new Thread(() -> {
            try {
                SharedPreferences p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
                String token = p.getString("firebase_id_token", "");
                String uid = p.getString("firebase_uid", "");
                String email = p.getString("user_email", "");
                if (token.trim().isEmpty() || uid.trim().isEmpty()) {
                    if (cb != null) cb.done(false, "Faça login primeiro.");
                    return;
                }

                JSONObject keyPositions = new JSONObject();
                for (String k : KEYS) {
                    keyPositions.put("x_" + k, p.getInt("x_" + k, -1));
                    keyPositions.put("y_" + k, p.getInt("y_" + k, -1));
                }

                JSONArray arr;
                try { arr = new JSONArray(p.getString(MainActivity.KEY_LIBRARY, "[]")); } catch (Exception e) { arr = new JSONArray(); }
                JSONObject songs = new JSONObject();
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.getString(i);
                    JSONObject s = new JSONObject();
                    s.put("name", p.getString("songlib_name_" + id, "Música"));
                    s.put("data", p.getString("songlib_data_" + id, ""));
                    songs.put(id, s);
                }

                JSONObject fields = new JSONObject();
                fields.put("email", fieldString(email));
                fields.put("dark_mode", fieldBool(p.getBoolean("dark_mode", false)));
                fields.put("selected_song_id", fieldString(p.getString(MainActivity.KEY_SELECTED_ID, "")));
                fields.put("library_ids", fieldString(arr.toString()));
                fields.put("songs_json", fieldString(songs.toString()));
                fields.put("key_positions", fieldString(keyPositions.toString()));
                fields.put("updated_at", fieldInt(System.currentTimeMillis()));

                JSONObject body = new JSONObject();
                body.put("fields", fields);
                firestorePatch(ctx, body);
                if (cb != null) cb.done(true, "Backup salvo na nuvem.");
            } catch (Exception e) {
                if (cb != null) cb.done(false, friendlyError(e));
            }
        }).start();
    }

    public static void restoreAll(Context ctx, Callback cb) {
        restoreCachedUser(ctx);
        new Thread(() -> {
            try {
                JSONObject doc = firestoreGet(ctx);
                if (doc == null) {
                    cb.done(false, "Nenhum backup encontrado para essa conta.");
                    return;
                }
                restoreFromDocument(ctx, doc, cb);
            } catch (Exception e) {
                cb.done(false, friendlyError(e));
            }
        }).start();
    }

    private static void restoreFromDocument(Context ctx, JSONObject doc, Callback cb) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            JSONObject f = doc.getJSONObject("fields");
            SharedPreferences.Editor ed = p.edit();
            ed.putString("user_email", getStringField(f, "email", p.getString("user_email", "")));
            ed.putBoolean("dark_mode", getBoolField(f, "dark_mode", false));
            ed.putString(MainActivity.KEY_SELECTED_ID, getStringField(f, "selected_song_id", ""));

            String keyJson = getStringField(f, "key_positions", "{}");
            JSONObject keys = new JSONObject(keyJson);
            for (String k : KEYS) {
                ed.putInt("x_" + k, keys.optInt("x_" + k, -1));
                ed.putInt("y_" + k, keys.optInt("y_" + k, -1));
            }

            String library = getStringField(f, "library_ids", "[]");
            String songsText = getStringField(f, "songs_json", "{}");
            JSONArray arr = new JSONArray(library);
            JSONObject songs = new JSONObject(songsText);
            ed.putString(MainActivity.KEY_LIBRARY, arr.toString());
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.getString(i);
                JSONObject s = songs.optJSONObject(id);
                if (s != null) {
                    ed.putString("songlib_name_" + id, s.optString("name", "Música"));
                    ed.putString("songlib_data_" + id, s.optString("data", ""));
                }
            }
            ed.apply();
            cb.done(true, "Backup restaurado da nuvem.");
        } catch (Exception e) {
            cb.done(false, friendlyError(e));
        }
    }

    private static JSONObject authRequest(String method, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        body.put("returnSecureToken", true);
        String url = "https://identitytoolkit.googleapis.com/v1/" + method + "?key=" + API_KEY;
        return httpJson("POST", url, null, body);
    }

    private static JSONObject firestoreGet(Context ctx) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String uid = p.getString("firebase_uid", "");
        String token = p.getString("firebase_id_token", "");
        if (uid.trim().isEmpty() || token.trim().isEmpty()) return null;
        String url = firestoreUrl(uid);
        try { return httpJson("GET", url, token, null); }
        catch (Exception e) { return null; }
    }

    private static void firestorePatch(Context ctx, JSONObject body) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String uid = p.getString("firebase_uid", "");
        String token = p.getString("firebase_id_token", "");
        if (uid.trim().isEmpty() || token.trim().isEmpty()) throw new Exception("Faça login primeiro.");
        httpJson("PATCH", firestoreUrl(uid), token, body);
    }

    private static String firestoreUrl(String uid) throws Exception {
        String path = "users/" + URLEncoder.encode(uid, "UTF-8") + "/backup/settings";
        return "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/" + path;
    }

    private static JSONObject httpJson(String method, String urlStr, String bearerToken, JSONObject body) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(25000);
            c.setReadTimeout(35000);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setRequestProperty("Accept", "application/json");
            if (bearerToken != null && !bearerToken.trim().isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearerToken);
            if (body != null) {
                c.setDoOutput(true);
                byte[] out = body.toString().getBytes("UTF-8");
                OutputStream os = c.getOutputStream();
                os.write(out);
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String text = readText(is);
            if (code < 200 || code >= 300) throw new Exception(parseFirebaseError(text));
            if (text.trim().isEmpty()) return new JSONObject();
            return new JSONObject(text);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readText(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static JSONObject fieldString(String v) throws Exception { JSONObject o = new JSONObject(); o.put("stringValue", v == null ? "" : v); return o; }
    private static JSONObject fieldBool(boolean v) throws Exception { JSONObject o = new JSONObject(); o.put("booleanValue", v); return o; }
    private static JSONObject fieldInt(long v) throws Exception { JSONObject o = new JSONObject(); o.put("integerValue", String.valueOf(v)); return o; }

    private static String getStringField(JSONObject fields, String name, String def) {
        JSONObject o = fields.optJSONObject(name);
        return o == null ? def : o.optString("stringValue", def);
    }

    private static boolean getBoolField(JSONObject fields, String name, boolean def) {
        JSONObject o = fields.optJSONObject(name);
        return o == null ? def : o.optBoolean("booleanValue", def);
    }

    private static String parseFirebaseError(String text) {
        try {
            String msg = new JSONObject(text).getJSONObject("error").optString("message", text);
            if (msg.contains("EMAIL_NOT_FOUND") || msg.contains("INVALID_LOGIN_CREDENTIALS")) return "E-mail ou senha incorretos.";
            if (msg.contains("EMAIL_EXISTS")) return "Esse e-mail já existe. Tente entrar com a senha correta.";
            if (msg.contains("WEAK_PASSWORD")) return "Senha fraca. Use pelo menos 6 caracteres.";
            if (msg.contains("PERMISSION_DENIED")) return "Sem permissão no Firestore. Confira se o banco foi criado e as regras permitem usuários logados.";
            return msg;
        } catch (Exception e) { return text == null || text.trim().isEmpty() ? "Erro de conexão." : text; }
    }

    private static String friendlyError(Exception e) {
        String m = e == null ? "Erro desconhecido." : e.getMessage();
        if (m == null) return "Erro desconhecido.";
        if (m.contains("403") || m.contains("PERMISSION_DENIED")) return "Sem permissão no Firebase. Verifique Authentication, Firestore e regras.";
        if (m.contains("400")) return "Dados inválidos ou Firebase não configurado.";
        return m;
    }
}
