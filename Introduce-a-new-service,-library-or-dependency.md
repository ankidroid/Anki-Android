In this page, we'll consider how we decide if we allow or not a new library or dependency in AnkiDroid repository and application. We'll start with constraints that applies to the entire development process, and then consider rules that applies only to AnkiDroid application itself.

# Rules for the entire AnkiDroid repository

## Currently used tools.

First, let me list the services that we use today. That may illustrate the choices we make. I probably forget some tools here.

* Android studio, java and kotlin compiler, for compilation
* We use [gradle](https://github.com/ankidroid/Anki-Android/blob/main/gradlew) as a build system.
* We use [https://opencollective.com/ankidroid] for donations I suspect that I may even forgot some tools here and there.
* We use [crowdin](https://crowdin.com/project/ankidroid) for translation
* We use github to host code, host our wiki, and a lot of automated [actions](https://github.com/ankidroid/Anki-Android/tree/main/.github/workflows) such as automated testing, linting, adding milestone to PR, publishing an update, updating translation from, hosting various alpha binaries.
* We use [codecove](https://codecov.io/gh/ankidroid/Anki-Android/) for code coverage by test.
* We use Play Store, F-store and Amazon store to distribute our app to users and update the apps.
* A host for [our website](https://docs.ankidroid.org/)
* Sellers to provide testing device
* Acralyzer to host crash report

## How to decide whether we use a tool.

Here is how what we take into consideration before deciding whether we add an extra tool/service.

### The cost

As far as possible, we use services that have a free tier for free software. Hosting a website cost a little bit of money. OpenCollective takes a fee on the donations we receive. We had to bought a MIUI device for debugging purpose.  I believe those are the only services that cost us currently. Since we receive donations, we may be able to spend a little bit if a service really adds values and either there is no other choice or if the other choices have too many downsides. However, this extra cost should be reasonable. In particular, it can not scale with our numbers of users. After all, even a cent by user would cost us tens of thousands of dollars.

### What would be the impact if the service disappears

Ideally, if a service comes to disappear, the project should survive. That would be the cade with codecov for example, which is a great tool but that is not mandatory in our production system. If crowdin were to disable free account, that would hurt us, but we already have all translations in our repo and we can still use them and send them to another service provider if necessary.

Sometime, we would be really hurt if a service were to disappear or drastically limit its free tier. The current process would immediately stop without github or the Play Store. Even with local copy of the git repo, not having the wiki, the issues, the automation would considerably slow down the development. And while it is not certain that those services will still be here in 10 or 20 years, historical experience shows that Google and Microsoft usually warn in advance before changing their term of service, which may allow some time to prepare ourselves. On the other hand, we probably would not want to have our entire process depending on a new-ish service that risk to get bankrupts without warning.

As a working example of service we had to stop using. We used to use Travis, until they limited their free tier, then we switched to github actions. While it took some work, the same set of unit test and on-device tests can run on most platforms, so Travis was acceptable.

### What permission require the service

If a service requires any permission, that would be extremely suspicious. For example, codecov can pull our code to analyse it and don't require any special permission. Crowd-in receives and sends translations through their API without requiring any access to github. It is hard to imagine a reason why we would agree to give permissions, in particular related to github or play store, to any service.

### How much much maintainance does it require

Ideally, most service don't require maintainance at all. Crowd-In require manual work to add a new language when a translator request it, it's rare enough to be okay. A service which changes its API every months and require us to rewrite part of our code would not be acceptable.

### Do the service access people data

It sometime is the case. Obviously, play store knows who the user are. Open-Collective, which deals with payment, must know who they send money too, and who is in charge of managing the account they host for us. While crash report do not contains any PII, there is always a risk that a report would leak some data, and we take all precautions we can to limit access to crash report to few people, even if we never found any user data in a report up to today. Those are all justified uses of people data.  Which is related to the next question:

### Can/should we host the service ourselves

Generally, it's a bad idea to self-host. It requires more maintainance, hosting cost, and we are not expert in most services, so we would probably not manage it as well as a team whose full time job is to deal with the service. For example, distributing AnkiDroid updates to millions of users would be a full-time job for example.

However, some smaller services, such as hosting crash report, can be self-hosted without requiring too much work to keep working and it reduces the number of people who can access crash report. In this case, self-hosting is an advantage.

# Rules for AnkiDroid code

This section concentrates specifically on the case of adding a new library/dependency in AnkiDroid.

## Licence

AnkiDroid is under GPL. This licence can incorporate codes using a lot of other licences, however, we still must be sure that we only incorporate code we are legally allowed to incorporate.

## Fixed version

There as been a few examples of maintainers volunterally breaking their code with an updaet. It is safer if we can access the code from a source that allows us to select the version number we require. While it's not perfect safety, at least, we get an idea of which version of the code we run - as long as the package manager is honest.

## Easy to add/update with our tooling systems

If a dependency can directly be incorporated with gradle, that's perfect. If it requires new tooling (e.g. a svelte compiler), we will not want it into ankidroid repository. However, it can be added to another repository that we then incorporate as a dependency. If the library is simple but not available (e.g. we had the case with a CSV parser), it is also an option to port it manually in Kotlin.

## Application size

A lot of our userbase has small smartphone. We will almost certainly reject a library that considerably increase the application size. Note however that the actual size is hard to know until it is actually measured, because AnkiDroid has tooling that scraps unused part of the codebase when packaging it. In particular, any big file that can not be compressed are not acceptable. This may include bitmap pictures, videos, audio files, or trained ML models.

## How buggy is it

Sometime we must accept buggy code when there is no alternative. As an example, MathJax 3 seems to render improperly some mathematics equation that were rendered properly with MathJax2. However, Anki uses MJ3 and MJ 2 is not developed anymore. While rendering bugs are frustrating for users using it, there is still no alternative in this space, especially since many anki cards are written for mathjax and some uses MJ 3 features. Furthermore, rendering bugs don't leak or destry user data

However, in general, we prefer dependencies that are not buggy. That means in particular, dependencies that are used by a lot of product, which would have detected and reported those bugs. That also means that there is a ticket/issue system so that we can check how the dependency deals with bugs.

## Is it maintained

Some code may never change. E.g. it's probable that a CSV parser do not have to be updated regularly. However, a library that adds a user interface elements (onboarding slides, new button) may need to be updated when the API it uses gets deprecated. A library may have a bug that needs to be corrected. So we strongly prefer a library that has shown to be maintained by its creator or community.