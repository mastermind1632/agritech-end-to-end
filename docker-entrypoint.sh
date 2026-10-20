#!/bin/sh
set -e
mkdir -p /app/vault
chown -R appuser:appuser /app/vault 2>/dev/null || true
exec su appuser -c "/opt/java/openjdk/bin/java -jar /app/app.jar"