import SwiftUI

/// Tabs and segmented controls: a 1.5 pt ink outline with the selected segment
/// filled with ink.
@MainActor
struct SegmentedControl<Value: Hashable>: View {
    /// What the control chooses between, for VoiceOver.
    let label: String
    let options: [(value: Value, title: String)]
    @Binding var selection: Value

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options.indices, id: \.self) { index in
                if index > 0 {
                    Rectangle()
                        .fill(Theme.ink)
                        .frame(width: Theme.outline)
                }
                segment(value: options[index].value, title: options[index].title)
            }
        }
        // As high as the labels need, so the separators do not stretch.
        .fixedSize(horizontal: false, vertical: true)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.radius).strokeBorder(Theme.ink, lineWidth: Theme.outline)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
    }

    private func segment(value: Value, title: String) -> some View {
        let isSelected = value == selection
        return Button {
            selection = value
        } label: {
            Text(title)
                .font(.tabLabel)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .foregroundStyle(isSelected ? Theme.onInk : Theme.ink)
                .padding(.horizontal, 4)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(isSelected ? Theme.ink : Color.clear)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
