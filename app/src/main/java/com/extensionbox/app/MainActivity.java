package com.extensionbox.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvTier;
    private MaterialButton btnToggle;

    private static final String[][] EXT_DATA = {
            {"battery",     "🔋  Battery",          "Current, power, temperature, health",     "true"},
            {"cpu_ram",     "🧠  CPU & RAM",        "CPU usage, memory status",                "true"},
            {"screen",      "📱  Screen Time",      "Screen on/off time, drain rates",         "true"},
            {"sleep",       "😴  Deep Sleep",       "CPU sleep vs awake ratio",                "true"},
            {"network",     "📶  Network Speed",    "Real-time download and upload speed",     "true"},
            {"data",        "📊  Data Usage",       "Daily & monthly, WiFi & mobile",          "true"},
            {"unlock",      "🔓  Unlock Counter",   "Daily unlocks, detox tracking",           "true"},
            {"storage",     "💾  Storage",           "Internal storage usage",                  "false"},
            {"connection",  "📡  Connection Info",   "WiFi, cellular, VPN status",              "false"},
            {"uptime",      "⏱  Uptime",            "Device uptime since boot",                "false"},
            {"steps",       "👣  Step Counter",      "Steps and distance",                      "false"},
            {"speedtest",   "🏎  Speed Test",       "Periodic download speed test",            "false"},
    };

    private static final int[] EXT_VIEW_IDS = {
            R.id.extBattery, R.id.extCpuRam, R.id.extScreen, R.id.extSleep,
            R.id.extNetwork, R.id.extData, R.id.extUnlock, R.id.extStorage,
            R.id.extConnection, R.id.extUptime, R.id.extSteps, R.id.extSpeedTest,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvTier = findViewById(R.id.tvTier);
        btnToggle = findViewById(R.id.btnToggle);

        requestPerms();
        setupExtensions();

        btnToggle.setOnClickListener(v -> toggleService());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void requestPerms() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), 100);
    }

    private void setupExtensions() {
        for (int i = 0; i < EXT_DATA.length; i++) {
            View card = findViewById(EXT_VIEW_IDS[i]);
            if (card == null) continue;

            String key = EXT_DATA[i][0];
            String title = EXT_DATA[i][1];
            String desc = EXT_DATA[i][2];
            boolean defEnabled = "true".equals(EXT_DATA[i][3]);

            TextView tvTitle = card.findViewById(R.id.extTitle);
            TextView tvDesc = card.findViewById(R.id.extDesc);
            MaterialSwitch sw = card.findViewById(R.id.extSwitch);

            tvTitle.setText(title);
            tvDesc.setText(desc);
            sw.setChecked(Prefs.isModuleEnabled(this, key, defEnabled));
            sw.setOnCheckedChangeListener((b, checked) ->
                    Prefs.setModuleEnabled(this, key, checked));
        }
    }

    private void toggleService() {
        if (Prefs.isRunning(this)) {
            Intent i = new Intent(this, MonitorService.class);
            i.setAction(MonitorService.ACTION_STOP);
            startService(i);
        } else {
            ContextCompat.startForegroundService(this,
                    new Intent(this, MonitorService.class));
        }
        btnToggle.postDelayed(this::refreshStatus, 500);
    }

    private void refreshStatus() {
        boolean on = Prefs.isRunning(this);
        tvStatus.setText(on ? "● Running" : "○ Stopped");
        tvStatus.setTextColor(on ? 0xFF4CAF50 : 0xFFF44336);
        btnToggle.setText(on ? "⏹  Stop Monitoring" : "▶  Start Monitoring");
        tvTier.setText("🔑 " + new SystemAccess(this).getTierName());
    }
}
