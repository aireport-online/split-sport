import SplitKit
import SwiftUI

/// "Inloggen" / "Account maken".
@MainActor
struct AuthView: View {
    let session: AppSession
    @State private var model: AuthViewModel

    init(session: AppSession) {
        self.session = session
        _model = State(initialValue: AuthViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            Form {
                modeSection
                if let notice = session.notice {
                    Section {
                        Label(notice, systemImage: "info.circle")
                            .font(.callout)
                    }
                    .listRowBackground(Theme.surface)
                }
                credentialsSection
                serverSection
                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }
                submitSection
            }
            .themedList()
            .navigationTitle("Split")
        }
    }

    private var modeSection: some View {
        Section {
            Picker("Wat wil je doen?", selection: $model.mode) {
                ForEach(AuthViewModel.Mode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    private var credentialsSection: some View {
        Section {
            if model.mode == .register {
                TextField("Naam", text: $model.name)
                    .textContentType(.name)
            }
            TextField("E-mailadres", text: $model.email)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            SecureField("Wachtwoord", text: $model.password)
                .textContentType(model.mode == .register ? .newPassword : .password)
        } footer: {
            if model.mode == .register {
                Text("Kies een wachtwoord van minstens 8 tekens.")
            }
        }
        .listRowBackground(Theme.surface)
    }

    /// The "Server" field behind "Geavanceerd", to point the app at another server.
    private var serverSection: some View {
        Section {
            DisclosureGroup("Geavanceerd") {
                TextField("Server", text: $model.serverAddress)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        }
        .listRowBackground(Theme.surface)
    }

    private var submitSection: some View {
        Section {
            Button {
                Task { await model.submit() }
            } label: {
                HStack {
                    Text(model.mode.title)
                        .frame(maxWidth: .infinity)
                    if model.isSubmitting {
                        ProgressView()
                    }
                }
            }
            .disabled(!model.canSubmit)
        }
        .listRowBackground(Theme.surface)
    }
}
