package online.aireport.split.ui.group

import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind

/**
 * What an expense means for one member: positive when they get money back for it, negative when
 * they owe for it. Null when they are not in it, when it evens out, and for settlements.
 */
fun Expense.effectFor(memberId: String): Long? {
    if (kind == ExpenseKind.SETTLEMENT) return null
    val paid = if (paidById == memberId) amountCents else 0L
    val owed = shares.firstOrNull { it.memberId == memberId }?.amountCents ?: 0L
    return (paid - owed).takeIf { it != 0L }
}
