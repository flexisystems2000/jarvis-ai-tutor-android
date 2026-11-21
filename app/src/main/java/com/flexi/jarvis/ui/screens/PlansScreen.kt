package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.repository.PlanPolicy
import com.flexi.jarvis.ui.theme.*

@Composable
fun PlansScreen(
    accountType: AccountType,
    usedToday: Int,
    masterclassExpiresAt: String? = null,
    onSelectPlan: (AccountType) -> Unit
) {
    val usage = PlanPolicy.usage(accountType, usedToday)
    Column(
        Modifier.fillMaxSize().background(JarvisBackground).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Plans", color = JarvisTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Choose the JARVIS experience that fits your learning.", color = JarvisTextSecondary, fontSize = 13.sp)

        if (accountType == AccountType.FLEXI_MASTERCLASS) {
            Surface(
                color = JarvisSurface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("FLEXI MASTERCLASS", color = JarvisGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Unlimited access is active", color = JarvisTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Your existing Flexi Lessons subscription is verified. Access remains active while your subscription is valid.", color = JarvisTextSecondary)
                    Text("No daily token limit  •  Paid learning features included", color = JarvisTextPrimary, fontSize = 12.sp)
                    masterclassExpiresAt?.let {
                        HorizontalDivider(color = JarvisOutline)
                        Text("Subscription expiry", color = JarvisTextSecondary, fontSize = 12.sp)
                        Text(it, color = JarvisTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            return@Column
        }

        Surface(
            color = JarvisSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CURRENT PLAN", color = JarvisTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(usage.planLabel, color = JarvisTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("$usedToday / ${usage.dailyLimit} daily usage", color = JarvisTextSecondary)
                LinearProgressIndicator(
                    progress = { if ((usage.dailyLimit ?: 0) > 0) (usedToday.toFloat() / (usage.dailyLimit ?: 1)).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisGreen,
                    trackColor = JarvisSurfaceVariant
                )
            }
        }

        PlanCard("Free", "50 tokens per day", "Standard tutor access", false) { onSelectPlan(AccountType.FREE) }
        PlanCard("JARVIS Plus", "4,000 tokens", "Longer chats, more image creation and CBT practice", true) { onSelectPlan(AccountType.JARVIS_PLUS) }
        PlanCard("JARVIS Pro", "10,000 tokens", "Plus benefits and video creation", false) { onSelectPlan(AccountType.JARVIS_PRO) }

        Text(
            "Plan selection is currently a preview. Secure payments will be enabled after payment-backend integration.",
            color = JarvisTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    quota: String,
    details: String,
    featured: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = JarvisSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (featured) 1.5.dp else 1.dp,
            if (featured) JarvisBlue else JarvisOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = JarvisTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (featured) Text("POPULAR", color = JarvisGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(quota, color = JarvisBlue, fontWeight = FontWeight.SemiBold)
            Text(details, color = JarvisTextSecondary, fontSize = 12.sp)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text("View plan")
            }
        }
    }
}
