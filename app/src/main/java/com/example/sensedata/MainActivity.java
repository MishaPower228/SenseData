package com.example.sensedata;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.squareup.picasso.Picasso;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private RoomAdapter adapter;
    private List<RoomInfo> roomList;

    // Елементи UI
    private TextView textCity, textMain, textDescription, textTemp, textFeels, textHumidity, textPressure;
    private ImageView imageWeatherIcon;

    // Оновлення погоди кожні 10 хвилин
    private final Handler weatherHandler = new Handler();
    private final int UPDATE_INTERVAL = 10 * 60 * 1000;

    private final Runnable weatherUpdater = new Runnable() {
        @Override
        public void run() {
            fetchLocationAndWeather();
            weatherHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        FloatingActionButton fab = findViewById(R.id.fab_create_room);
        fab.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.RoundedDialog);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            dialog.show();
        });


        FloatingActionButton fabCreateRoom = findViewById(R.id.fab_create_room);
        fabCreateRoom.setOnClickListener(view -> showCreateRoomDialog());

        // Відступи від системних панелей
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.room_recycler_view),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                }
        );

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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

        // ---------- Пошук елементів погоди ----------
        textCity = findViewById(R.id.textWeatherCity);
        textMain = findViewById(R.id.textWeatherMain);
        textDescription = findViewById(R.id.textWeatherDescription);
        textTemp = findViewById(R.id.textWeatherTemp);
        textFeels = findViewById(R.id.textWeatherFeelsLike);
        textHumidity = findViewById(R.id.textWeatherHumidity);
        textPressure = findViewById(R.id.textWeatherPressure);
        imageWeatherIcon = findViewById(R.id.imageWeatherIcon);

        // ---------- Старт автооновлення погоди ----------
        weatherUpdater.run();
    }

    private void fetchLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                fetchWeatherByCoordinates(location.getLatitude(), location.getLongitude());
            } else {
                // fallback до Києва
                fetchWeatherByCoordinates(50.45, 30.52);
            }
        });
    }

    private void fetchWeatherByCoordinates(double lat, double lon) {
        WeatherApiService apiService = ApiClient.getClient().create(WeatherApiService.class);
        Call<WeatherResponse> call = apiService.getWeatherByCoordinates(lat, lon, "2ef0e3c8a248cd6859d19fd7e7e2b04f", "metric");

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse weather = response.body();

                    textCity.setText("Місто: " + weather.cityName);
                    textMain.setText("Погода: " + weather.weather.get(0).main);
                    textDescription.setText("Опис: " + weather.weather.get(0).description);
                    textTemp.setText("🌡 Температура: " + weather.main.temp + "°C");
                    textFeels.setText("Відчувається як: " + weather.main.feelsLike + "°C");
                    textHumidity.setText("💧 Вологість: " + weather.main.humidity + "%");
                    textPressure.setText("🧭 Тиск: " + weather.main.pressure + " гПа");

                    String iconUrl = "https://openweathermap.org/img/wn/" + weather.weather.get(0).icon + "@2x.png";
                    Picasso.get().load(iconUrl).into(imageWeatherIcon);
                } else {
                    Toast.makeText(MainActivity.this, "Помилка відповіді: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка підключення: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateRoomDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);

        EditText editRoomName = dialogView.findViewById(R.id.editRoomName);
        ImageView image1 = dialogView.findViewById(R.id.imageOption1);
        ImageView image2 = dialogView.findViewById(R.id.imageOption2);
        ImageView image3 = dialogView.findViewById(R.id.imageOption3);
        ImageView image4 = dialogView.findViewById(R.id.imageOption4);
        // Додай ще, якщо є

        final int[] selectedImageRes = {R.drawable.livingroom}; // за замовчуванням

        image1.setOnClickListener(v -> selectedImageRes[0] = R.drawable.livingroom);
        image2.setOnClickListener(v -> selectedImageRes[0] = R.drawable.kitchen);
        image3.setOnClickListener(v -> selectedImageRes[0] = R.drawable.living_room);
        image4.setOnClickListener(v -> selectedImageRes[0] = R.drawable.living_room_2);

        new AlertDialog.Builder(this)
                .setTitle("Створити кімнату")
                .setView(dialogView)
                .setPositiveButton("Створити", (dialog, which) -> {
                    String roomName = editRoomName.getText().toString().trim();
                    if (!roomName.isEmpty()) {
                        RoomInfo newRoom = new RoomInfo(roomName, selectedImageRes[0], "22°C", "50%");
                        roomList.add(newRoom);
                        adapter.notifyItemInserted(roomList.size() - 1);
                    }
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        weatherHandler.removeCallbacks(weatherUpdater);
    }
}
