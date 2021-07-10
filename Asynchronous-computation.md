In this document, you'll first learn how AnkiDroid deals with executing task asynchronously. The first part should be sufficient for any developer wanting to add a feature that require database access or non-trivial computation. The second part will give some context that may be useful to anyone wanting to do application wide architectural change.

This document might be of interest to you if you want to know more about `TaskDelegate`, `CollectionTask`, `ProgressSenderAndCancelListener` or `TaskListener`. It does not contain any information about ankiweb synchronization, which is done by a different process.


# What is an asynchronous task?

If you already know the answer, you can skip this session. This is intended for developers that are new to the concept and may not understand why we ask them to make some task asynchronous.

The user mainly see a graphical interface, that is, what is on screen, what they hear, and interacts with click or external keyboard. It is important for their experience that this never stop. If there is a gif for example, the animation should always continue. If they click on the previous button, they should go to the last screen even if the current screen is still waiting to get fully loaded. On the contrary it should cancel the current screen loading (or ask the user confirmation that they want to cancel, in which case the confirmation should be asked immediately)

The problem is that, sometime, you want to show the user something that you don't already know. Either you need to download an image or a sound, or you need to compute the average of a lot of elements from the database. It can take half a second - which is already noticeable - or even minutes. 

The solution is to ask the program to do two things at once. One thing, on the UI thread, is to continue to show the screen to the user and listen to its action. The other task, done by the background thread, is to download, to access the database, or whatever they have to do to get the data, or save the data, etc... then when the data is available, when the action is done, the background thread sends everything required (image, sound, the computed average) to the UI thread so that the UI thread can show it. In between the UI thread had to deal with the fact that the information is unknown and potentially let the user know that the background task is doing the work.

If the background task is long, it is current for it to indicate to the user that it has done N percent or that S seconds remain. Another role of the background task is to send those updates to the UI task. Usually, it serves to let the user know what to expect. However, it may have more creative use. For example, the background task can "update" the UI task with the downloaded image, so that the downloaded image can be shown immediately, and then put the image in a cache.

# How to do asynchronous computation in AnkiDroid

The main method you want to use is `TaskManager.launchCollectionTask`. As it is usual with code, you should search for use of this method in the codebase to see how it is used. Once you have read the theoretical documentation, seeing actual use should be most helpful

You can do
```java
class TaskToDoInBackground extends TaskDelegate<Void, Void> {
  new Void task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> psacl) {
    stuffToDo;
  }
}
```
then in a method, use 
```java
CollectionTask ct = TaskManager.launchCollectionTask(new TaskToDoInBackground())
``` 
to launch `stuffToDo` in background.  You can ignore the `Void`, we'll deal with them below. 

As the annotation indicate, it is assumed that the collection is not null. It is also assumed that it is opened.

## Cancelling
Let's say for some reason you want to cancel the task. Then you can then use `ct.cancel(false)` to ensure that the task is not executed if it is not already started (as starting a task may take some time), or `ct.cancel(true)` to ask to cancel it even if it already started.

Your task can use `psacl.isCancelled()` to check if it was asked to be cancelled, in which case it must halt. Your task can also never call this method, and ignore the cancellation request. You can also use `CancelListener.isCancelled(psacl)` to avoid NullPointerException on `psacl`.

## How to send a value back to the UI thread.

The previous example did a task by itself and the UI never get any news from it. This may be okay if you want to clean the database, not if you have downloaded an image to show the user.

You can do
```java
class TaskToDoInBackground extends TaskDelegate<Void, Image> {
  new Image task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> psacl) {
    stuffToDo;
    return image;
  }
}
```
to download and send back the image.
```java
class ImageListener extends TaskListener<Void, Image> {
  @Override
  public void onPreExecute() {
    let the user know downloading start;
  }

  @Override
  public void onPostExecute(Image image) {
    showTheImage(image);
  }
}
```
then in a method, use 
```java
CollectionTask ct = TaskManager.launchCollectionTask(new TaskToDoInBackground(), new ImageListener());
``` 
When the task is ready to start, usually immediately, it will execute the content of `onPreExecute` in the UI thread, then the content of `task` in the background thread, send its result to the UI thread in `onPostExecute`.

It is important in this example that the image can be shown as is. It should not require further processing, as it may be slow and should have been done in background.

