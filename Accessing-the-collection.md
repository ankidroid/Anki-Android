This page explains how AnkiDroid Kotlin code should access the collection. It explains how to write new code that request access to the collection, and how to migrate currently existing code that access it. 

# The goal

Our main goal is to ensure that the collection do not get closed while we must use it, and that no code try to access the collection while it is closed. This avoids various subtile bug AnkiDroid used to have. 

# How do I use the collection in my code?

In this part, we explain what a developer needs to know when manipulating the collection

## How should collection be accessed

In order to achieve our goal, all code that must access the collection should do so either:
* using `withCol{myCode}`, where, in `myCode`, `this` represents the collection,
* using an instance of the collection that is given as function argument. 

For example,
```Kotlin
val count = withCol { decks.count() }
```
Note that `decks` here is a value from the Collection object.

## Restriction on the code around `withCol`

You should note that `withCol` is a [suspend function](https://kotlinlang.org/docs/coroutines-basics.html). If you are not familiar with Kotlin, be reassured, you should still be able to use `withCol` without a deep understanding of coroutines(even if reading the link above is highly suggested to discover the basics of Kotlin coroutines). 

The most standard way to call a coroutine function in AnkiDroid are:
* calling it inside a function which is marked `suspend` already
* putting `withCol` in a block that takes a coroutine block, such as:
 * `launchCatchingTask` which, as the name suggest, catch anything that is thrown (so you should ensure that you catch any exception you care about inside the block
 * or `withProgress`, that displays a progress bar, blocking the UI until the task is done.

## Restriction on the code inside `withCol` block.

Essentially, you can use `withCol` to read content from the collection or to edit the collection. While it is possible for some code to do both (for example, saving a card review and fetching the next card), for the sake of simplicity, we'll consider only those two cases.

### Accessing data

Each call to `withCol` should get some data needed and return it without using it. In the example above `val count = withCol { decks.count() }`, we only fetch the number of decks and then returns it. Whatever you need to do with this number of deck, you'll do it outside of `withCol` since the collection is not useful anymore.

### Writing data 

Each call to `withCol` should consider that the collection may be closed immediately after `withCol` ends. That means that you should always leave the collection in an acceptable state. For example, if you want to record that a card was reviewed, be sure to edit the log of review and the card in the same `withCol`. 

### What is forbidden inside `withCol`

A few things are generally forbidden inside `withCol`. 
* You should never touch the UI. 
* You should never suspend, as it would risk that two `withCol` function would get interleaved.
* In particular, it means you should never call `withCol` inside `withCol`. So once you get the collection, you should send it as method argument to any method that may need it, as you can't request it a second time.

## Exception

None of the rules described above apply to `com.ichi2.libanki.Storage` and `com.ichi2.anki.CollectionManager`. Those two classes are in charge of the low-level implementation of collection manipulation, and so are the only classes allowed to do what is forbidden everywhere else.

# Migrating non-compliant code

The system described above was created in Summer 2022, when AnkiDroid started to use coroutine and increased its used of Anki code. AnkiDroid has more than a decade of code that directly access the collection that needs to be migrated.

On the one hand, this is NOT a priority. AnkiDroid works correctly today, and, even if the current state may causes some bugs, they are rare enough that it is not worth it to stop all development to concentrate on this task.

On the other hand, this task is important for the future of AnkiDroid, and we must eventually do the migration. The good news is that most part of the migration should be simple enough that new contributors could find one use of access of collection outside of `withCol` and remove it. If you are a new contributors, it's a good way to help us, discover various part of the codebase, and, in the worst case, get constructive feedback from maintainers if you didn't do the migration correctly on the first try.

## End goal

Here we explain how me can consider that the migration was sucesfull. 
* com.ichi2.anki.CollectionHelper should probably disappear. If it can't disappear (this is yet to be determined), at least the methods `getCol*` should not be called anymore anywhere in the code; and subsequently should be deleted
* no object should get any reference to a collection. For example, currently (September 2022), com.ichi2.libanki.Card has `var col: Collection`. This collection is used, for example, to return the note associated to the card.

## How to migrate code

There are two ways to migrate code using a collection. We'll describe both and then explain how to chose the best one.

### Taking `col` as a method argument.

This is probably the simplest migration possible. Consider the function, in the class `Card`,
```kotlin
    open fun note(reload: Boolean): Note {
        if (note == null || reload) {
            note = col.getNote(nid)
        }
        return note!!
    }
```
where `col` is a member of `Card`.
You can replace it by 
```kotlin
    open fun note(col: Collection, reload: Boolean): Note {
        if (note == null || reload) {
            note = col.getNote(nid)
        }
        return note!!
    }
```
and search for all place where `note(reload)` was called, to add the collection as argument.

Once `col` is not used anywhere anymore, you can remove `col` from being a class member.

### withCol

Let's now consider that you see some code 
```kotlin
val col = CollectionHelper.instance.getCol(this)
foo(col)
```

If this code appears inside a suspend function, great, you can just replace it with `withCol {foo(this)}`.
Otherwise, you may need to do `launchCatchingTask{withCol{foo(this)}}`, assuming that you are fine with catching exceptions that `foo` may send.

### Which method to use

There is not yet an established best-practice, a fixed rules, to decide which of those two methods to use. In case of doubt, don't hesitate to ask for opinion from more experimented contributors on discord or on the github issue you are working on (probably #12439).

Here are a few rules of thumbs:

#### Transform `CollectionHelper`'s `getCol` into `withCol`

Indeed, in both case, we are simply getting a col from the current context

#### Back-end method should take the collection as an argument

Back-end is not exactly defined in AnkiDroid. If it's in `libanki` it's certainly back-end. Any method that only compute or save data should usually be considered back-end. Since those methods are generally not suspend function, they can't directly call `withCol` anyway; and we probably don't want to launch tasks in them. 

#### Methods in activities, creating a UI, responding to a user even, should take `withCol`.

Indeed, when you create a UI or respond to a user event, you should normally have no way to access the collection directly. So `withCol` seems mandatory here.

# How does it work

Now that you have read all of this wiki page, you may wonder how accessing the collection actually works internally. This is not a required reading, but may be interesting for any curious person. 

The class `com.ichi2.anki.CollectionManager` deals with opening and closing the collection. Each time a block requires the collection, it is added to a queue of tasks, each tasks being executed in received order. Those tasks may have to wait until the collection is actually available, since opening it may takes time.  