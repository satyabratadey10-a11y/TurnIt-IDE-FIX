package com.turnit.ide.ui

import android.util.Patterns
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.turnit.ide.R
import com.turnit.ide.auth.FirebaseAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authManager: FirebaseAuthManager,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(email) {
        emailError = null
        if (email.isBlank()) return@LaunchedEffect
        delay(500)
        val currentEmail = email.trim()
        if (currentEmail.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            authManager.checkEmailExists(currentEmail) { exists ->
                if (email.trim() != currentEmail) return@checkEmailExists
                emailError = if (exists) {
                    "these email is already existing,you use another email"
                } else {
                    null
                }
            }
        } else {
            emailError = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlassBackground(
                imageResId = R.drawable.bg_default,
                fallbackColor = IdeColors.Bg
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedRgbLogo()
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cloud Sign In",
                    color = IdeColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    colors = outlinedFieldColors()
                )

                if (!emailError.isNullOrBlank()) {
                    Text(
                        text = emailError.orEmpty(),
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors = outlinedFieldColors()
                )

                if (!passwordError.isNullOrBlank()) {
                    Text(
                        text = passwordError.orEmpty(),
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        val trimmedEmail = email.trim()
                        emailError = null
                        passwordError = null

                        var hasValidationError = false
                        if (trimmedEmail.isBlank()) {
                            emailError = "Enter email"
                            hasValidationError = true
                        } else if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                            emailError = "Enter a valid email"
                            hasValidationError = true
                        }
                        if (password.isBlank()) {
                            passwordError = "Enter password"
                            hasValidationError = true
                        }
                        if (!hasValidationError) {
                            isLoading = true
                            authManager.checkEmailExists(trimmedEmail) { exists ->
                                if (!exists) {
                                    isLoading = false
                                    emailError = "these gmail is not exist,you can singup or rewrite the correct email"
                                    return@checkEmailExists
                                }

                                authManager.logIn(
                                    email = trimmedEmail,
                                    password = password,
                                    onSuccess = {
                                        isLoading = false
                                        onAuthenticated()
                                    },
                                    onError = { exception ->
                                        isLoading = false
                                        passwordError = if (exception is FirebaseAuthInvalidCredentialsException) {
                                            "the password is incorrect"
                                        } else {
                                            exception.message ?: "Login failed"
                                        }
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentBlue)
                ) {
                    Text(if (isLoading) "Logging In..." else "Log In")
                }

                Button(
                    onClick = {
                        val trimmedEmail = email.trim()
                        emailError = null
                        passwordError = null

                        var hasValidationError = false
                        if (trimmedEmail.isBlank()) {
                            emailError = "Enter email"
                            hasValidationError = true
                        }
                        if (password.isBlank()) {
                            passwordError = "Enter password"
                            hasValidationError = true
                        }
                        if (!hasValidationError) {
                            authManager.checkEmailExists(trimmedEmail) { exists ->
                                if (exists) {
                                    emailError = "these email is already existing,you use another email"
                                    return@checkEmailExists
                                }

                                if (emailError.isNullOrBlank() && passwordError.isNullOrBlank()) {
                                    isLoading = true
                                    authManager.signUp(
                                        email = trimmedEmail,
                                        password = password,
                                        onSuccess = {
                                            isLoading = false
                                            onAuthenticated()
                                        },
                                        onError = { exception ->
                                            isLoading = false
                                            passwordError = exception.message ?: "Sign up failed"
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentGreen)
                ) {
                    Text("Sign Up")
                }

                Button(
                    onClick = {
                        scope.launch {
                            emailError = null
                            passwordError = null
                            isLoading = true

                            val resourceId = context.resources.getIdentifier(
                                "default_web_client_id",
                                "string",
                                context.packageName
                            )
                            val webClientId = if (resourceId != 0) {
                                context.getString(resourceId)
                            } else {
                                ""
                            }

                            if (webClientId.isBlank()) {
                                isLoading = false
                                return@launch
                            }

                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            try {
                                val result = credentialManager.getCredential(context, request)
                                val credential = result.credential
                                if (credential is CustomCredential &&
                                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                    authManager.signInWithGoogleToken(
                                        idToken = googleCredential.idToken,
                                        onSuccess = {
                                            isLoading = false
                                            onAuthenticated()
                                        },
                                        onError = { exception ->
                                            isLoading = false
                                            passwordError = exception.message ?: "Google sign-in failed"
                                        }
                                    )
                                } else {
                                    isLoading = false
                                }
                            } catch (exception: GetCredentialException) {
                                isLoading = false
                            } catch (exception: Exception) {
                                isLoading = false
                                passwordError = exception.message ?: "Google sign-in failed"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_google_logo),
                            contentDescription = "Google logo"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sign in with Google")
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedRgbLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "auth_logo_color")
    val color by infiniteTransition.animateColor(
        initialValue = Color.Red,
        targetValue = Color.Blue,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auth_logo_color_value"
    )

    Text(
        text = "TurnIt",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = IdeColors.AccentBlue,
    unfocusedBorderColor = IdeColors.Border,
    focusedTextColor = IdeColors.TextPrimary,
    unfocusedTextColor = IdeColors.TextPrimary,
    focusedLabelColor = IdeColors.AccentBlue,
    unfocusedLabelColor = IdeColors.TextSecondary,
    cursorColor = IdeColors.AccentBlue
)