## UI cancellation feedback

The TaskListener can also have a `onCancelled` method that will be run if the task is asked to be cancelled. This is not mandatory, but can allow to confirm the user that the cancellation request has been received (even if it does not confirm that the background task actually took it into account) 

## Sending progress

Let's say your image is big and your connection is slow.  

In this case, you should do
```java
class TaskToDoInBackground extends TaskDelegate<Long, Image> {
  new Image task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> psacl) {
    stuffToDo;
    for (download) {
      ...
      psacl.progress(numberOfDownloadedByte);
      ...
    }
    return image;
  }
}
```
to download and send back the image.
```java
class ImageListener extends TaskListener<Long, Image> {
  @Override
  public void onPreExecute() {
    let the user know downloading start;
  }

  @Override
  public void onPostExecute(Image image) {
    showTheImage(image);
  }

  @Override
  public void onProgressUpdate(Long bytesDownloaded) {
    show the number of downloaded bytes;
  }
}
```

Note that all of the Void from the primary example have been replaced by more useful type, indicating the type of the updates and the type of the data sent at the end of the computation

## Listener only care about current activity

Let's say you are loading an image to show it. If the user click back, the current activity disappear and it's useless to show the image in it. It's theoretically possible to show the image in it, since the object still exists, but it's pointless. In order to avoid this, you can use `TaskListenerWithContext<MyActivity, Long, Image>`, which ensure that the methods `onPreExecute`, `onProgressUpdate`, `onCancelled` and `onPostExecute` are called only if the activity still exists. If the activity ended, it does not stop the activity to be destructed, and in this case, the method returns immediately.

In this case, you'll need to implement the methods `actualOnPreExecute(MyActivity activity)` instead of `onPreExecute()` and so on with the three other methods.


## Allow for closed collection
In some rare case, you might want to allow for the task to be executed with a closed collection. It might be useful for backup, for cleaning the database, etc...

In this case, you must add to the `TaskToDoInBackground`  `@Override protected boolean requiresOpenCollection() {return true;}`.


# Architecture

Our asynchronous architecture use many classes.

* TaskDelegate, that you have already seen a lot, is in charge of executing the task in backround, sending update and final value to listener, and listening to the CancelListener to check if it must halts.
* TaskListener is in charge of UI action when the task start, when it receives update, cancellation, and at the end of the task.
* TaskManager is in charge of excuting the task and ensuring the communication between the Delegate and the Listener. There are currently two managers, SingelTaskManager, the default one, and ForegroundTaskManager, used for test, that do not actually execute anything in background.
* CollectionTask this class represents a computation. It can be used to cancel a task. I don't think there is any other use of it outside of the task manager. (TODO: check. either update the documentation, or `launchCollectionTask` should returns a `cancelSender` instead)

## Why we use this architecture

Essentially, we are a 10 years old app. If someone want to move to our history, it might be interesting to know how we made the decision we did. I feel safe to state that we did not plan to become so big, with so many feature, and we coupled too many things together. It led to a program that works but was maybe not ideal to survive a library deprecation. Until 2020, CollectionTask was in charge of what is Delegate, Manager and CollectionTask today. Instead of having proper typing between the Delegate and the Listener, they communicated using `TaskData`, a class that essentially represents an Object or an array of Object. We have made huge progress, but it's not done yet.

Currently, the app execute in background using AsyncTask. This is transparent to most developer unless you look at CollectionTask implementation.

This has been deprecated for quite some time and should be removed eventually. One of the problem with async task being that it keeps the current activity in memory and do not allow to destroy it. We tried to mitigate this problem with `TaskListenerWithContext` but it's only a band aid on a more fundamental problem. 

Also, AsyncTask can execute a single task at a time. If we were really downloading images, it would be unacceptable, as it would forbid us to download multiple images in parallel. For a developer perspective it's great as it ensure there is no interaction between the activities of multiple background thread, but from the user perspective it's uselessly slow.

The trouble being that our entire codebase was written with the idea that you can ignore interaction. So you can not just move from AsyncTask to a parallel executor without checking what problem could occur. You can probably be safe if you allow many reader or a single writer, but it's not entirely trivial to do.

The good news is that, now that TaskDelegate and Manager are separated in their own classes, it will theoretically be easier to concentrate on the manager and implement it in a more efficient way