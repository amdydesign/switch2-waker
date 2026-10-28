package io.github.amdydesign.switch2waker;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;

/** Shared logic: settings, webhook call and HDMI input switching. */
final class Waker {
    static final String PREFS = "switch2";
    static final String K_URL = "ha_url";
    static final String K_HOOK = "webhook_id";
    static final String K_INPUT = "input_id";
    static final String K_INPUT_LABEL = "input_label";

    private Waker() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean configured(Context c) {
        SharedPreferences p = prefs(c);
        return !p.getString(K_URL, "").isEmpty() || !p.getString(K_INPUT, "").isEmpty();
    }

    static String webhookUrl(Context c) {
        SharedPreferences p = prefs(c);
        String base = p.getString(K_URL, "").trim();
        String hook = p.getString(K_HOOK, "").trim();
        if (base.isEmpty() || hook.isEmpty()) return null;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
        return base + "/api/webhook/" + hook;
    }

    /** Calls the HA webhook in the background and switches the TV input. */
    static void wake(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        final String url = webhookUrl(app);
        if (url != null) {
            Toast.makeText(app, "Waking up Switch 2…", Toast.LENGTH_SHORT).show();
            new Thread(new Runnable() { public void run() {
                String msg;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setDoOutput(true);
                    c.getOutputStream().write(new byte[0]);
                    int code = c.getResponseCode();
                    c.disconnect();
                    msg = (code >= 200 && code < 300) ? "Sent - Switch 2 is waking up" : "Home Assistant error: HTTP " + code;
                } catch (Exception e) {
                    msg = "Can't reach Home Assistant (" + e.getClass().getSimpleName() + ")";
                }
                final String m = msg;
                h.post(new Runnable() { public void run() { Toast.makeText(app, m, Toast.LENGTH_LONG).show(); } });
            }}).start();
        }
        switchInput(app);
    }

    static void switchInput(Context app) {
        String input = prefs(app).getString(K_INPUT, "");
        if (input.isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(app, "This TV can't switch inputs from apps", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(app, "Input switch failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
