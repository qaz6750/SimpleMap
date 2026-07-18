# SimpleMap

基于高德 Android 导航 SDK、Kotlin 与 Jetpack Compose 构建的原生地图导航应用。

## 中文说明

### 功能

- 隐私同意持久化后才初始化高德地图、搜索、定位与导航 SDK。
- 全屏地图主页、顶部搜索、悬浮底栏、实时路况、卫星图、定位与缩放控制。
- POI 与公交线路搜索、附近地点排序、地点详情、地图标记和本地收藏；收藏可分为家、公司和收藏夹，点击地点可直接规划路线。
- 驾车、公交、骑行与步行路线对比；驾车途经点可调整顺序，“通勤”“高速优先”“新能源省电”预设及避堵、避免高速、少收费等手动偏好会保存在本机。路线规划页不显示全局底部导航，地图路线保持可见。
- 实时 GPS 导航、内置语音、路况、路线总览、偏航与拥堵重算；满足高德约束的实时驾车导航可查看并通过 `selectMainPathID(pathId)` 切换 SDK 周期更新的备选路线。前台 Service 持有真实导航会话，划掉 Activity 后仍可继续定位与语音导航，并可从通知返回或结束导航。
- 统一蓝色的竖屏手机和横屏车机自适应导航：高德 SDK 官方路口放大图完整展示，紧凑布局仍保持转向距离和道路名称的独立层级；`showLaneInfo` 回调提供真实车道方向与推荐状态，推荐车道以蓝色高亮。车速、限速、区间测速剩余距离与建议速度常驻，路线按实时路况着色，安全事件使用单个优先提示槽。拥堵首次出现、加重或缓解时提供去重语音提醒；预计到达时间累计提前或延后 3 分钟时提醒一次并重置基线。上述提醒服从路线提示与语音设置。弱 GPS 模式合并 SDK 信号回调、参与定位卫星数和定位精度，区分系统定位未开启、弱信号、低精度漂移与连续未匹配路线，不保存坐标或轨迹。限行、避堵、路线更新和途经点到达使用自动消退的路线事件提示。点击沿途设施卡可在方向自适应悬浮窗中滚动查看全路线服务区和收费站。GPS 详情使用同一悬浮窗规则并在 5 秒后自动关闭，导航设置可直接选择跟随系统、按时间、始终日间或始终夜间。
- 自动日夜主题可跟随系统、按时间或固定日夜，并在导航进入隧道时临时切换高德夜间底图。导航语音支持详细、简洁和静音三级，自定义跨午夜静音时段，以及独立的重要提示语音开关。
- 手动拖图后才显示跟随、设置与结束操作，常态导航保持紧凑。
- 到达目的地后可搜索终点 3 公里内停车场、保存单个本地停车点并规划步行返回。行程历史按到达、取消和失败终态记录真实耗时与里程，提供时长、里程、平均速度和状态复盘，明确标记模拟导航；行程摘要仅保存在本机且不含轨迹点。支持一键复用路线、导航偏好，以及带容量统计和仅 Wi-Fi 策略的高德离线城市包。
- 定位权限按需申请，并提供系统权限入口、本地数据清除和隐私同意撤回。

产品四屏总览见 [docs/simplemap-ui-preview.svg](docs/simplemap-ui-preview.svg)，自动主题与语音设置见 [docs/theme-voice-settings-preview.svg](docs/theme-voice-settings-preview.svg)，路线增强见 [docs/route-enhancements-preview.svg](docs/route-enhancements-preview.svg)，上下文搜索见 [docs/contextual-search-preview.svg](docs/contextual-search-preview.svg)，常用地点见 [docs/favorite-places-preview.svg](docs/favorite-places-preview.svg)，竖屏导航见 [docs/navigation-portrait-preview.svg](docs/navigation-portrait-preview.svg)，横屏车机导航见 [docs/navigation-junction-landscape-preview.svg](docs/navigation-junction-landscape-preview.svg)，弱 GPS 与夜间模式见 [docs/navigation-gps-night-preview.svg](docs/navigation-gps-night-preview.svg)，紧凑屏与大字体导航见 [docs/navigation-compact-layout-preview.svg](docs/navigation-compact-layout-preview.svg)，持续导航与本地行程复盘见 [docs/persistent-navigation-trips-preview.svg](docs/persistent-navigation-trips-preview.svg)，离线下载策略见 [docs/offline-download-policy-preview.svg](docs/offline-download-policy-preview.svg)，隐私与数据控制见 [docs/privacy-data-controls-preview.svg](docs/privacy-data-controls-preview.svg)。预览中的地图和转向符号用于展示布局；真实导航时，转向图标来自高德 `NaviInfo.iconBitmap`，路况和路线数据来自 SDK 回调。

高德 Android 导航 SDK 11.2 已公开 `setMultipleRouteNaviMode`、`getNaviPaths`、`selectMainPathID`、`displayOverview`、`onGpsSignalWeak` 和目的地中心 `PoiSearch.SearchBound`，本项目只在各自文档约束内调用。多路线导航要求实时驾车、多路径策略、无途经点且起终点直线距离不超过 80 公里。当前公开 SDK 没有交通事件上报端点，`onUpdateDriveEvent` 和 `onNaviRouteNotify` 均为下行通知而非上报接口；在获得高德商务授权与正式接口前不实现事件上报。

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

连接一台已授权的 ARM64 Android 真机后，可运行 `scripts/device-regression.sh all` 安装应用和测试 APK、执行仪器测试，并以全新数据启动高德在线回归。地图、定位、搜索、路线、后台导航和常用地点检查步骤见 `docs/device-regression.md`。

### 架构

应用采用单 Activity Compose 架构。高德 `MapView` 与 `AMapNaviView` 由生命周期感知的 Compose 适配器承载；搜索、路线、导航、收藏、行程、设置和离线地图通过功能级不可变模型与单向数据流接入 UI。旋转设备时保留同一个导航 View，并原地调整横竖屏车辆中心，避免重复算路。

## English

SimpleMap is a native Android map and navigation app built with Kotlin, Jetpack Compose, Material 3, and the AMap Android navigation SDK.

### Features

- Persisted privacy consent gates every AMap SDK call.
- Map search, POI details, favorites, route comparison, trip history, preferences, and offline cities.
- Real GPS navigation driven by AMap callbacks, including official maneuver bitmaps, live alternative-route switching, weak-GPS diagnostics, speed limits, cameras, interval cameras, traffic lights, and the nearest two service areas.
- Ordered waypoints, persisted driving preferences, destination-centered parking assistance, and local trip reviews without stored route traces.
- Adaptive portrait phone and landscape vehicle layouts. The route planner intentionally hides the global bottom navigation.
- Lifecycle-aware Android View adapters, immutable UI state, main-thread callback dispatch, bounded icon caching, R8, resource shrinking, and ARM64 packaging.

### Build

Install JDK 17 and Android SDK Platform 35, configure `sdk.dir` and `AMAP_API_KEY` in `local.properties`, then run `./gradlew assembleDebug`. Never commit an AMap key, signing material, location traces, or user data.

The AMap LLM Agent coordinate documented by the bundled Skill (`com.amap.lbs.client:amap-agent:1.1.41`) is not available from the configured public Maven repositories. Obtain a compatible AAR or private repository access from AMap before enabling Agent queries. Keep the existing aggregate navigation artifact as the sole map, navigation, location, and search implementation.