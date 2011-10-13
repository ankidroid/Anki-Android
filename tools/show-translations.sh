#! /bin/sh
# Spot malformed string replacement patterns in Android localization files.
# Hopefully it will prevent this kind of bugs: https://code.google.com/p/ankidroid/issues/detail?id=359

find ../res -name "*\.xml" | xargs grep "name=\""$1"\""
