package com.mdh.sms;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;

import androidx.core.app.NotificationCompat;

/**
 * Papely SMS_DELIVER receiver (com.mdh.sms).
 *
 * Mirrors upstream SmsDeliverReceiver -> SmsReceiver.deliverSmsMessages ->
 * ReceiveSmsMessageAction flow, with the Papely rule inserted BEFORE notify:
 *
 *   known contact -> Telephony inbox + notification
 *   unknown       -> Telephony inbox + UnknownStore=UNKNOWN, NO notification
 *
 * Must be Default SMS app to receive SMS_DELIVER on Android 4.4+.
 */
public class PapelySmsDeliverReceiver extends BroadcastReceiver {

    static final String KNOWN_CHANNEL_ID = "papely_known";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;

        android.telephony.SmsMessage[] msgs;
        try {
            msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (Exception e) {
            return;
        }
        if (msgs == null || msgs.length == 0) return;

        String sender = msgs[0].getOriginatingAddress();
        StringBuilder sb = new StringBuilder();
        for (android.telephony.SmsMessage m : msgs) {
            if (m.getMessageBody() != null) sb.append(m.getMessageBody());
        }
        String body = sb.toString();

        UnknownSenderFilter.Prefs prefs = UnknownStore.loadPrefs(context);
        boolean known = ContactLookup.isKnownSender(context, sender);
        UnknownSenderFilter.Result r =
                UnknownSenderFilter.classify(sender, body, known, prefs);

        long smsId = insertInbox(context, sender != null ? sender : "", body);
        if (smsId != -1) {
            UnknownStore.mark(context.getApplicationContext(), smsId, r.folder, r.marketing);
        }
        if (r.notify) {
            showNotification(context, sender != null ? sender : "Unknown", body);
        }
        // else: silent by design — no sound, no heads-up, stays in Unknown.
    }

    private long insertInbox(Context context, String sender, String body) {
        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.ADDRESS, sender);
        v.put(Telephony.Sms.BODY, body);
        v.put(Telephony.Sms.DATE, System.currentTimeMillis());
        v.put(Telephony.Sms.READ, 0);
        v.put(Telephony.Sms.SEEN, 0);
        v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
        try {
            Uri uri = context.getContentResolver()
                    .insert(Telephony.Sms.Inbox.CONTENT_URI, v);
            if (uri == null) return -1;
            try {
                return Long.parseLong(uri.getLastPathSegment());
            } catch (NumberFormatException e) {
                return -1;
            }
        } catch (SecurityException e) {
            return -1; // not default SMS app
        }
    }

    private void showNotification(Context context, String sender, String body) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(
                    KNOWN_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_DEFAULT));
        }
        android.app.Notification n = new NotificationCompat.Builder(context, KNOWN_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(sender)
                .setContentText(body)
                .setAutoCancel(true)
                .build();
        nm.notify(sender.hashCode(), n);
    }
}
