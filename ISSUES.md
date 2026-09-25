# SMS & Notification Forwarder — Project Audit / Issue List

Audit date: 2026-09-25 · Branch: `claude/lucid-bell-jxu2q3` · Codebase: ~7,100 lines Kotlin (Compose + Room)

This document lists every problem found in a full read of the codebase, grouped by severity.
Each item has a file/line reference, what is wrong, and the fix direction.

**Summary of counts:** 12 blockers · 21 functional bugs · 18 UI/UX issues · 14 missing features · 9 code-quality issues.

---

## 0. TL;DR — the 8 things that matter most

| # | Problem | Impact |
|---|---------|--------|
| 1 | Notification forwarding has **no app picker** — you must type app names as free text | Core feature unusable |
| 2 | Notification listener has **no de-duplication and no ongoing/group filter** | Email spam: music player, download bars, chat drafts all forwarded repeatedly |
| 3 | **No foreground service, no boot receiver, no battery-optimisation prompt** | Android kills the app; forwarding silently stops after a few hours |
| 4 | **No retry queue / offline handling** | Every SMS that arrives without internet is permanently lost |
| 5 | Rules can only forward to **one email**; SMS→SMS, webhook, Telegram are dead code | Not really an "SMS forwarder" yet |
| 6 | Hand-written SMTP has **no dot-stuffing, no MIME encoding, no header escaping** | Corrupt/truncated emails, broken non-English text, header-injection risk |
| 7 | **No `rememberSaveable` anywhere** | Rotating the phone resets the tab and wipes a half-filled rule form |
| 8 | **App password stored in plaintext + `allowBackup=true` + `usesCleartextTraffic=true`** | Gmail credentials leak to cloud backup |

---

## 1. BLOCKERS — app does not do its job reliably

### 1.1 No app-selection UI for notification forwarding
- **Where:** `app/src/main/java/com/example/ui/screens/RulesScreen.kt:768-793`
- The rule wizard only offers `Any Sender` / `Specific Number/Name` with a free-text field
  (`placeholder = "+1234567890, BANK_ALERT, WhatsApp"`). There is no installed-app list, no
  checkbox list, no icons, no package-name picker.
- `ForwardingManager.kt:264-268` then matches that text against **both** `appName` and `packageName`,
  so the user has to guess the exact label the OEM uses.
- **Fix:** add an `AppPickerDialog` that queries `PackageManager.getInstalledApplications()`,
  shows icon + label + package, lets the user multi-select, and stores a
  `packageNames: String` (comma-separated) column on `ForwardingRuleEntity`. Match on package,
  not on a typed label.

### 1.2 Notification filter matching is dangerously loose
- **Where:** `ForwardingManager.kt:389`
  ```kotlin
  "CONTAINS" -> targets.any { cleanSender.contains(it) || it.contains(cleanSender) }
  ```
- The reverse `it.contains(cleanSender)` means a filter value of `"whatsapp business"` matches a
  sender called `"app"`. A short sender string matches almost any filter. Silent over-forwarding
  of private messages.
- **Fix:** drop the reverse direction; add explicit `EXACT` / `STARTS_WITH` / `REGEX` /
  `NOT_CONTAINS` modes.

### 1.3 No de-duplication — notification spam
- **Where:** `service/NotificationForwarderListener.kt:17-38`
- `onNotificationPosted` fires on **every** update to a notification. A music player, a download
  progress bar, or a chat app that re-posts on typing will generate one email per update.
- No filter for `Notification.FLAG_ONGOING_EVENT`, `FLAG_GROUP_SUMMARY`, `FLAG_FOREGROUND_SERVICE`,
  local-only notifications, or `sbn.isOngoing`.
- **Fix:** skip ongoing/group-summary/foreground notifications, and keep an in-memory LRU of
  `hash(package + title + text)` with a 30–60 s TTL; also persist a `contentHash` column and
  skip if the same hash was forwarded in the last N minutes.

