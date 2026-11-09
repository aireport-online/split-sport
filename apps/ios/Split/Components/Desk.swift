import SwiftUI

/// The ground of every screen: a scrolling column of receipts on the desk,
/// with the till's typeface as the default for everything on it.
@MainActor
struct Desk<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                content
            }
            .padding(.horizontal, Theme.deskPadding)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.desk)
        .font(.till)
        .foregroundStyle(Theme.ink)
    }
}

/// The primary action of a screen, pinned above the bottom edge.
@MainActor
struct BottomBar<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(.horizontal, Theme.deskPadding)
            .padding(.vertical, 12)
            .background(Theme.desk)
    }
}

/// A failed request: the server's message in stamp red.
@MainActor
struct ErrorBanner: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.tillSmall)
            .foregroundStyle(Theme.stamp)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay {
                RoundedRectangle(cornerRadius: Theme.radius).strokeBorder(Theme.stamp, lineWidth: Theme.outline)
            }
    }
}

/// A screen that could not load at all, with a way to try again.
@MainActor
struct LoadFailedView: View {
    let message: String
    let retry: () async -> Void

    var body: some View {
        Desk {
            Receipt {
                ReceiptTitle(text: "Laden mislukt")
                Text(message)
                    .foregroundStyle(Theme.mutedInk)
                Button("Opnieuw proberen") {
                    Task { await retry() }
                }
                .buttonStyle(PaperButtonStyle())
            }
        }
    }
}

/// The first load of a screen.
@MainActor
struct LoadingView: View {
    var body: some View {
        ProgressView("Laden…")
            .font(.till)
            .tint(Theme.ink)
            .foregroundStyle(Theme.mutedInk)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.desk)
    }
}
