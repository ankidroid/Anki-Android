#! /bin/sh
# Spot malformed string replacement patterns in Android localization files.
# Hopefully it will prevent this kind of bugs: https://code.google.com/p/ankidroid/issues/detail?id=359

cd ../res
grep -R '%' values* | 
 sed -e 's/%/\n%/g' | # Split lines that contain several expressions
 grep '%'           | # Filter out lines that do not contain expressions
 grep -v ' % '      | # Lone % character, not a variable
 grep -v '%<'       | # Same, at the end of the string
 grep -v '% '       | # Same, at the beginning of the string
 grep -v '%s'       | # Single string variable
 grep -v '%d'       | # Single decimal variable
 grep -v '%[0-9]$s' | # Multiple string variable
 grep -v '%[0-9]$d'   # Multiple decimal variable