### 1.4 Notification listener never rebinds after Android kills it
- **Where:** `NotificationForwarderListener.kt` — no `onListenerConnected()`,
  `onListenerDisconnected()`, or `requestRebind(ComponentName)` override.
- Android routinely unbinds listener services. Without `requestRebind` the app stops receiving
  notifications until the user manually toggles the permission. This is the single most common
  "app stopped working" complaint for this app category.
- **Fix:**
  ```kotlin
  override fun onListenerDisconnected() { requestRebind(ComponentName(this, javaClass)) }
  ```
  plus a "Notification access health" check on the dashboard.

### 1.5 No foreground service — the process gets killed
- **Where:** `AndroidManifest.xml:24-25` declares `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_DATA_SYNC`, but **no `<service>` of that type exists** in the project.
- `SmsReceiver.goAsync()` gives ~10 s of execution. Any forward that takes longer (slow network,
  OAuth token refresh) is killed mid-flight.
- Declaring an unused `FOREGROUND_SERVICE*` permission is also a Play Console policy rejection.
- **Fix:** add a `ForwarderForegroundService` (type `dataSync`) with a persistent low-importance
  notification, started when the master switch is on, and hand work off to it from the receiver.

### 1.6 No boot receiver despite the permission
- **Where:** `AndroidManifest.xml:23` declares `RECEIVE_BOOT_COMPLETED`; no
  `BOOT_COMPLETED` receiver exists.
- After a reboot the foreground service (once added) will not restart, and on many OEM ROMs
  (Xiaomi/Oppo/Vivo/Samsung) the app stays frozen.
