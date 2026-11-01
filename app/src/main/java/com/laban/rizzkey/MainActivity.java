package com.laban.rizzkey;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A screen you can actually find. Without this the app has no icon at all,
 * which is why the first build looked like it had never installed.
 *
 * It shows whether the keyboard is switched on, and gives the two taps that
 * matter: enable it, then switch to it.
 */
public class MainActivity extends Activity {

    private TextView status;
    private Button enable;
    private Button choose;
    private Button floatButton;
    private EditText keyField;
    private boolean floatOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Rizz key");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(15, 23, 42));
        root.addView(title, matchWrap());

        TextView lede = new TextView(this);
        lede.setText("A keyboard with three ready replies above the letters. "
                + "Nothing leaves your phone: the app has no internet permission.");
        lede.setTextSize(16);
        lede.setTextColor(Color.rgb(71, 85, 105));
        root.addView(lede, matchWrapWithTop(pad / 2));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, pad, 0, pad / 2);
        root.addView(status, matchWrap());

        enable = new Button(this);
        enable.setText("1. Turn Rizz key on");
        enable.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Exception error) {
                openLanguageSettings();
            }
        });
        root.addView(enable, matchWrap());

        choose = new Button(this);
        choose.setText("2. Switch to Rizz key");
        choose.setOnClickListener(v -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showInputMethodPicker();
            }
        });
        root.addView(choose, matchWrapWithTop(dp(8)));

        TextView floatTitle = new TextView(this);
        floatTitle.setText("Floating button");
        floatTitle.setTextSize(18);
        floatTitle.setTextColor(Color.rgb(15, 23, 42));
        root.addView(floatTitle, matchWrapWithTop(dp(20)));

        TextView floatCopy = new TextView(this);
        floatCopy.setText("A small W sits on the screen beside the keyboard. Tap it to ask "
                + "what to say, tap a line to copy it. Drag it, or press and hold it to "
                + "close it. This needs permission to display over other apps.");
        floatCopy.setTextSize(14);
        floatCopy.setTextColor(Color.rgb(100, 116, 139));
        root.addView(floatCopy, matchWrapWithTop(dp(4)));

        floatButton = new Button(this);
        floatButton.setText("Show the floating W");
        floatButton.setOnClickListener(v -> toggleFloat());
        root.addView(floatButton, matchWrapWithTop(dp(6)));

        /* Some phones will not open the picker from a screen that has no text
           field, so give them one to tap into and try again there. */
        /* ---- the AI key: without it, the pad only has the written lines ---- */
        TextView keyTitle = new TextView(this);
        keyTitle.setText("AI replies (optional)");
        keyTitle.setTextSize(17);
        keyTitle.setTextColor(Color.parseColor("#0f172a"));
        keyTitle.setPadding(0, 26, 0, 6);
        content.addView(keyTitle);

        TextView keyHint = new TextView(this);
        keyHint.setText("The Ask AI button needs a free Gemini key from Google AI Studio. "
                + "Paste it here and it stays on this phone.");
        keyHint.setTextSize(13);
        keyHint.setTextColor(Color.parseColor("#475569"));
        keyHint.setPadding(0, 0, 0, 8);
        content.addView(keyHint);

        keyField = new EditText(this);
        keyField.setHint("Paste your key");
        keyField.setText(AiClient.apiKey(this));
        keyField.setTextSize(13);
        keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        content.addView(keyField);

        Button saveKey = new Button(this);
        saveKey.setText("Save key");
        saveKey.setOnClickListener(v -> {
            AiClient.saveKey(this, keyField.getText().toString());
            Toast.makeText(this, "Key saved", Toast.LENGTH_SHORT).show();
        });
        content.addView(saveKey);

        Button testKey = new Button(this);
        testKey.setText("Try one AI reply");
        testKey.setOnClickListener(v -> {
            testKey.setText("Asking...");
            AiClient.ask(AiClient.apiKey(this),
                    AiClient.buildPrompt("First message", "Sweet", "testing the key"),
                    (answer, error) -> runOnUiThread(() -> {
                        testKey.setText("Try one AI reply");
                        if (error != null) {
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, answer[0], Toast.LENGTH_LONG).show();
                        }
                    }));
        });
        content.addView(testKey);

        TextView hint = new TextView(this);
        hint.setText("If nothing happens, tap the box below, then hold the globe key "
                + "on the keyboard. Your old keyboard comes back the same way.");
        hint.setTextSize(14);
        hint.setTextColor(Color.rgb(100, 116, 139));
        root.addView(hint, matchWrapWithTop(pad));

        EditText box = new EditText(this);
        box.setHint("Try the keyboard here");
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.setMinLines(3);
        root.addView(box, matchWrapWithTop(dp(6)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isEnabled();
        status.setText(on
                ? "Status: switched on. Step 2 puts it on the keyboard."
                : "Status: still off. Step 1 opens the keyboard settings; find "
                  + "Rizz key in the list and turn it on.");
        status.setTextColor(on ? Color.rgb(13, 148, 136) : Color.rgb(180, 83, 9));
        enable.setEnabled(!on);
        choose.setEnabled(on);
        floatOn = canDrawOverlays();
        if (floatButton != null) {
            floatButton.setText(floatOn ? "Hide the floating W" : "Show the floating W");
        }
    }

    private void toggleFloat() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Allow display over other apps, then come back", Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception error) {
                openLanguageSettings();
            }
            return;
        }

        Intent service = new Intent(this, AssistantOverlayService.class);
        if (floatOn) {
            stopService(service);
            floatOn = false;
            floatButton.setText("Show the floating W");
            return;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            floatOn = true;
            floatButton.setText("Hide the floating W");
        } catch (Exception error) {
            Toast.makeText(this, "The phone refused: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean canDrawOverlays() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true;
        return Settings.canDrawOverlays(this);
    }

    private boolean isEnabled() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager == null) return false;
        for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
            if (getPackageName().equals(info.getPackageName())) return true;
        }
        return false;
    }

    private void openLanguageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));
        } catch (Exception ignored) {
            /* Nothing more to offer: the phone has hidden both screens. */
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = top;
        params.gravity = Gravity.START;
        return params;
    }
}
