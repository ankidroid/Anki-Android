#!/bin/sh

find src -name "*\.java" | xargs grep -l 'Log.d' | xargs sed --no-run-if-empty -i 's/\/\/ Log\.d/Log\.d/g'
find src -name "*\.java" | xargs grep -l 'Log.v' | xargs sed --no-run-if-empty -i 's/\/\/ Log\.v/Log\.v/g'
find src -name "*\.java" | xargs grep -l 'Log.i' | xargs sed --no-run-if-empty -i 's/\/\/ Log\.i/Log\.i/g'
