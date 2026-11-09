import SwiftUI

/// "Split", torn in two: a dashed perforation in ink runs between "Sp" and "lit".
@MainActor
struct Wordmark: View {
    var body: some View {
        HStack(spacing: 7) {
            Text("Sp")
            VerticalLine()
                .stroke(Theme.ink, style: StrokeStyle(lineWidth: 4, dash: [9, 6]))
                .frame(width: 4)
                // The cut extends a little above and below the letters.
                .padding(.vertical, -10)
            Text("lit")
        }
        .font(.wordmark)
        .foregroundStyle(Theme.ink)
        .fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Split")
        .accessibilityAddTraits(.isHeader)
    }
}

/// The rubber stamp on a payment between members.
@MainActor
struct Stamp: View {
    var body: some View {
        Text("BETAALD")
            .font(.chipLabel)
            .foregroundStyle(Theme.stamp)
            .padding(.horizontal, 7)
            .padding(.vertical, 1)
            .overlay {
                RoundedRectangle(cornerRadius: 3).strokeBorder(Theme.stamp, lineWidth: 2)
            }
            .rotationEffect(.degrees(-8))
            .fixedSize()
            .accessibilityLabel("Betaald")
    }
}

/// A 20 pt square with an ink outline; checked, it is filled with ink.
/// Only the picture: the row around it is the button.
@MainActor
struct Checkbox: View {
    let isChecked: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(isChecked ? Theme.ink : Color.clear)
            .frame(width: 20, height: 20)
            .overlay {
                RoundedRectangle(cornerRadius: 2).strokeBorder(Theme.ink, lineWidth: Theme.outline)
            }
            .overlay {
                if isChecked {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Theme.onInk)
                }
            }
            .accessibilityHidden(true)
    }
}
