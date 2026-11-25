# FORGE Android APK build

This is the FORGE native Android Java/OpenGL ES 2.0 project.

## Build an APK from an Android phone using GitHub Actions

1. Create a new empty GitHub repository named `forge-engine`.
2. Extract this ZIP on your phone.
3. Upload the **contents** of the extracted folder to the repository root (not the ZIP itself and not an extra enclosing folder).
4. Open the repository's **Actions** tab.
5. Select the `build-apk` workflow. If it has not run yet, push/commit the uploaded files first.
6. Open the completed workflow run.
7. Under **Artifacts**, download `forge-swarm-apk`.
8. Extract the downloaded artifact and install `app-debug.apk` on your Android phone. If Android asks, allow installation from the app you used to open the APK.

## Build requirements

The workflow uses:
- JDK 17
- Gradle 8.7
- Android Gradle Plugin 8.5.2
- Android compile SDK 34

The APK is a debug-signed installable build. The app targets Android 14 (API 34), supports Android 8.0+ (API 26+), and runs in landscape orientation.

## Important

The source archive does not contain a precompiled APK. GitHub Actions compiles the APK from source and publishes it as a downloadable workflow artifact. No paid API or cloud service is required by the game engine itself; GitHub Actions is only used as the build machine.
