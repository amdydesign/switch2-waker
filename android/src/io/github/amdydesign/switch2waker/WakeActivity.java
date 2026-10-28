package io.github.amdydesign.switch2waker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class WakeActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Waker.configured(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        } else {
            Waker.wake(this);
        }
        finish();
    }
}
