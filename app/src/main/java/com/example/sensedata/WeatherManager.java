package com.example.sensedata;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
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
    private final int UPDATE_INTERVAL = 10 * 60 * 1000;

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

    public void startWeatherUpdates() {
        weatherUpdater.run();
    }

    public void stopWeatherUpdates() {
        handler.removeCallbacks(weatherUpdater);
    }

    public void handlePermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // можеш реалізувати при потребі
    }

    private void fetchWeather() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 123);
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
        Call<WeatherResponse> call = apiService.getWeatherByCoordinates(lat, lon, "2ef0e3c8a248cd6859d19fd7e7e2b04f", "metric");

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse weather = response.body();

                    ((TextView) activity.findViewById(R.id.textWeatherCity)).setText("Місто: " + weather.cityName);
                    ((TextView) activity.findViewById(R.id.textWeatherMain)).setText("Погода: " + weather.weather.get(0).main);
                    ((TextView) activity.findViewById(R.id.textWeatherDescription)).setText("Опис: " + weather.weather.get(0).description);
                    ((TextView) activity.findViewById(R.id.textWeatherTemp)).setText("Температура: " + weather.main.temp + "°C");
                    ((TextView) activity.findViewById(R.id.textWeatherFeelsLike)).setText("Відчувається як: " + weather.main.feelsLike + "°C");
                    ((TextView) activity.findViewById(R.id.textWeatherHumidity)).setText("Вологість: " + weather.main.humidity + "%");
                    ((TextView) activity.findViewById(R.id.textWeatherPressure)).setText("Тиск: " + weather.main.pressure + " гПа");

                    ImageView image = activity.findViewById(R.id.imageWeatherIcon);
                    String iconUrl = "https://openweathermap.org/img/wn/" + weather.weather.get(0).icon + "@2x.png";
                    Picasso.get().load(iconUrl).into(image);
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(activity, "Помилка погоди: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
