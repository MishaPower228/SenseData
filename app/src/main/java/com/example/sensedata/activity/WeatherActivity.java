package com.example.sensedata.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.example.sensedata.R;
import com.example.sensedata.model.WeatherResponse;
import com.example.sensedata.network.ApiClientWeather;
import com.example.sensedata.network.WeatherApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.squareup.picasso.Picasso;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherActivity {
    private final Activity activity;
    private final Handler handler = new Handler();
    private static final int UPDATE_INTERVAL = 10 * 60 * 1000; // 10 хв
    private final FusedLocationProviderClient fusedLocationClient;

    private final Runnable weatherUpdater = new Runnable() {
        @Override
        public void run() {
            fetchWeather();
            handler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    public WeatherActivity(Activity activity) {
        this.activity = activity;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    // 🔹 Запуск оновлення
    public void startWeatherUpdates() {
        showCachedWeather(); // показати кешовані дані
        fetchWeather();      // одразу оновити погоду з API
        handler.postDelayed(weatherUpdater, UPDATE_INTERVAL);
    }


    @SuppressWarnings("unused")
    public void handlePermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // TODO: можна реалізувати логіку при потребі
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

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call,
                                   @NonNull Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse weather = response.body();
                    showWeatherOnUi(weather);
                    saveWeatherToCache(weather);
                }
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                Toast.makeText(activity,
                        activity.getString(R.string.weather_error, t.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------ UI + кеш ------------------
    private void showWeatherOnUi(@NonNull WeatherResponse weather) {
        setText(R.id.textWeatherCity, activity.getString(R.string.weather_city, weather.cityName));
        setText(R.id.textWeatherMain, activity.getString(R.string.weather_main, weather.weather.get(0).main));
        setText(R.id.textWeatherDescription, activity.getString(R.string.weather_description, weather.weather.get(0).description));
        setText(R.id.textWeatherTemp, activity.getString(R.string.weather_temp, weather.main.temp));
        setText(R.id.textWeatherHumidity, activity.getString(R.string.weather_humidity, weather.main.humidity));
        setText(R.id.textWeatherPressure, activity.getString(R.string.weather_pressure, weather.main.pressure));

        ImageView image = activity.findViewById(R.id.imageWeatherIcon);
        String iconUrl = "https://openweathermap.org/img/wn/" + weather.weather.get(0).icon + "@2x.png";
        Picasso.get().load(iconUrl).into(image);
    }

    private void saveWeatherToCache(@NonNull WeatherResponse weather) {
        SharedPreferences prefs = activity.getSharedPreferences("WeatherCache", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("city", weather.cityName)
                .putString("main", weather.weather.get(0).main)
                .putString("desc", weather.weather.get(0).description)
                .putFloat("temp",  weather.main.temp)
                .putInt("humidity", weather.main.humidity)
                .putInt("pressure", weather.main.pressure)
                .putString("icon", weather.weather.get(0).icon)
                .apply();
    }

    private void showCachedWeather() {
        SharedPreferences prefs = activity.getSharedPreferences("WeatherCache", Context.MODE_PRIVATE);
        String city = prefs.getString("city", null);

        if (city == null) {
            setText(R.id.textWeatherCity, activity.getString(R.string.weather_city, "—"));
            setText(R.id.textWeatherMain, activity.getString(R.string.weather_loading));
            setText(R.id.textWeatherDescription, "");
            setText(R.id.textWeatherTemp, activity.getString(R.string.weather_temp, 0f));
            setText(R.id.textWeatherHumidity, activity.getString(R.string.weather_humidity, 0));
            setText(R.id.textWeatherPressure, activity.getString(R.string.weather_pressure, 0));

            ((ImageView) activity.findViewById(R.id.imageWeatherIcon))
                    .setImageResource(R.drawable.ic_user); // 👈 іконка-заглушка
            return;
        }

        setText(R.id.textWeatherCity, activity.getString(R.string.weather_city, city));
        setText(R.id.textWeatherMain, activity.getString(R.string.weather_main, prefs.getString("main", "")));
        setText(R.id.textWeatherDescription, activity.getString(R.string.weather_description, prefs.getString("desc", "")));
        setText(R.id.textWeatherTemp, activity.getString(R.string.weather_temp, prefs.getFloat("temp", 0)));
        setText(R.id.textWeatherHumidity, activity.getString(R.string.weather_humidity, prefs.getInt("humidity", 0)));
        setText(R.id.textWeatherPressure, activity.getString(R.string.weather_pressure, prefs.getInt("pressure", 0)));

        String icon = prefs.getString("icon", null);
        if (icon != null) {
            String iconUrl = "https://openweathermap.org/img/wn/" + icon + "@2x.png";
            Picasso.get().load(iconUrl).into((ImageView) activity.findViewById(R.id.imageWeatherIcon));
        }
    }

    private void setText(int viewId, String text) {
        ((TextView) activity.findViewById(viewId)).setText(text);
    }
}
