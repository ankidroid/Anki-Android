This explains AnkiDroid opinion and desires regarding atomic commits and try to solve issues that our new contributors generally have. It is not a general essay about atomic commits and probably can't solve all issues. However, if you have an issue while contributing to our software, please let us know and we'll try to improve it.

# What means being atomic

## Atomic commit

An atomic commit is a commit that can not be cut in multiple parts. At least, that is the intuition behind atomic commits, this is the goal we try to approach. For example adding one function can be an atomic commit. If the function is extremely simple, and make the commit almost pointless, we sometime accept to add multiple related small functions. However, if a function is already hundreds of lines long, and for some reason is too hard to split in multiple function (we should really try to avoid huge functions), then it definitely must be in its own atomic commit. 

The optimal size of atomic commits varies from community to community, and it takes some time for new contributors to know what is expected in which codebase. Expect to have a few PR discussed with reviewers before you figure out our intuitions. Same way than reviewers of AnkiDroid have to learn the size of ideal commits each time they contribute to a new project.

## Atomic PR

An atomic PR is a PR that could hardly but cut more meaningfully. It may contain a single or many atomic commits. A PR may add a new feature or fix a bug, and each of its commit add a function and its test. The big idea is that we expect all contributors and the various versions, alpha, beta, stable, to contains entire PR. While some atomic commits are nice by themselves, we don't want contributors working on unrelated feature to see partial work, it may confuse them if they don't know why some unused function is there. So we wait until the feature is entirely done to merge it into our codebase.

Once again this is not an absolute rules. Some features requires literally years of work. In this case, we may want to have partial work merged in the codebase even if it's not used. However, those are the exceptions.

# Why do we want to be atomic

It often occurs that new contributors makes a lot of great changes. However, the reviewer read them, and have a hard time understanding them. One of the main issue is that a lot of changes are done at once, and it requires a lot of thinking to consider hundreds of lines simultaneously. Were the reviewer behind the author shoulder, it may have made total sense. But, alone, the reviewer can't know which modification caused which other change, and which changes are entirely unrelated. This get worst when a property of an object is modified, when a function takes a different set of arguments, because the change may be reflected in a lot of files. This is why, reviewers generally ask for atomic commits.

On top of that, the more code you add to a PR, the more change reviewers may require. When a reviewer require a change, it blocks not only the part of the code the reviewer disagree with, but everything the reviewer like. With atomic PR, you can get some PRs merged quickly. This means that all other contributors can use your change immediately. If it counts for you, it also means more green on your github page. From the reviewer perspective, it also means that on second or third review, the reviewer only has to re-read a small part of the code instead of having to read everything they agreed to previously. 

# How to create atomic commits

I repeat, this is not a general introduction to git or to atomic commits in general. It is a box of recipies to solve issues found by our communities.

## Create new PRs.

In my opinion, this is the simplest solution. This is one often used by the original author of this wiki page. If you want to split a PR into atomic PRs, you can create a new local branch on your computer, starting on main, and copy paste in it your atomic change. Push it, create a pull request on github. Then create another local branch from main, make another atomic change, make a pull request from it and so on.

Your original PR will become useless in the end and can be closed, as all of its content are in other PRs.

The main issue are:
* if you can expect those changes to conflict. If you are fine with solving merge conflict manually, no issue, if you are efficient it can take a minute or two.
* if one change requires another change to be done first. This one requires actual atomic commits in a single PR.

## Create one new branch, then rename it.

As in the previous case, create a new branch from main. Create the first atomic commit by copy pasting from your original PR to this branch.  Run the test to ensure everything compiles and work as expected. Commit it. In the author experience, copy-paste is never enough, because there are lines that should be touched by multiple commits, and if you copy the final result in the first commit, the code won't compile as this version of the line will require the whole PR to work. So you'll need to rewrite this line or those few lines manually for the commit to compile and test to pass. 

Once it is done, copy paste the change of the second atomic commits, commit, test it, commit it. Repeat until you have all your changes in the series of commit.

Once all changes are done, force push the result on top of your original pull request. `git push --force username/originalBranchName`.  You may want to destruct your original branch and rename your current working branch to the name of your original branch, as it ensures that the local branch behind "username/originalBranchName" is called "originalBranchName".

## If the changes are on different lines

If you want to make a series of atomic commits, and each commit touch different lines, you can also cancel your current existing commit(s). On android studio, that would be with "git > Reset Head...", reset type "mixed" and "to commit: HEAD~n" with n the number of commits you want to cancel. You'll have the same content in your source code directory, but the commit won't be in the commit history. Then you can the "git commit" (ctrl-k) tool to select line by line what you put in the first commit, create the commit, then what you put in the second one, and so on.

In the author experience, this method is rarely simpler than the previous one, because it's pretty rare that you have multiple commits that depends on one another, and that don't touch a single line in common. And as soon as one line in common is touched, creating those atomic commits requires some extra work.