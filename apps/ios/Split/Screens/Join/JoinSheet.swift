import SplitKit
import SwiftUI

/// "Deelnemen": enter an invite code (or arrive by link), see the group and say who you are.
@MainActor
struct JoinSheet: View {
    let onJoined: (GroupDetail) -> Void
    @State private var model: JoinViewModel
    @Environment(\.dismiss) private var dismiss

    init(api: SplitAPI, code: String?, onJoined: @escaping (GroupDetail) -> Void) {
        self.onJoined = onJoined
        _model = State(initialValue: JoinViewModel(api: api, code: code))
    }

    var body: some View {
        NavigationStack {
            Form {
                if let preview = model.preview {
                    groupSections(preview)
                } else {
                    codeSections
                }

                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .themedList()
            .navigationTitle("Deelnemen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleer") { dismiss() }
                }
            }
            .task { await model.lookUpIfPrefilled() }
        }
    }

    @ViewBuilder
    private var codeSections: some View {
        Section {
            TextField("Uitnodigingscode", text: $model.codeText)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .font(.body.monospaced())
        } footer: {
            Text("De code van 8 tekens staat in de uitnodiging. Een uitnodigingslink plakken kan ook.")
        }
        .listRowBackground(Theme.surface)

        Section {
            Button {
                Task { await model.lookUp() }
            } label: {
                HStack {
                    Text("Zoek groep")
                        .frame(maxWidth: .infinity)
                    if model.isWorking {
                        ProgressView()
                    }
                }
            }
            .disabled(!model.canLookUp)
        }
        .listRowBackground(Theme.surface)
    }

    @ViewBuilder
    private func groupSections(_ preview: InvitePreview) -> some View {
        Section {
            HStack(spacing: 12) {
                InitialsBadge(initials: Initials.of(preview.name), color: preview.color, outline: .tile, side: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(preview.name)
                        .font(.headline)
                    Text(Wording.memberCount(preview.memberCount))
                        .font(.subheadline)
                        .foregroundStyle(Theme.mutedText)
                }
            }
            .accessibilityElement(children: .combine)
            if preview.alreadyMember {
                Text("Je bent al lid van deze groep.")
                    .foregroundStyle(Theme.mutedText)
            }
        }
        .listRowBackground(Theme.surface)

        if model.needsIdentity {
            Section {
                ForEach(preview.unclaimedMembers) { member in
                    IdentityRow(title: member.name, isSelected: model.identity == .member(id: member.id)) {
                        model.identity = .member(id: member.id)
                    }
                }
                IdentityRow(title: "Ik sta er niet tussen", isSelected: model.identity == .newMember) {
                    model.identity = .newMember
                }
            } header: {
                Text("Wie ben jij?")
            } footer: {
                Text("Deze leden hebben nog geen account. Kies je naam om hun uitgaven over te nemen.")
            }
            .listRowBackground(Theme.surface)
        }

        Section {
            Button {
                Task {
                    if let group = await model.join() {
                        onJoined(group)
                    }
                }
            } label: {
                HStack {
                    Text(preview.alreadyMember ? "Open groep" : "Deelnemen")
                        .frame(maxWidth: .infinity)
                    if model.isWorking {
                        ProgressView()
                    }
                }
            }
            .disabled(!model.canJoin)

            Button("Andere code invoeren") {
                model.chooseOtherCode()
            }
        }
        .listRowBackground(Theme.surface)
    }
}

/// One answer to "Wie ben jij?", with a checkmark when chosen.
@MainActor
private struct IdentityRow: View {
    let title: String
    let isSelected: Bool
    let select: () -> Void

    var body: some View {
        Button(action: select) {
            HStack {
                Text(title)
                    .foregroundStyle(Theme.text)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark")
                        .foregroundStyle(Theme.accent)
                        .accessibilityHidden(true)
                }
            }
        }
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
