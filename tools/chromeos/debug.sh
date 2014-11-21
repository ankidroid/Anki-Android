#!/usr/bin/env bash

if [ $# -lt 2 ]; then
    echo "missing conversion script or apk file"
    exit 1
fi

SCRIPT_PATH=`dirname $0`
APK_BASENAME=`basename $2`
OUTPUT_PATH=${APK_BASENAME%apk}crx

python $1 --metadata $SCRIPT_PATH/debug.crx.json --crx --output $PWD/$OUTPUT_PATH --destructive $2
