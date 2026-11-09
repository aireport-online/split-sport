package online.aireport.split.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExpenseKind {
    @SerialName("expense") EXPENSE,
    @SerialName("settlement") SETTLEMENT,
}

@Serializable
enum class SplitModeDto {
    @SerialName("equal") EQUAL,
    @SerialName("exact") EXACT,
    @SerialName("shares") SHARES,
    @SerialName("items") ITEMS,
}

@Serializable
data class ExpenseShare(val memberId: String, val amountCents: Long, val weight: Int? = null)

@Serializable
data class ExpenseItem(val name: String, val amountCents: Long, val memberIds: List<String>)

@Serializable
data class Expense(
    val id: String,
    val kind: ExpenseKind,
    val description: String,
    val amountCents: Long,
    /** Member that paid. For a settlement: the member that paid back. */
    val paidById: String,
    /** Calendar date, `YYYY-MM-DD`. */
    val date: String,
    val splitMode: SplitModeDto,
    /** What every participant owes. For a settlement: one share for the receiver. */
    val shares: List<ExpenseShare>,
    /** Receipt lines, only for split mode "items". */
    val items: List<ExpenseItem> = emptyList(),
)

@Serializable
data class ExpenseList(val expenses: List<Expense>)

/**
 * A split as it goes over the wire: `mode` plus the one list that belongs to it. The other lists
 * stay null and are left out of the JSON.
 */
@Serializable
data class SplitDto(
    val mode: SplitModeDto,
    val memberIds: List<String>? = null,
    val amounts: List<AmountDto>? = null,
    val weights: List<WeightDto>? = null,
    val items: List<ExpenseItem>? = null,
)

@Serializable
data class AmountDto(val memberId: String, val amountCents: Long)

@Serializable
data class WeightDto(val memberId: String, val weight: Int)

@Serializable
data class ExpenseRequest(
    val description: String,
    val amountCents: Long,
    val date: String,
    val paidById: String,
    val split: SplitDto,
)

@Serializable
data class SettlementRequest(
    val fromMemberId: String,
    val toMemberId: String,
    val amountCents: Long,
    val date: String,
)
