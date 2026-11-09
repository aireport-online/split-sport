import SplitKit
import SwiftUI

/// "Per regel": the lines of the receipt, each with a name, an amount and the
/// members as toggles.
@MainActor
struct ItemsSplitSection: View {
    @Bindable var model: ExpenseEditorViewModel

    var body: some View {
        Section {
            ForEach($model.items) { $item in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        TextField("Wat was het?", text: $item.name)
                        AmountField(label: "Bedrag van \(item.name)", text: $item.amountText)
                            .frame(maxWidth: 96)
                        Button {
                            model.removeItem(id: item.id)
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Verwijder regel \(item.name)")
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(model.members.enumerated()), id: \.element.id) { index, member in
                                MemberChip(
                                    name: member.name,
                                    color: GroupColor.palette(at: index),
                                    isSelected: item.memberIds.contains(member.id)
                                ) {
                                    model.toggleMember(member.id, inItem: item.id)
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 4)
            }
            Button {
                model.addItem()
            } label: {
                Label("Regel toevoegen", systemImage: "plus")
            }
        } footer: {
            if let difference = model.itemsDifferenceText {
                Text(difference)
            }
        }
        .listRowBackground(Theme.surface)
    }
}

/// A member as a toggle on a receipt line: tinted when they share the line.
@MainActor
private struct MemberChip: View {
    let name: String
    let color: GroupColor
    let isSelected: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 4) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .accessibilityHidden(true)
                Text(name)
            }
            .font(.subheadline)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .foregroundStyle(isSelected ? Color(color.tint.ink) : Theme.mutedText)
            .background(isSelected ? Color(color.tint.background) : Theme.ground)
            .clipShape(RoundedRectangle(cornerRadius: Theme.controlRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.controlRadius, style: .continuous)
                    .strokeBorder(Theme.border, lineWidth: isSelected ? 0 : 1)
            }
        }
        .buttonStyle(.borderless)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
