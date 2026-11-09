package online.aireport.split.core

/**
 * Splitting an amount of money over group members.
 *
 * A Kotlin port of `packages/core/src/split.ts`. All amounts are integer cents. The server result
 * is authoritative; this copy drives the live preview. `spec/split-vectors.json` keeps the
 * implementations identical.
 */

const val MAX_AMOUNT_CENTS = 99_999_999L
const val MAX_WEIGHT = 1_000_000

/** The four ways an expense can be split, in the order the form offers them. */
enum class SplitMode { EQUAL, EXACT, SHARES, ITEMS }

sealed interface Split {
    data class Equal(val memberIds: List<String>) : Split

    data class Exact(val amounts: List<Share>) : Split

    data class Shares(val weights: List<Weight>) : Split

    data class Items(val items: List<SplitItem>) : Split
}

data class Share(val memberId: String, val amountCents: Long)

data class Weight(val memberId: String, val weight: Int)

data class SplitItem(val name: String, val amountCents: Long, val memberIds: List<String>)

enum class SplitErrorCode(val code: String) {
    INVALID_AMOUNT("invalid_amount"),
    NO_PARTICIPANTS("no_participants"),
    DUPLICATE_MEMBER("duplicate_member"),
    INVALID_WEIGHT("invalid_weight"),
    EXACT_SUM_MISMATCH("exact_sum_mismatch"),
    INVALID_ITEM("invalid_item"),
    ITEM_UNASSIGNED("item_unassigned"),
}

class SplitException(val code: SplitErrorCode) : IllegalArgumentException(code.code)

/**
 * Distributes [totalCents] proportionally to [weights] using the largest remainder method.
 * Leftover cents go to the largest fractional parts; ties go to the earlier index. The result
 * always sums to exactly [totalCents]. A negative total is allocated as its absolute value and
 * negated.
 */
fun allocate(totalCents: Long, weights: List<Long>): List<Long> {
    val weightSum = weights.sum()
    if (weights.isEmpty() || weightSum <= 0) throw SplitException(SplitErrorCode.NO_PARTICIPANTS)
    if (totalCents < 0) return allocate(-totalCents, weights).map { -it }

    // At most 99_999_999 * 1_000_000 < 2^63, so the product fits in a Long.
    val products = weights.map { totalCents * it }
    val result = products.map { it / weightSum }.toMutableList()
    val byRemainder = weights.indices.sortedWith(
        compareByDescending<Int> { products[it] % weightSum }.thenBy { it },
    )
    val leftover = (totalCents - result.sum()).toInt()
    byRemainder.take(leftover).forEach { result[it] += 1 }
    return result
}

/**
 * Computes what every participant owes for an expense of [amountCents]. Shares are returned in
 * order of first appearance and sum to [amountCents].
 *
 * @throws SplitException when the split is not valid.
 */
fun computeShares(amountCents: Long, split: Split): List<Share> {
    if (amountCents <= 0 || amountCents > MAX_AMOUNT_CENTS) {
        throw SplitException(SplitErrorCode.INVALID_AMOUNT)
    }
    return when (split) {
        is Split.Equal -> equalShares(amountCents, split.memberIds)
        is Split.Exact -> exactShares(amountCents, split.amounts)
        is Split.Shares -> weightedShares(amountCents, split.weights)
        is Split.Items -> itemShares(amountCents, split.items)
    }
}

private fun equalShares(amountCents: Long, memberIds: List<String>): List<Share> {
    if (memberIds.isEmpty()) throw SplitException(SplitErrorCode.NO_PARTICIPANTS)
    assertUnique(memberIds)
    val parts = allocate(amountCents, memberIds.map { 1L })
    return memberIds.mapIndexed { index, memberId -> Share(memberId, parts[index]) }
}

private fun exactShares(amountCents: Long, amounts: List<Share>): List<Share> {
    if (amounts.isEmpty()) throw SplitException(SplitErrorCode.NO_PARTICIPANTS)
    assertUnique(amounts.map { it.memberId })
    if (amounts.any { it.amountCents < 0 }) throw SplitException(SplitErrorCode.INVALID_AMOUNT)
    if (amounts.sumOf { it.amountCents } != amountCents) {
        throw SplitException(SplitErrorCode.EXACT_SUM_MISMATCH)
    }
    return amounts.filter { it.amountCents > 0 }
}

private fun weightedShares(amountCents: Long, weights: List<Weight>): List<Share> {
    assertUnique(weights.map { it.memberId })
    if (weights.any { it.weight < 0 || it.weight > MAX_WEIGHT }) {
        throw SplitException(SplitErrorCode.INVALID_WEIGHT)
    }
    val active = weights.filter { it.weight > 0 }
    if (active.isEmpty()) throw SplitException(SplitErrorCode.NO_PARTICIPANTS)
    val parts = allocate(amountCents, active.map { it.weight.toLong() })
    return active.mapIndexed { index, entry -> Share(entry.memberId, parts[index]) }
}

private fun itemShares(amountCents: Long, items: List<SplitItem>): List<Share> {
    if (items.isEmpty()) throw SplitException(SplitErrorCode.NO_PARTICIPANTS)
    // A LinkedHashMap keeps the members in order of first appearance.
    val subtotals = LinkedHashMap<String, Long>()
    var itemsTotal = 0L
    for (item in items) {
        if (item.amountCents <= 0 || item.amountCents > MAX_AMOUNT_CENTS) {
            throw SplitException(SplitErrorCode.INVALID_ITEM)
        }
        if (item.memberIds.isEmpty()) throw SplitException(SplitErrorCode.ITEM_UNASSIGNED)
        assertUnique(item.memberIds)
        itemsTotal += item.amountCents
        val parts = allocate(item.amountCents, item.memberIds.map { 1L })
        item.memberIds.forEachIndexed { index, memberId ->
            subtotals[memberId] = (subtotals[memberId] ?: 0L) + parts[index]
        }
    }
    if (itemsTotal > MAX_AMOUNT_CENTS) throw SplitException(SplitErrorCode.INVALID_ITEM)
    // Whatever the receipt total differs from the items (tip, service charge, discount) is
    // spread in proportion to what everyone ordered.
    val weights = subtotals.values.toList()
    val extra = allocate(amountCents - itemsTotal, weights)
    return subtotals.keys.mapIndexed { index, memberId -> Share(memberId, weights[index] + extra[index]) }
}

private fun assertUnique(ids: List<String>) {
    if (ids.toSet().size != ids.size) throw SplitException(SplitErrorCode.DUPLICATE_MEMBER)
}
