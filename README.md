# SimpleMap

A Kotlin and Jetpack Compose navigation app for Android, designed around the AMap Android navigation SDK.

## Features

- Privacy-gated AMap initialization with persisted consent.
- Full-screen map home, floating bottom navigation, traffic, satellite, location, and zoom controls.
- POI search, place details, persistent favorites, and map markers.
- Driving, transit, cycling, and walking route comparison with map polylines.
- Custom real-time GPS navigation UI with voice guidance, traffic display, overview, and route alerts.
- Trip history, one-tap route reuse, navigation preferences, network state, and real offline city packages.

The four-screen visual direction is available in [docs/simplemap-ui-preview.svg](docs/simplemap-ui-preview.svg).

## Local setup

1. Install JDK 17 and Android SDK Platform 35.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` and `AMAP_API_KEY` in `local.properties`.
4. Run `./gradlew assembleDebug`.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The AMap key is injected into the manifest and `BuildConfig`; it must never be committed. AMap SDK initialization must happen only after the user accepts the privacy notice.

Favorites, trip history, navigation settings, location traces, and privacy consent are excluded from Android cloud backup and device transfer. Offline city packages are managed by the AMap offline map manager and remain usable when validated network connectivity is unavailable.

## Architecture

The app uses a single Compose activity. AMap's `MapView` and `AMapNaviView` are hosted through lifecycle-aware adapters, while search, routing, navigation, favorites, trip history, settings, and offline maps are exposed to Compose through feature-level immutable models.