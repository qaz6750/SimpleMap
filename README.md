# SimpleMap

<div align="center">
	<p><strong>一款覆盖搜索、路线规划、实时导航与行程复盘的 Android 地图应用</strong></p>
	<p>
		<a href="https://github.com/qaz6750/SimpleMap/actions/workflows/android-verify.yml"><img src="https://github.com/qaz6750/SimpleMap/actions/workflows/android-verify.yml/badge.svg" alt="Android Verify" /></a>
		<img src="https://img.shields.io/badge/Android-8.0%2B-0C6DFF?logo=android&amp;logoColor=white" alt="Android 8.0+" />
		<img src="https://img.shields.io/badge/Kotlin-2.3.21-1466D8?logo=kotlin&amp;logoColor=white" alt="Kotlin 2.3.21" />
		<img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-1769E0?logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose with Material 3" />
	</p>
	<p><strong>中文</strong> · <a href="README_EN.md">English</a></p>
</div>

SimpleMap 使用 Kotlin、Jetpack Compose、Material 3 与高德 Android 导航 SDK 构建，针对手机竖屏和车机横屏分别优化驾驶信息布局。

> [!IMPORTANT]
> 运行项目需要自行申请高德 Android Key。原生导航依赖目前仅打包 `arm64-v8a`，真实地图与导航验证需要 ARM64 真机或兼容设备云。

## 核心能力

- 地点模糊搜索、最近记录、地图选点、收藏分组与周边搜索。
- 驾车、公交、骑行和步行路线对比，支持驾车途经点与路线偏好。
- 竖横屏实时导航，包含路口放大图、车道、路况、限速、沿途设施与 GPS 状态。
- 前台 Service、导航通知、Android 16+ Live Updates 与可选悬浮导航卡片。
- 行程摘要、停车位置、离线城市包和本地数据管理。
- 2D / 3D、正北、实时路况、卫星图和日夜主题。

## 隐私边界

SimpleMap 在用户明确同意并持久化隐私状态前，不创建或调用任何高德地图、定位、搜索和导航 API。用户可随时清除本地数据或撤回同意。

Android 云备份和设备迁移已关闭。收藏、设置、搜索记录、停车位置与行程摘要仅保存在本机；行程历史和 GPS 诊断不保存轨迹点。

## 快速开始

### 环境要求

- JDK 17
- Android SDK Platform 37 与 Build Tools 36.0.0
- 已绑定 `com.simplemap` 包名和签名信息的高德 Android Key
- 验证真实导航时需要已授权的 ARM64 Android 设备

### 配置

```bash
git clone https://github.com/qaz6750/SimpleMap.git
cd SimpleMap
cp local.properties.example local.properties
```

编辑 `local.properties`：

```properties
sdk.dir=/absolute/path/to/Android/Sdk
AMAP_API_KEY=your_android_key
```

`local.properties` 已被 Git 忽略。请勿提交真实 Key、签名文件、位置记录或用户数据。

### 构建与安装

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 使用 `gradlew.bat assembleDebug`。首次启动后需先确认应用内隐私说明。

## 验证与发布

```bash
# 单元测试、Lint、Debug APK 与 Android 测试 APK
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest

# Release APK 与 AAB
./gradlew assembleRelease bundleRelease

# 已连接一台授权 ARM64 设备时运行设备回归
ADB="$ANDROID_HOME/platform-tools/adb" ./scripts/device-regression.sh all
```

详细真机检查项见 [设备回归清单](docs/device-regression.md)。GitHub Actions 提供 [自动验证](.github/workflows/android-verify.yml)、[手动构建](.github/workflows/android-manual-build.yml) 与 [版本发布](.github/workflows/android-release.yml)。

## 技术栈

| 领域 | 版本或实现 |
| :--- | :--- |
| 语言与 UI | Kotlin 2.3.21、Jetpack Compose、Material 3 |
| Android | minSdk 26，compileSdk / targetSdk 37 |
| 构建 | Gradle 9.5.0、Android Gradle Plugin 9.3.0、JDK 17 |
| 地图与导航 | 高德 `navi-3dmap-location-search` 11.2 聚合依赖 |
| 架构 | 单 Activity、不可变 UI 状态、单向数据流、生命周期感知 View 适配器 |
| 本地存储 | SharedPreferences，仅保存设置、收藏、搜索历史和行程摘要 |

## 已知限制

- 仅打包 `arm64-v8a`，标准 x86_64 模拟器无法运行高德原生导航引擎。
- 地图、搜索、算路和导航验证需要有效 Key、网络与兼容设备。
- 项目只使用高德聚合依赖，请勿重复添加地图、定位、搜索或导航 SDK artifact。
- Release 默认未签名；正式分发前需配置独立签名。
- 后台导航可能受厂商省电策略限制，应在目标设备上回归验证。