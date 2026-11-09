import SwiftUI

/// A piece of receipt paper: square but for 2 pt top corners, with a zigzag tear
/// along the bottom edge. The teeth are stretched slightly so a whole number fits.
struct ReceiptShape: Shape {
    func path(in rect: CGRect) -> Path {
        let corner: CGFloat = 2
        let teeth = max(1, Int((rect.width / Theme.toothWidth).rounded()))
        let toothWidth = rect.width / CGFloat(teeth)
        let tearY = rect.maxY - Theme.toothHeight

        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + corner))
        path.addQuadCurve(
            to: CGPoint(x: rect.minX + corner, y: rect.minY),
            control: CGPoint(x: rect.minX, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX - corner, y: rect.minY))
        path.addQuadCurve(
            to: CGPoint(x: rect.maxX, y: rect.minY + corner),
            control: CGPoint(x: rect.maxX, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: tearY))
        for tooth in 0..<teeth {
            let right = rect.maxX - CGFloat(tooth) * toothWidth
            path.addLine(to: CGPoint(x: right - toothWidth / 2, y: rect.maxY))
            path.addLine(to: CGPoint(x: right - toothWidth, y: tearY))
        }
        path.closeSubpath()
        return path
    }
}

/// A horizontal line through the middle of its frame; stroked dashed it is a
/// perforation, stroked with round dots a leader.
struct HorizontalLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        return path
    }
}

/// A vertical line through the middle of its frame: the cut in the wordmark.
struct VerticalLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        return path
    }
}

/// One swipe of a highlighter: a bar tilted about −1° with slightly uneven ends.
struct HighlighterStroke: Shape {
    /// Picks between two end shapes so strokes below each other differ. The shape
    /// only depends on this value and the frame, so it never changes between redraws.
    var variant = 0
    /// The part of the frame's height the stroke covers, from the top (0) to the bottom (1).
    var top: CGFloat = 0.08
    var bottom: CGFloat = 0.96

    func path(in rect: CGRect) -> Path {
        let upper = rect.minY + rect.height * top
        let lower = rect.minY + rect.height * bottom
        let middle = (upper + lower) / 2
        // tan(1°) ≈ 0.0175: the right end sits that much higher per point of width.
        let rise = rect.width * 0.0175 / 2
        let near: CGFloat = variant % 2 == 0 ? 2.5 : 1
        let far: CGFloat = 3.5 - near

        var path = Path()
        path.move(to: CGPoint(x: rect.minX + near, y: upper + rise))
        path.addLine(to: CGPoint(x: rect.maxX - far, y: upper - rise))
        path.addLine(to: CGPoint(x: rect.maxX, y: middle - rise))
        path.addLine(to: CGPoint(x: rect.maxX - near, y: lower - rise))
        path.addLine(to: CGPoint(x: rect.minX + far, y: lower + rise))
        path.addLine(to: CGPoint(x: rect.minX, y: middle + rise))
        path.closeSubpath()
        return path
    }
}
