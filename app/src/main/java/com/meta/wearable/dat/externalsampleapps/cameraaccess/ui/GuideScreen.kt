package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows one of the bundled help guides (assets/guide_*.html) inside a WebView.
 *
 * The guides are the very same trilingual illustrated pages that live on the
 * public site — shipped inside the APK so a user can open them offline from a
 * button, no browser or internet needed. They are self-contained (all CSS/JS/
 * images inlined) and switch language themselves via the tabs at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(assetFile: String, title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Back walks the WebView history first (the install guide links to the
    // key guide); only from the first page does it close the screen
    // (pregled 11).
    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient() // keep navigation inside the WebView
                    settings.javaScriptEnabled = true // the language switcher needs it
                    settings.domStorageEnabled = true // remembers the chosen language
                    loadUrl("file:///android_asset/$assetFile")
                    webView = this
                }
            },
            onRelease = { wv ->
                // WebView is the one Android view that must be destroyed by
                // hand or it leaks its renderer process (pregled 11).
                if (webView === wv) webView = null
                wv.destroy()
            },
        )
    }
}
