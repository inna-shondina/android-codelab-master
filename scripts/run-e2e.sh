#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
adb_command=${ADB:-adb}

if ! command -v "$adb_command" >/dev/null 2>&1; then
    echo "adb is required to run the E2E test" >&2
    exit 1
fi

if [ -z "${ANDROID_SERIAL:-}" ]; then
    emulator_serials=$(
        "$adb_command" devices |
            awk '$1 ~ /^emulator-/ && $2 == "device" { print $1 }'
    )
    emulator_count=$(printf '%s\n' "$emulator_serials" | awk 'NF { count++ } END { print count + 0 }')
    if [ "$emulator_count" -ne 1 ]; then
        echo "Start exactly one emulator or set ANDROID_SERIAL explicitly" >&2
        exit 1
    fi
    ANDROID_SERIAL=$emulator_serials
    export ANDROID_SERIAL
fi

case "$ANDROID_SERIAL" in
    emulator-*) ;;
    *)
        echo "The E2E route test must run on an Android Emulator" >&2
        exit 1
        ;;
esac

api_level=$("$adb_command" -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$api_level" -lt 31 ]; then
    echo "The E2E route test requires API 31 or newer; found API $api_level" >&2
    exit 1
fi

echo "Running the GPX E2E test on $ANDROID_SERIAL (API $api_level)"

exec "$script_dir/../gradlew" connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sap.codelab.e2e.LocationReminderE2ETest
