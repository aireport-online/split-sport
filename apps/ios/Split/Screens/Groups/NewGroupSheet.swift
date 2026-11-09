import SplitKit
import SwiftUI

/// "Nieuwe groep".
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
                    Text("Bijvoorbeeld je huis, een vakantie of een etentje.")
                        .font(.tillSmall)
                        .foregroundStyle(Theme.mutedInk)
                }
                Receipt {
                    ReceiptTitle(text: "Kleur")
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
            .navigationTitle("Nieuwe groep")
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
                        BusyLabel(title: "Maak groep", isBusy: model.isSaving)
                    }
                    .buttonStyle(PaperButtonStyle(kind: .primary))
                    .disabled(!model.canSave)
                }
            }
        }
    }
}
