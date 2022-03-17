AnkiDroid is moving to Kotlin.

You should use Kotlin for all new files.

## Conversion

We have developed a nonstandard process to convert existing files to Kotlin. Rationale is below.

## Process

The process may seem quite long. However, it's only because we are detailling every single step here. Once you did it a few time, you'll see it's quite quick.

### Choosing a file and confirming you can edit it

* Choose some java file `filename.java`. Check in the [pull requests](https://github.com/ankidroid/Anki-Android/pulls) that nobody started converting it,
* Open `filename.java` in Android Studio,
* Select the `Code > Convert Java File to Kotlin File` functionality (or `ctrl + alt + shift + k`),
* When asked "Some code in the rest of your project may require corrections after performing this conversion. Do you want to find such code and correct it too?", answer "Yes",
* Build the project (If you edited a test file, you must build the test),
* If the build fails, check whether you can correct the errors. 

#### What to do if work is needed in other files.


If you need to change other files, please discuss it with the maintainers.

The typical solutions are:

* Convert other files first,
* Change other files:
   *  This may be worth doing in a separate pull request to ensure the Kotlin migration PR only contains automatic changes.

### Commiting the change

* Select `Git > Commit` (`ctrl + k`).
  * **First time only**: If "Commit Changes" opens in a window, no change is required. If the commit open in a side pane, click on the gear icon and select "Switch to commit dialog",
* Select `Extra commit for java > kt renames`,
* Enter the commit message "Migrate `[filename]` to Kotlin",
* Click "Commit",
* If you get warning, you can click "Commit" again. Most warnings are not actually related to this commit and can be ignored. 

This creates two commits:

1. `rename .java to .kt`: renames `filename.java` to `filename.kt` without changing its content,
2. `Migrate [filename] to Kotlin`: converts the content of `filename.kt` to Kotlin

### Ensuring the first commit build

#### Access the first commit
You need to use git's interactive rebase to change the first commit. To do so in Android Studio:

* Open the git tab in the bottom toolbar. `Alt + 9`,
* Select 'log',
* Right click on the bottom commit "rename .java to .kt",
* Click on "Interactively Rebase from here",
* Right click on the top commit "Rename .java to .kt",
* Click on "Stop To Edit",
* Click on "Start rebasing".

#### Edit the first commit

While you are still in `filename.java`:
* Right click on the file name on top of the window,
* Click on "Copy Path…",
* Click on "Path from Source Root".

In the file `kotlinMigration.gradle`
* Find the line `def className = ""`,
* Add a / after the first double quote,
* Paste the filename you copied earlier between the two double-quotes.  The result should be something as
```gradle
def className = "/com/ichi2/anki/filename.kt"
```

If you changed a TEST or an ANDROID_TEST file:
* Find the line `def source = Source.MAIN`,
* replace `MAIN` by `TEST` or by `ANDROID_TEST`.

Test that everything compiles:
* Select `File > Sync Project with Gradle Files`,
* Select `Build > Rebuild project`.

If the build fails, there was an error in some previous step. Do not hesitate to ask for help if needed. Otherwise, you can continue. 

Android Studio will display errors in the `.kt` file, this is due to it containing Java code. This is not a problem if the build succeeds.

#### Amend the first commit and end rebasing

If you already know how to amend and end rebasing in git, great. Otherwise, follow those steps:

* Select `Git > Commit` (`ctrl + k`),
* Toggle on "Amend commit",
* Ensure that `kotlinMigration.gradle` is selected,
* Click on "Commit",
* Select `Git > Rebase > Continue`.

### Edit the second commit

#### Reverting kotlinMigration.gradle
* Open `kotlinMigration.gradle` if it is not already opened,
* Find the first line you edited. The one that starts with `def className = "`,
* Replace this line by `def className = ""` (that is: replace the path of the file and set the variable to an empty string),
* If you are working on tests, find the second line you edited. Set it back to `def source = Source.MAIN`.

#### Amending the second commit
This can be done exactly as in "Amending the first commit" section above.

### Pull Request
* Send in a PR. One PR per file conversion. The PR should contain two commits, both modifying `kotlinMigration.gradle`,
* Maintainers need to **rebase** this pull request.

## Rationale

Our aims for the Kotlin migration process are:

1. We want `git blame` to understand java -> Kotlin conversion,
2. We want `git bisect` to work during the java -> Kotlin conversion.

### Android Studio Conversion issues

Android Studio provides a `Extra commit for java > kt renames` option, which fits (1), but the commit doesn't compile, breaking (2).

### Workaround

To work around this issue, a Gradle script: `kotlinMigration.gradle` is used. The script accepts a single file to compile as java, despite a `.kt` extension.

To define this renamed file, set the `className` variable to the relative file path.

A valid path is:

`def className = "/com/ichi2/anki/AbstractFlashcardViewer.kt"`.

In Android Studio right-click the converted Kotlin file, select `Copy Path > From Source Root` and prefix the path with `/`


#### Tests/Android Tests

Set `def source = Source.TEST/ANDROID_TEST` and keep `className` in the form: `/com/ichi2/...`.

### Workaround (second commit)

Reset the path in `kotlinMigration.gradle` to the empty string. The path is no longer required due to the file conversion commit.

## References

* [Example Pull Request](https://github.com/ankidroid/Anki-Android/pull/9738/commits)
* [Further history of kotlinMigration.gradle](https://github.com/ankidroid/Anki-Android/pull/9480)
