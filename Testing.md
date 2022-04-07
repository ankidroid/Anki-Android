This document explains how to create tests for AnkiDroid. Please see the [development guide](Development-Guide#running-automated-tests) to find how to run tests.

- Performance: Verifying correct behavior even if it is slow is the priority. We are not too concerned with test execution time but as tests do run quite frequently, if you intend to create a very long running test please communicate with us as we may need to partition the test suite into "small" and "large" tests, annotate the large test and only run it under specific conditions.

- Coverage: Ideally, we want to cover most of the code. We don't expect to reach 100%, however, 36% (as of March 2021) is certainly too low. [More about code coverage](Development-Guide#code-coverage). Please feel free to make PR with more tests in them. Of course, tests must succeed.

The remaining documents is about best practice to write tests for AnkiDroid. You may find past code not following those best practice, feel free to correct them, in particular if your change makes tests quicker. Please also get a look at our [code style](Code-style).

# Values
## Reused values
Please avoid recreating variables if possible. Uses a `static final` variable to save values that never changes. We expect the content of those variables not to be changed by tests. If really necessary, it can be enforced with [Collections.unmodifiable](https://docs.oracle.com/javase/7/docs/api/java/util/Collections.html#unmodifiableList) but we generally don't do it as it would call an extra function, add more codes and we have not yet had a problem that it would have solved.

## The collection
If you need to access the collection in your test you may extend the `RobolectricTest` class (more details below). Then you must access the collection by `getCol()`. You'll get a freshly initialized collection in each unit test, that is, only the default deck, no card... On the embedded test you'll get the device real collection, so any change you make here could have impact on the collection, or could fail if for some reason there was an interaction between the real collection and the tests. E.g. better make a backup of/rename your "ankidroid" folder before running the tests.

## Testing hypothesis
Methods are only tested with inputs that they are supposed to accept. You can refer to a method documentation, type and annotations to check what the method is supposed to accept. For example if a function takes an argument a "card type" value, we know it's a value between 0 and 3 and we do not test it on other values. 

### Null
If a method parameter is annotated with `@NonNull`, we do not want a test to send it a `null` value. On the other hand if it is annotated with `@Nullable` (in Java) or has a type ending with "?" (in Kotlin) we want to have test checking the case where the value is `null`. In a Java file, if there is no annotation and the type is not a scalar, then it is good practice to add the annotation or to [migrate](https://github.com/ankidroid/Anki-Android/blob/main/docs/kotlin-migration.md) the file to Kotlin

## Time
Unit tests are run with simulated time. The collection is created the 7th of August 2020 at 7 hour, UTC. Each time the time is checked, it advanced by 10 milliseconds. You can also use 
```java
MockTime timeManager = (MockTime) col.getTime();
timeManager.addD(1);
timeManager.addM(3);
```
to add 1 day and 3 minutes to the simulated time

# Testing styles

## Pure Unit Tests

These are tests that verify a portion of logic without needing access to any persistent storage (collection) or device-specific features (API-specific verification etc). An example would be a sorting algorithm or similar - these may be tested in a pure unit testing mode with no special test runners. These tests are in the `src/tests/` directory, but do not have the `AndroidJUnit4` runner and do not extend the `RobolectricTest` object

## Robolectric Unit Tests

Many of the tests rely on Android APIs or on having the collection database available. Pure unit tests do not provide access to Android APIs and do not initialize collections, so we have implemented a `RobolectricTest` object you may extend that provides several useful testing utilities and provides your test with a freshly initialized collection to start. These tests are in the `src/tests/` directory, they use the `AndroidJUnit4` runner and extend the `RobolectricTest` class.

The general pattern here is to then create a test data scaffold in the new collection, execute the code under test, then verify the test data has been altered in the way you expect with assertions

There are also facilities (provided by Robolectric) to start Activities and verify correct behavior during the Android Activity Lifecycle (for instance to make sure code works across pause/resume) and the ability to access View elements to verify UI behavior.

## On-Device Tests

These tests are usually not needed but occasionally functionality may only be verified when executing on an actual running Android instance (device or emulator). These tests live in `src/androidTest` and are typically very low performance but if there is no alternative to on-device verification, this is the place to put the test.

Be careful with these tests (both running them, and designing them) as the collection you operate on will either be the live collection of another developer running the tests, or will be the test harness data from existing on-device import/export tests and so may contain information you don't expect. To be more specific: if you create data make sure it is completely separate and easy to identify from any other existing data, carefully clean your tests data and only your test data up when your test is done, and if you create test data make sure you make no assumptions about it being the only data in the collection.

When you run on-devices tests, you may want to run them on a new collection. To do so, rename your folder `AnkiDroid` to any other name, such as `AnkiDroidBackup`. Once you are done testing, delete the `AnkiDroid` folder that was created during the test and rename `AnkiDroidBackup`  to `AnkiDroid` to get back your collection.

# Writing Good Tests

## Good Failure Messages
One goal of a test is to have an easy to read error message when it fails. Most tests methods allow to take a string as first parameter to explain what is tested. Please fill it unless the meaning of the test is obvious. While it is a low priority, PR adding reasons to tests are a nice addition to the codebase.

The expected value is always in first position and the result of a function in last position. Pay close attention to it, as there is no way for Java type system to check whether the meaning is respected. Experiences shows that it is some very common error.

This also means that we have a preferences for tests that give detailed error messages. As an example `org.junit.Assert.assertArrayEquals` do not only fail when two arrays differ, it also state whether the size is different or the first differing position. Similarly, `com.ichi2.utils.ListUtil.assertListEquals` allow to tests list equality.

For standards arithmetical properties, we uses hamcrest. E.g.
```java
        assertThat("At least one card added for note", col.addNote(newNote), is(greaterThanOrEqualTo(1)));
```
is a clear and readable way to check that a number is greater than or equal to one and explain what is tested. Please familiarize yourselves with the variety of standard tests it offers


# Background / Async tasks


AnkiDroid (and generally any software with a graphical user interface) is multi-threaded. While the main thread display the UI, any non-trivial tasks is send in a background thread to be executed without blocking the UI. However, unit tests traditionally interacts badly with multi-threading. In order to avoid this, by default, all backgrounds tasks are run in foreground during unit test.

If for some reason (e.g. tasks related to missing collection or broken database)
, you need to actually run background tests in background, you can call `RobolectrictTest`'s method `runTasksInBackground()`. In this case, you may find your test fails because it created a background task, and the failure message may include a message from the Robolectric framework indicating there were "pending tasks on the main looper" or similar. This is a sign of the classic asynchronous / multi-thread race condition, and means that assertions sometime may be checked before the code you are testing executed and finished. If this happens, you can  uses `advanceRobolectricLooperWithSleep` or `advanceRobolectricLooper` to ensure that a task is executed before moving on.

### Parameterized Tests (Enclosed Runner)

You may encounter few parameterized tests in the codebase. Parameterized tests allow a developer to run the same test over and over again using different values. The way to implement them can be seen [here](https://junit.org/junit4/javadoc/4.12/org/junit/runners/Parameterized.html) with an example. 
Note : To run parameterized and non-parameterized tests in the same class, one must be familiar with the concept of [Enclosed](https://junit.org/junit4/javadoc/latest/org/junit/experimental/runners/Enclosed.html) runner. To make such a  class, annotate the class with `@RunWith(Enclosed.class)`. Then, two inner classes can be created. One of them must be annotated with `@RunWith(Parameterized.class)` and the another must be kept non-annotated. By this way, we test both the classes at the same time without the need to create two separate classes. To see an example in the current codebase, have a look at this [class](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/test/java/com/ichi2/anki/analytics/AnalyticsConstantsTest.java).  
