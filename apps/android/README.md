# Split Sport for Android

The native Android client of Split Sport, Split for sports clubs: a team with a pot (teamkas), a
penningmeester, contributions and a fine list. Kotlin, Jetpack Compose, one `app` module, one
activity.
What it must do and how it must look is in [`docs/design.md`](../../docs/design.md); the server it
talks to is described in [`docs/api.md`](../../docs/api.md).

## Build and run

Requirements: JDK 17 or newer and the Android SDK (platform 36). Point `ANDROID_HOME` at the SDK or
open the folder in Android Studio, which writes `local.properties` for you (never commit that file).

```sh
cd apps/android
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug         # onto a connected device or emulator
```

In Android Studio: open `apps/android` (not the repository root) and run the `app` configuration.

## Which server

| | |
| --- | --- |
| Debug builds | `http://10.0.2.2:8787`: the API on the machine that runs the emulator (`pnpm --filter @split/api dev`). |
| Another server at build time | `./gradlew :app:assembleDebug -Psplit.serverUrl=https://split.example.org` (or put `split.serverUrl=…` in `~/.gradle/gradle.properties`). |
| Release builds | No server is built in unless `split.serverUrl` is given; the login screen then asks for one. |
| At run time | Login screen → "Geavanceerd" → "Server". The address is remembered with the session. |

Plain `http://` only works in debug builds (`src/debug/res/xml/network_security_config.xml`);
release builds talk HTTPS only. On a real phone use the computer's LAN address, e.g.
`http://192.168.1.20:8787`.

The application id is `online.aireport.split.sport` (the Kotlin package stays
`online.aireport.split`) and invitation links open the app as `splitsport://join/<code>`, so Split
and Split Sport can be installed side by side:

```sh
adb shell am start -a android.intent.action.VIEW -d "splitsport://join/ABCD2345"
```

## Tests

```sh
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:lintDebug            # Android lint, report in app/build/reports
```

- **Split vectors.** `SplitVectorsTest` runs the Kotlin port of `computeShares` against
  `spec/split-vectors.json`, read in place from the repository.
- **Integration test.** `SplitApiIntegrationTest` drives the repositories against a live server:
  register, a team with its pot and fine list, a contribution and a fine, paying the pot, expenses
  from and for the pot, an expense in every split mode, settling, joining, deleting. It is skipped
  unless `SPLIT_API_URL` is set:

  ```sh
  (cd ../api && PORT=8791 DATABASE_PATH=/tmp/split-sport-android-test.db pnpm exec tsx src/server.ts) &
  SPLIT_API_URL=http://localhost:8791 ./gradlew :app:testDebugUnitTest --tests '*IntegrationTest'
  ```

- **Screenshots.** `ScreenshotTest` renders the screens with fake state on the JVM (Robolectric
  native graphics + Roborazzi), in light and dark and at the largest font size. As a unit test it
  only checks that every screen composes; to write the PNGs to `app/build/outputs/roborazzi`:

  ```sh
  ./gradlew :app:recordRoborazziDebug
  ```

  The first run downloads Robolectric's Android runtime from Maven Central.

## How the code is laid out

```
app/src/main/java/online/aireport/split/
  core/        pure Kotlin: computeShares, money parsing and formatting, dates, seasons, rides, invite codes
  data/        the REST API (Retrofit + kotlinx.serialization), the session (DataStore), repositories
  ui/theme/    the "Kassabon" house style: colours, highlighters, the two typefaces
  ui/components/  Receipt, ReceiptLine (leader), PersonName (highlighter), PotName (the Teamkas label),
               Stamp, buttons, fields, …
  ui/<screen>/ one package per screen: a view model with a StateFlow, a stateless screen composable
               (groups = Teams, group = a team with Uitgaven / Teamkas / Saldo, charge = Boete geven
               and Inleg vragen, expense, settings, join, profile, auth)
  ui/navigation/  the navigation graph; signing in or out replaces the whole back stack
  AppContainer.kt  the dependencies, created once (no DI framework)
```

The pot is a member of the team (`Member.kind == POT`, always last), so it can pay, be paid and be
split over like anybody else; the app only shows what the server computes (`potCents`, `peerCents`,
`pot.cashCents`) and never charges the pot. In the code a team is still a "group", as in the API.

Money is integer cents everywhere. Every call to the server goes through `ApiGateway`, which turns
failures into `ApiException` (the server's Dutch `error.message` is shown as-is) and ends the
session on HTTP 401.

`compileSdk` is 36, so a few libraries are held one release back; `gradle/libs.versions.toml` says
which and why.

## Fonts

Barlow Condensed and JetBrains Mono are bundled in `app/src/main/res/font`; both are licensed under
the SIL Open Font License, see `app/src/main/assets/licenses`.
