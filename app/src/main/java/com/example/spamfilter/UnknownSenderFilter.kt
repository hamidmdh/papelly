package com.example.spamfilter

/**
 * Pure, testable decision logic for the "unknown sender -> silent + Unknown folder" feature.
 *
 * Has ZERO Android dependencies so it can be unit-tested on JVM and ported 1:1
 * to PowerShell (tools/Test-Filter.ps1) for environments without a JDK.
 *
 * Rule (per user spec):
 *   known contact    -> Folder.INBOX,   notify = true
 *   unknown sender   -> Folder.UNKNOWN, notify = false
 *   ...except optional OTP bypass + user allowlist.
 */

enum class Folder { INBOX, UNKNOWN }

data class FilterPrefs(
    /** Master switch for the feature. If false, everything goes to INBOX with notify. */
    val filterUnknownEnabled: Boolean = true,
    /** If true, OTP-looking messages still notify even from unknown senders. Default false = strict spec. */
    val notifyOtpEvenIfUnknown: Boolean = false,
    /** Normalized addresses (see normalize()) the user explicitly allowed. */
    val allowedSendersNormalized: Set<String> = emptySet(),
    /** If true, messages matching marketing keywords get flagged (UI can show "Marketing" chip inside Unknown). */
    val flagMarketing: Boolean = true,
)

data class ClassifyResult(
    val folder: Folder,
    val notify: Boolean,
    val isMarketing: Boolean,
    val reason: String,
)

object UnknownSenderFilter {

    // Broad, language-agnostic marketing cues. Used ONLY as a label inside Unknown,
    // NOT as the routing decision (routing = contact check only, per spec).
    private val MARKETING_KEYWORDS = listOf(
        "offer", "discount", "sale", "promo", "coupon", "cashback",
        "limited time", "hurry", "buy now", "free", "prize", "winner",
        "congratulations", "loan", "credit card", "insurance",
        "offre", "remise", "promo", "gagne", "félicitations",
        "عرض", "خصم", "تخفيض", "مبروك", "ربحت", "قرض"
    )

    // OTP patterns: 4-8 digit code near otp/passcode/verification keywords.
    private val OTP_HINT = Regex("(?i)\\b(otp|passcode|verification|verify|code)\\b")
    private val OTP_DIGITS = Regex("\\b\\d{4,8}\\b")

    /** Normalize for comparison: keep leading + and letters/digits, strip spaces/dashes/parens. */
    fun normalize(address: String?): String {
        if (address == null) return ""
        val t = address.trim().lowercase()
        val sb = StringBuilder()
        for ((i, c) in t.withIndex()) {
            if (c.isLetterOrDigit() || (c == '+' && i == 0)) sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Numeric suffix match so "+1-415-555-1234" matches contact "4155551234".
     * Alphanumeric sender IDs (e.g. "ad-hdfcbk", "vm-amazon") skip suffix logic
     * and require exact normalized match (which will normally fail -> UNKNOWN).
     */
    fun addressesMatch(a: String?, b: String?): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val da = na.filter { it.isDigit() }
        val db = nb.filter { it.isDigit() }
        // If either side has no digits (alphanumeric ID), only exact match counts.
        if (da.isEmpty() || db.isEmpty()) return false
        // Compare last 10 digits (covers country-code differences for NANP-like numbers;
        // for longer E.164 numbers last 10 still avoids most false merges while catching +prefix variants).
        val tailA = da.takeLast(10)
        val tailB = db.takeLast(10)
        return tailA.length >= 7 && tailA == tailB
    }

    fun looksLikeOtp(body: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        return OTP_HINT.containsMatchIn(body) && OTP_DIGITS.containsMatchIn(body)
    }

    fun looksLikeMarketing(body: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        val lower = body.lowercase()
        // URL / short-link is a strong marketing signal for unknown senders.
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("bit.ly") ||
            lower.contains("tinyurl") || lower.contains("cutt.ly")
        ) return true
        return MARKETING_KEYWORDS.any { lower.contains(it) }
    }

    fun classify(
        sender: String?,
        body: String?,
        isKnownContact: Boolean,
        prefs: FilterPrefs = FilterPrefs(),
    ): ClassifyResult {
        val marketing = if (prefs.flagMarketing) looksLikeMarketing(body) else false

        if (!prefs.filterUnknownEnabled) {
            return ClassifyResult(Folder.INBOX, true, marketing, "filter disabled")
        }
        if (isKnownContact) {
            return ClassifyResult(Folder.INBOX, true, marketing, "known contact")
        }
        val norm = normalize(sender)
        if (norm.isNotEmpty() && prefs.allowedSendersNormalized.contains(norm)) {
            return ClassifyResult(Folder.INBOX, true, marketing, "user allowlist")
        }
        if (prefs.notifyOtpEvenIfUnknown && looksLikeOtp(body)) {
            // Still filed under UNKNOWN so inbox stays clean, but buzzes once.
            return ClassifyResult(Folder.UNKNOWN, true, marketing, "otp bypass (still in Unknown)")
        }
        return ClassifyResult(
            Folder.UNKNOWN, false, marketing,
            if (marketing) "unknown sender + marketing signals" else "unknown sender"
        )
    }
}
