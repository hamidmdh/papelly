package com.mdh.sms;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Folder index: Telephony SMS provider has no folder column, so Papely keeps
 * smsId -> folder (+marketing flag) in SharedPreferences. Production: replace
 * with Room (thread_id -> folder) and back the Unknown list from it.
 */
public final class UnknownStore {

    private static final String PREFS = "papely_filter";

    private UnknownStore() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void mark(Context ctx, long smsId,
                            UnknownSenderFilter.Folder folder, boolean marketing) {
        prefs(ctx).edit().putString("sms_" + smsId, folder.name() + "|" + marketing).apply();
    }

    public static UnknownSenderFilter.Folder folderOf(Context ctx, long smsId) {
        String v = prefs(ctx).getString("sms_" + smsId, "INBOX|false");
        return v != null && v.startsWith("UNKNOWN")
                ? UnknownSenderFilter.Folder.UNKNOWN
                : UnknownSenderFilter.Folder.INBOX;
    }

    public static UnknownSenderFilter.Prefs loadPrefs(Context ctx) {
        SharedPreferences p = prefs(ctx);
        UnknownSenderFilter.Prefs out = new UnknownSenderFilter.Prefs();
        out.filterUnknownEnabled = p.getBoolean("filterUnknownEnabled", true);
        out.notifyOtpEvenIfUnknown = p.getBoolean("notifyOtpEvenIfUnknown", false);
        Set<String> raw = p.getStringSet("allowlist", new HashSet<String>());
        Set<String> norm = new HashSet<>();
        if (raw != null) {
            for (String r : raw) norm.add(UnknownSenderFilter.normalize(r));
        }
        out.allowedSendersNormalized = norm;
        return out;
    }
}
