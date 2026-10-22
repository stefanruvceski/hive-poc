#!/usr/bin/env bash
# Stores the Stride release keystore in GitHub Actions secrets for
# stefanruvceski/stride. Run once on a machine with `gh` logged in:
#
#   scripts/set-github-secrets.sh path/to/stride-release.jks path/to/stride-keystore-password.txt
#
# Sets STRIDE_KEYSTORE_BASE64 (the keystore, base64) and STRIDE_KEYSTORE_PASSWORD.
# Keep the keystore and password somewhere safe (password manager): if the key is
# lost or changed, new builds can no longer be installed over the existing app.
set -euo pipefail

REPO="stefanruvceski/stride"
KEYSTORE="${1:?usage: $0 <keystore.jks> <password-file>}"
PASSWORD_FILE="${2:?usage: $0 <keystore.jks> <password-file>}"

command -v gh >/dev/null || { echo "Install the GitHub CLI first: https://cli.github.com" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Run 'gh auth login' first." >&2; exit 1; }
[ -f "$KEYSTORE" ] || { echo "Keystore not found: $KEYSTORE" >&2; exit 1; }
[ -f "$PASSWORD_FILE" ] || { echo "Password file not found: $PASSWORD_FILE" >&2; exit 1; }

PASSWORD="$(tr -d '\r\n' < "$PASSWORD_FILE")"

# Fail early if the password does not open the keystore or the alias is missing.
if command -v keytool >/dev/null; then
  keytool -list -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias stride >/dev/null \
    || { echo "Password does not open $KEYSTORE, or alias 'stride' is missing." >&2; exit 1; }
fi

# base64 without line breaks; works with both GNU and macOS base64.
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set STRIDE_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set STRIDE_KEYSTORE_PASSWORD --repo "$REPO"

echo "Done. Secrets on $REPO:"
gh secret list --repo "$REPO"
