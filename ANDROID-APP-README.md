# Bosket's Alimentos — Android App

> Publisher / Developer / Owner: **Team Bosket's Alimentos**

A native Android app (hybrid shell) for the Bosket's Alimentos community website.
It shares the **same database as the website by design** — the app renders the live
site inside a native shell, so anything done in the app (login, profile changes,
recipes, wall posts, buddy requests, forum replies) is instantly visible on the
website and vice versa. There is no second database and no sync to break.

## What the native shell adds

- Branded **splash screen** and **adaptive launcher icon** (the artisan seal)
- **Pull-to-refresh**, loading progress bar, smooth in-app navigation
- **Photo/video uploads** from gallery **or camera** (recipe photos, avatars, steps)
- **Downloads** handled by Android's download manager
- **Share buttons** (WhatsApp / Facebook / X) open the real apps
- **Offline screen** with retry when there's no connection
- **Notification polling**: every ~30 minutes the app checks for unread community
  notifications (using your logged-in session) and shows an Android notification
  that opens the notifications page
- Back button navigates website history naturally; session cookies persist, so
  you stay logged in between launches
- Compatibility: **Android 7.0 (API 24) and newer** — ~98.5% of active devices

## Project layout

```
BosketsAlimentosAndroid/
  app/src/main/java/com/bosketsalimentos/app/
    MainActivity.kt        ← WebView shell, uploads, downloads, offline, URL config
    NotificationWorker.kt  ← background unread-notification polling
    App.kt                 ← notification channel + work scheduling
    ServerConfig.kt        ← which server the app talks to
  app/src/main/res/        ← icons, splash, layout, strings, themes
  boskets-release.keystore ← release signing key  ← KEEP THIS SAFE + BACKED UP
  keystore.properties      ← signing credentials (do not share publicly)
```

## Pointing the app at your website

The app needs the website's address. Two ways:

1. **No rebuild (easiest):** install the APK, and on first launch the app asks
   for the server address. Enter `https://yourdomain.com` (after deploying to
   Hostinger) — or `http://<your-PC-IP>:8088` to test against the local site
   (phone and PC must be on the same Wi-Fi, and the PHP server must listen on
   the network: `php -S 0.0.0.0:8088 -t <website folder>`).
   You can change it later from the offline screen → **Change server address**.

2. **Bake it in:** edit one line in `app/build.gradle.kts`:
   `buildConfigField("String", "SITE_URL", "\"https://yourdomain.com\"")`
   then rebuild (below). Users then never see the address prompt.

## Building the APK

Everything needed is installed under `D:\Ketul\AndroidBuildTools`
(JDK 17, Android SDK 34, Gradle 8.9). To rebuild:

```powershell
$env:JAVA_HOME   = 'D:\Ketul\AndroidBuildTools\jdk17'
$env:ANDROID_HOME = 'D:\Ketul\AndroidBuildTools\sdk'
& 'D:\Ketul\AndroidBuildTools\gradle\bin\gradle.bat' -p 'D:\Ketul\Claude_Projects\BosketsAlimentosAndroid' assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk` — a **signed, installable
APK with all dependencies bundled** (also copied to the project root as
`BosketsAlimentos-v1.0.0.apk`).

## Installing on a phone (sideload)

1. Copy the APK to the phone (USB, Google Drive, WhatsApp-to-self, etc.)
2. Tap it → Android asks to allow installs from that source → allow → Install.
3. First launch: enter the server address (see above), sign in, done.

## Signing key — important

`boskets-release.keystore` (alias `boskets`, password in `keystore.properties`,
certificate owner *Team Boskets Alimentos*, valid 30 years) signs every release.
**Back it up.** Updates must be signed with the same key, or users would have to
uninstall/reinstall. If you ever publish to Google Play, you'll enroll this key
(or let Play App Signing wrap it) under your Play Console account.

## Play Store later (optional)

- Build an AAB instead: `gradle bundleRelease`
- You'll need a Google Play developer account ($25 one-time) under
  "Team Bosket's Alimentos", a privacy policy URL (a simple page on the website
  works), and store listing assets (icon is ready; add screenshots).
