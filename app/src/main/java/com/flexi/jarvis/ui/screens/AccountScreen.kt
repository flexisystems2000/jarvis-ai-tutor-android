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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flexi.jarvis.data.model.Account
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    account: Account?,
    busy: Boolean,
    message: String?,
    onEmailSignIn: (String, String) -> Unit,
    onEmailSignUp: (String, String, String) -> Unit,
    onPasswordRecovery: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onMasterclassLink: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var isRegistering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var masterclassToken by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your JARVIS account", style = MaterialTheme.typography.headlineSmall)
        if (account != null) {
            Text("Signed in as ${account.displayName}", style = MaterialTheme.typography.titleMedium)
            Text(account.email.orEmpty())
            Text(if (account.isFlexiMasterclassLinked) "Flexi Masterclass verified" else "Free account")
            Button(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
            Spacer(Modifier.height(8.dp))
            Text("Link existing Flexi Masterclass account", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = masterclassToken, onValueChange = { masterclassToken = it },
                label = { Text("Flexi verification code") }, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onMasterclassLink(masterclassToken) },
                enabled = !busy && masterclassToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Verify Flexi Masterclass account") }
        } else {
            Text(
                if (isRegistering) "Create your account" else "Sign in to continue",
                style = MaterialTheme.typography.titleMedium
            )
            if (isRegistering) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                email, { email = it }, label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(
                onClick = {
                    if (isRegistering) onEmailSignUp(name, email, password)
                    else onEmailSignIn(email, password)
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank() &&
                    (!isRegistering || name.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isRegistering) "Create account" else "Sign in with email") }

            OutlinedButton(
                onClick = onGoogleSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { Text("Continue with Google") }

            OutlinedButton(
                onClick = { onPasswordRecovery(email) },
                enabled = !busy && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Forgot password? Send recovery email") }

            Text(
                if (isRegistering) "Already registered? Sign in"
                else "New to JARVIS? Create an account",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = { isRegistering = !isRegistering }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isRegistering) "Go to sign in" else "Create new account")
            }
        }

        if (busy) CircularProgressIndicator()
        if (!message.isNullOrBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        Text(
            "Flexi Masterclass access is granted only after the existing Flexi Lessons registration is verified by its secure server. A JARVIS sign-up alone never grants Masterclass benefits.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
