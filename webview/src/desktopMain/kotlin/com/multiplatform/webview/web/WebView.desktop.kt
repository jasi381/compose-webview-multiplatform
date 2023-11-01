package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import org.cef.browser.CefRendering
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.resource

/**
 * Desktop WebView implementation.
 */
@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    DesktopWebView(
        state,
        modifier,
        navigator,
        onCreated = onCreated,
        onDispose = onDispose,
    )
}

/**
 * Desktop WebView implementation.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun DesktopWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    val currentOnDispose by rememberUpdatedState(onDispose)
    val client =
        remember(state.webSettings.desktopWebSettings.disablePopupWindows) {
            KCEF.newClientOrNullBlocking()?.also {
                if (state.webSettings.desktopWebSettings.disablePopupWindows) {
                    it.addLifeSpanHandler(DisablePopupWindowsLifeSpanHandler())
                } else {
                    if (it.getLifeSpanHandler() is DisablePopupWindowsLifeSpanHandler) {
                        it.removeLifeSpanHandler()
                    }
                }
            }
        }
    val scope = rememberCoroutineScope()
    val fileContent by produceState("", state.content) {
        value =
            if (state.content is WebContent.File) {
                val res = resource("assets/${(state.content as WebContent.File).fileName}")
                res.readBytes().decodeToString().trimIndent()
            } else {
                ""
            }
    }

    val browser: KCEFBrowser? =
        remember(
            client,
            state.webSettings.desktopWebSettings.offScreenRendering,
            state.webSettings.desktopWebSettings.transparent,
            fileContent,
        ) {
            val rendering =
                if (state.webSettings.desktopWebSettings.offScreenRendering) {
                    CefRendering.OFFSCREEN
                } else {
                    CefRendering.DEFAULT
                }

            when (val current = state.content) {
                is WebContent.Url ->
                    client?.createBrowser(
                        current.url,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                is WebContent.Data ->
                    client?.createBrowserWithHtml(
                        current.data,
                        current.baseUrl ?: KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                is WebContent.File ->
                    client?.createBrowserWithHtml(
                        fileContent,
                        KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                else -> {
                    client?.createBrowser(
                        KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )
                }
            }
        }?.also {
            state.webView = DesktopWebView(it, scope, state.jsBridge)
        }

    browser?.let {
        SwingPanel(
            factory = {
                browser.apply {
                    addDisplayHandler(state)
                    addLoadListener(state, navigator)
                }
                onCreated()
                browser.uiComponent
            },
            modifier = modifier,
        )
    }

    // Handle navigation events. Workaround for navigator not working issue.
    LaunchedEffect(state.webView, navigator) {
        state.webView?.let { wv ->
            with(navigator) {
                wv.handleNavigationEvents()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            client?.dispose()
            currentOnDispose()
        }
    }
}
