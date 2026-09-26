# Publishing to Google Play

Everything below is what still stands between this build and a live listing.
Read the **Before you publish** section first — two of those items cannot be undone later.

---

## 1. The keystore is yours to create, and to never lose

The APK you were sent is signed with a **throwaway key generated for testing**. It cannot be
used for Play. You create your own upload key, once:

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'CHOOSE-A-STRONG-PASSWORD' \
  -keypass 'CHOOSE-A-STRONG-PASSWORD' \
  -dname "CN=Your Name, O=Your Company, C=PK"
```

**Back this file up somewhere you will still have in five years, and keep the passwords with
it.** If you lose the upload key you cannot publish an update to the same listing — the app has
to be republished under a new package name and every existing install is stranded. A password
manager plus one offline copy is the usual arrangement.

Do not commit it. `.gitignore` already excludes `*.jks` and `debug.keystore`.

## 2. Build the bundle Play actually wants

Play takes an **.aab** (App Bundle), not an .apk, for new apps.

```bash
export KEYSTORE_PATH=/absolute/path/to/my-upload-key.jks
export STORE_PASSWORD='your-store-password'
export KEY_PASSWORD='your-key-password'

./gradlew clean bundleRelease
```

The file lands at `app/build/outputs/bundle/release/app-release.aab`. That is what you upload.

To sanity-check the signature before uploading:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | head
```

---

## Before you publish — the two irreversible ones

### The application ID is permanent

It is currently **`com.aistudio.smsforwarder.vknwpx`** — an auto-generated name from the
scaffold. Once a listing goes live this can never change. Decide now; something like
`com.penducoder.smsforwarder` reads better. Change it in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.penducoder.smsforwarder"
}
```

(The `namespace = "com.example"` line above it is only the internal R-class package. Play does
not see it, but renaming it to match is worth doing while you are there.)

### The launcher icon is still the template

`ic_launcher_foreground.xml` and `ic_launcher_background.xml` are the stock green Android robot
from the project template. Replace them before anyone sees the listing — Android Studio's
**Image Asset** wizard generates every density from one source image.

---

## Play Console requirements for this app

### Permissions Declaration — the real hurdle

This app requests `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` and `READ_CALL_LOG`. Google restricts
these to apps whose **core functionality** genuinely needs them, and you must complete the
**Permissions Declaration Form** in the Play Console and be approved.

Be honest about the risk: SMS-forwarder apps do exist on Play, but this category is reviewed
closely and rejections are common. Read the current policy before you submit — it changes, and
what was accepted last year may not be now. If the SMS declaration is refused, the app still
has a complete product without it: notification forwarding, MMS and missed calls can ship while
the SMS permissions are removed.

`QUERY_ALL_PACKAGES` (used by the app picker) also needs a declaration. If you would rather
avoid that one, drop the permission and limit the picker to apps that have already posted a
notification.

### Prominent disclosure

An app that reads messages and sends them off the device must show a disclosure **before**
requesting the permission, saying what is collected and where it goes. The Privacy card in
**Setup** covers the substance; check the current wording requirements and make sure the
onboarding screen states it before the permission prompt.

### Privacy policy

A hosted URL is mandatory. It must state that message content is read on-device and delivered
only to destinations the user configures, that nothing is sent to the developer, and that there
is no analytics. A simple page on penducoder.com is enough.

### Data safety form

Answer it to match reality:

| Question | Answer for this app |
|---|---|
| Does the app collect or share user data? | No data is sent to the developer |
| Is data encrypted in transit? | Yes (TLS for Gmail API, webhooks, Telegram; STARTTLS for SMTP) |
| Can users request deletion? | Yes — history is local and can be cleared in the app |
| SMS/Call log access | Used on-device only, forwarded to user-chosen destinations |

### Store listing assets

* App icon 512×512 PNG
* Feature graphic 1024×500
* At least 2 phone screenshots (the dashboard health card and the rule editor make the case well)
* Short description (80 chars) and full description (4000)

---

## Release checklist

- [ ] Application ID decided and set — cannot be changed later
- [ ] Launcher icon replaced
- [ ] `versionCode` / `versionName` bumped in `app/build.gradle.kts`
- [ ] Own upload keystore created and backed up in two places
- [ ] `./gradlew clean bundleRelease` with the keystore environment variables set
- [ ] `./gradlew testDebugUnitTest lintDebug` pass
- [ ] Installed the release APK on a real phone and confirmed: a message forwards, the app
      survives a reboot, and the dashboard health card reports "working"
- [ ] Privacy policy published and the URL added
- [ ] Data safety form completed
- [ ] Permissions Declaration Form submitted
- [ ] Internal testing track first, not production

---

## Test it properly before you submit

The one thing a reviewer will try is leaving the app alone. Install the release build, set up a
rule, then:

1. Lock the phone and leave it for an hour. Send an SMS. It should still forward.
2. Reboot the phone, do not open the app, and send an SMS. It should still forward.
3. Turn off mobile data, send an SMS, then turn data back on. It should arrive late, not vanish.
4. Open the app and confirm the health card says **Background forwarding is working**.

If step 1 or 2 fails on your phone, the health card will tell you why — that is what it is for.
