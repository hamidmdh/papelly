# Papely Messaging — `com.mdh.sms`

App name: **Papely Messaging**. Package: **`com.mdh.sms`**.
Base: AOSP `platform/packages/apps/Messaging` (fetched 2026-10-04 from
`https://android.googlesource.com/platform/packages/apps/Messaging` as
`+archive/refs/heads/main.tar.gz`, 3.6 MB, 469 `.java` files) into `_upstream/`.
Upstream README states: "not actively supported, source only as reference" —
so Papely is a repackaged + filtered fork, not a live upstream tracker.

## Layout

- `_upstream/` — verbatim upstream reference (`com.android.messaging`, Soong `Android.bp`, minSdk 19 / target 24).
- `papely/app/` — buildable modern Gradle skeleton, namespace `com.mdh.sms`, label Papely Messaging.
  - `PapelySmsDeliverReceiver.java` — SMS_DELIVER + unknown→silent+Unknown.
  - `UnknownSenderFilter.java` — pure-Java rule (port of `app/.../UnknownSenderFilter.kt`).
  - `ContactLookup.java`, `UnknownStore.java` — contacts + folder index.
  - `ConversationListActivity.java` / `UnknownListActivity.java` — Inbox / Unknown tabs.
- `papely/patches/ReceiveSmsMessageAction.patch` — exact insertion into upstream
  `ReceiveSmsMessageAction.java:86-171` (contact check before `BugleNotifications.update:171`).
- `papely/tools/repackage-to-com.mdh.sms.ps1` — rewrites `com.android.messaging` → `com.mdh.sms`
  (authorities, manifest, providers) into `papely/full-src/`.

## Behavior

Known contact → Inbox + notification. Unknown (incl. `AD-XXX` marketing IDs, short codes,
non-contacts) → Unknown folder, `READ=1/SEEN=1` in full-fork mode so no badge, **no
`BugleNotifications.update()` / no `NotificationManager.notify()`**. Marketing keywords/URLs
only set a chip inside Unknown; routing is contact-check only.

Must be Default SMS app (only Default gets `SMS_DELIVER`; non-default `SMS_RECEIVED`
cannot suppress the system notification).

## Build

1. `papely/tools/repackage-to-com.mdh.sms.ps1` (full fork) or open `papely/` skeleton directly.
2. Open `papely/` in Android Studio (JDK 17, compileSdk 34), set as Default SMS app on device.
3. Toggle in settings: `filterUnknownEnabled` (default ON), `notifyOtpEvenIfUnknown` (default OFF),
   `allowlist` for OTP/bank/delivery senders.

## Verify (no JDK here, so PowerShell spec)

`tools/Test-Filter.ps1` — 19/19 PASS, mirrors `UnknownSenderFilter` 1:1.
