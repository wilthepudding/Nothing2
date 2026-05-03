package com.example.robloxpianoauto;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

public class FloatingControlService extends Service {
    private WindowManager wm;
    private View panel;
    private View calibView;
    private View libraryPanel;
    private LinearLayout contentWrap;
    private TextView selectedLabel;
    private Toast activeToast;
    private WindowManager.LayoutParams panelLp;
    private boolean collapsed = false;
    private final String[] keys = {"1","2","3","4","5","6","7","8","9","0","Q","E","R","T","Y","U","P"};
    private int keyIndex = 0;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showPanel();
    }

    private void showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setBackground(panelBg());
        box.setElevation(dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(12), dp(8));
        header.setBackground(headerBg());
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(dp(172), LinearLayout.LayoutParams.WRAP_CONTENT);
        header.setLayoutParams(headerLp);

        ImageView avatar = new ImageView(this);
        applyProfileImage(avatar);
        makeRoundImage(avatar);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        avatarLp.setMargins(0, 0, dp(8), 0);
        avatar.setLayoutParams(avatarLp);
        header.addView(avatar);

        TextView title = new TextView(this);
        title.setText("JJSAP");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        box.addView(header);

        contentWrap = new LinearLayout(this);
        contentWrap.setOrientation(LinearLayout.VERTICAL);
        contentWrap.setPadding(0, dp(10), 0, 0);

        selectedLabel = new TextView(this);
        selectedLabel.setTextColor(0xC7FFFFFF);
        selectedLabel.setTextSize(8.5f);
        selectedLabel.setTypeface(Typeface.DEFAULT_BOLD);
        selectedLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        selectedLabel.setMaxLines(2);
        selectedLabel.setPadding(dp(6), 0, dp(6), dp(8));
        contentWrap.addView(selectedLabel);
        updateSelectedLabel();

        contentWrap.addView(actionButton("TOCAR", 0xCC47C88B));
        contentWrap.addView(actionButton("PARAR", 0xCCE05B68));
        contentWrap.addView(actionButton("MÚSICAS", 0xCC5A63F6));
        contentWrap.addView(actionButton("CONF. TECLAS", 0xCC7158F6));
        contentWrap.addView(actionButton("FECHAR", 0xCC000000));
        box.addView(contentWrap);

        Button play = (Button) contentWrap.getChildAt(1);
        Button stop = (Button) contentWrap.getChildAt(2);
        Button library = (Button) contentWrap.getChildAt(3);
        Button cal = (Button) contentWrap.getChildAt(4);
        Button close = (Button) contentWrap.getChildAt(5);

        play.setOnClickListener(v -> {
            pauseAutoTouches();
            PianoAccessibilityService svc = PianoAccessibilityService.get();
            if (svc == null) notifyUser("Ative a acessibilidade do app.");
            else svc.playSavedSong();
        });
        stop.setOnClickListener(v -> {
            pauseAutoTouches();
            PianoAccessibilityService svc = PianoAccessibilityService.get();
            if (svc != null) {
                svc.stopPlaying();
                notifyUser("Parado.");
            }
        });
        library.setOnClickListener(v -> { pauseAutoTouches(); toggleLibraryPanel(); });
        cal.setOnClickListener(v -> { pauseAutoTouches(); startCalibration(); });
        close.setOnClickListener(v -> {
            pauseAutoTouches();
            PianoAccessibilityService svc = PianoAccessibilityService.get();
            if (svc != null) svc.stopPlaying();
            stopSelf();
        });

        panel = box;
        panelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.LEFT;
        panelLp.x = dp(14);
        panelLp.y = dp(160);
        wm.addView(panel, panelLp);

        attachDragAndToggle(header, panel, panelLp);
        box.setAlpha(0f);
        box.setScaleX(0.96f);
        box.setScaleY(0.96f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
    }

    private void attachDragAndToggle(View handle, View target, WindowManager.LayoutParams lp) {
        final int[] startX = new int[1], startY = new int[1];
        final float[] touchX = new float[1], touchY = new float[1];
        final boolean[] dragging = new boolean[1];
        final long[] downTime = new long[1];
        handle.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pauseAutoTouches();
                    startX[0] = lp.x;
                    startY[0] = lp.y;
                    touchX[0] = e.getRawX();
                    touchY[0] = e.getRawY();
                    dragging[0] = false;
                    downTime[0] = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - touchX[0];
                    float dy = e.getRawY() - touchY[0];
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) dragging[0] = true;
                    if (dragging[0]) {
                        lp.x = startX[0] + (int) dx;
                        lp.y = startY[0] + (int) dy;
                        try { wm.updateViewLayout(target, lp); } catch (Exception ignored) {}
                        if (libraryPanel != null) updateLibraryPanelPosition();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging[0] && System.currentTimeMillis() - downTime[0] < 250) toggleCollapsed();
                    return true;
            }
            return false;
        });
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        if (collapsed) {
            contentWrap.animate().alpha(0f).translationY(-dp(5)).setDuration(120).withEndAction(() -> contentWrap.setVisibility(View.GONE)).start();
            if (libraryPanel != null) {
                try { wm.removeView(libraryPanel); } catch (Exception ignored) {}
                libraryPanel = null;
            }
        } else {
            contentWrap.setVisibility(View.VISIBLE);
            contentWrap.setAlpha(0f);
            contentWrap.setTranslationY(-dp(5));
            contentWrap.animate().alpha(1f).translationY(0).setDuration(140).start();
        }
    }

    private void toggleLibraryPanel() {
        if (libraryPanel != null) {
            try { wm.removeView(libraryPanel); } catch (Exception ignored) {}
            libraryPanel = null;
            return;
        }
        if (collapsed) toggleCollapsed();
        showLibraryPanel();
    }

    private void showLibraryPanel() {
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String selected = p.getString(MainActivity.KEY_SELECTED_ID, "");
        try {
            JSONArray arr = new JSONArray(p.getString(MainActivity.KEY_LIBRARY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.getString(i);
                String data = p.getString("songlib_data_" + id, "");
                if (!data.trim().isEmpty()) {
                    ids.add(id);
                    names.add(p.getString("songlib_name_" + id, "Música " + (i + 1)));
                }
            }
        } catch (Exception ignored) {}

        LinearLayout bubbleWrap = new LinearLayout(this);
        bubbleWrap.setOrientation(LinearLayout.VERTICAL);
        bubbleWrap.setPadding(dp(2), dp(2), dp(2), dp(2));

        if (ids.isEmpty()) {
            TextView empty = bubbleRow("Nenhuma música ainda", false);
            bubbleWrap.addView(empty);
        } else {
            for (int i = 0; i < ids.size(); i++) {
                final String id = ids.get(i);
                final String name = names.get(i);
                TextView row = bubbleRow((id.equals(selected) ? "▶ " : "♪ ") + name, id.equals(selected));
                row.setOnClickListener(v -> {
                    pauseAutoTouches();
                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putString(MainActivity.KEY_SELECTED_ID, id).apply();
                    if (FirebaseSync.isLoggedIn()) FirebaseSync.saveAll(this, (ok, msg) -> {});
                    updateSelectedLabel();
                    notifyUser("Selecionada: " + name);
                    try { wm.removeView(libraryPanel); } catch (Exception ignored) {}
                    libraryPanel = null;
                });
                bubbleWrap.addView(row);
            }
        }

        libraryPanel = bubbleWrap;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = (panelLp != null ? panelLp.x : dp(14)) + dp(204);
        lp.y = (panelLp != null ? panelLp.y : dp(160)) + dp(10);
        wm.addView(libraryPanel, lp);
        libraryPanel.setAlpha(0f);
        libraryPanel.setTranslationX(dp(10));
        libraryPanel.animate().alpha(1f).translationX(0).setDuration(130).start();
    }

    private TextView bubbleRow(String label, boolean selected) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextColor(Color.WHITE);
        row.setTextSize(11);
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setMaxLines(2);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(selected ? 0xE02A2A2A : 0xB8000000, 12, selected ? 0x66FFFFFF : 0x33FFFFFF));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(9));
        row.setLayoutParams(rlp);
        return row;
    }

    private void updateLibraryPanelPosition() {
        if (libraryPanel == null) return;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) libraryPanel.getLayoutParams();
        lp.x = panelLp.x + dp(204);
        lp.y = panelLp.y + dp(10);
        try { wm.updateViewLayout(libraryPanel, lp); } catch (Exception ignored) {}
    }

    private void updateSelectedLabel() {
        if (selectedLabel == null) return;
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String id = p.getString(MainActivity.KEY_SELECTED_ID, "");
        String name = id.isEmpty() ? "Nenhuma música selecionada" : p.getString("songlib_name_" + id, "Música selecionada");
        selectedLabel.setText(name);
    }

    private void applyProfileImage(ImageView avatar) {
        avatar.setImageResource(R.drawable.app_avatar);
    }

    private void makeRoundImage(ImageView avatar) {
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(round(0xEEFFFFFF, 999, 0));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            avatar.setClipToOutline(true);
            avatar.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        }
    }

    private void startCalibration() {
        keyIndex = 0;
        PianoAccessibilityService svc = PianoAccessibilityService.get();
        if (svc != null) svc.stopPlaying();
        try { if (libraryPanel != null) wm.removeView(libraryPanel); } catch (Exception ignored) {}
        libraryPanel = null;
        if (panel != null) wm.removeView(panel);

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0x22000000);

        TextView bubble = new TextView(this);
        bubble.setText(keys[keyIndex]);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(20);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(round(0xD0000000, 999, 0x66DADADA));
        if (android.os.Build.VERSION.SDK_INT >= 21) bubble.setElevation(dp(8));
        android.widget.FrameLayout.LayoutParams bubbleLp = new android.widget.FrameLayout.LayoutParams(dp(54), dp(54), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bubbleLp.setMargins(0, 0, 0, dp(158));
        root.addView(bubble, bubbleLp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(14), dp(12), dp(14), dp(12));
        bottom.setBackground(round(0xDD000000, 18, 0x33FFFFFF));
        if (android.os.Build.VERSION.SDK_INT >= 21) bottom.setElevation(dp(8));

        TextView title = new TextView(this);
        title.setText("Marque a tecla " + keys[keyIndex]);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Toque no centro da tecla marcada. Depois eu mostro a próxima.");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(11);
        hint.setPadding(0, dp(4), 0, dp(8));
        bottom.addView(hint);

        Button done = smallOverlayButton("CONCLUIR");
        done.setOnClickListener(v -> {
            if (keyIndex < keys.length) {
                notifyUser("Ainda faltam algumas teclas.");
                return;
            }
            finishCalibration();
        });
        bottom.addView(done);

        android.widget.FrameLayout.LayoutParams bottomLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        bottomLp.setMargins(dp(12), 0, dp(12), dp(14));
        root.addView(bottom, bottomLp);

        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (keyIndex >= keys.length) return true;
                String key = keys[keyIndex];
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                        .putInt("x_" + key, (int) e.getRawX())
                        .putInt("y_" + key, (int) e.getRawY())
                        .apply();
                keyIndex++;
                if (keyIndex >= keys.length) {
                    bubble.setText("✓");
                    title.setText("Teclas marcadas");
                    hint.setText("Toque em CONCLUIR para salvar.");
                } else {
                    bubble.setText(keys[keyIndex]);
                    title.setText("Marque a tecla " + keys[keyIndex]);
                    hint.setText("Toque no centro da tecla. Próxima: " + keys[keyIndex]);
                }
                bubble.setScaleX(0.86f);
                bubble.setScaleY(0.86f);
                bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return true;
            }
            return true;
        });

        calibView = root;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        wm.addView(calibView, lp);
    }

    private Button smallOverlayButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(12);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setBackground(round(0xCC2A2A2A, 12, 0x44FFFFFF));
        return b;
    }

    private void finishCalibration() {
        if (FirebaseSync.isLoggedIn()) FirebaseSync.saveAll(this, (ok, msg) -> {});
        notifyUser("Teclas salvas!");
        try { if (calibView != null) wm.removeView(calibView); } catch (Exception ignored) {}
        calibView = null;
        showPanel();
    }

    private void attachPressAnimation(View view) {
        view.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(60).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110).start();
                    break;
            }
            return false;
        });
    }

    private Button actionButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setBackground(round(color, 11, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(172), dp(42));
        lp.setMargins(0, 0, 0, dp(9));
        b.setLayoutParams(lp);
        attachPressAnimation(b);
        return b;
    }

    private GradientDrawable panelBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xB3000000);
        gd.setCornerRadius(dp(12));
        return gd;
    }

    private GradientDrawable headerBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x80484848);
        gd.setCornerRadius(dp(999));
        return gd;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radius));
        if (strokeColor != 0) gd.setStroke(dp(1), strokeColor);
        return gd;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void pauseAutoTouches() {
        PianoAccessibilityService svc = PianoAccessibilityService.get();
        if (svc != null) svc.pauseForUserControl(900);
    }

    private void notifyUser(String msg) {
        try { if (activeToast != null) activeToast.cancel(); } catch (Exception ignored) {}
        activeToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        activeToast.show();
    }

    @Override public void onDestroy() {
        PianoAccessibilityService svc = PianoAccessibilityService.get();
        if (svc != null) svc.stopPlaying();
        super.onDestroy();
        try { if (libraryPanel != null) wm.removeView(libraryPanel); } catch (Exception ignored) {}
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        try { if (calibView != null) wm.removeView(calibView); } catch (Exception ignored) {}
        try { if (activeToast != null) activeToast.cancel(); } catch (Exception ignored) {}
    }
}
