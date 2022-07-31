This document describes how code gets added to AnkiDroid. It does not define any rules, there is no obligation to follow what is written below. It's only a draft by to help new contributors and new reviewers to understand how we have been working until now.

# Requesting change

Once AnkiDroid receives a pull request (PR), anybody can comment on it and request change. In order to remain a welcoming community, it is expected to greet first-time contributors (a bot detects them and greets them, however, it’s always nicer to get greeted by people). It is also nice to thank them for their contribution when they submit them and when we merge it.

## How to request a change

If you request some change, you should aim to explain why you made the request. You are free to judge how much explanation is necessary. 

If you find a typo, no explanation is necessary. If you review code written by someone you know is an experienced developer (e.g. a maintainer of AnkiDroid) and that uses only standard libraries, you can usually assume they have an idea how Kotlin and Android works and just note that their code introduces a bug or does not follow best practice. 

However, if you review code from a new contributor and the bug is non-obvious (e.g. inefficient code, race condition, potential issue with localization, database access in foreground, …) it is nice to either give a short explanation, a link to a description of the issue, or ask them whether they need clarification.

## How detailed should your review be

You must judge how critical the code changed is and adapt your style of review accordingly.

For example, if the code touched can delete or corrupt data from the user, it is justified to require unit test before accepting to merge it. It is usually justified to spend time on each line and ensure that you understand absolutely everything, that you request clarification, either from the PR author or from a reviewer knowing this part of the codebase. In particular, you must ensure that you cover all corner cases, even those that you expect to be rare. Indeed, some people regularly update AnkiDroid to alphas version, or even run directly from main, and they may also have the most unusual collection.

On the other hand, if you are reviewing a change that only affect the UI, it may be perfectly okay to download the code, compile it, ensure that the UI is as expected on smartphone and tablet, in light and dark mode, and accept it, even if you don’t know the meaning of each line of the XML of the layout or of the view’s widget. In the worst case if we discover an issue on some specific device, it can always be corrected later and at worst the user on alpha will have to wait some time.

Obviously, this does not mean that those bugs are welcome, we should still try to avoid it. But slowing down the process of improving AnkiDroid and making a bad experience for our contributors is also a cost that we must keep low.


# Replying to a change request

When someone requests you to change your PR, you can agree, disagree, or require clarification.

If you agree with a request, you can resolve it once the change requested is on github’s PR. 

If you require clarification, you can ask your question on github. It’s also okay to go on discord and ask the request author directly, chatting often allows to understand faster than an asynchronous discussion.

It is always acceptable to disagree with a change request; in which case the disagreement must be stated explicitly and justified in reply to the request. Since other people may make similar requests, it is often a good idea to add a comment in the code base or the commit message to explain why you made your choice. In particular, you should not “resolve” the request without addressing it.

# Approving change

By default, each pull request (PR) must be approved by two people. One of those people at least must be a member of the reviewer team, since this is a requirement to be able to merge the PR in our codebase. 

If a reviewer believes that their judgement is enough, it is perfectly fine to merge the code without a second review. For example, if the PR corrects a simple typo, does a trivial code clean-up. If for some reason the code at the head of the main branch does not build or fails its test (e.g. because two incompatible PRs were merged), it is perfectly fine to merge code quickly in order to get build working again, and to ask for one or two extra reviews later.

The author of the PR and the reviewer who merged it into the codebase are both responsible for any bug that the PR introduced. While nobody is ever forced to do anything for AnkiDroid, we usually expect either the code author or the reviewer who merged the code to take the time to correct any bug or build failure their code introduced.

## How to become a reviewer

If you make good contributions to AnkiDroid codebase and review PR written by other contributors in a polite and constructive way, current reviewers will probably notice you and offer to join. It is perfectly acceptable to contact current reviewers yourself to offer to join their team, however this did not have to occur since early 2020. 

Becoming a reviewer comes with no obligation, you can stop reviewing anytime you want.

When you become a reviewer, you may get access to some private discussions, such as the administration of Google Summer of Code and change to our Open Collective policy. You do not have to participate, but you are welcome to become a GSoC mentor for the next summer.
