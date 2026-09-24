#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

# Issue 22033: ADB reported "device offline" after sys.boot_completed=1.
# The underlying cause is unknown; fixing it would likely remove the need for retries.
# Retries may help if the disconnect is transient; this is unverified in CI.
# Only use for commands safe to repeat: a failed ADB call may already have
# executed the command on the device.
retry_repeatable_adb_shell_cmd() {
  local attempt output
  for ((attempt = 1; attempt <= 5; attempt++)); do
    if output=$(adb shell "$@"); then
      printf '%s\n' "$output"
      return 0
    fi
    sleep 1
  done
  return 1
}

retry_repeatable_adb_shell_cmd "$@"
