# Build the existing Tactical Urbanist game as an Android APK

This packaging-only setup leaves the game's rules, UI, assets, and gameplay source unchanged.


## Build via GitHub Actions
1. Upload/push this project folder to a GitHub repository.
2. Open **Actions** in the repository.
3. Select **Build Android APK** workflow.
4. Click **Run workflow** (or push a commit to trigger it automatically).
5. Wait until the run is green.
6. In the successful run, open **Artifacts** and download `tactical-urbanist-debug-apk`.
7. Extract the artifact and install `app-debug.apk` on Android. If Android prompts, allow installing from that source.


## What the workflow does
- Sets up JDK 17, Android SDK Platform 34 and Build Tools 34.0.0.
- Runs the Java core self-test, map static checks, and embedded JavaScript syntax check.
- Builds the debug APK with Gradle 8.7.
- Uploads the APK as an Actions artifact for 30 days.


## Build limitation
This environment does not have Android SDK installed, so the APK cannot be built or device-tested here. GitHub Actions is configured to perform the actual Android build remotely; a successful green workflow is required before claiming the APK is ready.

## v13 visual refresh
The v13 package is based on v12 and updates only presentation assets and CSS: card artwork (Spy, General, Disaster, Economic Crisis, Sabotage), resource icons (Energy, Industry, Army, Defense), the deck back, and interface styling. Java game rules and inline JS behavior are preserved. See `CHANGELOG_v13.md`.
