package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.NicoVideo
import com.example.ui.components.VideoGridCard
import com.example.ui.theme.LiveRed

const val NICO_SP_LIVE_URL = "https://sp.live.nicovideo.jp/"

@Composable
fun LiveScreen(
    livePrograms: List<NicoVideo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLiveClick: (NicoVideo) -> Unit
) {
    var forceWebPortal by remember { mutableStateOf(false) }
    var webReloadTrigger by remember { mutableIntStateOf(0) }

    val showWebPortal = forceWebPortal || (!isLoading && livePrograms.isEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LiveTv,
                        contentDescription = null,
                        tint = LiveRed
                    )
                    Text(
                        text = " ニコニコ生放送",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (livePrograms.isNotEmpty()) {
                        IconButton(onClick = { forceWebPortal = !forceWebPortal }) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "切换 sp.live.nicovideo.jp 视图",
                                tint = if (forceWebPortal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (showWebPortal) {
                            webReloadTrigger++
                        } else {
                            onRefresh()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新生放送",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && livePrograms.isEmpty() && !forceWebPortal -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                showWebPortal -> {
                    NicoLivePortalWebView(
                        reloadKey = webReloadTrigger,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(livePrograms, key = { it.contentId }) { program ->
                            VideoGridCard(
                                video = program,
                                onClick = { onLiveClick(program) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val AD_HOST_KEYWORDS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "googletagmanager.com",
    "google-analytics.com",
    "ads.nicovideo.jp",
    "ads-api.nicovideo.jp",
    "nicoad",
    "microad",
    "criteo",
    "amazon-adsystem",
    "yads.c.yimg.jp",
    "im-apps.net",
    "adnxs.com",
    "popin.cc",
    "outbrain.com",
    "taboola.com",
    "fluct.jp",
    "geniee.jp",
    "i-mobile.co.jp",
    "nend.net",
    "logly.co.jp",
    "ad-stir.com",
    "adingo.jp",
    "ladsp.com"
)

fun isAdUrl(url: String): Boolean {
    val lower = url.lowercase()
    return AD_HOST_KEYWORDS.any { lower.contains(it) } ||
        lower.contains("/ads/") ||
        lower.contains("/adbanner") ||
        lower.contains("pagead")
}

private const val AD_BLOCK_JS = """
(function() {
  if (window.__nicobiliAdBlockInstalled) return;
  window.__nicobiliAdBlockInstalled = true;
  var style = document.createElement('style');
  style.innerHTML = `
    [class*="ad-"], [class*="AdBanner"], [class*="ad_"], [id*="ad_"], [id*="google_ads"],
    [class*="nicoad"], [class*="Nicoad"], [class*="Sponsor"], [class*="sponsor"],
    [class*="AppBanner"], [class*="app-banner"], [class*="BannerAd"], [class*="PrBanner"],
    [data-ad], [data-google-query-id], ins.adsbygoogle, iframe[src*="doubleclick"],
    iframe[src*="googlesyndication"], iframe[src*="ads.nicovideo.jp"] {
      display: none !important;
      height: 0 !important;
      min-height: 0 !important;
      opacity: 0 !important;
      pointer-events: none !important;
    }
  `;
  (document.head || document.documentElement).appendChild(style);
})();
"""

fun createAdBlockingWebViewClient(): WebViewClient = object : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
        if (isAdUrl(url)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(AD_BLOCK_JS, null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NicoLivePortalWebView(
    reloadKey: Int = 0,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = createAdBlockingWebViewClient()
                loadUrl(NICO_SP_LIVE_URL)
            }
        },
        update = { webView ->
            if (reloadKey > 0 && webView.tag != reloadKey) {
                webView.tag = reloadKey
                webView.reload()
            }
        },
        modifier = modifier
    )
}
