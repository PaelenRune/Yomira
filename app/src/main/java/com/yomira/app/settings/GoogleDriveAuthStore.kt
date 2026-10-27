package com.yomira.app.settings

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

interface DriveAccountAuth {
    val email: StateFlow<String?>
    fun signInIntent(): Intent
    fun completeSignIn(data: Intent?): Boolean
    fun disconnect(onComplete: (Boolean) -> Unit)
    fun clearConnection()
}

/** Persists account identity only. Google Play services owns consent and short-lived tokens. */
@Suppress("DEPRECATION")
@Singleton
class GoogleDriveAuthStore @Inject constructor(@ApplicationContext private val context: Context) : DriveAccountAuth {
    private val preferences = context.getSharedPreferences("google_drive_auth", Context.MODE_PRIVATE)
    private val scope = Scope("https://www.googleapis.com/auth/drive.appdata")
    private val client = GoogleSignIn.getClient(context, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail().requestScopes(scope).build())
    private val mutableEmail = MutableStateFlow(restoredEmail())
    override val email = mutableEmail.asStateFlow()

    private fun restoredEmail(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return account.email?.takeIf {
            it == preferences.getString("email", null) && GoogleSignIn.hasPermissions(account, scope)
        }
    }

    override fun signInIntent(): Intent = client.signInIntent

    /** A cancelled or failed consent flow never establishes a local connection. */
    override fun completeSignIn(data: Intent?): Boolean {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)
        val email = account.email ?: return false
        if (!GoogleSignIn.hasPermissions(account, scope)) return false
        preferences.edit().putString("email", email).apply()
        mutableEmail.value = email
        return true
    }

    override fun disconnect(onComplete: (Boolean) -> Unit) {
        // Gate workers immediately, even if Play services is unavailable or revocation fails.
        preferences.edit().clear().apply()
        mutableEmail.value = null
        client.revokeAccess().addOnCompleteListener { revoke ->
            client.signOut().addOnCompleteListener { onComplete(revoke.isSuccessful && it.isSuccessful) }
        }
    }

    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val expected = mutableEmail.value ?: return@withContext null
        val signedIn = GoogleSignIn.getLastSignedInAccount(context)
        if (signedIn?.email != expected || !GoogleSignIn.hasPermissions(signedIn, scope)) {
            clearConnection()
            return@withContext null
        }
        try {
            val account = signedIn.account ?: return@withContext null
            GoogleAuthUtil.getToken(context, account, "oauth2:${scope.scopeUri}")
                .takeIf { mutableEmail.value == expected }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: UserRecoverableAuthException) {
            clearConnection()
            null
        }
    }

    override fun clearConnection() {
        preferences.edit().clear().apply()
        mutableEmail.value = null
    }
}
