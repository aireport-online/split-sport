package online.aireport.split.ui.charge

import java.time.LocalDate
import online.aireport.split.core.MAX_AMOUNT_CENTS
import online.aireport.split.core.formatAmountInput
import online.aireport.split.core.parseAmount
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.Fine
import online.aireport.split.data.model.GroupDetail

/** A fine ("Boete geven") or a contribution ("Inleg vragen") as typed. */
data class ChargeForm(
    val type: ChargeType,
    val description: String = "",
    /** The amount per team member. */
    val amountText: String = "",
    /** Who is charged; never the pot. */
    val memberIds: Set<String> = emptySet(),
    val date: LocalDate,
) {
    /** The amount per member; null when it is not a positive amount. */
    val amountCents: Long? get() = parseAmount(amountText)?.takeIf { it > 0 }

    /** What the charge adds up to over everyone; null without an amount or anyone to charge. */
    val totalCents: Long?
        get() = amountCents?.takeIf { memberIds.isNotEmpty() }?.let { it * memberIds.size }

    val canSave: Boolean
        get() = description.isNotBlank() && (totalCents ?: 0L) in 1..MAX_AMOUNT_CENTS

    fun toggleMember(memberId: String): ChargeForm =
        copy(memberIds = if (memberId in memberIds) memberIds - memberId else memberIds + memberId)

    /** Fills in a line of the fine list. */
    fun withFine(fine: Fine): ChargeForm =
        copy(description = fine.name, amountText = formatAmountInput(fine.amountCents))

    /** Whether the form still says exactly what [fine] says. */
    fun isFine(fine: Fine): Boolean = description == fine.name && amountCents == fine.amountCents
}

/**
 * A new charge. A contribution is asked of everyone, so every team member starts ticked; a fine
 * is for whoever earned it, so nobody does.
 */
fun newChargeForm(type: ChargeType, group: GroupDetail, today: LocalDate): ChargeForm = ChargeForm(
    type = type,
    memberIds = when (type) {
        ChargeType.CONTRIBUTION -> group.players.map { it.id }.toSet()
        ChargeType.FINE -> emptySet()
    },
    date = today,
)
