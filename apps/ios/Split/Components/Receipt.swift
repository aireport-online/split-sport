import SwiftUI

/// A piece of receipt paper with what is printed on it. No border and no shadow:
/// the only depth is paper on the desk.
@MainActor
struct Receipt<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            content
        }
        .padding(.horizontal, Theme.receiptPadding)
        .padding(.top, Theme.receiptPadding)
        .padding(.bottom, Theme.receiptPadding + Theme.toothHeight)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ReceiptShape().fill(Theme.paper)
        }
    }
}

/// The title printed big at the top of a receipt.
@MainActor
struct ReceiptTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.receiptTitle)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The dashed tear line between two parts of one receipt.
@MainActor
struct Perforation: View {
    var body: some View {
        HorizontalLine()
            .stroke(Theme.perforation, style: Theme.perforationStroke)
            .frame(height: 1.5)
            .accessibilityHidden(true)
    }
}

/// The dotted line between a name and its amount: 2 pt dots, 6 pt apart, on the text baseline.
@MainActor
struct Leader: View {
    var body: some View {
        HorizontalLine()
            .stroke(Theme.perforation, style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [0.01, 6]))
            .frame(height: 2)
            .frame(minWidth: 16)
            .accessibilityHidden(true)
    }
}

/// A receipt line: "name ........ amount".
@MainActor
struct LeaderRow<Label: View>: View {
    private let amount: String
    private let label: Label

    init(amount: String, @ViewBuilder label: () -> Label) {
        self.amount = amount
        self.label = label()
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            label
            Leader()
            Text(amount)
                .fixedSize()
        }
    }
}

/// A small note with a dashed outline, such as "geen account".
@MainActor
struct DashedPill: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.tillSmall)
            .foregroundStyle(Theme.mutedInk)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .overlay {
                RoundedRectangle(cornerRadius: 3)
                    .strokeBorder(Theme.perforation, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            }
    }
}
