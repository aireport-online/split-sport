# Split Sport for iOS

The native iOS client of Split Sport, Split for sports clubs (iOS 17+, SwiftUI): a team with a
team pot (Teamkas), a penningmeester, contributions (inleg), a fine list (boetelijst) and the usual
shared expenses. It follows `docs/design.md` for screens, wording and house style and talks to the
REST API described in `docs/api.md`.

## Layout

| Path | What |
| --- | --- |
| `SplitKit/` | Swift package without dependencies: models, `APIClient`, the `computeShares` port, money parsing and formatting, the colour table, sports and seasons, the trip calculation, and an `@Observable` view model for every screen. Builds and is tested on macOS and Linux. |
| `Split/` | The app target: thin SwiftUI views bound to those view models, the "Kassabon" house style (`Theme/`, `Components/`: receipts, perforations, leaders, highlighters, stamps, the Teamkas label) and the platform parts (Keychain token store, camera and photo picking, image downscaling). |
| `project.yml` | [XcodeGen](https://github.com/yonaskolb/XcodeGen) spec. `Split.xcodeproj` and `Split/Info.plist` are generated from it and are not committed. |

The target and the Swift module are still called `Split`; the app shows itself as "Split Sport"
(`CFBundleDisplayName`), with bundle identifier `online.aireport.split.sport` and its own URL scheme
`splitsport`, so it can be installed next to Split.

All state and logic live in `SplitKit`; a view only reads a view model and forwards taps. If something
can be tested without a simulator, it belongs in the package. That includes the colour table of the
house style (`SplitKit/Sources/SplitKit/Core/Palette.swift`); `Split/Theme` turns it into SwiftUI
colours and defines the text styles.

The team pot is a member of the team that is not a person (`Member.kind == .pot`, always last).
`GroupDetail.parties` turns members into what the screens draw: a person is a name on their
highlighter (their position in the team), the pot the ink "Teamkas" label. The server computes all
balances (`potCents`, `peerCents`, `pot`, `transfers`, `fineStats`); the app only displays them.

The two typefaces, Barlow Condensed Bold and JetBrains Mono (both SIL OFL), are bundled from
`Split/Resources/Fonts` together with their licence texts and registered under `UIAppFonts` in
`project.yml`. The app icon is `docs/design/v2/icon-1024.png` (the mint stroke).

## Build and run (macOS, Xcode 15 or newer)

```sh
brew install xcodegen
cd apps/ios
xcodegen            # writes Split.xcodeproj and Split/Info.plist
open Split.xcodeproj
```

Choose the **Split** scheme and a simulator, then Run. From the command line:

```sh
xcodebuild -project Split.xcodeproj -scheme Split \
  -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO build
```

Run `xcodegen` again after adding, moving or removing files. To run on a device, pick your team under
*Signing & Capabilities*.

## Which server it talks to

- **Debug builds** default to `http://localhost:8787`, which reaches a server on the Mac from the
  simulator (`pnpm --filter @split/api dev` in the repository root starts one; `pnpm --filter
  @split/api seed` fills it with a demo team).
- **Release builds** use `AppConfig.releaseServerURL` in `Split/App/AppConfig.swift`. **It is a
  placeholder (`https://set-the-split-sport-server.invalid`) that never resolves: set it to the real
  Split Sport server before making a release build.** Until then a release build can only be used
  by typing a server on the login screen.
- At run time, the **Server** field behind **Geavanceerd** on the login screen points the app at any
  other server, for example `http://192.168.1.20:8787` to reach a Mac from an iPhone on the same
  Wi-Fi. The address is stored in UserDefaults and used from then on; plain `http` works for
  `localhost`, `.local` names and IP addresses (`NSAllowsLocalNetworking`), everything else needs `https`.

The session token is stored in the Keychain. `splitsport://join/<code>` opens the "Deelnemen" screen:

```sh
xcrun simctl openurl booted splitsport://join/ABCD2345
```

## Tests

The tests live in the package and need no simulator.

```sh
cd apps/ios/SplitKit
swift test                       # macOS: uses Xcode's toolchain
```

On Linux, use a Swift 5.9+ toolchain (developed with 6.2):

```sh
export PATH=/opt/swift-dl/swift-6.2-RELEASE-ubuntu24.04/usr/bin:$PATH
cd apps/ios/SplitKit && swift test
```

The suite includes the shared vectors of `spec/split-vectors.json` (read from the repository, not
copied) and view-model tests against a fake API. One integration test drives the real `APIClient`
against a running server, including the team pot (contribution, fine, paying the pot, paying from
the pot, the fine list, the penningmeester), and is skipped unless `SPLIT_API_URL` is set:

```sh
# in another terminal, from apps/api:
PORT=8792 DATABASE_PATH=/tmp/split-sport-ios-test.db pnpm exec tsx src/server.ts

SPLIT_API_URL=http://localhost:8792 swift test --filter IntegrationTests
```

It registers throw-away accounts and deletes them again.
