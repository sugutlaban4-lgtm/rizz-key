package com.laban.rizzkey;

import android.inputmethodservice.InputMethodService;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A small keyboard that carries its own suggestion engine.
 *
 * Two switches sit above the letters: one picks where the chat is, the other
 * picks how you want to sound. Three lines follow, and tapping one types it
 * into whatever app is open. Everything is bundled, so it works with no data.
 */
public class RizzKeyboardService extends InputMethodService {

    private static final String[][] STAGE_LABELS = {
            {"open", "First message"},
            {"keep", "Keep it going"},
            {"dead", "Went quiet"},
            {"meet", "Ask to meet"},
            {"compliment", "Compliment"},
            {"sorry", "Apologise"},
            {"banter", "Banter"}
    };

    private static final String[][] TONE_LABELS = {
            {"sweet", "Sweet"},
            {"funny", "Funny"},
            {"bold", "Bold"},
            {"sheng", "Sheng"}
    };

    private JSONObject data;
    private int stageIndex = 0;
    private int toneIndex = 0;
    private boolean shift = false;
    private boolean numbers = false;

    private LinearLayout root;
    private LinearLayout letterRows;
    private TextView stageButton;
    private TextView toneButton;
    private final TextView[] suggestionViews = new TextView[3];
    private final List<Button> characterKeys = new ArrayList<>();

