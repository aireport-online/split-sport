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
            Form {
                Section {
                    HStack(spacing: 12) {
                        InitialsBadge(initials: Initials.of(model.name), color: model.color, outline: .tile, side: 44)
                        TextField("Naam, bijvoorbeeld Weekendje Texel", text: $model.name)
                    }
                }
                .listRowBackground(Theme.surface)

                Section("Kleur") {
                    TintPicker(selection: $model.color)
                }
                .listRowBackground(Theme.surface)

                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .themedList()
            .navigationTitle("Nieuwe groep")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleer") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Maak groep") {
                        Task {
                            if let group = await model.create() {
                                onCreated(group)
                            }
                        }
                    }
                    .disabled(!model.canSave)
                }
            }
        }
    }
}
