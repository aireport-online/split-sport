import SwiftUI

/// The buttons of the house style, in Barlow Condensed with a 4 pt radius.
struct PaperButtonStyle: ButtonStyle {
    enum Kind {
        /// Ink fill with the on-ink label.
        case primary
        /// A 1.5 pt ink outline on the paper.
        case secondary
        /// Stamp red label and outline.
        case destructive
    }

    var kind = Kind.secondary
    /// A small button (36 pt high, smaller label); otherwise 48 pt high.
    var isCompact = false
    /// Whether the button is as wide as it can be. A compact button hugs its label unless told otherwise.
    var fillsWidth: Bool?

    func makeBody(configuration: Configuration) -> some View {
        PaperButtonBody(
            kind: kind, isCompact: isCompact, fillsWidth: fillsWidth ?? !isCompact,
            label: configuration.label, isPressed: configuration.isPressed
        )
    }
}

/// A view of its own, so it can read whether the button is enabled. Not marked
/// `@MainActor` like the other views: `makeBody` above creates it, and that is not
/// main-actor isolated in every SDK.
private struct PaperButtonBody: View {
    let kind: PaperButtonStyle.Kind
    let isCompact: Bool
    let fillsWidth: Bool
    let label: ButtonStyleConfiguration.Label
    let isPressed: Bool
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        label
            .font(font)
            .lineLimit(1)
            .foregroundStyle(foreground)
            .tint(foreground)
            .padding(.horizontal, horizontalPadding)
            .frame(maxWidth: maxWidth, minHeight: minHeight)
            .background {
                RoundedRectangle(cornerRadius: Theme.radius).fill(fill)
            }
            .overlay {
                RoundedRectangle(cornerRadius: Theme.radius).strokeBorder(outline, lineWidth: Theme.outline)
            }
            .contentShape(Rectangle())
            .opacity(opacity)
    }

    private var font: Font {
        isCompact ? .chipLabel : .buttonLabel
    }

    private var horizontalPadding: CGFloat {
        isCompact ? 12 : 18
    }

    private var maxWidth: CGFloat? {
        fillsWidth ? .infinity : nil
    }

    private var minHeight: CGFloat {
        isCompact ? 36 : Theme.buttonHeight
    }

    private var foreground: Color {
        switch kind {
        case .primary: return Theme.onInk
        case .secondary: return Theme.ink
        case .destructive: return Theme.stamp
        }
    }

    private var fill: Color {
        kind == .primary ? Theme.ink : Color.clear
    }

    private var outline: Color {
        kind == .destructive ? Theme.stamp : Theme.ink
    }

    private var opacity: Double {
        if !isEnabled { return 0.45 }
        return isPressed ? 0.7 : 1
    }
}

/// The label of a button that shows a spinner while its action runs.
@MainActor
struct BusyLabel: View {
    let title: String
    let isBusy: Bool

    var body: some View {
        if isBusy {
            ProgressView()
                .accessibilityLabel("Bezig")
        } else {
            Text(title)
        }
    }
}

/// A text link: underlined, in ink or (for destructive actions) stamp red.
@MainActor
struct LinkButton: View {
    let title: String
    var isDestructive = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .underline()
                .foregroundStyle(isDestructive ? Theme.stamp : Theme.ink)
                .frame(minHeight: 32)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
