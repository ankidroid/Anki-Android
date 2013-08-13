#!/bin/bash

# This scripts downloads all the necessary components for making a full build
# from scratch.

# It assumes it is being executed on a Linux machine.

function download_and_install() {
  local uri="$1"; shift
  local dst="$1"; shift
  test $# = 0

  rm -rf tmp
  mkdir -p tmp
  local filename="$(basename $uri)"
  echo "Downloading $filename"
  curl -o tmp/$filename \
    -s \
    -w '  %{http_code}: %{time_total} seconds %{size_download} bytes\n' \
    $uri
  mkdir -p $dst
  unzip -d $dst tmp/$filename >/dev/null
  rm -rf tmp
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
    $base_uri/tools_r22.0.5-linux.zip \
    $root/android

  download_and_install \
    $base_uri/platform-tools_r18.0.1-linux.zip \
    $root/android

  download_and_install \
    $base_uri/build-tools_r18.0.1-linux.zip \
    $root/android/build-tools

  download_and_install \
    $base_uri/android-18_r01.zip \
    $root/android/platforms

  download_and_install \
    $base_uri/sysimg_armv7a-18_r01.zip \
    $root/android/system-images/android-18
}

set -e
main "$@"
