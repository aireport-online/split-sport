package online.aireport.split.data.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExpenseKind {
    @SerialName("expense") EXPENSE,

    /** A payment between two members, one of which may be the pot. */
    @SerialName("settlement") SETTLEMENT,

    /** A charge: the pot is the payer and every listed member owes it the amount. */
    @SerialName("fine") FINE,

    /** A charge like a fine, asked of everyone: "inleg". */
    @SerialName("contribution") CONTRIBUTION,
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
    /** Member that paid. Settlement: who paid. Fine and contribution: always the pot. */
    val paidById: String,
    /** Calendar date, `YYYY-MM-DD`. */
    val date: String,
    val splitMode: SplitModeDto,
    /** What every participant owes. Settlement: one share, the receiver. Charge: one per charged member. */
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

/**
 * Kept by name: it is an argument of a navigation route, and Navigation finds an enum argument
 * by its class name at run time.
 */
@Keep
@Serializable
enum class ChargeType {
    @SerialName("fine") FINE,
    @SerialName("contribution") CONTRIBUTION,
}

/** A fine or a contribution; [amountCents] is the amount per member. */
@Serializable
data class ChargeRequest(
    val type: ChargeType,
    val description: String,
    val amountCents: Long,
    val memberIds: List<String>,
    val date: String,
)
