# Lint Rules Module

This module contains custom Android lint checks for AnkiDroid.

## How to add a new lint check

1. Create a new .kt file in com.ichi2.anki.lint/
2. Create a class that extends Detector
3. Define an Issue object with:
   - id - unique identifier
   - briefDescription - short summary
   - explanation - detailed description
   - category - issue category
   - priority - priority level
   - severity - severity level
4. Add the issue to IssueRegistry.kt

See DirectDateFormatDetector.kt for a small example.

## How to run checks locally

### Terminal

./gradlew :lint-rules:assemble
./gradlew lint

### Android Studio

1. Make changes in lint-rules/
2. Build -> Rebuild Project
3. Restart Android Studio
4. Analyze -> Run Inspection by Name -> Type "Lint"
