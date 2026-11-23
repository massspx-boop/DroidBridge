# DroidBridge

**DroidBridge** is an Android app-transfer utility for moving installed APK sets and ordinary files between Android devices, checking package compatibility, and installing received APK sets with Android's native package installer.

## Implemented capabilities
- Installed-app inventory with Android package-visibility permission.
- Export of an app's base APK and exposed split APK paths into a package-folder ZIP layout.
- Local Wi-Fi TCP transfer with an 8-digit receiver-generated pairing code.
- Wi-Fi Direct group creation, peer discovery and connection workflow.
- Foreground transfer service to keep the receiving socket alive while the app is backgrounded.
- PIN-derived AES-256-CTR encryption, HMAC-SHA256 authentication/integrity, and receiver acknowledgement.
- Independent SHA-256 file verification after transfer.
- APK metadata inspection: package ID, version, min SDK, target SDK, native ABI contents, signer certificate SHA-256 and installed-app signer comparison.
- Android PackageInstaller sessions for single APKs and multi-APK package ZIP bundles; Android confirmation remains required.
- Offline rule-based installation diagnosis.
- Optional OpenRouter AI diagnosis using a user-supplied API key encrypted with Android Keystore.
- File sharing via Android Sharesheet and Android Storage Access Framework.
- GitHub Actions debug build, unit tests, and artifact upload.

## Build
- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.9
- Run: `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`

GitHub Actions runs unit tests, builds the debug APK and publishes it as a workflow artifact on pushes to `main`.

## Transfer
1. Use the same trusted Wi-Fi network, or create a Wi-Fi Direct group on the receiving phone and discover/connect from the sending phone. On Android 11 and earlier, location permission and the device Location toggle may be required for peer discovery.
2. On the receiving phone, press **Start Receiver** and note its IP and 8-digit pairing code. The app keeps the receiver active with a foreground notification.
3. On the sending phone, enter `IP|PIN` (example: `192.168.1.12|12345678`); after Wi-Fi Direct connects, the group-owner IP is filled automatically. Press **Send File** and choose a file.
4. Both sides derive session keys from the pairing code and a random challenge/salt. AES-256-CTR encrypts the stream and HMAC-SHA256 authenticates it in transit.
5. The receiver verifies the decrypted file's SHA-256 and returns an acknowledgement before the sender reports success.

Use a trusted private Wi-Fi network. The pairing code is temporary and should only be shared with the intended receiving phone.

## APK bundle layout
Each exported package is stored as:
```
package.name/
  base.apk
  split_1.apk
  split_2.apk
```
The installer uses Android PackageInstaller sessions. Split sets must be complete and from the same package/signing identity.

## Platform limits (not bypassed)
- Android requires user confirmation and the per-app "Install unknown apps" permission for sideloading.
- Ordinary apps cannot extract another app's private sandbox data.
- Play Store dynamic feature modules, expansion files, on-demand assets, DRM-protected resources, and server-delivered content may not be available from local APK paths.
- Some apps require account, device certification, Play Integrity, licensing, or online activation after installation.
- ABI checks inspect native library folders contained in the selected APK; device-specific runtime restrictions may still apply.
- The optional AI feature requires the user to supply their own OpenRouter API key and may incur provider charges. Without a key, offline diagnosis is used.
- OBB/data folders belonging to other apps are restricted by Android scoped storage and are not silently copied.

## Device target
Minimum Android 8.0 (API 26). The app uses a lightweight native UI and avoids bundling a large on-device language model, keeping low-RAM phones in mind.
