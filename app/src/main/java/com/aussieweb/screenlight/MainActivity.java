package com.aussieweb.screenlight;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private TextView status;
    private Button toggleLight;
    private TextView sizeLabel, startLabel, endLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ScreenLightService.PREFS, MODE_PRIVATE);

        final float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Call Light");
        title.setTextSize(26);
        title.setTextColor(Color.parseColor("#0B1F4B"));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Turns part of your screen white as a front light for night video calls.");
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 0, 0, (int) (8 * d));
        root.addView(status);

        Button enable = new Button(this);
        enable.setText("1. Turn on service (Accessibility settings)");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(MainActivity.this,
                        "Open Installed apps > Call Light > turn it ON", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(enable);

        Button appInfo = new Button(this);
        appInfo.setText("If switch is greyed out: allow restricted settings");
        appInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
                Toast.makeText(MainActivity.this,
                        "Tap the 3-dot menu > Allow restricted settings", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(appInfo);

        toggleLight = new Button(this);
        toggleLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ScreenLightService s = ScreenLightService.instance;
                if (s == null) {
                    Toast.makeText(MainActivity.this,
                            "Turn on the service first (step 1)", Toast.LENGTH_LONG).show();
                    return;
                }
                s.setLight(!s.isLightOn());
                refreshStatus();
            }
        });
        root.addView(toggleLight);

        root.addView(heading("Light position", d));
        RadioGroup pos = new RadioGroup(this);
        pos.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton top = new RadioButton(this);
        top.setText("Top");
        top.setId(View.generateViewId());
        RadioButton bottom = new RadioButton(this);
        bottom.setText("Bottom");
        bottom.setId(View.generateViewId());
        pos.addView(top);
        pos.addView(bottom);
        final int topId = top.getId();
        if ("top".equals(prefs.getString(ScreenLightService.K_POSITION, "bottom"))) {
            top.setChecked(true);
        } else {
            bottom.setChecked(true);
        }
        pos.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit().putString(ScreenLightService.K_POSITION,
                        checkedId == topId ? "top" : "bottom").apply();
                applyToService();
            }
        });
        root.addView(pos);

        sizeLabel = new TextView(this);
        sizeLabel.setPadding(0, (int) (12 * d), 0, 0);
        root.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(40); // 10% .. 50%
        size.setProgress(prefs.getInt(ScreenLightService.K_SIZE, 25) - 10);
        updateSizeLabel(prefs.getInt(ScreenLightService.K_SIZE, 25));
        size.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void changed(int value) {
                int percent = value + 10;
                prefs.edit().putInt(ScreenLightService.K_SIZE, percent).apply();
                updateSizeLabel(percent);
                applyToService();
            }
        });
        root.addView(size);

        root.addView(heading("Night rules", d));
        CheckBox nightOnly = new CheckBox(this);
        nightOnly.setText("Show floating button only at night");
        nightOnly.setChecked(prefs.getBoolean(ScreenLightService.K_NIGHT_ONLY, true));
        nightOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(ScreenLightService.K_NIGHT_ONLY, checked).apply();
            }
        });
        root.addView(nightOnly);

        startLabel = new TextView(this);
        root.addView(startLabel);
        SeekBar start = new SeekBar(this);
        start.setMax(23);
        start.setProgress(prefs.getInt(ScreenLightService.K_NIGHT_START, 18));
        updateStartLabel(prefs.getInt(ScreenLightService.K_NIGHT_START, 18));
        start.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void changed(int value) {
                prefs.edit().putInt(ScreenLightService.K_NIGHT_START, value).apply();
                updateStartLabel(value);
            }
        });
        root.addView(start);

        endLabel = new TextView(this);
        root.addView(endLabel);
        SeekBar end = new SeekBar(this);
        end.setMax(23);
        end.setProgress(prefs.getInt(ScreenLightService.K_NIGHT_END, 6));
        updateEndLabel(prefs.getInt(ScreenLightService.K_NIGHT_END, 6));
        end.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void changed(int value) {
                prefs.edit().putInt(ScreenLightService.K_NIGHT_END, value).apply();
                updateEndLabel(value);
            }
        });
        root.addView(end);

        CheckBox auto = new CheckBox(this);
        auto.setText("Also turn the light ON automatically when a call starts");
        auto.setChecked(prefs.getBoolean(ScreenLightService.K_AUTO_ON, false));
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(ScreenLightService.K_AUTO_ON, checked).apply();
            }
        });
        root.addView(auto);

        TextView note = new TextView(this);
        note.setText("\nHow it works: when a WhatsApp call screen opens at night, a round ON/OFF "
                + "button floats on screen. Tap it to switch the light strip. Drag it to move it. "
                + "The button and light disappear when you leave the call screen.");
        note.setTextSize(13);
        root.addView(note);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean running = ScreenLightService.instance != null;
        boolean enabledInSettings = isServiceEnabledInSettings();
        if (running) {
            status.setText("Service: ON - ready for calls");
            status.setTextColor(Color.parseColor("#2E7D32"));
        } else if (enabledInSettings) {
            status.setText("Service: starting... reopen this screen in a moment");
            status.setTextColor(Color.parseColor("#EF6C00"));
        } else {
            status.setText("Service: OFF - do step 1");
            status.setTextColor(Color.parseColor("#C62828"));
        }
        ScreenLightService s = ScreenLightService.instance;
        toggleLight.setText(s != null && s.isLightOn() ? "2. Test light: turn OFF" : "2. Test light: turn ON");
    }

    private boolean isServiceEnabledInSettings() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String me = new ComponentName(this, ScreenLightService.class).flattenToString();
        String meShort = new ComponentName(this, ScreenLightService.class).flattenToShortString();
        return enabled.contains(me) || enabled.contains(meShort);
    }

    private void applyToService() {
        ScreenLightService s = ScreenLightService.instance;
        if (s != null) s.refreshLight();
    }

    private void updateSizeLabel(int percent) {
        sizeLabel.setText("Light size: " + percent + "% of screen");
    }

    private void updateStartLabel(int hour) {
        startLabel.setText("Night starts at: " + hourText(hour));
    }

    private void updateEndLabel(int hour) {
        endLabel.setText("Night ends at: " + hourText(hour));
    }

    private static String hourText(int h) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (h < 12 ? " AM" : " PM");
    }

    private TextView heading(String text, float d) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTextColor(Color.parseColor("#0B1F4B"));
        t.setPadding(0, (int) (18 * d), 0, (int) (4 * d));
        return t;
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        abstract void changed(int value);

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) changed(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
