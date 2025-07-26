package com.example.sensedata;

import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomInfo;
import com.example.sensedata.model.WeatherResponse;
import com.example.sensedata.network.ApiClient;
import com.example.sensedata.network.WeatherApiService;
import com.squareup.picasso.Picasso;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private RoomAdapter adapter;
    private List<RoomInfo> roomList;

    // Елементи для погоди
    private TextView textCity, textMain, textDescription, textTemp, textFeels, textHumidity, textPressure;
    private ImageView imageWeatherIcon;

    // Оновлення погоди кожні 10 хв
    private final Handler weatherHandler = new Handler();
    private final int UPDATE_INTERVAL = 10 * 60 * 1000; // 10 хвилин = 600000 мс

    private final Runnable weatherUpdater = new Runnable() {
        @Override
        public void run() {
            fetchWeather();
            weatherHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Налаштування системних відступів
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.room_recycler_view),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                }
        );

        // ---------- RecyclerView кімнат ----------
        RecyclerView recyclerView = findViewById(R.id.room_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        roomList = Arrays.asList(
                new RoomInfo("Living Room", R.drawable.livingroom, "22°C", "45%"),
                new RoomInfo("Bedroom", R.drawable.livingroom, "21°C", "50%"),
                new RoomInfo("Kitchen", R.drawable.livingroom, "25°C", "55%")
        );

        adapter = new RoomAdapter(roomList, room ->
                Toast.makeText(this, "Selected: " + room.name, Toast.LENGTH_SHORT).show()
        );
        recyclerView.setAdapter(adapter);

        // ---------- Пошук елементів для погоди ----------
        textCity = findViewById(R.id.textWeatherCity);
        textMain = findViewById(R.id.textWeatherMain);
        textDescription = findViewById(R.id.textWeatherDescription);
        textTemp = findViewById(R.id.textWeatherTemp);
        textFeels = findViewById(R.id.textWeatherFeelsLike);
        textHumidity = findViewById(R.id.textWeatherHumidity);
        textPressure = findViewById(R.id.textWeatherPressure);
        imageWeatherIcon = findViewById(R.id.imageWeatherIcon);

        // ---------- Старт автооновлення погоди ----------
        weatherUpdater.run(); // запустити перше оновлення
    }

    // Метод запиту погоди через Retrofit
    private void fetchWeather() {
        WeatherApiService apiService = ApiClient.getClient().create(WeatherApiService.class);
        Call<WeatherResponse> call = apiService.getWeatherByCity("Kyiv", "2ef0e3c8a248cd6859d19fd7e7e2b04f", "metric");

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                android.util.Log.d("WEATHER", "HTTP код: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    android.util.Log.d("WEATHER", "Дані отримано");

                    WeatherResponse weather = response.body();

                    // Встановлення значень
                    textCity.setText("Місто: " + weather.cityName);
                    textMain.setText("Погода: " + weather.weather.get(0).main);
                    textDescription.setText("Опис: " + weather.weather.get(0).description);
                    textTemp.setText("🌡 Температура: " + weather.main.temp + "°C");
                    textFeels.setText("Відчувається як: " + weather.main.feelsLike + "°C");
                    textHumidity.setText("💧 Вологість: " + weather.main.humidity + "%");
                    textPressure.setText("Тиск: " + weather.main.pressure + " гПа");

                    String iconUrl = "https://openweathermap.org/img/wn/" + weather.weather.get(0).icon + "@2x.png";
                    Picasso.get().load(iconUrl).into(imageWeatherIcon);

                    Toast.makeText(MainActivity.this, "Погода оновлена: " + weather.main.temp + "°C", Toast.LENGTH_SHORT).show();

                } else {
                    android.util.Log.e("WEATHER", "Немає даних або код != 200");
                    Toast.makeText(MainActivity.this, "Помилка відповіді: код " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                android.util.Log.e("WEATHER", "Помилка: " + t.getMessage());
                Toast.makeText(MainActivity.this, "Помилка підключення: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        weatherHandler.removeCallbacks(weatherUpdater); // зупинити оновлення при виході
    }
}
