
package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType

@Composable
fun AccountScreen(
    account: Account?,
    busy: Boolean,
    message: String?,
    onEmailSignIn: (String, String) -> Unit,
    onEmailSignUp: (String, String, String) -> Unit,
    onPasswordRecovery: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onVerifyMasterclass: (String) -> Unit,
    onGoToSignIn: () -> Unit
) {
    var isRegistering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var whatsappNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "JARVIS Account",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Your account keeps your learning experience connected.",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        if (account != null) {

            if (account.id == "pending-email-verification") {

                Text(
                    text = "Registration submitted",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "A verification email has been requested for ${account.email.orEmpty()}. " +
                        "Check your inbox before attempting to sign in."
                )

                OutlinedButton(
                    onClick = {
                        isRegistering = false
                        password = ""
                        passwordVisible = false
                        onGoToSignIn()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to sign in")
                }

            } else {

                Text(
                    text = "Welcome, ${account.displayName}",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = account.email.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Account plan: ${account.type.displayName()}",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = onSignOut,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign out")
                }

                HorizontalDivider()

                Text(
                    text = "Flexi Masterclass",
                    style = MaterialTheme.typography.titleLarge
                )

                if (account.isFlexiMasterclassLinked) {

                    Text(
                        text = "Your existing Flexi Masterclass account is verified."
                    )

                } else {

                    Text(
                        text = "Already registered on Flexi Lessons? " +
                            "Verify your existing registration here."
                    )

                    Text(
                        text = "Use the same WhatsApp number you provided " +
                            "when registering on Flexi Lessons. Your JARVIS " +
                            "sign-in email will be checked automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = whatsappNumber,
                        onValueChange = {
                            whatsappNumber = it
                        },
                        label = {
                            Text("Flexi Lessons WhatsApp number")
                        },
                        placeholder = {
                            Text("+2348012345678")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onVerifyMasterclass(
                                whatsappNumber.trim()
                            )
                        },
                        enabled = !busy &&
                            whatsappNumber.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify Flexi Masterclass")
                    }

                    Text(
                        text = "Only an existing registration with verified " +
                            "payment and an active subscription can qualify. " +
                            "No new Masterclass account is created.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

        } else {

            Text(
                text = if (isRegistering) {
                    "Create your JARVIS account"
                } else {
                    "Sign in to JARVIS"
                },
                style = MaterialTheme.typography.titleLarge
            )

            if (isRegistering) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            passwordVisible = !passwordVisible
                        },
                        enabled = !busy
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy
            )

            if (isRegistering) {
                Text(
                    text = "Password must contain at least 8 characters.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    if (isRegistering) {
                        onEmailSignUp(
                            name.trim(),
                            email.trim(),
                            password
                        )
                    } else {
                        onEmailSignIn(
                            email.trim(),
                            password
                        )
                    }
                },
                enabled = !busy &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    (!isRegistering ||
                        (name.isNotBlank() && password.length >= 8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isRegistering) {
                        "Create account"
                    } else {
                        "Sign in"
                    }
                )
            }

            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue with Google")
            }

            OutlinedButton(
                onClick = {
                    onPasswordRecovery(email.trim())
                },
                enabled = !busy && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Forgot password?")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isRegistering) {
                    "Already have an account?"
                } else {
                    "New to JARVIS?"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    isRegistering = !isRegistering
                    name = ""
                    password = ""
                    passwordVisible = false
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isRegistering) {
                        "Go to sign in"
                    } else {
                        "Create a new account"
                    }
                )
            }
        }

        if (busy) {
            CircularProgressIndicator()
        }

        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun AccountType.displayName(): String {
    return when (this) {
        AccountType.FREE -> "Free"
        AccountType.JARVIS_PLUS -> "Jarvis Plus"
        AccountType.JARVIS_PRO -> "Jarvis Pro"
        AccountType.FLEXI_MASTERCLASS -> "Flexi Masterclass"
    }
}
