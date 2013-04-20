#!/bin/bash

# This scripts downloads all the necessary components for making a full build
# from scratch.

# It assumes it is being executed on a Linux machine.

function download_and_install() {
  local uri="$1"; shift
  local dst="$1"; shift
  test $# = 0

  local filename="$(basename $uri)"
  wget $uri
  unzip -d $dst $filename
  rm $filename
}

function main() {
  set -e
  local root="."
  if [ $# -gt 0 ]; then
    root="$1"; shift
  fi
  test $# = 0

  # Download the Android SDK and necessary components.
  download_and_install \
    http://dl-ssl.google.com/android/repository/tools_r21.1-linux.zip \
    $root/android

  download_and_install \
    http://dl-ssl.google.com/android/repository/platform-tools_r16.0.2-linux.zip \
    $root/android

  download_and_install \
    http://dl-ssl.google.com/android/repository/android-15_r03.zip \
    $root/android/platforms
}

main "$@"
