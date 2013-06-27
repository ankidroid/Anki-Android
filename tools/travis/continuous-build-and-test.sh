#!/bin/bash

# It assumes Android SDK is installed in ../android.

# It assumes it is being executed on a Linux machine.

function main() {
  $(dirname $0)/continuous-build.sh
  $(dirname $0)/continuous-test.sh
}

set -e
main "$@"
