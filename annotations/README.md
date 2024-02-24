# External Annotations for Android Standard Library

## Rationale

`@Contract` annotations for Android methods allow the removal of tautological lint checks.

For example: if `TextUtils.isEmpty(str)` returns `false`, then `str` is non-null. `@Contract` allows us to specify this invariant to the linter, which reduces lint warnings and/or unnecessary code.

**@Contract annotations should be inline whenever possible**. This folder exists so we can define our own contract annotations on our dependencies.

## Syntax

See: https://www.jetbrains.com/help/idea/contract-annotations.html

## Installation Instructions

* Open an Android Source file in Android Studio
* Move the Cursor over a method and bring up the Quick Fixes Menu (Alt + Enter)
* Select "Add Method Contract to `[method]`"
* Enter a dummy annotation: `null -> null`
* Select the folder containing this file as the annotations root

Annotations should now appear

## Modificiation Instructions

* In Android Studio Settings: `jdk.table.xml`
* You can find the element under one of the `<jdk>` elements as a `file://` URL:
* Removing this line will allow the selection of another annotations root.

Sample:
```xml
<jdk version="2">
      <name value="Android API 28 Platform" />
      <type value="Android SDK" />
      <version value="java version &quot;1.8.0_112-release&quot;" />
      <homePath value="C:\Users\David\AppData\Local\Android\Sdk" />
      <roots>
        <annotationsPath>
          <root type="composite">
            <root url="jar://$USER_HOME$/AppData/Local/Android/Sdk/platforms/android-28/data/annotations.zip!/" type="simple" />
            <root url="file://C:/GitHub/Anki-Android-David/annotations" type="simple" />
```

## Future Goals

It would be ideal if these could be set on a per-project basis. I haven't had the time to determine whether this is possible.

These annotations are not yet supported by our automated tooling.
