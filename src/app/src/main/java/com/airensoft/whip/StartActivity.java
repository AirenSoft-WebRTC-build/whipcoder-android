package com.airensoft.whip;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.airensoft.whip.databinding.ActivityStartBinding;

public class StartActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ActivityStartBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Request permission
        permissionGrant();

        // Assign Settings Fragment to Activity
        SettingsFragment settingsFragment = new SettingsFragment();
        FragmentTransaction mContext = getSupportFragmentManager().beginTransaction();
        mContext.replace(R.id.fragmentContainerView, settingsFragment).commit();

        // Initialize and load preference settings
        initDefaultSharedPreference();

        // UI Event Binding
        attachUIHandler();
    }

    public void attachUIHandler() {
        binding.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startMainActivity();
            }
        });
    }

    @Override
    public void onResume() {
        SettingsFragment settingsFragment = new SettingsFragment();
        FragmentTransaction mContext = getSupportFragmentManager().beginTransaction();
        mContext.replace(R.id.fragmentContainerView, settingsFragment).commit();

        super.onResume();
    }

    public void initDefaultSharedPreference() {
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    // Start Intent
    public void startMainActivity() {

        Intent intent = new Intent(StartActivity.this, BroadcastActivity.class);

        startActivity(intent);
    }

    // Preference Change Event

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        if (s == null)
            return;
    }

    // Permissions

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public void permissionGrant() {
        int permission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                this.startActivity(intent);
            }
        }
    }

}