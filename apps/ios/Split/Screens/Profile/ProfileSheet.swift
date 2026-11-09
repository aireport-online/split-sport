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
            Desk {
                Receipt {
                    PaperField(label: "Naam", text: $model.name, kind: .name)
                    ReadOnlyField(label: "E-mail", value: model.email)
                    PaperField(label: "IBAN", text: $model.iban, kind: .code)
                    Text("Zodat anderen weten waar ze kunnen betalen.")
                        .font(.tillSmall)
                        .foregroundStyle(Theme.mutedInk)
                }
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                Receipt {
                    Button("Uitloggen") {
                        dismiss()
                        Task { await model.signOut() }
                    }
                    .buttonStyle(PaperButtonStyle())
                    LinkButton(title: "Account verwijderen", isDestructive: true) {
                        isConfirmingDelete = true
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(model.isWorking)
            }
            .navigationTitle("Profiel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Sluiten") { dismiss() }
                        .font(.buttonLabel)
                }
            }
            .safeAreaInset(edge: .bottom) {
                BottomBar {
                    Button {
                        Task { await model.save() }
                    } label: {
                        BusyLabel(title: "Opslaan", isBusy: model.isWorking)
                    }
                    .buttonStyle(PaperButtonStyle(kind: .primary))
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
                Button("Annuleren", role: .cancel) {}
            } message: {
                Text("Je account en je aanmelding bij al je groepen worden verwijderd. Dit kan niet ongedaan worden gemaakt.")
            }
        }
    }
}
