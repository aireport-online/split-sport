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

/// A group's name, underlined with a thick stroke of the group's highlighter
/// that runs behind the foot of the letters: from a little above the baseline to
/// just below it, like the web app's underline.
@MainActor
struct GroupNameText: View {
    let name: String
    let color: GroupColor

    var body: some View {
        Text(name)
            .font(.receiptTitle)
            .background {
                HighlighterStroke(variant: name.count, top: 0.66, bottom: 0.96).fill(color.highlighterColor)
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

/// Says who had a receipt line: a filled pill in the person's colour when they
/// did, a dashed outline when they did not.
@MainActor
struct PersonChip: View {
    let name: String
    let color: GroupColor
    let isSelected: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            Text(name)
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
        isSelected ? Theme.highlighterInk : Theme.mutedInk
    }

    private var fillColor: Color {
        isSelected ? color.highlighterColor : Color.clear
    }

    private var outlineColor: Color {
        isSelected ? Color.clear : Theme.perforation
    }
}

/// The eight highlighters to choose a group's colour from.
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
