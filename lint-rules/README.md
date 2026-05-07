# Lint Rules Module

This module contains custom Android lint checks for the AnkiDroid project.

## Module Structure

```
lint-rules/
├── src/main/java/com/ichi2/anki/lint/
│   ├── IssueRegistry.kt      # Register all lint issues here
│   ├── *.kt                   # Individual Detector classes
└── build.gradle.kts           # Build configuration
```

## How to Add a New Lint Check

### Step 1: Create a Detector Class

Create a new `.kt` file in `com.ichi2/anki/lint/` (e.g., `MyCustomDetector.kt`).

**Example:** Look at `DirectDateFormatDetector.kt` - it's a small, simple example to copy.

### Step 2: Define the Issue

Inside your Detector, define an `Issue` object with:
- `id` - Unique identifier
- `briefDescription` - Short summary
- `explanation` - Detailed description
- `category` - Issue category
- `priority` - Priority level
- `severity` - Severity level

### Step 3: Register the Issue

Open `IssueRegistry.kt` and add your new `Issue` to the `ISSUES` list:

```kotlin
val ISSUES = listOf(
    // ... existing issues ...
    MyCustomDetector.ISSUE
)
```

## How to Run a Check Locally in Android Studio

### Option 1: Terminal (Fastest)

```bash
# Build the lint-rules module
./gradlew :lint-rules:assemble

# Run all lint checks
./gradlew lint
```

### Option 2: Android Studio Workflow

1. Make your changes in `lint-rules/`
2. Rebuild: **Build** → **Rebuild Project**
3. **Restart Android Studio** (important!)
4. Run: **Analyze** → **Run Inspection by Name** → Type "Lint"

## Related Resources

- [Android Lint Documentation](https://developer.android.com/studio/write/lint)
- [Writing Custom Lint Rules](https://googlesamples.github.io/android-custom-lint-rules/)
