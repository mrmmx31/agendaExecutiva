#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID="$ROOT/android"
PHONE="${AGENDA_PHONE_SERIAL:-emulator-5554}"
WEAR="${AGENDA_WEAR_SERIAL:-emulator-5556}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME

TEST_CLASS='com.pessoal.agenda.mobile.P2_10ResilienceTest'
PHONE_PAIRED='com.pessoal.agenda.mobile.wear.PhoneWearPairedGateTest'
WEAR_PAIRED='com.pessoal.agenda.wear.WearPairedGateTest'
RUNNER='com.pessoal.agenda.mobile.test/androidx.test.runner.AndroidJUnitRunner'

fail() { printf 'P2-10 resilience gate: %s\n' "$*" >&2; exit 1; }

restore() {
  adb -s "$PHONE" shell svc wifi enable >/dev/null 2>&1 || true
  adb -s "$PHONE" shell svc data enable >/dev/null 2>&1 || true
  adb -s "$PHONE" shell dumpsys battery reset >/dev/null 2>&1 || true
  adb -s "$PHONE" shell cmd deviceidle unforce >/dev/null 2>&1 || true
}
trap restore EXIT

verify_emulator() {
  local serial="$1"
  [[ "$serial" == emulator-* ]] || fail "serial fisico recusado: $serial"
  [[ "$(adb -s "$serial" get-state)" == "device" ]] || fail "AVD indisponivel: $serial"
  [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]] || \
    fail "dispositivo nao e emulador: $serial"
}

wait_boot() {
  local serial="$1"
  adb -s "$serial" wait-for-device
  for _ in $(seq 1 120); do
    [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && return
    sleep 1
  done
  fail "boot nao terminou: $serial"
}

instrument() {
  local serial="$1" class="$2" method="$3" paired="${4:-false}" output
  output="$(adb -s "$serial" shell am instrument -w \
    -e pairedGate "$paired" -e class "$class#$method" "$RUNNER")"
  printf '%s\n' "$output"
  rg -q '^OK \(' <<<"$output" || fail "instrumentacao falhou: $class#$method"
}

instrument_retry() {
  local serial="$1" class="$2" method="$3" output
  for attempt in $(seq 1 12); do
    output="$(adb -s "$serial" shell am instrument -w \
      -e pairedGate true -e class "$class#$method" "$RUNNER")"
    printf '%s\n' "$output"
    if rg -q '^OK \(' <<<"$output"; then return; fi
    printf 'API Wear ainda indisponivel; tentativa %s/12\n' "$attempt"
    sleep 10
  done
  fail "API Wear nao estabilizou: $serial"
}

verify_emulator "$PHONE"
verify_emulator "$WEAR"
adb -s "$PHONE" shell dumpsys batterystats --reset >/dev/null

cd "$ANDROID"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :wear:assembleDebug :wear:assembleDebugAndroidTest
adb -s "$PHONE" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$PHONE" install -t -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
adb -s "$WEAR" install -r wear/build/outputs/apk/debug/wear-debug.apk >/dev/null
adb -s "$WEAR" install -t -r wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk >/dev/null
adb -s "$PHONE" shell pm clear com.pessoal.agenda.mobile >/dev/null

adb -s "$PHONE" shell svc wifi disable
adb -s "$PHONE" shell svc data disable
instrument "$PHONE" "$TEST_CLASS" prepareDurableOfflineFixtureWithoutDelivery

adb -s "$PHONE" shell am force-stop com.pessoal.agenda.mobile
adb -s "$PHONE" shell dumpsys battery unplug
adb -s "$PHONE" shell cmd deviceidle force-idle
instrument "$PHONE" "$TEST_CLASS" assertDurableFixtureAfterProcessIdleOrReboot
adb -s "$PHONE" shell cmd deviceidle unforce
adb -s "$PHONE" shell dumpsys battery reset
adb -s "$PHONE" shell svc wifi enable
adb -s "$PHONE" shell svc data enable

adb -s "$PHONE" reboot
wait_boot "$PHONE"
adb -s "$WEAR" reboot
wait_boot "$WEAR"
adb -s "$PHONE" shell am start -n com.pessoal.agenda.mobile/.MainActivity >/dev/null
adb -s "$WEAR" shell am start -n com.pessoal.agenda.mobile/com.pessoal.agenda.wear.MainActivity >/dev/null
sleep 5

instrument "$PHONE" "$TEST_CLASS" assertDurableFixtureAfterProcessIdleOrReboot
instrument_retry "$PHONE" "$PHONE_PAIRED" pairedNodeIsReachable
instrument_retry "$WEAR" "$WEAR_PAIRED" pairedNodeIsReachable

adb -s "$PHONE" shell am start -n com.pessoal.agenda.mobile/.MainActivity >/dev/null
sleep 3
printf '%s\n' '--- memoria telefone ---'
adb -s "$PHONE" shell dumpsys meminfo com.pessoal.agenda.mobile | sed -n '1,18p'
printf '%s\n' '--- jobs telefone ---'
adb -s "$PHONE" shell dumpsys jobscheduler com.pessoal.agenda.mobile | \
  rg -m 12 'JOB #|agenda-alert|Pending queue|Ready:' || true
printf '%s\n' '--- bateria telefone (baseline virtual) ---'
adb -s "$PHONE" shell dumpsys batterystats com.pessoal.agenda.mobile | sed -n '1,35p'
printf 'P2-10 resilience gate: aprovado\n'
