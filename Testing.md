This document explains how to create tests for AnkiDroid. Please see the [development guide](Development-Guide#running-automated-tests) to find how to run tests.

- Performance: Verifying correct behavior even if it is slow is the priority. We are not too concerned with test execution time but as tests do run quite frequently, if you intend to create a very long running test please communicate with us as we may need to partition the test suite into "small" and "large" tests, annotate the large test and only run it under specific conditions.

- Coverage: Ideally, we want to cover most of the code. We don't expect to reach 100%, however, 36% (as of March 2021) is certainly too low. [More about code coverage](Development-Guide#code-coverage). Please feel free to make PR with more tests in them. Of course, tests must succeed.

The remaining documents is about best practice to write tests for AnkiDroid. You may find past code not following those best practice, feel free to correct them, in particular if your change makes tests quicker. Please also get a look at our [code style](Code-style).

# Values
## Reused values
Please avoid recreating variables if possible. Uses a `static final` variable to save values that never changes. We expect the content of those variables not to be changed by tests. If really necessary, it can be enforced with [Collections.unmodifiable](https://docs.oracle.com/javase/7/docs/api/java/util/Collections.html#unmodifiableList(java.util.List) but we generally don't do it as it would call an extra function, add more codes and we have not yet had a problem that it would have solved.

## The collection
If you need to access the collection in your test you may extend the `RobolectricTest` class (more details below). Then you must access the collection by `getCol()`. You'll get a freshly initialized collection in each unit test, that is, only the default deck, no card... On the embedded test you'll get the device real collection, so any change you make here could have impact on the collection, or could fail if for some reason there was an interaction between the real collection and the tests. E.g. better make a backup of/rename your "ankidroid" folder before running the tests.

## Testing hypothesis
We restrict tests to verifying a function's javadoc and annotation accept what they state they should accept without verifying input is obviously outside the javadoc or annotation contract. For example, if a function takes an argument a "card type" value, we know it's a value between 0 and 3 and we do not test it with other values. On the other hand, if the returned value is a "card type" we want to check that it is indeed between 0 and 3, and generally we want to check the exact returned values.

### Null
If a method parameter is annotated with `@NonNull`, we do not want a test to send it a `null` value. On the other hand if it is annotated with `@Nullable` we want to have test checking the case where the value is `null`. If there is no annotation and the type is not a scalar, then it is good practice to add the annotation. 

## Time
Unit tests are run with simulated time. The collection is created the 7th of August 2020 at 7 hour, UTC. Each time the time is checked, it advanced by 10 milliseconds. You can also use the MockTime class to change the date to any time you decide.

# Testing styles

## Pure Unit Tests

These are tests that verify a portion of logic without needing access to any persistent storage (collection) or device-specific features (API-specific verification etc). An example would be a sorting algorithm or similar - these may be tested in a pure unit testing mode with no special test runners. These tests are in the `src/tests/` directory, but do not have the `AndroidJUnit4` runner and do not extend the `RobolectricTest` object

## Robolectric Unit Tests

Many of the tests rely on Android APIs or on having the collection database available. Pure unit tests do not provide access to Android APIs and do not initialize collections, so we have implemented a `RobolectricTest` object you may extend that provides several useful testing utilities and provides your test with a freshly initialized collection to start. These tests are in the `src/tests/` directory, they use the `AndroidJUnit4` runner and extend the `RobolectricTest` class.

The general pattern here is to then create a test data scaffold in the new collection, execute the code under test, then verify the test data has been altered in the way you expect with assertions

There are also facilities (provided by Robolectric) to start Activities and very correct behavior during the Android Activity Lifecycle (for instance to make sure code works across pause/resume) and the ability to access View elements to verify UI behavior.

## On-Device Tests

These tests are usually not needed but occasionally functionality may only be verified when executing on an actual running Android instance (device or emulator). These tests live in `src/androidTest` and are typically very low performance but if there is no alternative to on-device verification, this is the place to put the test.

Be careful with these tests (both running them, and designing them) as the collection you operate on will either be the live collection of another developer running the tests, or will be the test harness data from existing on-device import/export tests and so may contain information you don't expect. To be more specific: if you create data make sure it is completely separate and easy to identify from any other existing data, carefully clean your tests data and only your test data up when your test is done, and if you create test data make sure you make no assumptions about it being the only data in the collection

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

You may find your test fails because it created a background task, and the failure message may include a message from the Robolectric framework indicating there were "pending tasks on the main looper" or similar.

Currently, any call to background tasks really starts a new thread. This sets up a classic asynchronous / multi-thread race condition, and means that assertions sometime may be checked before the code you are testing executed and finished. If this happens, you can either: 
* uses `advanceRobolectricLooperWithSleep` or `advanceRobolectricLooper` to ensure that a task is executed before moving on
* calls `runTasksInForeground` to ensure that tasks are executed in the main thread. 

Once #8442 is merged, this section should be updated to indicate that the default changed, and that tasks will be run by default on foreground and that if required, some tasks should be run in background (e.g. tasks related to missing collection or broken database)

