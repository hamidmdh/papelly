# Unknown-Sender Filter — for an open-source SMS app

## Important correction: Google Messages is NOT open-source

Google Messages (`com.google.android.apps.messaging`) — the "Android default messaging app"
on most phones — is **proprietary / closed-source**. Google does not publish its source,
so it cannot be forked and patched.

Verifiable facts:
- No source at android.googlesource.com for `com.google.android.apps.messaging`.
- Play Store listing is proprietary Google LLC app.
- AOSP only publishes the old system apps: `platform/packages/apps/Messaging`
  (AOSP Messaging, archived) and `platform/packages/apps/Mms` (old Mms).

## What this repo is

Since the closed app can't be patched, this repo implements your requested feature
as a **drop-in feature module** you can add to any open-source SMS app that *can*
become the Default SMS app:

Recommended bases (all open-source):
1. **QKSMS** (GPLv3, Kotlin, maintained) — best practical base. Apply `SmsReceiver.kt`
   + `UnknownSenderFilter.kt` to its `QkReceiver` / conversation list.
2. **AOSP Messaging** (`platform/packages/apps/Messaging`) — closest to "default",
   but archived / Java, harder to build.
3. This skeleton itself — minimal Default-SMS-app showing the exact logic.

## Your requested feature

> Any SMS from a number **not in my contacts** → **no notification/sound**,
> moved to **Unknown folder** (includes marketing SMS, since marketers are never contacts).

Implemented in:
- `app/src/main/java/com/example/spamfilter/UnknownSenderFilter.kt` — pure logic, no Android deps, unit-testable
- `app/src/main/java/com/example/spamfilter/ContactChecker.kt` — contact lookup (needs `READ_CONTACTS`)
- `app/src/main/java/com/example/spamfilter/SmsReceiver.kt` — `SMS_DELIVER` receiver, silent write to provider
- `app/src/main/res/` + `AndroidManifest.xml.snippet` — wiring to become Default SMS app

## Key Android behavior you must know

1. **Must be Default SMS app.** Since Android 4.4 (KitKat), only the Default SMS app
   receives `android.provider.Telephony.SMS_DELIVER` and can write to `Telephony.Sms.Inbox`.
   A non-default app receiving `SMS_RECEIVED` **cannot** suppress the default app's notification.
   So: install → set as Default → this filter takes effect.
2. **Notification suppression = don't post one.** Unknown senders are inserted with
   `READ=0, SEEN=0` into the Unknown conversation/thread, and `NotificationManager` is
   never called for them. Known senders get a normal notification channel.
3. **Alphanumeric senders** (`AD-HDFCBK`, `VM-AMAZON`, short codes like `56767`) will
   never match contacts → they correctly go to Unknown. This is what catches marketing SMS.
4. **Edge case: OTP / bank / delivery alerts** are also from unknown senders. This build
   adds a setting `notifyOtpEvenIfUnknown` (default OFF per your spec, toggle ON if you
   want OTPs to still buzz) + an allowlist. See `UnknownSenderFilter.kt`.

## Integration into QKSMS (5 min)

1. Copy `UnknownSenderFilter.kt` into QKSMS `data/` module.
2. In `QkReceiver.onReceive()`, after parsing `SmsMessage`:
   ```kotlin
   val known = ContactChecker.isKnownSender(context, sender)
   val result = UnknownSenderFilter.classify(sender, body, known, prefs)
   if (result.folder == Folder.UNKNOWN) {
       // insert into QKSMS db with type=unknown, do NOT call notificationHelper
       return
   }
   // else normal path + notify
   ```
3. Add left-drawer folder "Unknown" backed by `folder == UNKNOWN` query, with
   "Move to Inbox / Add to contacts / Block" actions.

## Build / test

Core logic has zero Android dependencies so it runs anywhere. A PowerShell port
(`tools/Test-Filter.ps1`) mirrors the Kotlin and is the executable spec here
(no JDK on this machine).
