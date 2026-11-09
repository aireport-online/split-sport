import SwiftUI

/// A text field for an amount of money: decimal keypad, right-aligned.
@MainActor
struct AmountField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        TextField("0,00", text: $text)
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.trailing)
            .monospacedDigit()
            .accessibilityLabel(label)
    }
}
