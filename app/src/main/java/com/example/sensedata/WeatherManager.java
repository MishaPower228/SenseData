package com.example.sensedata;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.example.sensedata.model.WeatherResponse;
import com.example.sensedata.network.ApiClientWeather;
import com.example.sensedata.network.WeatherApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.squareup.picasso.Picasso;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherManager {
    private final Activity activity;
    private final Handler handler = new Handler();
    private final int UPDATE_INTERVAL = 10 * 60 * 1000; // 10 хв

    private final FusedLocationProviderClient fusedLocationClient;

    private final Runnable weatherUpdater = new Runnable() {
        @Override
        public void run() {
            fetchWeather();
            handler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    public WeatherManager(Activity activity) {
        this.activity = activity;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    // 🔹 Запуск оновлення
    public void startWeatherUpdates() {
        // 1. показати кешовані дані (якщо є)
        showCachedWeather();

        // 2. одразу оновити погоду з API
        fetchWeather();

        // 3. запустити періодичні оновлення
        handler.postDelayed(weatherUpdater, UPDATE_INTERVAL);
    }

    public void stopWeatherUpdates() {
        handler.removeCallbacks(weatherUpdater);
    }

    public void handlePermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // можна доробити при потребі
    }

    // ------------------ Основний запит ------------------

    private void fetchWeather() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 123);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                fetchWeatherByCoordinates(location.getLatitude(), location.getLongitude());
            }
        });
    }

    private void fetchWeatherByCoordinates(double lat, double lon) {
        WeatherApiService apiService = ApiClientWeather.getClient().create(WeatherApiService.class);
        Call<WeatherResponse> call = apiService.getWeatherByCoordinates(
                lat, lon,
                "2ef0e3c8a248cd6859d19fd7e7e2b04f", // 🔑 твій API-ключ
                "metric"
        );

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse weather = response.body();
                    showWeatherOnUi(weather);
                    saveWeatherToCache(weather); // 👈 зберігаємо кеш
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(activity, "Помилка погоди: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------ UI + кеш ------------------

    private void showWeatherOnUi(WeatherResponse weather) {
        ((TextView) activity.findViewById(R.id.textWeatherCity))
                .setText("Місто: " + weather.cityName);
        ((TextView) activity.findViewById(R.id.textWeatherMain))
                .setText("Погода: " + weather.weather.get(0).main);
        ((TextView) activity.findViewById(R.id.textWeatherDescription))
                .setText("Опис: " + weather.weather.get(0).description);
        ((TextView) activity.findViewById(R.id.textWeatherTemp))
                .setText("Температура: " + weather.main.temp + "°C");
        ((TextView) activity.findViewById(R.id.textWeatherHumidity))
                .setText("Вологість: " + weather.main.humidity + "%");
        ((TextView) activity.findViewById(R.id.textWeatherPressure))
                .setText("Тиск: " + weather.main.pressure + " гПа");

        ImageView image = activity.findViewById(R.id.imageWeatherIcon);
        String iconUrl = "https://openweathermap.org/img/wn/" +
                weather.weather.get(0).icon + "@2x.png";
        Picasso.get().load(iconUrl).into(image);
    }

    private void saveWeatherToCache(WeatherResponse weather) {
        SharedPreferences prefs = activity.getSharedPreferences("WeatherCache", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("city", weather.cityName)
                .putString("main", weather.weather.get(0).main)
                .putString("desc", weather.weather.get(0).description)
                .putFloat("temp", (float) weather.main.temp)
                .putInt("humidity", weather.main.humidity)
                .putInt("pressure", weather.main.pressure)
                .putString("icon", weather.weather.get(0).icon)
                .apply();
    }

    private void showCachedWeather() {
        SharedPreferences prefs = activity.getSharedPreferences("WeatherCache", Context.MODE_PRIVATE);
        String city = prefs.getString("city", null);

        if (city == null) {
            // 🔹 якщо кешу немає, показати заглушку "Завантажую..."
            ((TextView) activity.findViewById(R.id.textWeatherCity))
                    .setText("Місто: —");
            ((TextView) activity.findViewById(R.id.textWeatherMain))
                    .setText("Завантажую погоду…");
            ((TextView) activity.findViewById(R.id.textWeatherDescription))
                    .setText("");
            ((TextView) activity.findViewById(R.id.textWeatherTemp))
                    .setText("Температура: —");
            ((TextView) activity.findViewById(R.id.textWeatherHumidity))
                    .setText("Вологість: —");
            ((TextView) activity.findViewById(R.id.textWeatherPressure))
                    .setText("Тиск: —");
            ((ImageView) activity.findViewById(R.id.imageWeatherIcon))
                    .setImageResource(R.drawable.ic_user); // 👈 твоя іконка-заглушка
            return;
        }

        // 🔹 якщо кеш є → показуємо його
        ((TextView) activity.findViewById(R.id.textWeatherCity))
                .setText("Місто: " + city);
        ((TextView) activity.findViewById(R.id.textWeatherMain))
                .setText("Погода: " + prefs.getString("main", ""));
        ((TextView) activity.findViewById(R.id.textWeatherDescription))
                .setText("Опис: " + prefs.getString("desc", ""));
        ((TextView) activity.findViewById(R.id.textWeatherTemp))
                .setText("Температура: " + prefs.getFloat("temp", 0) + "°C");
        ((TextView) activity.findViewById(R.id.textWeatherHumidity))
                .setText("Вологість: " + prefs.getInt("humidity", 0) + "%");
        ((TextView) activity.findViewById(R.id.textWeatherPressure))
                .setText("Тиск: " + prefs.getInt("pressure", 0) + " гПа");

        ImageView image = activity.findViewById(R.id.imageWeatherIcon);
        String icon = prefs.getString("icon", null);
        if (icon != null) {
            String iconUrl = "https://openweathermap.org/img/wn/" + icon + "@2x.png";
            Picasso.get().load(iconUrl).into(image);
        }
    }

}
