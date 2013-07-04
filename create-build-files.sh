#!/bin/sh
# Build all needed files for ant compilation
# Norbert Nagold (2011)
# You must:
#  - Have installed the Android SDK,
#  - Have added android to the PATH system variable.

android update project -p . -n AnkiDroid -s -t android-17
