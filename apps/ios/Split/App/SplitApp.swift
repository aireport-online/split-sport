import SplitKit
import SwiftUI

@main
@MainActor
struct SplitApp: App {
    @State private var session: AppSession

    init() {
        Theme.applyNavigationBarAppearance()
        let session = AppSession(
            tokenStore: KeychainTokenStore(),
            preferences: UserDefaultsPreferences(),
            defaultServerURL: AppConfig.defaultServerURL
        )
        _session = State(initialValue: session)
    }

    var body: some Scene {
        WindowGroup {
            RootView(session: session)
                .tint(Theme.ink)
                .task { await session.restore() }
                .onOpenURL { url in
                    // split://join/<code>: the code is shown as soon as someone is logged in.
                    session.open(url)
                }
        }
    }
}
