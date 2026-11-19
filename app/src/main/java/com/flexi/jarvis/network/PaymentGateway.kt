package com.flexi.jarvis.network

data class CheckoutRequest(val planId: String, val successUrl: String, val cancelUrl: String)
data class CheckoutSession(val checkoutUrl: String, val reference: String)

interface PaymentGateway {
    suspend fun createCheckout(request: CheckoutRequest): Result<CheckoutSession>
    suspend fun verifyPayment(reference: String): Result<Boolean>
}

/**
 * Implement with a server-created checkout session (e.g. Paystack/Flutterwave).
 * Never collect card details directly in this app or trust client-side payment success.
 */
class UnconfiguredPaymentGateway : PaymentGateway {
    override suspend fun createCheckout(request: CheckoutRequest) =
        Result.failure<CheckoutSession>(IllegalStateException("Payment gateway backend is not configured."))
    override suspend fun verifyPayment(reference: String) =
        Result.failure<Boolean>(IllegalStateException("Payment verification backend is not configured."))
}
