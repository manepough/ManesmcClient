package com.project.manes

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MicrosoftAuthActivity : Activity() {

    companion object {
        const val EXTRA_GAMERTAG = "gamertag"
        const val EXTRA_TOKEN    = "mc_token"
        private const val CLIENT_ID = "00000000402b5328"
        private const val REDIRECT  = "https://login.live.com/oauth20_desktop.srf"
        private const val AUTH_URL  =
            "https://login.live.com/oauth20_authorize.srf" +
            "?client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&scope=service::user.auth.xboxlive.com::MBI_SSL" +
            "&redirect_uri=$REDIRECT" +
            "&display=touch"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wv = WebView(this).also { setContentView(it) }
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                if (url.startsWith(REDIRECT)) {
                    val code = req.url.getQueryParameter("code")
                    if (code != null) {
                        wv.loadUrl("about:blank")
                        Toast.makeText(this@MicrosoftAuthActivity, "Authenticating…", Toast.LENGTH_SHORT).show()
                        scope.launch { doAuth(code) }
                    } else {
                        finish()
                    }
                    return true
                }
                return false
            }
        }
        wv.loadUrl(AUTH_URL)
    }

    private suspend fun doAuth(code: String) {
        try {
            // Step 1: Microsoft token
            val msToken = postForm(
                "https://login.live.com/oauth20_token.srf",
                "client_id=$CLIENT_ID&code=$code&grant_type=authorization_code&redirect_uri=$REDIRECT"
            ).getString("access_token")

            // Step 2: Xbox Live
            val xblResp = postJson(
                "https://user.auth.xboxlive.com/user/authenticate",
                """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"$msToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
            )
            val xblToken = xblResp.getString("Token")
            val uhs = xblResp.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")

            // Step 3: XSTS
            val xstsResp = postJson(
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
            )
            val xstsToken = xstsResp.getString("Token")

            // Step 4: Minecraft
            val mcResp = postJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
            )
            val mcToken = mcResp.getString("access_token")

            // Step 5: Profile (gamertag)
            val profile = getJson(
                "https://api.minecraftservices.com/minecraft/profile",
                mcToken
            )
            val gamertag = profile.getString("name")

            withContext(Dispatchers.Main) {
                val result = Intent().apply {
                    putExtra(EXTRA_GAMERTAG, gamertag)
                    putExtra(EXTRA_TOKEN, mcToken)
                }
                setResult(RESULT_OK, result)
                finish()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MicrosoftAuthActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun postForm(url: String, body: String): JSONObject {
        val con = URL(url).openConnection() as HttpsURLConnection
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        con.doOutput = true
        OutputStreamWriter(con.outputStream).use { it.write(body) }
        return JSONObject(con.inputStream.bufferedReader().readText())
    }

    private fun postJson(url: String, body: String): JSONObject {
        val con = URL(url).openConnection() as HttpsURLConnection
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("Accept", "application/json")
        con.doOutput = true
        OutputStreamWriter(con.outputStream).use { it.write(body) }
        return JSONObject(con.inputStream.bufferedReader().readText())
    }

    private fun getJson(url: String, token: String): JSONObject {
        val con = URL(url).openConnection() as HttpsURLConnection
        con.requestMethod = "GET"
        con.setRequestProperty("Authorization", "Bearer $token")
        return JSONObject(con.inputStream.bufferedReader().readText())
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
