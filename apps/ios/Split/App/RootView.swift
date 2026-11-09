import SplitKit
import SwiftUI

/// Shows the login screen or the app, depending on the session.
@MainActor
struct RootView: View {
    let session: AppSession

    var body: some View {
        switch session.phase {
        case .launching:
            ProgressView("Laden…")
        case .unreachable(let message):
            UnreachableView(session: session, message: message)
        case .signedOut:
            AuthView(session: session)
        case .signedIn:
            GroupsView(session: session)
        }
    }
}

/// There is a stored session, but the server could not be reached to resume it.
@MainActor
private struct UnreachableView: View {
    let session: AppSession
    let message: String

    var body: some View {
        ContentUnavailableView {
            Label("Geen verbinding", systemImage: "wifi.slash")
        } description: {
            Text(message)
        } actions: {
            Button("Opnieuw proberen") {
                Task { await session.restore() }
            }
            .buttonStyle(PrimaryButtonStyle())
            Button("Uitloggen") {
                session.forgetSession()
            }
        }
    }
}
