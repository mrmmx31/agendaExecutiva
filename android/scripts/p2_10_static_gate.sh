#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID="$ROOT/android"
APP_MANIFEST="$ANDROID/app/src/main/AndroidManifest.xml"
WEAR_MANIFEST="$ANDROID/wear/src/main/AndroidManifest.xml"
APP_APK="${1:-$ANDROID/app/build/outputs/apk/release/app-release-unsigned.apk}"
WEAR_APK="${2:-$ANDROID/wear/build/outputs/apk/release/wear-release-unsigned.apk}"

fail() { printf 'P2-10 static gate: %s\n' "$*" >&2; exit 1; }
require_text() { grep -qF "$2" "$1" || fail "ausente em $1: $2"; }

command -v xmllint >/dev/null || fail "xmllint indisponivel"
command -v apkanalyzer >/dev/null || fail "apkanalyzer indisponivel"
[[ -f "$APP_APK" ]] || fail "APK telefone inexistente: $APP_APK"
[[ -f "$WEAR_APK" ]] || fail "APK Wear inexistente: $WEAR_APK"

xmllint --noout "$APP_MANIFEST" "$WEAR_MANIFEST" \
  "$ANDROID/app/src/main/res/xml/backup_rules.xml" \
  "$ANDROID/app/src/main/res/xml/data_extraction_rules.xml" \
  "$ANDROID/wear/src/main/res/xml/backup_rules.xml" \
  "$ANDROID/wear/src/main/res/xml/data_extraction_rules.xml"

require_text "$APP_MANIFEST" 'android:allowBackup="false"'
require_text "$APP_MANIFEST" '<uses-permission android:name="android.permission.CAMERA" />'
require_text "$APP_MANIFEST" '<uses-feature android:name="android.hardware.camera" android:required="false" />'
require_text "$WEAR_MANIFEST" 'android:allowBackup="false"'
require_text "$APP_MANIFEST" 'android:usesCleartextTraffic="false"'
require_text "$ROOT/docs/privacy/PRIVACY_NOTICE.md" 'controlador: `A DEFINIR`'
require_text "$ANDROID/app/src/main/res/values/strings.xml" 'A Agenda não diagnostica, não prescreve'

[[ "$(apkanalyzer manifest debuggable "$APP_APK" 2>/dev/null)" == "false" ]] || fail "APK telefone e debugavel"
[[ "$(apkanalyzer manifest debuggable "$WEAR_APK" 2>/dev/null)" == "false" ]] || fail "APK Wear e debugavel"

allowed_permissions='^(android\.permission\.(ACCESS_NETWORK_STATE|CAMERA|FOREGROUND_SERVICE|INTERNET|POST_NOTIFICATIONS|RECEIVE_BOOT_COMPLETED|VIBRATE|WAKE_LOCK|health\.READ_(HEART_RATE|RESTING_HEART_RATE|SLEEP|STEPS))|com\.pessoal\.agenda\.mobile\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$'
for apk in "$APP_APK" "$WEAR_APK"; do
  unexpected="$(apkanalyzer manifest permissions "$apk" 2>/dev/null | grep -Ev "$allowed_permissions" || true)"
  [[ -z "$unexpected" ]] || fail "permissao inesperada em $apk: $unexpected"
done
if apkanalyzer manifest permissions "$WEAR_APK" 2>/dev/null | grep -qx 'android.permission.CAMERA'; then
  fail "APK Wear nao deve solicitar CAMERA"
fi

if grep -En 'firebase|crashlytics|analytics|onnxruntime|play-services-tflite' \
    "$ANDROID/app/build.gradle.kts" "$ANDROID/wear/build.gradle.kts"; then
  fail "SDK de analytics ou runtime nao aprovado"
fi
if grep -R -En --include='*.kt' 'Log\.|printStackTrace|println\(' \
    "$ANDROID/app/src/main/java/com/pessoal/agenda/mobile/health" \
    "$ANDROID/app/src/main/java/com/pessoal/agenda/mobile/recommendation"; then
  fail "log no limite de saude/recomendacao"
fi
if grep -R -En --exclude-dir=build \
    "(AIza[0-9A-Za-z_-]{20,}|BEGIN (RSA |EC |)PRIVATE KEY|client_secret[\"'=: ]+[A-Za-z0-9_-])" \
    "$ANDROID"; then
  fail "possivel segredo versionado"
fi

printf 'P2-10 static gate: aprovado\n'
printf 'telefone_permissions=%s\n' "$(apkanalyzer manifest permissions "$APP_APK" 2>/dev/null | sort | paste -sd, -)"
printf 'wear_permissions=%s\n' "$(apkanalyzer manifest permissions "$WEAR_APK" 2>/dev/null | sort | paste -sd, -)"
