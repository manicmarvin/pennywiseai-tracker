package com.pennywiseai.parser.core

/**
 * Card-side confirmations that a credit card bill was paid, e.g.
 *  - CRED: "Payment of Rs.X has been successfully credited towards your <Bank> Credit Card"
 *  - Federal: "We've received your payment of ₹X towards your Scapia Federal credit card"
 *  - ICICI: "Payment of Rs X has been received on your ICICI Bank Credit Card XX1234 through ..."
 *  - ICICI: "thank you for your payment of INR X towards ICICI Bank Credit Card Account XX1234 ..."
 *
 * The money itself leaves on the paying bank account's own debit SMS. These
 * confirmations are kept as TRANSFER rows so the app can pair them with that
 * debit and turn it into a transfer instead of spending.
 */
object CardPaymentConfirmation {
    private val phrases = listOf(
        "credited towards your",
        "received your payment",
        "has been received on your",
        "thank you for your payment of"
    )

    fun matches(smsBody: String): Boolean {
        val lower = smsBody.lowercase()
        return lower.contains("credit card") && phrases.any { lower.contains(it) }
    }
}
