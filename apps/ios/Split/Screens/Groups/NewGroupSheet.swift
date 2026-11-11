import SplitKit
import SwiftUI

/// "Nieuw team": name, sport, club, season and highlighter.
@MainActor
struct NewGroupSheet: View {
    let onCreated: (GroupDetail) -> Void
    @State private var model: NewGroupViewModel
    @Environment(\.dismiss) private var dismiss

    init(api: SplitAPI, existingGroupCount: Int, onCreated: @escaping (GroupDetail) -> Void) {
        self.onCreated = onCreated
        _model = State(initialValue: NewGroupViewModel(api: api, existingGroupCount: existingGroupCount))
    }

    var body: some View {
        NavigationStack {
            Desk {
                Receipt {
                    PaperField(label: "Naam", text: $model.name)
                    Text("Bijvoorbeeld Heren 3, Dames 2 of de zaterdagploeg.")
                        .font(.tillSmall)
                        .foregroundStyle(Theme.mutedInk)
                    SportMenu(selection: $model.sport)
                    PaperField(label: "Vereniging (mag leeg blijven)", text: $model.club)
                    PaperField(label: "Seizoen", text: $model.season)
                }
                Receipt {
                    ReceiptTitle(text: "Markeerstift")
                    HighlighterPicker(selection: $model.color)
                    if !model.name.isEmpty {
                        Perforation()
                        GroupNameText(name: model.name, color: model.color)
                    }
                }
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
            }
            .navigationTitle("Nieuw team")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleren") { dismiss() }
                        .font(.buttonLabel)
                }
            }
            .safeAreaInset(edge: .bottom) {
                BottomBar {
                    Button {
                        Task {
                            if let group = await model.create() {
                                onCreated(group)
                            }
                        }
                    } label: {
                        BusyLabel(title: "Maak team", isBusy: model.isSaving)
                    }
                    .buttonStyle(PaperButtonStyle(kind: .primary))
                    .disabled(!model.canSave)
                }
            }
        }
    }
}
