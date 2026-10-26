package app.stride.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.money.domain.Finance
import app.stride.money.model.MoneyData
import app.stride.ui.FCard
import app.stride.ui.PeriodNav
import app.stride.ui.SectionHost
import app.stride.ui.ThinBar
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.YearMonth
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/** Spending for a month against the category budgets, and the latest expenses: typed ones and Wallet's receipts. */
@Composable
fun SpendingTab(vm: MoneyViewModel, d: MoneyData, month: YearMonth, onMonth: (YearMonth) -> Unit, onAdd: () -> Unit, padding: PaddingValues, host: SectionHost, onAll: () -> Unit = {}, onEdit: (String) -> Unit = {}) {
    val base = d.baseCurrency
    val status = Finance.budgetStatus(d, month)
    val sum = Finance.budgetSummary(d, month)
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PeriodNav("${month.month.getDisplayName(DateStyle.FULL, Locale.US)} ${month.year}", { onMonth(month.minusMonths(1)) }, { onMonth(month.plusMonths(1)) }, subtitle = "Spending · $base") }
        with(host) {
            sections {
                section("budgets") {
                    item {
                        FCard {
                            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                Text("Spent ${Finance.money(sum.totalSpent, base)}", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (sum.totalBudget > 0) Text("of ${Finance.money(sum.totalBudget, base)}", color = StrideColors.Faint, fontSize = 13.sp)
                            }
                            if (sum.totalBudget > 0) ThinBar(sum.ratio.toFloat(), if (sum.overCount > 0) StrideColors.Danger else StrideColors.Accent)
                            if (sum.overCount > 0) Text("⚠ ${sum.overCount} over budget", color = StrideColors.Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(12.dp))
                            if (status.isEmpty()) Text("No spending or budgets this month yet.", color = StrideColors.Faint, fontSize = 13.sp)
                            status.forEach { c ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.category.color)))
                                    Spacer(Modifier.width(6.dp))
                                    Text(c.category.name, color = StrideColors.Text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    Text(Finance.money(c.spent, base) + if (c.limit > 0) " / ${Finance.money(c.limit, base)}" else "", color = if (c.over) StrideColors.Danger else StrideColors.Dim, fontSize = 12.sp)
                                }
                                if (c.limit > 0) ThinBar(c.ratio.toFloat(), if (c.over) StrideColors.Danger else Color(c.category.color), modifier = Modifier.padding(bottom = 10.dp))
                                else Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), enabled = d.categories.any { !it.archived }) {
                            Text(if (d.categories.any { !it.archived }) "+ Add expense" else "Add a category in Setup first")
                        }
                    }
                }
                section("expenses") {
                    if (d.expenses.isNotEmpty()) item {
                        androidx.compose.material3.OutlinedButton(onClick = onAll, modifier = Modifier.fillMaxWidth()) { Text("See all ${d.expenses.size} expenses →") }
                    }
                    val list = d.expenses.filter { it.date.startsWith(month.toString()) }.sortedByDescending { it.date }
                    if (list.isNotEmpty()) item {
                        val cats = d.categories.associateBy { it.id }
                        FCard {
                            Text("Expenses", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            list.forEach { e ->
                                val c = cats[e.categoryId]
                                Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).clickable { onEdit(e.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(c?.let { Color(it.color) } ?: StrideColors.Faint))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c?.name ?: "—", color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            Dates.full(e.date) + (e.note?.let { " · $it" } ?: "") + (e.company?.let { " · $it" } ?: "") + if (e.open != null) " · 🧾 receipt" else "",
                                            color = StrideColors.Faint, fontSize = 11.sp, maxLines = 1,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Finance.money(e.amount, e.currency), color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        if (e.currency != base) Text("≈ ${Finance.money(Finance.expenseBase(d, e), base)}", color = StrideColors.Faint, fontSize = 10.sp)
                                    }
                                    // Receipts belong to Wallet: tap opens them there; only typed expenses are deleted here.
                                    if (e.open == null) IconButton(onClick = { vm.deleteExpense(e.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Delete", tint = StrideColors.Faint) }
                                    else Spacer(Modifier.width(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
