# Spam Blocker (India, Android 16+)

> **Status: shelved (Oct 2026).** The app works as a manual blocker, but it could not do the one thing it was
> started for: automatically blocking calls that the network has already labelled as spam. This page records what
> was tried and why it failed, so nobody has to rediscover it.

## Findings

Tested on a Xiaomi phone running Android 16, on Jio (India), with the app set as the call-screening app.

### What works (confirmed on the device)
- Becoming the call-screening app and blocking a saved contact's number from the blocklist.
- **Reject** is invisible to the receiver. The caller hears the carrier's standard "busy" message, not a
  "declined" message.
- Flagging numbers from the Calls tab, with the Block / Spam / Not spam buttons toggling with the number's state.

Implemented and unit-tested in CI, but not reported as field-tested: pattern rules (starts / ends / contains),
region rules (state, landline / mobile), allowlist, block-all-unknown, rules export / import, call-log import.

### What does not work: blocking calls the network flags as spam
Jio's dialer integration shows "(Spam Alert From Jio) <name>" on the incoming-call screen. We tried to read it:

| Attempt | Result |
|---|---|
| `CallScreeningService`: caller display name, contact display name, verification status, extras | Always empty: `presentation=0 props=0`, name "none", verification "not verified". The label is not in the data Android gives a screening app. |
| STIR/SHAKEN caller-ID verification | "not verified" on every call, so Indian carriers don't send it in a usable form. |
| `NotificationListenerService` reading the dialer's incoming-call notification (access granted, restricted settings allowed) | Nothing captured for any spam call. The label is not in a notification this app can read. |
| Phone call log | No label stored; entries are bare numbers. |

Conclusion: the label is shown only on the dialer's live incoming-call screen and is not exposed to third-party
apps through any public Android API we could find. Where the dialer gets it (Jio's network, or a lookup the
dialer does itself) was not established.

### Other limits hit
- **A short ring before the cut.** Android hands the call to a screening app after it has reached the phone, so the
  caller hears a moment of ringing. The phone's built-in block has no such ring.
- **What the caller hears is up to the carrier.** The app can only allow, silence or reject. It cannot make a call
  sound "unreachable"; that is only possible with carrier-side forwarding rules, which apply to every caller.
- **Rotating spam numbers.** The spam calls we saw came from different mobile numbers each time, with no shared
  prefix, so blocklists and prefix rules only catch repeats.
- **Only one app can hold the screening role**, so this app replaces the dialer's own spam filter while it is set.
- Sideloaded apps need "Allow restricted settings" before Android lets them have Notification access.

### If someone picks this up
- A **spam database** is the real fix: a paid SDK (Hiya, etc.) or a community-reported list with a backend that
  stores hashed numbers. Truecaller has no open API for this.
- **Block all unknown numbers** plus a good allowlist does stop rotating spam numbers, at the cost of blocking some
  legitimate unknown callers. It is already in the app (Settings).
- An **accessibility service** reading the incoming-call screen could capture the label, but it is invasive, likely
  to be blocked by the phone maker, and would not pass Play Store review. Not recommended.
- Alternatively, hand the screening role back to the dialer and use its own spam setting.

The notification reader and the network-spam toggle were removed from the app in 0.1.10 (use the git history
before that commit to see them).

### Next experiment (in progress)
Since no external spam signal is available, the app now records how each call ended (answered / missed /
rejected / blocked, talk time) and can export a masked, labelled CSV (first 6 digits only). The plan is to test
whether KNN / SVM / simple models beat "block all unknown numbers", mainly by saving the occasional legitimate
unknown caller. Labels: your Spam / Not spam taps, answered calls of 20 s or more (legit), unanswered unknown calls
(spam by default).

Personal call blocker built on Android's `CallScreeningService`.

## Rules (first match wins)
1. Saved contact → allow
2. Allowlist → allow
3. Blocklist → block
4. Pattern rules (starts with / ends with / contains) → block
5. Region rules (state / circle, landline and mobile separately) → block
6. Block all unknown numbers (toggle) → block
7. Otherwise allow

Blocked calls are **rejected** by default (configurable to *silence*, also per number). A "network-flagged spam" toggle and a notification reader were tried and removed in 0.1.10; see Findings above.
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
