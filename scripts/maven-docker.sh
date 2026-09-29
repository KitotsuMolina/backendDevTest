#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Keep generated files owned by the invoking user; cache stays inside the checkout.
exec docker run --rm --user "$(id -u):$(id -g)" \
  -e MAVEN_CONFIG=/tmp/maven \
  -v "$PWD:/workspace" -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  mvn -B -Dmaven.repo.local=/workspace/.m2 "$@"
