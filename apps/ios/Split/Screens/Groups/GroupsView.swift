import SplitKit
import SwiftUI

/// "Teams": the home screen, and the root of the navigation stack.
@MainActor
struct GroupsView: View {
    private enum Sheet: Identifiable {
        case newGroup
        case join(code: String?)
        case profile

        var id: String {
            switch self {
            case .newGroup: return "newGroup"
            case .join(let code): return "join-\(code ?? "")"
            case .profile: return "profile"
            }
        }
    }

    let session: AppSession
    @State private var model: GroupsViewModel
    @State private var path: [GroupRoute] = []
    @State private var sheet: Sheet?

    init(session: AppSession) {
        self.session = session
        _model = State(initialValue: GroupsViewModel(api: session.api))
    }

    var body: some View {
        NavigationStack(path: $path) {
            content
                .navigationTitle("Teams")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            sheet = .profile
                        } label: {
                            Image(systemName: "person.crop.circle")
                        }
                        .accessibilityLabel("Profiel")
                    }
                }
                .navigationDestination(for: GroupRoute.self) { route in
                    GroupView(session: session, route: route, onLeave: { path.removeAll() })
                }
                // Runs again when coming back from a team, so balances are fresh.
                .task { await model.load() }
        }
        // On the stack itself, so an invite link also opens while a team is on screen.
        .task(id: session.pendingInviteCode) { showPendingInvite() }
        .sheet(item: $sheet, onDismiss: { Task { await model.load() } }) { sheet in
            switch sheet {
            case .newGroup:
                NewGroupSheet(api: session.api, existingGroupCount: model.groups.count, onCreated: { group in show(group) })
            case .join(let code):
                JoinSheet(api: session.api, code: code, onJoined: { group in show(group) })
            case .profile:
                ProfileSheet(session: session)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.hasLoaded {
            Desk {
                TotalBalanceReceipt(
                    figure: model.totalBalanceFigure, words: model.totalBalanceText, isZero: model.totalBalanceCents == 0
                )
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                if model.groups.isEmpty {
                    emptyState
                } else {
                    ForEach(model.groups) { group in
                        NavigationLink(value: GroupRoute(group)) {
                            GroupReceipt(group: group)
                        }
                        .buttonStyle(.plain)
                    }
                }
                Button("Deelnemen met code") {
                    sheet = .join(code: nil)
                }
                .buttonStyle(PaperButtonStyle())
            }
            .refreshable { await model.load() }
            .safeAreaInset(edge: .bottom) {
                BottomBar {
                    Button("Nieuw team") {
                        sheet = .newGroup
                    }
                    .buttonStyle(PaperButtonStyle(kind: .primary))
                }
            }
        } else if let message = model.errorMessage {
            LoadFailedView(message: message) { await model.load() }
        } else {
            LoadingView()
        }
    }

    private var emptyState: some View {
        Receipt {
            ReceiptTitle(text: "Nog geen teams")
            Text("Maak er een voor je elftal, zevental of trainingsgroep.")
                .foregroundStyle(Theme.mutedInk)
        }
    }

    /// Opens a team that was just created or joined.
    private func show(_ group: GroupDetail) {
        sheet = nil
        path = [GroupRoute(group)]
    }

    /// An invite link was opened: show the join sheet with its code.
    private func showPendingInvite() {
        guard let code = session.pendingInviteCode else { return }
        session.pendingInviteCode = nil
        sheet = .join(code: code)
    }
}

/// The "Jouw saldo" receipt: the balance over all teams as a large figure, and in words.
@MainActor
private struct TotalBalanceReceipt: View {
    let figure: String
    let words: String
    let isZero: Bool

    var body: some View {
        Receipt {
            Text("Jouw saldo")
            Text(figure)
                .font(.tillFigure)
                .foregroundStyle(isZero ? Theme.mutedInk : Theme.ink)
                .accessibilityHidden(true)
            Text(words)
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
        }
        .accessibilityElement(children: .combine)
    }
}

/// One team as a small receipt: its name underlined with its highlighter, the
/// line "Hockey · MHC Oostpoort · 2026/27", the number of team members and your balance.
@MainActor
private struct GroupReceipt: View {
    let group: GroupSummary

    var body: some View {
        Receipt {
            GroupNameText(name: group.name, color: group.color)
            Text(Wording.teamLine(sport: group.sport, club: group.club, season: group.season))
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(Wording.memberCount(group.memberCount))
                    .font(.tillSmall)
                Spacer(minLength: 8)
                Text(Money.signedEuro(group.myBalanceCents))
                    .foregroundStyle(group.myBalanceCents == 0 ? Theme.mutedInk : Theme.ink)
                    .accessibilityLabel(Wording.ownBalance(group.myBalanceCents))
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
