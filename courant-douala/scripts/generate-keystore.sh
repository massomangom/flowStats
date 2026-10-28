#!/usr/bin/env bash
# Crée la clé d'envoi (upload key) pour Google Play. À GARDER PRÉCIEUSEMENT, hors du dépôt.
set -euo pipefail
OUT="${1:-upload.jks}"
keytool -genkeypair -v -keystore "$OUT" -alias upload -keyalg RSA -keysize 2048 -validity 10000
echo
echo "Clé créée : $OUT"
echo "Pour GitHub Actions, copiez ceci dans le secret KEYSTORE_BASE64 :"
base64 -w0 "$OUT"; echo
