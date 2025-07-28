package com.example.sensedata;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomRequest;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.WeatherResponse;
import com.example.sensedata.network.ApiClient;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.WeatherApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private TextView textCity, textMain, textDescription, textTemp, textFeels, textHumidity, textPressure;
    private ImageView imageWeatherIcon;

    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private final List<RoomWithSensorDto> roomList = new ArrayList<>();
    private final Handler weatherHandler = new Handler();
    private final int UPDATE_INTERVAL = 10 * 60 * 1000; // 10 хв

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

        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v -> showCreateRoomDialog());

        // Ініціалізація локації
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupWeatherUI();

        roomRecyclerView = findViewById(R.id.room_recycler_view);
        roomAdapter = new RoomAdapter(roomList, room -> {
            // клік по кімнаті — за бажанням
        });
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        loadRoomsFromServer();

        weatherUpdater.run();
    }
    private void createRoomOnServer(String roomName, String imageName) {
        RoomRequest request = new RoomRequest(roomName, imageName);
        RoomApiService apiService = ApiClient.getClient().create(RoomApiService.class);

        apiService.createRoom(request).enqueue(new Callback<RoomWithSensorDto>() {
            @Override
            public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RoomWithSensorDto newRoom = response.body();
                    Toast.makeText(MainActivity.this, "Кімната створена: " + newRoom.name, Toast.LENGTH_SHORT).show();
                    // Можеш тут оновити RecyclerView
                } else {
                    Toast.makeText(MainActivity.this, "Не вдалося створити кімнату", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка з’єднання", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRoomsFromServer() {
        RoomApiService apiService = ApiClient.getClient().create(RoomApiService.class);
        apiService.getAllRooms().enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    roomList.clear();
                    roomList.addAll(response.body());
                    roomAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка з’єднання при завантаженні кімнат", Toast.LENGTH_SHORT).show();
            }
        });
    }
    // ---------- UI для погоди ----------
    private void showCreateRoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        builder.setView(dialogView);

        EditText roomNameInput = dialogView.findViewById(R.id.editRoomName);
        ImageView[] imageViews = new ImageView[] {
                dialogView.findViewById(R.id.img1),
                dialogView.findViewById(R.id.img2),
                dialogView.findViewById(R.id.img3),
                dialogView.findViewById(R.id.img4)
        };

        final String[] selectedImage = {null};

        for (ImageView img : imageViews) {
            img.setOnClickListener(v -> {
                selectedImage[0] = (String) v.getTag();
            });
        }

        builder.setPositiveButton("Створити", (dialog, which) -> {
            String roomName = roomNameInput.getText().toString().trim();

            if (!roomName.isEmpty() && selectedImage[0] != null) {
                createRoomOnServer(roomName, selectedImage[0]);
            } else {
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Скасувати", (dialog, which) -> dialog.dismiss());
        builder.setCancelable(false);
        builder.show();
    }

    private void setupWeatherUI() {
        textCity = findViewById(R.id.textWeatherCity);
        textMain = findViewById(R.id.textWeatherMain);
        textDescription = findViewById(R.id.textWeatherDescription);
        textTemp = findViewById(R.id.textWeatherTemp);
        textFeels = findViewById(R.id.textWeatherFeelsLike);
        textHumidity = findViewById(R.id.textWeatherHumidity);
        textPressure = findViewById(R.id.textWeatherPressure);
        imageWeatherIcon = findViewById(R.id.imageWeatherIcon);
    }

    private void fetchLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                fetchWeatherByCoordinates(location.getLatitude(), location.getLongitude());
            } else {
                fetchWeatherByCoordinates(50.45, 30.52); // Київ
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
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка підключення: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        weatherHandler.removeCallbacks(weatherUpdater);
    }
}
