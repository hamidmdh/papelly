package com.example.spamfilter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat

/**
 * Default-SMS-app receiver implementing:
 *   unknown sender -> insert into provider under Unknown thread, NO notification
 *   known sender   -> insert + normal notification
 *
 * Manifest must declare this for android.provider.Telephony.SMS_DELIVER with
 * priority and the app must be the system Default SMS app (see AndroidManifest.xml.snippet).
 * Only the Default app receives SMS_DELIVER; non-default apps get SMS_RECEIVED
 * and CANNOT suppress the system notification — that is why "patching" the
 * closed Google Messages from outside is impossible.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val KNOWN_CHANNEL_ID = "inbox_known"
        const val PREFS = "spamfilter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return

        val sender = msgs[0].originatingAddress
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        val prefsStore = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefs = FilterPrefs(
            filterUnknownEnabled = prefsStore.getBoolean("filterUnknownEnabled", true),
            notifyOtpEvenIfUnknown = prefsStore.getBoolean("notifyOtpEvenIfUnknown", false),
            allowedSendersNormalized = prefsStore.getStringSet("allowlist", emptySet())
                ?.map { UnknownSenderFilter.normalize(it) }?.toSet() ?: emptySet(),
        )

        val known = ContactChecker.isKnownSender(context, sender)
        val result = UnknownSenderFilter.classify(sender, body, known, prefs)

        // Write to SMS provider. Thread/threading: insert; the conversation list
        // queries folder via a custom "folder" column if your schema has one, or
        // via a separate local Room table mapping thread_id -> folder.
        // Minimal portable version: mark unknown with SEEN=0 and store folder in
        // SharedPreferences-indexed set + local DB (see UnknownStore below).
        insertSms(context, sender ?: "", body, result)

        if (result.notify) {
            showNotification(context, sender ?: "Unknown", body)
        }
        // If !notify: deliberately do nothing — no sound, no heads-up.
    }

    private fun insertSms(context: Context, sender: String, body: String, result: ClassifyResult) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, if (result.folder == Folder.INBOX) 0 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        try {
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            // Record folder mapping locally (provider has no folder column).
            uri?.lastPathSegment?.toLongOrNull()?.let { id ->
                UnknownStore.mark(context, id, result.folder, result.isMarketing)
            }
        } catch (_: SecurityException) {
            // Not default SMS app — insert will fail. Log + ignore.
        }
    }

    private fun showNotification(context: Context, sender: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(KNOWN_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = NotificationCompat.Builder(context, KNOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(sender)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(sender.hashCode(), n)
    }

    @Suppress("unused")
    private fun parsePduCompat(pdu: ByteArray, format: String?): SmsMessage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            SmsMessage.createFromPdu(pdu, format)
        else
            @Suppress("DEPRECATION") SmsMessage.createFromPdu(pdu)
}

/** Minimal folder index. Replace with Room in production (thread_id -> folder). */
object UnknownStore {
    private const val KEY = "folder_map"
    fun mark(context: Context, smsId: Long, folder: Folder, marketing: Boolean) {
        context.getSharedPreferences("spamfilter", Context.MODE_PRIVATE).edit()
            .putString("sms_$smsId", "${folder.name}|$marketing").apply()
    }
    fun folderOf(context: Context, smsId: Long): Folder {
        val v = context.getSharedPreferences("spamfilter", Context.MODE_PRIVATE)
            .getString("sms_$smsId", "INBOX|false") ?: "INBOX|false"
        return if (v.startsWith("UNKNOWN")) Folder.UNKNOWN else Folder.INBOX
    }
}
