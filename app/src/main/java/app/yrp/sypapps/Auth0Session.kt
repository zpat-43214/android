package app.yrp.sypapps

import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.storage.CredentialsManager
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Holds the tokens from the native Auth0 login (LoginActivity drives WebAuthProvider; this is
 * just where the result lives afterwards). Every screen that needs to know "are we logged in"
 * or "what's the current access token" goes through here rather than caring how login happened.
 */
object Auth0Session {
    lateinit var account: Auth0
        private set

    private lateinit var credentialsManager: CredentialsManager

    fun init(ctx: Context) {
        if (::account.isInitialized) return
        account = Auth0.getInstance(BuildConfig.AUTH0_CLIENT_ID, BuildConfig.AUTH0_DOMAIN)
        credentialsManager = CredentialsManager(AuthenticationAPIClient(account), SharedPreferencesStorage(ctx))
    }

    fun save(credentials: Credentials) = credentialsManager.saveCredentials(credentials)

    fun hasValidCredentials(): Boolean = credentialsManager.hasValidCredentials()

    /** Suspends until we have a non-expired access token, transparently refreshing via the
     * stored refresh token if the old one's expired. Throws if there's no session at all, or
     * the refresh token's been revoked server-side — callers should treat that as "logged out". */
    suspend fun accessToken(): String = suspendCancellableCoroutine { cont ->
        credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) { cont.resume(result.accessToken) }
            override fun onFailure(error: CredentialsManagerException) { cont.resumeWithException(error) }
        })
    }

    fun clear() = credentialsManager.clearCredentials()
}
