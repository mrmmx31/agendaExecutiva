#!/usr/bin/env bash
set -euo pipefail

SERIAL="${AGENDA_PHYSICAL_SERIAL:-}"
EXPECTED_MODEL="${AGENDA_PHYSICAL_EXPECTED_MODEL:-}"
AUTHORIZATION="${AGENDA_ALLOW_PHYSICAL_TESTS:-}"
BASELINE_UTC="${AGENDA_GATE9_BASELINE_UTC:-}"
REPORT="${AGENDA_GATE9_REPORT:-/tmp/agenda-p2-10-gate9-final.txt}"
PACKAGE='com.pessoal.agenda.mobile.fieldtest'

fail() { printf 'P2-10 gate 9: %s\n' "$*" >&2; exit 1; }
value_from_battery() {
  local key="$1"
  awk -F: -v key="$key" '$1 ~ "^[[:space:]]*" key "$" {sub(/^[[:space:]]*/, "", $2); print $2; exit}'
}
pss_kb() {
  adb -s "$SERIAL" shell dumpsys meminfo "$PACKAGE" |
    awk '/TOTAL PSS:/ {print $3; exit}'
}

[[ "$AUTHORIZATION" == 'I_HAVE_EXPLICIT_USER_AUTHORIZATION' ]] ||
  fail 'autorizacao explicita ausente; nenhum comando ADB foi executado'
[[ -n "$SERIAL" ]] || fail 'AGENDA_PHYSICAL_SERIAL e obrigatorio'
[[ "$SERIAL" != emulator-* ]] || fail 'emulador recusado'
[[ -n "$EXPECTED_MODEL" ]] || fail 'AGENDA_PHYSICAL_EXPECTED_MODEL e obrigatorio'
[[ -n "$BASELINE_UTC" ]] || fail 'AGENDA_GATE9_BASELINE_UTC e obrigatorio'

baseline_epoch="$(date -u -d "$BASELINE_UTC" +%s)" || fail 'baseline UTC invalido'
now_epoch="$(date -u +%s)"
minimum_epoch="$((baseline_epoch + 86400))"
(( now_epoch >= minimum_epoch )) ||
  fail "janela incompleta; repetir depois de $(date -u -d "@$minimum_epoch" +%Y-%m-%dT%H:%M:%SZ)"

[[ "$(adb -s "$SERIAL" get-state)" == device ]] || fail 'dispositivo autorizado indisponivel'
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]] || fail 'QEMU recusado'
actual_model="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
[[ "$actual_model" == "$EXPECTED_MODEL" ]] || fail "modelo inesperado: $actual_model"
package_path="$(adb -s "$SERIAL" shell pm path "$PACKAGE" | tr -d '\r')"
[[ "$package_path" == package:* ]] || fail 'variante fieldTest nao instalada'

battery="$(adb -s "$SERIAL" shell dumpsys battery | tr -d '\r')"
level="$(value_from_battery level <<<"$battery")"
temperature_tenths="$(value_from_battery temperature <<<"$battery")"
status="$(value_from_battery status <<<"$battery")"
ac_powered="$(value_from_battery 'AC powered' <<<"$battery")"
usb_powered="$(value_from_battery 'USB powered' <<<"$battery")"
wireless_powered="$(value_from_battery 'Wireless powered' <<<"$battery")"

declare -a pss_samples=()
for attempt in 1 2 3 4 5; do
  adb -s "$SERIAL" shell am force-stop "$PACKAGE"
  adb -s "$SERIAL" shell am start -W -n "$PACKAGE/com.pessoal.agenda.mobile.MainActivity" >/dev/null
  sleep 2
  sample="$(pss_kb)"
  [[ "$sample" =~ ^[0-9]+$ ]] || fail "PSS invalido na amostra $attempt"
  pss_samples+=("$sample")
done

pss_monotonic_growth=false
if (( ${pss_samples[1]} > ${pss_samples[0]} &&
      ${pss_samples[2]} > ${pss_samples[1]} &&
      ${pss_samples[3]} > ${pss_samples[2]} &&
      ${pss_samples[4]} > ${pss_samples[3]} )); then
  pss_monotonic_growth=true
fi

job_refs="$(adb -s "$SERIAL" shell dumpsys jobscheduler | grep -Fc "$PACKAGE" || true)"
wakelock_refs="$(adb -s "$SERIAL" shell dumpsys batterystats | grep -Fic "$PACKAGE" || true)"
fatal_refs="$(adb -s "$SERIAL" shell logcat -d -b crash | grep -Fic "$PACKAGE" || true)"

umask 077
{
  printf 'timestamp_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'baseline_utc=%s\n' "$(date -u -d "@$baseline_epoch" +%Y-%m-%dT%H:%M:%SZ)"
  printf 'elapsed_seconds=%s\n' "$((now_epoch - baseline_epoch))"
  printf 'model=%s\n' "$actual_model"
  printf 'battery_level_percent=%s\n' "$level"
  printf 'battery_temperature_c=%s\n' "$(awk -v value="$temperature_tenths" 'BEGIN {printf "%.1f", value / 10}')"
  printf 'battery_status=%s\n' "$status"
  printf 'ac_powered=%s\n' "$ac_powered"
  printf 'usb_powered=%s\n' "$usb_powered"
  printf 'wireless_powered=%s\n' "$wireless_powered"
  printf 'pss_kb_samples=%s\n' "$(IFS=,; printf '%s' "${pss_samples[*]}")"
  printf 'pss_monotonic_growth=%s\n' "$pss_monotonic_growth"
  printf 'jobscheduler_package_references=%s\n' "$job_refs"
  printf 'batterystats_package_references=%s\n' "$wakelock_refs"
  printf 'crash_buffer_package_references=%s\n' "$fatal_refs"
} >"$REPORT"

printf 'P2-10 gate 9: coleta final concluida em %s\n' "$REPORT"
