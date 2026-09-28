#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

"""Run emulator tests and generate the coverage report."""

import subprocess
import sys


def main():
    connected_test = ":AnkiDroid:connectedPlayReleaseAndroidTest"
    subprocess.run(
        ["./gradlew", "uninstallAll", connected_test,
         "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true", "--daemon"],
        check=True,
    )
    subprocess.run(
        ["./gradlew", ":AnkiDroid:jacocoAndroidTestReport", "-x", connected_test, "--daemon"],
        check=True,
    )


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
    except OSError as error:
        sys.exit(str(error))
