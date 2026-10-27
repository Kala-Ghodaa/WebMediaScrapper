#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
# Preserve the AI Studio signing configuration; generate only a missing local development key.
if [[ ! -f debug.keystore ]]; then
  "${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -genkeypair -keystore debug.keystore \
    -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" -noprompt
fi
git config --local core.hooksPath .githooks
printf 'Local debug signing and Git quality hooks are ready. Run scripts/check.sh.\n'
