package com.aussieweb.screenlight;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Draws a white "front light" strip on the top or bottom part of the screen and,
 * when a WhatsApp call screen opens at night, shows a draggable ON/OFF floating button.
 * Everything is drawn with accessibility overlay windows, so no "draw over other apps"
 * permission is needed.
 */
public class ScreenLightService extends AccessibilityService {

    public static final String PREFS = "cfg";
    public static final String K_POSITION = "position";       // "top" or "bottom"
    public static final String K_SIZE = "size";               // percent of screen height
    public static final String K_NIGHT_ONLY = "nightOnly";
    public static final String K_NIGHT_START = "nightStart";  // hour 0-23
    public static final String K_NIGHT_END = "nightEnd";      // hour 0-23
    public static final String K_AUTO_ON = "autoOn";

    /** Non-null while the accessibility service is running. */
    public static ScreenLightService instance;

    private WindowManager wm;
    private View lightView;
    private TextView fab;
    private WindowManager.LayoutParams fabParams;
    private boolean lightOn = false;
    private boolean inCall = false;
    private boolean lightStartedByCall = false;
    private int savedFabX = Integer.MIN_VALUE;
    private int savedFabY = 0;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgCs = event.getPackageName();
        CharSequence clsCs = event.getClassName();
        if (pkgCs == null || clsCs == null) return;
        String pkg = pkgCs.toString();
        String cls = clsCs.toString();

        // Ignore our own windows, system UI, keyboards and popups (they are not app screens).
        if (pkg.equals(getPackageName())) return;
        if (pkg.equals("com.android.systemui")) return;
        if (cls.startsWith("android.") || cls.startsWith("androidx.")) return;

        boolean whatsapp = pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b");
        boolean callScreen = whatsapp && cls.toLowerCase().contains("voip");

        if (callScreen) {
            onCallScreen();
        } else if (inCall) {
            onCallEnded();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        removeFab();
        removeLight();
        lightOn = false;
        inCall = false;
        instance = null;
    }

    // ---------------------------------------------------------------- call handling

    private void onCallScreen() {
        if (inCall) return;
        inCall = true;
        if (!nightAllowed()) return;
        showFab();
        if (prefs().getBoolean(K_AUTO_ON, false)) {
            lightStartedByCall = true;
            setLight(true);
        }
    }

    private void onCallEnded() {
        inCall = false;
        removeFab();
        if (lightStartedByCall || lightOn) {
            setLight(false);
        }
        lightStartedByCall = false;
    }

    private boolean nightAllowed() {
        SharedPreferences p = prefs();
        if (!p.getBoolean(K_NIGHT_ONLY, true)) return true;
        int start = p.getInt(K_NIGHT_START, 18);
        int end = p.getInt(K_NIGHT_END, 6);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (start == end) return true;
        if (start < end) return hour >= start && hour < end;
        return hour >= start || hour < end; // wraps past midnight
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- light strip

    public boolean isLightOn() {
        return lightOn;
    }

    public void setLight(boolean on) {
        lightOn = on;
        if (on) {
            showLight();
        } else {
            removeLight();
        }
        updateFabLabel();
    }

    /** Re-applies position/size after settings change. */
    public void refreshLight() {
        if (lightOn) showLight();
    }

    private void showLight() {
        removeLight();
        if (wm == null) return;
        SharedPreferences p = prefs();
        int percent = Math.max(10, Math.min(50, p.getInt(K_SIZE, 25)));
        boolean top = "top".equals(p.getString(K_POSITION, "bottom"));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int height = dm.heightPixels * percent / 100;

        View v = new View(this);
        v.setBackgroundColor(Color.WHITE);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.START;
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;

        try {
            wm.addView(v, lp);
            lightView = v;
        } catch (Exception ignored) {
            lightView = null;
        }
        // Keep the floating button above the light strip.
        if (fab != null) {
            removeFab();
            showFab();
        }
    }

    private void removeLight() {
        if (lightView != null && wm != null) {
            try {
                wm.removeView(lightView);
            } catch (Exception ignored) {
            }
        }
        lightView = null;
    }

    // ---------------------------------------------------------------- floating button

    private void showFab() {
        if (wm == null || fab != null) return;
        final float density = getResources().getDisplayMetrics().density;
        final int size = (int) (60 * density);

        final TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        fab = t;
        updateFabLabel();

        fabParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        fabParams.gravity = Gravity.TOP | Gravity.START;
        if (savedFabX != Integer.MIN_VALUE) {
            fabParams.x = savedFabX;
            fabParams.y = savedFabY;
        } else {
            fabParams.x = getResources().getDisplayMetrics().widthPixels - size - (int) (12 * density);
            fabParams.y = (int) (140 * density);
        }

        t.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = fabParams.x;
                        startY = fabParams.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - touchX;
                        float dy = e.getRawY() - touchY;
                        if (Math.abs(dx) > 10 * density || Math.abs(dy) > 10 * density) moved = true;
                        if (moved) {
                            fabParams.x = startX + (int) dx;
                            fabParams.y = startY + (int) dy;
                            try {
                                wm.updateViewLayout(t, fabParams);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            lightStartedByCall = false;
                            setLight(!lightOn);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            wm.addView(t, fabParams);
        } catch (Exception ignored) {
            fab = null;
        }
    }

    private void updateFabLabel() {
        if (fab == null) return;
        fab.setText(lightOn ? "ON" : "OFF");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(lightOn ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        bg.setStroke((int) (2 * getResources().getDisplayMetrics().density), Color.WHITE);
        fab.setBackground(bg);
    }

    private void removeFab() {
        if (fabParams != null && fab != null) {
            savedFabX = fabParams.x;
            savedFabY = fabParams.y;
        }
        if (fab != null && wm != null) {
            try {
                wm.removeView(fab);
            } catch (Exception ignored) {
            }
        }
        fab = null;
    }
}
