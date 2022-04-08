In this document, written in March 2021, we expects to explains the rules regarding User Interface changes in AnkiDroid in the near future.

# We avoid changes
As a general rule, we are conservatives and avoid changes. Almost every UI change should have an associated accepted issue in our issue tracker.

It’s easy for a lot of people to have an opinion on UI changes as they are very visible, and this leads to long discussions. We want to avoid this as we are currently time-constrained, since March 2021 we have a lot of active contributors, we must select a Google Summer of Code student, and we have little time left.

Similarly nearly every change results in complaints from existing users that are used to the existing style, and even PRs from the community to turn the change into an option. Meaning we would have two styles to maintain forever if we accept the PR. So ours have demonstrated in their reviews and their actions (via PRs) they feel a real cost and many prefer there are now UI/UX changes.

Here is a list of things that matter to us and may justify a rejection of a change

# Criterias we take into consideration
## Anki Ecosystem Compatibility

Our primary goal is to stay functionally compatible with Anki Desktop, and many UI changes also contain functional changes. If our functionality differs from Anki Desktop, we consider it to be a bug unless a strong justification is given.

## Localisation/Internationalisation
AnkiDroid is available in 90+ languages. If any image/media contains hard-coded text, then our translators will be unable to translate it.

## App size
We have users all around the world. In some places, internet access is expensive and devices do not have a lot of storage. We try to keep the app as small as possible. Any changes that increase the app size considerably, in particular adding images, sound and/or videos will probably be rejected unless there is an excellent reason for it. For example, we would prefer to link to a YouTube video rather than embed it in the app.

## Day and night
AnkiDroid has multiple color themes. We require screenshots in both modes as soon as we suspect one mode may break.

## Screen size
Some devices have very small screens. A lot of windows list elements one above the other, e.g. card in browser, deck in deck picker, fields in note editor. It means that if you increase the size of an element, less elements can be displayed at once. It can be an acceptable tradeoff but must be clearly explained.
Similarly, each button, menu, entry added to a screen mean that some users will not see everything. This trade off should be taken into consideration.

Some users use tablets and have a very big screen. It sometimes is the case that we use different windows depending on the size. We try to keep the app similar for all devices, but if required, we already consider having different UI depending on the size.

## Animations/Screen speed
Some users install AnkiDroid on e-ink devices. Those devices take time to display new content, and they should always have an option to have instantaneous activities. Animations which cannot be disabled are not acceptable for them. 

## Device speed
Some of our users have phones with low memory, disk space and processor speed which would be considered low compared to latest models. We still want AnkiDroid to run smoothly to them. Any UX change that requires significant computing resources would be unacceptable.

## Accessibility
AnkiDroid aims to be accessible and any change that is made should improve accessibility. We will ask you to check with accessibility tools (like the [Accessibility Scanner](https://play.google.com/store/apps/details?id=com.google.android.apps.accessibility.auditor) app) to ensure your change does not create any easy to detect accessibility issues. Ensure that no new issues are introduced.

## Design
We do our best to follow [Material Design](https://material.io) guidelines. Ideas that do not adhere to those standards will likely not be considered as they will not have the correct UI/UX experience on the platform.

# Changes we may accept

Please discuss with us any change you expect to make if it’s complex. If it’s really simple and you are okay with having your work rejected, you are free to make the change you want and send a screenshot with the project. Here is a list of cases where we may accept changes.

## Accepted Issues
If a UI-based issue has been created by a maintainer, or has been labeled as ‘Accepted’ then it is open for PRs.

## Onboarding
A change that’s related to the first use of AnkiDroid and helps users understand how to use the app may be accepted. This is the first thing the user will see so expect it to be subjected to a lot of discussion and change requests.

## Localization
If for some reason our current choices do not work well for non-English users, we may accept change. For example, at the time I write this, people are working to improve the right-to-left experience for people using their app mainly in a RTL language. 

If any other change is required for ease of use in a non-english speaking language, please discuss it with us.

## Accessibility
AnkiDroid does not yet satisfy accessibility criteria. If you make a change for accessibility reasons, we may consider it. We will still ask you to do the minimal change that allows the app to satisfy accessibility criteria. We also require you to explain which criteria is not satisfied so that we can check ourselves whether your solution is the best one. 

This explanation can be either an automated accessibility tool. It can also be a free document about best practices, that our UX clearly violates.

## Design
These are accepted more rarely, but if there is an easily demonstrated difference between our design guidelines and the way the app is behaving, and we judge in our *opinion* that correcting the difference will benefit most users with as few irritated users as possible, we may accept a design change.

## Typo
Feel free to correct any typos and other obvious mistakes. As an example, a user noted that a window had an empty title instead of having no title. Correcting it decreased the space used by the window, which is a clear win for users with small phones.

## Guidelines and consistency with Android
AnkiDroid should not be surprising the user. If we do not follow an Android guideline, if we behave differently from other standard Android application, it's okay to correct AnkiDroid behavior. Please either provide the guideline, or an example of Android application that shows the change you intend to make.

## Small improvements
It is hard to describe precisely what a small improvement is. In general, it is change that improves a feature and presents no trade off, that won’t make the experience worse for any criteria mentioned above. Examples are simpler to give:

* Allowing to search for deck instead of forcing the user to use a scrolling menu
* Highlighting the deck currently selected in the deck list.
* Adding a small orange circle to indicate that the collection is not currently synced with ankiweb.

# What about big changes ?
There are a few changes that we work on, such as having a better interface to change note content. That’s a big non-trivial work that we accept to tackle because it is a neat great improvement for the users. 
As explained above, time is a big limiting factor, plus expertise. So, until things get calmer, we don’t expect to accept any big change to the UI. We may be convinced to make other important changes, but we don’t expect this to occur often. 