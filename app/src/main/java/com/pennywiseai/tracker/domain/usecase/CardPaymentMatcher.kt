package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.parser.core.CardPaymentConfirmation
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a credit card bill payment from a bank account into a transfer instead
 * of spending, but only on evidence: the card side confirmed a payment of the
 * same amount ("credited towards your ... Credit Card", "received your payment
 * ... credit card", ...). A debit to a payee like CRED with no such
 * confirmation (rent, say) stays an expense.
 *
 * Both rows become TRANSFER with from/to filled in, the same two-row shape as
 * a user-confirmed self-transfer. Balances are not touched: the debit already
 * moved the bank balance when it was saved.
 */
@Singleton
class CardPaymentMatcher @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    /** Pairs within [start]..[end], widened by the match window on both sides. */
    suspend fun matchBetween(start: LocalDateTime, end: LocalDateTime): Int {
        val rows = transactionRepository.getTransactionsBetweenDatesList(
            start.minus(WINDOW), end.plus(WINDOW)
        )
        val byId = rows.associateBy { it.id }
        val now = LocalDateTime.now()
        val pairs = findCardPaymentPairs(rows)
        for ((debitId, confirmationId) in pairs) {
            val debit = byId[debitId] ?: continue
            val confirmation = byId[confirmationId] ?: continue
            val toAccount = confirmation.toAccount ?: confirmation.accountNumber
            transactionRepository.updateTransaction(
                debit.copy(
                    transactionType = TransactionType.TRANSFER,
                    fromAccount = debit.accountNumber,
                    fromBankName = debit.bankName,
                    toAccount = toAccount,
                    toBankName = confirmation.bankName,
                    updatedAt = now
                )
            )
            transactionRepository.updateTransaction(
                confirmation.copy(
                    fromAccount = debit.accountNumber,
                    // Also marks the confirmation as paired (see findCardPaymentPairs).
                    fromBankName = debit.bankName ?: "Bank account",
                    toAccount = toAccount,
                    toBankName = confirmation.bankName,
                    updatedAt = now
                )
            )
        }
        return pairs.size
    }

    /** Whole history: catches past payments too. */
    suspend fun matchAll(): Int = matchBetween(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now())

    companion object {
        val WINDOW: Duration = Duration.ofDays(3)
    }
}

/** CRED reports whole rupees ("Rs.99,236" for a ₹99,235.90 debit). */
private val AMOUNT_TOLERANCE = BigDecimal("1.00")

/**
 * Debit → confirmation pairs, closest in time first; each row used once.
 *
 * A confirmation is a TRANSFER row whose SMS matches [CardPaymentConfirmation]
 * and isn't paired yet. A debit is an untouched EXPENSE (never edited by the
 * user, not split, not on a loan) in the same currency, within ₹1 and
 * [CardPaymentMatcher.WINDOW] of it. Pure so it can be unit-tested.
 */
internal fun findCardPaymentPairs(rows: List<TransactionEntity>): List<Pair<Long, Long>> {
    val confirmations = rows.filter {
        it.transactionType == TransactionType.TRANSFER &&
            it.fromAccount == null && it.fromBankName == null &&
            it.smsBody?.let(CardPaymentConfirmation::matches) == true
    }
    if (confirmations.isEmpty()) return emptyList()

    val debits = rows.filter {
        it.transactionType == TransactionType.EXPENSE &&
            it.loanId == null &&
            it.lentSharesTotal == null &&
            Duration.between(it.createdAt, it.updatedAt).abs() < Duration.ofMinutes(1) &&
            it.smsBody?.let(CardPaymentConfirmation::matches) != true
    }

    data class Candidate(val debitId: Long, val confirmationId: Long, val gap: Duration)
    val candidates = buildList {
        for (c in confirmations) for (d in debits) {
            if (d.currency != c.currency) continue
            if ((d.amount - c.amount).abs() > AMOUNT_TOLERANCE) continue
            val gap = Duration.between(d.dateTime, c.dateTime).abs()
            if (gap > CardPaymentMatcher.WINDOW) continue
            add(Candidate(d.id, c.id, gap))
        }
    }.sortedBy { it.gap }

    val usedDebits = HashSet<Long>()
    val usedConfirmations = HashSet<Long>()
    val result = mutableListOf<Pair<Long, Long>>()
    for (cand in candidates) {
        if (cand.debitId in usedDebits || cand.confirmationId in usedConfirmations) continue
        usedDebits += cand.debitId
        usedConfirmations += cand.confirmationId
        result += cand.debitId to cand.confirmationId
    }
    return result
}
