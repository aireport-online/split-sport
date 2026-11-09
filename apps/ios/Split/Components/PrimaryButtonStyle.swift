import SwiftUI

/// The primary action: accent ground with the on-accent text colour, which the
/// system's prominent style (always white text) does not give in dark mode.
struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Theme.onAccent)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Theme.accent)
            .clipShape(RoundedRectangle(cornerRadius: Theme.controlRadius, style: .continuous))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
