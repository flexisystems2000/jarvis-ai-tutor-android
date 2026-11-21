package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.Conversation
import com.flexi.jarvis.data.repository.ConversationRepository
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    userId: String?,
    repository: ConversationRepository,
    onOpenConversation: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var conversations by remember {
        mutableStateOf<List<Conversation>>(emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        if (userId.isNullOrBlank()) return

        loading = true

        runCatching {
            repository.list(userId)
        }.onSuccess {
            conversations = it
            error = null
        }.onFailure {
            error = it.message ?: "Unable to load conversation history."
        }

        loading = false
    }

    LaunchedEffect(userId) {
        refresh()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Conversation history",
            color = colors.onBackground,
            fontSize = 26.sp
        )

        if (userId.isNullOrBlank()) {
            EmptyHistory(
                "Sign in to view conversations saved to your account."
            )
        } else if (loading && conversations.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (conversations.isEmpty()) {
            EmptyHistory(
                error ?: "Your saved conversations will appear here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            colors.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(
                                start = 15.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                                end = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        onOpenConversation(conversation.id)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    conversation.title,
                                    color = colors.onSurface,
                                    fontSize = 14.sp
                                )

                                Text(
                                    conversation.lastMessageAt
                                        .replace('T', ' ')
                                        .take(16),
                                    color = colors.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            repository.delete(
                                                userId,
                                                conversation.id
                                            )
                                        }.onSuccess {
                                            conversations =
                                                conversations.filterNot {
                                                    it.id == conversation.id
                                                }
                                        }.onFailure {
                                            error = it.message
                                                ?: "Unable to delete this conversation."
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete conversation",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        error?.takeIf { conversations.isNotEmpty() }?.let {
            Text(
                it,
                color = colors.error,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyHistory(message: String) {
    val colors = MaterialTheme.colorScheme

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 42.dp,
                    horizontal = 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.History,
                null,
                tint = colors.primary,
                modifier = Modifier.size(42.dp)
            )

            Text(
                "Your conversations",
                color = colors.onSurface
            )

            Text(
                message,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}
