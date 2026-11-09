package online.aireport.split.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatMonthHeading
import online.aireport.split.core.formatShortDate
import online.aireport.split.core.parseIsoDate
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.EmptyState
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.PersonName
import online.aireport.split.ui.components.ReceiptBottom
import online.aireport.split.ui.components.ReceiptLine
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.ReceiptTop
import online.aireport.split.ui.components.Stamp
import online.aireport.split.ui.theme.SplitColors
import online.aireport.split.ui.theme.SplitTheme

/** The expenses and payments of a group as one long receipt, newest first, a heading per month. */
@Composable
fun ExpensesTab(group: GroupDetail, expenses: List<Expense>, onOpenExpense: (Expense) -> Unit) {
    val months = remember(expenses) { expenses.groupByMonth() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        item(key = "top") { ReceiptTop() }
        if (expenses.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.expenses_empty), Modifier.paper(SplitTheme.colors)) }
        }
        months.forEachIndexed { index, month ->
            monthHeading(month.heading, first = index == 0)
            items(month.expenses, key = { it.id }) { expense ->
                ExpenseRow(expense, group, onClick = { onOpenExpense(expense) })
            }
        }
        item(key = "bottom") { ReceiptBottom() }
    }
}

private class Month(val heading: String, val expenses: List<Expense>)

/** Groups consecutive expenses of the same month; the list is already sorted by date. */
private fun List<Expense>.groupByMonth(): List<Month> {
    val months = mutableListOf<Month>()
    for (expense in this) {
        // Without a readable date the entry goes under a heading of its own, as given.
        val heading = parseIsoDate(expense.date)?.let(::formatMonthHeading) ?: expense.date
        val last = months.lastOrNull()
        if (last?.heading == heading) {
            months[months.lastIndex] = Month(heading, last.expenses + expense)
        } else {
            months += Month(heading, listOf(expense))
        }
    }
    return months
}

private fun LazyListScope.monthHeading(heading: String, first: Boolean) {
    item(key = "month-$heading") {
        Column(Modifier.paper(SplitTheme.colors)) {
            if (!first) Perforation(Modifier.padding(horizontal = ReceiptPadding, vertical = 6.dp))
            Text(
                text = heading,
                modifier = Modifier
                    .padding(start = ReceiptPadding, end = ReceiptPadding, top = 8.dp, bottom = 2.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseRow(expense: Expense, group: GroupDetail, onClick: () -> Unit) {
    val small = MaterialTheme.typography.bodyMedium
    val muted = SplitTheme.colors.mutedInk
    val isSettlement = expense.kind == ExpenseKind.SETTLEMENT
    val date = parseIsoDate(expense.date)?.let { formatShortDate(it) } ?: expense.date
    Column(
        modifier = Modifier
            .paper(SplitTheme.colors)
            .clickable(onClick = onClick)
            .padding(horizontal = ReceiptPadding, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ReceiptLine(name = expenseTitle(expense, group), amount = formatAmount(expense.amountCents))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonName(
                    name = group.memberName(expense.paidById),
                    position = group.memberPosition(expense.paidById),
                    modifier = Modifier.weight(1f, fill = false),
                    style = small,
                )
                Text(
                    text = stringResource(
                        if (isSettlement) R.string.settlement_paid_on else R.string.expense_paid_on,
                        date,
                    ),
                    style = small,
                    color = muted,
                    maxLines = 1,
                )
            }
            if (isSettlement) {
                Stamp(stringResource(R.string.stamp_paid), Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
            } else {
                expense.effectFor(group.myMemberId)?.let { effect ->
                    Text(
                        text = stringResource(
                            if (effect > 0) R.string.expense_effect_positive else R.string.expense_effect_negative,
                            formatCents(abs(effect)),
                        ),
                        style = small,
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A line of a receipt that is built from lazy list items. */
private fun Modifier.paper(colors: SplitColors): Modifier = fillMaxWidth().background(colors.paper)

/** The description, or "Bas betaalde Anna" for a payment between members. */
@Composable
internal fun expenseTitle(expense: Expense, group: GroupDetail): String = when (expense.kind) {
    ExpenseKind.EXPENSE -> expense.description
    ExpenseKind.SETTLEMENT -> stringResource(
        R.string.settlement_title,
        group.memberName(expense.paidById),
        group.memberName(expense.shares.firstOrNull()?.memberId),
    )
}

@Composable
internal fun GroupDetail.memberName(memberId: String?): String =
    memberId?.let(::member)?.name ?: stringResource(R.string.member_unknown)
