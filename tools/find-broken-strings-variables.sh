#! /bin/sh
# Spot malformed string replacement patterns in Android localization files.
# Hopefully it will prevent this kind of bugs: https://code.google.com/p/ankidroid/issues/detail?id=359

grep -R "%1$ s" res/values*
grep -R "%1$ d" res/values*
grep -R "%1" res/values* | grep -v "%1\\$"

grep -RH '%' res/values* | 
 sed -e 's/%/\n%/g' | # Split lines that contain several expressions
 grep '%'           | # Filter out lines that do not contain expressions
 grep -v ' % '      | # Lone % character, not a variable
 grep -v '%<'       | # Same, at the end of the string
 #grep -v '% '       | # Same, at the beginning of the string
 grep -v '%で'      | # Same, no spaces in Japanese
 grep -v '%s'       | # Single string variable
 grep -v '%d'       | # Single decimal variable
 grep -v '%[0-9][0-9]\?$s' | # Multiple string variable
 grep -v '%[0-9][0-9]\?$d' |  # Multiple decimal variable
 grep -v '%1$.1f'   | # ?
 grep -v '%.1f'     |
 grep -v '%\\n'

grep -R '％' res/values*

grep -R "CDATA " res/values*

lint --check StringFormatInvalid ./res
