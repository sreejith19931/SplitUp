package com.finance.splitup.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data object LocalDemo : AuthState
    data class EmailVerificationPending(val user: FirebaseUser) : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth? = try {
        if (FirebaseApp.getApps(application).isNotEmpty()) {
            FirebaseAuth.getInstance()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private val currentUser: FirebaseUser?
        get() = try { auth?.currentUser } catch (_: Exception) { null }

    private val _authState = MutableStateFlow<AuthState>(
        try {
            val user = currentUser
            if (user != null) {
                if (user.isEmailVerified) {
                    AuthState.Authenticated(user)
                } else {
                    AuthState.EmailVerificationPending(user)
                }
            } else {
                AuthState.Unauthenticated
            }
        } catch (_: Exception) {
            AuthState.Unauthenticated
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    var errorMessage by mutableStateOf<String?>(
        if (auth == null) "Firebase is not configured." else null
    )
        private set

    var verificationMessage by mutableStateOf<String?>(null)
        private set

    var isResendingEmail by mutableStateOf(false)
        private set

    private var emailPollingJob: kotlinx.coroutines.Job? = null

    fun clearError() {
        errorMessage = null
    }

    fun setCustomError(msg: String) {
        errorMessage = msg
    }

    fun enterGuestMode() {
        errorMessage = null
        _authState.value = AuthState.LocalDemo
    }

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    fun startEmailVerificationPolling() {
        emailPollingJob?.cancel()
        emailPollingJob = viewModelScope.launch {
            while (_authState.value is AuthState.EmailVerificationPending) {
                kotlinx.coroutines.delay(4000L)
                try {
                    val user = currentUser
                    user?.reload()?.await()
                    if (user != null && user.isEmailVerified) {
                        _authState.value = AuthState.Authenticated(user)
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stopEmailVerificationPolling() {
        emailPollingJob?.cancel()
        emailPollingJob = null
    }

    fun signInWithGoogleToken(idToken: String) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            errorMessage = "Firebase is not configured."
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    val app = getApplication<Application>()
                    app.getSharedPreferences("splitup_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("last_user_email", user.email).apply()
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userMap = mutableMapOf<String, Any>("lastActive" to java.util.Date())
                        user.email?.let { userMap["email"] = it }
                        user.displayName?.let { userMap["name"] = it }
                        db.collection("users").document(user.uid).set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (_: Exception) {}
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Google sign in failed"
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        if (!isValidEmail(email)) {
            errorMessage = "Please enter a valid email address"
            return
        }
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            errorMessage = "Firebase is not configured."
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), pass).await()
                val user = result.user
                if (user != null) {
                    val app = getApplication<Application>()
                    app.getSharedPreferences("splitup_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("last_user_email", user.email).apply()
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userMap = mutableMapOf<String, Any>("lastActive" to java.util.Date())
                        user.email?.let { userMap["email"] = it }
                        user.displayName?.let { userMap["name"] = it } ?: run {
                            user.email?.substringBefore("@")?.let { userMap["name"] = it }
                        }
                        db.collection("users").document(user.uid).set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (_: Exception) {}
                    if (user.isEmailVerified) {
                        _authState.value = AuthState.Authenticated(user)
                    } else {
                        _authState.value = AuthState.EmailVerificationPending(user)
                    }
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Sign in failed"
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, displayName: String = "") {
        if (email.isBlank() || pass.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        if (!isValidEmail(email)) {
            errorMessage = "Please enter a valid email address"
            return
        }
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            errorMessage = "Firebase is not configured."
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), pass).await()
                val user = result.user
                if (user != null) {
                    val nameToSave = if (displayName.isNotBlank()) displayName else email.substringBefore("@")
                    if (displayName.isNotBlank()) {
                        val profileUpdates = userProfileChangeRequest {
                            this.displayName = displayName
                        }
                        user.updateProfile(profileUpdates).await()
                    }
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userMap = mutableMapOf<String, Any>(
                            "lastActive" to java.util.Date(),
                            "createdAt" to java.util.Date(),
                            "defaultCurrency" to "EUR"
                        )
                        user.email?.let { userMap["email"] = it }
                        userMap["name"] = nameToSave
                        db.collection("users").document(user.uid).set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (_: Exception) {}
                    user.sendEmailVerification().await()
                    _authState.value = AuthState.EmailVerificationPending(user)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: FirebaseAuthUserCollisionException) {
                errorMessage = "An account already exists with $email. If you previously signed in using Google, please tap 'Sign in with Google'."
                _authState.value = AuthState.Unauthenticated
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Sign up failed"
                if (msg.contains("already in use", ignoreCase = true) || msg.contains("collision", ignoreCase = true) || msg.contains("exists", ignoreCase = true)) {
                    errorMessage = "An account already exists with $email. If you signed up using Google, please tap 'Sign in with Google'."
                } else {
                    errorMessage = msg
                }
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun resendEmailVerification() {
        val user = currentUser
        if (user == null) {
            verificationMessage = "No user logged in."
            return
        }
        viewModelScope.launch {
            isResendingEmail = true
            verificationMessage = null
            try {
                user.reload().await()
                user.sendEmailVerification().await()
                verificationMessage = "Verification email sent! Check your inbox (and spam folder)."
            } catch (e: com.google.firebase.FirebaseTooManyRequestsException) {
                verificationMessage = "Please wait a moment before requesting another email (rate-limited by Firebase)."
            } catch (e: Exception) {
                verificationMessage = e.localizedMessage ?: "Failed to resend email."
            } finally {
                isResendingEmail = false
            }
        }
    }

    fun checkEmailVerificationStatus() {
        viewModelScope.launch {
            verificationMessage = null
            try {
                val user = currentUser
                user?.reload()?.await()
                if (user != null && user.isEmailVerified) {
                    _authState.value = AuthState.Authenticated(user)
                } else if (user != null) {
                    verificationMessage = "Email not verified yet. Please check your inbox and click the verification link."
                    _authState.value = AuthState.EmailVerificationPending(user)
                }
            } catch (e: Exception) {
                verificationMessage = e.localizedMessage ?: "Failed to check verification status"
            }
        }
    }

    fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            errorMessage = "Firebase is not configured."
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = firebaseAuth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Google sign in failed"
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun deleteAccount(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = currentUser
        if (user == null) {
            onError("No logged in user found.")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val uid = user.uid
                com.finance.splitup.data.CloudSyncManager.deleteUserCloudData(uid)
                com.finance.splitup.data.CloudSyncManager.stopListeningForIncomingNotifications()
                try {
                    val app = getApplication<Application>()
                    val database = com.finance.splitup.data.db.AppDatabase.getDatabase(app)
                    database.financeDao().clearAllLocalData()
                } catch (e: Exception) {
                    android.util.Log.e("AuthViewModel", "Failed to clear local DB on account deletion", e)
                }
                user.delete().await()
                _authState.value = AuthState.Unauthenticated
                onSuccess()
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                onError("Security requirement: Account deletion requires a recent sign-in. Please sign out, sign back in, and try again.")
                _authState.value = AuthState.Authenticated(user)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Account deletion failed. Please try again.")
                _authState.value = AuthState.Authenticated(user)
            }
        }
    }

    fun updateDisplayName(newName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = currentUser
        if (user == null) {
            onError("No user logged in")
            return
        }
        viewModelScope.launch {
            try {
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = newName
                }
                user.updateProfile(profileUpdates).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to update display name")
            }
        }
    }

    fun sendPasswordResetEmail(
        email: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank()) {
            onError("Please enter your email address above first")
            return
        }
        viewModelScope.launch {
            try {
                auth?.sendPasswordResetEmail(email)?.await()
                onSuccess("Password reset email sent to $email. Please check your inbox.")
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to send password reset email")
            }
        }
    }

    fun updatePassword(
        newPassword: String,
        sendEmailNotification: Boolean = true,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = currentUser
        if (user == null) {
            onError("No user logged in")
            return
        }
        viewModelScope.launch {
            try {
                user.updatePassword(newPassword).await()
                val userEmail = user.email
                if (sendEmailNotification && !userEmail.isNullOrBlank()) {
                    try {
                        auth?.sendPasswordResetEmail(userEmail)?.await()
                    } catch (_: Exception) {}
                }
                onSuccess()
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                onError("Recent login required. Please sign out and sign back in before changing password.")
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to update password")
            }
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val database = com.finance.splitup.data.db.AppDatabase.getDatabase(app)
                val repository = com.finance.splitup.data.repository.OfflineFirstFinanceRepository(database.financeDao())
                com.finance.splitup.data.CloudSyncManager.performCloudSync(repository, app)
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Failed final sync on sign out", e)
            } finally {
                try {
                    stopEmailVerificationPolling()
                    com.finance.splitup.data.CloudSyncManager.stopListeningForIncomingNotifications()
                } catch (_: Exception) {}
                try {
                    val app = getApplication<Application>()
                    val database = com.finance.splitup.data.db.AppDatabase.getDatabase(app)
                    database.financeDao().clearAllLocalData()
                } catch (e: Exception) {
                    android.util.Log.e("AuthViewModel", "Failed to clear local DB on sign out", e)
                }
                try {
                    auth?.signOut()
                } catch (_: Exception) {}
                _authState.value = AuthState.Unauthenticated
                onComplete()
            }
        }
    }
}
