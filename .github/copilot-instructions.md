# Copilot instructions for InfiniteUniverseDrawing

## Project overview
- This repository is a single-module Android app written in Kotlin.
- The main drawing behavior lives in `app/src/main/java/com/example/infiniteuniversedrawing/DrawingView.kt`.
- `app/src/main/java/com/example/infiniteuniversedrawing/MainActivity.kt` wires the toolbar, tool controls, and actions around `DrawingView`.
- UI layouts use XML and ViewBinding, not Jetpack Compose.

## Working conventions
- Prefer small, surgical changes that stay within the existing architecture.
- Keep drawing interaction logic in `DrawingView.kt` and activity-level UI wiring in `app/src/main/java/com/example/infiniteuniversedrawing/MainActivity.kt`.
- Follow the existing Kotlin style in the touched file instead of introducing new patterns.
- Reuse existing AndroidX and Material components before adding dependencies.

## Validation
- Run unit tests with `./gradlew testDebugUnitTest`.
- Build the release APK with `./gradlew assembleRelease`.
- If you change drawing or gesture behavior, also review the instrumentation coverage in `app/src/androidTest/java/com/example/infiniteuniversedrawing/`.

## Testing guidance
- Add the smallest focused test that covers the change when practical.
- Prefer extending the existing drawing and viewport tests for regressions instead of creating duplicate coverage.
