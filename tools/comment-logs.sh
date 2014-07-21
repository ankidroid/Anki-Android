#!/bin/sh

find src -name "*\.java" | xargs grep -l 'Log\.d' | xargs --no-run-if-empty sed -i 's/Log\.d/\/\/ Log\.d/g'
find src -name "*\.java" | xargs grep -l 'Log\.v' | xargs --no-run-if-empty sed -i 's/Log\.v/\/\/ Log\.v/g'
find src -name "*\.java" | xargs grep -l 'Log\.i' | xargs --no-run-if-empty sed -i 's/Log\.i/\/\/ Log\.i/g'
