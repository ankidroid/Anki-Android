#!/bin/bash

pushd res
pushd ../docs/marketing/icons/blue

/bin/cp drawable/anki.png ~1/drawable/

/bin/cp drawable-ldpi/anki.png ~1/drawable-ldpi/

/bin/cp drawable-mdpi/anki.png ~1/drawable-mdpi/
/bin/cp drawable-mdpi/widget_bg_small.png ~1/drawable-mdpi/
/bin/cp drawable-mdpi/widget_bg_small_finish.png ~1/drawable-mdpi/
/bin/cp drawable-hdpi/anki.png ~1/drawable-hdpi/
/bin/cp drawable-hdpi/widget_bg_small.png ~1/drawable-hdpi/
/bin/cp drawable-hdpi/widget_bg_small_finish.png ~1/drawable-hdpi/
/bin/cp drawable-xhdpi/anki.png ~1/drawable-xhdpi/
/bin/cp drawable-xhdpi/widget_bg_small.png ~1/drawable-xhdpi/
/bin/cp drawable-xhdpi/widget_bg_small_finish.png ~1/drawable-xhdpi/

popd
popd
