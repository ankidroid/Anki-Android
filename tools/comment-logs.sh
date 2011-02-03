#!/bin/sh

find ../src -name "*\.java" | xargs grep -l 'Log\.d' | xargs sed -i '' -e 's/Log\.d/\/\/ Log\.d/g'
find ../src -name "*\.java" | xargs grep -l 'Log\.v' | xargs sed -i '' -e 's/Log\.v/\/\/ Log\.d/v'

