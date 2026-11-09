import SplitKit
import SwiftUI

/// A group (rounded tile) or a person (circle) drawn as initials on a pale tint.
@MainActor
struct InitialsBadge: View {
    enum Outline {
        case tile, circle
    }

    let initials: String
    let color: GroupColor
    let outline: Outline
    @ScaledMetric private var side: CGFloat

    init(initials: String, color: GroupColor, outline: Outline, side: CGFloat = 40) {
        self.initials = initials
        self.color = color
        self.outline = outline
        _side = ScaledMetric(wrappedValue: side)
    }

    var body: some View {
        Text(initials)
            .font(.system(size: side * 0.4, weight: .semibold))
            .foregroundStyle(Color(color.tint.ink))
            .frame(width: side, height: side)
            .background(Color(color.tint.background))
            .clipShape(RoundedRectangle(cornerRadius: outline == .circle ? side / 2 : side * 0.28, style: .continuous))
            .accessibilityHidden(true)
    }
}
