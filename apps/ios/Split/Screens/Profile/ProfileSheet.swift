import SplitKit
import SwiftUI

/// "Profiel": name, IBAN, logging out and deleting the account.
@MainActor
struct ProfileSheet: View {
    @State private var model: ProfileViewModel
    @State private var isConfirmingDelete = false
    @Environment(\.dismiss) private var dismiss

    init(session: AppSession) {
        _model = State(initialValue: ProfileViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Naam") {
                        TextField("Naam", text: $model.name)
                            .textContentType(.name)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("E-mail", value: model.email)
                }
                .listRowBackground(Theme.surface)

                Section {
                    TextField("IBAN", text: $model.iban)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .font(.body.monospaced())
                } header: {
                    Text("IBAN")
                } footer: {
                    Text("Zodat anderen weten waar ze kunnen betalen.")
                }
                .listRowBackground(Theme.surface)

                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }

                Section {
                    Button("Uitloggen") {
                        dismiss()
                        Task { await model.signOut() }
                    }
                    Button("Account verwijderen", role: .destructive) {
                        isConfirmingDelete = true
                    }
                }
                .listRowBackground(Theme.surface)
                .disabled(model.isWorking)
            }
            .themedList()
            .navigationTitle("Profiel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Sluit") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Bewaar") {
                        Task { await model.save() }
                    }
                    .disabled(!model.canSave)
                }
            }
            .confirmationDialog(
                "Account verwijderen?",
                isPresented: $isConfirmingDelete,
                titleVisibility: .visible
            ) {
                Button("Verwijder mijn account", role: .destructive) {
                    Task { await model.deleteAccount() }
                }
                Button("Annuleer", role: .cancel) {}
            } message: {
                Text("Je account en je aanmelding bij al je groepen worden verwijderd. Dit kan niet ongedaan worden gemaakt.")
            }
        }
    }
}
