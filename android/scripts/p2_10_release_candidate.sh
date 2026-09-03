#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID="$ROOT/android"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
APKSIGNER="$(find "$SDK/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort -V | tail -1)"
REPORT="${AGENDA_RELEASE_REPORT:-/tmp/agenda-p2-10-release-candidate.txt}"

fail() { printf 'P2-10 release candidate: %s\n' "$*" >&2; exit 1; }
require_env() { [[ -n "${!1:-}" ]] || fail "variavel obrigatoria ausente: $1"; }

require_env AGENDA_RELEASE_STORE_FILE
require_env AGENDA_RELEASE_STORE_PASSWORD
require_env AGENDA_RELEASE_KEY_ALIAS
require_env AGENDA_RELEASE_KEY_PASSWORD
[[ -f "$AGENDA_RELEASE_STORE_FILE" ]] || fail 'keystore inexistente'
STORE_REAL="$(realpath "$AGENDA_RELEASE_STORE_FILE")"
case "$STORE_REAL" in
  "$ROOT"|"$ROOT"/*) fail 'keystore deve ficar fora do repositorio' ;;
esac
[[ -x "$APKSIGNER" ]] || fail 'apksigner indisponivel'
command -v jarsigner >/dev/null || fail 'jarsigner indisponivel'

cd "$ANDROID"
./gradlew :app:clean :wear:clean :app:assembleRelease :wear:assembleRelease
./gradlew :app:bundleRelease :wear:bundleRelease

APP_APK="$ANDROID/app/build/outputs/apk/release/app-release.apk"
WEAR_APK="$ANDROID/wear/build/outputs/apk/release/wear-release.apk"
APP_AAB="$ANDROID/app/build/outputs/bundle/release/app-release.aab"
WEAR_AAB="$ANDROID/wear/build/outputs/bundle/release/wear-release.aab"
for artifact in "$APP_APK" "$WEAR_APK" "$APP_AAB" "$WEAR_AAB"; do
  [[ -f "$artifact" ]] || fail "artefato ausente: $artifact"
done

"$APKSIGNER" verify --verbose "$APP_APK" >/dev/null
"$APKSIGNER" verify --verbose "$WEAR_APK" >/dev/null
jarsigner -verify "$APP_AAB" >/dev/null
jarsigner -verify "$WEAR_AAB" >/dev/null

APP_CERT="$("$APKSIGNER" verify --print-certs "$APP_APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
WEAR_CERT="$("$APKSIGNER" verify --print-certs "$WEAR_APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ -n "$APP_CERT" && "$APP_CERT" == "$WEAR_CERT" ]] || \
  fail 'telefone e Wear nao possuem o mesmo certificado'

"$ANDROID/scripts/p2_10_static_gate.sh" "$APP_APK" "$WEAR_APK" >/dev/null

{
  printf 'generated_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version=0.1.0\n'
  printf 'certificate_sha256=%s\n' "$APP_CERT"
  sha256sum "$APP_APK" "$WEAR_APK" "$APP_AAB" "$WEAR_AAB"
} | tee "$REPORT"

printf 'P2-10 release candidate: artefatos assinados e verificados; nada foi publicado\n'
