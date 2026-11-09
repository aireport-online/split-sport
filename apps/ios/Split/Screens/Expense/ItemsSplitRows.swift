import SplitKit
import SwiftUI

/// "Per regel": the lines of the receipt. Each line is swiped with the colours
/// of the people who had it; the chips below it say who.
@MainActor
struct ItemsSplitRows: View {
    @Bindable var model: ExpenseEditorViewModel

    var body: some View {
        ForEach($model.items) { $item in
            VStack(alignment: .leading, spacing: 8) {
                line(name: $item.name, amountText: $item.amountText, item: item)
                chips(for: item)
                Perforation()
            }
        }
        Button {
            model.addItem()
        } label: {
            Text("Regel toevoegen")
        }
        .buttonStyle(PaperButtonStyle(isCompact: true))
        if let difference = model.itemsDifferenceText {
            Text(difference)
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
        }
    }

    private func line(
        name: Binding<String>, amountText: Binding<String>, item: ExpenseEditorViewModel.ItemDraft
    ) -> some View {
        let colors = model.colors(of: item)
        return HStack(alignment: .firstTextBaseline, spacing: 10) {
            TextField("Wat was het?", text: name)
                // On a highlighter the text is always the dark ink, also in dark mode.
                .foregroundStyle(colors.isEmpty ? Theme.ink : Theme.highlighterInk)
                .padding(.horizontal, 6)
                .frame(minHeight: 28)
                .background {
                    LineSwipe(colors: colors)
                }
                .accessibilityLabel("Omschrijving van de regel")
            AmountField(label: "Bedrag van \(item.name)", text: amountText)
            Button {
                model.removeItem(id: item.id)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Verwijder regel \(item.name)")
        }
    }

    private func chips(for item: ExpenseEditorViewModel.ItemDraft) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(model.members.indices, id: \.self) { index in
                    let member = model.members[index]
                    PersonChip(
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
}
