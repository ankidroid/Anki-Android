#!/bin/bash

# It assumes Android SDK is installed in ../android.

# It assumes it is being executed on a Linux machine.

TARGET=android-18
ABI=armeabi-v7a
DELAY_SECONDS=10  # Wait as many seconds before checking again.
NOT_RUNNING_TIMEOUT_SECONDS=60  # Fail if not running for that many seconds. 
RUNNING_TIMEOUT_SECONDS=180  # Fail if running for that many seconds. 

function start_emulator() {
  echo no | 
    android create avd \
      --force \
      --name test \
      --target $TARGET \
      --abi $ABI
  emulator -avd test -no-skin -no-audio -no-window -gpu off &
}

function wait_for_emulator() {
  while true; do
    status="$(adb -e shell getprop init.svc.bootanim 2>&1 | tr -d '\r')"
    echo "status: $status"
    case "$status" in
      stopped)
        echo "emulator is ready"
        return 0;;
      running)
        running=$[$running + $DELAY_SECONDS]
        if [ "$running" = $RUNNING_TIMEOUT_SECONDS ]; then
          echo "boot animation running for $running seconds; quitting..."
          return 1;
        fi;;
      *)
        not_running=$[$not_found + $DELAY_SECONDS]
        if [ "$not_running" = $NOT_RUNNING_TIMEOUT_SECONDS ]; then
          echo "boot animation not running for $not_found seconds; quitting..."
          return 1;
        fi;;
    esac
    sleep $DELAY_SECONDS
  done
}

function main() {
  if [ ! -d ../android ]; then
    echo "Could not find Android SDK: ../android"
    return 1
  fi
  export ANDROID_HOME="$(cd ../android; /bin/pwd)"
  export PATH="$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$PATH"

  which adb || (echo "adb not found"; return 1)
  which emulator || (echo "adb not found"; return 1)

  start_emulator
  cd tests
  ant clean debug
  wait_for_emulator
  ant installd test
  pkill emulator
}

set -e
main "$@"
