#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

"""Report failures and skipped tests from Android's JUnit XML using the standard library."""

import html
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


def read_ignored_tests(log_file):
    ignored = set()
    failed = set()
    if log_file is None or not log_file.is_file():
        return ignored
    with log_file.open(encoding="utf-8", errors="replace") as log:
        for line in log:
            match = re.search(r"\bTestRunner: (ignored|failed): (.+)\(([^()]+)\)\s*$", line)
            if match:
                status, method, classname = match.groups()
                (ignored if status == "ignored" else failed).add(f"{classname}.{method}")
    return ignored - failed


def main(results_dir, log_file=None):
    failures = []
    skipped = []
    ignored = read_ignored_tests(log_file)
    for report in sorted(results_dir.rglob("TEST-*.xml")):
        try:
            root = ET.parse(report).getroot()
        except (ET.ParseError, OSError) as error:
            failures.append(f"Could not read {report}: {error}")
            continue

        for test in root.iter("testcase"):
            for result in test:
                if result.tag not in ("failure", "error", "skipped"):
                    continue
                name = f"{test.get('classname', '')}.{test.get('name', '')}"
                details = "".join(result.itertext()).strip()
                message = result.get("message", "")
                # Android can report assumption violations as failures or errors.
                # Only match the top-level exception, not mentions in a real failure.
                exception_type = result.get("type") or (details or message).partition("\n")[0].partition(":")[0]
                # The Android test engine also writes @Ignore as an empty failure.
                # Require the runner's ignore event before treating an empty failure as skipped.
                is_ignored = name in ignored and not (details or message or result.get("type"))
                if message and message not in details:
                    details = f"{message}\n{details}".strip()
                if result.tag == "skipped" or is_ignored or exception_type in (
                    "org.junit.AssumptionViolatedException",
                    "org.junit.internal.AssumptionViolatedException",
                ):
                    skipped.append(f"{name}\n{details or result.get('type', 'Test skipped')}")
                else:
                    failures.append(f"{name}\n{details or result.get('type', 'Test failed')}")

    for failure in failures:
        if os.environ.get("GITHUB_ACTIONS") == "true":
            # GitHub renders the escaped message as a multiline error in the log.
            message = failure.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error::{message}")
        else:
            print(failure, end="\n\n")

    if skipped:
        title = f"Skipped tests ({len(skipped)})"
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::group::{title}")
        else:
            print(title, end="\n\n")
        for skip in skipped:
            print(skip, end="\n\n")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print("::endgroup::")

    if (failures or skipped) and os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            if failures:
                summary.write("### Emulator test failures\n\n")
                for failure in failures:
                    summary.write(f"<pre>{html.escape(failure)}</pre>\n\n")
            if skipped:
                summary.write(f"<details><summary>{title}</summary>\n\n")
                for skip in skipped:
                    summary.write(f"<pre>{html.escape(skip)}</pre>\n\n")
                summary.write("</details>\n\n")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1]), Path(sys.argv[2]) if len(sys.argv) > 2 else None))
