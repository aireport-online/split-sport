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
            Form {
                nameSection
                Section("Kleur") {
                    TintPicker(selection: colorBinding)
                }
                .listRowBackground(Theme.surface)
                membersSection
                inviteSection
                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }
                Section {
                    Button("Groep verlaten", role: .destructive) {
                        isConfirmingLeave = true
                    }
                    .disabled(model.isWorking)
                }
                .listRowBackground(Theme.surface)
            }
            .themedList()
            .navigationTitle("Groepsinstellingen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gereed") { dismiss() }
                }
            }
            .alert("Lid hernoemen", isPresented: isRenaming) {
                TextField("Naam", text: $renameText)
                Button("Bewaar") {
                    if let member = memberToRename {
                        Task { await model.renameMember(id: member.id, to: renameText) }
                    }
                    memberToRename = nil
                }
                Button("Annuleer", role: .cancel) {
                    memberToRename = nil
                }
            }
            .confirmationDialog("Groep verlaten?", isPresented: $isConfirmingLeave, titleVisibility: .visible) {
                Button("Verlaat groep", role: .destructive) {
                    Task { await leave() }
                }
                Button("Annuleer", role: .cancel) {}
            } message: {
                Text("Je uitgaven blijven in de groep staan, zodat de saldo's van de anderen kloppen.")
            }
        }
    }

    private var nameSection: some View {
        Section("Naam") {
            HStack {
                TextField("Naam van de groep", text: $model.name)
                    .onSubmit {
                        Task { await model.saveName() }
                    }
                Button("Bewaar") {
                    Task { await model.saveName() }
                }
                .buttonStyle(.borderless)
                .disabled(!model.canSaveName)
            }
        }
        .listRowBackground(Theme.surface)
    }

    private var membersSection: some View {
        Section("Leden") {
            ForEach(model.memberRows) { row in
                memberRow(row)
            }
            HStack {
                TextField("Naam van nieuw lid", text: $model.newMemberName)
                    .textContentType(.name)
                    .onSubmit {
                        Task { await model.addMember() }
                    }
                Button("Voeg toe") {
                    Task { await model.addMember() }
                }
                .buttonStyle(.borderless)
                .disabled(!model.canAddMember)
            }
        }
        .listRowBackground(Theme.surface)
    }

    private var inviteSection: some View {
        Section {
            LabeledContent("Code") {
                Text(model.detail.inviteCode)
                    .font(.body.monospaced())
                    .textSelection(.enabled)
            }
            ShareLink(item: model.inviteURL) {
                Label("Deel uitnodiging", systemImage: "square.and.arrow.up")
            }
        } header: {
            Text("Uitnodigen")
        } footer: {
            Text("Iedereen met de link of de code kan deelnemen aan deze groep.")
        }
        .listRowBackground(Theme.surface)
    }

    private func memberRow(_ row: GroupSettingsViewModel.MemberRow) -> some View {
        HStack(spacing: 12) {
            InitialsBadge(initials: row.initials, color: row.tint, outline: .circle, side: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.displayName)
                if !row.hasAccount {
                    Text("geen account")
                        .font(.footnote)
                        .foregroundStyle(Theme.mutedText)
                }
            }
            Spacer(minLength: 8)
            if row.isEditable {
                memberMenu(row)
            }
        }
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
            Image(systemName: "ellipsis.circle")
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
