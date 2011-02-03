#!/bin/sh

find . -name "*\.java" | xargs grep -l 'Log\.d' | xargs sed -i '' -e 's/\/\/ Log\.d/Log\.d/g'
find . -name "*\.java" | xargs grep -l 'Log\.v' | xargs sed -i '' -e 's/\/\/ Log\.v/Log\.v/g'
