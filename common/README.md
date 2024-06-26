## AnkiDroid Common

AnkiDroid Common is a [Gradle module](https://developer.android.com/topic/modularization) 
containing utility functions, and definitions for core functionality used by other modules 
within AnkiDroid. Common should be the base of the AnkiDroid dependency tree.

Common is used by `libAnki` (which has no Android dependencies), so dependencies on the Android 
framework should be in packages named `android`.

This module is expected to define interfaces which are initialized in the `AnkiDroid` module

## Packages

### `com.ichi2.anki.common` 

Definitions/interfaces exposing core functionality e.g. `CrashReportService`, `UsageAnalytics`

These are to be initialized higher up the dependency tree, typically in `AnkiDroid`

### `com.ichi2.anki.common.utils`

Utility classes and methods without an Android dependency


### `com.ichi2.anki.common.utils.ext` 

Extension methods, universally applicable to the classes they extend

Examples:

* `Int.kt` - `ifNotZero`
* `InputStream.kt` - `convertToString`

### `com.ichi2.anki.common.utils.android` 

Utilities with a dependency on Android

## Context

This is a work in progress. As discussed in 
[#12582](https://github.com/ankidroid/Anki-Android/issues/12582), AnkiDroid decided to split the 
codebase into two modules, `libAnki` (business logic) and `AnkiDroid` (code interacting with 
Android APIs). 

At the time of writing, this split is not yet done. We expect to do it with the following steps:

* `com.ichi2.compat` was deemed to be an easy module to split out to trial this refactor 
 but this had circular dependencies
* A `common` module was proposed to fix this
* To reduce the execution time of tests, `libAnki` should have no dependencies on Android 
  * A lint rule will be applied to `libAnki` from using Android dependencies
  * The alternate: splitting modules based on architecture was deemed to be unwieldy 

The following were blockers for `compat` to be split out

* `isRobolectric`
* `CrashReportService`
* `showThemedToast`
* `TimeManager` (maybe)
* `@KotlinCleanup` (maybe)

Discussed on Discord: https://discord.gg/qjzcRTx

* Discussion: https://discord.com/channels/368267295601983490/701922522836369498/1243991110888591482
* Thread: https://discord.com/channels/368267295601983490/1244372448233914438
* https://github.com/ankidroid/Anki-Android/pull/16498
