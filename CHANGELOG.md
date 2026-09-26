# Changelog

## Unreleased — reliability, correctness and UI overhaul

The audit in [`ISSUES.md`](ISSUES.md) found 74 problems. This release addresses the
blockers, the functional bugs, the missing destinations and the UI foundation.

### Reliability — the app now survives in the background

* **Added a foreground service** (`ForwarderForegroundService`). `FOREGROUND_SERVICE` was
  declared in the manifest but no service existed, so forwarding only worked while the app
  happened to be in memory.
* **Added a boot receiver.** `RECEIVE_BOOT_COMPLETED` was declared with no receiver;
  nothing restarted after a reboot or an app update.
* **The notification listener now rebinds.** `onListenerDisconnected` → `requestRebind`.
  Android unbinds listeners routinely, and without this the app stopped forwarding
  permanently until the permission was toggled by hand.
* **Added a battery-optimisation prompt** and OEM autostart guidance.
* **Added a retry queue** (WorkManager, exponential backoff, connectivity-aware). Failed
  forwards used to be written off as `FAILED` and lost; `retryCount` was never incremented
  and `ACCESS_NETWORK_STATE` was never used. Permanent failures are now separated from
  transient ones so a wrong address is not retried forever.

### Notification forwarding — usable for the first time

* **Added an app picker** with icons, labels, package names and search. The rule editor
  previously asked the user to type an app's display label into a free-text box.
* **Added de-duplication**, in memory and against the database. A music player or a
  download bar used to produce one email per redraw.
* **Ongoing, foreground-service, group-summary, local-only and progress notifications are
  now filtered out.**
* **Fixed reverse substring matching.** `matchesSenderFilter` also tested
  `needle.contains(candidate)`, so a filter of `"whatsapp business"` matched a sender
  called `"app"`.

### Email correctness

* **Dot-stuffing (RFC 5321).** A message containing a line starting with `.` truncated the
  email and fed the remainder to the SMTP server as commands.
* **RFC 2047 subject encoding and base64 bodies.** Urdu, Arabic and emoji subjects used to
  arrive as mojibake; the body was declared UTF-8 with no transfer encoding.
* **Header injection blocked.** CR/LF is stripped from every interpolated header value; the
  sender display name could previously smuggle in a `Bcc:`.
* **Multi-line SMTP replies handled**, `AUTH PLAIN`/`AUTH LOGIN` chosen from the server's
  advertised capabilities, and I/O errors surfaced instead of swallowed by `PrintWriter`.
* **Gmail consent is recoverable.** `UserRecoverableAuthException.intent` was discarded,
  leaving an account permanently unable to send; it is now parked and launched by the UI.

### New destinations and rule features

* SMS to another phone number and webhook delivery are **wired up** — both classes existed
  and were never referenced by anything.
* **Telegram bot** delivery added.
* Multiple recipients per rule, **exclude filters**, regex / exact / starts-with matching,
  **per-rule schedules** with weekday selection, global quiet hours, and **custom
  subject/body templates**.
* **Backup and restore** of rules and settings as JSON, and **CSV export** of history.
  Credentials are never written to either file.

### Data and security

* **Real Room migrations** replace `fallbackToDestructiveMigration()`, which silently wiped
  every rule and log on a schema change.
* **Credentials encrypted** with an Android Keystore AES-GCM key, in a separate preferences
  file.
* **`allowBackup` disabled** and the backup rule files filled in; the previously empty
  templates meant message history and the mail password were eligible for cloud backup.
* **`usesCleartextTraffic` removed.**
* **SIM detection rewritten** to use the `subscription` extra and `SubscriptionManager`.
  The old code read undocumented OEM extras, so the per-SIM filter never matched on most
  devices. `dualSimEnabled` was stored and toggled but never read; it now drives real
  behaviour through the detected SIM list.
* **Log queries moved into SQL** with indices and a retention policy. The whole table used
  to be streamed into memory and filtered in Kotlin on every change.

### Interface

* **New design system** (`ui/components/DesignSystem.kt`, `ui/theme/`): semantic colour
  tokens, a full type scale and spacing tokens. Screens no longer branch on
  `isSystemInDarkTheme()` or hard-code hex colours and `sp` sizes.
