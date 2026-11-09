import SplitKit
import SwiftUI

/// "Groepen": the home screen, and the root of the navigation stack.
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
                .navigationTitle("Groepen")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            sheet = .profile
                        } label: {
                            Image(systemName: "person.crop.circle")
                        }
                        .accessibilityLabel("Profiel")
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Button {
                                sheet = .newGroup
                            } label: {
                                Label("Nieuwe groep", systemImage: "plus")
                            }
                            Button {
                                sheet = .join(code: nil)
                            } label: {
                                Label("Deelnemen met code", systemImage: "person.badge.plus")
                            }
                        } label: {
                            Image(systemName: "plus")
                        }
                        .accessibilityLabel("Groep toevoegen")
                    }
                }
                .navigationDestination(for: GroupRoute.self) { route in
                    GroupView(session: session, route: route, onLeave: { path.removeAll() })
                }
                // Runs again when coming back from a group, so balances are fresh.
                .task { await model.load() }
        }
        // On the stack itself, so an invite link also opens while a group is on screen.
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
            List {
                Section {
                    TotalBalanceRow(text: model.totalBalanceText, sign: model.totalBalanceSign)
                }
                .listRowBackground(Theme.surface)

                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }

                if model.groups.isEmpty {
                    emptyState
                } else {
                    Section {
                        ForEach(model.groups) { group in
                            NavigationLink(value: GroupRoute(group)) {
                                GroupRow(group: group)
                            }
                        }
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .themedList()
            .refreshable { await model.load() }
        } else if let message = model.errorMessage {
            LoadFailedView(message: message) { await model.load() }
        } else {
            LoadingView()
        }
    }

    private var emptyState: some View {
        Section {
            VStack(spacing: 16) {
                Image(systemName: "person.3")
                    .font(.largeTitle)
                    .foregroundStyle(Theme.mutedText)
                    .accessibilityHidden(true)
                Text("Nog geen groepen. Maak er een voor je huis, vakantie of etentje.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Theme.mutedText)
                Button("Nieuwe groep") {
                    sheet = .newGroup
                }
                .buttonStyle(PrimaryButtonStyle())
                Button("Deelnemen met code") {
                    sheet = .join(code: nil)
                }
                .buttonStyle(.borderless)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
        }
        .listRowBackground(Color.clear)
    }

    /// Opens a group that was just created or joined.
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

/// The balance over all groups, in words.
@MainActor
private struct TotalBalanceRow: View {
    let text: String
    let sign: Wording.BalanceSign

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Totaal over alle groepen")
                .font(.subheadline)
                .foregroundStyle(Theme.mutedText)
            Text(text)
                .font(.title2.weight(.semibold))
                .foregroundStyle(sign.color)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// One group: colour tile with initials, name, number of members and your balance.
@MainActor
private struct GroupRow: View {
    let group: GroupSummary

    var body: some View {
        HStack(spacing: 12) {
            InitialsBadge(initials: Initials.of(group.name), color: group.color, outline: .tile, side: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(group.name)
                    .font(.headline)
                Text(Wording.memberCount(group.memberCount))
                    .font(.subheadline)
                    .foregroundStyle(Theme.mutedText)
            }
            Spacer(minLength: 8)
            Text(Money.formatSigned(group.myBalanceCents))
                .font(.callout)
                .monospacedDigit()
                .foregroundStyle(Wording.BalanceSign(cents: group.myBalanceCents).color)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}
