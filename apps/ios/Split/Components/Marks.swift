import SwiftUI

/// "Split", torn in two: a dashed perforation in ink runs between "Sp" and "lit".
/// Split Sport carries the stamp "SPORT" after it.
@MainActor
struct Wordmark: View {
    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 10) {
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
            Stamp(kind: .sport)
        }
        .foregroundStyle(Theme.ink)
        .fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Split Sport")
        .accessibilityAddTraits(.isHeader)
    }
}

/// A rubber stamp: Barlow Condensed, uppercase, a 2 pt outline in the text colour, rotated −8°.
@MainActor
struct Stamp: View {
    enum Kind {
        /// "BETAALD" on a payment, in stamp red.
        case paid
        /// "BOETE" on a fine, in stamp red.
        case fine
        /// "INLEG" on a contribution, in ink.
        case contribution
        /// "KONING" on the leader of the fine ranking, in stamp red.
        case king
        /// "SPORT" next to the wordmark, in ink.
        case sport
    }

    let kind: Kind

    var body: some View {
        Text(text)
            .font(.chipLabel)
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 1)
            .overlay {
                RoundedRectangle(cornerRadius: 3).strokeBorder(color, lineWidth: 2)
            }
            .rotationEffect(.degrees(-8))
            .fixedSize()
            .accessibilityLabel(spokenText)
    }

    private var text: String {
        switch kind {
        case .paid: return "BETAALD"
        case .fine: return "BOETE"
        case .contribution: return "INLEG"
        case .king: return "KONING"
        case .sport: return "SPORT"
        }
    }

    /// The same word in normal case, so VoiceOver reads it as a word.
    private var spokenText: String {
        switch kind {
        case .paid: return "Betaald"
        case .fine: return "Boete"
        case .contribution: return "Inleg"
        case .king: return "Boetekoning"
        case .sport: return "Sport"
        }
    }

    private var color: Color {
        switch kind {
        case .paid, .fine, .king: return Theme.stamp
        case .contribution, .sport: return Theme.ink
        }
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
