# Making "Connect Google account" work

**Short version:** it does not work because the app has never been registered with Google.
This is not a bug in the code — nothing in the APK identifies it to Google, so Play Services
refuses to hand it a Gmail token. Fixing it is a Google Cloud task, not a code change.

**The App password method already works and sends through the same mailbox.** If you are not
ready for the steps below, use that and leave one-tap Google for later.

---

## Why it fails

When the app calls `GoogleAuthUtil.getToken(account, "oauth2:.../auth/gmail.send")`, Play
Services looks for an **OAuth client registered in Google Cloud** whose *package name* and
*signing certificate SHA-1* match the app asking. There is no such client, so it refuses,
usually with `UNREGISTERED_ON_API_CONSOLE`. The app now shows that reason in plain words on the
Setup screen instead of failing silently.

Proof, from the built APK:

```
$ unzip -l app-release.apk | grep google-services
(nothing — no Google configuration is bundled)
```

The `firebase-applet-config.json` in the repo is scaffold left over from AI Studio. It holds a
**web** client for someone else's project (`diesel-ring-436005-s9`), which cannot authorise an
Android app. It is unused and can be deleted.

---

## What you have to do

### 1. Create a Google Cloud project

<https://console.cloud.google.com/> → new project, name it whatever you like.

### 2. Enable the Gmail API

**APIs & Services → Library → Gmail API → Enable.**

### 3. Configure the OAuth consent screen

**APIs & Services → OAuth consent screen**

* User type **External**
* App name, support email, developer contact
* **Scopes → Add** `https://www.googleapis.com/auth/gmail.send`
* **Test users →** add every Google account you want to test with

While the app is unverified, only accounts on that test-user list can connect, and the list is
capped at 100.

### 4. Get your signing certificate fingerprint

The OAuth client is tied to the certificate that signs the APK, so you need one client per key
you sign with — the debug key while developing, and the upload key for Play.

```bash
# Debug key (the one Android Studio uses)
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1

# Your upload key
keytool -list -v -keystore my-upload-key.jks -alias upload | grep SHA1
```

If you publish through Play, Google re-signs your app with its own key. Take that SHA-1 from
**Play Console → Setup → App signing** and register it as a third client, or Google sign-in
will work in your testing and fail for everyone who installs from the Store.

### 5. Create the Android OAuth client

**APIs & Services → Credentials → Create credentials → OAuth client ID → Android**

* **Package name:** `com.aistudio.smsforwarder.vknwpx`
  (or whatever you change `applicationId` to — they must match exactly)
* **SHA-1:** from step 4

Repeat for each fingerprint: debug key, upload key, and the Play app-signing key.

No file needs to be downloaded into the project. Play Services checks this registration at
runtime by package name and signature.

### 6. Rebuild and try again

```bash
./gradlew clean assembleDebug
```

Connect the account in **Setup**. The app now verifies the token immediately and tells you
whether Google accepted it, rather than waiting for the first real message to fail.

---

## The catch before you ship it

`gmail.send` is a **restricted scope**. Unverified, it is limited to your test users. To offer
it to the public, Google requires:

* OAuth app verification, and
* a **CASA security assessment** by an approved third party, because the scope reads and sends
  a user's mail.

That is a process measured in weeks, and the assessment is usually paid. Plan for it, or ship
with App password only.

### A cheaper alternative worth considering

If the goal is simply "send mail from the user's own Gmail without them creating an app
password", the honest options are:

| Approach | Works today | Cost |
|---|---|---|
| **App password** (current, working) | Yes | Free. User creates a 16-character password once. |
| **Gmail API with `gmail.send`** | After Cloud setup | Free for testing; verification + CASA to go public |
| **Your own SMTP relay** (SendGrid, Brevo, Mailgun) | Yes | Free tier, but mail comes from your domain, not the user's Gmail |

For a forwarder, App password is the pragmatic choice: it is one setup step for the user, it
needs no approval from anyone, and the mail genuinely comes from their own mailbox.

---

## Checklist

- [ ] Google Cloud project created
- [ ] Gmail API enabled
- [ ] Consent screen configured with the `gmail.send` scope
- [ ] Test users added
- [ ] Android OAuth client for the **debug** SHA-1
- [ ] Android OAuth client for the **upload** SHA-1
- [ ] Android OAuth client for the **Play app-signing** SHA-1 (before release)
- [ ] `applicationId` matches every client exactly
- [ ] Verification and CASA assessment planned, or shipping with App password only
