#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID="$ROOT/android"
PHONE="${AGENDA_PHONE_SERIAL:-emulator-5554}"
WEAR="${AGENDA_WEAR_SERIAL:-emulator-5556}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME

PHONE_CLASS='com.pessoal.agenda.mobile.wear.PhoneWearPairedGateTest'
WEAR_CLASS='com.pessoal.agenda.wear.WearPairedGateTest'
RUNNER='com.pessoal.agenda.mobile.test/androidx.test.runner.AndroidJUnitRunner'

fail() { printf 'P2-10 emulator gate: %s\n' "$*" >&2; exit 1; }

verify_emulator() {
  local serial="$1"
  [[ "$serial" == emulator-* ]] || fail "serial fisico recusado: $serial"
  [[ "$(adb -s "$serial" get-state)" == "device" ]] || fail "AVD indisponivel: $serial"
  [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]] || \
    fail "dispositivo nao e emulador: $serial"
}

instrument() {
  local serial="$1" class="$2" method="$3" output
  printf 'Executando %s#%s em %s\n' "$class" "$method" "$serial"
  output="$(adb -s "$serial" shell am instrument -w \
    -e pairedGate true -e class "$class#$method" "$RUNNER")"
  printf '%s\n' "$output"
  rg -q '^OK \(' <<<"$output" || fail "instrumentacao falhou: $class#$method"
}

install_pair() {
  adb -s "$PHONE" install -r "$ANDROID/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
  adb -s "$PHONE" install -t -r "$ANDROID/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null
  adb -s "$WEAR" install -r "$ANDROID/wear/build/outputs/apk/debug/wear-debug.apk" >/dev/null
  adb -s "$WEAR" install -t -r "$ANDROID/wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk" >/dev/null
}

verify_emulator "$PHONE"
verify_emulator "$WEAR"

cd "$ANDROID"
ANDROID_SERIAL="$PHONE" ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL="$WEAR" ./gradlew :wear:connectedDebugAndroidTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :wear:assembleDebug :wear:assembleDebugAndroidTest
install_pair

instrument "$PHONE" "$PHONE_CLASS" pairedNodeIsReachable
instrument "$WEAR" "$WEAR_CLASS" pairedNodeIsReachable

instrument "$PHONE" "$PHONE_CLASS" publishSnoozeFixture
instrument "$WEAR" "$WEAR_CLASS" snoozeReceivedFixtureAndAwaitAcknowledgement
instrument "$PHONE" "$PHONE_CLASS" assertSnoozeConverged

instrument "$PHONE" "$PHONE_CLASS" publishCompleteFixture
instrument "$WEAR" "$WEAR_CLASS" completeReceivedFixtureAndAwaitAcknowledgement
instrument "$PHONE" "$PHONE_CLASS" assertCompleteConverged

instrument "$PHONE" "$PHONE_CLASS" publishOfflineFixture
adb -s "$PHONE" shell am force-stop com.pessoal.agenda.mobile
instrument "$WEAR" "$WEAR_CLASS" completeOfflineFixtureAndKeepDurableOutbox
adb -s "$PHONE" shell am start -n com.pessoal.agenda.mobile/.MainActivity >/dev/null
instrument "$PHONE" "$PHONE_CLASS" assertOfflineConvergedAfterRestart

instrument "$PHONE" "$PHONE_CLASS" publishProtocolStepFixture
instrument "$WEAR" "$WEAR_CLASS" completeProtocolStepAndAwaitAcknowledgement
instrument "$PHONE" "$PHONE_CLASS" assertProtocolAdvancedAfterWearConfirmation

printf 'P2-10 emulator gate: aprovado\n'

