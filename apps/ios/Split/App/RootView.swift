import SplitKit
import SwiftUI

/// Shows the login screen or the app, depending on the session.
@MainActor
struct RootView: View {
    let session: AppSession

    var body: some View {
        switch session.phase {
        case .launching:
            LoadingView()
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
        Desk {
            Wordmark()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
            Receipt {
                ReceiptTitle(text: "Geen verbinding")
                Text(message)
                    .foregroundStyle(Theme.mutedInk)
                Button("Opnieuw proberen") {
                    Task { await session.restore() }
                }
                .buttonStyle(PaperButtonStyle(kind: .primary))
                LinkButton(title: "Uitloggen") {
                    session.forgetSession()
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}
