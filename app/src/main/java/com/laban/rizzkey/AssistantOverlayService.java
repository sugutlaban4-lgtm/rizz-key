package com.laban.rizzkey;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The floating button that sits on the screen while another app is in front.
 *
 * Tap it to ask what to say: three lines appear, tap one and it goes to the
 * clipboard ready to paste. Drag it anywhere. Long press it to close it.
 *
 * Why the clipboard and not straight into the box: an overlay window is not the
 * focused input, so Android will not let it type into another app. The keyboard
 * can type; this panel answers and hands you the text.
 */
public class AssistantOverlayService extends Service {

    private static final String CHANNEL = "rizz-float";
    private static final int NOTIFICATION_ID = 41;

    private static final String[][] STAGES = {
            {"open", "First message"},
            {"keep", "Keep it going"},
            {"dead", "Went quiet"},
            {"meet", "Ask to meet"},
            {"compliment", "Compliment"},
            {"sorry", "Apologise"},
            {"banter", "Banter"}
    };

    private static final String[][] TONES = {
            {"sweet", "Sweet"},
            {"funny", "Funny"},
            {"bold", "Bold"},
            {"sheng", "Sheng"}
    };

    private WindowManager manager;
    private LinearLayout panel;
    private TextView bubble;
    private TextView stageChip;
    private TextView toneChip;
    private TextView readChip;
    private final TextView[] lines = new TextView[3];

    private JSONObject data;
    private String chatWord = null;
    private int stageIndex = 0;
    private int toneIndex = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        loadData();
        startInForeground();

        manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) {
            stopSelf();
            return;
        }

        try {
            manager.addView(buildPanel(), windowParams());
        } catch (Exception error) {
            Toast.makeText(this, "The phone refused the floating button", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Floating Rizz button", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager notifications =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notifications != null) notifications.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("Rizz key is floating")
                .setContentText("Tap the W on screen to ask what to say")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private WindowManager.LayoutParams windowParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = 12;
        params.y = 260;
        return params;
    }

    /* ------------------------------------------------------------- the panel */

    private View buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setVisibility(View.GONE);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#101826"));
        cardBg.setCornerRadius(dp(14));
        panel.addView(card);

        card.addView(chipRow());

        for (int i = 0; i < lines.length; i++) {
            final TextView line = new TextView(this);
            line.setTextSize(14);
            line.setTextColor(Color.parseColor("#f8fbff"));
            line.setPadding(dp(8), dp(8), dp(8), dp(8));
            line.setBackgroundColor(Color.parseColor("#16233a"));
            line.setMaxLines(3);
            line.setVisibility(View.GONE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(240), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            line.setLayoutParams(params);
            line.setOnClickListener(v -> copyLine(line));
            lines[i] = line;
            card.addView(line);
        }

        TextView hint = new TextView(this);
        hint.setText("Tap a line to copy it, then paste in the chat. Long press W to close.");
        hint.setTextSize(11);
        hint.setTextColor(Color.parseColor("#94a3b8"));
        hint.setPadding(0, dp(6), 0, 0);
        hint.setMaxLines(2);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                dp(240), ViewGroup.LayoutParams.WRAP_CONTENT);
        hint.setLayoutParams(hintParams);
        card.addView(hint);

        bubble = new TextView(this);
        bubble.setText("W");
        bubble.setTextSize(18);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.parseColor("#e11d48"));
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubble.setBackground(bubbleBg);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        bubbleParams.topMargin = dp(10);
        bubbleParams.gravity = Gravity.END;
        bubble.setLayoutParams(bubbleParams);

        bubble.setOnTouchListener(new DragAndTap(card));
        panel.addView(bubble);

        this.card = card;
        return panel;
    }

    private LinearLayout card;

    private View chipRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        stageChip = miniChip(STAGES[stageIndex][1]);
        stageChip.setOnClickListener(v -> {
            stageIndex = (stageIndex + 1) % STAGES.length;
            stageChip.setText(STAGES[stageIndex][1]);
            refresh();
        });

        toneChip = miniChip(TONES[toneIndex][1]);
        toneChip.setOnClickListener(v -> {
            toneIndex = (toneIndex + 1) % TONES.length;
            toneChip.setText(TONES[toneIndex][1]);
            refresh();
        });

        readChip = miniChip("Read chat");
        readChip.setOnClickListener(v -> {
            if (chatWord != null) {
                chatWord = null;
                readChip.setText("Read chat");
            } else {
                String word = keywordFrom(readClipboard());
                chatWord = word;
                readChip.setText(word == null ? "Copy chat first" : "chat: " + word);
            }
            refresh();
        });

        row.addView(stageChip);
        row.addView(toneChip);
        row.addView(readChip);
        return row;
    }

    private TextView miniChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(6), dp(8), dp(6));
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextColor(Color.parseColor("#dbeafe"));
        chip.setBackgroundColor(Color.parseColor("#1c2b45"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        chip.setLayoutParams(params);
        return chip;
    }

    /* --------------------------------------------------------------- content */

    private void loadData() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("rizz.json"), "UTF-8"))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            data = new JSONObject(text.toString());
        } catch (Exception error) {
            data = null;
        }
    }

    private List<String> currentLines() {
        List<String> out = new ArrayList<>();
        if (data == null) return out;

        String tone = TONES[toneIndex][0];
        try {
            if (chatWord != null) {
                JSONArray templates = data.optJSONObject("keyword").optJSONArray(tone);
                if (templates != null) {
                    for (int i = 0; i < templates.length(); i++) {
                        out.add(templates.getString(i).replace("{word}", chatWord));
                    }
                }
            }

            JSONObject stageBlock = data.optJSONObject("lines").optJSONObject(STAGES[stageIndex][0]);
            JSONArray set = stageBlock == null ? null : stageBlock.optJSONArray(tone);
            if (set != null) {
                for (int i = 0; i < set.length(); i++) {
                    out.add(set.getString(i));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void refresh() {
        List<String> found = currentLines();
        for (int i = 0; i < lines.length; i++) {
            if (i < found.size()) {
                lines[i].setText(found.get(i));
                lines[i].setVisibility(View.VISIBLE);
            } else {
                lines[i].setText("");
                lines[i].setVisibility(View.GONE);
            }
        }
    }

    private String readClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            return text == null ? "" : text.toString();
        } catch (Exception error) {
            return "";
        }
    }

    private String keywordFrom(String text) {
        if (text == null) return null;

        Set<String> stop = new HashSet<>();
        try {
            JSONArray list = data == null ? null : data.optJSONArray("stop");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) stop.add(list.getString(i));
            }
        } catch (Exception ignored) {
        }

        String[] dull = {"ly", "able", "ible", "ful", "ous", "ive", "est", "ed"};
        String best = "";
        for (String word : text.toLowerCase().split("[^a-z']+")) {
            if (word.length() < 4 || stop.contains(word)) continue;
            boolean skip = false;
            for (String ending : dull) {
                if (word.endsWith(ending)) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;
            if (word.length() > best.length()) best = word;
        }
        return best.isEmpty() ? null : best;
    }

    private void copyLine(TextView view) {
        CharSequence text = view.getText();
        if (TextUtils.isEmpty(text)) return;
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Rizz key", text));
                Toast.makeText(this, "Copied. Long press the box and paste.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception error) {
            Toast.makeText(this, "Could not copy", Toast.LENGTH_SHORT).show();
        }
    }

    /* ------------------------------------------------------------- gestures */

    private class DragAndTap implements View.OnTouchListener {

        private final View target;
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;
        private boolean moved;
        private long downAt;

        DragAndTap(View target) {
            this.target = target;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    WindowManager.LayoutParams start = window();
                    if (start == null) return false;
                    startX = start.x;
                    startY = start.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    moved = false;
                    downAt = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(touchX - event.getRawX());
                    int dy = Math.round(touchY - event.getRawY());
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                    if (moved) {
                        WindowManager.LayoutParams live = window();
                        if (live != null) {
                            live.x = startX + dx;
                            live.y = startY + dy;
                            try {
                                manager.updateViewLayout(panel, live);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        if (System.currentTimeMillis() - downAt > 700) {
                            stopSelf();
                        } else {
                            togglePanel();
                        }
                    }
                    return true;
            }
            return false;
        }

        private WindowManager.LayoutParams window() {
            ViewGroup.LayoutParams raw = panel == null ? null : panel.getLayoutParams();
            return raw instanceof WindowManager.LayoutParams
                    ? (WindowManager.LayoutParams) raw : null;
        }

        private void togglePanel() {
            if (target.getVisibility() == View.VISIBLE) {
                target.setVisibility(View.GONE);
                return;
            }
            refresh();
            target.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (manager != null && panel != null) manager.removeView(panel);
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
