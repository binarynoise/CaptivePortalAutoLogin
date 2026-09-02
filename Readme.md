# Captive Portal Auto Login

> The only way to deal with an unfree world is to become so absolutely free that your very existence is an act of rebellion.
>
> — Albert Camus

Detect captive portals and automatically get liberated on Android and Linux.

[![GitHub](https://img.shields.io/badge/GitHub-releases-green?logo=github)](https://github.com/binarynoise/CaptivePortalAutoLogin/releases/latest)
[![Telegram Channel](https://img.shields.io/badge/Telegram-channel-blue?logo=telegram&type=plastic)](https://t.me/captiveportalautologin)
[![Telegram Group](https://img.shields.io/badge/Telegram-group-blue?logo=telegram)](https://t.me/+a5Kj_MA-OGoyN2My)

## Android app

Features:
- A background service waits for networks with portals
  and tries to solve them automatically
- Record and submit new portals

Required Permissions:
- `Notifications` for [running in the background](#persistent-notification)
- `Location` for obtaining names (SSIDs) of the Wi-Fi networks

### Installation

1. Download the APK from [GitHub Releases](https://github.com/binarynoise/CaptivePortalAutoLogin/releases)
    - `arm64` should work on almost all phones, _use this if unsure_
    - `arm` is mainly useful for old phones
    - `universal` works everywhere
1. Install the APK on your Android device
1. Follow the instructions to set up the app

### Usage

#### Starting the Service

1. Open the app
1. A [persistent notification](#persistent-notification) will show up indicating the service is running
1. The service will automatically detect and liberate captive portals when you connect to Wi-Fi networks

#### Automatically Connect to Supported Networks

You can have your device automatically connect to supported networks.

1. Open the app
1. Enable "**Network Suggestions**"
1. Click allow on androids network suggestions popup

The app only sends a list of supported networks to Android,
we have no control over when and if Android actually decides to connect to them.

#### Manual Liberation

You can manually trigger a liberation attempt:

1. Open the app's advanced settings or view the notification
1. Tap "**Liberate me now**" to attempt liberation on the current network
1. The notification will show whether the attempt was successful

#### Capturing Portal Information

To help add support for new portals:

1. Connect to a captive portal network
1. Open the app and tap **"Capture Captive Portal Login"** 
   or click on Android's "**Sign in to network**" and select "**CaptivePortalAutoLogin**"
1. Liberate the portal manually
1. Confirm the popup to upload the captured data

#### Persistent Notification

In order to run in the background, 
[Android requires us to show a persistent notification](https://developer.android.com/develop/background-work/services/fgs).
On modern Android versions this notification can be swiped away,
but it will re-appear with the next service start.
Many Android variants allow the user to only disable the notification category "Persistent Notification", 
but don't disable the notifications of the entire app, 
because that would make the app dysfunctional.

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

## Contributing a portal implementation

Portal support lives under `liberator/src/main/kotlin/de/binarynoise/liberator/portals`. 

To add a portal:
- Copy the `_Template` class and rename it.
- Implement `PortalLiberator` with `canSolve(...)` and `solve(...)` methods.
- Optionally annotate with `@SSID("ssid1", "ssid2", ...)`.
- Test your implementation in the wild.
- Submit a Pull Request.
