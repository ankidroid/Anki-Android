#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

###
# Upload each module's unit test XML report under a distinct Codecov flag, using the report's basename.
# This split allows Codecov to carry forward coverage if a module is skipped (as it was unchanged).
# For example, note-editor-test.xml is uploaded with the note-editor-test flag.
#
# Fork pull requests have no repository token, so FORK_BRANCH supplies the fork owner and branch
# to Codecov when uploading without a token.
###

set -eo pipefail
shopt -s nullglob

args=()
if [[ -n "${FORK_BRANCH:-}" && -z "${CODECOV_TOKEN:-}" ]]; then
  args+=(--branch "$FORK_BRANCH")
fi

for report in AnkiDroid/build/reports/codecov/*-test.xml; do
  flag=$(basename "$report" .xml)
  # The CLI's native parser supports up to Python 3.14. macos-15 defaults to 3.15.
  pipx run --python 3.13 --fetch-python=missing --spec codecov-cli==11.3.2 \
    codecovcli upload-coverage --fail-on-error \
    --disable-search --plugin noop --file "$report" --flag "$flag" "${args[@]}"
done
