AnkiDroid is moving to Kotlin.

You should use Kotlin for all new files.

## Conversion

We have developed a nonstandard process to convert existing files to Kotlin. Rationale is below

## Process

* Select the `.java` file in Android Studio and use the `Convert Java File to Kotlin File` functionality
* Commit, selecting `Extra commit for java > kt renames`
* Git rebase the first commit (`rename .java to .kt`)
    * Android Studio: `Open the Git tab in the bottom toolbar (Alt + 9) > Select 'log' > Right click on the bottom commit (rename .java to .kt) > Click on Interactively Rebase from here > Right click on the top commit > Stop To Edit (Alt + E) > Start Rebasing`
* Add the source-root relative path of the file to `kotlinMigration.gradle`
* `File > Sync Project with Gradle Files`, then `Build > Rebuild` to ensure the commit builds
* Amend the first commit with the change to `kotlinMigration.gradle`
* Finish the rebase: `git rebase --continue`
* Amend the conversion commit, reverting `kotlinMigration.gradle` (set the path to an empty string, and the source to `MAIN`)
* Build to confirm the automated conversion was successful
* Send in a PR. One PR per file conversion. The PR should contain two commits, both modifying `kotlinMigration.gradle`
* Maintainers need to **rebase** this pull request

## Rationale

Our aims for the Kotlin migration process are:

1. We want `git blame` to understand java -> Kotlin conversion
2. We want `git bisect` to work during the java -> Kotlin conversion.

### Android Studio Conversion issues

Android Studio provides a `Extra commit for java > kt renames` option, which fits (1), but the commit doesn't compile, breaking (2).

### Workaround

To work around this issue, a Gradle script: `kotlinMigration.gradle` is used. The script accepts a single file to compile as java, despite a `.kt` extension.

To define this renamed file, set the `className` variable to the relative file path.

A valid path is:

`def className = "/com/ichi2/anki/AbstractFlashcardViewer.kt"`

In Android Studio right-click the converted Kotlin file, select `Copy Path > From Source Root` and prefix the path with `/`


#### Tests/Android Tests

Set `def source = Source.TEST/ANDROID_TEST` and keep `className` in the form: `/com/ichi2/...`

### Workaround (second commit)

Reset the path in `kotlinMigration.gradle` to the empty string. The path is no longer required due to the file conversion commit.

## References

* [Example Pull Request](https://github.com/ankidroid/Anki-Android/pull/9738/commits)
* [Further history of kotlinMigration.gradle](https://github.com/ankidroid/Anki-Android/pull/9480)