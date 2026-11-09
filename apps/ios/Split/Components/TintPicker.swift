import SplitKit
import SwiftUI

/// The eight group colours as a row of swatches.
@MainActor
struct TintPicker: View {
    @Binding var selection: GroupColor

    private let columns = [GridItem(.adaptive(minimum: 44), spacing: 12)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(GroupColor.allCases) { color in
                Button {
                    selection = color
                } label: {
                    Circle()
                        .fill(Color(color.tint.background))
                        .frame(width: 40, height: 40)
                        .overlay {
                            Circle().strokeBorder(Color(color.tint.ink), lineWidth: color == selection ? 3 : 1)
                        }
                        .overlay {
                            if color == selection {
                                Image(systemName: "checkmark")
                                    .font(.footnote.weight(.bold))
                                    .foregroundStyle(Color(color.tint.ink))
                            }
                        }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(color.title)
                .accessibilityAddTraits(color == selection ? .isSelected : [])
            }
        }
        .padding(.vertical, 4)
    }
}
