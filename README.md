# lite-tv-launcher

A tiny home screen for low-RAM Google TV / Android TV sets. No ads, no recommendation rows, no dependencies, and an APK of about 25 KB.

Built for a TCL 32V4C (MediaTek MT5867, 1 GB RAM, Android 14 / Google TV). On that TV it uses about 20 MB of RAM, compared with about 107 MB for the stock Google TV home screen.

## Features

- Clock and date at the top, with **Inputs** (HDMI/source menu) and **Settings** shortcuts
- A 5-column grid of every installed TV app, using each app's banner
- Long-press OK on an app to move it, hide it, open App info (force stop, clear cache) or uninstall it
- Hidden apps are kept under a "Hidden apps" tile at the end of the grid
- Pressing Home while already home jumps back to the first app

The first-run order and hidden set are in `DEFAULT_ORDER` and `DEFAULT_HIDDEN` in `HomeActivity.java`.

## Download

Every release has a ready-to-install APK. This link always points to the newest one:

https://github.com/mannam95/lite-tv-launcher/releases/latest/download/lite-tv-launcher.apk

To install it without a computer:

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Open it and enter the link above. When asked, allow Downloader to install unknown apps.
3. Install the APK. Lite TV now appears in your app list.

To make it the home screen you still need a computer once. See [Install on the TV](#install-on-the-tv-over-wi-fi).

## Releasing

Push a version tag. CI builds a signed APK and publishes a GitHub release:

```sh
git tag v1.0.1 && git push origin v1.0.1
```

CI signs with the keystore stored in the repo secrets `LTV_KEYSTORE_BASE64`, `LTV_KEYSTORE_PASSWORD`, `LTV_KEY_ALIAS` and `LTV_KEY_PASSWORD`. Every release must use the same key, or installed copies can't update.

## Build

You need JDK 17 and the Android SDK (platform 35).

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleRelease
```

Without the `LTV_*` signing env vars, local release builds are signed with the debug key. They can't update a copy installed from a GitHub release, so uninstall that copy first.

## Install on the TV (over Wi-Fi)

1. On the TV, open **Settings → System → About** and press OK on **Android TV OS build** 7 times.
2. Open **Developer options** and turn on **Wireless debugging**. Choose **Pair device with pairing code**.
3. On the laptop:

```sh
adb pair <tv-ip>:<pairing-port> <code>
adb connect <tv-ip>:<port>
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity com.litetv.launcher/.HomeActivity
```

On Google TV, the stock home screen is a privileged system app and wins the Home button anyway. To make Lite TV the home screen, disable the stock one and the setup app that restores it:

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

## Undo

```sh
adb shell pm enable com.google.android.tungsten.setupwraith
adb shell pm enable com.google.android.apps.tv.launcherx
adb uninstall com.litetv.launcher
```

## Troubleshooting

- If Gradle downloads time out on a network with broken IPv6, run the build with `GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"`.
