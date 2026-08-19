#!/usr/bin/env bash
set -euo pipefail

echo "==> Checking required tools..."
for cmd in sqlite3 curl jq java; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: Command '$cmd' is required but not installed." >&2
    exit 1
  fi
done

echo "==> Ensuring Gradle wrapper permissions..."
if [ -f "./gradlew" ]; then
  chmod +x ./gradlew
fi

echo "==> Checking GitHub Packages credentials..."
# Check environment variables or ~/.gradle/gradle.properties
HAS_ENV=0
if [ -n "${GITHUB_ACTOR:-}" ] && [ -n "${GITHUB_TOKEN:-}" ]; then
  HAS_ENV=1
fi

GRADLE_PROP_FILE="$HOME/.gradle/gradle.properties"
HAS_PROP=0
if [ -f "$GRADLE_PROP_FILE" ]; then
  if grep -q "^gpr.user=" "$GRADLE_PROP_FILE" && grep -q "^gpr.key=" "$GRADLE_PROP_FILE"; then
    HAS_PROP=1
  fi
fi

if [ "$HAS_ENV" -eq 0 ] && [ "$HAS_PROP" -eq 0 ]; then
  echo "Notice: Neither GITHUB_ACTOR/GITHUB_TOKEN nor ~/.gradle/gradle.properties (gpr.user/gpr.key) found."
  echo "GitHub Packages authentication is required to download packages like graphql-java-codegen."
  echo "Please export GITHUB_ACTOR and GITHUB_TOKEN (with read:packages permission) or add gpr.user and gpr.key to $GRADLE_PROP_FILE."
fi

echo "==> Initializing Gradle Wrapper..."
./gradlew --version

echo "==> Setup completed successfully."
