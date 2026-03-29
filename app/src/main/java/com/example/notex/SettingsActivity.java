package com.example.notex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    MaterialSwitch switchCloud, switchMultiPage;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        // INIT
        switchCloud = findViewById(R.id.switchCloud);
        switchMultiPage = findViewById(R.id.switchMultiPage);
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        // LOAD SAVED STATE
        boolean isCloud = prefs.getBoolean("cloud_sync", true);
        boolean isMultiPage = prefs.getBoolean("multi_page", true);

        if (switchCloud != null) switchCloud.setChecked(isCloud);
        if (switchMultiPage != null) switchMultiPage.setChecked(isMultiPage);

        // CLOUD SYNC TOGGLE
        if (switchCloud != null) {
            switchCloud.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("cloud_sync", isChecked).apply();
                Toast.makeText(this, isChecked ? "Cloud Sync Enabled" : "Cloud Sync Disabled (Local Storage Only)", Toast.LENGTH_SHORT).show();
            });
        }

        // MULTI-PAGE SCANNING TOGGLE
        if (switchMultiPage != null) {
            switchMultiPage.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("multi_page", isChecked).apply();
                Toast.makeText(this, isChecked ? "Multi-page Enabled" : "Multi-page Disabled (Single scan only)", Toast.LENGTH_SHORT).show();
            });
        }

        // BACK BUTTON & MENU
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
            
            if (toolbar.getMenu().findItem(R.id.action_search) != null) {
                toolbar.getMenu().findItem(R.id.action_search).setVisible(false);
            }

            toolbar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    finish();
                    return true;
                } else if (id == R.id.nav_saved) {
                    startActivity(new Intent(this, SavedFilesActivity.class));
                    return true;
                }
                return false;
            });
        }
    }
}
