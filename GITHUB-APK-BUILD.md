# SA-TV GitHub Actions APK Build

## Build the APK on GitHub

1. Create or open your GitHub repository.
2. Upload and commit all files from this ZIP to the repository root (keep the `app/`, `build.gradle.kts`, and `settings.gradle.kts` paths).
3. Open the repository's **Actions** tab.
4. Select **SA-TV Android APK Build**.
5. Press **Run workflow** (or push a commit to `main` / `master`).
6. Wait for the job to finish with a green check.
7. Open the completed workflow run, scroll to **Artifacts**, and download **SA-TV-debug-apk**.
8. Extract the downloaded artifact ZIP. It contains `app-debug.apk`, which you can install on your Android phone.

The workflow uses JDK 17, Gradle 8.9, and Android SDK 35. It creates a debug APK; it is not a Play Store release-signed APK.

## Important
- A GitHub Actions workflow has been added at `.github/workflows/build.yml`.
- The build has not yet been run on GitHub. A successful APK is only confirmed after the workflow run completes.
- Live streams require valid, reachable stream URLs and may be restricted by their providers.
