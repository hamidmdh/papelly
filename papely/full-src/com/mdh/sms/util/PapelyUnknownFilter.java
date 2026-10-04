package com.mdh.sms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;

import java.util.Locale;

/**
 * Papely bridge for the full AOSP fork (papely/full-src, package com.mdh.sms).
 * Used by ReceiveSmsMessageAction.patch:
 *   isUnknown()    -> true if sender not in contacts (alphanumeric IDs always unknown)
 *   recordFolder() -> persists conversationId -> UNKNOWN/INBOX for the Unknown folder UI.
 */
public final class PapelyUnknownFilter {

    private static final String PREFS = "papely_filter";

    private PapelyUnknownFilter() {}

    public static String normalize(String address) {
        if (address == null) return "";
        String t = address.trim().toLowerCase(Locale.US);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c) || (c == '+' && i == 0)) sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isUnknown(Context context, String address, String body) {
        if (address == null || address.trim().isEmpty()) return true;
        for (int i = 0; i < address.length(); i++) {
            if (Character.isLetter(address.charAt(i))) return true; // AD-XXX marketing IDs
        }
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("filterUnknownEnabled", true)) return false;
        String norm = normalize(address);
        try {
            if (p.getStringSet("allowlist", null) != null) {
                for (String r : p.getStringSet("allowlist", null)) {
                    if (normalize(r).equals(norm)) return false;
                }
            }
        } catch (Exception ignored) {}
        try {
            Uri uri = Uri.withAppendedPath(
                    PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address));
            Cursor c = context.getContentResolver().query(
                    uri, new String[] { PhoneLookup._ID }, null, null, null);
            if (c != null) {
                try {
                    if (c.getCount() > 0) return false;
                } finally {
                    c.close();
                }
            }
        } catch (SecurityException e) {
            return true; // fail closed: silent + Unknown
        }
        return true;
    }

    public static void recordFolder(Context context, String conversationId,
                                    boolean unknown, String body) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("conv_" + conversationId, unknown ? "UNKNOWN" : "INBOX").apply();
    }

    public static boolean isUnknownConversation(Context context, String conversationId) {
        return "UNKNOWN".equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("conv_" + conversationId, "INBOX"));
    }
}
