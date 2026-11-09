import SplitKit
import SwiftUI

/// "Gelijk": tick who takes part; every row shows that person's amount.
@MainActor
struct EqualSplitRows: View {
    let model: ExpenseEditorViewModel

    var body: some View {
        ForEach(model.members.indices, id: \.self) { index in
            let member = model.members[index]
            let isIncluded = model.equalMemberIds.contains(member.id)
            Button {
                model.toggleEqualMember(member.id)
            } label: {
                HStack(spacing: 12) {
                    Highlight(name: member.name, color: GroupColor.palette(at: index))
                    Spacer(minLength: 8)
                    Checkbox(isChecked: isIncluded)
                    AmountColumn(text: model.previewAmount(for: member.id))
                }
                .frame(minHeight: 32)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isIncluded ? .isSelected : [])
        }
    }
}

/// "Bedragen": an amount per person, and how much is still to divide.
@MainActor
struct ExactSplitRows: View {
    let model: ExpenseEditorViewModel

    var body: some View {
        ForEach(model.members.indices, id: \.self) { index in
            let member = model.members[index]
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Highlight(name: member.name, color: GroupColor.palette(at: index))
                Spacer(minLength: 8)
                AmountField(label: "Bedrag voor \(member.name)", text: amountText(member.id))
            }
        }
        if let remainder = model.exactRemainderText {
            Text(remainder)
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
        }
    }

    private func amountText(_ memberId: String) -> Binding<String> {
        Binding(
            get: { model.exactText(for: memberId) },
            set: { model.setExactText($0, for: memberId) }
        )
    }
}

/// "Aandelen": a stepper per person; 0 means they are not in.
@MainActor
struct SharesSplitRows: View {
    let model: ExpenseEditorViewModel

    var body: some View {
        ForEach(model.members.indices, id: \.self) { index in
            let member = model.members[index]
            HStack(spacing: 12) {
                Highlight(name: member.name, color: GroupColor.palette(at: index))
                Spacer(minLength: 8)
                PaperStepper(name: member.name, value: weight(member.id))
                AmountColumn(text: model.previewAmount(for: member.id))
            }
        }
        Text("0 betekent: doet niet mee.")
            .font(.tillSmall)
            .foregroundStyle(Theme.mutedInk)
    }

    private func weight(_ memberId: String) -> Binding<Int> {
        Binding(
            get: { model.weight(for: memberId) },
            set: { model.setWeight($0, for: memberId) }
        )
    }
}

/// A person's amount at the right edge of a row; a dash when they are not in.
@MainActor
private struct AmountColumn: View {
    let text: String?

    var body: some View {
        Text(text ?? "–")
            .foregroundStyle(text == nil ? Theme.mutedInk : Theme.ink)
            .lineLimit(1)
            .frame(minWidth: 64, alignment: .trailing)
    }
}
