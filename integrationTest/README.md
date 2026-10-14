# Integration tests

These tests install the app on every emulator of the local machine, one after another, and check that it
liberates a captive portal from start to finish: onboarding, permissions, captive portal detection, the
solver and the statistics upload. Every emulator is tested with the debug and the release apk.

## Running them

```bash
./gradlew :integrationTest:integrationTest
```

The api server url is managed by the test run itself: the test starts its own api server and hands its url
to the app under test through a global setting that only adb shell can write. Both the debug and the release
apk are built the way they are shipped, with their compiled in url as a fallback only. The tests themselves keep
running on the default `test` task out of the way, they only run on `integrationTest` because they need
emulators and take a long time.

Useful options:

| Option               | Description                                          |
|----------------------|------------------------------------------------------|
| `-Pavds=...`         | only run these emulators, comma separated            |
| `-PbuildTypes=debug` | only run these build types, `debug` and/or `release` |
| `-PskipAvds=...`     | skip these emulators, comma separated                |

Emulators that cannot run the app are skipped and reported as such: images below the `minSdk`.
Further emulators can be left out with `-PskipAvds=...`.
Nothing is skipped by default on purpose, so that a broken device never goes unnoticed.

## How a single test works

For each emulator and build type:

1. `portalProxy` runs in the test process on the host machine.
   It answers the captive portal probe with a redirect to its portal page while the client is captured, and
   with `204 No Content` once the client logged in.
2. The emulator gets `http://10.0.2.2:8000/` as its captive portal url and `10.0.2.2:8000` as its system proxy.
   The proxy is needed because the app resolves `portal.binarynoise.de` through it, which is the only portal
   `BinarynoisePortalProxy` can solve. The Wi-Fi is cycled afterwards, because the system only probes for
   captive portals while it validates a network for the first time.
3. The api server of this repository runs inside the test process with a database of its own. The app reaches
   it through `adb reverse`, so it stays reachable while the app is behind the proxy.
4. The app is installed, pointed at the api server of this run through a global setting, and the test
   walks through the onboarding by tapping what a user would tap, answering every permission dialog.
5. Both build types enable the experimental portal liberators and press `Liberate me now`, because
   the liberators are disabled by default and only they can solve this portal.
6. The test waits until the app logged in at the portal, until the system stops reporting a captive portal,
   and, on debug builds, until the app reported the liberation to the api server. Release builds strip
   every `Log` call and report on their hourly schedule, which the test does not wait for. Scheduled jobs
   are run by hand, the app would otherwise upload its statistics hours later.

Emulators, logcat dumps and the portal proxy log are written to `build/integrationTest/`.
