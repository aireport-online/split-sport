import SplitKit
import SwiftUI

/// "Gelijk": tick who takes part; every row shows that person's amount.
@MainActor
struct EqualSplitSection: View {
    let model: ExpenseEditorViewModel

    var body: some View {
        Section {
            ForEach(model.members) { member in
                Toggle(isOn: isIncluded(member.id)) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(model.memberName(member.id))
                        if let amount = model.previewAmount(for: member.id) {
                            Text(amount)
                                .font(.footnote)
                                .monospacedDigit()
                                .foregroundStyle(Theme.mutedText)
                        }
                    }
                }
            }
        }
        .listRowBackground(Theme.surface)
    }

    private func isIncluded(_ memberId: String) -> Binding<Bool> {
        Binding(
            get: { model.equalMemberIds.contains(memberId) },
            set: { isOn in
                if isOn != model.equalMemberIds.contains(memberId) {
                    model.toggleEqualMember(memberId)
                }
            }
        )
    }
}

/// "Bedragen": an amount per person, and how much is still to divide.
@MainActor
struct ExactSplitSection: View {
    let model: ExpenseEditorViewModel

    var body: some View {
        Section {
            ForEach(model.members) { member in
                LabeledContent(model.memberName(member.id)) {
                    AmountField(label: "Bedrag voor \(member.name)", text: amountText(member.id))
                }
            }
        } footer: {
            if let remainder = model.exactRemainderText {
                Text(remainder)
            }
        }
        .listRowBackground(Theme.surface)
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
struct SharesSplitSection: View {
    let model: ExpenseEditorViewModel

    var body: some View {
        Section {
            ForEach(model.members) { member in
                Stepper(value: weight(member.id), in: 0...99) {
                    HStack {
                        Text(model.memberName(member.id))
                        Spacer(minLength: 8)
                        Text("\(model.weight(for: member.id))×")
                            .monospacedDigit()
                            .foregroundStyle(model.weight(for: member.id) == 0 ? Theme.mutedText : Theme.text)
                    }
                }
                .accessibilityValue("\(model.weight(for: member.id)) aandelen")
            }
        } footer: {
            Text("0 betekent: doet niet mee.")
        }
        .listRowBackground(Theme.surface)
    }

    private func weight(_ memberId: String) -> Binding<Int> {
        Binding(
            get: { model.weight(for: memberId) },
            set: { model.setWeight($0, for: memberId) }
        )
    }
}
