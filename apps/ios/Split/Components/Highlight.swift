import SplitKit
import SwiftUI

/// A person: never an avatar, but their name with their highlighter swiped behind it.
@MainActor
struct Highlight: View {
    let name: String
    let color: GroupColor

    var body: some View {
        Text(name)
            .foregroundStyle(Theme.highlighterInk)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .background {
                HighlighterStroke(variant: name.count).fill(color.highlighterColor)
            }
    }
}

/// The Teamkas: not a person, so no highlighter. Its name is printed as a label,
/// on-ink text on an ink fill with the same slightly tilted stroke as a highlighter.
@MainActor
struct PotLabel: View {
    let name: String

    var body: some View {
        Text(name)
            .foregroundStyle(Theme.onInk)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .background {
                HighlighterStroke(variant: name.count).fill(Theme.ink)
            }
    }
}

/// A member of the team as it is printed everywhere: a person highlighted, the pot as a label.
@MainActor
struct PartyName: View {
    let party: Party

    var body: some View {
        if let color = party.highlighter {
            Highlight(name: party.name, color: color)
        } else {
            PotLabel(name: party.name)
        }
    }
}

/// A team's name, underlined with a thick stroke of the team's highlighter.
/// On light paper the stroke runs behind the foot of the letters, like the web
/// app's underline; on dark paper light letters would be unreadable on it, so
/// there it sits just below them.
@MainActor
struct GroupNameText: View {
    let name: String
    let color: GroupColor

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let onDarkPaper = colorScheme == .dark
        Text(name)
            .font(.receiptTitle)
            .background {
                HighlighterStroke(
                    variant: name.count,
                    top: onDarkPaper ? 0.9 : 0.66,
                    bottom: onDarkPaper ? 1.04 : 0.96
                )
                .fill(color.highlighterColor)
            }
    }
}

/// The swipe behind a receipt line: the colours of the people who had it, in
/// equal parts from left to right. Nothing when nobody is assigned.
@MainActor
struct LineSwipe: View {
    let colors: [GroupColor]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(colors.indices, id: \.self) { index in
                Rectangle().fill(colors[index].highlighterColor)
            }
        }
        .clipShape(HighlighterStroke(variant: colors.count))
        .accessibilityHidden(true)
    }
}

/// Says who takes part (in a receipt line, a fine, a contribution): a filled
/// pill in the person's colour when they do, a dashed outline when they do not.
/// The Teamkas, which has no colour, is filled with ink.
@MainActor
struct PartyChip: View {
    let party: Party
    let isSelected: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            Text(party.name)
                .font(.chipLabel)
                .lineLimit(1)
                .foregroundStyle(textColor)
                .padding(.horizontal, 12)
                .frame(minHeight: 32)
                .background {
                    Capsule().fill(fillColor)
                }
                .overlay {
                    Capsule().strokeBorder(outlineColor, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                }
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var textColor: Color {
        guard isSelected else { return Theme.mutedInk }
        return party.isPot ? Theme.onInk : Theme.highlighterInk
    }

    private var fillColor: Color {
        guard isSelected else { return Color.clear }
        return party.highlighter?.highlighterColor ?? Theme.ink
    }

    private var outlineColor: Color {
        isSelected ? Color.clear : Theme.perforation
    }
}

/// The eight highlighters to choose a team's colour from.
@MainActor
struct HighlighterPicker: View {
    @Binding var selection: GroupColor

    private let columns = [GridItem(.adaptive(minimum: 64), spacing: 8)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(GroupColor.allCases) { color in
                Button {
                    selection = color
                } label: {
                    HighlighterStroke(variant: color.title.count)
                        .fill(color.highlighterColor)
                        .frame(height: 22)
                        .padding(8)
                        .overlay {
                            RoundedRectangle(cornerRadius: Theme.radius)
                                .strokeBorder(color == selection ? Theme.ink : Color.clear, lineWidth: Theme.outline)
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(color.title)
                .accessibilityAddTraits(color == selection ? .isSelected : [])
            }
        }
    }
}
