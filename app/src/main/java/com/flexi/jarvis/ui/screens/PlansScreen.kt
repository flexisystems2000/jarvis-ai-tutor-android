
package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.repository.PlanPolicy

@Composable
fun PlansScreen(
    accountType: AccountType,
    usedToday: Int,
    masterclassExpiresAt: String? = null,
    onSelectPlan: (AccountType) -> Unit
) {
    val usage = PlanPolicy.usage(accountType, usedToday)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Usage and Limits",
            style = MaterialTheme.typography.headlineSmall
        )

        if (accountType == AccountType.FLEXI_MASTERCLASS) {

            Text(
                "Flexi Masterclass",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "Your existing Flexi Lessons subscription is verified."
            )

            Text(
                "Unlimited JARVIS access is active while your subscription remains valid."
            )

            Text("Token limits: No daily token limit")

            Text("Paid learning features: Included")

            Text("Video creation: Included")

            masterclassExpiresAt?.let { expiry ->
                HorizontalDivider()

                Text(
                    "Subscription expiry",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(expiry)
            }

            return@Column
        }

        Text("Current plan: ${usage.planLabel}")

        Text("Daily usage: $usedToday / ${usage.dailyLimit}")

        Text("Remaining today: ${usage.remaining}")

        HorizontalDivider()

        PlanCard(
            title = "Free",
            quota = "50 tokens per day",
            details = "Standard tutor access",
            onClick = {
                onSelectPlan(AccountType.FREE)
            }
        )

        PlanCard(
            title = "Jarvis Plus",
            quota = "4,000 tokens",
            details = "Longer chats, more image creation and more CBT practice",
            onClick = {
                onSelectPlan(AccountType.JARVIS_PLUS)
            }
        )

        PlanCard(
            title = "Jarvis Pro",
            quota = "10,000 tokens",
            details = "Plus benefits and video creation",
            onClick = {
                onSelectPlan(AccountType.JARVIS_PRO)
            }
        )

        Text(
            "Payments will be completed through the secure payment gateway after backend setup. Selecting a plan here is preview-only."
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    quota: String,
    details: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(quota)

            Text(
                details,
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = onClick) {
                Text("Preview plan")
            }
        }
    }
}
