package online.aireport.split.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatShortDate
import online.aireport.split.core.parseIsoDate
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.EmptyState
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.balanceColor

/** Room under the list so the last row can scroll clear of the floating button. */
internal val FabClearance = 96.dp

/** The expenses and payments of a group, newest first. */
@Composable
fun ExpensesTab(group: GroupDetail, expenses: List<Expense>, onOpenExpense: (Expense) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = FabClearance),
    ) {
        if (expenses.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.expenses_empty)) }
        } else {
            item(key = "expenses") {
                SplitCard {
                    expenses.forEachIndexed { index, expense ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ExpenseRow(expense, group, onClick = { onOpenExpense(expense) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, group: GroupDetail, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = expenseTitle(expense, group),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expenseSubtitle(expense, group),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatCents(expense.amountCents), style = MaterialTheme.typography.titleMedium)
            expense.effectFor(group.myMemberId)?.let { effect ->
                Text(
                    text = stringResource(
                        if (effect > 0) R.string.expense_effect_positive else R.string.expense_effect_negative,
                        formatCents(abs(effect)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = balanceColor(effect),
                )
            }
        }
    }
}

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

/** "Anna betaalde · 2 nov", or only the date for a payment between members. */
@Composable
internal fun expenseSubtitle(expense: Expense, group: GroupDetail): String {
    val date = parseIsoDate(expense.date)?.let { formatShortDate(it) } ?: expense.date
    return when (expense.kind) {
        ExpenseKind.EXPENSE -> stringResource(R.string.expense_paid_by, group.memberName(expense.paidById), date)
        ExpenseKind.SETTLEMENT -> date
    }
}

@Composable
internal fun GroupDetail.memberName(memberId: String?): String =
    memberId?.let(::member)?.name ?: stringResource(R.string.member_unknown)
