#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

# ADB can report the device offline just after boot (#22033/#22042).
serial="emulator-$EMULATOR_PORT"

adb_connection() {
  adb devices -l 2>/dev/null | awk -v serial="$serial" '$1 == serial { for (i = 3; i <= NF; i++) if ($i ~ /^transport_id:/) print $2, $i }' || true
}

for ((attempt = 1; attempt <= 5; attempt++)); do
  connection=$(adb_connection)
  if "$@"; then
    exit 0
  else
    status=$?
  fi

  if ((attempt == 5)) || [[ "$connection" == "device "* && "$(adb_connection)" == "$connection" ]]; then
    exit "$status"
  fi
  # Keep retry messages out of stdout so getprop results remain usable.
  printf 'ADB lost %s; retrying once it reconnects (attempt %s/5).\n' "$serial" "$((attempt + 1))" >&2
  timeout 60s adb -s "$serial" wait-for-device || exit "$status"
done
