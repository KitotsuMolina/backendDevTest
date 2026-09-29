#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# PlantUML 1.2026.8. Pin the image for repeatable layout and syntax support.
renderer='ghcr.io/plantuml/plantuml@sha256:d08610df482510844382caa4e016ba2bf7e3231f630f02ee12f250f3416c62b1'
for format in svg png; do
  docker run --rm --network none --user "$(id -u):$(id -g)" \
    -v "$PWD/docs/diagrams:/data" -w /data "$renderer" \
    --check-before-run --no-error-image "--$format" '0*.puml'
done
