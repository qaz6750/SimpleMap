# SimpleMap

A Kotlin and Jetpack Compose navigation app for Android, designed around the AMap Android navigation SDK.

## Features

- Privacy-gated AMap initialization with persisted consent.
- Full-screen map home, floating bottom navigation, traffic, satellite, location, and zoom controls.
- POI search, place details, persistent favorites, and map markers.
- Driving, transit, cycling, and walking route comparison with map polylines.
- Custom real-time GPS navigation UI with voice guidance, traffic display, overview, and route alerts.
- Blue high-contrast navigation guidance with custom maneuver, map, route, trip, and profile icons.
- Trip history, one-tap route reuse, navigation preferences, network state, and real offline city packages.

The complete four-screen product preview is available in [docs/simplemap-ui-preview.svg](docs/simplemap-ui-preview.svg). Navigation states including normal guidance, manual map browsing, service-area and interval-camera alerts, weak GPS, and rerouting are shown in [docs/navigation-scenes-preview.svg](docs/navigation-scenes-preview.svg).

## Local setup

1. Install JDK 17 and Android SDK Platform 35.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` and `AMAP_API_KEY` in `local.properties`.
4. Run `./gradlew assembleDebug`.

The public Maven repositories do not currently publish the AMap LLM Agent SDK coordinate documented by the bundled Android LLM Agent Skill (`com.amap.lbs.client:amap-agent:1.1.41`). Obtain the compatible AAR and repository credentials from AMap before enabling Agent queries. Do not add separate `navi-3dmap` or `location` dependencies: this project deliberately uses the existing aggregate navigation artifact as the only map, navigation, location, and search implementation.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
The minified, resource-shrunk release APK is generated at `app/build/outputs/apk/release/app-release-unsigned.apk`. The app targets `arm64-v8a`, matching the complete native library set shipped by the current AMap navigation artifact.

The AMap key is injected into the manifest and `BuildConfig`; it must never be committed. AMap SDK initialization must happen only after the user accepts the privacy notice.

Favorites, trip history, navigation settings, location traces, and privacy consent are excluded from Android cloud backup and device transfer. Offline city packages are managed by the AMap offline map manager and remain usable when validated network connectivity is unavailable.

## Continuous integration

`.github/workflows/android-ci.yml` runs unit tests, Android Lint, dependency validation, and Debug/Release builds on pushes and pull requests. It uploads both APKs and the Lint HTML report. Instrumented navigation tests require an ARM64 device or device lab because the AMap native navigation engine is not available for the standard x86_64 GitHub emulator.

## Architecture

The app uses a single Compose activity. AMap's `MapView` and `AMapNaviView` are hosted through lifecycle-aware adapters, while search, routing, navigation, favorites, trip history, settings, and offline maps are exposed to Compose through feature-level immutable models.