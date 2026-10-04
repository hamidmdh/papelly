package com.example.spamfilter

import android.content.Context
import android.provider.ContactsContract.PhoneLookup
import android.net.Uri

/**
 * Contact lookup. Requires READ_CONTACTS.
 * Uses PhoneLookup (E.164-aware) first, falls back to suffix match via
 * UnknownSenderFilter.addressesMatch() for odd formatting.
 */
object ContactChecker {

    fun isKnownSender(context: Context, senderAddress: String?): Boolean {
        if (senderAddress.isNullOrBlank()) return false
        // Alphanumeric sender IDs / short codes are never contacts.
        // Short-circuit before hitting the provider.
        val digits = senderAddress.filter { it.isDigit() }
        val letters = senderAddress.any { it.isLetter() }
        if (letters && digits.length < 7) {
            // e.g. "AD-HDFCBK", "VM-AMAZON", "56767" with letters -> unknown
            // (pure numeric short codes like "56767" still go through lookup below)
            if (letters) return false
        }
        return try {
            val uri = Uri.withAppendedPath(
                PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(senderAddress)
            )
            context.contentResolver.query(
                uri,
                arrayOf(PhoneLookup._ID),
                null, null, null
            )?.use { c -> c.count > 0 } ?: false
        } catch (_: SecurityException) {
            false // READ_CONTACTS not granted -> treat as unknown (safe: silent + Unknown folder)
        }
    }
}
