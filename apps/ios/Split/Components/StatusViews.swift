import SwiftUI

/// A failed request inside a list or form: the server's message in the error colour.
@MainActor
struct ErrorRow: View {
    let message: String

    var body: some View {
        Label(message, systemImage: "exclamationmark.triangle")
            .foregroundStyle(Theme.negative)
            .font(.callout)
    }
}

/// A screen that could not load at all, with a way to try again.
@MainActor
struct LoadFailedView: View {
    let message: String
    let retry: () async -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Laden mislukt", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Opnieuw proberen") {
                Task { await retry() }
            }
            .buttonStyle(PrimaryButtonStyle())
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.ground)
    }
}

/// The first load of a screen.
@MainActor
struct LoadingView: View {
    var body: some View {
        ProgressView("Laden…")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.ground)
    }
}
