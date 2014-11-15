#!/usr/bin/env bash

if [ $# -lt 2 ]; then
    echo "missing conversion script or apk file"
    exit 1
fi

SCRIPT_PATH=`dirname $0`

python $1 --metadata $SCRIPT_PATH/debug.crx.json --destructive --crx $2
