#!/bin/bash

# * Start from a branch with no staged files
# * In Android studio, choose a java file
# * Select the Code > Convert Java File to Kotlin File functionality (or ctrl +
#   alt + shift + k),
# * When asked "Some code in the rest of your project may require corrections
#   after performing this conversion. Do you want to find such code and correct
#   it too?", answer "Yes",
# * Build the project (If you edited a test file, you must build the test),
# * If the build fails, check whether you can correct the errors.
# * In a terminal, from git root repository, run ./tools/migrate.sh (Linux/macOS)
# * Do `git push`
# * Open your pull request in AnkiDroid's github.

function sedcompat() {
  # shellcheck disable=SC2154
  # unamestr is referenced but not assigned
  if [[ $unamestr == 'Darwin' ]]; then
    #specific case for Mac OSX
    sed -E -i '' "$1" "$2"
  else
    sed -i "$1" "$2"
  fi
}

# Getting file paths
#########################

# Deleted, Renamed and/or Modified file(s)
# ------------
# The deleted/renamed file(s). Normally a single one.
DELETED=$(git status | grep "deleted:" | sed "s/[ \t]*deleted:[ \t]*//" | awk '$1=$1')
echo "DELETED='$DELETED'"

RENAMED=$(git status | grep "renamed:" | sed "s/[ \t]*renamed:[ \t]*//" | awk -F '->' '{print $1}' | awk '$1=$1')
echo "RENAMED='$RENAMED'"

git add . #Assume all changes are intentional and stage them, is verified by user later.

MODIFIED=$(git status | grep "modified:" | sed "s/[ \t]*modified:[ \t]*//" | awk '$1=$1')
echo "MODIFIED='$MODIFIED'"

# Checking there is a single deleted, renamed and/or modified file(s)
NB_DELETED=$(git status | grep -c "deleted:")
echo "NB_DELETED=$NB_DELETED"

NB_RENAMED=$(git status | grep -c "renamed:")
echo "NB_RENAMED=$NB_RENAMED"

NB_MODIFIED=$(git status | grep -c "modified:")
echo "NB_MODIFIED=$NB_MODIFIED"

if [[ $NB_DELETED -lt 1 && $NB_RENAMED -lt 1 ]]; then
  echo "******************************"
  echo "* No file deleted or renamed *"
  echo "******************************"
  exit 1
elif [[ $NB_DELETED -gt 1 || $NB_RENAMED -gt 1 ]]; then
  echo "*************************************************"
  echo "* More than one file either deleted or renamed! *"
  echo "*************************************************"
  exit 1
elif [[ $NB_DELETED -ge 1 && $NB_RENAMED -ge 1 ]]; then
  echo "*******************************************"
  echo "* More than one file deleted AND renamed! *"
  echo "*******************************************"
  exit 1
elif [[ ($NB_RENAMED == 1 && $NB_MODIFIED -ge $NB_RENAMED) || ($NB_DELETED == 1 && $NB_MODIFIED -gt $NB_DELETED) ]]; then
  git status | grep "modified:"
  echo "************************************************************"
  echo "* Additional file(s) modified!                             *"
  echo "* Please Enter y to confirm these are all required changes *"
  echo "* Enter any other key to exit                              *"
  echo "************************************************************"

  read -r CONFIRMATION
  if [[ $CONFIRMATION != "y" ]]; then
    echo "Status: Exit"
    echo "Please commit or reset/restore the unintended changes"
    exit 1
  fi
fi

