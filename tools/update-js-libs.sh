# update mathjax and jquery js library with Anki Desktop

# path variable
ANKI_SRC=~/tmp/anki
ANKIDROID_SRC=/home/runner/work/Anki-Android/Anki-Android

# ensure some basic tools are installed
sudo apt install bash grep findutils curl gcc g++ git

# install bazelisk
curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.10.1/bazelisk-linux-amd64 -o ./bazel
chmod +x bazel && sudo mv bazel /usr/local/bin/

# clone Anki
git clone https://github.com/ankitects/anki $ANKI_SRC

# build for mathjax and jquery
cd $ANKI_SRC/qt/aqt/data/web/js/vendor
bazel build vendor

cd $ANKI_SRC/ts 
bazel build mathjax

# copy latest jquery to assets dir
cp $ANKI_SRC/.bazel/bin/qt/aqt/data/web/js/vendor/jquery.min.js $ANKIDROID_SRC/AnkiDroid/src/main/assets/jquery.min.js

# remove old mathjax file
rm -rf $ANKIDROID_SRC/AnkiDroid/src/main/assets/mathjax/*

# copy latest mathjax to assets dir
cp -r $ANKI_SRC/.bazel/bin/qt/aqt/data/web/js/vendor/mathjax $ANKIDROID_SRC/AnkiDroid/src/main/assets/

cp $ANKI_SRC/.bazel/bin/ts/mathjax/index.js $ANKIDROID_SRC/AnkiDroid/src/main/assets/mathjax/conf.js

# replace paths in conf.js for AnkiDroid
ANKI_MATHJAX_DIR="_anki/js/vendor/mathjax"
ANKIDROID_MATHJAX_DIR="android_asset/mathjax"
MATHJAX_CONF_JS=$ANKIDROID_SRC/AnkiDroid/src/main/assets/mathjax/conf.js

# check if the path exists in conf.js file or not, otherwise exit
if grep "$ANKI_MATHJAX_DIR" $MATHJAX_CONF_JS
then
    sed -i "s|$ANKI_MATHJAX_DIR|$ANKIDROID_MATHJAX_DIR|" $MATHJAX_CONF_JS
else
    echo "MathJax path does not exist in conf.js file, it is changed"
    exit 1
fi

# cleanup
rm $ANKIDROID_SRC/AnkiDroid/src/main/assets/mathjax/mathjax-cp.sh

# restore mathjax.css file
cd $ANKIDROID_SRC
git restore AnkiDroid/src/main/assets/mathjax/mathjax.css

# change mode of files in assets dir
find $ANKIDROID_SRC/AnkiDroid/src/main/assets/mathjax/ -type f -print0 | xargs -0 chmod 644
