package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.AccountType

@Composable
fun PlansScreen(
    accountType: AccountType,
    usedToday: Int,
    masterclassExpiresAt: String? = null,
    onSelectPlan: (AccountType) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Plans",
            color = colors.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Choose the JARVIS experience that fits your learning.",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp
        )

        if (accountType == AccountType.FLEXI_MASTERCLASS) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, colors.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "FLEXI MASTERCLASS",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        "Unlimited access is active",
                        color = colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Your Flexi Masterclass access includes all paid learning features.",
                        color = colors.onSurfaceVariant
                    )

                    PlanFeature("Unlimited AI tutoring")
                    PlanFeature("Unlimited CBT practice")
                    PlanFeature("Image creation")
                    PlanFeature("Video creation")
                    PlanFeature("No subscription upgrade prompts")

                    masterclassExpiresAt?.let {
                        HorizontalDivider(color = colors.outlineVariant)

                        Text(
                            "Subscription expiry",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Text(
                            it,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            Text(
                "AVAILABLE PLANS",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            PlanCard(
                title = "FREE",
                price = "Free",
                description = "Get started with JARVIS AI Tutor.",
                features = listOf(
                    "50 tokens daily",
                    "Standard AI tutoring",
                    "Available free learning features"
                ),
                featured = false,
                current = accountType == AccountType.FREE,
                onClick = {
                    onSelectPlan(AccountType.FREE)
                }
            )

            PlanCard(
                title = "JARVIS PLUS",
                price = "₦4,000",
                description = "An enhanced learning experience.",
                features = listOf(
                    "Longer AI conversations",
                    "More image creation",
                    "More CBT practice",
                    "Enhanced learning experience"
                ),
                featured = true,
                current = accountType == AccountType.JARVIS_PLUS,
                onClick = {
                    onSelectPlan(AccountType.JARVIS_PLUS)
                }
            )

            PlanCard(
                title = "JARVIS PRO",
                price = "₦10,000",
                description = "The complete JARVIS learning experience.",
                features = listOf(
                    "Everything in JARVIS Plus",
                    "More extensive AI usage",
                    "Video creation",
                    "Enhanced learning features"
                ),
                featured = false,
                current = accountType == AccountType.JARVIS_PRO,
                onClick = {
                    onSelectPlan(AccountType.JARVIS_PRO)
                }
            )

            PlanCard(
                title = "FLEXI MASTERCLASS",
                price = "Unlimited",
                description = "All paid learning features in one experience.",
                features = listOf(
                    "Unlimited AI tutoring",
                    "Unlimited CBT practice",
                    "Image creation",
                    "Video creation",
                    "No subscription upgrade prompts"
                ),
                featured = false,
                current = false,
                onClick = {
                    onSelectPlan(AccountType.FLEXI_MASTERCLASS)
                }
            )

            Text(
                "Secure subscription payments will be enabled when payment integration is connected.",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    description: String,
    features: List<String>,
    featured: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        color = if (featured) {
            colors.primaryContainer
        } else {
            colors.surface
        },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            if (featured) 1.5.dp else 1.dp,
            if (featured) colors.primary else colors.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    color = if (featured) {
                        colors.onPrimaryContainer
                    } else {
                        colors.onSurface
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                if (featured) {
                    Text(
                        "POPULAR",
                        color = colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                price,
                color = if (featured) {
                    colors.onPrimaryContainer
                } else {
                    colors.primary
                },
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                description,
                color = if (featured) {
                    colors.onPrimaryContainer
                } else {
                    colors.onSurfaceVariant
                },
                fontSize = 12.sp
            )

            HorizontalDivider(
                color = if (featured) {
                    colors.onPrimaryContainer.copy(alpha = 0.18f)
                } else {
                    colors.outlineVariant
                }
            )

            features.forEach { feature ->
                PlanFeature(
                    feature,
                    featured = featured
                )
            }

            if (current) {
                Text(
                    "CURRENT PLAN",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (title == "FREE") {
                            "Select Free"
                        } else {
                            "Choose $title"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanFeature(
    text: String,
    featured: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text,
            color = if (featured) {
                colors.onPrimaryContainer
            } else {
                colors.onSurface
            },
            fontSize = 12.sp
        )
    }
}
