# SimpleMap

[中文](README.md) | **English**

A native Android map app covering place search, route planning, live navigation, and trip review. SimpleMap is built with Kotlin, Jetpack Compose, Material 3, and the AMap Android Navigation SDK, with dedicated portrait phone and landscape vehicle layouts.

> [!IMPORTANT]
> You need your own AMap Android key. The native navigation dependency currently packages `arm64-v8a` only, so live map and navigation verification requires an ARM64 physical device or compatible device cloud.

## Core Capabilities

- Fuzzy place search, recent searches, map point selection, favorite groups, and nearby search.
- Driving, transit, cycling, and walking route comparison, with driving waypoints and preferences.
- Portrait and landscape navigation with junction views, lanes, traffic, speed limits, facilities, and GPS status.
- Foreground service, navigation notifications, Android 16+ Live Updates, and an optional floating navigation card.
- Trip summaries, parking location, offline city packages, and local data management.
- 2D / 3D perspective, north reset, live traffic, satellite imagery, and day/night themes.

## Privacy Boundary

SimpleMap does not create or call any AMap map, location, search, or navigation API until the user has explicitly agreed and that consent has been persisted. Users can clear local data or revoke consent at any time.

Android cloud backup and device transfer are disabled. Favorites, settings, search history, parking location, and trip summaries remain on-device; trip history and GPS diagnostics do not store trace points.

## Quick Start

### Requirements

- JDK 17
- Android SDK Platform 37 and Build Tools 36.0.0
- An AMap Android key bound to `com.simplemap` and the signing identity
- An authorized ARM64 Android device for live navigation verification

### Configuration

```bash
git clone https://github.com/qaz6750/SimpleMap.git
cd SimpleMap
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
AMAP_API_KEY=your_android_key
```

`local.properties` is ignored by Git. Never commit a real key, signing file, location record, or user data.

### Build and Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat assembleDebug`. Accept the in-app privacy agreement on first launch before map and location capabilities initialize.

## Verification and Release

```bash
# Unit tests, Lint, Debug APK, and Android test APK
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest

# Release APK and AAB
./gradlew assembleRelease bundleRelease

# Device regression with one authorized ARM64 device connected
ADB="$ANDROID_HOME/platform-tools/adb" ./scripts/device-regression.sh all
```

See the [device regression checklist](docs/device-regression.md) for physical-device checks. GitHub Actions provides [automated verification](.github/workflows/android-verify.yml), [manual builds](.github/workflows/android-manual-build.yml), and [releases](.github/workflows/android-release.yml).

## Technology

| Area | Version or implementation |
| --- | --- |
| Language and UI | Kotlin 2.3.21, Jetpack Compose, Material 3 |
| Android | minSdk 26, compileSdk / targetSdk 37 |
| Build | Gradle 9.5.0, Android Gradle Plugin 9.3.0, JDK 17 |
| Map and navigation | AMap `navi-3dmap-location-search` 11.2 aggregate dependency |
| Architecture | Single Activity, immutable UI state, unidirectional data flow, lifecycle-aware View adapters |
| Local storage | SharedPreferences for settings, favorites, search history, and trip summaries |

## Known Limitations

- Only `arm64-v8a` is packaged; the AMap native navigation engine cannot run on a standard x86_64 emulator.
- Map, search, routing, and navigation verification require a valid key, network access, and a compatible device.
- The project uses only the AMap aggregate dependency; do not add duplicate map, location, search, or navigation SDK artifacts.
- Release outputs are unsigned by default; configure a separate signing identity before distribution.
- Persistent navigation may be affected by vendor battery policies and should be verified on target devices.