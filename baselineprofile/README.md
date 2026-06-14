# `:baselineprofile`

This module generates AnkiDroid's **baseline profile** - a list of classes
and methods ART pre-compiles at install time so cold start is faster.

It has two classes:

- **`BaselineProfileGenerator`** captures a cold-start trace and writes
  `baseline-prof.txt` into `:AnkiDroid`.
- **`StartupBenchmark`** measures cold-start time with vs without the
  profile so you can see the impact.

Neither runs in CI. Both are manual tools for maintainers.

## Requirements

- Physical Android device, API 28+ (emulators aren't trustworthy)
- USB debugging on, device plugged in
- `adb uninstall com.ichi2.anki` first if you hit a signing-key error

## Regenerate the profile

```sh
./gradlew :AnkiDroid:generateBaselineProfile
```

Takes a few minutes. The plugin installs a non-minified build, runs the
cold-start journey (launch app -> dismiss intro screen if present -> wait
for DeckPicker), and writes the result to `baseline-prof.txt`.

## When to regenerate

When the startup path changes meaningfully — new init work in
`AnkiDroidApp.onCreate`, a refactor to `IntentHandler` or `DeckPicker`,
a new dependency wired into `Application`. Otherwise, leave it alone;
the profile is device- and OS-agnostic, one commit covers everyone.

## Config notes

See comments in [`build.gradle.kts`](build.gradle.kts) for the rationale
behind `minSdk`, `missingDimensionStrategy`, the `benchmark` buildType,
and `self-instrumenting`. The KDoc on each class explains the journey
and the measurement modes.
