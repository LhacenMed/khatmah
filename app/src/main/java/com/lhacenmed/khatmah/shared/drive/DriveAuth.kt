package com.lhacenmed.khatmah.shared.drive

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Obtains a short-lived OAuth2 access token from Google using a service account.
 *
 * The service account JSON is bundled at assets/service_account.json.
 * Token is cached in memory for its lifetime (~1 hour) so repeated calls
 * within a session never hit the network.
 *
 * Uses only java.security — no third-party JWT library required.
 */
object DriveAuth {

    private const val TOKEN_URL  = "https://oauth2.googleapis.com/token"
    private const val SCOPE      = "https://www.googleapis.com/auth/drive.readonly"
    private const val ASSET_PATH = "service_account.json"

    @Volatile private var cachedToken:   String = ""
    @Volatile private var tokenExpiryMs: Long   = 0L

    suspend fun token(context: Context): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedToken.isNotEmpty() && now < tokenExpiryMs - 60_000) return@withContext cachedToken

        val sa = loadServiceAccount(context)
        val jwt = buildJwt(sa)
        val resp = exchangeJwt(jwt)

        cachedToken = resp.first
        tokenExpiryMs = now + resp.second * 1000L
        cachedToken
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private data class ServiceAccount(
        val clientEmail: String,
        val privateKey:  String,
    )

    private fun loadServiceAccount(context: Context): ServiceAccount {
        val json = context.assets.open(ASSET_PATH).bufferedReader().readText()
        val obj  = JSONObject(json)
        return ServiceAccount(
            clientEmail = obj.getString("client_email"),
            privateKey  = obj.getString("private_key"),
        )
    }

    /**
     * Builds a signed JWT for the service account.
     * Header.Payload.Signature — all Base64url-encoded, signed with RS256.
     */
    private fun buildJwt(sa: ServiceAccount): String {
        val now     = System.currentTimeMillis() / 1000L
        val header  = base64url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64url("""{"iss":"${sa.clientEmail}","scope":"$SCOPE","aud":"$TOKEN_URL","iat":$now,"exp":${now + 3600}}""")
        val data    = "$header.$payload"
        val sig     = sign(data, sa.privateKey)
        return "$data.$sig"
    }

    /** Signs [data] with the RSA private key (PEM string) using SHA256withRSA. */
    private fun sign(data: String, pemKey: String): String {
        val stripped = pemKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.decode(stripped, Base64.DEFAULT)
        val key      = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val sig      = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(data.toByteArray(Charsets.UTF_8))
        }.sign()
        return Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Exchanges a signed JWT for an access token. Returns (token, expiresInSeconds). */
    private fun exchangeJwt(jwt: String): Pair<String, Long> {
        val body = "grant_type=${encode("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${encode(jwt)}"
        val conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
            outputStream.use { it.write(body.toByteArray()) }
        }
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val obj = JSONObject(resp)
        return obj.getString("access_token") to obj.getLong("expires_in")
    }

    private fun base64url(input: String): String =
        Base64.encodeToString(
            input.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}