* **State survives rotation.** `rememberSaveable` throughout — the selected tab used to
  reset to Home and a half-filled rule form was wiped.
* **Keyboard no longer covers inputs** (`imePadding`, and the zeroed `contentWindowInsets`
  removed).
* **Dialogs size correctly** (`usePlatformDefaultWidth = false`), so the rule wizard is
  usable on small screens and does not sit in a narrow column on tablets.
* **Adaptive navigation**: `NavigationRail` on medium and expanded widths.
* **First-run onboarding** and a single setup checklist replace warnings scattered across
  four screens.
* **Permanent permission denial** now routes to the app's settings page instead of doing
  nothing.
* **Notification grouping and a proper status-bar icon** — twenty forwards produced twenty
  notifications drawn with `android.R.drawable.ic_dialog_info`.
* Theme mode (system/light/dark), Material You, and a switch to turn off the developer
  credit appended to forwarded messages.
* **Urdu translation** and all user-facing strings extracted to resources.

### Build and tests

* Unused dependencies commented out (Retrofit, Moshi, Firebase AI, App Check, DataStore,
  Navigation Compose); WorkManager and the window-size-class artifact added.
* R8 enabled for release with keep rules.
* Template tests replaced with **51 real tests** covering `RuleMatcher`, `MimeBuilder`,
  `MessageTemplate` and `BackupManager`, including regression tests for the reverse-match,
  dot-stuffing and header-injection bugs.

### Background health monitoring — the app now tells you the truth

A switch being on proves nothing: Android kills background apps silently, and the old app had
no way to notice or to say so. The engine now reports on itself.

* The foreground service **checks in every 10 minutes** and the app records what the engine has
  really been doing: last check-in, last message seen, last message forwarded, last restart
  after a reboot, and why the service last stopped (`EngineStateStore`).
* A **health card sits at the top of the dashboard** with one of three verdicts — *working*,
  *may stop*, or **NOT working** — plus the line "last message forwarded 4 minutes ago" so the
  claim is backed by a fact rather than a setting.
* `BackgroundHealthChecker` names every reason forwarding can fail and attaches a button that
  fixes it: engine switched off, no SMS permission, no sending account, no active rules, the
  service not running, a **stale heartbeat** (the flag says running but the process was killed),
  notifications blocked for the app, battery optimisation, OEM autostart, missing notification
  access or call-log permission, and messages waiting in the retry queue.
* A **"See background details"** panel shows the raw timestamps, so a sceptical user can check
  for themselves instead of taking the app's word for it.
* **Per-manufacturer autostart instructions** for Xiaomi, Redmi, Poco, Oppo, Realme, Vivo,
  iQOO, Huawei, Honor, OnePlus and Samsung, since each hides the switch somewhere different.
* Permission gaps are only reported when a rule actually needs them — notification access is
  not a problem if no rule forwards notifications.

### The four remaining features

* **App lock** (4.11) — fingerprint, face or device PIN via `BiometricPrompt`, with a keyguard
  fallback on older devices. Locks again the moment the app leaves the foreground, and the
  unlocked flag is deliberately not saved across process death.
* **Missed calls** (4.12) — `CallReceiver` infers a missed call from the RINGING → IDLE
  transition with no OFFHOOK, then reads the number and contact name back from the call log,
  which is the only reliable source since Android 10. Keyword filters are skipped for calls,
  which carry no text.
* **MMS** (4.13) — `WAP_PUSH_DELIVER` only reaches the default SMS app, so `MmsWatcher` observes
  the MMS provider instead and reads each new message once Android has assembled it. The text
  is forwarded; attachments are reported by count.
* **Digest / batching** (4.8) — a rule can collect its matches and send them as one combined
  message every 15 minutes to 1 day. Messages arriving in the same window join the open batch
  rather than starting a new one, and `DigestWorker` reschedules itself for the next batch
  instead of polling.

Schema v4 adds `forwardMms`, `forwardMissedCalls`, `digestEnabled` and `digestIntervalMinutes`
to rules, plus the `BATCHED` log status. Both new sources default to off, so existing rules
behave exactly as before.

### Still not done

* `GoogleSignIn` is still the deprecated API; migrating to Credential Manager +
  `AuthorizationClient` is `ISSUES.md` item 2.14 and is a separate piece of work.
