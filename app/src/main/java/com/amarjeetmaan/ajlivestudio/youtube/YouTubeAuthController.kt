package com.amarjeetmaan.ajlivestudio.youtube

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Sign-In scoped for YouTube, using the long-established
 * GoogleSignInClient + GoogleAuthUtil.getToken() flow (play-services-auth).
 *
 * Google has been pushing newer Credential Manager APIs, but for scoped
 * OAuth access-token retrieval like this, the classic flow is what I have
 * high confidence in getting right without a compile-test — it's been
 * stable for years and is still fully supported.
 *
 * PREREQUISITE (you have to do this in Google Cloud Console, not code):
 * 1. Create/select a project, enable "YouTube Data API v3"
 * 2. Create an OAuth 2.0 Client ID of type "Android"
 * 3. Register the app's package name (com.amarjeetmaan.ajlivestudio) and
 *    its signing certificate's SHA-1 fingerprint
 * 4. Configure the OAuth consent screen (can stay in "Testing" mode with
 *    your own Google account added as a test user — no Google review
 *    needed just for your own use)
 * Without this setup, sign-in will fail with a developer-console error
 * regardless of whether this code is correct.
 */
class YouTubeAuthController(private val context: Context) {

    companion object {
        const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube"
    }

    private val signInClient: GoogleSignInClient

    init {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(YOUTUBE_SCOPE))
            .build()
        signInClient = GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(): Intent = signInClient.signInIntent

    fun lastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> =
        runCatching {
            com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(data)
                .getResult(com.google.android.gms.common.api.ApiException::class.java)
        }.recoverCatching { throwable ->
            val apiException = throwable as? com.google.android.gms.common.api.ApiException
            val message = if (apiException != null) {
                "Google Sign-In failed (code ${apiException.statusCode}). " +
                    when (apiException.statusCode) {
                        10 -> "This is DEVELOPER_ERROR. Package=${contextPackageName()}; APK SHA-1=${signingSha1()}. Register this exact package name + SHA-1 on the Android OAuth client in Google Cloud Console."
                        12501 -> "Sign-in was cancelled."
                        else -> "Check Google Cloud Console OAuth setup — see README."
                    }
            } else {
                "Google Sign-In failed: ${throwable.message}"
            }
            throw Exception(message)
        }

    fun signOut() {
        signInClient.signOut()
    }

    private fun contextPackageName(): String =
        context.packageName

    private fun signingSha1(): String {
        return runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            MessageDigest.getInstance("SHA-1")
                .digest(signatures.first().toByteArray())
                .joinToString(":") { "%02X".format(it) }
        }.getOrElse { "unavailable" }
    }

    /**
     * Fetches a fresh OAuth access token for the YouTube scope. Must be
     * called off the main thread — GoogleAuthUtil.getToken() blocks on
     * network/AccountManager I/O.
     */
    suspend fun fetchAccessToken(activity: Activity, account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            val email = account.email ?: return@withContext null
            runCatching {
                GoogleAuthUtil.getToken(activity, Account(email, "com.google"), "oauth2:$YOUTUBE_SCOPE")
            }.getOrNull()
        }
}
