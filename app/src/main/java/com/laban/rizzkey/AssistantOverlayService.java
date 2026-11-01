package com.laban.rizzkey;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The floating W. One suggestion at a time in a purple card, with Insert and
 * Regenerate, because that is the shape Laban asked for after seeing Wrizz.
 *
 * An overlay is never the focused input, so Insert cannot type into WhatsApp.
 * It copies instead and says so; the keyboard types.
 */
public class AssistantOverlayService extends Service {

    private static final String[][] STAGES = {
            {"open", "First message"},
            {"keep", "Keeping it going"},
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

    private static final int PURPLE = 0xFF5C22E5;
    private static final int PURPLE_LIGHT = 0xFF7C3AED;
    private static final int CARD = 0xFF14121F;
    private static final int CHIP = 0xFF262238;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private LinearLayout panel;
    private TextView bubble;
    private TextView suggestion;
    private TextView status;
    private TextView stageChip;
    private TextView toneChip;
    private TextView readChip;

    private JSONObject data;
    private String chatWord = null;
    private int stageIndex = 0;
    private int toneIndex = 0;
    private int lineIndex = 0;
    private List<String> pool = new ArrayList<>();
    private String lastPrompt = null;

    @Override
    public void onCreate() {
        super.onCreate();
        loadData();
        startInForeground();
        showBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            if (manager != null && panel != null) manager.removeView(panel);
        } catch (Exception ignored) {
        }
        panel = null;
        bubble = null;
        super.onDestroy();
    }

