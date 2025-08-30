package com.example.sensedata.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class SplashActivity extends ImmersiveActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String access = prefs.getString("accessToken", null);
        boolean firstRun = prefs.getBoolean("firstRun", true);

        if (access != null) {
            // вже залогінений
            startActivity(new Intent(this, MainActivity.class));
        } else {
            // не залогінений
            if (firstRun) {
                startActivity(new Intent(this, RegisterActivity.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        }
        finish();
    }
}
