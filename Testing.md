This document, which is a work in progress, will cover our current practice in terms of test. Please see the [development guide](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#running-automated-tests) to find how to run those tests.

There is a trade-off when it comes to test. Each new tests makes tests slightly slower. Currently, all tests are run on each pull request and each time a new commits are made on PR, this means that all tests must be quick. Please discuss with us if you have a really good reason to create a slow test. Even 2 seconds of tests would be a huge cost, given the number of time tests run.

Ideally, we want to cover most of the code. We don't expect to reach 100%, however, 36% (as of March 2021) is certainly too low. [More about code coverage](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#code-coverage). Please feel free to make PR with more tests in them. Of course, tests must succeed.

The remaining documents is about best practice to write tests for AnkiDroid. You may find past code not following those best practice, feel free to correct them, in particular if your change makes tests quicker. Please also get a look at our [code style](https://github.com/ankidroid/Anki-Android/wiki/Code-style).

# Values
## Reused values
Please avoid recreating variables if possible. Uses a `static final` variable to save values that never changes. We expect the content of those variables not to be changed by tests. If really necessary, it can be enforced with [Collections.unmodifiable](https://docs.oracle.com/javase/7/docs/api/java/util/Collections.html#unmodifiableList(java.util.List) but we generally don't do it as it would call an extra function, add more codes and we have not yet had a problem that it would have solved.

## The collection
We always access the collection by `getCol()`. You'll get a new collection in each unit test, that is, only the default deck, no card... On the embedded test you'll get the device real collection, so any change you make here could have impact on the collection, or could fail if for some reason there was an interaction between the real collection and the tests. E.g. better make a backup of/rename your "ankidroid" folder before running the tests.

## Testing hypothesis
We restrict tests to cases the function's javadoc and annotation state they should accept. For example, if a function takes an argument a "card type" value, we know it's a value between 0 and 3 and we do not test it with other values. On the other hand, if the returned value is a "card type" we want to check that it is indeed between 0 and 3, and generally we want to check the exact returned values.

### Null
If a method parameter is annotated with `@NonNull`, we do not want a test to send it a `null` value. On the other hand if it is annotated with `@Nullable` we want to have test checking the case where the value is `null`. If there is no annotation and the type is not a scalar, then it is good practice to add the annotation. 

## Time
Unit tests are run with simulated time. The collection is created the 7th of August 2020 at 7 hour, UTC. Each time the time is checked, it advanced by 10 milliseconds. You can also use the MockTime class to change the date to any time you decide.