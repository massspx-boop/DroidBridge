# DroidBridge

**DroidBridge** is an Android app-transfer utility focused on moving installed APK sets and ordinary files between Android devices, then checking and installing the received package sets with Android's native package installer.

## Implemented capabilities
- Installed-app inventory with Android package-visibility permission.
- Export of an app's base APK and exposed split APK paths into a package-folder ZIP layout.
- Local Wi-Fi direct TCP transfer using a receiver-generated pairing PIN.
- SHA-256 verification at the receiver and acknowledgement back to the sender.
- APK metadata inspection: package ID, version, min SDK, target SDK, native ABI contents, signer certificate SHA-256 and installed-app signer comparison.
- Android PackageInstaller sessions for single APKs and multi-APK package ZIP bundles; Android confirmation remains required.
- Offline rule-based installation diagnosis.
- Optional OpenRouter AI diagnosis using a user-supplied API key encrypted with Android Keystore.
- File sharing via Android Sharesheet and Android Storage Access Framework.
- GitHub Actions debug build and artifact upload.

## Build
- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.9
- Run: `gradle --no-daemon :app:assembleDebug`

The Actions workflow builds the debug APK and publishes it as a workflow artifact on pushes to `main`.

## Transfer
1. Connect both phones to the same trusted Wi-Fi network.
2. On the receiving phone, press **Start Receiver** and note its IP and pairing PIN.
3. On the sending phone, enter `IP|PIN` (example: `192.168.1.12|123456`), press **Send File**, and choose a file.
4. The receiver saves it under the app-specific `DroidBridge-Received` directory and verifies SHA-256.

The current TCP transfer uses PIN pairing and integrity verification, but does not yet provide end-to-end encryption. Use only a trusted private network; do not use an open/public Wi-Fi network for sensitive files.

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
- ABI checks only inspect native library folders contained in the selected APK; device-specific runtime restrictions may still apply.
- The optional AI feature requires the user to supply their own API key and may incur provider charges. Without a key, offline diagnosis is used.

## Device target
Minimum Android 8.0 (API 26). The app uses a lightweight native UI and avoids bundling a large on-device language model, keeping low-RAM phones in mind.