# The file path without extension
if [[ $NB_DELETED == 1 ]]; then
  FILEPATH=${DELETED//.java/}
elif [[ $NB_RENAMED == 1 ]]; then
  FILEPATH=${RENAMED//.java/}
fi
echo "FILEPATH='$FILEPATH'"

# Checking that the file is Java
if [[ ($NB_DELETED == 1 && $DELETED != "$FILEPATH.java") || ($NB_RENAMED == 1 && $RENAMED != "$FILEPATH.java") ]]; then
  echo "************************************"
  echo "* Deleted/Renamed file is not Java *"
  echo "************************************"
  exit 1
fi

# The added file is $FILEPATH.kt
if [[ $NB_DELETED == 1 ]]; then
  FILEPATH_JAVA=$DELETED
elif [[ $NB_RENAMED == 1 ]]; then
  FILEPATH_JAVA=$RENAMED
fi

# Added files
# ------------

# The added file(s). Normally a single one.
if [[ $NB_RENAMED -ge 1 ]]; then
  ADDED=$(git status | grep "renamed:" | sed "s/[ \t]*renamed:[ \t]*//" | awk -F '->' '{print $2}' | awk '$1=$1')
else
  ADDED=$(git status | grep "new file" | sed "s/[ \t]*new file:[ \t]*//" | awk '$1=$1')
fi
echo "ADDED='$ADDED'"

# Checking there is a single file added
if [[ $NB_RENAMED -ge 1 ]]; then
  NB_ADDED="$NB_RENAMED" #renaming inherently means that a new file was added
else
  NB_ADDED=$(git status | grep -c "new file:")
fi
echo "NB_ADDED=$NB_ADDED"

if [[ $NB_ADDED -lt 1 ]]; then
  echo "********************************************************************************"
  echo "* No file added (you may have to add the new file manually if it was not done) *"
  echo "********************************************************************************"
  exit 1
elif [[ $NB_ADDED -gt 1 ]]; then
  echo "****************************"
  echo "* More than one file added *"
  echo "****************************"
  exit 1
fi

# The added file is $FILEPATH.kt
FILEPATH_KT=$ADDED

# Checking that the file added is the same as the one removed/renamed
if [[ $ADDED != "$FILEPATH.kt" ]]; then
  echo "********************************************************"
  echo "* Added file is not the deleted/renamed file in kotlin *"
  echo "********************************************************"
  exit 1
fi

# Computing values for AnkiDroid/kotlinMigration.gradle and
# commit messages.
# ----------------------------------

# The file name, without its directory
FILENAME=$(basename -- "$FILEPATH")
echo "FILENAME=$FILENAME"

# SOURCE is the value for "source" variable in AnkiDroid/kotlinMigration.gradle
if [[ $FILEPATH == *AnkiDroid/src/main* ]]; then
  SOURCE=MAIN
elif [[ $FILEPATH == *AnkiDroid/src/test* ]]; then
  SOURCE=TEST
elif [[ $FILEPATH == *AnkiDroid/src/androidTest* ]]; then
  SOURCE=ANDROID_TEST
else
  echo "********************************************************************************"
  echo "* The added/deleted file is not in AnkiDroid/src/' main or test or AndroidTest *"
  echo "********************************************************************************"
  exit 1
fi
echo "SOURCE='$SOURCE'"

# The path, as we want it in "SOURCE" in AnkiDroid/kotlinMigration.gradle.
# That is, the path after AnkiDroid/src/(main|test|androidTest)/java
# shellcheck disable=SC2001
PATH_IN_SOURCE=$(echo "$FILEPATH" | sed "s:^AnkiDroid/src/.*/java::")
echo "PATH_IN_SOURCE=$PATH_IN_SOURCE"

# The package name of the file modified
PACKAGE_NAME=$(echo "$PATH_IN_SOURCE" | sed "s:/:.:g" | sed "s:^.::")
echo "PACKAGE_NAME=$PACKAGE_NAME"

############
# git work #
############

# Stashing all change to create first commit
git stash
echo "stash"

# Creating first commit
#########################
# Moving .java to .kotlin without actually converting the file
git mv "$FILEPATH_JAVA" "$FILEPATH_KT"
echo "mv .java .kt"
# In sed, instead of "/", we use ":" as separator. This is because "/" are in filepath while ":" are not
# Modifying className value in kotlinMigration.gradle
sed -i.bak "s:def className = .*:def className = \"$PATH_IN_SOURCE.kt\":" AnkiDroid/kotlinMigration.gradle
echo "change className"
# Modifying source value in kotlinMigration.gradle
sed -i.bak "s:def source = .*:def source = Source.$SOURCE:" AnkiDroid/kotlinMigration.gradle
echo "change source"
# Creating the first commit
git add AnkiDroid/kotlinMigration.gradle
echo "add kotlinMigration.gradle"
git commit -m "refactor: Rename $FILENAME.java to .kt

$PACKAGE_NAME"
echo "first commit"

## Creating second commit
#########################
# Removing the java file with .kt name to be able to pop the actual kotlin file
git rm "$FILEPATH_KT"
echo "remove .kt"
# Getting back all files stashed above
git stash pop
echo "pop"
# We must add the new file explicitly, as it won't be seen by `git -a`
git add "$FILEPATH_KT"
echo "add kt"
# Setting back className in kotlinMigration.gradle to its original value
sed -i.bak "s:def className = .*:def className = \"\":" AnkiDroid/kotlinMigration.gradle
echo "reset className"
# Setting back source in kotlinMigration.gradle to its original value
sed -i.bak "s:def source = .*:def source = Source.MAIN:" AnkiDroid/kotlinMigration.gradle
echo "reset source"
# No need to add explicitly kotlinMigration.gradle in git commit, since we use `git -a`

# Adding all files because the commit must contains all modifications made in other file
# to use the conversion of FILENAME.
git commit -am "refactor: Convert $FILENAME to Kotlin

$PACKAGE_NAME"
echo "second commit"
rm AnkiDroid/kotlinMigration.gradle.bak || true
echo "optional cleanup"

echo "*************************"
echo "* Successful Migration! *"
echo "*************************"

git log --oneline -3
