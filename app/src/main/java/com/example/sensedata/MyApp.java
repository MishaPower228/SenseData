package com.example.sensedata;

import android.app.Application;
import android.content.SharedPreferences;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
            //SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
            //prefs.edit().clear().apply();
            //android.util.Log.d("MyApp", "DEBUG: SharedPreferences очищено");
    }
}
