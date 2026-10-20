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

## Build

You need JDK 17 and the Android SDK (platform 35).

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleRelease
```

The release APK is signed with the local debug key, since it is meant for sideloading.

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
