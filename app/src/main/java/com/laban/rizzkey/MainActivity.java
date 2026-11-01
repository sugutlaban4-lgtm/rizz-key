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

        /* Some phones will not open the picker from a screen that has no text
           field, so give them one to tap into and try again there. */
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
