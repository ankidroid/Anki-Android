#!/bin/bash

# It assumes Android SDK is installed in ../android.

# It assumes it is being executed on a Linux machine.

function main() {
  if [ ! -d ../android ]; then
    echo "Could not find Android SDK: ../android"
    return 1
  fi
  export ANDROID_HOME="$(cd ../android; /bin/pwd)"
  export PATH="$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$PATH"
  ./gradlew assembleDebug
}

set -e
main "$@"
