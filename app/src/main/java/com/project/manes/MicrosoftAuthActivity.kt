package com.project.manes

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
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
            "&display=touch" +
            "&prompt=select_account"

        // Try to silently refresh using stored refresh_token
        suspend fun tryRefresh(ctx: android.content.Context): Pair<String, String>? {
            val refreshToken = Store.loadStr(ctx, "ms_refresh_token")
            if (refreshToken.isBlank()) return null
            return try {
                val resp = postFormStatic(
                    "https://login.live.com/oauth20_token.srf",
                    "client_id=$CLIENT_ID&refresh_token=$refreshToken" +
                    "&grant_type=refresh_token&redirect_uri=$REDIRECT"
                )
                val newRefresh = if (resp.has("refresh_token")) resp.getString("refresh_token") else refreshToken
                Store.saveStr(ctx, "ms_refresh_token", newRefresh)
                val msToken = resp.getString("access_token")
                val (gamertag, mcToken) = getMinecraftTokens(msToken)
                Pair(gamertag, mcToken)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun getMinecraftTokens(msToken: String): Pair<String, String> {
            // XBL
            val xblResp = postJsonStatic(
                "https://user.auth.xboxlive.com/user/authenticate",
                """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
            )
            val xblToken = xblResp.getString("Token")
            val uhs = xblResp.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")

            // XSTS
            val xstsResp = postJsonStatic(
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
            )
            val xstsToken = xstsResp.getString("Token")
            val xui = xstsResp.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0)
            val xboxTag = when {
                xui.has("gtg") -> xui.getString("gtg")
                xui.has("agg") -> xui.getString("agg")
                else -> "Player"
            }

            // MC token
            val mcResp = postJsonStatic(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
            )
            val mcToken = mcResp.getString("access_token")

            // Try Java profile (graceful fallback)
            val finalTag = xboxTag

            return Pair(finalTag, mcToken)
        }

        private fun readResp(con: HttpsURLConnection): JSONObject {
            val code = con.responseCode
            val body = (if (code in 200..299) con.inputStream else con.errorStream)
                ?.bufferedReader()?.readText() ?: "{}"
            if (code !in 200..299) throw Exception("HTTP $code: ${body.take(300)}")
            return JSONObject(body)
        }

        fun postFormStatic(url: String, body: String): JSONObject {
            val con = URL(url).openConnection() as HttpsURLConnection
            con.requestMethod = "POST"
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            con.setRequestProperty("Accept", "application/json")
            con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 15000
            OutputStreamWriter(con.outputStream).use { it.write(body) }
            return readResp(con)
        }

        fun postJsonStatic(url: String, body: String): JSONObject {
            val con = URL(url).openConnection() as HttpsURLConnection
            con.requestMethod = "POST"
            con.setRequestProperty("Content-Type", "application/json")
            con.setRequestProperty("Accept", "application/json")
            con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 15000
            OutputStreamWriter(con.outputStream).use { it.write(body) }
            return readResp(con)
        }

        fun getJsonStatic(url: String, token: String): JSONObject {
            val con = URL(url).openConnection() as HttpsURLConnection
            con.requestMethod = "GET"
            con.setRequestProperty("Authorization", "Bearer $token")
            con.setRequestProperty("Accept", "application/json")
            con.connectTimeout = 15000; con.readTimeout = 15000
            return readResp(con)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statusText: TextView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).also { setContentView(it) }
        root.setBackgroundColor(Color.parseColor("#0D1117"))

        // Header
        val hdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding((16*density).toInt(),(12*density).toInt(),(12*density).toInt(),(12*density).toInt())
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,(48*density).toInt()).apply { gravity = Gravity.TOP }
        }
        hdr.addView(TextView(this).apply {
            text = "Add Account"; textSize = 14f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        hdr.addView(TextView(this).apply {
            text = "✕"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding((12*density).toInt(),0,(12*density).toInt(),0)
            setOnClickListener { finish() }
        })
        root.addView(hdr)

        // WebView — uses CookieManager to persist session!
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            // Enable persistent cookies so Microsoft remembers the device
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = (48*density).toInt() }
        }

        // Loading overlay
        val loading = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = (48*density).toInt() }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        inner.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams((220*density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val lbl = TextView(this).apply {
            text = "Please wait  securing your account..."; textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF")); gravity = Gravity.CENTER
            setPadding(0,(16*density).toInt(),0,0)
        }
        statusText = lbl; inner.addView(lbl); loading.addView(inner)

        root.addView(wv)
        root.addView(loading)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                if (url.startsWith(REDIRECT)) {
                    val code = req.url.getQueryParameter("code")
                    if (code != null) {
                        wv.visibility = View.GONE
                        loading.visibility = View.VISIBLE
                        // Flush cookies so next login is instant
                        CookieManager.getInstance().flush()
                        scope.launch { doAuth(code) }
                    } else finish()
                    return true
                }
                return false
            }
        }
        wv.loadUrl(AUTH_URL)
    }

    private suspend fun doAuth(code: String) {
        try {
            val msResp = postFormStatic(
                "https://login.live.com/oauth20_token.srf",
                "client_id=$CLIENT_ID&code=$code&grant_type=authorization_code&redirect_uri=$REDIRECT"
            )
            val msToken = msResp.getString("access_token")
            // Save refresh token — next login will be silent!
            if (msResp.has("refresh_token")) {
                Store.saveStr(this, "ms_refresh_token", msResp.getString("refresh_token"))
            }
            val (gamertag, mcToken) = getMinecraftTokens(msToken)
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK, Intent().apply {
                    putExtra(EXTRA_GAMERTAG, gamertag)
                    putExtra(EXTRA_TOKEN, mcToken)
                })
                finish()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MicrosoftAuthActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELED); finish()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
