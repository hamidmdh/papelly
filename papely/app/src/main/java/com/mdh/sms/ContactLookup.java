package com.mdh.sms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;

/**
 * Papely Messaging contact check. Requires READ_CONTACTS.
 * Alphanumeric sender IDs (AD-HDFCBK, VM-AMAZON) short-circuit to false —
 * they are never contacts, so marketing SMS lands in Unknown.
 */
public final class ContactLookup {

    private ContactLookup() {}

    public static boolean isKnownSender(Context context, String senderAddress) {
        if (senderAddress == null || senderAddress.trim().isEmpty()) return false;
        boolean hasLetter = false;
        for (int i = 0; i < senderAddress.length(); i++) {
            if (Character.isLetter(senderAddress.charAt(i))) { hasLetter = true; break; }
        }
        if (hasLetter) return false;
        try {
            Uri uri = Uri.withAppendedPath(
                    PhoneLookup.CONTENT_FILTER_URI, Uri.encode(senderAddress));
            Cursor c = context.getContentResolver().query(
                    uri, new String[] { PhoneLookup._ID }, null, null, null);
            if (c == null) return false;
            try {
                return c.getCount() > 0;
            } finally {
                c.close();
            }
        } catch (SecurityException e) {
            return false; // permission missing -> fail closed (silent + Unknown)
        }
    }
}
