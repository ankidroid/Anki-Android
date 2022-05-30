# update mathjax and jquery js library with Anki Desktop

# ensure some basic tools are installed
sudo apt install bash grep findutils curl gcc g++ git

# install bazelisk
curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.10.1/bazelisk-linux-amd64 -o ./bazel
chmod +x bazel && sudo mv bazel /usr/local/bin/

# clone Anki
git clone https://github.com/ankitects/anki ~/tmp/anki

# build for mathjax and jquery
cd ~/tmp/anki/qt/aqt/data/web/js/vendor; bazel build vendor

# change dir to AnkiDroid
cd /home/runner/work/Anki-Android/Anki-Android

# copy latest jquery to assets dir
cp ~/tmp/anki/.bazel/bin/qt/aqt/data/web/js/vendor/jquery.min.js AnkiDroid/src/main/assets/jquery.min.js

# remove old mathjax file
rm -rf AnkiDroid/src/main/assets/mathjax
mkdir AnkiDroid/src/main/assets/mathjax

# copy latest mathjax to assets dir
cp -r ~/tmp/anki/.bazel/bin/qt/aqt/data/web/js/vendor/mathjax AnkiDroid/src/main/assets/
