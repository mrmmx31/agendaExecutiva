#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID="$ROOT/android"
SERIAL="${AGENDA_PHYSICAL_SERIAL:-}"
WEAR_SERIAL="${AGENDA_PHYSICAL_WEAR_SERIAL:-}"
STAGE="${AGENDA_PHYSICAL_STAGE:-preflight}"
AUTHORIZATION="${AGENDA_ALLOW_PHYSICAL_TESTS:-}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME

PACKAGE='com.pessoal.agenda.mobile.fieldtest'
APK="$ANDROID/app/build/outputs/apk/fieldTest/app-fieldTest.apk"
WEAR_APK="$ANDROID/wear/build/outputs/apk/fieldTest/wear-fieldTest.apk"
REPORT="${AGENDA_PHYSICAL_REPORT:-/tmp/agenda-p2-10-physical-preflight.txt}"

fail() { printf 'P2-10 physical gate: %s\n' "$*" >&2; exit 1; }

[[ "$AUTHORIZATION" == 'I_HAVE_EXPLICIT_USER_AUTHORIZATION' ]] || \
  fail 'autorizacao explicita ausente; nenhum comando ADB foi executado'
[[ -n "$SERIAL" ]] || fail 'AGENDA_PHYSICAL_SERIAL e obrigatorio'
[[ "$SERIAL" != emulator-* ]] || fail 'este gate aceita somente dispositivo fisico autorizado'
[[ "$STAGE" == preflight || "$STAGE" == install ]] || fail 'stage deve ser preflight ou install'

[[ "$(adb -s "$SERIAL" get-state)" == device ]] || fail 'dispositivo autorizado indisponivel'
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]] || \
  fail 'QEMU recusado pelo gate fisico'
if [[ -n "$WEAR_SERIAL" ]]; then
  [[ "$WEAR_SERIAL" != emulator-* ]] || fail 'serial Wear emulado recusado pelo gate fisico'
  [[ "$(adb -s "$WEAR_SERIAL" get-state)" == device ]] || fail 'Wear autorizado indisponivel'
  [[ "$(adb -s "$WEAR_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]] || \
    fail 'QEMU Wear recusado pelo gate fisico'
fi

{
  printf 'timestamp_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'manufacturer=%s\n' "$(adb -s "$SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
  printf 'model=%s\n' "$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
  printf 'android_release=%s\n' "$(adb -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
  printf 'sdk=%s\n' "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
  printf 'field_test_installed=%s\n' "$(adb -s "$SERIAL" shell pm path "$PACKAGE" | sed 's/package:/yes:/' || true)"
  printf '%s\n' 'battery:'
  adb -s "$SERIAL" shell dumpsys battery | grep -E 'level:|temperature:|status:|plugged:'
} | tee "$REPORT"
if [[ -n "$WEAR_SERIAL" ]]; then
  {
    printf 'wear_manufacturer=%s\n' "$(adb -s "$WEAR_SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
    printf 'wear_model=%s\n' "$(adb -s "$WEAR_SERIAL" shell getprop ro.product.model | tr -d '\r')"
    printf 'wear_release=%s\n' "$(adb -s "$WEAR_SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
    printf 'wear_sdk=%s\n' "$(adb -s "$WEAR_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
  } | tee -a "$REPORT"
fi

if [[ "$STAGE" == preflight ]]; then
  printf 'P2-10 physical gate: preflight concluido; nada foi instalado\n'
  exit 0
fi

cd "$ANDROID"
./gradlew :app:assembleFieldTest :wear:assembleFieldTest
[[ -f "$APK" ]] || fail "APK isolado inexistente: $APK"
[[ "$(apkanalyzer manifest application-id "$APK" 2>/dev/null)" == "$PACKAGE" ]] || \
  fail 'applicationId inesperado no APK telefone'
[[ "$(apkanalyzer manifest debuggable "$APK" 2>/dev/null)" == true ]] || \
  fail 'variante telefone nao e fieldTest depuravel'
adb -s "$SERIAL" install -r "$APK"
adb -s "$SERIAL" shell am start -n "$PACKAGE/com.pessoal.agenda.mobile.MainActivity"
if [[ -n "$WEAR_SERIAL" ]]; then
  [[ -f "$WEAR_APK" ]] || fail "APK Wear isolado inexistente: $WEAR_APK"
  [[ "$(apkanalyzer manifest application-id "$WEAR_APK" 2>/dev/null)" == "$PACKAGE" ]] || \
    fail 'applicationId inesperado no APK Wear'
  [[ "$(apkanalyzer manifest debuggable "$WEAR_APK" 2>/dev/null)" == true ]] || \
    fail 'variante Wear nao e fieldTest depuravel'
  adb -s "$WEAR_SERIAL" install -r "$WEAR_APK"
  adb -s "$WEAR_SERIAL" shell am start \
    -n "$PACKAGE/com.pessoal.agenda.wear.MainActivity"
fi
sleep 3
{
  printf '%s\n' 'permissions:'
  adb -s "$SERIAL" shell dumpsys package "$PACKAGE" | \
    sed -n '/requested permissions:/,/install permissions:/p'
  printf '%s\n' 'memory:'
  adb -s "$SERIAL" shell dumpsys meminfo "$PACKAGE" | sed -n '1,18p'
} | tee -a "$REPORT"

printf 'P2-10 physical gate: variante isolada instalada; nenhum dado foi limpo e nenhuma permissao foi concedida\n'
