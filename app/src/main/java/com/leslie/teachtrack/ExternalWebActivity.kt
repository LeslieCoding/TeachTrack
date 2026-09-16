package com.leslie.teachtrack

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** In-app fallback for devices without a browser Activity (common on debloated phones). */
class ExternalWebActivity : Activity() {
    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) { finish(); return }
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handle(request.url)
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handle(Uri.parse(url))
                private fun handle(uri: Uri): Boolean {
                    if (uri.scheme == "teachtrack") {
                        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
                        finish()
                        return true
                    }
                    return false
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(url)
    }

    companion object {
        const val EXTRA_URL = "external_url"
    }
}
