package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.model.UsageSnapshot

/**
 * Client-side display policy only. The backend MUST repeat every entitlement
 * and token check before fulfilling a request; never trust the APK for billing.
 */
object PlanPolicy {
    fun dailyLimit(type: AccountType): Int? = when (type) {
        AccountType.FREE -> 50
        AccountType.JARVIS_PLUS -> 4_000
        AccountType.JARVIS_PRO -> 10_000
        AccountType.FLEXI_MASTERCLASS -> null
    }

    fun videoEnabled(type: AccountType): Boolean =
        type == AccountType.JARVIS_PRO || type == AccountType.FLEXI_MASTERCLASS

    fun usage(type: AccountType, used: Int): UsageSnapshot {
        val limit = dailyLimit(type)
        val remaining = limit?.let { (it - used).coerceAtLeast(0) }
        val label = when (type) {
            AccountType.FREE -> "Free"
            AccountType.JARVIS_PLUS -> "Jarvis Plus"
            AccountType.JARVIS_PRO -> "Jarvis Pro"
            AccountType.FLEXI_MASTERCLASS -> "Flexi Masterclass"
        }
        return UsageSnapshot(limit, used, remaining, label, videoEnabled(type))
    }

    fun canUseTokens(type: AccountType, used: Int, requested: Int = 1): Boolean {
        if (type == AccountType.FLEXI_MASTERCLASS) return true
        val limit = dailyLimit(type) ?: return true
        return used + requested <= limit
    }
}
