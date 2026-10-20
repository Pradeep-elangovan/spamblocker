# Spam Blocker (India, Android 16+)

Personal call blocker built on Android's `CallScreeningService`.

## Rules (first match wins)
1. Saved contact → allow
2. Allowlist → allow
3. Blocklist → block
4. Network-flagged spam (toggle) → block
5. Pattern rules (starts with / ends with / contains) → block
6. Region rules (state / circle, landline and mobile separately) → block
7. Block all unknown numbers (toggle) → block
8. Otherwise allow

Blocked calls are **rejected** by default (configurable to *silence*, also per number).
Unknown callers that are let through get a heads-up notification with **Block** / **Spam** buttons;
the Calls tab lets you flag numbers afterwards, and can import your phone's call log.

## Install on your phone
1. Open the repo's **Releases** page on the phone → **Latest build** → tap `SpamBlocker.apk`.
2. Allow "Install unknown apps" for the browser when asked; confirm Play Protect's prompt if shown.
3. Open the app → **Set as call screening app** → grant permissions.

Every push to `main` builds a new APK (GitHub Actions) and replaces the *Latest build* release.
The debug key is committed on purpose so updates install over the previous build.
For a Play Store release, switch to a private upload key and add a `release` signing config.

## Develop
Android Studio (JDK 17), open the project root. `./gradlew testDebugUnitTest assembleDebug`.
