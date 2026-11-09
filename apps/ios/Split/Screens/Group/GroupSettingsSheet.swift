import SplitKit
import SwiftUI

/// Group settings: rename, colour, members, invitation and leaving the group.
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
                nameReceipt
                Receipt {
                    ReceiptTitle(text: "Kleur")
                    HighlighterPicker(selection: colorBinding)
                }
                membersReceipt
                inviteReceipt
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                Button("Groep verlaten") {
                    isConfirmingLeave = true
                }
                .buttonStyle(PaperButtonStyle(kind: .destructive))
                .disabled(model.isWorking)
            }
            .navigationTitle("Groepsinstellingen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gereed") { dismiss() }
                        .font(.buttonLabel)
                }
            }
            .alert("Lid hernoemen", isPresented: isRenaming) {
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
            .confirmationDialog("Groep verlaten?", isPresented: $isConfirmingLeave, titleVisibility: .visible) {
                Button("Verlaat groep", role: .destructive) {
                    Task { await leave() }
                }
                Button("Annuleren", role: .cancel) {}
            } message: {
                Text("Je uitgaven blijven in de groep staan, zodat de saldo's van de anderen kloppen.")
            }
        }
    }

    private var nameReceipt: some View {
        Receipt {
            PaperField(label: "Naam", text: $model.name)
            Button("Opslaan") {
                Task { await model.saveName() }
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))
            .disabled(!model.canSaveName)
        }
    }

    private var membersReceipt: some View {
        Receipt {
            ReceiptTitle(text: "Leden")
            ForEach(model.memberRows) { row in
                memberRow(row)
            }
            Perforation()
            PaperField(label: "Nieuw lid", text: $model.newMemberName, kind: .name)
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
            Text("Iedereen met de link of de code kan deelnemen aan deze groep.")
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
            Highlight(name: row.name, color: row.color)
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

    /// Rename or remove: only offered for members without an account.
    private func memberMenu(_ row: GroupSettingsViewModel.MemberRow) -> some View {
        Menu {
            Button {
                renameText = row.name
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
        .accessibilityLabel("Acties voor \(row.name)")
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

    private var isRenaming: Binding<Bool> {
        Binding(
            get: { memberToRename != nil },
            set: { isShown in
                if !isShown { memberToRename = nil }
            }
        )
    }
}
