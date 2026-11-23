# DroidBridge

DroidBridge is a lightweight Android APK and file sharing starter app, designed for Android 8.0+ and low-memory devices.

## Current features
- Browse installed applications (subject to Android package visibility and platform restrictions).
- Export installed app APK files, including split APK files when Android exposes them, into a ZIP bundle.
- Share any file through Android's Sharesheet.
- Share exported APK bundles through installed transfer apps.
- No account, cloud server, or analytics.

## Build
Use JDK 17, Android SDK 35 and Gradle 8.9:
`gradle --no-daemon :app:assembleDebug`

GitHub Actions builds a debug APK on pushes to main and uploads it as an artifact.

## Important Android limitations
- Android requires user approval to install APKs. This starter does not silently install apps.
- Exporting private app data is not supported; Android app sandboxing prevents ordinary apps from reading another app's private data.
- Some Play-distributed apps use dynamic features or assets that are not present in locally exposed APK paths. This ZIP export cannot reproduce Play Store delivery in every case.
- A split APK bundle must be installed as a set, not by installing only one split file.
- Package visibility and access to installed app metadata are governed by Android and may vary by OS version and store policy.

Minimum Android: 8.0 (API 26).
