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
            Desk {
                if let preview = model.preview {
                    groupReceipt(preview)
                    if model.needsIdentity {
                        identityReceipt(preview)
                    }
                } else {
                    codeReceipt
                }
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                if model.preview != nil {
                    LinkButton(title: "Andere code invoeren") {
                        model.chooseOtherCode()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Deelnemen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleren") { dismiss() }
                        .font(.buttonLabel)
                }
            }
            .safeAreaInset(edge: .bottom) {
                BottomBar {
                    primaryButton
                }
            }
            .task { await model.lookUpIfPrefilled() }
        }
    }

    private var codeReceipt: some View {
        Receipt {
            PaperField(label: "Uitnodigingscode", text: $model.codeText, kind: .code)
            Text("De code van 8 tekens staat in de uitnodiging. Een uitnodigingslink plakken kan ook.")
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
        }
    }

    private func groupReceipt(_ preview: InvitePreview) -> some View {
        Receipt {
            GroupNameText(name: preview.name, color: preview.color)
            Text(Wording.memberCount(preview.memberCount))
                .font(.tillSmall)
            if preview.alreadyMember {
                Text("Je bent al lid van deze groep.")
                    .foregroundStyle(Theme.mutedInk)
            }
        }
    }

    private func identityReceipt(_ preview: InvitePreview) -> some View {
        Receipt {
            ReceiptTitle(text: "Wie ben jij?")
            ForEach(preview.unclaimedMembers) { member in
                IdentityRow(title: member.name, isSelected: model.identity == .member(id: member.id)) {
                    model.identity = .member(id: member.id)
                }
            }
            IdentityRow(title: "Ik sta er niet tussen", isSelected: model.identity == .newMember) {
                model.identity = .newMember
            }
            Text("Deze leden hebben nog geen account. Kies je naam om hun uitgaven over te nemen.")
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
        }
    }

    @ViewBuilder
    private var primaryButton: some View {
        if let preview = model.preview {
            Button {
                Task {
                    if let group = await model.join() {
                        onJoined(group)
                    }
                }
            } label: {
                BusyLabel(title: preview.alreadyMember ? "Open groep" : "Deelnemen", isBusy: model.isWorking)
            }
            .buttonStyle(PaperButtonStyle(kind: .primary))
            .disabled(!model.canJoin)
        } else {
            Button {
                Task { await model.lookUp() }
            } label: {
                BusyLabel(title: "Zoek groep", isBusy: model.isWorking)
            }
            .buttonStyle(PaperButtonStyle(kind: .primary))
            .disabled(!model.canLookUp)
        }
    }
}

/// One answer to "Wie ben jij?": a checkbox and a name.
@MainActor
private struct IdentityRow: View {
    let title: String
    let isSelected: Bool
    let select: () -> Void

    var body: some View {
        Button(action: select) {
            HStack(spacing: 12) {
                Checkbox(isChecked: isSelected)
                Text(title)
                Spacer(minLength: 0)
            }
            .frame(minHeight: 32)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
