#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

resolve_adb() {
    local configured_adb=${ADB:-}
    local path_adb

    if [[ -n "$configured_adb" ]]; then
        if [[ -x "$configured_adb" ]]; then
            printf '%s\n' "$configured_adb"
            return
        fi
        path_adb=$(command -v "$configured_adb" 2>/dev/null || true)
        if [[ -n "$path_adb" ]]; then
            printf '%s\n' "$path_adb"
            return
        fi
    fi
    if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
        printf '%s\n' "$ANDROID_SDK_ROOT/platform-tools/adb"
        return
    fi
    if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
        printf '%s\n' "$ANDROID_HOME/platform-tools/adb"
        return
    fi
    if [[ -x "${HOME:-}/Android/Sdk/platform-tools/adb" ]]; then
        printf '%s\n' "$HOME/Android/Sdk/platform-tools/adb"
        return
    fi
    path_adb=$(command -v adb 2>/dev/null || true)
    if [[ -n "$path_adb" ]]; then
        printf '%s\n' "$path_adb"
        return
    fi

    printf 'Unable to find adb. Set ADB, ANDROID_SDK_ROOT, or ANDROID_HOME.\n' >&2
    return 1
}

ADB=$(resolve_adb)
APP_ID=com.simplemap
RUNNER=androidx.test.runner.AndroidJUnitRunner
APP_APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

require_device() {
    mapfile -t devices < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    if [[ ${#devices[@]} -ne 1 ]]; then
        printf 'Expected exactly one authorized Android device, found %d.\n' "${#devices[@]}" >&2
        "$ADB" devices -l >&2
        exit 1
    fi
    export ANDROID_SERIAL=${devices[0]}
}

build_apks() {
    "$ROOT_DIR/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest \
        --no-parallel --max-workers=1
}

install_apks() {
    [[ -f "$APP_APK" && -f "$TEST_APK" ]] || build_apks
    "$ADB" install -r -t "$APP_APK"
    "$ADB" install -r -t "$TEST_APK"
}

prepare_live_test() {
    "$ADB" shell pm clear "$APP_ID"
    "$ADB" logcat -c
    "$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null
    printf 'App launched with clean data. Accept privacy consent on-device before testing AMap.\n'
    printf 'Checklist: %s\n' "$ROOT_DIR/docs/device-regression.md"
}

run_tests() {
    "$ADB" shell am instrument -w "$APP_ID.test/$RUNNER"
}

collect_logs() {
    local output=${1:-"$ROOT_DIR/build/device-regression-log.txt"}
    mkdir -p "$(dirname "$output")"
    "$ADB" logcat -d -v threadtime | grep -Ei \
        'simplemap|amap|autonavi|AndroidRuntime|ANR|FATAL EXCEPTION' > "$output" || true
    printf 'Logs written to %s\n' "$output"
}

require_device
case "${1:-all}" in
    check) "$ADB" shell getprop ro.product.model ;;
    install) install_apks ;;
    tests) install_apks; run_tests ;;
    launch) install_apks; prepare_live_test ;;
    logs) collect_logs "${2:-$ROOT_DIR/build/device-regression-log.txt}" ;;
    all) install_apks; run_tests; prepare_live_test ;;
    *) printf 'Usage: %s {check|install|tests|launch|logs [file]|all}\n' "$0" >&2; exit 2 ;;
esac