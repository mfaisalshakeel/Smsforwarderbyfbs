# SMS & Notification Forwarder

Forwards incoming SMS messages and app notifications to **email, another phone number,
Telegram or a webhook** — automatically, in the background, and without sending anything
to a third-party server.

Native Android, Kotlin, Jetpack Compose, Room, WorkManager.

---

## What it does

| | |
|---|---|
| **Sources** | Incoming SMS (per-SIM), picture messages (MMS), missed calls, app notifications (per-app) |
| **Destinations** | Email (Gmail API or any SMTP server), SMS to another number, Telegram bot, webhook (JSON / Discord / Slack) |
| **Filtering** | Include and exclude filters on sender and message text, with contains / exact / starts-with / regex matching |
| **Scheduling** | Per-rule time windows and weekday selection, plus a global quiet-hours setting |
| **Formatting** | Built-in layout or your own subject/body template with placeholders |
| **Reliability** | Foreground service, restart on boot, battery-optimisation prompt, and a WorkManager retry queue with exponential backoff |
| **History** | Searchable log of every attempt with the exact error, one-tap resend, CSV export |
| **Backup** | Export and import rules and settings as JSON |
| **Digest** | Collect a rule's matches and send them as one combined message |
| **Privacy** | Optional app lock with fingerprint, face or device PIN |

---

## Is it actually running?

The hardest part of a forwarder is not forwarding — it is surviving in the background. Android
kills background apps silently, and most apps in this category simply stop working without
telling anyone.

This app answers the question directly. The foreground service checks in every ten minutes and
records what the engine has really been doing. The dashboard shows one of three verdicts:

* **Background forwarding is working** — with the time of the last message it actually forwarded.
* **Background forwarding may stop** — battery optimisation is on, or this phone needs autostart.
* **Background forwarding is NOT working** — with each blocking reason and a button that fixes it.

A stale heartbeat counts as *not working* even when the service's own flag still says it is
running, because that is exactly what a silent kill looks like: `onDestroy` never runs, so the
flag is left behind lying. See `util/BackgroundHealth.kt` and
`data/preferences/EngineStateStore.kt`.

## Architecture

```
receiver/SmsReceiver ─────┐
receiver/CallReceiver ────┤
service/MmsWatcher ───────┼──► forwarder/ForwardingManager ──► EmailForwarder (SMTP)
service/NotificationF… ───┘            │                        GmailApiForwarder
                                       │                        PhoneForwarder
                                       │                        TelegramForwarder
                                       │                        WebhookForwarder
                                       │
                                       ├──► RuleMatcher      (which rules apply)
                                       ├──► MessageTemplate  (what the message says)
                                       ├──► MimeBuilder      (RFC 5322 / 2047 encoding)
                                       ├──► EngineStateStore (heartbeat + what it really did)
                                       └──► ForwardScheduler ──► ForwardRetryWorker
                                                             └─► DigestWorker
```

* `data/` — Room entities, DAOs, repositories, and the settings store.
* `forwarder/` — the delivery pipeline. `RuleMatcher`, `MessageTemplate` and `MimeBuilder`
  are free of Android types so they can be unit tested directly.
* `service/` — the foreground service that keeps the process alive and the
  `NotificationListenerService` that captures other apps' notifications.
* `ui/` — Compose screens on a shared design system (`ui/components/DesignSystem.kt`,
  `ui/theme/`). Screens never hard-code colours or font sizes.

---

## Security and privacy

* Nothing is uploaded to the developer. There is no account, no analytics, no telemetry.
* The SMTP app password and Telegram bot token are encrypted with an AES-GCM key held in
  the Android Keystore (`util/SecretCipher`), stored in a separate preferences file.
* `allowBackup` is off, and both backup rule files explicitly exclude the database and
  the credential store, so message history never reaches Google's cloud backup.
* Cleartext traffic is disabled.
* Exported backup files deliberately omit every credential.

---

## Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint
```

Release builds are signed from environment variables:

| Variable | Purpose |
|---|---|
| `KEYSTORE_PATH` | Path to the upload keystore (defaults to `./my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password for the `upload` alias |

---

## Play Store notes

This app requests `READ_SMS` / `RECEIVE_SMS` and uses notification-listener access, so a
Play Console **Permissions Declaration Form** is required, along with a prominent
in-app disclosure and a published privacy policy. The in-app disclosure lives in
**Setup → Privacy**.

`QUERY_ALL_PACKAGES` is declared so the rule editor can show the installed-app list;
this also needs a declaration. If you would rather avoid it, drop the permission and
restrict the app picker to packages that have already posted a notification.

`READ_CALL_LOG` is only used by rules that forward missed calls, and is also covered by the
Permissions Declaration Form. If you do not want to ship missed-call forwarding, remove the
permission, `CallReceiver` and the `forwardMissedCalls` rule option.

---

## Credits

Developed by [PenduCoder](https://penducoder.com).