    private static final String[] ROW_1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] ROW_2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] ROW_3 = {"z", "x", "c", "v", "b", "n", "m"};
    private static final String[] NUM_1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] NUM_2 = {"-", "/", ":", ";", "(", ")", "?", "!", "'", "\""};
    private static final String[] NUM_3 = {".", ",", "@", "#", "_", "+", "="};

    @Override
    public View onCreateInputView() {
        loadData();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#101826"));
        root.setPadding(dp(6), dp(8), dp(6), dp(10));

        root.addView(buildSwitches());
        root.addView(buildSuggestions());
        letterRows = new LinearLayout(this);
        letterRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(letterRows);
        buildKeys();

        return root;
    }

    /* ---------------------------------------------------------------- data */

    private void loadData() {
        if (data != null) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("rizz.json"), "UTF-8"))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
            data = new JSONObject(text.toString());
        } catch (Exception error) {
            data = new JSONObject();
        }
    }

    private String[] currentLines() {
        List<String> lines = new ArrayList<>();
        try {
            String stage = STAGE_LABELS[stageIndex][0];
            String tone = TONE_LABELS[toneIndex][0];
            JSONObject stageBlock = data.optJSONObject("lines").optJSONObject(stage);
            JSONArray set = stageBlock == null ? null : stageBlock.optJSONArray(tone);
            if (set != null) {
                for (int i = 0; i < set.length(); i++) {
                    lines.add(set.getString(i));
                }
            }
        } catch (Exception ignored) {
        }
        return lines.toArray(new String[0]);
    }

    /* --------------------------------------------------------------- views */

    private View buildSwitches() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, 0, 0, dp(6));

        stageButton = chip(STAGE_LABELS[stageIndex][1]);
        stageButton.setOnClickListener(v -> {
            stageIndex = (stageIndex + 1) % STAGE_LABELS.length;
            stageButton.setText(STAGE_LABELS[stageIndex][1]);
            refreshSuggestions();
        });

        toneButton = chip(TONE_LABELS[toneIndex][1]);
        toneButton.setOnClickListener(v -> {
            toneIndex = (toneIndex + 1) % TONE_LABELS.length;
            toneButton.setText(TONE_LABELS[toneIndex][1]);
            refreshSuggestions();
        });

        bar.addView(stageButton);
        LinearLayout.LayoutParams toneParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        toneParams.setMarginStart(dp(6));
        bar.addView(toneButton, toneParams);
        return bar;
    }

    private TextView chip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(8), dp(10), dp(8));
        chip.setTextColor(Color.parseColor("#dbeafe"));
        chip.setBackgroundColor(Color.parseColor("#1c2b45"));
        chip.setOnClickListener(null);
        chip.setClickable(true);
        return chip;
    }

    private View buildSuggestions() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, dp(8));

        for (int i = 0; i < suggestionViews.length; i++) {
            final TextView line = new TextView(this);
            line.setTextSize(14);
            line.setTextColor(Color.parseColor("#f8fbff"));
            line.setPadding(dp(10), dp(9), dp(10), dp(9));
            line.setBackgroundColor(Color.parseColor("#16233a"));
            line.setMaxLines(2);
            line.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(4);
            line.setLayoutParams(params);
            line.setOnClickListener(v -> {
                CharSequence text = line.getText();
                if (!TextUtils.isEmpty(text)) {
                    getCurrentInputConnection().commitText(text, 1);
                }
            });
            suggestionViews[i] = line;
            column.addView(line);
        }
        return column;
    }

    private void refreshSuggestions() {
        String[] lines = currentLines();
        for (int i = 0; i < suggestionViews.length; i++) {
            suggestionViews[i].setText(i < lines.length ? lines[i] : "");
            suggestionViews[i].setVisibility(i < lines.length ? View.VISIBLE : View.GONE);
        }
    }

    /* ---------------------------------------------------------------- keys */

    private void buildKeys() {
        letterRows.removeAllViews();
        characterKeys.clear();
        if (!numbers) {
            letterRows.addView(keyRow(ROW_1));
            letterRows.addView(keyRow(ROW_2));
            letterRows.addView(lastLetterRow());
            letterRows.addView(actionRow());
        } else {
            letterRows.addView(keyRow(NUM_1));
            letterRows.addView(keyRow(NUM_2));
            letterRows.addView(lastNumberRow());
            letterRows.addView(actionRow());
        }
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
        backspace.setOnClickListener(v -> getCurrentInputConnection().deleteSurroundingText(1, 0));
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

        Button space = key("space", 3.4f);
        space.setTextSize(13);
        row.addView(space);

        row.addView(key(".", 0.8f));

        Button enter = key("⏎", 1.2f);
        enter.setOnClickListener(v -> sendEnter());
        row.addView(enter);

        return row;
    }

    private void sendEnter() {
        android.view.inputmethod.InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        int action = getCurrentInputEditorInfo() == null
                ? android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                : getCurrentInputEditorInfo().imeOptions & android.view.inputmethod.EditorInfo.IME_MASK_ACTION;
        boolean noAction = action == android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                || action == android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED;
        boolean sent = !noAction && sendDefaultEditorAction(true);
        if (!sent) {
            connection.commitText("\n", 1);
        }
    }

    private View lastNumberRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String symbol : NUM_3) {
            row.addView(key(symbol, 1f));
        }
        Button backspace = key("⌫", 1.4f);
        backspace.setOnClickListener(v -> getCurrentInputConnection().deleteSurroundingText(1, 0));
        row.addView(backspace);
        return row;
    }

    private Button key(String label, float weight) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT);
        button.setTextColor(Color.parseColor("#f1f5f9"));
        button.setBackgroundColor(Color.parseColor("#223350"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), weight);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(v -> type(label));
        return button;
    }

    private void type(String label) {
        if ("⌫".equals(label)) {
            getCurrentInputConnection().deleteSurroundingText(1, 0);
            return;
        }
        if ("⇧".equals(label) || "⇩".equals(label) || "123".equals(label) || "abc".equals(label)) {
            return;
        }
        if ("space".equals(label)) {
            getCurrentInputConnection().commitText(" ", 1);
            return;
        }
        if ("⏎".equals(label)) {
            sendEnter();
            return;
        }
        String text = letterOrSymbol(label);
        getCurrentInputConnection().commitText(text, 1);
        if (shift && !numbers) {
            shift = false;
            buildKeys();
        }
    }

    private String letterOrSymbol(String label) {
        if (numbers) return label;
        if (label.length() == 1 && Character.isLetter(label.charAt(0))) {
            return shift ? label.toUpperCase() : label;
        }
        return label;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshSuggestions();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
