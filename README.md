# TV Settings

Per-app control and hidden settings for Fire TV and Android TV, built with Jetpack Compose and driven by a D-pad.

Features marked * need [Shizuku](https://shizuku.rikka.app/) running.

## Features

- **Apps:** filter, sort (including CPU and RAM) and search. Each app shows its Details, Actions, Permissions, VPN, services and activities.
- **Advanced:** system settings, privacy toggles, screensaver and appearance, location, mouse, Home redirect, APK install.
- **VPN dot:** a status dot in the corner of the screen.
- **Widget:** CPU and RAM for your launcher.

## Setup

1. Install the APK.
2. Start Shizuku, then authorise the app in Advanced → Status.
3. Optional: grant `WRITE_SECURE_SETTINGS` for secure-setting changes without Shizuku.

## Build

    ./gradlew assembleDebug

## Notes

- Amazon blocks ADB from the stick to itself, so Shizuku needs a second device as the ADB host.
- Per-app cache clearing is not included, since no safe shell command is verified.
