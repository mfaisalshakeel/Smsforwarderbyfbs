# Room entities are constructed reflectively by the generated DAO/adapter code.
-keep class com.example.data.local.entity.** { *; }

# Settings model is persisted field-by-field; keep names stable.
-keep class com.example.data.preferences.ForwarderSettings { *; }

# WorkManager instantiates workers by class name.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# Manifest-declared components.
-keep class com.example.receiver.** { *; }
-keep class com.example.service.** { *; }

# Google Play Services auth (reflection inside GoogleAuthUtil / GoogleSignIn).
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.**

# OkHttp / Okio ship their own rules but these silence the optional deps.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
