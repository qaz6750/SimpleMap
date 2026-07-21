# SimpleMap

[中文](README.md) | English

A native Android map and navigation app built with Kotlin, Jetpack Compose, Material 3, and the AMap Android Navigation SDK.

## Features

- Persisted privacy consent gates every AMap SDK call — no map, search, location, or navigation API is invoked before explicit user consent.
- Full-screen map home with top search bar, floating bottom bar, real-time traffic, satellite imagery, and location/zoom controls.
- POI and transit search, nearby place sorting, place details, map markers, and local favorites (Home, Work, and custom collections). Tap a place to plan a route directly.
- Driving, transit, cycling, and walking route comparison. Driving waypoints can be reordered; presets (Commute, Highway-First, EV Energy-Saving) and manual preferences (avoid congestion, avoid highways, minimize tolls) persist on-device. The route planner hides the global bottom navigation while keeping the map route visible.
- Real-time GPS navigation with built-in voice guidance, live traffic, route overview, and automatic rerouting on deviation or congestion. When AMap constraints are met, alternative routes updated periodically by the SDK can be viewed and switched via `selectMainPathID(pathId)`. A foreground Service holds the live navigation session so guidance continues after the Activity is swiped away, with notification actions to return or end navigation.
- Adaptive portrait (phone) and landscape (vehicle) navigation with a unified blue theme. Official AMap junction enlarged views display in full; compact layouts preserve independent turn-distance and road-name hierarchy. `showLaneInfo` provides real lane directions and recommended lanes highlighted in blue. Speed, speed limit, interval-camera remaining distance, and suggested speed are always visible. Routes are colored by live traffic; safety events use a single priority slot. De-duplicated voice alerts fire on first congestion, worsening, or easing; ETA shifts of ±3 minutes trigger a one-time reminder and reset the baseline. Weak-GPS mode fuses SDK signal callbacks, satellite count, and accuracy to distinguish disabled system location, weak signal, low-accuracy drift, and prolonged route mismatch — no coordinates or traces are stored. Route event toasts (restrictions, congestion avoidance, route updates, waypoint arrival) auto-dismiss. Tapping the roadside facility card opens a direction-adaptive floating panel listing all service areas and toll stations along the route. GPS details follow the same floating-panel rules and auto-close after 5 seconds. Navigation settings offer Follow System, Time-Based, Always Day, and Always Night modes.
- Automatic day/night theming follows the system, a schedule, or a fixed setting, and temporarily switches to the AMap night basemap when entering a tunnel. Navigation voice supports Detailed, Concise, and Mute levels with a custom cross-midnight quiet period and an independent critical-alert voice toggle.
- Follow, Settings, and End controls appear only after manual map panning; steady-state navigation stays compact.
- On arrival, search for parking within 3 km of the destination, save a single local parking spot, and plan a walking route back. Trip history records real duration, distance, average speed, and final state (arrived, cancelled, failed), clearly marking simulated navigation. Trip summaries are stored on-device only and contain no trace points. One-tap route reuse, navigation preference recall, and AMap offline city packages with capacity stats and Wi-Fi-only download policy are supported.
- Location permission is requested on demand, with shortcuts to system permission settings, local data clearing, and privacy consent revocation.

UI previews: [four-screen overview](docs/simplemap-ui-preview.svg), [theme & voice settings](docs/theme-voice-settings-preview.svg), [route enhancements](docs/route-enhancements-preview.svg), [contextual search](docs/contextual-search-preview.svg), [favorite places](docs/favorite-places-preview.svg), [portrait navigation](docs/navigation-portrait-preview.svg), [landscape vehicle navigation](docs/navigation-junction-landscape-preview.svg), [weak GPS & night mode](docs/navigation-gps-night-preview.svg), [compact & large-font navigation](docs/navigation-compact-layout-preview.svg), [persistent navigation & trip review](docs/persistent-navigation-trips-preview.svg), [offline download policy](docs/offline-download-policy-preview.svg), [privacy & data controls](docs/privacy-data-controls-preview.svg). Preview maps and turn symbols illustrate layout only; in real navigation, turn icons come from AMap `NaviInfo.iconBitmap` and traffic/route data from SDK callbacks.

AMap Android Navigation SDK 11.2 publicly exposes `setMultipleRouteNaviMode`, `getNaviPaths`, `selectMainPathID`, `displayOverview`, `onGpsSignalWeak`, and destination-centered `PoiSearch.SearchBound`; this project calls them only within their documented constraints. Multi-route navigation requires real-time driving, a multi-path strategy, no waypoints, and a straight-line origin–destination distance ≤ 80 km. The public SDK has no traffic-event reporting endpoint — `onUpdateDriveEvent` and `onNaviRouteNotify` are downstream notifications, not upload interfaces; event reporting will not be implemented until AMap provides commercial authorization and a formal API.

## Build

1. Install JDK 17 and Android SDK Platform 35.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` and `AMAP_API_KEY` in `local.properties`.
4. Run `./gradlew assembleDebug`.

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`; the R8-minified, resource-shrunk, unsigned release APK is at `app/build/outputs/apk/release/app-release-unsigned.apk`. Only `arm64-v8a` is packaged, matching the full native library set provided by the AMap aggregate navigation dependency.

The AMap LLM Agent coordinate recorded in the bundled Skill (`com.amap.lbs.client:amap-agent:1.1.41`) cannot currently be resolved from Google Maven or Maven Central. Obtain a compatible AAR or private repository credentials from AMap before enabling Agent queries. Do not add separate `navi-3dmap`, `3dmap`, `location`, or `search` dependencies — this project uses the `navi-3dmap-location-search` aggregate artifact as the sole map, navigation, location, and search implementation.

## Privacy & Security

- The AMap key is injected into the Manifest and `BuildConfig` via local properties and must never be committed.
- No AMap SDK API is called before explicit privacy consent is persisted.
- Android cloud backup and device migration are disabled to prevent favorites, trips, settings, location traces, and privacy state from leaking.
- Navigation callbacks are dispatched to the main thread before updating immutable UI state; the turn-bitmap cache is bounded to 32 entries.
- Offline city packages are managed by the AMap offline map manager and remain usable when the network is confirmed unavailable.

## CI

`.github/workflows/android-ci.yml` runs unit tests, Android Lint, an AMap dependency allowlist check, Debug/Release builds, and an Android test APK build on every push and pull request, uploading APKs, Lint reports, and UI previews. Because the AMap native navigation engine does not support standard x86_64 GitHub emulators, device-interaction tests require an ARM64 physical device or device cloud.

With an authorized ARM64 Android device connected, run `scripts/device-regression.sh all` to install the app and test APKs, execute instrumented tests, and launch an AMap online regression with fresh data. Map, location, search, route, background-navigation, and favorite-place check steps are documented in `docs/device-regression.md`.

## Architecture

The app uses a single-Activity Compose architecture. AMap `MapView` and `AMapNaviView` are hosted by lifecycle-aware Compose adapters; search, routing, navigation, favorites, trips, settings, and offline maps connect to the UI through feature-level immutable models and unidirectional data flow. On device rotation the same navigation View is retained and the portrait/landscape vehicle center is adjusted in place, avoiding redundant route recalculations.