- **Fix:** add `BootReceiver` listening for `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, and
  `MY_PACKAGE_REPLACED`.

### 1.7 No battery-optimisation / autostart guidance
- Nothing requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and there is no OEM autostart
  deep-link screen.
- **Fix:** onboarding step that checks `PowerManager.isIgnoringBatteryOptimizations()` and opens
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; plus a "Xiaomi/Oppo/Vivo users" help card.

### 1.8 No retry queue — failed forwards are lost forever
- **Where:** `ForwardingManager.kt:130-151` writes `status = "FAILED"` and stops.
- `SmsLogEntity.retryCount` exists but nothing ever increments it automatically.
  `ACCESS_NETWORK_STATE` is declared but never used — connectivity is never checked.
- **Fix:** enqueue a `WorkManager` `OneTimeWorkRequest` with
  `NetworkType.CONNECTED` + exponential backoff for every failed forward; a periodic worker to
  drain the pending queue. (WorkManager is not even a dependency yet.)

### 1.9 `retryForwarding` re-sends the wrong content and can target an invalid address
- **Where:** `ForwardingManager.kt:311-340`
  ```kotlin
  formattedBody = log.body   // raw SMS text, not the formatted email body
  toEmail = log.destinationTarget  // can be "ALL" or "None" for SKIPPED logs
  ```
- Retrying a SKIPPED log fails with "Recipient email is invalid"; retrying a real log sends a
  differently-formatted email than the original.
- **Fix:** re-run the rule pipeline by rule id, or persist the rendered body separately from the
  raw message.

### 1.10 Room uses `fallbackToDestructiveMigration()`
- **Where:** `data/local/AppDatabase.kt:33` (DB already at `version = 2`)
- Any future schema change silently **deletes every rule and every log** on update.
- **Fix:** write real `Migration` objects; keep destructive fallback only for debug builds.

### 1.11 Credentials stored in plaintext and backed up to the cloud
- **Where:** `data/preferences/SettingsRepository.kt:27,44` — `senderAppPassword` in plain
  `SharedPreferences`.
- **Where:** `AndroidManifest.xml:29` `allowBackup="true"`, and both
  `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` are **empty templates**, so
  the prefs file (with the Gmail app password) is included in Google auto-backup.
- **Fix:** move secrets to `EncryptedSharedPreferences` / Keystore, and `<exclude>` the prefs file
  in both backup XMLs (or set `allowBackup="false"`).

### 1.12 `usesCleartextTraffic="true"`
- **Where:** `AndroidManifest.xml:37`
- Allows SMS content (OTPs, bank codes) to travel over unencrypted HTTP/SMTP.
- **Fix:** remove it and add a `network_security_config.xml` that permits cleartext only for
  explicitly user-added webhook hosts, if at all.

---

## 2. FUNCTIONAL BUGS

### 2.1 SMTP: no dot-stuffing → truncated or corrupt emails
- **Where:** `forwarder/EmailForwarder.kt:144-165`
- The message body is written straight into `DATA`. If any line of the SMS starts with `.`
  (very common: `.5% interest`, `...ok`), the SMTP server treats it as end-of-data. The email
  is truncated and the remaining text is interpreted as SMTP commands.
- **Fix:** replace `\n.` with `\n..` before sending (RFC 5321 §4.5.2).

### 2.2 SMTP/Gmail: no MIME encoding → broken Urdu/Arabic/emoji
- **Where:** `EmailForwarder.kt:144-162`, `GmailApiForwarder.kt:187-212`
- `Content-Type: text/plain; charset=UTF-8` is declared but there is **no
  `Content-Transfer-Encoding`**, and the `Subject:` header contains raw UTF-8
  (`"[SMS Alert] New message from $senderNumber"` where the sender can be `"ادارہ"`).
  Non-ASCII subjects must be RFC 2047 encoded (`=?UTF-8?B?...?=`).
- The body also mixes `\r\n` (headers) with `\n` (content) — some servers reject or mangle this.
- **Fix:** RFC 2047-encode the subject, add `Content-Transfer-Encoding: base64`, base64 the body,
  and use `\r\n` throughout.

### 2.3 Email header injection
- **Where:** `EmailForwarder.kt:150` and `GmailApiForwarder.kt:201`
  ```kotlin
  append("From: $fromName <$fromAddress>\r\n")
  append("Subject: $subject\r\n")   // subject contains $senderNumber
  ```
- `senderDisplayName` is a free-text field in Settings and `senderNumber` comes from the network.
  A `\r\n` in either injects arbitrary headers (e.g. `Bcc:`).
- **Fix:** strip CR/LF from every value interpolated into a header.

### 2.4 SMTP: only `AUTH LOGIN`, no `AUTH PLAIN` fallback, multi-line greeting breaks
- **Where:** `EmailForwarder.kt:79` `readResponse(reader, 220)` reads **one** line, but many
  servers send a multi-line `220-` greeting → immediate failure.
- **Where:** `EmailForwarder.kt:110-124` — servers that advertise only `AUTH PLAIN`/`XOAUTH2`
  fail with a confusing error.
- **Fix:** parse `250-AUTH` capabilities and pick a supported mechanism; use
  `readMultilineResponse` for the greeting too.

### 2.5 `PrintWriter` swallows I/O errors
- **Where:** `EmailForwarder.kt:76-77` — `PrintWriter` never throws; a broken pipe mid-send is
  reported as success up to the next `readResponse`.
- **Fix:** use `BufferedWriter`/`OutputStreamWriter` directly and check for exceptions.

### 2.6 SIM-slot detection uses undocumented OEM extras
- **Where:** `receiver/SmsReceiver.kt:24`
  ```kotlin
  intent.getIntExtra("simSlot", intent.getIntExtra("slot", intent.getIntExtra("phone", -1)))
  ```
- These extras are not part of the platform API. On most modern devices they are absent, so
  `simSlot` is always `0` and the whole "SIM 1 only / SIM 2 only" rule filter silently never
  applies.
- **Fix:** read the `"subscription"` extra → `SubscriptionManager.getActiveSubscriptionInfo(subId)`
  → `simSlotIndex`, and show the real carrier name.

### 2.7 `dualSimEnabled` setting is dead
- **Where:** `ForwarderSettings.kt:19`, `SettingsRepository.kt:33,48`,
  `SettingsScreen.kt:126,147,701` — stored, toggled in the UI, **never read** by any logic.
- Turning it off does nothing. Misleading UI.

### 2.8 `filterSource` is dead; log source filtering is a hack
- **Where:** `MainViewModel.kt:60` declares `filterSource`, but `logsState`
  (`MainViewModel.kt:62-84`) combines only 4 flows and never uses it.
- Instead `filterDestination` is overloaded:
  `log.destinationType.equals(dest) || log.source.equals(dest)` — so filtering by "EMAIL"
  and by "SMS" go through the same field, giving wrong counts.

### 2.9 `PhoneForwarder` and `WebhookForwarder` are completely unreferenced
- **Where:** `forwarder/PhoneForwarder.kt`, `forwarder/WebhookForwarder.kt` — 184 lines of
  working code that nothing ever constructs.
- `ForwardingRuleEntity` has no `destinationType`; only `recipientEmail`.
- Meanwhile `strings.xml` advertises `dest_phone` = "SMS / Phone" and `dest_webhook` = "Webhook",
  and `metadata.json` claims the app forwards "to Phone numbers, Webhooks, or Email".
- **Result:** the app requests `SEND_SMS` (a high-risk permission requiring a Play Console
  declaration) and never sends a single SMS.

### 2.10 `SEND_SMS` is requested at startup but never used
- **Where:** `MainActivity.kt:156` adds `SEND_SMS` to `permissionsToRequest`.
- Guaranteed Play Store rejection and a big trust hit for users.
- **Fix:** either wire up `PhoneForwarder` (see 4.1) or remove the permission entirely.

### 2.11 Stats and log filtering load the entire table into memory
- **Where:** `MainViewModel.kt:62` and `MainViewModel.kt:89` both `combine(smsLogRepo.allLogs, …)`
  and filter/count in Kotlin.
- Meanwhile `SmsLogDao` already has `getTotalCount()`, `getSuccessCount()`, `getFailedCount()`,
  `getLogsByStatus()`, `getLogsByDestination()` — **all unused**.
- With a few thousand logs the app recomposes the whole list on every insert → jank and OOM risk.
- **Fix:** use the SQL count queries, push filtering into the DAO with a `@RawQuery` or
  parameterised `LIKE`, and use Paging 3 for the list.

### 2.12 No log retention / auto-cleanup
- `sms_logs` grows forever, with full message bodies. No cap, no "delete logs older than N days".
- **Fix:** a settings option (30/90/365 days / unlimited) + a periodic cleanup worker.

### 2.13 `UserRecoverableAuthException` recovery intent is thrown away
- **Where:** `GmailApiForwarder.kt:157-163`
  ```kotlin
  catch (e: UserRecoverableAuthException) { /* e.intent discarded */ }
  ```
- If the user has not granted the `gmail.send` scope, every forward fails forever with a generic
  message and no way to fix it from the UI.
- **Fix:** surface `e.intent` up to the Activity and launch it via an
  `ActivityResultLauncher`, then retry.

### 2.14 `GoogleSignIn` API is deprecated
- **Where:** `ui/components/GoogleSignInComponent.kt:94-101` uses `GoogleSignInOptions` /
  `GoogleSignIn.getClient` — deprecated and removed from new Play Services.
- **Fix:** migrate to Credential Manager + `AuthorizationClient` (`Identity.getAuthorizationClient`)
  for the `gmail.send` scope.

### 2.15 Multiple messages from the same sender in one broadcast get concatenated
- **Where:** `SmsReceiver.kt:38-41` — `groupBy { displayOriginatingAddress }` then
  `joinToString("")`. This is correct for multipart parts of one SMS but merges two separate
  messages that happen to arrive together.
- **Fix:** group by `(sender, timestampMillis)` or use `SmsMessage.getIndexOnIcc`/ref number.

### 2.16 `GET_ACCOUNTS` is requested in the manifest but never at runtime
- **Where:** `AndroidManifest.xml:14`, used by `GoogleAccountHelper.getDeviceGoogleAccounts()`.
- On API 23+ this is a runtime permission and is never in `permissionsToRequest`
  (`MainActivity.kt:148-155`), so `getAccountsByType` returns an empty list and the "pick
  recipient from phone accounts" button silently falls back every time.
- Note: since API 26 this returns only accounts the app owns anyway — the whole helper is
  unreliable. Use the system account picker directly.

### 2.17 Notification forwarding ignores the "forwarder paused" log trail
- **Where:** `ForwardingManager.kt:171-176` returns `emptyList()` when the engine is off or no
  rules match, with **no log entry** — unlike the SMS path which logs `SKIPPED`.
- Users get zero feedback about why a notification was not forwarded.

### 2.18 Notification body and title are duplicated in the email
- **Where:** `ForwardingManager.kt:283-292` — the email header already contains `Title: $title`,
  and `fullContent` (logged) is `"[$title]\n$text"`, but the emailed body uses `text` only.
  Log and email content differ, so "Retry" (see 1.9) sends yet another variant.

### 2.19 `SmsReceiver` creates a new `CoroutineScope` per instance with no cancellation
- **Where:** `SmsReceiver.kt:15` — the scope outlives `onReceive`; `goAsync()`'s
  `pendingResult.finish()` is called but the scope itself is never cancelled.
- Low impact (receiver instances are short-lived) but leaks a `SupervisorJob` per broadcast.

### 2.20 No `onNotificationRemoved`/state reconciliation, no MMS, no sent-SMS handling
- Only `SMS_RECEIVED_ACTION` is handled. `WAP_PUSH_DELIVER` (MMS) and outgoing messages are
  not covered at all.

### 2.21 `Application.instance` static leak
- **Where:** `SmsForwarderApplication.kt:31,48` — a static `lateinit var instance` that is never
  used anywhere else in the project. Dead code + lint warning.

---

## 3. UI / UX ISSUES

### 3.1 Nothing survives rotation — no `rememberSaveable` in the whole project
- **Where:** `MainActivity.kt:116-117` (`currentTab`, `showAboutDialog`),
  `RulesScreen.kt:544-559` (every field of the rule wizard),
  `SettingsScreen.kt:120-130`, `SimulatorScreen.kt`.
- Rotating the phone (or a keyboard config change, or dark-mode switch) **resets the selected tab
  to Dashboard and wipes a half-filled rule form**.
- **Fix:** `rememberSaveable` everywhere; keep wizard state in the ViewModel.

### 3.2 Keyboard covers the input fields
- No `Modifier.imePadding()` anywhere in the project, and
  `MainActivity.kt:269` sets `contentWindowInsets = WindowInsets(0, 0, 0, 0)`, which cancels
  Scaffold's inset handling.
- Combined with `enableEdgeToEdge()`, content can also render under the status bar / gesture nav
  bar on some devices.
- **Fix:** remove the zero insets (or apply `.imePadding().navigationBarsPadding()` on the
  content Box), and add `imePadding()` to the scrollable dialogs.

### 3.3 Rule-editor dialog is narrow on tablets and can't be dismissed on small screens
- **Where:** `RulesScreen.kt:577-585` — `Dialog(onDismissRequest = …)` without
  `DialogProperties(usePlatformDefaultWidth = false)`, so the `widthIn(max = 520.dp)` never takes
  effect; the dialog stays at the platform default (~280–360 dp) on every device.
- On a 320 dp-wide phone in landscape, the two-step wizard's buttons are pushed below the fold.
- **Fix:** `DialogProperties(usePlatformDefaultWidth = false)` + `fillMaxWidth(0.95f)` +
  `heightIn(max = …)` + `imePadding()`.

### 3.4 Bottom navigation: 5 items, labels ellipsize on small screens
- **Where:** `MainActivity.kt:239-247` — `fontSize = 11.sp`, `softWrap = false`,
  `overflow = Ellipsis`. On a 320 dp device with a large system font scale, "Filters"/"Settings"
  become "Fil…"/"Set…".
- **Fix:** 4 primary tabs + overflow, or icon-only on compact widths, or
  `NavigationRail` on `WindowWidthSizeClass.Medium+`.

### 3.5 No adaptive layout for tablets/landscape
- Every screen is a single column capped at `widthIn(max = 640.dp)`
  (`DashboardScreen.kt:109`, `LogsScreen.kt:79`, `RulesScreen.kt:128`,
  `SettingsScreen.kt:162`, `SimulatorScreen.kt:91`).
- On a tablet you get a 640 dp strip with huge empty margins; in landscape the bottom bar eats
  most of the height.
- **Fix:** `WindowSizeClass` → `NavigationRail` + 2-pane (list/detail) on Medium/Expanded.

### 3.6 Hardcoded `sp` font sizes ignore the user's font-scale
- 32 literal `.sp` values across the UI (`11.sp` × 10, `12.sp` × 7, …).
- With Android's "largest" font setting, labels overflow and cards clip.
- **Fix:** use `MaterialTheme.typography` tokens; if a custom size is needed, define it in
  `Type.kt`.

### 3.7 Hardcoded hex colours bypass the theme
- 23 `Color(0xFF……)` literals in screens/components — e.g.
  `DashboardScreen.kt:126-129` (`0xFF451A03`, `0xFFFEF3C7`, …),
  `GoogleSignInComponent.kt:161-166`, `PermissionCard.kt` (9 occurrences).
- These don't follow the colour scheme, don't adapt to contrast settings, and each one is
  manually `if (isDark)`-branched, which is error-prone.
- **Fix:** move every one into `Color.kt` as a semantic token and expose them via a
  `CompositionLocal` or extended colour scheme.

### 3.8 Material You / dynamic colour is disabled
- **Where:** `ui/theme/Theme.kt:63` `dynamicColor: Boolean = false`.
- **Fix:** default to `true` on API 31+, with a settings toggle.

### 3.9 No theme setting (system / light / dark)
- `MyApplicationTheme` only follows `isSystemInDarkTheme()`. Most users of this app category
  expect a manual toggle.

### 3.10 `isSystemInDarkTheme()` called in 6 different screens
- `DashboardScreen.kt:92,503`, `RulesScreen.kt:543`, `MainActivity.kt:181`, etc. — each screen
  re-derives its own colours instead of reading them from the theme. Guarantees drift.

### 3.11 Accessibility: decorative icons are fine, but meaningful ones lack labels
- Many `Icon(..., contentDescription = null)` on interactive rows
  (`RulesScreen.kt:670`, `PenduCoderBranding.kt:195-199`, `DashboardScreen.kt:420`).
- No `Modifier.semantics` on status indicator dots (`MainActivity.kt:188-193` — the colour is the
  *only* signal for engine status, which fails for colour-blind users).
- Touch targets: `IconButton` is fine, but several `clickable` `Surface` rows have <48 dp height.

### 3.12 No first-run onboarding
- A new user lands on a Dashboard with warnings and has to discover: grant permissions → connect
  Google → create a rule → enable notification access. Four disconnected places.
- **Fix:** a 4-step onboarding flow with progress, shown until setup is complete.

### 3.13 No permission-denied-forever path
- `MainActivity.kt:157-161` launches the permission request and only re-checks state. If the user
  taps "Don't allow" twice, the button does nothing forever with no explanation.
- **Fix:** detect `shouldShowRequestPermissionRationale == false` and offer
  "Open App Settings".

### 3.14 No loading / empty / error states in several places
- `LogsScreen` shows an empty card but there is no skeleton while Room loads, no error state,
  no pull-to-refresh.
- `isTestingAccount` and `isSimulating` exist but there is no global progress indicator.

### 3.15 All UI text is hardcoded in Kotlin — no localisation
- `strings.xml` has 18 entries, mostly unused; every visible string is a Kotlin literal
  (e.g. `"Create Forwarding Filter"`, `"Google Account Not Connected"`).
- No translation is possible. For an app aimed at Urdu/Hindi-speaking users this is significant.
- **Fix:** extract to `strings.xml`, add `values-ur/`, `values-hi/`.

### 3.16 RTL not actually verified
- `supportsRtl="true"` is set, but several `Row`s use fixed `Spacer(width)` padding patterns and
  non-auto-mirrored icons, so an Urdu/Arabic locale will look wrong.

### 3.17 Notification uses a stock system icon
- **Where:** `util/NotificationHelper.kt:54` — `android.R.drawable.ic_dialog_info`.
  On API 21+ the small icon must be a white silhouette; this one renders as a grey blob.
- Also `notifId = (System.currentTimeMillis() % 100000).toInt()` — every forward creates a **new**
  notification instead of updating/grouping one. 20 SMS = 20 notifications.
- **Fix:** a proper vector `ic_stat_forward`, a stable id + `setGroup()` + summary notification.

### 3.18 Launcher icon and app identity are still the template defaults
- `ic_launcher_foreground.xml` / `ic_launcher_background.xml` are the stock Android Studio green
  robot. `namespace = "com.example"` while `applicationId = "com.aistudio.smsforwarder.vknwpx"`
  (auto-generated). `metadata.json` describes features the app doesn't have.

---

## 4. MISSING FEATURES (what a real SMS forwarder has)

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 4.1 | **Forward SMS → another phone number** | Code exists, not wired | `PhoneForwarder.kt` is complete, including a `{sender}/{body}/{time}` template engine, but no UI and no `destinationType` on the rule |
| 4.2 | **Forward → Webhook / Discord / Slack** | Code exists, not wired | `WebhookForwarder.kt` supports all three formats + custom headers |
| 4.3 | **Forward → Telegram bot** | Missing | The single most-requested destination for this app category |
| 4.4 | **Multiple recipients per rule** | Missing | `recipientEmail` is a single `String`; no CC/BCC |
| 4.5 | **Custom message template per rule** | Missing | Body format is hardcoded in `ForwardingManager.kt:110-125` including a "Developed by PenduCoder" footer users cannot remove |
| 4.6 | **Regex / exclude (blacklist) filters** | Missing | Only `ANY`/`CONTAINS`/`EXACT`; no `NOT_CONTAINS`, no regex, no per-rule priority/stop-processing |
| 4.7 | **Schedule / quiet hours per rule** | Missing | No time window, no weekday selection |
| 4.8 | **Digest / batching mode** | Missing | Every message = one email. 50 SMS/day = 50 emails |
| 4.9 | **Backup & restore rules + settings (JSON export/import)** | Missing | Re-installing means reconfiguring everything |
| 4.10 | **Export logs to CSV** | Missing | |
| 4.11 | **App lock (PIN / biometric)** | Missing | The app's log screen holds every OTP the phone received, in plaintext, behind no lock |
| 4.12 | **Forward missed calls / call log** | Missing | Standard in competing apps |
| 4.13 | **MMS support** | Missing | Only `SMS_RECEIVED` is handled |
| 4.14 | **Privacy policy screen + prominent disclosure** | Missing | Required by Play for `READ_SMS`/notification-access apps; there is no policy link anywhere |

---

## 5. CODE QUALITY / BUILD

### 5.1 Unused dependencies bloat the APK
`app/build.gradle.kts` pulls in but never uses: `firebase-ai`, `firebase-appcheck-recaptcha`,
`firebase-appcheck-debug`, `retrofit`, `converter-moshi`, `moshi-kotlin` (+ its KSP processor),
`logging-interceptor`, `androidx-datastore-preferences`, `androidx-navigation-compose`.
The `google-services` plugin is applied with no `google-services.json`
(masked by `MissingGoogleServicesStrategy.WARN`).

### 5.2 `navigation-compose` is a dependency but navigation is a `when` on an enum
`MainActivity.kt:271-305`. No back-stack, no deep links, no saved state.
The system Back button exits the app from any tab instead of returning to Dashboard.

### 5.3 DataStore is a dependency but settings use `SharedPreferences`
`SettingsRepository.kt` — the synchronous `getSettings()` is called from IO coroutines, which
works, but the mix is inconsistent.

### 5.4 No dependency injection
`SmsForwarderApplication` manually constructs a 6-object graph; `MainViewModel` reaches into
`application as SmsForwarderApplication`. Untestable.

### 5.5 Tests are template leftovers
- `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`, `ExampleRobolectricTest.kt` are the
  generated stubs.
- `GreetingScreenshotTest.kt` screenshots a bare `Text("SMS Forwarder")` — it tests nothing.
- **Zero tests** for `matchesSenderFilter`, `matchesContentFilter`, `ForwardingManager`, the
  SMTP builder, or any DAO.

### 5.6 `minSdk = 24` vs `compileSdk = 36` with untested API branches
Several `Build.VERSION.SDK_INT` branches (notification permission, `SmsManager`) have no
instrumentation coverage.

### 5.7 R8/ProGuard disabled for release
`build.gradle.kts` → `release { isMinifyEnabled = false }`. Larger APK, no obfuscation of the
credential-handling code.

### 5.8 Release signing depends on env vars with a silent fallback
`storePassword = System.getenv("STORE_PASSWORD")` → `null` on a dev machine produces a confusing
build failure rather than a clear message.

### 5.9 Hardcoded vendor branding in the forwarding pipeline
`ForwardingManager.kt:124,238`, `EmailForwarder.kt:160`, `GmailApiForwarder.kt:209` append
`"Developed by PenduCoder • https://penducoder.com"` to **every forwarded message**, with no
setting to turn it off. Users forwarding bank OTPs to a work address will not want this.

---

## 6. Suggested fix order

**Phase 1 — make it reliable (blockers)**
1. Foreground service + boot receiver + battery-optimisation prompt (1.5, 1.6, 1.7)
2. Notification listener: `requestRebind`, ongoing/group filter, de-duplication (1.3, 1.4)
3. WorkManager retry queue + connectivity awareness (1.8)
4. Fix `retryForwarding` (1.9) and Room migrations (1.10)

**Phase 2 — make it correct**
5. App picker for notification rules + package-based matching (1.1, 1.2)
6. SMTP dot-stuffing, MIME/RFC 2047 encoding, header escaping (2.1, 2.2, 2.3)
7. Real SIM detection via `SubscriptionManager` (2.6)
8. Encrypted prefs, backup excludes, drop cleartext traffic (1.11, 1.12)

**Phase 3 — make it complete**
9. Wire up `PhoneForwarder` + `WebhookForwarder`, add Telegram; multi-destination rules (4.1–4.4)
10. Templates, regex/exclude filters, schedules, quiet hours (4.5–4.8)
11. Backup/restore, CSV export, app lock (4.9–4.11)

**Phase 4 — make it feel good**
12. `rememberSaveable` + IME padding + dialog sizing (3.1, 3.2, 3.3)
13. Adaptive layout (NavigationRail / 2-pane), typography & colour tokens (3.5, 3.6, 3.7)
14. Onboarding, permission-denied path, notification grouping (3.12, 3.13, 3.17)
15. Localisation (`values-ur`, `values-hi`) (3.15)

**Phase 5 — hygiene**
16. Remove unused deps, add real navigation, add DI, write tests for the filter/forward logic