    private void startInForeground() {
        String id = "rizz_assistant";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notes = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notes != null) {
                NotificationChannel channel = new NotificationChannel(
                        id, "Floating Rizz button", NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                notes.createNotificationChannel(channel);
            }
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, id)
                : new Notification.Builder(this);
        Notification note = builder
                .setContentTitle("Rizz is ready")
                .setContentText("Tap the W in the corner for something to say")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, note);
        }
    }

    /* --------------------------------------------------------------- bubble */

    private void showBubble() {
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager == null) {
            stopSelf();
            return;
        }

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(buildBubble());
        panel.addView(buildCard());
        panel.getChildAt(1).setVisibility(View.GONE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = dp(10);
        params.y = dp(180);

        try {
            manager.addView(panel, params);
        } catch (Exception error) {
            stopSelf();
            return;
        }
        panel.setOnTouchListener(new DragAndTap());
    }

    private View buildBubble() {
        bubble = new TextView(this);
        bubble.setText("W");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(22);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(bubble.getTypeface(), android.graphics.Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{PURPLE_LIGHT, PURPLE});
        shape.setShape(GradientDrawable.OVAL);
        bubble.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(54));
        bubble.setLayoutParams(params);
        bubble.setElevation(dp(8));
        return bubble;
    }

    /* ----------------------------------------------------------------- card */

    private View buildCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(20));
        shape.setStroke(dp(1), 0xFF3A3153);
        card.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.min(screenWidth() - dp(24), dp(330)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);

        card.addView(buildHeader());
        card.addView(buildSuggestion());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);

        TextView insert = pill("Insert", PURPLE, Color.WHITE);
        insert.setOnClickListener(v -> insert());
        LinearLayout.LayoutParams insertParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        insertParams.rightMargin = dp(8);
        actions.addView(insert, insertParams);

        TextView again = pill("Regenerate ↻", 0xFF262238, 0xFFE9E4FF);
        again.setOnClickListener(v -> regenerate());
        actions.addView(again, new LinearLayout.LayoutParams(0, dp(40), 1f));

        card.addView(actions);
        card.addView(buildChips());

        status = new TextView(this);
        status.setTextSize(11);
        status.setTextColor(0xFFB9B2D0);
        status.setPadding(dp(2), dp(8), 0, 0);
        card.addView(status);
        return card;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("Rizz");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(17);
        brand.setTypeface(brand.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(brand, brandParams);

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(0xFFB9B2D0);
        close.setTextSize(15);
        close.setPadding(dp(8), dp(4), dp(4), dp(4));
        close.setOnClickListener(v -> panel.getChildAt(1).setVisibility(View.GONE));
        header.addView(close);
        return header;
    }

    private View buildSuggestion() {
        suggestion = new TextView(this);
        suggestion.setTextColor(Color.WHITE);
        suggestion.setTextSize(14);
        suggestion.setLineSpacing(dp(2), 1f);
        suggestion.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6D28D9, PURPLE});
        shape.setCornerRadius(dp(16));
        suggestion.setBackground(shape);
        suggestion.setOnClickListener(v -> insert());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        suggestion.setLayoutParams(params);
        suggestion.setMinHeight(dp(70));
        return suggestion;
    }

    private View buildChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);

        stageChip = chip(STAGES[stageIndex][1]);
        stageChip.setOnClickListener(v -> {
            stageIndex = (stageIndex + 1) % STAGES.length;
            stageChip.setText(STAGES[stageIndex][1]);
            reloadPool();
        });

        toneChip = chip(TONES[toneIndex][1]);
        toneChip.setOnClickListener(v -> {
            toneIndex = (toneIndex + 1) % TONES.length;
            toneChip.setText(TONES[toneIndex][1]);
            reloadPool();
        });

        readChip = chip("Read chat");
        readChip.setOnClickListener(v -> {
            if (chatWord != null) {
                chatWord = null;
                readChip.setText("Read chat");
            } else {
                String word = keywordFrom(readClipboard());
                chatWord = word;
                readChip.setText(word == null ? "Copy chat first" : "chat: " + word);
            }
            reloadPool();
        });

        TextView ask = chip("Ask AI");
        ask.setOnClickListener(v -> askAi());

        row.addView(stageChip);
        row.addView(toneChip);
        row.addView(readChip);
        row.addView(ask);
        return row;
    }

    private TextView chip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextColor(0xFFE9E4FF);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CHIP);
        shape.setCornerRadius(dp(12));
        chip.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        chip.setLayoutParams(params);
        return chip;
    }

    private TextView pill(String label, int background, int textColor) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(textColor);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(12));
        view.setBackground(shape);
        return view;
    }

    /* -------------------------------------------------------------- actions */

    private void insert() {
        String text = suggestion == null ? null : suggestion.getText().toString();
        if (text == null || text.trim().isEmpty()) return;
        copy(text);
        say("Copied. Long-press the message box and paste.");
    }

    private void regenerate() {
        if (pool.isEmpty()) return;
        lineIndex = (lineIndex + 1) % pool.size();
        showCurrent();
        if (lineIndex == 0 && lastPrompt != null) askAi();
    }

    private void reloadPool() {
        pool = currentLines();
        lineIndex = 0;
        showCurrent();
    }

    private void showCurrent() {
        if (suggestion == null) return;
        if (pool.isEmpty()) {
            suggestion.setText("Tap Read chat, or type with the Rizz key keyboard.");
            return;
        }
        suggestion.setText(pool.get(lineIndex % pool.size()));
    }

    private List<String> currentLines() {
        List<String> lines = new ArrayList<>();
        try {
            String stage = STAGES[stageIndex][0];
            String tone = TONES[toneIndex][0];
            if (chatWord != null) {
                JSONArray templates = data.optJSONObject("keyword")
                        .optJSONArray(tone);
                if (templates != null) {
                    for (int i = 0; i < templates.length(); i++) {
                        lines.add(templates.getString(i).replace("{word}", chatWord));
                    }
                }
            }
            JSONObject stageBlock = data.optJSONObject("lines").optJSONObject(stage);
            JSONArray set = stageBlock == null ? null : stageBlock.optJSONArray(tone);
            if (set != null) {
                for (int i = 0; i < set.length(); i++) {
                    lines.add(set.getString(i));
                }
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    private void askAi() {
        final String stage = STAGES[stageIndex][1];
        final String tone = TONES[toneIndex][1];
        final String chat = readClipboard();
        lastPrompt = AiClient.buildPrompt(stage, tone, chat);
        if (status != null) status.setText("Thinking…");
        AiClient.ask(AiClient.apiKey(this), lastPrompt, (answer, error) -> main.post(() -> {
            if (status == null) return;
            if (error != null) {
                status.setText(error);
                return;
            }
            pool = new ArrayList<>(Arrays.asList(answer));
            lineIndex = 0;
            showCurrent();
            status.setText(chat.trim().isEmpty()
                    ? "From the model. Copy a chat first for a closer answer."
                    : "From the model, using the chat you copied.");
        }));
    }

    /* -------------------------------------------------------------- content */

    private void loadData() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("rizz.json"), "UTF-8"))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            data = new JSONObject(text.toString());
        } catch (Exception error) {
            data = new JSONObject();
        }
        reloadPool();
    }

    private String keywordFrom(String text) {
        if (text == null) return null;
        Set<String> stop = new HashSet<>();
        try {
            JSONArray list = data.optJSONArray("stop");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) stop.add(list.getString(i).toLowerCase());
            }
        } catch (Exception ignored) {
        }
        String[] words = text.toLowerCase().split("[^a-z']+");
        String best = null;
        for (String word : words) {
            if (word.length() < 4 || stop.contains(word)) continue;
            if (dull(word)) continue;
            if (best == null || word.length() > best.length()) best = word;
        }
        return best;
    }

    private boolean dull(String word) {
        String[] endings = {"ly", "able", "ible", "ful", "ous", "ive", "est", "ed"};
        for (String ending : endings) {
            if (word.endsWith(ending)) return true;
        }
        return false;
    }

    private String readClipboard() {
        try {
            ClipboardManager board = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (board == null || !board.hasPrimaryClip()) return "";
            ClipData clip = board.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            return text == null ? "" : text.toString();
        } catch (Exception error) {
            return "";
        }
    }

    private void copy(String text) {
        try {
            ClipboardManager board = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (board != null) board.setPrimaryClip(ClipData.newPlainText("Rizz", text));
        } catch (Exception ignored) {
        }
    }

    private void say(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    /* ------------------------------------------------------------- gestures */

    private class DragAndTap implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float downX;
        private float downY;
        private long downAt;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            WindowManager.LayoutParams params = window();
            if (params == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    downAt = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX - (int) (event.getRawX() - downX);
                    params.y = startY - (int) (event.getRawY() - downY);
                    try {
                        manager.updateViewLayout(panel, params);
                    } catch (Exception ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float movedX = Math.abs(event.getRawX() - downX);
                    float movedY = Math.abs(event.getRawY() - downY);
                    boolean held = System.currentTimeMillis() - downAt > 700;
                    if (!held && movedX < dp(10) && movedY < dp(10)) toggleCard();
                    if (held && movedX < dp(10) && movedY < dp(10)) {
                        say("Rizz hidden");
                        stopSelf();
                    }
                    return true;
                default:
                    return false;
            }
        }

        private WindowManager.LayoutParams window() {
            if (panel == null) return null;
            ViewGroup.LayoutParams params = panel.getLayoutParams();
            return params instanceof WindowManager.LayoutParams
                    ? (WindowManager.LayoutParams) params
                    : null;
        }
    }

    private void toggleCard() {
        if (panel == null || panel.getChildCount() < 2) return;
        View card = panel.getChildAt(1);
        boolean showing = card.getVisibility() == View.VISIBLE;
        card.setVisibility(showing ? View.GONE : View.VISIBLE);
        if (!showing) {
            pool = currentLines();
            lineIndex = 0;
            showCurrent();
            if (status != null) status.setText("");
        }
    }
}
