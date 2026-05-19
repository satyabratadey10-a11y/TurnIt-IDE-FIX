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
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun logIn(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
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
