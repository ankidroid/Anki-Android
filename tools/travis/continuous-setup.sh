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
  mkdir -p $dst
  unzip -d $dst $filename
  rm $filename
}

function main() {
  readonly base_uri="http://dl-ssl.google.com/android/repository"

  local root="."
  if [ $# -gt 0 ]; then
    root="$1"; shift
  fi
  test $# = 0

  # Download the Android SDK and necessary components.
  download_and_install \
    $base_uri/tools_r22.0.1-linux.zip \
    $root/android

  download_and_install \
    $base_uri/platform-tools_r17-linux.zip \
    $root/android

  download_and_install \
    $base_uri/build-tools_r17-linux.zip \
    $root/android/build-tools

  download_and_install \
    $base_uri/android-17_r02.zip \
    $root/android/platforms

  download_and_install \
    $base_uri/sysimg_armv7a-17_r02.zip \
    $root/android/system-images/android-17
}

set -e
main "$@"
