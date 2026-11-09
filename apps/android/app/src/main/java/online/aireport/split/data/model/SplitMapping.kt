package online.aireport.split.data.model

import online.aireport.split.core.Split
import online.aireport.split.core.SplitMode

fun SplitModeDto.toDomain(): SplitMode = when (this) {
    SplitModeDto.EQUAL -> SplitMode.EQUAL
    SplitModeDto.EXACT -> SplitMode.EXACT
    SplitModeDto.SHARES -> SplitMode.SHARES
    SplitModeDto.ITEMS -> SplitMode.ITEMS
}

fun Split.toDto(): SplitDto = when (this) {
    is Split.Equal -> SplitDto(SplitModeDto.EQUAL, memberIds = memberIds)
    is Split.Exact -> SplitDto(
        SplitModeDto.EXACT,
        amounts = amounts.map { AmountDto(it.memberId, it.amountCents) },
    )
    is Split.Shares -> SplitDto(
        SplitModeDto.SHARES,
        weights = weights.map { WeightDto(it.memberId, it.weight) },
    )
    is Split.Items -> SplitDto(
        SplitModeDto.ITEMS,
        items = items.map { ExpenseItem(it.name, it.amountCents, it.memberIds) },
    )
}
