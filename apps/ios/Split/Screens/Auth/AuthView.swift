import SplitKit
import SwiftUI

/// "Inloggen" / "Account maken".
@MainActor
struct AuthView: View {
    let session: AppSession
    @State private var model: AuthViewModel
    /// Whether the "Server" field behind "Geavanceerd" is shown.
    @State private var showsServer = false

    init(session: AppSession) {
        self.session = session
        _model = State(initialValue: AuthViewModel(session: session))
    }

    private var isRegistering: Bool {
        model.mode == .register
    }

    var body: some View {
        Desk {
            Wordmark()
                .frame(maxWidth: .infinity)
                .padding(.top, 32)
                .padding(.bottom, 16)

            if let notice = session.notice {
                Text(notice)
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
            }

            fields

            if let message = model.errorMessage {
                ErrorBanner(message: message)
            }

            Button {
                Task { await model.submit() }
            } label: {
                BusyLabel(title: model.mode.title, isBusy: model.isSubmitting)
            }
            .buttonStyle(PaperButtonStyle(kind: .primary))
            .disabled(!model.canSubmit)

            modeSwitch
        }
    }

    private var fields: some View {
        Receipt {
            if isRegistering {
                PaperField(label: "Naam", text: $model.name, kind: .name)
            }
            PaperField(label: "E-mailadres", text: $model.email, kind: .email)
            PaperField(label: "Wachtwoord", text: $model.password, kind: isRegistering ? .newPassword : .password)
            if isRegistering {
                Text("Kies een wachtwoord van minstens 8 tekens.")
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
            }
            if showsServer {
                PaperField(label: "Server", text: $model.serverAddress, kind: .url)
            }
        }
    }

    /// "Nog geen account? Account maken", and the way to the "Server" field.
    private var modeSwitch: some View {
        VStack(spacing: 4) {
            HStack(spacing: 8) {
                Text(isRegistering ? "Al een account?" : "Nog geen account?")
                LinkButton(title: isRegistering ? "Inloggen" : "Account maken") {
                    model.mode = isRegistering ? .login : .register
                    model.errorMessage = nil
                }
            }
            if !showsServer {
                LinkButton(title: "Geavanceerd") {
                    showsServer = true
                }
                .font(.tillSmall)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
