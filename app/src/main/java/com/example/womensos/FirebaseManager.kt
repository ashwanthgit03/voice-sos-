package com.example.womensos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {
    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    fun register(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "Registration successful")
                } else {
                    onResult(
                        false,
                        task.exception?.localizedMessage
                            ?: "Registration failed"
                    )
                }
            }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "Login successful")
                } else {
                    onResult(
                        false,
                        task.exception?.localizedMessage
                            ?: "Login failed"
                    )
                }
            }
    }

    fun logout() {
        auth.signOut()
    }
}