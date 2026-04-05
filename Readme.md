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

1. Download the APK from [GitHub Releases](https://github.com/binarynoise/CaptivePortalAutoLogin/releases)
2. Install the APK on your Android device
3. Grant the required permissions

### Usage

#### Starting the Service

1. Open the app
2. Grant permissions
3. Toggle on the **Service Status** switch to enable the background monitoring service
4. The service will automatically detect and liberate captive portals when you connect to Wi-Fi networks

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

## Contributing Portals

### HAR files

HAR files contain the HTTP traffic needed to liberate a portal.
If you find a new portal, please capture the traffic (via app or browser: Chrome or Firefox DevTools)
and submit it (via app, Telegram, or GitHub; will contain all personal information you send to the portal).
This will help me add support for it.

### Code

Portal support lives under `liberator/src/main/kotlin/de/binarynoise/liberator/portals`. To add a portal:

- Copy the `_Template` class and rename it.
- Implement `PortalLiberator` with `canSolve(...)` and `solve(...)` methods.
- Optionally annotate with `@SSID("ssid1", "ssid2", ...)`.
- Submit a Pull Request.
