package online.aireport.split.data

import java.time.LocalDate
import online.aireport.split.core.Split
import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseRequest
import online.aireport.split.data.model.SettlementRequest
import online.aireport.split.data.model.toDto

/** What is needed to create or replace an expense. */
data class ExpenseInput(
    val description: String,
    val amountCents: Long,
    val date: LocalDate,
    val paidById: String,
    val split: Split,
)

/** Expenses and settlements of a group. */
class ExpenseRepository(
    private val gateway: ApiGateway,
    private val changes: GroupChanges,
) {
    /** Newest date first, settlements included. */
    suspend fun expenses(groupId: String): List<Expense> = gateway.call { expenses(groupId) }.expenses

    suspend fun expense(groupId: String, expenseId: String): Expense = gateway.call { expense(groupId, expenseId) }

    suspend fun createExpense(groupId: String, input: ExpenseInput): Expense =
        gateway.call { createExpense(groupId, input.toRequest()) }.also { changes.notifyChanged() }

    suspend fun updateExpense(groupId: String, expenseId: String, input: ExpenseInput): Expense =
        gateway.call { updateExpense(groupId, expenseId, input.toRequest()) }.also { changes.notifyChanged() }

    /** Deletes an expense or a settlement. */
    suspend fun deleteExpense(groupId: String, expenseId: String) {
        gateway.call { deleteExpense(groupId, expenseId) }
        changes.notifyChanged()
    }

    /** Records that [fromMemberId] paid [toMemberId] back. */
    suspend fun settle(
        groupId: String,
        fromMemberId: String,
        toMemberId: String,
        amountCents: Long,
        date: LocalDate,
    ): Expense =
        gateway.call {
            createSettlement(groupId, SettlementRequest(fromMemberId, toMemberId, amountCents, date.toString()))
        }.also { changes.notifyChanged() }

    private fun ExpenseInput.toRequest() = ExpenseRequest(
        description = description.trim(),
        amountCents = amountCents,
        date = date.toString(),
        paidById = paidById,
        split = split.toDto(),
    )
}
