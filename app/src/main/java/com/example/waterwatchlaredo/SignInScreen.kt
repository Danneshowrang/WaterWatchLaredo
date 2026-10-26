package com.example.waterwatchlaredo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Reading Map and Community stays public; only the Report tab passes through this gate.
@Composable
fun AccountReportScreen(
    reportDao: ReportDao,
    modifier: Modifier = Modifier,
    onViewMap: (Double, Double) -> Unit = { _, _ -> },
    onViewCommunity: () -> Unit = {}
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(auth.currentUser) }
    var emailVerified by remember { mutableStateOf(auth.currentUser?.isEmailVerified == true) }
    var message by remember { mutableStateOf("") }
    var checkingEmail by remember { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { updatedAuth ->
            user = updatedAuth.currentUser
            emailVerified = user?.isEmailVerified == true
            message = ""
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Column(modifier.fillMaxSize()) {
        if (user != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Account signed in", modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = {
                    auth.signOut()
                    // Clear chooser state so the next person can pick their own account.
                    scope.launch {
                        try {
                            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: androidx.credentials.exceptions.ClearCredentialException) {
                            // Firebase is already signed out even if chooser cleanup fails.
                        }
                    }
                }) { Text("Sign out") }
            }
        }
        when {
            user == null -> SignInScreen(auth, Modifier.weight(1f))
            !emailVerified -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Verify your email", style = MaterialTheme.typography.headlineSmall)
                Text("Open the verification link in your inbox, then return here. " +
                    "This checks that you control the address; it does not verify your water reports.")
                Text("Verification address: ${user?.email.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !checkingEmail, onClick = {
                    checkingEmail = true
                    user?.sendEmailVerification()?.addOnCompleteListener { task ->
                        checkingEmail = false
                        message = if (task.isSuccessful) "Verification email sent. Check your inbox and spam folder."
                            else "Could not send the email. Wait a moment and try again."
                    }
                }) { Text("Send verification email") }
                Button(enabled = !checkingEmail, onClick = {
                    checkingEmail = true
                    user?.reload()?.addOnCompleteListener { task ->
                        checkingEmail = false
                        user = auth.currentUser
                        emailVerified = user?.isEmailVerified == true
                        message = when {
                            !task.isSuccessful -> "Could not check verification. Try again."
                            !emailVerified -> "Email is not verified yet. Open the link first."
                            else -> ""
                        }
                    }
                }) { Text(if (checkingEmail) "Checking…" else "I've verified my email") }
                if (message.isNotEmpty()) Text(message)
            }
            else -> ReportForm(reportDao, Modifier.weight(1f), onViewMap, onViewCommunity)
        }
    }
}

@Composable
private fun SignInScreen(auth: FirebaseAuth, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentials = remember(context) { CredentialManager.create(context) }
    // Keep passwords only in this screen's memory, never in saved state, Room, or logs.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (creatingAccount) "Create your account" else "Sign in to report",
            style = MaterialTheme.typography.headlineMedium)
        Text("Help document water issues in Laredo. The map and Community board are available without signing in.")
        OutlinedButton(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = {
                busy = true
                message = ""
                scope.launch {
                    try {
                        val googleOption = GetGoogleIdOption.Builder()
                            .setServerClientId(context.getString(R.string.default_web_client_id))
                            .setFilterByAuthorizedAccounts(false)
                            .setAutoSelectEnabled(false)
                            .build()
                        val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                        val result = credentials.getCredential(context, request)
                        val credential = result.credential
                        if (credential !is CustomCredential ||
                            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            message = "Google did not return a supported sign-in credential."
                            busy = false
                        } else {
                            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                                .addOnCompleteListener { task ->
                                    busy = false
                                    if (!task.isSuccessful) message = "Google sign-in could not finish. Please try again."
                                }
                        }
                    } catch (error: GetCredentialCancellationException) {
                        busy = false
                        message = "Google sign-in was cancelled."
                    } catch (error: NoCredentialException) {
                        busy = false
                        message = "No Google account is available. Add one in Android Settings, or use email below."
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: GetCredentialException) {
                        busy = false
                        message = "Google sign-in is unavailable. Try again or use email below."
                    } catch (error: Exception) {
                        busy = false
                        message = "Could not complete Google sign-in. Please try again."
                    }
                }
            }
        ) { Text("Continue with Google") }

        HorizontalDivider()
        Text("Or use your email", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = email, onValueChange = { email = it; message = "" },
            label = { Text("Email") }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it; message = "" },
            label = { Text("Password") }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (creatingAccount) Text("Use at least 6 characters. You'll verify your email before reporting.",
            style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = {
                busy = true
                message = ""
                val task = if (creatingAccount) auth.createUserWithEmailAndPassword(email.trim(), password)
                    else auth.signInWithEmailAndPassword(email.trim(), password)
                task.addOnCompleteListener { result ->
                    busy = false
                    if (result.isSuccessful) {
                        password = ""
                        // The verification screen lets the user request the email explicitly.
                    } else {
                        message = when (result.exception) {
                            is FirebaseAuthWeakPasswordException -> "Choose a stronger password and try again."
                            is FirebaseAuthUserCollisionException -> "An account uses this email. Try signing in with your original method."
                            is FirebaseAuthInvalidCredentialsException -> "The email or password was not accepted. Please check them."
                            else -> "Could not complete sign-in. Check your connection and try again."
                        }
                    }
                }
            }
        ) {
            Text(if (busy) "Please wait…" else if (creatingAccount) "Create account" else "Sign in")
        }
        TextButton(enabled = !busy, onClick = {
            creatingAccount = !creatingAccount
            password = ""
            message = ""
        }) { Text(if (creatingAccount) "Already have an account? Sign in" else "New here? Create an account") }
        if (message.isNotEmpty()) Text(message)
        Text("Accounts are connected to Firebase. Water reports still stay on this device until cloud syncing is added.",
            style = MaterialTheme.typography.bodySmall)
    }
}
