package online.aireport.split.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import online.aireport.split.ui.components.MemberName
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.ReceiptBottom
import online.aireport.split.ui.components.ReceiptLine
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.ReceiptTop
import online.aireport.split.ui.components.Stamp
import online.aireport.split.ui.theme.SplitColors
import online.aireport.split.ui.theme.SplitTheme

/**
 * Everything that happened in a team as one long receipt, newest first, a heading per month:
 * expenses, fines, contributions and payments.
 */
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
                EntryRow(expense, group, onClick = { onOpenExpense(expense) })
            }
        }
        item(key = "bottom") { ReceiptBottom() }
    }
}

private class Month(val heading: String, val expenses: List<Expense>)

/** Groups consecutive entries of the same month; the list is already sorted by date. */
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

/** A line of the receipt and, under it, who it was about, when, and its stamp or your part in it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(expense: Expense, group: GroupDetail, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .paper(SplitTheme.colors)
            .clickable(onClick = onClick)
            .padding(horizontal = ReceiptPadding, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ReceiptLine(name = entryTitle(expense, group), amount = formatAmount(expense.amountCents))
        // On a narrow screen or with a large font the parts wrap onto lines of their own.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            when (expense.kind) {
                ExpenseKind.EXPENSE -> ExpenseDetails(expense, group)
                ExpenseKind.SETTLEMENT -> SettlementDetails(expense, group)
                ExpenseKind.FINE -> FineDetails(expense, group)
                ExpenseKind.CONTRIBUTION -> ContributionDetails(expense)
            }
        }
    }
}

/** "Anna betaalde · 2 nov" (or "Teamkas betaalde") and what it means for you. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.ExpenseDetails(expense: Expense, group: GroupDetail) {
    MemberName(group, expense.paidById, style = MaterialTheme.typography.bodyMedium)
    Detail(stringResource(R.string.expense_paid_on, shortDate(expense)))
    Spacer(Modifier.weight(1f))
    expense.effectFor(group.myMemberId)?.let { effect ->
        Detail(
            stringResource(
                if (effect > 0) R.string.expense_effect_positive else R.string.expense_effect_negative,
                formatCents(abs(effect)),
            ),
        )
    }
}

/** Who paid, when, and the stamp "BETAALD". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.SettlementDetails(expense: Expense, group: GroupDetail) {
    MemberName(group, expense.paidById, style = MaterialTheme.typography.bodyMedium)
    Detail(stringResource(R.string.settlement_paid_on, shortDate(expense)))
    Spacer(Modifier.weight(1f))
    Stamp(stringResource(R.string.stamp_paid), Modifier.padding(vertical = 2.dp))
}

/** Who got the fine, when, and the stamp "BOETE". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.FineDetails(expense: Expense, group: GroupDetail) {
    expense.shares.forEach { share ->
        MemberName(group, share.memberId, style = MaterialTheme.typography.bodyMedium)
    }
    Detail(stringResource(R.string.entry_on, shortDate(expense)))
    Spacer(Modifier.weight(1f))
    Stamp(stringResource(R.string.stamp_fine), Modifier.padding(vertical = 2.dp))
}

/** "5 × 25,00 · 2 nov" and the stamp "INLEG" in ink. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.ContributionDetails(expense: Expense) {
    val each = expense.shares.firstOrNull()?.amountCents ?: 0L
    Detail(stringResource(R.string.contribution_each_on, expense.shares.size, formatAmount(each), shortDate(expense)))
    Spacer(Modifier.weight(1f))
    Stamp(stringResource(R.string.stamp_contribution), Modifier.padding(vertical = 2.dp), color = SplitTheme.colors.ink)
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = SplitTheme.colors.mutedInk, maxLines = 1)
}

private fun shortDate(expense: Expense): String =
    parseIsoDate(expense.date)?.let { formatShortDate(it) } ?: expense.date

/** A line of a receipt that is built from lazy list items. */
private fun Modifier.paper(colors: SplitColors): Modifier = fillMaxWidth().background(colors.paper)

/** The description, or "Bas betaalde Teamkas" for a payment. */
@Composable
internal fun entryTitle(expense: Expense, group: GroupDetail): String = when (expense.kind) {
    ExpenseKind.SETTLEMENT -> stringResource(
        R.string.settlement_title,
        group.memberName(expense.paidById),
        group.memberName(expense.shares.firstOrNull()?.memberId),
    )
    ExpenseKind.EXPENSE, ExpenseKind.FINE, ExpenseKind.CONTRIBUTION -> expense.description
}
