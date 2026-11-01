package com.laban.rizzkey;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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
 * The keyboard itself, now shaped like the panel: one suggestion in purple,
 * Insert and Regenerate, then the chips, then the letters.
 *
 * The keyboard can do what the floating card cannot: Insert really types the
 * line into the chat, because an input method owns the field.
 */
public class RizzKeyboardService extends InputMethodService {

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

    private static final int PURPLE = 0xFF5C22E5;
    private static final int PURPLE_DEEP = 0xFF4C1D95;
    private static final int CHIP_BG = 0xFF1F1B2E;
    private static final int PANEL = 0xFF101826;
    private static final int KEY_BG = 0xFF223350;

    private static final String[] ROW_1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] ROW_2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] ROW_3 = {"z", "x", "c", "v", "b", "n", "m"};
    private static final String[] NUM_1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] NUM_2 = {"-", "/", ":", ";", "(", ")", "?", "!", "'", "\""};
    private static final String[] NUM_3 = {".", ",", "@", "#", "_", "+", "="};

    private final Handler main = new Handler(Looper.getMainLooper());

    private JSONObject data;
    private String chatWord = null;
    private String lastPrompt = null;
    private int stageIndex = 0;
    private int toneIndex = 0;
    private int lineIndex = 0;
    private List<String> pool = new ArrayList<>();

    private boolean shift = false;
    private boolean numbers = false;

    private LinearLayout letterRows;
    private TextView suggestion;
    private TextView stageChip;
    private TextView toneChip;
    private TextView readChip;
    private TextView status;

    @Override
    public View onCreateInputView() {
        loadData();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PANEL);
        root.setPadding(dp(8), dp(8), dp(8), dp(10));

        root.addView(buildChips());
        root.addView(buildSuggestion());
        root.addView(buildActions());

        letterRows = new LinearLayout(this);
        letterRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowsParams.topMargin = dp(6);
        root.addView(letterRows, rowsParams);
        buildKeys();

        return root;
    }

    /* ---------------------------------------------------------------- chips */

    private View buildChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

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
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(11);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(6), dp(9), dp(6));
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(0xFFC4B5FD);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CHIP_BG);
        shape.setCornerRadius(dp(12));
        view.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    /* ----------------------------------------------------------- suggestion */

    private View buildSuggestion() {
        suggestion = new TextView(this);
        suggestion.setTextColor(Color.WHITE);
        suggestion.setTextSize(13.5f);
        suggestion.setPadding(dp(12), dp(10), dp(12), dp(10));
        suggestion.setMinHeight(dp(52));
        suggestion.setMaxLines(3);
        suggestion.setEllipsize(TextUtils.TruncateAt.END);
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6D28D9, PURPLE});
        shape.setCornerRadius(dp(12));
        suggestion.setBackground(shape);
        suggestion.setOnClickListener(v -> insert());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        suggestion.setLayoutParams(params);
        return suggestion;
    }

    private View buildActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button insert = smallButton("Insert", PURPLE_DEEP, Color.WHITE);
        insert.setOnClickListener(v -> insert());

        Button again = smallButton("Regenerate", CHIP_BG, 0xFFC4B5FD);
        again.setOnClickListener(v -> regenerate());

        status = new TextView(this);
        status.setTextSize(10);
        status.setTextColor(0xFF9AA4B8);
        status.setPadding(dp(8), 0, 0, 0);
        status.setMaxLines(1);
        status.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams insertParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        insertParams.rightMargin = dp(6);
        insertParams.topMargin = dp(6);
        row.addView(insert, insertParams);

        LinearLayout.LayoutParams againParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        againParams.rightMargin = dp(6);
        againParams.topMargin = dp(6);
        row.addView(again, againParams);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(34), 1.2f);
        statusParams.topMargin = dp(6);
        row.addView(status, statusParams);
        return row;
    }

    private Button smallButton(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(10));
        button.setBackground(shape);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    /* -------------------------------------------------------------- content */

    private void loadData() {
        if (data != null) return;
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

    private List<String> currentLines() {
        List<String> lines = new ArrayList<>();
        if (data == null) return lines;
        try {
            String stage = STAGES[stageIndex][0];
            String tone = TONES[toneIndex][0];
            if (chatWord != null) {
                JSONArray templates = data.optJSONObject("keyword").optJSONArray(tone);
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

    private void reloadPool() {
        pool = currentLines();
        lineIndex = 0;
        showCurrent();
    }

    private void showCurrent() {
        if (suggestion == null) return;
        if (pool.isEmpty()) {
            suggestion.setText("Tap Read chat, or Ask AI, for something to say.");
        } else {
            suggestion.setText(pool.get(lineIndex % pool.size()));
        }
    }

    /* -------------------------------------------------------------- actions */

    private void insert() {
        if (suggestion == null) return;
        CharSequence text = suggestion.getText();
        if (TextUtils.isEmpty(text)) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
    }

    private void regenerate() {
        if (pool.isEmpty()) return;
        lineIndex = (lineIndex + 1) % pool.size();
        showCurrent();
        if (lineIndex == 0 && lastPrompt != null) askAi();
    }

    private void askAi() {
        String chat = readClipboard();
        lastPrompt = AiClient.buildPrompt(
                STAGES[stageIndex][1], TONES[toneIndex][1], chat);

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
            status.setText(chat.trim().isEmpty() ? "From the model" : "From the model, with your chat");
        }));
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

    private String keywordFrom(String text) {
        if (text == null) return null;
        Set<String> stop = new HashSet<>();
        try {
            JSONArray list = data == null ? null : data.optJSONArray("stop");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) stop.add(list.getString(i).toLowerCase());
            }
        } catch (Exception ignored) {
        }
        String best = null;
        for (String word : text.toLowerCase().split("[^a-z']+")) {
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

    /* ----------------------------------------------------------------- keys */

    private void buildKeys() {
        letterRows.removeAllViews();
        if (!numbers) {
            letterRows.addView(keyRow(ROW_1));
            letterRows.addView(keyRow(ROW_2));
            letterRows.addView(lastLetterRow());
        } else {
            letterRows.addView(keyRow(NUM_1));
            letterRows.addView(keyRow(NUM_2));
            letterRows.addView(lastNumberRow());
        }
        letterRows.addView(actionRow());
    }

    private View keyRow(String[] letters) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String letter : letters) {
            row.addView(key(letter, 1f));
        }
        return row;
    }

    private View lastLetterRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button shiftKey = key(shift ? "⇧" : "⇩", 1.4f);
        shiftKey.setOnClickListener(v -> {
            shift = !shift;
            buildKeys();
        });
        row.addView(shiftKey);

        for (String letter : ROW_3) {
            row.addView(key(letter, 1f));
        }

        Button backspace = key("⌫", 1.4f);
        backspace.setOnClickListener(v -> deleteOne());
        row.addView(backspace);
        return row;
    }

    private View lastNumberRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String symbol : NUM_3) {
            row.addView(key(symbol, 1f));
        }
        Button backspace = key("⌫", 1.4f);
        backspace.setOnClickListener(v -> deleteOne());
        row.addView(backspace);
        return row;
    }

    private View actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button mode = key(numbers ? "abc" : "123", 1.2f);
        mode.setOnClickListener(v -> {
            numbers = !numbers;
            buildKeys();
        });
        row.addView(mode);

        row.addView(key(",", 0.8f));

        Button space = key("space", 3.2f);
        space.setTextSize(12);
        row.addView(space);

        row.addView(key(".", 0.8f));

        Button enter = key("⏎", 1.2f);
        enter.setOnClickListener(v -> sendEnter());
        row.addView(enter);
        return row;
    }

    private Button key(String label, float weight) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(0xFFF1F5F9);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(KEY_BG);
        shape.setCornerRadius(dp(8));
        button.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), weight);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(v -> type(label));
        return button;
    }

    private void type(String label) {
        if ("⌫".equals(label)) {
            deleteOne();
            return;
        }
        if ("⇧".equals(label) || "⇩".equals(label)
                || "123".equals(label) || "abc".equals(label)) {
            return;
        }
        if ("space".equals(label)) {
            commit(" ");
            return;
        }
        if ("⏎".equals(label)) {
            sendEnter();
            return;
        }
        commit(letterOrSymbol(label));
        if (shift && !numbers) {
            shift = false;
            buildKeys();
        }
    }

    private void commit(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
    }

    private void deleteOne() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.deleteSurroundingText(1, 0);
    }

    private void sendEnter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE
                : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noAction = action == EditorInfo.IME_ACTION_NONE
                || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        boolean sent = !noAction && sendDefaultEditorAction(true);
        if (!sent) connection.commitText("\n", 1);
    }

    private String letterOrSymbol(String label) {
        if (numbers) return label;
        if (label.length() == 1 && Character.isLetter(label.charAt(0))) {
            return shift ? label.toUpperCase() : label;
        }
        return label;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (pool.isEmpty()) reloadPool();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
