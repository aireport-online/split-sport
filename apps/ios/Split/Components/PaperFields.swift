import SplitKit
import SwiftUI

/// A field without a box: a small muted label, the value below it and a dashed
/// perforation underline that turns solid ink while the field has focus.
@MainActor
struct PaperField: View {
    enum Kind {
        case text, name, email, password, newPassword, url, code
    }

    let label: String
    @Binding var text: String
    var kind = Kind.text
    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
                .accessibilityHidden(true)
            input
                .font(.till)
                .focused($isFocused)
                .keyboardType(keyboard)
                .textContentType(contentType)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled(kind != .text)
                .accessibilityLabel(label)
                .frame(minHeight: 28)
            FieldUnderline(isFocused: isFocused)
        }
    }

    @ViewBuilder
    private var input: some View {
        switch kind {
        case .password, .newPassword:
            SecureField("", text: $text)
        case .text, .name, .email, .url, .code:
            TextField("", text: $text)
        }
    }

    private var keyboard: UIKeyboardType {
        switch kind {
        case .email: return .emailAddress
        case .url: return .URL
        case .code: return .asciiCapable
        case .text, .name, .password, .newPassword: return .default
        }
    }

    private var contentType: UITextContentType? {
        switch kind {
        case .name: return .name
        case .email: return .emailAddress
        case .password: return .password
        case .newPassword: return .newPassword
        case .url: return .URL
        case .text, .code: return nil
        }
    }

    private var capitalization: TextInputAutocapitalization {
        switch kind {
        case .text: return .sentences
        case .name: return .words
        case .code: return .characters
        case .email, .password, .newPassword, .url: return .never
        }
    }
}

/// A value that cannot be changed, laid out like a field.
@MainActor
struct ReadOnlyField: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
            Text(value)
                .foregroundStyle(Theme.mutedInk)
                .frame(minHeight: 28)
            Perforation()
        }
        .accessibilityElement(children: .combine)
    }
}

/// A field for an amount of money, with the decimal keypad.
@MainActor
struct AmountField: View {
    enum Style {
        /// The amount of an expense: a label and a large figure behind a euro sign.
        case figure
        /// An amount on a receipt line: narrow and right-aligned.
        case line
    }

    let label: String
    @Binding var text: String
    var style = Style.line
    /// What the empty field shows.
    var prompt = "0,00"
    @FocusState private var isFocused: Bool

    var body: some View {
        switch style {
        case .figure:
            VStack(alignment: .leading, spacing: 4) {
                Text(label)
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
                    .accessibilityHidden(true)
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text("€")
                        .accessibilityHidden(true)
                    field
                }
                .font(.tillFigure)
                FieldUnderline(isFocused: isFocused)
            }
        case .line:
            VStack(spacing: 4) {
                field
                    .multilineTextAlignment(.trailing)
                FieldUnderline(isFocused: isFocused)
            }
            .frame(width: 96)
        }
    }

    private var field: some View {
        TextField(prompt, text: $text)
            .keyboardType(.decimalPad)
            .focused($isFocused)
            .accessibilityLabel(label)
    }
}

/// The line under a field: a perforation, or solid ink while the field has focus.
@MainActor
struct FieldUnderline: View {
    let isFocused: Bool

    var body: some View {
        if isFocused {
            Rectangle()
                .fill(Theme.ink)
                .frame(height: 1.5)
                .accessibilityHidden(true)
        } else {
            Perforation()
        }
    }
}

/// Chooses one member of the team (or the Teamkas); the choice is shown the way
/// that member is printed everywhere.
@MainActor
struct MemberMenu: View {
    let label: String
    let members: [Party]
    @Binding var selection: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(label)
                .accessibilityHidden(true)
            Menu {
                Picker(label, selection: $selection) {
                    ForEach(members) { member in
                        Text(member.name).tag(member.id)
                    }
                }
            } label: {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    if let selected {
                        PartyName(party: selected)
                    } else {
                        Text("Kies")
                            .foregroundStyle(Theme.mutedInk)
                    }
                    MenuChevron()
                }
            }
            .accessibilityLabel(label)
            .accessibilityValue(selected?.name ?? "")
            Spacer(minLength: 0)
        }
    }

    private var selected: Party? {
        members.first { $0.id == selection }
    }
}

/// Chooses the sport of a team.
@MainActor
struct SportMenu: View {
    @Binding var selection: Sport

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text("Sport")
                .accessibilityHidden(true)
            Menu {
                Picker("Sport", selection: $selection) {
                    ForEach(Sport.allCases) { sport in
                        Text(sport.label).tag(sport)
                    }
                }
            } label: {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(selection.label)
                        .font(.tillBold)
                        .foregroundStyle(Theme.ink)
                    MenuChevron()
                }
            }
            .accessibilityLabel("Sport")
            .accessibilityValue(selection.label)
            Spacer(minLength: 0)
        }
    }
}

/// The small arrow that says a value opens a menu.
@MainActor
private struct MenuChevron: View {
    var body: some View {
        Image(systemName: "chevron.down")
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Theme.ink)
            .accessibilityHidden(true)
    }
}

/// A stepper for whole numbers from 0: "− 2 +" with outlined buttons.
@MainActor
struct PaperStepper: View {
    /// Whose number this is, for VoiceOver.
    let name: String
    @Binding var value: Int

    var body: some View {
        HStack(spacing: 8) {
            Button {
                value -= 1
            } label: {
                Image(systemName: "minus")
            }
            .disabled(value <= 0)
            .accessibilityLabel("Minder aandelen voor \(name)")

            Text("\(value)")
                .frame(minWidth: 24)
                .accessibilityLabel("\(value) aandelen voor \(name)")

            Button {
                value += 1
            } label: {
                Image(systemName: "plus")
            }
            .accessibilityLabel("Meer aandelen voor \(name)")
        }
        .buttonStyle(PaperButtonStyle(isCompact: true))
    }
}
