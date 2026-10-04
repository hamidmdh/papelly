package com.mdh.sms;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Papely Messaging — unknown-sender filter.
 *
 * Pure-Java decision logic (no Android deps) so it compiles both in the modern
 * Gradle app (com.mdh.sms) and as a drop-in under _upstream
 * (copy to src/com/android/messaging/util/UnknownSenderFilter.java and rename
 * package for the AOSP Soong build).
 *
 * Rule: not-in-contacts -> Folder.UNKNOWN + no notification (catches marketing
 * SMS, since marketers are never contacts). Known contacts -> INBOX + notify.
 */
public final class UnknownSenderFilter {

    public enum Folder { INBOX, UNKNOWN }

    public static final class Prefs {
        public boolean filterUnknownEnabled = true;
        /** Default false = strict spec: OTPs from unknown senders stay silent too. */
        public boolean notifyOtpEvenIfUnknown = false;
        public boolean flagMarketing = true;
        public Set<String> allowedSendersNormalized = new HashSet<>();
    }

    public static final class Result {
        public final Folder folder;
        public final boolean notify;
        public final boolean marketing;
        public final String reason;
        public Result(Folder folder, boolean notify, boolean marketing, String reason) {
            this.folder = folder; this.notify = notify;
            this.marketing = marketing; this.reason = reason;
        }
    }

    private static final String[] MARKETING_KEYWORDS = {
        "offer", "discount", "sale", "promo", "coupon", "cashback",
        "limited time", "hurry", "buy now", "free", "prize", "winner",
        "congratulations", "loan", "credit card", "insurance",
        "offre", "remise", "gagne",
        "عرض", "خصم", "تخفيض", "مبروك", "ربحت", "قرض"
    };

    private static final Pattern OTP_HINT =
            Pattern.compile("\\b(otp|passcode|verification|verify|code)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP_DIGITS = Pattern.compile("\\b\\d{4,8}\\b");

    private UnknownSenderFilter() {}

    /** Keep leading + and alphanumerics, strip spaces/dashes/parens. Lower-cased. */
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

    /**
     * Suffix match so "+1-415-555-1234" == "4155551234".
     * Alphanumeric IDs (e.g. AD-HDFCBK) require exact match only.
     */
    public static boolean addressesMatch(String a, String b) {
        String na = normalize(a), nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return false;
        if (na.equals(nb)) return true;
        String da = digitsOf(na), db = digitsOf(nb);
        if (da.isEmpty() || db.isEmpty()) return false;
        String tailA = tail(da, 10), tailB = tail(db, 10);
        return tailA.length() >= 7 && tailA.equals(tailB);
    }

    public static boolean looksLikeOtp(String body) {
        if (body == null || body.isEmpty()) return false;
        return OTP_HINT.matcher(body).find() && OTP_DIGITS.matcher(body).find();
    }

    /** Label only — routing decision is contact-check only. */
    public static boolean looksLikeMarketing(String body) {
        if (body == null || body.isEmpty()) return false;
        String l = body.toLowerCase(Locale.US);
        if (l.contains("http://") || l.contains("https://") || l.contains("bit.ly")
                || l.contains("tinyurl") || l.contains("cutt.ly")) return true;
        for (String k : MARKETING_KEYWORDS) {
            if (l.contains(k)) return true;
        }
        return false;
    }

    public static Result classify(String sender, String body, boolean isKnownContact, Prefs prefs) {
        if (prefs == null) prefs = new Prefs();
        boolean marketing = prefs.flagMarketing && looksLikeMarketing(body);
        if (!prefs.filterUnknownEnabled) return new Result(Folder.INBOX, true, marketing, "filter disabled");
        if (isKnownContact) return new Result(Folder.INBOX, true, marketing, "known contact");
        String norm = normalize(sender);
        if (!norm.isEmpty() && prefs.allowedSendersNormalized.contains(norm)) {
            return new Result(Folder.INBOX, true, marketing, "user allowlist");
        }
        if (prefs.notifyOtpEvenIfUnknown && looksLikeOtp(body)) {
            return new Result(Folder.UNKNOWN, true, marketing, "otp bypass (still in Unknown)");
        }
        return new Result(Folder.UNKNOWN, false, marketing,
                marketing ? "unknown sender + marketing signals" : "unknown sender");
    }

    private static String digitsOf(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static String tail(String s, int n) {
        return s.length() > n ? s.substring(s.length() - n) : s;
    }

    // For Arrays.toString-free allowlist building in callers.
    public static Set<String> normalizedSet(String... raw) {
        Set<String> out = new HashSet<>();
        for (String r : raw) out.add(normalize(r));
        return out;
    }
}
