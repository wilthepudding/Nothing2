package com.example.robloxpianoauto;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Html;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.util.Base64;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int SCREEN_SETUP = 0;
    private static final int SCREEN_MAIN = 1;
    private static final int SCREEN_OPTIONS = 2;
    private static final int SCREEN_SEARCH = 3;
    private static final int SCREEN_BROWSER = 4;
    private int currentScreen = SCREEN_MAIN;

    public static final String PREFS = "piano_prefs";
    public static final String KEY_LIBRARY = "library_ids";
    public static final String KEY_SELECTED_ID = "selected_song_id";
    private static final int PICK_MIDI = 42;
    private static final int PICK_WALLPAPER = 43;
    private static final String KEY_WALLPAPER_URI = "wallpaper_uri";
    private static final String KEY_APPEARANCE_TRANSPARENCY = "appearance_transparency";
    private static final String KEY_TRANSPARENT_MODE = "transparent_mode";

    private TextView status;
    private LinearLayout songListBox;
    private android.app.AlertDialog searchProgressDialog;
    private android.app.AlertDialog searchResultsDialog;
    private TextView searchStatusText;
    private LinearLayout searchResultsBox;
    private boolean searchScreenDark;
    private Toast activeToast;
    private WebView onlineSequencerWebView;
    private volatile boolean browserMidiSaveInProgress = false;
    private volatile long lastBrowserMidiSavedAt = 0L;
    private volatile int lastBrowserMidiHash = 0;
    private static int globalSearchGeneration = 0;
    private int mySearchGeneration = 0;
    private final ArrayList<String> ids = new ArrayList<>();
    private final ArrayList<String> names = new ArrayList<>();
    private Typeface fancyFont;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyWindowAppearance();
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        buildScreen();
    }

    private void buildScreen() {
        applyWindowAppearance();
        if (!isAccessibilityEnabled() || !Settings.canDrawOverlays(this)) showSetupScreen();
        else showMainScreen();
    }

    private void showSetupScreen() {
        currentScreen = SCREEN_SETUP;
        LinearLayout root = baseRoot(true);
        root.setBackgroundColor(0xFFECECF6);

        root.addView(mockupTopHint());
        root.addView(mockupMainTitle("TUTORIAL"));
        root.addView(mockupTutorialCard());

        root.addView(mockupPermissionCard(
                isAccessibilityEnabled(),
                "Pendente",
                "ACESSIBILIDADE",
                isAccessibilityEnabled() ? "Tudo certo: o serviço do app já está ativo." : "Procure \"JJSAP\" e ative a acessibilidade",
                isAccessibilityEnabled() ? null : "Abrir acessibilidade",
                0xFFFF7A7A,
                0xFFFF4F52,
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        ));

        root.addView(mockupPermissionCard(
                Settings.canDrawOverlays(this),
                "Pendente",
                "SOBREPOSIÇÃO",
                Settings.canDrawOverlays(this) ? "Tudo certo: a janelinha flutuante já está liberada." : "Permita a sobreposição para usar o controle sobre o Roblox.",
                Settings.canDrawOverlays(this) ? null : "Permitir sobreposição",
                0xFFFF7A7A,
                0xFFFF4F52,
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))
        ));

        Button refresh = mockupWideButton("Verificar novamente", 0xFFFF4F52);
        refresh.setOnClickListener(v -> buildScreen());
        root.addView(refresh);

        if (isAccessibilityEnabled() && Settings.canDrawOverlays(this)) {
            Button enter = mockupWideButton("Entrar no aplicativo", 0xFF6A67FF);
            enter.setOnClickListener(v -> showMainScreen());
            root.addView(enter);
        }

        setContentView(scroll(root));
        animateChildren(root);
    }

    private Typeface getFancyFont() {
        if (fancyFont != null) return fancyFont;
        try {
            fancyFont = Typeface.createFromAsset(getAssets(), "genshin-impact-drip-font.ttf");
        } catch (Exception e) {
            fancyFont = Typeface.DEFAULT_BOLD;
        }
        return fancyFont;
    }

    private View mockupTopHint() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(22));
        row.setLayoutParams(rowLp);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.app_avatar);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarLp.setMargins(0, 0, dp(10), 0);
        avatar.setLayoutParams(avatarLp);
        row.addView(avatar);

        TextView bubble = new TextView(this);
        bubble.setText("Antes de usar, ative estas permissões.");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(13);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackground(roundRect(0xFF000000, 18, 0x00000000, 0));
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bubble.setLayoutParams(bubbleLp);
        row.addView(bubble);
        return row;
    }

    private View mockupMainTitle(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTypeface(getFancyFont());
        t.setTextColor(0xFF262737);
        t.setTextSize(34);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(18));
        t.setLayoutParams(lp);
        return t;
    }

    private View mockupTutorialCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(roundRect(Color.WHITE, 24, 0x11BFC5D8, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(18));
        box.setLayoutParams(lp);

        TextView body = new TextView(this);
        body.setText("1. Ative a acessibilidade do app\n2. Libere a janela flutuante");
        body.setTextColor(0xFF101010);
        body.setTextSize(17);
        body.setTypeface(Typeface.DEFAULT_BOLD);
        body.setLineSpacing(dp(6), 1f);
        box.addView(body);
        return box;
    }

    private View mockupPermissionCard(boolean ok, String defaultBadge, String title, String body, String buttonLabel, int accentColor, int buttonColor, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        int fill = ok ? 0xFFE5F4E5 : Color.WHITE;
        int stroke = ok ? 0xFFBFE2BF : accentColor;
        box.setBackground(roundRect(fill, 24, stroke, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(18));
        box.setLayoutParams(lp);

        TextView badge = new TextView(this);
        badge.setText(ok ? "Ativado" : defaultBadge);
        badge.setTextColor(ok ? 0xFF5AA563 : accentColor);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(badge);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xFF1D1D24);
        heading.setTextSize(21);
        heading.setTypeface(getFancyFont());
        heading.setPadding(0, dp(6), 0, dp(4));
        box.addView(heading);

        TextView desc = new TextView(this);
        desc.setText(body);
        desc.setTextColor(0xFF202020);
        desc.setTextSize(15);
        desc.setTypeface(Typeface.DEFAULT_BOLD);
        desc.setLineSpacing(dp(2), 1f);
        box.addView(desc);

        if (buttonLabel != null && !ok) {
            Button btn = mockupWideButton(buttonLabel, buttonColor);
            btn.setOnClickListener(click);
            LinearLayout.LayoutParams blp = (LinearLayout.LayoutParams) btn.getLayoutParams();
            blp.setMargins(0, dp(18), 0, 0);
            btn.setLayoutParams(blp);
            box.addView(btn);
        }
        return box;
    }

    private Button mockupWideButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        b.setBackground(roundRect(color, 20, 0x00000000, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void showMainScreen() {
        currentScreen = SCREEN_MAIN;
        applyWindowAppearance();
        cleanupOnlineSequencerWebView();
        LinearLayout root = baseRoot(false);
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        applyScreenBackground(root, dark);

        root.addView(homeHeroCard());
        root.addView(homeSectionTitle("OPÇÕES"));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.addView(homeActionButton("Importar arquivo MIDI", v -> openMidiPicker()));
        actions.addView(homeActionButton("Buscar MIDI online", v -> showOnlineSearchDialog()));
        actions.addView(homeActionButton("Abrir o controle", v -> {
            startService(new Intent(this, FloatingControlService.class));
            notifyUser("Controle aberto.");
        }));
        root.addView(actions);

        root.addView(homeSectionTitle("SUAS MÚSICAS"));
        songListBox = new LinearLayout(this);
        songListBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(songListBox);

        status = new TextView(this);
        status.setTextColor(dark ? 0xFF7A7A7A : 0xFF555555);
        status.setTextSize(14);
        status.setLineSpacing(dp(2), 1f);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(darkInfoCard(status));
        root.addView(bottomGearRow());

        setContentView(scroll(root));
        refreshLibrary();
        animateChildren(root);
    }

    private View bottomGearRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(2), 0, dp(18));
        row.setLayoutParams(rowLp);

        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(22);
        gear.setGravity(Gravity.CENTER);
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        gear.setTextColor(dark ? Color.WHITE : 0xFF151515);
        gear.setTypeface(Typeface.DEFAULT_BOLD);
        gear.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 999, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) gear.setElevation(dark ? 0 : dp(6));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(44), dp(44));
        gear.setLayoutParams(glp);
        gear.setOnClickListener(v -> showOptionsScreen());
        attachPressAnimation(gear);
        row.addView(gear);
        return row;
    }

    private boolean isValidEmail(String e) {
        if (e == null) return false;
        e = e.trim();
        return e.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private void makeRoundImage(ImageView avatar) {
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            avatar.setClipToOutline(true);
            avatar.setBackground(roundRect(Color.WHITE, 999, 0x00000000, 0));
            avatar.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        }
    }

    private void showOptionsScreen() {
        currentScreen = SCREEN_OPTIONS;
        applyWindowAppearance();
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean dark = p.getBoolean("dark_mode", false);
        String currentTab = p.getString("settings_tab", "account");
        if (!"appearance".equals(currentTab)) currentTab = "account";

        LinearLayout root = baseRoot(false);
        applyScreenBackground(root, dark);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, dp(12));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setIncludeFontPadding(false);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(dark ? Color.WHITE : 0xFF151515);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 999, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) back.setElevation(dark ? 0 : dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(46), dp(46));
        blp.setMargins(0, 0, dp(12), 0);
        back.setLayoutParams(blp);
        back.setOnClickListener(v -> showMainScreen());
        attachPressAnimation(back);
        top.addView(back);

        TextView title = new TextView(this);
        title.setText("OPÇÕES");
        title.setTypeface(getFancyFont());
        title.setTextSize(28);
        title.setTextColor(dark ? Color.WHITE : 0xFF151515);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        root.addView(top);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, 0, 0, dp(14));

        Button accountTab = new Button(this);
        accountTab.setText("Conta");
        accountTab.setAllCaps(false);
        accountTab.setTextSize(15);
        accountTab.setTypeface(Typeface.DEFAULT_BOLD);
        accountTab.setTextColor("account".equals(currentTab) ? Color.WHITE : (dark ? Color.WHITE : 0xFF151515));
        accountTab.setBackground(roundRect("account".equals(currentTab) ? 0xFF7C4DFF : (dark ? 0xFF050505 : 0xFFFFFFFF), 999, dark ? 0x44DADADA : 0x11000000, 1));
        accountTab.setOnClickListener(v -> {
            p.edit().putString("settings_tab", "account").apply();
            showOptionsScreen();
        });
        LinearLayout.LayoutParams tabLp1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        tabLp1.setMargins(0, 0, dp(8), 0);
        tabs.addView(accountTab, tabLp1);
        attachPressAnimation(accountTab);

        Button appearanceTab = new Button(this);
        appearanceTab.setText("Aparência");
        appearanceTab.setAllCaps(false);
        appearanceTab.setTextSize(15);
        appearanceTab.setTypeface(Typeface.DEFAULT_BOLD);
        appearanceTab.setTextColor("appearance".equals(currentTab) ? Color.WHITE : (dark ? Color.WHITE : 0xFF151515));
        appearanceTab.setBackground(roundRect("appearance".equals(currentTab) ? 0xFF7C4DFF : (dark ? 0xFF050505 : 0xFFFFFFFF), 999, dark ? 0x44DADADA : 0x11000000, 1));
        appearanceTab.setOnClickListener(v -> {
            p.edit().putString("settings_tab", "appearance").apply();
            showOptionsScreen();
        });
        LinearLayout.LayoutParams tabLp2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        tabLp2.setMargins(dp(8), 0, 0, 0);
        tabs.addView(appearanceTab, tabLp2);
        attachPressAnimation(appearanceTab);

        root.addView(tabs);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(18));

        if ("account".equals(currentTab)) {
            TextView accountTitle = new TextView(this);
            accountTitle.setText("CONTA");
            accountTitle.setTypeface(getFancyFont());
            accountTitle.setTextColor(dark ? Color.WHITE : 0xFF151515);
            accountTitle.setTextSize(24);
            accountTitle.setPadding(dp(4), dp(8), dp(4), dp(8));
            root.addView(accountTitle);

            LinearLayout accountCard = new LinearLayout(this);
            accountCard.setOrientation(LinearLayout.VERTICAL);
            accountCard.setPadding(dp(18), dp(16), dp(18), dp(16));
            accountCard.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 18, dark ? 0x44DADADA : 0x11000000, 1));
            if (android.os.Build.VERSION.SDK_INT >= 21) accountCard.setElevation(dark ? 0 : dp(5));
            accountCard.setLayoutParams(cardLp);

            final boolean logged = FirebaseSync.isLoggedIn(this);
            final String currentEmail = FirebaseSync.currentEmail(this);

            TextView accountInfo = new TextView(this);
            accountInfo.setText(logged ? ("Logado como: " + currentEmail) : "Entre com seu e-mail e senha.");
            accountInfo.setTextColor(dark ? 0xFFBDBDBD : 0xFF555555);
            accountInfo.setTextSize(14);
            accountInfo.setPadding(0, 0, 0, dp(10));
            accountCard.addView(accountInfo);

            if (logged) {
                TextView secureInfo = new TextView(this);
                secureInfo.setText("Conta conectada. Você pode salvar backup, restaurar ou sair quando quiser.");
                secureInfo.setTextColor(dark ? 0xFFBDBDBD : 0xFF555555);
                secureInfo.setTextSize(13);
                secureInfo.setLineSpacing(dp(2), 1f);
                secureInfo.setPadding(0, 0, 0, dp(12));
                accountCard.addView(secureInfo);
            }

            if (!logged) {
                EditText email = new EditText(this);
                email.setSingleLine(true);
                email.setHint("Seu e-mail");
                email.setText(currentEmail == null ? "" : currentEmail);
                email.setTextColor(dark ? Color.WHITE : 0xFF151515);
                email.setHintTextColor(dark ? 0xFF777777 : 0xFF777777);
                email.setBackground(roundRect(dark ? 0xFF151515 : 0xFFFFFFFF, 12, dark ? 0x33DADADA : 0x11000000, 1));
                email.setPadding(dp(12), dp(10), dp(12), dp(10));
                accountCard.addView(email);

                EditText password = new EditText(this);
                password.setSingleLine(true);
                password.setHint("Senha");
                password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                password.setTextColor(dark ? Color.WHITE : 0xFF151515);
                password.setHintTextColor(dark ? 0xFF777777 : 0xFF777777);
                password.setBackground(roundRect(dark ? 0xFF151515 : 0xFFFFFFFF, 12, dark ? 0x33DADADA : 0x11000000, 1));
                password.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                passLp.setMargins(0, dp(10), 0, 0);
                password.setLayoutParams(passLp);
                accountCard.addView(password);

                Space loginGap = new Space(this);
                accountCard.addView(loginGap, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));

                Button enterBtn = homeActionButton("Entrar", v -> {
                    String e = email.getText().toString().trim();
                    String pass = password.getText().toString();
                    if (!isValidEmail(e)) {
                        notifyUser("Digite um e-mail válido.");
                        return;
                    }
                    if (pass.length() < 6) {
                        notifyUser("Digite a senha com pelo menos 6 caracteres.");
                        return;
                    }
                    notifyUser("Entrando...");
                    FirebaseSync.signIn(this, e, pass, (ok, msg) -> runOnUiThread(() -> {
                        notifyUserLong(msg);
                        refreshLibrary();
                        showOptionsScreen();
                    }));
                });
                accountCard.addView(enterBtn);

                Button createBtn = homeActionButton("Criar conta", v -> {
                    String e = email.getText().toString().trim();
                    String pass = password.getText().toString();
                    if (!isValidEmail(e)) {
                        notifyUser("Digite um e-mail válido.");
                        return;
                    }
                    if (pass.length() < 6) {
                        notifyUser("A senha precisa ter pelo menos 6 caracteres.");
                        return;
                    }
                    notifyUser("Criando conta...");
                    FirebaseSync.createAccount(this, e, pass, (ok, msg) -> runOnUiThread(() -> {
                        notifyUserLong(msg);
                        refreshLibrary();
                        showOptionsScreen();
                    }));
                });
                accountCard.addView(createBtn);

                Button resetBtn = homeActionButton("Redefinir senha por e-mail", v -> {
                    String e = email.getText().toString().trim();
                    if (!isValidEmail(e)) {
                        notifyUser("Digite o e-mail da conta primeiro.");
                        return;
                    }
                    notifyUser("Enviando e-mail...");
                    FirebaseSync.sendPasswordReset(e, (ok, msg) -> runOnUiThread(() -> notifyUserLong(msg)));
                });
                accountCard.addView(resetBtn);
            } else {
                Button syncBtn = homeActionButton("Sincronizar backup", v -> {
                    notifyUser("Salvando backup...");
                    FirebaseSync.saveAll(this, (ok, msg) -> runOnUiThread(() -> notifyUserLong(msg)));
                });
                accountCard.addView(syncBtn);

                Button restoreBtn = homeActionButton("Restaurar backup da nuvem", v -> {
                    notifyUser("Restaurando backup...");
                    FirebaseSync.restoreAll(this, (ok, msg) -> runOnUiThread(() -> {
                        notifyUserLong(msg);
                        refreshLibrary();
                        showMainScreen();
                    }));
                });
                accountCard.addView(restoreBtn);

                Button resetBtn = homeActionButton("Redefinir senha por e-mail", v -> {
                    notifyUser("Enviando e-mail...");
                    FirebaseSync.sendPasswordReset(currentEmail, (ok, msg) -> runOnUiThread(() -> {
                        notifyUserLong(msg);
                        if (ok) {
                            FirebaseSync.signOut(this);
                            showOptionsScreen();
                        }
                    }));
                });
                accountCard.addView(resetBtn);

                Button logoutBtn = homeActionButton("Sair da conta", v -> {
                    FirebaseSync.signOut(this);
                    notifyUser("Você saiu da conta.");
                    showOptionsScreen();
                });
                accountCard.addView(logoutBtn);
            }
            root.addView(accountCard);
        } else {
            TextView appearanceTitle = new TextView(this);
            appearanceTitle.setText("APARÊNCIA");
            appearanceTitle.setTypeface(getFancyFont());
            appearanceTitle.setTextColor(dark ? Color.WHITE : 0xFF151515);
            appearanceTitle.setTextSize(24);
            appearanceTitle.setPadding(dp(4), dp(8), dp(4), dp(8));
            root.addView(appearanceTitle);

            LinearLayout appearanceCard = new LinearLayout(this);
            appearanceCard.setOrientation(LinearLayout.VERTICAL);
            appearanceCard.setPadding(dp(18), dp(16), dp(18), dp(16));
            appearanceCard.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 18, dark ? 0x44DADADA : 0x11000000, 1));
            if (android.os.Build.VERSION.SDK_INT >= 21) appearanceCard.setElevation(dark ? 0 : dp(5));
            appearanceCard.setLayoutParams(cardLp);

            CheckBox darkMode = new CheckBox(this);
            darkMode.setText("Modo escuro");
            darkMode.setTextColor(dark ? Color.WHITE : 0xFF151515);
            darkMode.setTextSize(16);
            darkMode.setTypeface(Typeface.DEFAULT_BOLD);
            darkMode.setChecked(dark);
            darkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                p.edit().putBoolean("dark_mode", isChecked).apply();
                notifyUser(isChecked ? "Modo escuro ativado. Use Sincronizar backup para salvar isso na nuvem." : "Modo claro ativado. Use Sincronizar backup para salvar isso na nuvem.");
                showOptionsScreen();
            });
            appearanceCard.addView(darkMode);

            TextView wallInfo = new TextView(this);
            wallInfo.setText("Escolha uma foto para deixar o app com a sua cara. Ajuste abaixo o quanto ela aparece no fundo.");
            wallInfo.setTextColor(dark ? 0xFFBDBDBD : 0xFF555555);
            wallInfo.setTextSize(13);
            wallInfo.setLineSpacing(dp(2), 1f);
            wallInfo.setPadding(0, dp(10), 0, dp(10));
            appearanceCard.addView(wallInfo);

            Button chooseWallpaper = homeActionButton("Escolher foto de fundo", v -> openWallpaperPicker());
            appearanceCard.addView(chooseWallpaper);

            Button removeWallpaper = homeActionButton("Remover foto de fundo", v -> {
                p.edit().remove(KEY_WALLPAPER_URI).apply();
                notifyUser("Foto de fundo removida.");
                showOptionsScreen();
            });
            appearanceCard.addView(removeWallpaper);

            TextView transparencyLabel = new TextView(this);
            int currentTransparency = p.getInt(KEY_APPEARANCE_TRANSPARENCY, 45);
            transparencyLabel.setText("Transparência do fundo: " + currentTransparency + "%");
            transparencyLabel.setTextColor(dark ? Color.WHITE : 0xFF151515);
            transparencyLabel.setTextSize(15);
            transparencyLabel.setPadding(0, dp(12), 0, 0);
            appearanceCard.addView(transparencyLabel);

            SeekBar transparencySeek = new SeekBar(this);
            transparencySeek.setMax(100);
            transparencySeek.setProgress(currentTransparency);
            transparencySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    transparencyLabel.setText("Transparência do fundo: " + progress + "%");
                    if (fromUser) p.edit().putInt(KEY_APPEARANCE_TRANSPARENCY, progress).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    p.edit().putInt(KEY_APPEARANCE_TRANSPARENCY, seekBar.getProgress()).apply();
                    showOptionsScreen();
                }
            });
            appearanceCard.addView(transparencySeek);

            root.addView(appearanceCard);
        }

        setContentView(scroll(root));
        animateChildren(root);
    }

    private View homeHeroCard() {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(205));
        flp.setMargins(0, 0, 0, dp(18));
        frame.setLayoutParams(flp);
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        // No modo escuro, evita as bordas/cantos brancos ao redor da imagem do topo.
        frame.setBackground(roundRect(dark ? 0xFF050505 : Color.WHITE, 24, 0x00000000, 0));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            frame.setClipToOutline(true);
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
                }
            });
            frame.setElevation(dp(2));
        }

        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.main_header_bg);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(bg, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));
        frame.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.app_avatar);
        makeRoundImage(avatar);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(76), dp(76));
        alp.setMargins(0, 0, 0, dp(10));
        avatar.setLayoutParams(alp);
        content.addView(avatar);

        TextView title = new TextView(this);
        title.setText("JJSAP");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Um app simples para tocar boas músicas no piano do Jujutsu Shenanigans.");
        sub.setTextColor(0xE6FFFFFF);
        sub.setTextSize(11);
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(false);
        sub.setMaxLines(4);
        sub.setEllipsize(null);
        sub.setPadding(dp(12), dp(6), dp(12), 0);
        sub.setLineSpacing(dp(2), 1f);
        content.addView(sub, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return frame;
    }

    private View homeSectionTitle(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTypeface(getFancyFont());
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        t.setTextColor(dark ? 0xFFFFFFFF : 0xFF151515);
        t.setTextSize(27);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(12));
        t.setLayoutParams(lp);
        return t;
    }

    private void attachPressAnimation(View view) {
        view.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.86f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                    break;
            }
            return false;
        });
    }

    private Button homeActionButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(14), dp(18), dp(14), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        if (dark) {
            b.setTextColor(Color.WHITE);
            b.setBackground(roundRect(0xFF050505, 18, 0x44DADADA, 1));
            if (android.os.Build.VERSION.SDK_INT >= 21) b.setElevation(0);
        } else {
            b.setTextColor(0xFF151515);
            b.setBackground(roundRect(0xFFFFFFFF, 18, 0x11000000, 1));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                b.setElevation(dp(5));
                b.setTranslationZ(dp(2));
            }
        }
        attachPressAnimation(b);
        return b;
    }

    private View darkInfoCard(View child) {
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(8));
        box.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 18, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) box.setElevation(dark ? 0 : dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        box.setLayoutParams(lp);
        box.addView(child);
        return box;
    }

    private void showSettingsDialog() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        TextView loginInfo = text("Login e backup ainda estão em teste. Em breve você poderá salvar suas músicas na nuvem.");
        box.addView(loginInfo);

        EditText email = new EditText(this);
        email.setSingleLine(true);
        email.setHint("Seu e-mail");
        email.setText(p.getString("user_email", ""));
        box.addView(email);

        CheckBox darkMode = new CheckBox(this);
        darkMode.setText("Modo escuro");
        darkMode.setTextColor(0xFF202020);
        darkMode.setTextSize(15);
        darkMode.setChecked(p.getBoolean("dark_mode", false));
        box.addView(darkMode);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Opções")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    p.edit()
                            .putString("user_email", email.getText().toString().trim())
                            .putBoolean("dark_mode", darkMode.isChecked())
                            .apply();
                    notifyUser("Opções salvas.");
                    showMainScreen();
                })
                .show();
    }

    private LinearLayout baseRoot(boolean onboarding) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                onboarding ? new int[]{0xFFF6F1FF, 0xFFEFF4FF, 0xFFFFFFFF} : new int[]{0xFFF2F4FF, 0xFFF8F9FF, 0xFFFFFFFF}
        );
        root.setBackground(bg);
        return root;
    }

    private View scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(Color.TRANSPARENT);
        s.addView(v);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String wallpaperUri = p.getString(KEY_WALLPAPER_URI, "");
        if (wallpaperUri == null || wallpaperUri.trim().isEmpty()) return s;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.TRANSPARENT);

        if (wallpaperUri != null && !wallpaperUri.trim().isEmpty()) {
            ImageView bg = new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { bg.setImageURI(Uri.parse(wallpaperUri)); } catch (Exception ignored) {}
            frame.addView(bg, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        boolean dark = p.getBoolean("dark_mode", false);
        int visibility = p.getInt(KEY_APPEARANCE_TRANSPARENCY, 45);
        if (visibility < 0) visibility = 0;
        if (visibility > 100) visibility = 100;
        int alpha;
        alpha = dark ? Math.max(70, 230 - visibility) : Math.max(80, 245 - visibility);
        TextView overlay = new TextView(this);
        overlay.setBackgroundColor((alpha << 24) | (dark ? 0x000000 : 0xFFFFFF));
        frame.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(s, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return frame;
    }

    private void applyScreenBackground(LinearLayout root, boolean dark) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String wallpaperUri = p.getString(KEY_WALLPAPER_URI, "");
        if (wallpaperUri != null && !wallpaperUri.trim().isEmpty()) {
            root.setBackgroundColor(Color.TRANSPARENT);
        } else {
            root.setBackgroundColor(dark ? 0xFF111111 : 0xFFF4F4F4);
        }
    }

    private void applyWindowAppearance() {
        try {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        } catch (Exception ignored) {}
    }

    private View heroHeader(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF5B67FF, 0xFF8F6BFF, 0xFF59B4FF}
        );
        bg.setCornerRadius(dp(26));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(lp);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.app_avatar);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(88), dp(88));
        avatarLp.gravity = Gravity.CENTER_HORIZONTAL;
        avatarLp.setMargins(0, 0, 0, dp(12));
        avatar.setLayoutParams(avatarLp);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(avatar);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(25);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(14);
        s.setTextColor(0xFFEAF0FF);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setPadding(0, dp(8), 0, 0);
        card.addView(s);
        return card;
    }

    private TextView sectionTitle(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(19);
        t.setTextColor(0xFF1F2440);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(4), dp(4), dp(8));
        return t;
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(0xFF31374C);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private View modernCard(View child, boolean muted) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(muted ? 0xFFF7F8FF : Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), muted ? 0xFFDDE4FF : 0xFFE7EAFF);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(lp);
        box.addView(child);
        return box;
    }

    private View stepCard(String heading, String body, String btn, View.OnClickListener click, boolean ok, int accentColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ok ? 0xFFEAF9F0 : Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), ok ? 0xFFBEE6CB : 0xFFE7EAFF);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(lp);

        TextView badge = new TextView(this);
        badge.setText(ok ? "✓ Ativado" : "• Pendente");
        badge.setTextColor(ok ? 0xFF1E8E4A : accentColor);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextSize(12);
        box.addView(badge);

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(18);
        h.setTextColor(0xFF1F2440);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(6), 0, dp(6));
        box.addView(h);

        TextView b = text(body);
        box.addView(b);

        if (!ok) {
            Button action = primaryButton(btn, accentColor);
            action.setOnClickListener(click);
            box.addView(action);
        }
        return box;
    }

    private Button primaryButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(18));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        b.setPadding(dp(12), dp(13), dp(12), dp(13));
        return b;
    }

    private Button primaryButtonWithAction(String label, int color, View.OnClickListener action) {
        Button b = primaryButton(label, color);
        b.setOnClickListener(action);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void animateChildren(LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(18));
            child.setScaleX(0.985f);
            child.setScaleY(0.985f);
            child.animate()
                    .alpha(1f)
                    .translationY(0)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(i * 32L)
                    .setDuration(240)
                    .start();
        }
    }

    private void notifyUser(String msg) {
        try { if (activeToast != null) activeToast.cancel(); } catch (Exception ignored) {}
        activeToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        activeToast.show();
    }

    private void notifyUserLong(String msg) {
        try { if (activeToast != null) activeToast.cancel(); } catch (Exception ignored) {}
        activeToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        activeToast.show();
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                List<AccessibilityServiceInfo> enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                String target = getPackageName() + "/" + PianoAccessibilityService.class.getName();
                for (AccessibilityServiceInfo info : enabled) {
                    String id = info.getId();
                    if (id != null && (id.equals(target) || id.contains(getPackageName()))) return true;
                }
            }
        } catch (Exception ignored) {}
        try {
            String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices == null) return false;
            ComponentName cn = new ComponentName(this, PianoAccessibilityService.class);
            return enabledServices.toLowerCase().contains(cn.flattenToString().toLowerCase());
        } catch (Exception e) { return PianoAccessibilityService.get() != null; }
    }

    private void openWallpaperPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_WALLPAPER);
    }

    private void openMidiPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/midi", "audio/x-midi", "audio/mid", "application/octet-stream"});
        startActivityForResult(i, PICK_MIDI);
    }

    private void refreshLibrary() {
        ids.clear(); names.clear();
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String selected = p.getString(KEY_SELECTED_ID, "");
        boolean dark = p.getBoolean("dark_mode", false);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY_LIBRARY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.getString(i);
                String name = p.getString("songlib_name_" + id, "MIDI " + (i + 1));
                if (!p.getString("songlib_data_" + id, "").trim().isEmpty()) { ids.add(id); names.add(name); }
            }
        } catch (Exception ignored) {}

        if (songListBox != null) {
            songListBox.removeAllViews();
            if (ids.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("Suas músicas vão aparecer aqui");
                empty.setTextColor(dark ? 0xFF797979 : 0xFF555555);
                empty.setTextSize(14);
                empty.setPadding(dp(14), dp(12), dp(14), dp(12));
                songListBox.addView(darkInfoCard(empty));
            } else {
                for (int i = 0; i < ids.size(); i++) {
                    final String id = ids.get(i);
                    final String name = names.get(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(dp(16), dp(14), dp(16), dp(14));
                    row.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 18, dark ? 0x44DADADA : 0x11000000, 1));
                    if (android.os.Build.VERSION.SDK_INT >= 21) row.setElevation(dark ? 0 : dp(5));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 0, 0, dp(12));
                    row.setLayoutParams(lp);

                    TextView nameView = new TextView(this);
                    nameView.setText("♪ " + name);
                    nameView.setTextColor(dark ? Color.WHITE : 0xFF151515);
                    nameView.setTypeface(Typeface.DEFAULT_BOLD);
                    nameView.setTextSize(15);
                    row.addView(nameView);

                    TextView hint = new TextView(this);
                    hint.setText("Use pelo botão flutuante • segure para excluir");
                    hint.setTextColor(dark ? 0xFF7A7A7A : 0xFF666666);
                    hint.setTextSize(12);
                    hint.setPadding(0, dp(4), 0, 0);
                    row.addView(hint);

                    row.setOnClickListener(v -> notifyUser("Escolha a música pelo botão flutuante."));
                    row.setOnLongClickListener(v -> { deleteSong(id); return true; });
                    row.setAlpha(0f);
                    row.setTranslationY(dp(12));
                    row.animate().alpha(1f).translationY(0).setStartDelay(i * 35L).setDuration(220).start();
                    songListBox.addView(row);
                }
            }
        }
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String selected = p.getString(KEY_SELECTED_ID, "");
        String name = selected.isEmpty() ? "" : p.getString("songlib_name_" + selected, "");
        String data = selected.isEmpty() ? "" : p.getString("songlib_data_" + selected, "");
        if (ids.isEmpty()) status.setText("Suas músicas aparecem aqui. Para tocar, escolha pelo botão flutuante.");
        else if (selected.isEmpty() || data.trim().isEmpty()) status.setText("Biblioteca: " + ids.size() + " música(s). Escolha uma pelo botão flutuante.");
        else status.setText("Biblioteca: " + ids.size() + " música(s). No botão flutuante: " + name + "\n" + data.split("\\n").length + " notas prontas.");
    }

    private void selectSong(String id, String name) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SELECTED_ID, id).apply();
        if (FirebaseSync.isLoggedIn()) FirebaseSync.saveAll(this, (ok, msg) -> {});
        notifyUser("Selecionada: " + name);
        refreshLibrary();
    }

    private void deleteSong(String id) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (String existing : ids) if (!existing.equals(id)) arr.put(existing);
        SharedPreferences.Editor ed = p.edit();
        ed.putString(KEY_LIBRARY, arr.toString());
        ed.remove("songlib_name_" + id);
        ed.remove("songlib_data_" + id);
        if (id.equals(p.getString(KEY_SELECTED_ID, ""))) ed.remove(KEY_SELECTED_ID);
        ed.apply();
        if (FirebaseSync.isLoggedIn()) FirebaseSync.saveAll(this, (ok, msg) -> {});
        notifyUser("Música excluída.");
        refreshLibrary();
    }

    private void saveMidiToLibrary(String name, String songText) throws Exception {
        if (songText == null || songText.trim().isEmpty()) throw new Exception("MIDI sem eventos para salvar");
        name = cleanMidiFileName(name);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray arr;
        try { arr = new JSONArray(p.getString(KEY_LIBRARY, "[]")); } catch (Exception e) { arr = new JSONArray(); }

        // Evita duplicar quando o WebView dispara o DownloadListener e o JavaScript ao mesmo tempo,
        // ou quando o mesmo MIDI é importado/baixado novamente.
        for (int i = 0; i < arr.length(); i++) {
            String existingId = arr.optString(i, "");
            if (existingId.trim().isEmpty()) continue;
            String existingData = p.getString("songlib_data_" + existingId, "");
            if (songText.equals(existingData)) {
                p.edit()
                        .putString("songlib_name_" + existingId, name)
                        .putString(KEY_SELECTED_ID, existingId)
                        .apply();
                if (FirebaseSync.isLoggedIn()) FirebaseSync.saveAll(this, (ok, msg) -> {});
                return;
            }
        }

        String id = String.valueOf(System.currentTimeMillis());
        arr.put(id);
        p.edit().putString(KEY_LIBRARY, arr.toString()).putString("songlib_name_" + id, name).putString("songlib_data_" + id, songText).putString(KEY_SELECTED_ID, id).apply();
        if (FirebaseSync.isLoggedIn()) FirebaseSync.saveAll(this, (ok, msg) -> {});
    }

    private String cleanMidiFileName(String name) {
        if (name == null) name = "Online Sequencer MIDI";
        name = name.trim();
        try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
        name = name.replace("Online Sequencer - ", "").replace("- Online Sequencer", "");
        name = name.replace("Download MIDI", "").replace("Export MIDI", "");
        name = name.replaceAll("[\\\\/:*?\"<>|]+", " ");
        name = name.replaceAll("\\s+", " ").trim();
        if (name.toLowerCase().endsWith(".mid")) name = name.substring(0, name.length() - 4).trim();
        if (name.toLowerCase().endsWith(".midi")) name = name.substring(0, name.length() - 5).trim();
        if (name.isEmpty() || name.toLowerCase().startsWith("pesquisar no online sequencer")) name = "Online Sequencer MIDI";
        return name;
    }

    private String filenameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null) return "";
        try {
            Matcher m = Pattern.compile("filename\\*?=(?:UTF-8''|\\\")?([^\\\";]+)", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
            if (m.find()) return cleanMidiFileName(m.group(1));
        } catch (Exception ignored) {}
        return "";
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private String getDisplayName(Uri uri) {
        String result = "musica.mid";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static class OnlineMidiResult {
        String title;
        String pageUrl;
        String directMidiUrl;
        String source;
        OnlineMidiResult(String title, String pageUrl, String directMidiUrl, String source) {
            this.title = title;
            this.pageUrl = pageUrl;
            this.directMidiUrl = directMidiUrl;
            this.source = source;
        }
    }

    private void showOnlineSearchDialog() {
        showSearchScreen();
    }

    private void showSearchScreen() {
        currentScreen = SCREEN_SEARCH;
        applyWindowAppearance();
        cleanupOnlineSequencerWebView();
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        applyScreenBackground(root, dark);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(26);
        back.setIncludeFontPadding(false);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(dark ? Color.WHITE : 0xFF151515);
        back.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 999, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) back.setElevation(dark ? 0 : dp(5));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        backLp.setMargins(0, 0, dp(14), 0);
        back.setLayoutParams(backLp);
        back.setOnClickListener(v -> showMainScreen());
        attachPressAnimation(back);
        top.addView(back);

        TextView title = new TextView(this);
        title.setText("BUSCAR MIDI");
        title.setTypeface(getFancyFont());
        title.setTextSize(25);
        title.setTextColor(dark ? Color.WHITE : 0xFF151515);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        root.addView(top);

        TextView subtitle = new TextView(this);
        subtitle.setText("Busque músicas MIDI no Online Sequencer.");
        subtitle.setTextColor(dark ? 0xFFBDBDBD : 0xFF555555);
        subtitle.setTextSize(14);
        subtitle.setPadding(dp(4), dp(16), dp(4), dp(12));
        root.addView(subtitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 20, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dark ? 0 : dp(6));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(8), 0, dp(16));
        card.setLayoutParams(cardLp);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Ex: undertale, deltarune, megalovania");
        input.setTextColor(dark ? Color.WHITE : 0xFF151515);
        input.setHintTextColor(dark ? 0xFF777777 : 0xFF777777);
        input.setTextSize(15);
        input.setBackground(roundRect(dark ? 0xFF151515 : 0xFFFFFFFF, 14, dark ? 0x44DADADA : 0x16000000, 1));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(0, 0, 0, dp(14));
        input.setLayoutParams(ilp);
        card.addView(input);

        Button search = themedSearchButton("Buscar agora", dark);
        search.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) notifyUser("Digite o nome da música primeiro.");
            else searchOnlineMidi(q);
        });
        card.addView(search);

        TextView help = new TextView(this);
        help.setText("Dica: nomes em inglês costumam encontrar mais músicas.");
        help.setTextColor(dark ? 0xFF9A9A9A : 0xFF666666);
        help.setTextSize(12);
        help.setPadding(0, dp(14), 0, 0);
        card.addView(help);

        root.addView(card);

        searchStatusText = new TextView(this);
        searchStatusText.setText("Os resultados aparecem aqui.");
        searchStatusText.setTextColor(dark ? 0xFF888888 : 0xFF666666);
        searchStatusText.setTextSize(13);
        searchStatusText.setPadding(dp(4), dp(6), dp(4), dp(10));
        root.addView(searchStatusText);

        searchResultsBox = new LinearLayout(this);
        searchResultsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(searchResultsBox);
        searchScreenDark = dark;

        setContentView(scroll(root));
        animateChildren(root);
    }

    private Button themedSearchButton(String label, boolean dark) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(15);
        b.setTextColor(dark ? Color.WHITE : 0xFF151515);
        b.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 16, dark ? 0x55DADADA : 0x18000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setElevation(dark ? 0 : dp(5));
        b.setPadding(dp(14), dp(15), dp(14), dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button miniDialogButton(String label, boolean dark, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(13);
        b.setTextColor(dark ? Color.WHITE : 0xFF151515);
        int fill = primary ? (dark ? 0xFF151515 : 0xFFFFFFFF) : (dark ? 0xFF050505 : 0xFFFFFFFF);
        int stroke = dark ? 0x55DADADA : 0x18000000;
        b.setBackground(roundRect(fill, 16, stroke, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setElevation(dark ? 0 : dp(4));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(46));
        lp.setMargins(dp(8), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void searchOnlineMidi(String query) {
        final int generation;
        synchronized (MainActivity.class) {
            globalSearchGeneration++;
            generation = globalSearchGeneration;
        }
        mySearchGeneration = generation;

        try { if (searchProgressDialog != null) searchProgressDialog.dismiss(); } catch (Exception ignored) {}
        try { if (searchResultsDialog != null) searchResultsDialog.dismiss(); } catch (Exception ignored) {}

        if (searchResultsBox != null) {
            searchResultsBox.removeAllViews();
            searchResultsBox.addView(inlineLoadingRow("Buscando “" + query + "”..."));
        }
        if (searchStatusText != null) {
            searchStatusText.setText("Abrindo o Online Sequencer...");
        }

        new Thread(() -> {
            try {
                ArrayList<OnlineMidiResult> results = fetchOnlineMidiResults(query);
                runOnUiThread(() -> {
                    if (!isActivityAlive() || generation != globalSearchGeneration || generation != mySearchGeneration) return;
                    showSearchResults(query, results);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isActivityAlive() || generation != globalSearchGeneration || generation != mySearchGeneration) return;
                    if (searchResultsBox != null) searchResultsBox.removeAllViews();
                    if (searchStatusText != null) searchStatusText.setText("Não consegui abrir a busca: " + e.getMessage());
                    notifyUserLong("Não consegui abrir a busca: " + e.getMessage());
                });
            }
        }).start();
    }

    private View inlineLoadingRow(String labelText) {
        boolean dark = searchScreenDark;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 18, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) row.setElevation(dark ? 0 : dp(4));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rlp);

        ProgressBar bar = new ProgressBar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(34), dp(34));
        blp.setMargins(0, 0, dp(12), 0);
        row.addView(bar, blp);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(dark ? 0xFFEFEFEF : 0xFF222222);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View inlineSearchResultRow(OnlineMidiResult r) {
        boolean dark = searchScreenDark;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(roundRect(dark ? 0xFF050505 : 0xFFFFFFFF, 18, dark ? 0x44DADADA : 0x11000000, 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) row.setElevation(dark ? 0 : dp(4));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rlp);

        TextView title = new TextView(this);
        title.setText(r.title);
        title.setTextColor(dark ? Color.WHITE : 0xFF151515);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        row.addView(title);

        TextView source = new TextView(this);
        source.setText("Fonte: " + r.source);
        source.setTextColor(dark ? 0xFF999999 : 0xFF666666);
        source.setTextSize(11);
        source.setPadding(0, dp(4), 0, dp(10));
        row.addView(source);

        String buttonLabel = isOnlineSequencerUrl(r.pageUrl) ? "Abrir no Online Sequencer" : "Baixar e salvar";
        Button download = themedSearchButton(buttonLabel, dark);
        download.setTextSize(13);
        download.setOnClickListener(v -> confirmDownload(r));
        row.addView(download);
        return row;
    }

    private ArrayList<OnlineMidiResult> fetchOnlineMidiResults(String query) {
        ArrayList<OnlineMidiResult> out = new ArrayList<>();

        // v10: pesquisa rápida e confiável.
        // O Online Sequencer usa Cloudflare e gera MIDI como blob dentro do navegador.
        // Fazer scraping com HttpURLConnection deixa a tela lenta e instável. Então,
        // para qualquer texto digitado, mostramos imediatamente a busca oficial do
        // Online Sequencer no modo navegador, onde o download já funciona.
        try { addOnlineSequencerSearchEntry(query, out); } catch (Exception ignored) {}

        // Mantém alguns atalhos do próprio Online Sequencer para buscas populares,
        // sem depender de rede e sem misturar BitMidi/VGMusic no topo.
        try { addCuratedOnlineSequencerFallbacks(query, out); } catch (Exception ignored) {}

        return limitResults(out, 20);
    }

    private void fetchOnlineSequencerResultsInto(String query, ArrayList<OnlineMidiResult> out) throws Exception {
        ArrayList<String> urls = new ArrayList<>();
        String q = URLEncoder.encode(query, "UTF-8");
        addVariant(urls, "https://onlinesequencer.net/sequences?search=" + q);
        addVariant(urls, "https://onlinesequencer.net/sequences?search=" + q + "&sort=3&type=3");

        for (String url : urls) {
            String html = httpGetText(url);
            parseOnlineSequencerHtml(html, query, out);
            if (out.size() >= 30) return;
        }
    }

    private void fetchOnlineSequencerWebResultsInto(String query, ArrayList<OnlineMidiResult> out) throws Exception {
        ArrayList<String> searches = new ArrayList<>();
        addVariant(searches, query + " site:onlinesequencer.net");
        addVariant(searches, query + " site:onlinesequencer.net \"Download MIDI\"");
        addVariant(searches, "\"" + query + "\" \"Online Sequencer\"");
        addVariant(searches, query + " onlinesequencer.net midi");

        for (String search : searches) {
            fetchOnlineSequencerLinksFromSearchUrl("https://duckduckgo.com/html/?q=" + URLEncoder.encode(search, "UTF-8"), out);
            if (out.size() >= 35) return;
            fetchOnlineSequencerLinksFromSearchUrl("https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(search, "UTF-8"), out);
            if (out.size() >= 35) return;
            fetchOnlineSequencerLinksFromSearchUrl("https://www.bing.com/search?q=" + URLEncoder.encode(search, "UTF-8"), out);
            if (out.size() >= 35) return;
        }
    }

    private void fetchOnlineSequencerLinksFromSearchUrl(String url, ArrayList<OnlineMidiResult> out) throws Exception {
        String html = httpGetText(url);
        Pattern linkPattern = Pattern.compile("href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = linkPattern.matcher(html);
        int checked = 0;
        while (m.find() && checked < 90 && out.size() < 45) {
            String href = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString();
            String decoded = unwrapSearchUrl(href);
            if (decoded == null) continue;
            OnlineMidiResult r = buildOnlineSequencerResult(decoded, cleanSearchTitle(m.group(2)));
            if (r == null) continue;
            checked++;
            addOnlineResult(out, r);
        }

        Pattern raw = Pattern.compile("https?://(?:www\\.)?onlinesequencer\\.net/(\\d{3,})", Pattern.CASE_INSENSITIVE);
        Matcher rm = raw.matcher(html);
        while (rm.find() && out.size() < 45) {
            addOnlineResult(out, onlineSequencerResult(rm.group(1), "Online Sequencer #" + rm.group(1)));
        }
    }

    private void parseOnlineSequencerHtml(String html, String originalQuery, ArrayList<OnlineMidiResult> out) {
        if (html == null) return;
        Pattern a = Pattern.compile("<a[^>]+href=\"(?:https?://(?:www\\.)?onlinesequencer\\.net)?/(\\d{3,})(?:[^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        int checked = 0;
        while (m.find() && checked < 80 && out.size() < 35) {
            String id = m.group(1);
            String title = cleanHtml(m.group(2));
            if (!isUsefulOnlineSequencerTitle(title)) title = "Online Sequencer #" + id;
            OnlineMidiResult r = onlineSequencerResult(id, title);
            addOnlineResult(out, r);
            checked++;
        }

        // Fallback: se o HTML vier sem textos fáceis, ainda captura IDs puros.
        Pattern idPattern = Pattern.compile("(?:href=\"|https?://(?:www\\.)?onlinesequencer\\.net/)(\\d{3,})", Pattern.CASE_INSENSITIVE);
        Matcher im = idPattern.matcher(html);
        while (im.find() && out.size() < 35) {
            String id = im.group(1);
            addOnlineResult(out, onlineSequencerResult(id, "Online Sequencer #" + id));
        }
    }

    private boolean isUsefulOnlineSequencerTitle(String title) {
        if (title == null) return false;
        String t = title.trim().toLowerCase();
        if (t.length() < 2) return false;
        if (t.equals("image") || t.equals("ok") || t.equals("cancel")) return false;
        if (t.contains("online sequencer make music")) return false;
        if (t.equals("sequences") || t.equals("members") || t.equals("forum") || t.equals("wiki")) return false;
        return true;
    }

    private OnlineMidiResult buildOnlineSequencerResult(String url, String title) {
        if (url == null) return null;
        Pattern p = Pattern.compile("onlinesequencer\\.net/(\\d{3,})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);
        if (!m.find()) return null;
        String id = m.group(1);
        if (!isUsefulOnlineSequencerTitle(title)) title = "Online Sequencer #" + id;
        return onlineSequencerResult(id, title);
    }

    private OnlineMidiResult onlineSequencerResult(String id, String title) {
        String page = "https://onlinesequencer.net/" + id;
        String midi = "https://onlinesequencer.net/app/midi.php?id=" + id;
        return new OnlineMidiResult(title, page, midi, "Online Sequencer");
    }

    private void addOnlineSequencerSearchEntry(String query, ArrayList<OnlineMidiResult> out) throws Exception {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return;
        String page = "https://onlinesequencer.net/sequences?search=" + URLEncoder.encode(q, "UTF-8");
        addOnlineResult(out, new OnlineMidiResult("Pesquisar no Online Sequencer: " + q, page, null, "Online Sequencer busca"));
    }

    private void addCuratedOnlineSequencerFallbacks(String query, ArrayList<OnlineMidiResult> out) {
        String norm = normalizeSearch(query);
        if (norm.contains("deltarune") || norm.contains("delta run") || norm.contains("delta rune") || norm.contains("rude buster") || norm.contains("field of hopes") || norm.contains("toby fox")) {
            addOnlineResult(out, onlineSequencerResult("987303", "Rude Buster - Deltarune by:Luiz"));
            addOnlineResult(out, onlineSequencerResult("2596406", "Deltarune - Field of Hopes and Dreams"));
            addOnlineResult(out, onlineSequencerResult("3270895", "Deltarune - Rude Buster - MIDI"));
            addOnlineResult(out, onlineSequencerResult("969307", "Deltarune - Field of Hopes and Dreams"));
            addOnlineResult(out, onlineSequencerResult("2393187", "DELTARUNE - Lost Girl (Chapter 2)"));
        }
        if (norm.contains("amazing digital circus") || norm.contains("digital circus") || norm.contains("tadc") || norm.contains("gooseworx") || norm.contains("your new home")) {
            addOnlineResult(out, onlineSequencerResult("3699949", "The Amazing Digital Circus Theme MIDI"));
            addOnlineResult(out, onlineSequencerResult("3666348", "Your New Home - The Amazing Digital Circus - Gooseworx"));
            addOnlineResult(out, onlineSequencerResult("3655477", "The AMAZING Digital Circus - Main Theme - Gooseworx"));
            addOnlineResult(out, onlineSequencerResult("3660907", "The Amazing Digital Circus End Theme"));
            addOnlineResult(out, onlineSequencerResult("3838408", "Your New Home - The Amazing Digital Circus Official Soundtrack"));
        }
        if (norm.contains("undertale") || norm.contains("undertail") || norm.contains("megalovania") || norm.contains("fallen down") || norm.contains("sans")) {
            // Mantém a busca útil também para Undertale, que é muito pesquisado junto com Deltarune.
            addOnlineResult(out, onlineSequencerResult("969307", "Deltarune - Field of Hopes and Dreams"));
        }
    }

    private ArrayList<String> buildQueryVariants(String query) {
        ArrayList<String> vars = new ArrayList<>();
        String lower = query == null ? "" : query.trim().toLowerCase();
        lower = lower.replace("undertail", "undertale");
        lower = lower.replace("under tail", "undertale");
        lower = lower.replace("delta run", "deltarune");
        lower = lower.replace("delta rune", "deltarune");
        lower = lower.replace("deltarun", "deltarune");
        addVariant(vars, lower);
        addVariant(vars, simplifyOnlineSequencerQuery(lower));
        addVariant(vars, lower + " midi");
        if (lower.contains("undertale") || lower.contains("toby fox")) {
            addVariant(vars, "undertale midi");
            addVariant(vars, "megalovania midi");
            addVariant(vars, "fallen down undertale midi");
            addVariant(vars, "toby fox midi");
        }
        if (lower.contains("deltarune") || lower.contains("toby fox")) {
            addVariant(vars, "deltarune midi");
            addVariant(vars, "rude buster midi");
            addVariant(vars, "field of hopes and dreams midi");
        }
        if (lower.contains("digital circus") || lower.contains("amazing digital circus") || lower.equals("tadc")) {
            addVariant(vars, "the amazing digital circus midi");
            addVariant(vars, "amazing digital circus ost midi");
            addVariant(vars, "tadc midi");
            addVariant(vars, "your new home digital circus midi");
        }
        return vars;
    }

    private String simplifyOnlineSequencerQuery(String query) {
        if (query == null) return "";
        String q = query.toLowerCase();
        q = q.replace("official", "").replace("ost", "").replace("soundtrack", "");
        q = q.replace("piano", "").replace("midi", "").replace("song", "").replace("music", "");
        q = q.replaceAll("\\s+", " ").trim();
        if (q.length() > 12) q = q.substring(0, 12).trim();
        return q;
    }

    private void addVariant(ArrayList<String> vars, String v) {
        if (v == null) return;
        v = v.trim();
        if (v.isEmpty()) return;
        for (String x : vars) if (x.equalsIgnoreCase(v)) return;
        vars.add(v);
    }

    private boolean isUndertaleQuery(String norm) {
        return norm.contains("undertale") || norm.contains("undertail") || norm.contains("megalovania") || norm.contains("sans") || norm.contains("fallen down") || norm.contains("bonetrousle") || norm.contains("hopes and dreams") || norm.contains("death by glamour");
    }

    private void addDeltaruneFallbacks(String norm, ArrayList<OnlineMidiResult> out) {
        boolean wantsDeltarune = norm.contains("deltarune") || norm.contains("delta run") || norm.contains("delta rune") || norm.contains("rude buster") || norm.contains("kris") || norm.contains("susie") || norm.contains("lancer");
        if (!wantsDeltarune) return;
        addOnlineResult(out, new OnlineMidiResult("DELTARUNE - Chapter 1 - Rude Buster.mid", "https://www.vgmusic.com/file/4edeca32ba8688bc52289de791b6e18d.html", "https://www.vgmusic.com/new-files/DELTARUNE_-_Chapter_1_-_Rude_Buster_-_ShinkoNetCavy.mid", "VGMusic direto"));
    }

    private void fetchDirectoryMidiResults(String baseUrl, String source, String originalQuery, ArrayList<OnlineMidiResult> out) {
        try {
            String html = httpGetText(baseUrl);
            Pattern a = Pattern.compile("href=\"([^\"]+\\.mid)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = a.matcher(html);
            String normQuery = normalizeSearch(originalQuery);
            boolean generalGameQuery = normQuery.equals("undertale") || normQuery.equals("undertail") || normQuery.equals("under tail");
            while (m.find() && out.size() < 60) {
                String href = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString();
                String title = cleanHtml(m.group(2));
                if (title.trim().isEmpty()) title = href.substring(href.lastIndexOf('/') + 1);
                try { title = URLDecoder.decode(title, "UTF-8"); } catch (Exception ignored) {}
                if (!generalGameQuery && !roughMatch(normQuery, normalizeSearch(title))) continue;
                String u = href.startsWith("http") ? href : baseUrl + href;
                addOnlineResult(out, new OnlineMidiResult(title, u, u, source));
            }
        } catch (Exception ignored) {}
    }

    private boolean roughMatch(String query, String title) {
        String q = query.replace("undertale", "").replace("undertail", "").replace("midi", "").trim();
        if (q.isEmpty()) return true;
        if (title.contains(q)) return true;
        String[] parts = q.split("\\s+");
        int hits = 0;
        for (String part : parts) {
            if (part.length() < 3) continue;
            if (title.contains(part)) hits++;
        }
        return hits > 0;
    }

    private void fetchBitMidiResultsInto(String query, ArrayList<OnlineMidiResult> out) throws Exception {
        String url = "https://bitmidi.com/search?q=" + URLEncoder.encode(query, "UTF-8");
        String html = httpGetText(url);
        Pattern a = Pattern.compile("<a[^>]+href=\"(/(?!search|random|about|privacy|uploads)[^\"]+-mid)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        while (m.find() && out.size() < 60) {
            String page = "https://bitmidi.com" + m.group(1);
            String title = cleanHtml(m.group(2));
            if (!title.toLowerCase().endsWith(".mid")) continue;
            addOnlineResult(out, new OnlineMidiResult(title, page, null, "BitMidi"));
        }
    }

    private void addPopularWorkHints(String norm, ArrayList<OnlineMidiResult> out) {
        // Não inventa arquivo. Só adiciona pesquisas web melhores para obras em que o usuário provavelmente pesquisa pelo nome da obra.
        // Os downloads reais ainda são validados como MIDI antes de salvar.
    }

    private void fetchWebMidiSearchInto(String query, ArrayList<OnlineMidiResult> out) throws Exception {
        ArrayList<String> searches = new ArrayList<>();
        addVariant(searches, query + " midi download");
        addVariant(searches, query + " site:vgmusic.com");
        addVariant(searches, query + " site:bitmidi.com");
        addVariant(searches, query + " \".mid\"");

        for (String s : searches) {
            fetchDuckDuckGoResultsInto(s, out);
            if (out.size() >= 55) return;
        }
    }

    private void fetchDuckDuckGoResultsInto(String query, ArrayList<OnlineMidiResult> out) throws Exception {
        String url = "https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
        String html = httpGetText(url);

        Pattern linkPattern = Pattern.compile("href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = linkPattern.matcher(html);
        int checked = 0;
        while (m.find() && checked < 45 && out.size() < 60) {
            String href = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString();
            String title = cleanHtml(m.group(2));
            String decoded = unwrapSearchUrl(href);
            if (decoded == null || decoded.trim().isEmpty()) continue;
            if (!looksMidiRelated(decoded, title)) continue;
            checked++;

            String cleanTitle = title;
            if (cleanTitle.trim().isEmpty()) cleanTitle = fileNameFromUrl(decoded);
            if (cleanTitle.trim().isEmpty()) cleanTitle = decoded;

            if (decoded.toLowerCase().contains(".mid")) {
                addOnlineResult(out, new OnlineMidiResult(cleanTitle, decoded, decoded, "Busca web"));
            } else {
                addOnlineResult(out, new OnlineMidiResult(cleanTitle, decoded, null, sourceNameForUrl(decoded)));
            }
        }
    }

    private String unwrapSearchUrl(String href) {
        try {
            if (href == null) return null;
            href = href.trim();
            if (href.startsWith("//")) href = "https:" + href;
            if (href.startsWith("/l/?") || href.contains("duckduckgo.com/l/?")) {
                int idx = href.indexOf("uddg=");
                if (idx >= 0) {
                    String v = href.substring(idx + 5);
                    int amp = v.indexOf('&');
                    if (amp >= 0) v = v.substring(0, amp);
                    return URLDecoder.decode(v, "UTF-8");
                }
            }
            if (href.startsWith("/url?") || href.contains("bing.com/ck/a") || href.contains("google.com/url?")) {
                String[] keys = new String[] {"url=", "u=", "q="};
                for (String key : keys) {
                    int idx = href.indexOf(key);
                    if (idx >= 0) {
                        String v = href.substring(idx + key.length());
                        int amp = v.indexOf('&');
                        if (amp >= 0) v = v.substring(0, amp);
                        v = URLDecoder.decode(v, "UTF-8");
                        if (v.startsWith("http://") || v.startsWith("https://")) return v;
                    }
                }
            }
            int direct = href.indexOf("https://onlinesequencer.net/");
            if (direct < 0) direct = href.indexOf("http://onlinesequencer.net/");
            if (direct >= 0) return href.substring(direct);
            if (href.startsWith("http://") || href.startsWith("https://")) return href;
        } catch (Exception ignored) {}
        return null;
    }

    private boolean looksMidiRelated(String url, String title) {
        String u = url == null ? "" : url.toLowerCase();
        String t = title == null ? "" : title.toLowerCase();
        if (u.contains(".mid") || u.contains(".midi")) return true;
        if (u.contains("onlinesequencer.net/")) return true;
        if (u.contains("bitmidi.com") || u.contains("vgmusic.com")) return true;
        if (u.contains("midi") || t.contains("midi") || t.contains(".mid")) return true;
        return false;
    }

    private String sourceNameForUrl(String url) {
        String u = url == null ? "" : url.toLowerCase();
        if (u.contains("onlinesequencer.net")) return "Online Sequencer via web";
        if (u.contains("vgmusic.com")) return "VGMusic via web";
        if (u.contains("bitmidi.com")) return "BitMidi via web";
        if (u.contains("musescore.com")) return "MuseScore via web";
        return "Busca web";
    }

    private String fileNameFromUrl(String url) {
        try {
            String u = url;
            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            int slash = u.lastIndexOf('/');
            if (slash >= 0) u = u.substring(slash + 1);
            return URLDecoder.decode(u, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    private void addOnlineResult(ArrayList<OnlineMidiResult> out, OnlineMidiResult item) {
        if (item == null || item.title == null) return;
        String key = (item.directMidiUrl != null ? item.directMidiUrl : item.pageUrl);
        for (OnlineMidiResult r : out) {
            String rk = (r.directMidiUrl != null ? r.directMidiUrl : r.pageUrl);
            if (rk.equalsIgnoreCase(key) || r.title.equalsIgnoreCase(item.title)) return;
        }
        out.add(item);
    }

    private ArrayList<OnlineMidiResult> limitResults(ArrayList<OnlineMidiResult> in, int max) {
        ArrayList<OnlineMidiResult> out = new ArrayList<>();

        // Prioridade 1: músicas específicas do Online Sequencer.
        for (OnlineMidiResult r : in) {
            if (r == null) continue;
            if (isOnlineSequencerUrl(r.pageUrl) && !isOnlineSequencerSearchResult(r)) {
                out.add(r);
                if (out.size() >= max) return out;
            }
        }

        // Prioridade 2: entrada genérica para abrir a busca real do site no navegador.
        for (OnlineMidiResult r : in) {
            if (r == null) continue;
            if (isOnlineSequencerSearchResult(r)) {
                out.add(r);
                if (out.size() >= max) return out;
            }
        }

        // Prioridade 3: BitMidi, VGMusic e outros resultados diretos.
        for (OnlineMidiResult r : in) {
            if (r == null) continue;
            if (isOnlineSequencerUrl(r.pageUrl)) continue;
            out.add(r);
            if (out.size() >= max) break;
        }
        return out;
    }

    private String normalizeSearch(String s) {
        if (s == null) return "";
        s = s.toLowerCase();
        s = s.replace("_", " ").replace("-", " ").replace(".", " ");
        s = s.replaceAll("[^a-z0-9áàâãéêíóôõúüç ]", " ");
        s = s.replace("undertail", "undertale").replace("under tail", "undertale");
        s = s.replace("delta run", "deltarune").replace("delta rune", "deltarune").replace("deltarun", "deltarune");
        return s.replaceAll("\\s+", " ").trim();
    }

    private void showSearchResults(String query, ArrayList<OnlineMidiResult> results) {
        if (searchResultsBox != null) searchResultsBox.removeAllViews();

        if (results.isEmpty()) {
            if (searchStatusText != null) searchStatusText.setText("Não encontrei nada para “" + query + "”. Tente um nome mais curto ou em inglês.");
            return;
        }

        if (searchStatusText != null) {
            searchStatusText.setText("Pronto. Abra o Online Sequencer, escolha a música e toque em Download MIDI.");
        }

        if (searchResultsBox != null) {
            for (OnlineMidiResult r : results) {
                searchResultsBox.addView(inlineSearchResultRow(r));
            }
        }
    }

    private void confirmDownload(OnlineMidiResult r) {
        // Online Sequencer é o fluxo principal do app. Ao tocar em Abrir,
        // entra direto no navegador interno sem janela extra de confirmação.
        if (isOnlineSequencerSearchResult(r) || isOnlineSequencerUrl(r.pageUrl) || isOnlineSequencerUrl(r.directMidiUrl)) {
            openOnlineSequencerDownloader(r);
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(r.title)
                .setMessage("Fonte: " + r.source)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Baixar direto", (d, w) -> downloadOnlineMidi(r))
                .show();
    }

    private void openOnlineSequencerDownloader(OnlineMidiResult r) {
        currentScreen = SCREEN_BROWSER;
        String page = r.pageUrl;
        if (page == null || page.trim().isEmpty()) {
            String id = onlineSequencerIdFromUrl(r.directMidiUrl);
            if (id != null) page = "https://onlinesequencer.net/" + id;
        }
        if (page == null || page.trim().isEmpty()) {
            notifyUserLong("Não encontrei a página dessa música.");
            return;
        }

        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark_mode", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111111 : 0xFFFFFFFF);

        TextView info = new TextView(this);
        info.setText("Online Sequencer\nEscolha a música e toque em Download MIDI. O app salva para você.");
        info.setTextColor(dark ? Color.WHITE : 0xFF151515);
        info.setTextSize(13);
        info.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.addView(info);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(8), 0, dp(8), dp(8));

        Button back = miniDialogButton("Voltar", dark, false);
        back.setOnClickListener(v -> { cleanupOnlineSequencerWebView(); showSearchScreen(); });
        buttons.addView(back);

        root.addView(buttons);

        onlineSequencerWebView = new WebView(this);
        WebSettings st = onlineSequencerWebView.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setDatabaseEnabled(true);
        // Segurança: o navegador interno só precisa de web HTTPS do Online Sequencer.
        // Desativar acesso a arquivos/conteúdo locais reduz risco se alguma página tentar abusar do WebView.
        try { st.setAllowFileAccess(false); } catch (Exception ignored) {}
        try { st.setAllowContentAccess(false); } catch (Exception ignored) {}
        try { st.setAllowFileAccessFromFileURLs(false); } catch (Exception ignored) {}
        try { st.setAllowUniversalAccessFromFileURLs(false); } catch (Exception ignored) {}
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try { st.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); } catch (Exception ignored) {}
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try { st.setSafeBrowsingEnabled(true); } catch (Exception ignored) {}
        }
        st.setSaveFormData(false);
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);
        st.setSupportZoom(true);
        st.setBuiltInZoomControls(true);
        st.setDisplayZoomControls(false);
        st.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(onlineSequencerWebView, false);

        // O Online Sequencer gera o arquivo MIDI como uma URL interna do tipo blob:.
        // Android não consegue baixar blob: com HttpURLConnection. Por isso criamos
        // uma ponte JS para converter o blob em Base64 dentro do próprio WebView.
        onlineSequencerWebView.addJavascriptInterface(new OnlineSequencerBlobBridge(r), "AndroidMidiBridge");

        onlineSequencerWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return true;
                String u = request.getUrl().toString();
                if (isAllowedOnlineSequencerMainUrl(u)) return false;
                notifyUserLong("Por segurança, o navegador interno só abre o Online Sequencer.");
                return true;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isAllowedOnlineSequencerMainUrl(url)) return false;
                notifyUserLong("Por segurança, o navegador interno só abre o Online Sequencer.");
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
                if (isOnlineSequencerUrl(url)) injectOnlineSequencerBlobPatch(view);
            }
        });

        onlineSequencerWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            notifyUser("Salvando MIDI...");
            if (url != null && url.toLowerCase().startsWith("blob:")) {
                captureBlobMidiFromWebView(url);
            } else {
                downloadOnlineSequencerFromWebView(r, url, userAgent, mimeType, contentDisposition);
            }
        });

        root.addView(onlineSequencerWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        onlineSequencerWebView.loadUrl(page);
    }

    private boolean isAllowedOnlineSequencerMainUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        if (u.startsWith("about:blank")) return true;
        if (u.startsWith("blob:")) return true;
        if (!u.startsWith("https://")) return false;
        try {
            Uri parsed = Uri.parse(u);
            String host = parsed.getHost();
            if (host == null) return false;
            return host.equals("onlinesequencer.net") || host.equals("www.onlinesequencer.net");
        } catch (Exception e) { return false; }
    }

    private void cleanupOnlineSequencerWebView() {
        WebView w = onlineSequencerWebView;
        onlineSequencerWebView = null;
        if (w == null) return;
        try {
            w.evaluateJavascript("(function(){try{var a=document.querySelectorAll('audio,video');for(var i=0;i<a.length;i++){try{a[i].pause();a[i].src='';a[i].load();}catch(e){}}}catch(e){}try{if(window.AudioContext&&window.__rpaAudioCtx){window.__rpaAudioCtx.close();}}catch(e){}try{if(window.stop)window.stop();}catch(e){}})();", null);
        } catch (Exception ignored) {}
        try { w.stopLoading(); } catch (Exception ignored) {}
        try { w.loadUrl("about:blank"); } catch (Exception ignored) {}
        try { w.onPause(); } catch (Exception ignored) {}
        try { w.pauseTimers(); } catch (Exception ignored) {}
        try { w.removeJavascriptInterface("AndroidMidiBridge"); } catch (Exception ignored) {}
        try { w.removeAllViews(); } catch (Exception ignored) {}
        try { w.destroy(); } catch (Exception ignored) {}
    }

    private void injectOnlineSequencerBlobPatch(WebView view) {
        if (view == null) return;
        String js = "(function(){" +
                "if(window.__rpaMidiPatchInstalled)return;" +
                "window.__rpaMidiPatchInstalled=true;" +
                "window.__rpaBlobStore=window.__rpaBlobStore||{};" +
                "function saveBlob(u,b){try{window.__rpaBlobStore[u]=b;}catch(e){}}" +
                "function patchURL(obj){try{if(!obj||obj.__rpaPatched||!obj.createObjectURL)return;" +
                "var old=obj.createObjectURL.bind(obj);" +
                "obj.createObjectURL=function(blob){var u=old(blob);saveBlob(u,blob);return u;};" +
                "obj.__rpaPatched=true;}catch(e){}}" +
                "patchURL(window.URL);patchURL(window.webkitURL);" +
                "window.__rpaReadBlobUrl=function(u,n){try{" +
                "var b=(window.__rpaBlobStore||{})[u];" +
                "if(!b){AndroidMidiBridge.error('Blob expirou antes de salvar. Toque em Download MIDI novamente.');return;}" +
                "var reader=new FileReader();" +
                "reader.onloadend=function(){AndroidMidiBridge.saveMidiWithName(reader.result,n||document.title||'Online Sequencer MIDI');};" +
                "reader.onerror=function(){AndroidMidiBridge.error('Falha ao ler o blob MIDI');};" +
                "reader.readAsDataURL(b);" +
                "}catch(e){AndroidMidiBridge.error(String(e));}};" +
                "document.addEventListener('click',function(ev){try{" +
                "var el=ev.target;var a=null;" +
                "while(el&&el.tagName){if(String(el.tagName).toLowerCase()==='a'){a=el;break;}el=el.parentElement;}" +
                "if(!a)return;" +
                "var txt=(a.textContent||'').toLowerCase();" +
                "if(txt.indexOf('download midi')>=0||String(a.href||'').indexOf('blob:')===0){" +
                "setTimeout(function(){try{var h=String(a.href||'');var n=String(a.download||a.getAttribute('download')||document.title||'Online Sequencer MIDI');window.__rpaLastDownloadName=n;if(h.indexOf('blob:')===0&&window.__rpaReadBlobUrl)window.__rpaReadBlobUrl(h,n);}catch(e){}},250);" +
                "}" +
                "}catch(e){}},true);" +
                "})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private void captureBlobMidiFromWebView(String blobUrl) {
        if (onlineSequencerWebView == null) {
            notifyUserLong("WebView não está aberto.");
            return;
        }
        String safeUrl = blobUrl.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "");
        String js = "(function(){" +
                "var u='" + safeUrl + "';" +
                "if(window.__rpaReadBlobUrl){window.__rpaReadBlobUrl(u,window.__rpaLastDownloadName||document.title||'Online Sequencer MIDI');return;}" +
                "fetch(u).then(function(r){return r.blob();}).then(function(b){" +
                "var reader=new FileReader();" +
                "reader.onloadend=function(){AndroidMidiBridge.saveMidiWithName(reader.result,window.__rpaLastDownloadName||document.title||'Online Sequencer MIDI');};" +
                "reader.onerror=function(){AndroidMidiBridge.error('Falha ao ler blob MIDI');};" +
                "reader.readAsDataURL(b);" +
                "}).catch(function(e){AndroidMidiBridge.error(String(e));});" +
                "})();";
        try {
            injectOnlineSequencerBlobPatch(onlineSequencerWebView);
            onlineSequencerWebView.evaluateJavascript(js, null);
        } catch (Exception e) {
            notifyUserLong("Erro ao capturar blob: " + e.getMessage());
        }
    }

    private class OnlineSequencerBlobBridge {
        private final String title;

        OnlineSequencerBlobBridge(OnlineMidiResult r) {
            this.title = r != null && r.title != null ? r.title : "Online Sequencer MIDI";
        }

        @JavascriptInterface
        public void saveMidi(String dataUrl) {
            saveMidiWithName(dataUrl, title);
        }

        @JavascriptInterface
        public void saveMidiWithName(String dataUrl, String fileName) {
            new Thread(() -> {
                try {
                    if (dataUrl == null || !dataUrl.startsWith("data:")) {
                        throw new Exception("download capturado não veio em formato data URL");
                    }
                    int comma = dataUrl.indexOf(',');
                    if (comma < 0) throw new Exception("data URL inválida");
                    String meta = dataUrl.substring(0, comma).toLowerCase();
                    String payload = dataUrl.substring(comma + 1);
                    byte[] bytes;
                    if (meta.contains(";base64")) {
                        bytes = Base64.decode(payload, Base64.DEFAULT);
                    } else {
                        bytes = URLDecoder.decode(payload, "UTF-8").getBytes("ISO-8859-1");
                    }
                    if (!MidiSongParser.isMidi(bytes)) {
                        throw new Exception("o arquivo capturado não parece ser MIDI válido");
                    }

                    int midiHash = java.util.Arrays.hashCode(bytes);
                    long now = System.currentTimeMillis();
                    synchronized (MainActivity.this) {
                        if (browserMidiSaveInProgress) return;
                        if (midiHash == lastBrowserMidiHash && now - lastBrowserMidiSavedAt < 5000) return;
                        browserMidiSaveInProgress = true;
                    }

                    String songText = MidiSongParser.convertToSongText(bytes);
                    String saveName = cleanMidiFileName(fileName);
                    if (saveName.equals("Online Sequencer MIDI")) saveName = cleanMidiFileName(title);
                    saveMidiToLibrary(saveName, songText);
                    final String shownName = saveName;
                    synchronized (MainActivity.this) {
                        lastBrowserMidiHash = midiHash;
                        lastBrowserMidiSavedAt = System.currentTimeMillis();
                        browserMidiSaveInProgress = false;
                    }
                    runOnUiThread(() -> {
                        notifyUserLong("MIDI salvo pelo modo navegador: " + shownName);
                        refreshLibrary();
                        showMainScreen();
                    });
                } catch (Exception e) {
                    synchronized (MainActivity.this) { browserMidiSaveInProgress = false; }
                    runOnUiThread(() -> notifyUserLong("Erro ao salvar blob MIDI: " + e.getMessage()));
                }
            }).start();
        }

        @JavascriptInterface
        public void error(String message) {
            runOnUiThread(() -> notifyUserLong("Erro ao capturar blob: " + message));
        }
    }

    private void downloadOnlineSequencerFromWebView(OnlineMidiResult r, String url, String userAgent, String mimeType, String contentDisposition) {
        new Thread(() -> {
            try {
                String referer = r.pageUrl;
                if (referer == null || referer.trim().isEmpty()) {
                    String id = onlineSequencerIdFromUrl(url);
                    if (id != null) referer = "https://onlinesequencer.net/" + id;
                }
                String cookies = null;
                try { cookies = CookieManager.getInstance().getCookie(url); } catch (Exception ignored) {}
                if ((cookies == null || cookies.trim().isEmpty()) && referer != null) {
                    try { cookies = CookieManager.getInstance().getCookie(referer); } catch (Exception ignored) {}
                }
                byte[] bytes = httpGetBytesWithBrowserHeaders(
                        url,
                        1024 * 1024 * 8,
                        referer,
                        "audio/midi,audio/x-midi,audio/mid,application/octet-stream,*/*",
                        cookies,
                        userAgent
                );
                if (!MidiSongParser.isMidi(bytes)) throw new Exception("o download capturado não era MIDI válido");
                String songText = MidiSongParser.convertToSongText(bytes);
                String saveName = filenameFromContentDisposition(contentDisposition);
                if (saveName.trim().isEmpty()) saveName = r.title;
                saveMidiToLibrary(saveName, songText);
                final String shownName = cleanMidiFileName(saveName);
                runOnUiThread(() -> {
                    notifyUserLong("MIDI salvo pelo modo navegador: " + shownName);
                    refreshLibrary();
                    showMainScreen();
                });
            } catch (Exception e) {
                runOnUiThread(() -> notifyUserLong("Erro no modo navegador: " + e.getMessage()));
            }
        }).start();
    }

    private void downloadOnlineMidi(OnlineMidiResult r) {
        notifyUser("Baixando MIDI...");
        new Thread(() -> {
            try {
                byte[] bytes;
                String saveTitle = r.title;
                try {
                    bytes = downloadBytesForOnlineResult(r);
                } catch (Exception firstError) {
                    // O Online Sequencer frequentemente bloqueia app/midi.php fora do navegador (HTTP 403).
                    // Quando isso acontecer, procura automaticamente um .mid direto equivalente em fontes públicas.
                    if (isOnlineSequencerUrl(r.pageUrl) || isOnlineSequencerUrl(r.directMidiUrl)) {
                        runOnUiThread(() -> notifyUser("O Online Sequencer bloqueou esse download."));
                        DownloadedMidi alt = tryDownloadAlternativeMidi(r);
                        bytes = alt.bytes;
                        saveTitle = alt.result.title;
                    } else {
                        throw firstError;
                    }
                }

                if (!MidiSongParser.isMidi(bytes)) throw new Exception("esse resultado não liberou um arquivo MIDI válido");
                String songText = MidiSongParser.convertToSongText(bytes);
                saveMidiToLibrary(saveTitle, songText);
                String finalTitle = saveTitle;
                runOnUiThread(() -> {
                    notifyUserLong("Baixado e selecionado: " + finalTitle);
                    refreshLibrary();
                });
            } catch (Exception e) {
                runOnUiThread(() -> notifyUserLong("Erro ao baixar: " + e.getMessage()));
            }
        }).start();
    }

    private byte[] downloadBytesForOnlineResult(OnlineMidiResult r) throws Exception {
        String downloadUrl = r.directMidiUrl;
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            String pageHtml = httpGetText(r.pageUrl);
            downloadUrl = findMidiDownloadUrl(pageHtml, r.pageUrl);
        }
        if (downloadUrl == null) throw new Exception("link de download não encontrado");

        byte[] bytes;
        if (isOnlineSequencerUrl(downloadUrl) || isOnlineSequencerUrl(r.pageUrl)) {
            bytes = downloadOnlineSequencerMidiBytes(r.pageUrl, downloadUrl);
        } else {
            bytes = httpGetBytes(downloadUrl, 1024 * 1024 * 6);
        }

        if (!MidiSongParser.isMidi(bytes) && r.pageUrl != null && !r.pageUrl.equals(downloadUrl)) {
            try {
                String pageHtml = httpGetText(r.pageUrl);
                String fallback = findMidiDownloadUrl(pageHtml, r.pageUrl);
                if (fallback != null && !fallback.equals(downloadUrl)) {
                    if (isOnlineSequencerUrl(fallback) || isOnlineSequencerUrl(r.pageUrl)) bytes = downloadOnlineSequencerMidiBytes(r.pageUrl, fallback);
                    else bytes = httpGetBytes(fallback, 1024 * 1024 * 6);
                }
            } catch (Exception ignored) {}
        }
        if (!MidiSongParser.isMidi(bytes)) throw new Exception("esse resultado não liberou um arquivo MIDI válido");
        return bytes;
    }

    private static class DownloadedMidi {
        OnlineMidiResult result;
        byte[] bytes;
        DownloadedMidi(OnlineMidiResult result, byte[] bytes) {
            this.result = result;
            this.bytes = bytes;
        }
    }

    private DownloadedMidi tryDownloadAlternativeMidi(OnlineMidiResult original) throws Exception {
        ArrayList<OnlineMidiResult> alts = new ArrayList<>();
        String q = cleanAlternativeSearchQuery(original.title);
        if (q.length() < 3) q = cleanAlternativeSearchQuery(original.pageUrl);

        try { fetchBitMidiResultsInto(q, alts); } catch (Exception ignored) {}
        try { fetchWebMidiSearchInto(q, alts); } catch (Exception ignored) {}
        try { addDeltaruneFallbacks(normalizeSearch(q), alts); } catch (Exception ignored) {}

        Exception last = null;
        int checked = 0;
        for (OnlineMidiResult cand : alts) {
            if (cand == null) continue;
            if (isOnlineSequencerUrl(cand.pageUrl) || isOnlineSequencerUrl(cand.directMidiUrl)) continue;
            checked++;
            if (checked > 14) break;
            try {
                byte[] b = downloadBytesForOnlineResult(cand);
                if (MidiSongParser.isMidi(b)) return new DownloadedMidi(cand, b);
            } catch (Exception e) {
                last = e;
            }
        }

        if (last != null) throw new Exception("Online Sequencer retornou HTTP 403 e nenhuma alternativa MIDI direta funcionou");
        throw new Exception("Online Sequencer retornou HTTP 403. Tente um resultado BitMidi/VGMusic ou outro nome de música");
    }

    private String cleanAlternativeSearchQuery(String title) {
        if (title == null) return "";
        String q = cleanHtml(title);
        q = q.replaceAll("(?i)online sequencer", " ");
        q = q.replaceAll("(?i)download midi", " ");
        q = q.replaceAll("(?i)\\s+by[: ].*", " ");
        q = q.replaceAll("#\\d+", " ");
        q = q.replaceAll("\\.mid(i)?", " ");
        q = q.replaceAll("[^A-Za-z0-9 ]", " ");
        q = q.replaceAll("\\s+", " ").trim();
        if (q.length() > 80) q = q.substring(0, 80).trim();
        return q;
    }

    private boolean isOnlineSequencerSearchResult(OnlineMidiResult r) {
        if (r == null) return false;
        return r.source != null && r.source.equalsIgnoreCase("Online Sequencer busca");
    }

    private boolean isOnlineSequencerUrl(String url) {
        return url != null && url.toLowerCase().contains("onlinesequencer.net");
    }

    private byte[] downloadOnlineSequencerMidiBytes(String pageUrl, String midiUrl) throws Exception {
        String id = onlineSequencerIdFromUrl(pageUrl);
        if (id == null) id = onlineSequencerIdFromUrl(midiUrl);
        if (id == null && midiUrl != null) {
            Matcher mid = Pattern.compile("[?&]id=(\\d{3,})", Pattern.CASE_INSENSITIVE).matcher(midiUrl);
            if (mid.find()) id = mid.group(1);
        }
        if (id == null) return httpGetBytesWithReferer(midiUrl, 1024 * 1024 * 6, pageUrl, null);

        String referer = "https://onlinesequencer.net/" + id;
        String cookieHeader = null;
        try {
            HttpPayload page = httpGetPayload(referer, 1024 * 1024 * 3, null,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", null, null);
            cookieHeader = page.cookies;
        } catch (Exception ignored) {}
        try {
            String webCookie = CookieManager.getInstance().getCookie(referer);
            if (webCookie != null && webCookie.trim().length() > 0) {
                cookieHeader = mergeCookieHeaders(cookieHeader, webCookie);
            }
        } catch (Exception ignored) {}

        ArrayList<String> candidates = new ArrayList<>();
        addVariant(candidates, "https://onlinesequencer.net/app/midi.php?id=" + id);
        addVariant(candidates, "https://www.onlinesequencer.net/app/midi.php?id=" + id);
        if (midiUrl != null) addVariant(candidates, midiUrl);

        Exception last = null;
        for (String u : candidates) {
            try {
                byte[] b = httpGetBytesWithBrowserHeaders(u, 1024 * 1024 * 8, referer,
                        "audio/midi,audio/x-midi,audio/mid,application/octet-stream,*/*",
                        cookieHeader,
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                if (MidiSongParser.isMidi(b)) return b;
                last = new Exception("resposta não era MIDI válido");
            } catch (Exception e) { last = e; }
        }
        throw last != null ? last : new Exception("falha ao baixar MIDI do Online Sequencer");
    }

    private String onlineSequencerIdFromUrl(String url) {
        try {
            if (url == null) return null;
            Matcher m = Pattern.compile("onlinesequencer\\.net/(\\d{3,})", Pattern.CASE_INSENSITIVE).matcher(url);
            if (m.find()) return m.group(1);
            Matcher q = Pattern.compile("[?&]id=(\\d{3,})", Pattern.CASE_INSENSITIVE).matcher(url);
            if (q.find()) return q.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String onlineSequencerMidiUrlFromPage(String pageUrl) {
        try {
            if (pageUrl == null) return null;
            Matcher m = Pattern.compile("onlinesequencer\\.net/(\\d{3,})", Pattern.CASE_INSENSITIVE).matcher(pageUrl);
            if (m.find()) return "https://onlinesequencer.net/app/midi.php?id=" + m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String findMidiDownloadUrl(String html, String pageUrl) {
        String os = onlineSequencerMidiUrlFromPage(pageUrl);
        if (os != null) return os;
        if (html == null) return null;
        String bit = findBitMidiDownloadUrl(html);
        if (bit != null) return bit;

        Pattern p = Pattern.compile("(?:href|src)=['\"]([^'\"]+\\.midi?)(?:[?#][^'\"]*)?['\"]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String href = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString();
            String abs = absolutizeUrl(pageUrl, href);
            if (abs != null) return abs;
        }

        Pattern loose = Pattern.compile("(https?://[^\\s\"'<>]+\\.midi?)(?:[?#][^\\s\"'<>]*)?", Pattern.CASE_INSENSITIVE);
        Matcher lm = loose.matcher(html);
        if (lm.find()) return lm.group(1);
        return null;
    }

    private String absolutizeUrl(String base, String href) {
        try {
            if (href == null || href.trim().isEmpty()) return null;
            if (href.startsWith("http://") || href.startsWith("https://")) return href;
            URL b = new URL(base);
            return new URL(b, href).toString();
        } catch (Exception e) { return null; }
    }

    private String findBitMidiDownloadUrl(String html) {
        Pattern p = Pattern.compile("href=\"(https://bitmidi.com/uploads/[^\"]+\\.mid|/uploads/[^\"]+\\.mid)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String u = m.group(1);
            if (u.startsWith("/")) u = "https://bitmidi.com" + u;
            return u;
        }
        Pattern loose = Pattern.compile("(https://bitmidi.com/uploads/[A-Za-z0-9_./%-]+\\.mid|/uploads/[A-Za-z0-9_./%-]+\\.mid)", Pattern.CASE_INSENSITIVE);
        Matcher lm = loose.matcher(html);
        if (lm.find()) {
            String u = lm.group(1);
            if (u.startsWith("/")) u = "https://bitmidi.com" + u;
            return u;
        }
        return null;
    }

    private static class HttpPayload {
        byte[] bytes;
        String cookies;
        HttpPayload(byte[] bytes, String cookies) {
            this.bytes = bytes;
            this.cookies = cookies;
        }
    }

    private byte[] httpGetBytesWithBrowserHeaders(String urlStr, int maxBytes, String referer, String accept, String cookies, String userAgent) throws Exception {
        return httpGetPayload(urlStr, maxBytes, referer, accept, cookies, userAgent).bytes;
    }

    private HttpPayload httpGetPayload(String urlStr, int maxBytes, String referer, String accept, String cookies, String userAgent) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(urlStr).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(18000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", userAgent != null && userAgent.trim().length() > 0 ? userAgent : "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                c.setRequestProperty("Accept", accept != null ? accept : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
                c.setRequestProperty("Cache-Control", "no-cache");
                c.setRequestProperty("Connection", "keep-alive");
                if (referer != null && !referer.trim().isEmpty()) {
                    c.setRequestProperty("Referer", referer);
                    try {
                        URL ru = new URL(referer);
                        c.setRequestProperty("Origin", ru.getProtocol() + "://" + ru.getHost());
                    } catch (Exception ignored) {}
                }
                if (cookies != null && cookies.trim().length() > 0) c.setRequestProperty("Cookie", cookies);

                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                String setCookies = collectSetCookies(c);
                InputStream is = c.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                    if (bos.size() > maxBytes) throw new Exception("arquivo muito grande");
                }
                return new HttpPayload(bos.toByteArray(), mergeCookieHeaders(cookies, setCookies));
            } catch (Exception e) {
                last = e;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw last != null ? last : new Exception("erro de rede");
    }

    private String collectSetCookies(HttpURLConnection c) {
        try {
            List<String> vals = c.getHeaderFields().get("Set-Cookie");
            if (vals == null || vals.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (String v : vals) {
                if (v == null) continue;
                String part = v.split(";", 2)[0].trim();
                if (part.length() == 0) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append(part);
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private String mergeCookieHeaders(String a, String b) {
        if (a == null || a.trim().isEmpty()) return b;
        if (b == null || b.trim().isEmpty()) return a;
        return a + "; " + b;
    }

    private String httpGetText(String url) throws Exception {
        byte[] b = httpGetBytes(url, 1024 * 1024 * 3);
        return new String(b, "UTF-8");
    }

    private byte[] httpGetBytes(String urlStr, int maxBytes) throws Exception {
        return httpGetBytesWithReferer(urlStr, maxBytes, null, null);
    }

    private byte[] httpGetBytesWithReferer(String urlStr, int maxBytes, String referer, String accept) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(urlStr).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(16000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                c.setRequestProperty("Accept", accept != null ? accept : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
                c.setRequestProperty("Cache-Control", "no-cache");
                if (referer != null && !referer.trim().isEmpty()) {
                    c.setRequestProperty("Referer", referer);
                    try {
                        URL ru = new URL(referer);
                        c.setRequestProperty("Origin", ru.getProtocol() + "://" + ru.getHost());
                    } catch (Exception ignored) {}
                }
                if (isOnlineSequencerUrl(urlStr)) {
                    c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                }
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                InputStream is = c.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                    if (bos.size() > maxBytes) throw new Exception("arquivo muito grande");
                }
                return bos.toByteArray();
            } catch (Exception e) {
                last = e;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw last != null ? last : new Exception("erro de rede");
    }

    private String cleanSearchTitle(String html) {
        String t = cleanHtml(html);
        t = t.replace("Online Sequencer - ", "").replace("- Online Sequencer", "");
        t = t.replace("Download MIDI", "").replace("Export MIDI", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    private String cleanHtml(String s) {
        if (s == null) return "";
        s = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        return s.replaceAll("\\s+", " ").trim();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_WALLPAPER_URI, uri.toString()).apply();
                notifyUser("Foto de fundo aplicada.");
                showOptionsScreen();
            } catch (Exception e) {
                notifyUserLong("Erro ao escolher foto de fundo: " + e.getMessage());
            }
            return;
        }
        if (requestCode == PICK_MIDI && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String name = getDisplayName(uri);
                byte[] bytes = readAllBytes(getContentResolver().openInputStream(uri));
                if (!MidiSongParser.isMidi(bytes)) throw new Exception("O arquivo selecionado não parece ser MIDI.");
                String songText = MidiSongParser.convertToSongText(bytes);
                saveMidiToLibrary(name, songText);
                notifyUserLong("MIDI importado: " + name);
                refreshLibrary();
            } catch (Exception e) {
                notifyUserLong("Erro ao importar MIDI: " + e.getMessage());
            }
        }
    }

    @Override public void onBackPressed() {
        if (onlineSequencerWebView != null || currentScreen == SCREEN_BROWSER) {
            cleanupOnlineSequencerWebView();
            showSearchScreen();
            return;
        }
        if (currentScreen == SCREEN_SEARCH || currentScreen == SCREEN_OPTIONS) {
            showMainScreen();
            return;
        }
        if (currentScreen == SCREEN_SETUP) {
            notifyUser("Conclua as permissões para usar o app.");
            return;
        }
        notifyUser("Você já está na tela inicial.");
    }

    @Override protected void onPause() {
        try {
            if (onlineSequencerWebView != null) {
                onlineSequencerWebView.evaluateJavascript("(function(){try{var a=document.querySelectorAll('audio,video');for(var i=0;i<a.length;i++){try{a[i].pause();}catch(e){}}}catch(e){}})();", null);
                onlineSequencerWebView.onPause();
                onlineSequencerWebView.pauseTimers();
            }
        } catch (Exception ignored) {}
        super.onPause();
    }


    @Override protected void onDestroy() {
        synchronized (MainActivity.class) { globalSearchGeneration++; }
        try { if (searchProgressDialog != null) searchProgressDialog.dismiss(); } catch (Exception ignored) {}
        try { if (searchResultsDialog != null) searchResultsDialog.dismiss(); } catch (Exception ignored) {}
        try { if (activeToast != null) activeToast.cancel(); } catch (Exception ignored) {}
        cleanupOnlineSequencerWebView();
        super.onDestroy();
    }
}
