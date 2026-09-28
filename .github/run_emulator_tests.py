#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

"""Run emulator tests and generate the coverage report."""

import subprocess
import sys


def main():
    subprocess.run(
        ["./gradlew", "uninstallAll", "jacocoAndroidTestReport", "--daemon"],
        check=True,
    )


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
    except OSError as error:
        sys.exit(str(error))
