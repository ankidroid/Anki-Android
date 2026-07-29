#!/usr/bin/env bash
# Verifies the URL-bar view ids the app blocker relies on for website blocking.
#
# The ids in SupportedBrowsers.kt drift between browser versions, so they must be
# checked against the browsers actually installed on a device. For each supported
# browser this opens a known page, dumps the view hierarchy, and reports whether
# the expected id is present and holds the URL.
#
# Usage:  tools/blocker/verify-browser-url-bars.sh [adb-serial]
# Needs:  a connected device with USB debugging, and the browsers installed.

set -uo pipefail

ADB=${ADB:-adb}
SERIAL=${1:-}

adb_() {
  if [ -n "$SERIAL" ]; then
    "$ADB" -s "$SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

TEST_URL="https://example.com/"
TMP_XML="$(mktemp -t blocker-ui-XXXXXX.xml)"
trap 'rm -f "$TMP_XML"' EXIT

# package|expected view id (relative to package, as written in SupportedBrowsers.kt)
BROWSERS=(
  "com.android.chrome|url_bar"
  "com.chrome.beta|url_bar"
  "com.chrome.dev|url_bar"
  "com.chrome.canary|url_bar"
  "com.brave.browser|url_bar"
  "com.vivaldi.browser|url_bar"
  "com.microsoft.emmx|url_bar"
  "com.kiwibrowser.browser|url_bar"
  "org.mozilla.firefox|mozac_browser_toolbar_url_view"
  "org.mozilla.firefox_beta|mozac_browser_toolbar_url_view"
  "org.mozilla.focus|mozac_browser_toolbar_url_view"
  "com.sec.android.app.sbrowser|location_bar_edit_text"
  "com.opera.browser|url_field"
  "com.duckduckgo.mobile.android|omnibarTextInput"
)

installed() { adb_ shell pm list packages "$1" 2>/dev/null | grep -qx "package:$1"; }

echo "Checking URL-bar view ids against $(adb_ shell getprop ro.product.model | tr -d '\r')"
echo

any_installed=false
for entry in "${BROWSERS[@]}"; do
  pkg="${entry%%|*}"
  expected_id="${entry##*|}"
  installed "$pkg" || continue
  any_installed=true

  adb_ shell am start -a android.intent.action.VIEW -d "$TEST_URL" "$pkg" >/dev/null 2>&1
  sleep 5
  adb_ shell "uiautomator dump /sdcard/blocker-ui.xml >/dev/null 2>&1; cat /sdcard/blocker-ui.xml" > "$TMP_XML" 2>/dev/null

  # The text of the node carrying the expected resource id, if present.
  found_text=$(python3 - "$TMP_XML" "$pkg:id/$expected_id" <<'PY'
import re, sys
xml = open(sys.argv[1], errors="ignore").read()
want = sys.argv[2]
for node in re.finditer(r'<node[^>]*>', xml):
    n = node.group(0)
    if f'resource-id="{want}"' in n:
        m = re.search(r'text="([^"]*)"', n)
        print(m.group(1) if m else "")
        break
PY
)

  if [ -n "${found_text:-}" ]; then
    printf '  OK       %-32s %s -> "%s"\n' "$pkg" "$expected_id" "$found_text"
  else
    printf '  MISMATCH %-32s expected id "%s" not found or empty\n' "$pkg" "$expected_id"
    echo "           candidate id/text pairs from this browser:"
    python3 - "$TMP_XML" "$pkg" <<'PY'
import re, sys
xml = open(sys.argv[1], errors="ignore").read()
pkg = sys.argv[2]
seen = set()
for node in re.finditer(r'<node[^>]*>', xml):
    n = node.group(0)
    rid = re.search(r'resource-id="([^"]+)"', n)
    txt = re.search(r'text="([^"]+)"', n)
    if not rid or not txt:
        continue
    if not rid.group(1).startswith(pkg):
        continue
    # a URL bar shows a host, so look for text containing a dot and no spaces
    t = txt.group(1)
    if "." in t and " " not in t and rid.group(1) not in seen:
        seen.add(rid.group(1))
        print(f'             {rid.group(1)} -> "{t}"')
PY
  fi
  adb_ shell am force-stop "$pkg" >/dev/null 2>&1
done

$any_installed || echo "  No supported browsers are installed on this device."
echo
echo "Update AnkiDroid/src/main/java/com/ichi2/anki/blocker/SupportedBrowsers.kt for any MISMATCH."
