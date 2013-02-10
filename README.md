# Anki on Android

This is the repository for Anki on Android.
For more details see:
* https://code.google.com/p/ankidroid/

## Future releases

The branches for future releases are:  
* Maintenance release: [v2.0.1-dev](https://github.com/ankidroid/Anki-Android/tree/v2.0.1-dev)  
* Feature release: [v2.1-dev](https://github.com/ankidroid/Anki-Android/tree/v2.1-dev)

To contribute, please base your changes on one of the two branches above and
send a pull request.  
Changes for a maintenance release are usually limited to bug fixes.  
Close to a release, you might be asked to contribute to new features a next release.

## Past releases

To browse the code of previous releases, use one of the tags:  
* 2.0 Release: [v2.0](https://github.com/ankidroid/Anki-Android/tree/v2.0)

## Contributing

AnkiDroid is developed using release branches.  
Each upcoming release has a named branch with the "-dev" suffix.  
To contribute, you should clone this repository and build on top of one of the release branches.

### Step 1: Fork the repository

Go to the [ankidroid organization repository](https://github.com/ankidroid/Anki-Android) and click the `Fork` button at the top of the main repository page to create your own fork.

### Step 2: Check out the repository

Follow the Github instructions to check out a copy of your repository.

Add the AnkiDroid repository as a remote:  
`git remote add ankidroid git://github.com/ankidroid/Anki-Android.git`  

### Step 3: Start a feature branch

Fetch the latest version of the AnkiDroid repository:  
`git fetch ankidroid`

Create a new feature branch for what you want to contribute:  
`git checkout -b feature-name ankidroid/v2.1-dev`

Note that if the feature is meant for the maintenance branch, the command should use `v2.0.1-dev` instead of `v2.1-dev`.

### Step 4: Make your changes

At this point, you should edit the code and commit your changes.  
It is best to create a single commit with all the changes you want to contribute.  
If you created multiple commits, you can use `git rebase` to merge them into a single one.

### Step 5: Send a pull request

You can now make a `Pull request` using the button at the top of your repository page.  
Make sure the request is made against the correct release branch, e.g., `v2.1-dev` in the example above.
