import SplitKit
import SwiftUI

/// Team settings: name, sport, club, season, highlighter, penningmeester, the
/// fine list, team members, invitation and leaving the team.
@MainActor
struct GroupSettingsSheet: View {
    let onLeave: () -> Void
    @State private var model: GroupSettingsViewModel
    @State private var memberToRename: GroupSettingsViewModel.MemberRow?
    @State private var renameText = ""
    @State private var isConfirmingLeave = false
    @Environment(\.dismiss) private var dismiss

    init(model: GroupSettingsViewModel, onLeave: @escaping () -> Void) {
        self.onLeave = onLeave
        _model = State(initialValue: model)
    }

    var body: some View {
        NavigationStack {
            Desk {
                teamReceipt
                Receipt {
                    ReceiptTitle(text: "Kleur")
                    HighlighterPicker(selection: colorBinding)
                }
                potReceipt
                FineListReceipt(model: model)
                membersReceipt
                inviteReceipt
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                Button("Team verlaten") {
                    isConfirmingLeave = true
                }
                .buttonStyle(PaperButtonStyle(kind: .destructive))
                .disabled(model.isWorking)
            }
            .navigationTitle("Teaminstellingen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gereed") { dismiss() }
                        .font(.buttonLabel)
                }
            }
            .alert("Teamlid hernoemen", isPresented: isRenaming) {
                TextField("Naam", text: $renameText)
                Button("Opslaan") {
                    if let member = memberToRename {
                        Task { await model.renameMember(id: member.id, to: renameText) }
                    }
                    memberToRename = nil
                }
                Button("Annuleren", role: .cancel) {
                    memberToRename = nil
                }
            }
            .confirmationDialog("Team verlaten?", isPresented: $isConfirmingLeave, titleVisibility: .visible) {
                Button("Verlaat team", role: .destructive) {
                    Task { await leave() }
                }
                Button("Annuleren", role: .cancel) {}
            } message: {
                Text("Je uitgaven blijven in het team staan, zodat de saldo's van de anderen kloppen.")
            }
        }
    }

    /// Name, sport, club and season.
    private var teamReceipt: some View {
        Receipt {
            PaperField(label: "Naam", text: $model.name)
            SportMenu(selection: sportBinding)
            PaperField(label: "Club (mag leeg blijven)", text: $model.club)
            PaperField(label: "Seizoen", text: $model.season)
            Button("Opslaan") {
                Task { await model.saveDetails() }
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))
            .disabled(!model.canSaveDetails)
        }
    }

    /// Who keeps the pot's money.
    private var potReceipt: some View {
        Receipt {
            ReceiptTitle(text: "Teamkas")
            MemberMenu(label: "Penningmeester", members: model.treasurerCandidates, selection: treasurerBinding)
            Text("De penningmeester beheert het geld van de teamkas. Inleg en boetes worden naar zijn of haar IBAN overgemaakt.")
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
        }
    }

    private var membersReceipt: some View {
        Receipt {
            ReceiptTitle(text: "Teamleden")
            ForEach(model.memberRows) { row in
                memberRow(row)
            }
            Perforation()
            PaperField(label: "Nieuw teamlid", text: $model.newMemberName, kind: .name)
            Button("Voeg toe") {
                Task { await model.addMember() }
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))
            .disabled(!model.canAddMember)
        }
    }

    private var inviteReceipt: some View {
        Receipt {
            ReceiptTitle(text: "Uitnodigen")
            LeaderRow(amount: model.detail.inviteCode) {
                Text("Code")
            }
            .font(.tillBold)
            .textSelection(.enabled)
            Text("Iedereen met de link of de code kan deelnemen aan dit team.")
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
            ShareLink(item: model.inviteURL) {
                Text("Deel uitnodiging")
            }
            .buttonStyle(PaperButtonStyle())
        }
    }

    private func memberRow(_ row: GroupSettingsViewModel.MemberRow) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            PartyName(party: row.party)
            if row.isMe {
                Text("(jij)")
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
            }
            if !row.hasAccount {
                DashedPill(text: "geen account")
            }
            Spacer(minLength: 8)
            if row.isEditable {
                memberMenu(row)
            }
        }
        .frame(minHeight: 32)
    }

    /// Rename or remove: only offered for team members without an account.
    private func memberMenu(_ row: GroupSettingsViewModel.MemberRow) -> some View {
        Menu {
            Button {
                renameText = row.party.name
                memberToRename = row
            } label: {
                Label("Hernoem", systemImage: "pencil")
            }
            Button(role: .destructive) {
                Task { await model.removeMember(id: row.id) }
            } label: {
                Label("Verwijder", systemImage: "trash")
            }
        } label: {
            Image(systemName: "ellipsis")
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Acties voor \(row.party.name)")
    }

    private func leave() async {
        if await model.leave() {
            dismiss()
            onLeave()
        }
    }

    private var colorBinding: Binding<GroupColor> {
        Binding(
            get: { model.detail.color },
            set: { color in
                Task { await model.setColor(color) }
            }
        )
    }

    private var sportBinding: Binding<Sport> {
        Binding(
            get: { model.detail.sport },
            set: { sport in
                Task { await model.setSport(sport) }
            }
        )
    }

    private var treasurerBinding: Binding<String> {
        Binding(
            get: { model.detail.treasurerMemberId ?? "" },
            set: { memberId in
                Task { await model.setTreasurer(memberId: memberId) }
            }
        )
    }

    private var isRenaming: Binding<Bool> {
        Binding(
            get: { memberToRename != nil },
            set: { isShown in
                if !isShown { memberToRename = nil }
            }
        )
    }
}
