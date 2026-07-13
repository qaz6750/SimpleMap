# SimpleMap

A Kotlin and Jetpack Compose navigation app for Android, designed around the AMap Android navigation SDK.

## Local setup

1. Install JDK 17 and Android SDK Platform 35.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` and `AMAP_API_KEY` in `local.properties`.
4. Run `./gradlew assembleDebug`.

The AMap key is injected into the manifest and `BuildConfig`; it must never be committed. AMap SDK initialization must happen only after the user accepts the privacy notice.

## Architecture

The app uses a single Compose activity. AMap's `MapView` and `AMapNaviView` are hosted through lifecycle-aware adapters, while search, routing, navigation, favorites, and trip history are exposed to Compose through feature-level state holders.