# SimpleMap

基于高德 Android 导航 SDK、Kotlin 与 Jetpack Compose 构建的原生地图导航应用。

## 中文说明

### 功能

- 隐私同意持久化后才初始化高德地图、搜索、定位与导航 SDK。
- 全屏地图主页、顶部搜索、悬浮底栏、实时路况、卫星图、定位与缩放控制。
- POI 与公交线路搜索、附近地点排序、地点详情、地图标记和本地收藏。
- 驾车、公交、骑行与步行路线对比；路线规划页不显示全局底部导航，地图路线保持可见。
- 实时 GPS 导航、内置语音、路况、路线总览、偏航与拥堵重算；后台持续通知可返回当前导航。
- 统一蓝色的竖屏手机和横屏车机自适应导航：高德 SDK 官方路口放大图出现后直接展示，醒目的辅助行驶指令、左侧高速出口、限速、电子眼距离、区间测速剩余距离与建议速度；实时路况持续更新前方缓行和拥堵距离，并显示当前路线最近的事故、施工或道路封闭详情。拥堵首次出现、加重或缓解时提供去重的视觉和内置语音提醒；预计到达时间累计提前或延后 3 分钟时提醒一次并重置基线。上述提醒服从路线提示与语音设置。限行、避堵、路线更新和途经点到达使用自动消退的路线事件提示。点击沿途卡片可在方向自适应悬浮窗中滚动查看全路线服务区和收费站。GPS 详情使用同一悬浮窗规则并在 5 秒后自动关闭，导航设置支持持久化日间/夜间模式。
- 手动拖图后才显示跟随、设置与结束操作，常态导航保持紧凑。
- 行程历史按到达、取消和失败终态记录真实耗时与里程，并明确标记模拟导航；支持一键复用路线、导航偏好，以及带容量统计和仅 Wi-Fi 策略的高德离线城市包。
- 定位权限按需申请，并提供系统权限入口、本地数据清除和隐私同意撤回。

产品四屏总览见 [docs/simplemap-ui-preview.svg](docs/simplemap-ui-preview.svg)，上下文搜索见 [docs/contextual-search-preview.svg](docs/contextual-search-preview.svg)，竖屏导航见 [docs/navigation-portrait-preview.svg](docs/navigation-portrait-preview.svg)，横屏车机导航见 [docs/navigation-junction-landscape-preview.svg](docs/navigation-junction-landscape-preview.svg)，GPS 与夜间模式见 [docs/navigation-gps-night-preview.svg](docs/navigation-gps-night-preview.svg)，持续导航与真实行程见 [docs/persistent-navigation-trips-preview.svg](docs/persistent-navigation-trips-preview.svg)，离线下载策略见 [docs/offline-download-policy-preview.svg](docs/offline-download-policy-preview.svg)，隐私与数据控制见 [docs/privacy-data-controls-preview.svg](docs/privacy-data-controls-preview.svg)。预览中的地图和转向符号用于展示布局；真实导航时，转向图标来自高德 `NaviInfo.iconBitmap`，路况和路线数据来自 SDK 回调。

### 本地配置

1. 安装 JDK 17 和 Android SDK Platform 35。
2. 将 `local.properties.example` 复制为 `local.properties`。
3. 在 `local.properties` 中配置 `sdk.dir` 和 `AMAP_API_KEY`。
4. 运行 `./gradlew assembleDebug`。

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；经过 R8 压缩和资源裁剪的未签名 Release APK 位于 `app/build/outputs/apk/release/app-release-unsigned.apk`。当前只打包 `arm64-v8a`，与高德聚合导航依赖提供的完整原生库集合一致。

仓库内 Android LLM Agent Skill 记录的坐标 `com.amap.lbs.client:amap-agent:1.1.41` 当前无法从 Google Maven 或 Maven Central 解析。启用 Agent 查询前，需要向高德获取兼容 AAR 或私有仓库凭据。不要另行添加 `navi-3dmap`、`3dmap`、`location` 或 `search` 依赖；本项目使用 `navi-3dmap-location-search` 聚合依赖作为唯一的地图、导航、定位和搜索实现。

### 隐私与安全

- 高德 Key 通过本地属性注入 Manifest 和 `BuildConfig`，不得提交到版本库。
- 未持久化明确隐私同意前，不会调用任何高德 SDK API。
- Android 云备份和设备迁移已关闭，避免收藏、行程、设置、位置轨迹和隐私状态外泄。
- 导航回调切换到主线程后再更新不可变 UI 状态；转向位图缓存限制为 32 项。
- 离线城市包由高德离线地图管理器维护，在已确认无网络时仍可使用。

### 持续集成

`.github/workflows/android-ci.yml` 在 Push 和 Pull Request 中执行单元测试、Android Lint、高德依赖白名单检查、Debug/Release 构建和 Android 测试 APK 构建，并上传 APK、Lint 报告和界面预览。由于高德原生导航引擎不支持标准 x86_64 GitHub 模拟器，设备交互测试需要 ARM64 真机或设备云。

### 架构

应用采用单 Activity Compose 架构。高德 `MapView` 与 `AMapNaviView` 由生命周期感知的 Compose 适配器承载；搜索、路线、导航、收藏、行程、设置和离线地图通过功能级不可变模型与单向数据流接入 UI。旋转设备时保留同一个导航 View，并原地调整横竖屏车辆中心，避免重复算路。

## English

SimpleMap is a native Android map and navigation app built with Kotlin, Jetpack Compose, Material 3, and the AMap Android navigation SDK.

### Features

- Persisted privacy consent gates every AMap SDK call.
- Map search, POI details, favorites, route comparison, trip history, preferences, and offline cities.
- Real GPS navigation driven by AMap callbacks, including official maneuver bitmaps, speed limits, cameras, interval cameras, traffic lights, and the nearest two service areas.
- Adaptive portrait phone and landscape vehicle layouts. The route planner intentionally hides the global bottom navigation.
- Lifecycle-aware Android View adapters, immutable UI state, main-thread callback dispatch, bounded icon caching, R8, resource shrinking, and ARM64 packaging.

### Build

Install JDK 17 and Android SDK Platform 35, configure `sdk.dir` and `AMAP_API_KEY` in `local.properties`, then run `./gradlew assembleDebug`. Never commit an AMap key, signing material, location traces, or user data.

The AMap LLM Agent coordinate documented by the bundled Skill (`com.amap.lbs.client:amap-agent:1.1.41`) is not available from the configured public Maven repositories. Obtain a compatible AAR or private repository access from AMap before enabling Agent queries. Keep the existing aggregate navigation artifact as the sole map, navigation, location, and search implementation.