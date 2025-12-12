# Captive Portal Auto Login

> The only way to deal with an unfree world is to become so absolutely free that your very existence is an act of rebellion.
>
> — Albert Camus

Detect captive portals and automatically get liberated on Android and Linux.

## Android app

Features:

- Foreground service watching network state and trying to log in when a portal is detected.
- Optional Xposed integration to hint Android to re‑evaluate/accept the connection after liberation.
- Settings screen.
- Export for submitting new portals.

### Installation

1. Download the APK from
   - ~~[GitHub Releases](https://github.com/binarynoise/CaptivePortalAutoLogin/releases)~~
   - [the Telegram channel](https://t.me/+__MmjOzaVOw3MDc6)
2. Install the APK on your Android device
3. Grant the required permissions

### Usage

#### Starting the Service

1. Open the app
2. Toggle on the **Service Status** switch to enable the background monitoring service
3. The service will automatically detect and liberate captive portals when you connect to Wi-Fi networks

#### Manual Liberation

You can manually trigger a liberation attempt:

1. Open the app or view the notification
2. Tap **"Liberate me now"** to attempt liberation on the current network
3. The notification will show whether the attempt was successful

#### Capturing Portal Information

To help add support for new portals:

1. Connect to a captive portal network
2. Open the app and tap **"Capture portal"**
3. Liberate the portal manually
4. Upload the captured data

## Linux service

The Linux module is a JVM CLI that listens to NetworkManager via `nmcli` and runs the liberator when connectivity
changes to `portal`.

Build a fat jar:

- `./gradlew :linux:shadowJar`
- Artifact: `linux/build/libs/linux-shadow.jar`

Run:

- One‑shot check: `java -jar linux/build/libs/linux-shadow.jar --oneshot`
- As a service (keeps monitoring): `java -jar linux/build/libs/linux-shadow.jar --service`
- Options:
    - `--force`: run without checking connectivity first (implies one‑shot)
    - `--experimental`: enable experimental/incomplete portal handlers
    - `--restartNetworking`: toggle NetworkManager off/on at start;
    - at runtime: press `r` to restart networking, `q` to quit

## Roadmap

- Android
    - [x] UI (Settings screen)
    - [x] Background service
    - [ ] Notify the Android system about success
        - [x] Xposed
        - [ ] Hijack `android.net.conn.CAPTIVE_PORTAL` action
            - <https://cs.android.com/android/platform/superproject/+/android-latest-release:packages/modules/CaptivePortalLogin/src/com/android/captiveportallogin/CaptivePortalLoginActivity.java;l=985;drc=6de39e8e8b1228d1630121c65b5a172610394526>
            - <https://cs.android.com/android/platform/superproject/+/android-latest-release:packages/modules/Connectivity/framework/src/android/net/CaptivePortal.java;l=125;drc=7d6dfd52b270dd26cbc26f4e6f93365af1aabd68>
            - <https://cs.android.com/android/platform/superproject/+/android-latest-release:packages/modules/CaptivePortalLogin/AndroidManifest.xml;l=69;drc=ec150a8c6585a59e12fafd3b9de785c8d0c83139>
    - [ ] Replace all Toasts with updating the status notification
    - [x] Proper permission handling
    - [x] Export and upload HARs
    - [ ] Dynamically load new liberator versions
    - [x] Sign APK

- Android light version
    - [ ] Only service and permissions

- Linux service
    - [x] NetworkManager integration
    - [ ] Other network managers?

- Windows service?

- Frontend common
    - [ ] Retry Liberation with backoff
    - [x] Collect metrics
        - [x] Portal URL
        - [x] SSID
        - [x] Timestamp
        - [x] Success
        - [x] Error message
        - [ ] Exception
        - [ ] HTTP logs
        - [ ] Location?
    - [ ] Better icon
    - [ ] Proper logging (e.g. https://tinylog.org/v2/)

- Backend
    - [x] Collect metrics

- Misc
    - [ ] More quotes

## Contributing

### HAR files

HAR files contain the HTTP traffic needed to liberate a portal.
If you find a new portal, please capture the traffic (via app or browser: Chrome or Firefox DevTools)
and submit it (via app, Telegram, or GitHub; will contain all personal information you send to the portal).
This will help me add support for it. 

### Code

Portal support lives under `liberator/src/main/kotlin/de/binarynoise/liberator/portals`. To add a portal:

- Copy the `_Template` class and rename it.
- Implement `PortalLiberator` with `canSolve(locationUrl)` and `solve(...)` methods.
- Optionally annotate with `@SSID("ssid1", "ssid2", ...)`.
- Submit a Pull Request.
