package com.mdh.sms;

import android.app.Activity;
import android.os.Bundle;

/** Minimal SENDTO handler so the system lists Papely as a Default-SMS candidate. */
public class ComposeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
