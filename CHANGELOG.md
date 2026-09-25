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

### Not done

* App lock (PIN / biometric), MMS, missed-call forwarding and digest/batching remain open —
  see `ISSUES.md` items 4.8, 4.11, 4.12 and 4.13.
* `GoogleSignIn` is still the deprecated API; migrating to Credential Manager +
  `AuthorizationClient` is item 2.14 and is a separate piece of work.
