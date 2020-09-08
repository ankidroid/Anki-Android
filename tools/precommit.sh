#/bin/bash

for file in AnkiDroid/src/main/res/values/{0,1}*xml;
do
    ./tools/xml_autoformat.py $file > $file.back ;
    mv $file.back $file;
done

# .git/hooks/pre-commit should contain

# #/bin/bash
#
# ./tools/precommit.sh

