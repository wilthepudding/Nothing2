package com.example.robloxpianoauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PianoAccessibilityService extends AccessibilityService {
    private static PianoAccessibilityService instance;
    private volatile boolean playing = false;
    private Thread playThread;
    private volatile long pauseUntilMs = 0L;
    private final AtomicInteger playGeneration = new AtomicInteger(0);

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stopPlaying(); }
    @Override public void onDestroy() { stopPlaying(); instance = null; super.onDestroy(); }

    public static PianoAccessibilityService get() { return instance; }

    public void stopPlaying() {
        playing = false;
        playGeneration.incrementAndGet();
        Thread t = playThread;
        if (t != null) {
            t.interrupt();
            playThread = null;
        }
    }


    public void pauseForUserControl(long millis) {
        long until = System.currentTimeMillis() + Math.max(250, millis);
        if (until > pauseUntilMs) pauseUntilMs = until;
    }

    private void waitIfUserControl(int generation) throws InterruptedException {
        while (isCurrent(generation)) {
            long wait = pauseUntilMs - System.currentTimeMillis();
            if (wait <= 0) return;
            Thread.sleep(Math.min(wait, 25));
        }
    }

    public void playSavedSong() {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String selectedId = p.getString(MainActivity.KEY_SELECTED_ID, "");
        String song = selectedId.isEmpty() ? "" : p.getString("songlib_data_" + selectedId, "");
        if (song.trim().isEmpty()) return;
        play(song);
    }

    public void play(String song) {
        stopPlaying();
        playing = true;
        final int myGeneration = playGeneration.incrementAndGet();
        playThread = new Thread(() -> {
            try {
                ArrayList<String> tokens = tokenize(song);
                long delay = 300;
                for (String t : tokens) {
                    if (!isCurrent(myGeneration)) break;
                    if (t.startsWith("tempo=")) {
                        try { delay = Long.parseLong(t.substring(6).trim()); } catch (Exception ignored) {}
                        continue;
                    }
                    long d = delay;
                    String key = t;
                    if (t.contains(":")) {
                        String[] parts = t.split(":", 2);
                        key = parts[0];
                        try { d = Long.parseLong(parts[1]); } catch (Exception ignored) {}
                    }
                    if (key.equals("-") || key.equals("_") || key.equalsIgnoreCase("pause")) {
                        sleepCheckingStop(d, myGeneration);
                        continue;
                    }
                    if (!isCurrent(myGeneration)) break;
                    waitIfUserControl(myGeneration);
                    if (!isCurrent(myGeneration)) break;
                    tapKeys(key, Math.max(35, Math.min(120, d - 30)));
                    sleepCheckingStop(d, myGeneration);
                }
            } catch (InterruptedException ignored) {
            } finally {
                if (playGeneration.get() == myGeneration) {
                    playing = false;
                    playThread = null;
                }
            }
        });
        playThread.start();
    }

    private boolean isCurrent(int generation) {
        return playing && playGeneration.get() == generation && !Thread.currentThread().isInterrupted();
    }

    private void sleepCheckingStop(long millis, int generation) throws InterruptedException {
        long remaining = millis;
        while (remaining > 0 && isCurrent(generation)) {
            long wait = pauseUntilMs - System.currentTimeMillis();
            if (wait > 0) {
                Thread.sleep(Math.min(wait, 25));
                continue;
            }
            long step = Math.min(remaining, 25);
            Thread.sleep(step);
            remaining -= step;
        }
    }

    private ArrayList<String> tokenize(String s) {
        ArrayList<String> out = new ArrayList<>();
        String[] lines = s.split("\\r?\\n");
        for (String line : lines) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            for (String tok : line.trim().split("\\s+")) {
                if (!tok.trim().isEmpty()) out.add(tok.trim());
            }
        }
        return out;
    }

    private void tapKeys(String raw, long duration) {
        String k = raw.replace("[", "").replace("]", "").replace(",", "").replace("+", "").trim();
        if (k.isEmpty()) return;
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        GestureDescription.Builder b = new GestureDescription.Builder();
        boolean hasAny = false;
        for (int i = 0; i < k.length(); i++) {
            String key = String.valueOf(k.charAt(i)).toUpperCase(Locale.US);
            if (key.equals("O")) key = "0";
            int x = p.getInt("x_" + key, -1);
            int y = p.getInt("y_" + key, -1);
            if (x < 0 || y < 0) continue;
            Path path = new Path();
            path.moveTo(x, y);
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            hasAny = true;
        }
        if (hasAny) dispatchGesture(b.build(), null, null);
    }
}
