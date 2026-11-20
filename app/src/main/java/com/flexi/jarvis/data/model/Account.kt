package com.flexi.jarvis.data.model

enum class AccountType {
    FREE,
    JARVIS_PLUS,
    JARVIS_PRO,
    FLEXI_MASTERCLASS
}

data class Account(
    val id: String,
    val displayName: String,
    val email: String? = null,
    val type: AccountType = AccountType.FREE,
    val isFlexiMasterclassLinked: Boolean = false,
    val masterclassExpiresAt: String? = null
)

data class UsageSnapshot(
    val dailyLimit: Int?,
    val usedToday: Int,
    val remaining: Int?,
    val planLabel: String,
    val videoCreationEnabled: Boolean
)
