package com.turnit.ide.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class FirebaseAuthManager(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun isAuthenticated(): Boolean = firebaseAuth.currentUser != null

    fun checkEmailExists(email: String, onResult: (Boolean) -> Unit) {
        if (email.isBlank()) {
            onResult(false)
            return
        }
        firebaseAuth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                val isNewUser = result.signInMethods?.isEmpty() ?: true
                onResult(!isNewUser)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun signUp(
        email: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                firebaseAuth.currentUser?.sendEmailVerification()
                FirebaseAuth.getInstance().signOut()
                onSuccess("Account created! Please check your email to verify before logging in.")
            }
            .addOnFailureListener { onError(it) }
    }

    fun logIn(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = firebaseAuth.currentUser
                if (user?.isEmailVerified == true) {
                    onSuccess()
                } else {
                    user?.sendEmailVerification()
                    FirebaseAuth.getInstance().signOut()
                    onError(EmailNotVerifiedException("Please verify your email. A new link has been sent."))
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun signInWithGoogleToken(
        idToken: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}

class EmailNotVerifiedException(message: String) : Exception(message)
