package com.pennywiseai.tracker.presentation.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import org.junit.Test
import java.math.BigDecimal

class ExtraTotalForTest {

    private val totals = FilteredTotals(
        income = BigDecimal("1000"),
        expenses = BigDecimal("300"),
        credit = BigDecimal("250"),
        transfer = BigDecimal("400"),
        investment = BigDecimal("500"),
        netBalance = BigDecimal("700")
    )
    private val noCredit = totals.copy(credit = BigDecimal.ZERO)

    @Test
    fun creditTile_isGone_cardSpendIsInExpenses() {
        // Fork: card purchases count as expenses, so there's no separate Credit tile.
        assertNull(extraTotalFor(totals, TransactionTypeFilter.ALL, false))
        assertNull(extraTotalFor(totals, TransactionTypeFilter.TRANSFER, true))
    }

    @Test
    fun incomeAndExpenseFilters_haveNoExtraTile() {
        assertNull(extraTotalFor(noCredit, TransactionTypeFilter.INCOME, false))
        assertNull(extraTotalFor(noCredit, TransactionTypeFilter.EXPENSE, false))
    }

    @Test
    fun transferAndInvestmentFilters_showTheirOwnTotal() {
        assertEquals(ExtraTotal(ExtraTotalKind.TRANSFER, BigDecimal("400")), extraTotalFor(totals, TransactionTypeFilter.TRANSFER, false))
        assertEquals(ExtraTotal(ExtraTotalKind.INVESTMENT, BigDecimal("500")), extraTotalFor(totals, TransactionTypeFilter.INVESTMENT, false))
    }
}
