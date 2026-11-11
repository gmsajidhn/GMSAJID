# Sajid's Study Tracker

অ্যালার্ম, স্টাডি টাইমার ও বিল্ট-ইন ব্রাউজারসহ একটি Android অ্যাপ।

- Package: `com.sajid.studytracker`
- Version: `72.54.3` (versionCode 14)
- Min SDK 24 · Target SDK 34 · Kotlin + WebView

## APK বানানোর নিয়ম (GitHub Actions)

1. এই রিপোর সব ফাইল আপলোড করো (`.github` ফোল্ডারসহ)।
2. **Actions** ট্যাব → **Build APK** → (দরকার হলে **Run workflow**)।
3. বিল্ড শেষ (সবুজ ✔) হলে রানের পেজের নিচে **Artifacts** থেকে `SajidStudyTracker-APK` ডাউনলোড করো।
4. জিপ খুলে `SajidStudyTracker.apk` ফোনে ইনস্টল করো।

> `main`/`master` ব্রাঞ্চে প্রতিবার পুশ করলে নিজে থেকেই বিল্ড হয়।

## আপডেট ইনস্টল (আনইনস্টল ছাড়া)

`app/sajid.keystore` দিয়ে সবসময় একই কী-তে সাইন হয়, তাই নতুন APK পুরোনোটার উপর আপডেট হয়ে বসে।
এই ফাইলটা মুছো না বা বদলিও না।

## লোকাল বিল্ড

JDK 17 + Android SDK থাকলে:

```bash
gradle assembleDebug
# আউটপুট: app/build/outputs/apk/debug/app-debug.apk
```

## ফোল্ডার কাঠামো

```
.github/workflows/build.yml   ← অটো বিল্ড ওয়ার্কফ্লো
app/                          ← অ্যাপের কোড (Kotlin, assets/index.html, res)
build.gradle.kts
settings.gradle.kts
gradle.properties
```

## নিরাপত্তা নোট

কিস্টোরের পাসওয়ার্ড `app/build.gradle.kts`-এ লেখা আছে। তাই রিপোটা **Private** রাখাই ভালো।
Public করলে অন্য কেউ একই কী দিয়ে সাইন করতে পারবে।
