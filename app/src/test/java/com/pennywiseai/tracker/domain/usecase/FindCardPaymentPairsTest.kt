package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class FindCardPaymentPairsTest {

    private val base: LocalDateTime = LocalDateTime.of(2026, 3, 25, 22, 0)
    private val created: LocalDateTime = LocalDateTime.of(2026, 4, 1, 9, 0)

    private val upiDebit = "Dear UPI user A/C X1234 debited by 99235.90 on date 25Mar26 trf to CRED CCBP UPI Refno 000000000000 -SBI"
    private val credConfirmation = "Payment of Rs.99,236 has been successfully credited towards your Federal Bank Credit Card. Your payment was settled in 3 seconds - CRED"

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: String,
        minutesFromBase: Long = 0,
        sms: String? = null,
        currency: String = "INR",
        updatedAt: LocalDateTime = created,
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "M",
        category = "Others",
        transactionType = type,
        dateTime = base.plusMinutes(minutesFromBase),
        transactionHash = "hash-$id",
        smsBody = sms,
        currency = currency,
        createdAt = created,
        updatedAt = updatedAt,
    )

    @Test
    fun `debit pairs with a card confirmation within a rupee`() {
        val debit = tx(1, TransactionType.EXPENSE, "99235.90", sms = upiDebit)
        val conf = tx(2, TransactionType.TRANSFER, "99236", minutesFromBase = 2, sms = credConfirmation)

        assertEquals(listOf(1L to 2L), findCardPaymentPairs(listOf(debit, conf)))
    }

    @Test
    fun `debit with no card confirmation stays an expense`() {
        val rentViaCred = tx(1, TransactionType.EXPENSE, "90000.00", sms = upiDebit)
        val otherConf = tx(2, TransactionType.TRANSFER, "480", minutesFromBase = 2, sms = credConfirmation)

        assertTrue(findCardPaymentPairs(listOf(rentViaCred, otherConf)).isEmpty())
    }

    @Test
    fun `a transfer that is not a card confirmation is ignored`() {
        val debit = tx(1, TransactionType.EXPENSE, "500.00", sms = upiDebit)
        val selfTransfer = tx(2, TransactionType.TRANSFER, "500.00", minutesFromBase = 1, sms = "IMPS of Rs 500 credited to your account")

        assertTrue(findCardPaymentPairs(listOf(debit, selfTransfer)).isEmpty())
    }

    @Test
    fun `outside the window or other currency does not pair`() {
        val conf = tx(9, TransactionType.TRANSFER, "1000", sms = credConfirmation)
        val tooLate = tx(1, TransactionType.EXPENSE, "1000.00", minutesFromBase = 4L * 24 * 60, sms = upiDebit)
        val usd = tx(2, TransactionType.EXPENSE, "1000.00", minutesFromBase = 5, sms = upiDebit, currency = "USD")

        assertTrue(findCardPaymentPairs(listOf(conf, tooLate, usd)).isEmpty())
    }

    @Test
    fun `a debit the user edited is left alone`() {
        val edited = tx(1, TransactionType.EXPENSE, "1000.00", sms = upiDebit, updatedAt = created.plusDays(2))
        val conf = tx(2, TransactionType.TRANSFER, "1000", minutesFromBase = 2, sms = credConfirmation)

        assertTrue(findCardPaymentPairs(listOf(edited, conf)).isEmpty())
    }

    @Test
    fun `two confirmations for one payment use the debit once, closest first`() {
        val debit = tx(1, TransactionType.EXPENSE, "68555.65", sms = upiDebit)
        val bankConf = tx(2, TransactionType.TRANSFER, "68555.65", minutesFromBase = -29,
            sms = "Yay! We've received your payment of Rs 68,555.65 towards your Scapia Federal credit card. -Federal Bank")
        val credConf = tx(3, TransactionType.TRANSFER, "68556", minutesFromBase = 1, sms = credConfirmation)

        assertEquals(listOf(1L to 3L), findCardPaymentPairs(listOf(debit, bankConf, credConf)))
    }
}
