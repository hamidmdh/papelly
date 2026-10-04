package com.mdh.sms;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Minimal RESPOND_VIA_MESSAGE handler for Default-SMS eligibility. */
public class HeadlessSmsSendService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
