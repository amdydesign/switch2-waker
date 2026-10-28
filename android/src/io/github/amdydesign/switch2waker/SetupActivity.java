package io.github.amdydesign.switch2waker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Simple D-pad friendly settings screen, built in code (no AndroidX). */
public class SetupActivity extends Activity {
    private EditText url, hook;
    private Spinner input;
    private final List<String> inputIds = new ArrayList<>();
    private final List<String> inputLabels = new ArrayList<>();

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = Waker.prefs(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(24), dp(48), dp(24));

        col.addView(text("Switch 2 Waker", 26, true, Color.WHITE));
        col.addView(text("Wakes your Nintendo Switch 2 through Home Assistant and switches the TV to its HDMI input.", 15, false, 0xFFBBBBBB));

        col.addView(text("Home Assistant address", 16, true, Color.WHITE));
        url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("http://192.168.1.10:8123");
        url.setText(p.getString(Waker.K_URL, ""));
        col.addView(url);

        col.addView(text("Webhook ID (from the \"wake triggers\" blueprint)", 16, true, Color.WHITE));
        hook = new EditText(this);
        hook.setSingleLine(true);
        hook.setHint("switch2_wake");
        hook.setText(p.getString(Waker.K_HOOK, "switch2_wake"));
        col.addView(hook);

        col.addView(text("Switch's HDMI input", 16, true, Color.WHITE));
        inputIds.add("");
        inputLabels.add("Don't switch input");
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo i : tim.getTvInputList()) {
                    if (!i.isPassthroughInput()) continue;
                    CharSequence l = i.loadLabel(this);
                    inputIds.add(i.getId());
                    inputLabels.add((l != null ? l.toString() : "Input") + "   (" + i.getId() + ")");
                }
            }
        } catch (Exception ignored) { }
        input = new Spinner(this, Spinner.MODE_DIALOG);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, inputLabels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        input.setAdapter(ad);
        int sel = inputIds.indexOf(p.getString(Waker.K_INPUT, ""));
        input.setSelection(Math.max(sel, 0));
        col.addView(input);
        if (inputIds.size() == 1)
            col.addView(text("No HDMI inputs reported by this TV - only the webhook will be used.", 13, false, 0xFFFF8A80));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, dp(20), 0, 0);
        Button save = new Button(this);
        save.setText("Save");
        Button test = new Button(this);
        test.setText("Save & test");
        row.addView(save);
        row.addView(test);
        col.addView(row);

        col.addView(text("Tip: move the \"Switch 2\" tile to your favourites row. Open \"Switch 2 setup\" from Apps to change these settings.", 13, false, 0xFF888888));

        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (store()) { Toast.makeText(SetupActivity.this, "Saved", Toast.LENGTH_SHORT).show(); finish(); }
        }});
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (store()) Waker.wake(SetupActivity.this);
        }});

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF161616);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        url.requestFocus();
    }

    private boolean store() {
        String u = url.getText().toString().trim();
        String hId = hook.getText().toString().trim();
        int pos = input.getSelectedItemPosition();
        String in = pos >= 0 && pos < inputIds.size() ? inputIds.get(pos) : "";
        if (u.isEmpty() && in.isEmpty()) {
            Toast.makeText(this, "Enter the Home Assistant address or pick an input", Toast.LENGTH_LONG).show();
            return false;
        }
        Waker.prefs(this).edit()
            .putString(Waker.K_URL, u)
            .putString(Waker.K_HOOK, hId)
            .putString(Waker.K_INPUT, in)
            .apply();
        return true;
    }
}
