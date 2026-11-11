package online.aireport.split.ui.expense

import java.time.LocalDate
import online.aireport.split.core.SplitMode
import online.aireport.split.core.formatAmountInput
import online.aireport.split.core.parseIsoDate
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.ReceiptScan
import online.aireport.split.data.model.toDomain

/**
 * A new expense: paid by you today, split equally over the team members. The Teamkas is in every
 * list too but starts out of the split: unticked, without shares.
 */
fun newExpenseDraft(group: GroupDetail, today: LocalDate): ExpenseDraft {
    val playerIds = group.players.map { it.id }
    return ExpenseDraft(
        date = today,
        paidById = group.myMemberId,
        equalMemberIds = playerIds.toSet(),
        weights = playerIds.associateWith { 1 },
    )
}

/**
 * The form for an existing expense. The split is rebuilt from what the server stored, as
 * `docs/api.md` describes; the other modes start from their defaults.
 */
fun expenseDraft(expense: Expense, group: GroupDetail, today: LocalDate): ExpenseDraft {
    val base = newExpenseDraft(group, today).copy(
        description = expense.description,
        amountText = formatAmountInput(expense.amountCents),
        date = parseIsoDate(expense.date) ?: today,
        paidById = expense.paidById,
        mode = expense.splitMode.toDomain(),
    )
    return when (base.mode) {
        SplitMode.EQUAL -> base.copy(equalMemberIds = expense.shares.map { it.memberId }.toSet())
        SplitMode.EXACT -> base.copy(
            exactAmounts = expense.shares.associate { it.memberId to formatAmountInput(it.amountCents) },
        )
        SplitMode.SHARES -> base.copy(weights = expense.shares.associate { it.memberId to (it.weight ?: 0) })
        SplitMode.ITEMS -> base.copy(
            items = expense.items.mapIndexed { index, item ->
                ItemDraft(
                    key = index + 1L,
                    name = item.name,
                    amountText = formatAmountInput(item.amountCents),
                    memberIds = item.memberIds.toSet(),
                )
            },
        )
    }
}

/**
 * Fills the form for a ride: the cost and, when nothing was typed yet, [description]
 * ("Rit (84 km)").
 */
fun ExpenseDraft.withTrip(costCents: Long, description: String): ExpenseDraft = copy(
    amountText = formatAmountInput(costCents),
    description = this.description.ifBlank { description },
)

/**
 * Fills the form from a scanned receipt: the lines (shared by [memberIds] until someone unticks),
 * the total, the date, and the merchant as description when none was typed yet.
 */
fun ExpenseDraft.withReceipt(scan: ReceiptScan, memberIds: Collection<String>): ExpenseDraft {
    // Discount lines have a negative amount; they stay in the total and are spread in proportion.
    val lines = scan.items.filter { it.amountCents > 0 }
    return copy(
        mode = SplitMode.ITEMS,
        items = lines.mapIndexed { index, line ->
            ItemDraft(
                key = index + 1L,
                name = line.name,
                amountText = formatAmountInput(line.amountCents),
                memberIds = memberIds.toSet(),
            )
        },
        amountText = scan.totalCents?.takeIf { it > 0 }?.let(::formatAmountInput) ?: amountText,
        date = scan.date?.let(::parseIsoDate) ?: date,
        description = description.ifBlank { scan.merchant.orEmpty() },
    )
}
