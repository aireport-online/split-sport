package online.aireport.split.ui.expense

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import online.aireport.split.core.MAX_WEIGHT
import online.aireport.split.core.Share
import online.aireport.split.core.Split
import online.aireport.split.core.SplitErrorCode
import online.aireport.split.core.SplitException
import online.aireport.split.core.SplitItem
import online.aireport.split.core.SplitMode
import online.aireport.split.core.Weight
import online.aireport.split.core.computeShares
import online.aireport.split.core.parseAmount

/** One line of a receipt while it is being edited. [key] identifies the row on screen. */
@Serializable
data class ItemDraft(
    val key: Long,
    val name: String = "",
    val amountText: String = "",
    val memberIds: Set<String> = emptySet(),
)

/**
 * The expense form as typed. Every split mode keeps its own input, so switching modes back and
 * forth loses nothing. Serializable so it survives the app being killed behind the camera.
 */
@Serializable
data class ExpenseDraft(
    val description: String = "",
    val amountText: String = "",
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val paidById: String,
    val mode: SplitMode = SplitMode.EQUAL,
    /** "Gelijk": who takes part. */
    val equalMemberIds: Set<String> = emptySet(),
    /** "Bedragen": the amount typed per member. */
    val exactAmounts: Map<String, String> = emptyMap(),
    /** "Aandelen": the number of shares per member; absent means none. */
    val weights: Map<String, Int> = emptyMap(),
    /** "Per regel": the lines of the receipt. */
    val items: List<ItemDraft> = emptyList(),
) {
    /** The sum of the receipt lines that have a valid amount. */
    val itemsTotalCents: Long get() = items.sumOf { parseAmount(it.amountText) ?: 0L }

    /**
     * The amount of the expense. In "Per regel" an empty amount field means the total of the
     * lines. Null when there is no valid amount.
     */
    val amountCents: Long?
        get() = when {
            amountText.isNotBlank() -> parseAmount(amountText)
            mode == SplitMode.ITEMS -> itemsTotalCents.takeIf { it > 0 }
            else -> null
        }

    /** "Bedragen": what is still to divide; negative when too much was divided. Null without an amount. */
    val exactRemainingCents: Long?
        get() = amountCents?.let { total -> total - exactAmounts.values.sumOf { parseAmount(it) ?: 0L } }

    /** "Gelijk" with only [potMemberId] ticked: the expense is at the pot's cost, nobody chips in. */
    fun isChargedToPot(potMemberId: String): Boolean = mode == SplitMode.EQUAL && equalMemberIds == setOf(potMemberId)

    /** "Per regel": how much the typed total differs from the lines; zero when there is nothing to spread. */
    val itemsDifferenceCents: Long
        get() {
            if (items.isEmpty() || amountText.isBlank()) return 0L
            val total = amountCents ?: return 0L
            return total - itemsTotalCents
        }

    /**
     * The split and what everyone owes, computed locally with the same algorithm as the server.
     * [memberIds] is the group's member order, which is also the order of the split.
     */
    fun preview(memberIds: List<String>): SplitPreview {
        val amount = amountCents ?: return SplitPreview.Invalid(SplitErrorCode.INVALID_AMOUNT)
        val split = split(memberIds) ?: return SplitPreview.Invalid(SplitErrorCode.INVALID_AMOUNT)
        return try {
            SplitPreview.Valid(amount, split, computeShares(amount, split))
        } catch (e: SplitException) {
            SplitPreview.Invalid(e.code)
        }
    }

    /** Null when an amount in the split cannot be read. */
    private fun split(memberIds: List<String>): Split? = when (mode) {
        SplitMode.EQUAL -> Split.Equal(memberIds.filter { it in equalMemberIds })
        SplitMode.EXACT -> Split.Exact(
            memberIds.map { id ->
                val text = exactAmounts[id].orEmpty()
                Share(id, if (text.isBlank()) 0L else parseAmount(text) ?: return null)
            },
        )
        SplitMode.SHARES -> Split.Shares(memberIds.map { Weight(it, weights[it] ?: 0) })
        SplitMode.ITEMS -> Split.Items(
            items.map { item ->
                // A line without a valid amount becomes 0, which computeShares reports as invalid_item.
                SplitItem(item.name.trim(), parseAmount(item.amountText) ?: 0L, memberIds.filter { it in item.memberIds })
            },
        )
    }

    fun toggleEqualMember(memberId: String): ExpenseDraft =
        copy(equalMemberIds = equalMemberIds.toggle(memberId))

    fun setExactAmount(memberId: String, text: String): ExpenseDraft =
        copy(exactAmounts = exactAmounts + (memberId to text))

    fun changeWeight(memberId: String, delta: Int): ExpenseDraft =
        copy(weights = weights + (memberId to ((weights[memberId] ?: 0) + delta).coerceIn(0, MAX_WEIGHT)))

    /** Adds an empty receipt line shared by [memberIds]. */
    fun addItem(memberIds: Collection<String>): ExpenseDraft =
        copy(items = items + ItemDraft(key = nextItemKey(), memberIds = memberIds.toSet()))

    fun removeItem(key: Long): ExpenseDraft = copy(items = items.filterNot { it.key == key })

    fun updateItem(key: Long, change: (ItemDraft) -> ItemDraft): ExpenseDraft =
        copy(items = items.map { if (it.key == key) change(it) else it })

    fun toggleItemMember(key: Long, memberId: String): ExpenseDraft =
        updateItem(key) { it.copy(memberIds = it.memberIds.toggle(memberId)) }

    private fun nextItemKey(): Long = (items.maxOfOrNull { it.key } ?: 0L) + 1
}

/** The live "Per persoon" result of a draft. */
sealed interface SplitPreview {
    data class Valid(val amountCents: Long, val split: Split, val shares: List<Share>) : SplitPreview

    data class Invalid(val error: SplitErrorCode) : SplitPreview
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
