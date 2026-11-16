# Attention+ — Firebase accounts and learning sync

The existing Compose application now uses Hungarian labels throughout its current screens. The supplied transparent logo is used on login and registration. The illustrated Home cards and graduation cap remain; Tanulási napló opens a ruled notebook with independently curling pages, saved session details and original-material review. The wooden Bolt has six shelf slots, two illustrated products, a directional page transition and an acceleration-driven suspended sign. Settings now persist and control study defaults, daily goals, reminders, sound, vibration and reduced motion, with account and data-management actions. Navigation arrows respect insets and the actual previous route.

The separately installed Android application ID is `com.dattila.attentionplus`; the existing source namespace stays `com.example.a`. The old `com.example.a` installation is not removed. Minimum Android API is 33; compile/target API is 37. Kotlin, Compose, AGP and Gradle versions are unchanged.

Firebase Auth handles passwords and Google credentials. Firestore holds the account profile, immutable user-change journal, learning drafts/sessions, Focus records, preferences, legacy archive and server-confirmed rewards. An account-scoped durable outbox and WorkManager retain offline edits and retry them. Storage stores private note photos and avatars when available. See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) for the full data inventory, schema, security, migration, setup and verification details.

**Deployment status:** the user chose the free Spark plan. Email/Password and Google providers are enabled, the refreshed Android configuration includes Google OAuth clients, and Firestore rules/indexes are deployed. Cloud Functions and Storage are implemented and tested locally but cannot be deployed on the current plan. Uploaded image bytes therefore remain queued locally; username login, canonical reward approval, purchases, item activation, scheduled streak protection and full account deletion require the later backend deployment. Firestore still saves session history, notes, settings and pending completion records. This is not a claim that the entire cloud system is live.

The analysis label follows the saved material: “AI kép elemzés”, “AI szöveg elemzés”, or “AI kép- és szövegelemzés”. Its existing 26-second processing stage and ten-question history practice set remain placeholders: no AI inference API was added, and all subjects still use that question set.

## Historical fact references

Question explanations are original summaries based on:
- https://history.state.gov/countries/hungary (Trianon and Nagy Imre's neutrality declaration)
- https://history.state.gov/milestones/1945-1952/nato (Marshall Plan, NATO and Cold War context)
- https://history.state.gov/milestones/1945-1952/berlin-airlift (Berlin blockade and airlift)
- https://www.nato.int/en/about-us/nato-history/a-short-history-of-nato (INF Treaty, Berlin Wall)
- https://nato.int/en/about-us/nato-history/history-by-theme/my-country-and-nato/hungary-and-nato (Kádár era and Pan-European Picnic)

## Verification

Build with the existing Gradle wrapper and installed Android Studio JDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:testDebugUnitTest
```

Firebase tests use local Auth, Firestore, Storage and Functions emulators. Never run the Android Firebase suite against production; it explicitly requires the debug emulator switch. Backend commands and the latest verified results are documented in `FIREBASE_SETUP.md`.

Use `adb install -r` and `adb shell am instrument` for an existing emulator installation; the Gradle connected-test runner may remove its test installation afterwards. Preserve the developer emulator preferences and user-data backups before tests.

The release APK is unsigned until a release signing key is configured. The debug APK can be installed directly. Older glass-navigation tests describe superseded designs and are not part of the current navigation regression suite.

## Latest interface updates

The notebook card is named **Tanulási napló** and no longer has a separate Continue label. **Dupla XP Potion** retains the `xp_potion` identity and 30-minute effect; the bottle artwork contains no text. Existing saved purchases are unchanged.

The persisted **Értesítések némítása tanulás közben** preference controls an app-owned Android Do Not Disturb rule during study and Focus timers. Policy access must be granted in Android settings. The app removes only its own rule and allows alarms, media and system sounds. An expiry broadcast also handles timer completion when the process is reclaimed; Android may delay an inexact background alarm. Full device-level policy-access and interruption testing is still outstanding.

Latest label/silence build: debug assembly, lint and local unit tests passed; the debug APK was installed and launched on the API 37 emulator. The three material-label variants have a dedicated unit test. Earlier Firebase/shop/navigation verification and Spark deployment limits are documented in FIREBASE_SETUP.md; these are not claims that the latest DND integration was tested end to end.
