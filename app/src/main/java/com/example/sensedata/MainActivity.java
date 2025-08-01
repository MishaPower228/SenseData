package com.example.sensedata;

import android.Manifest;
import android.bluetooth.*;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient fusedLocationClient;
    private final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private final Handler weatherHandler = new Handler();
    private final int UPDATE_INTERVAL = 10 * 60 * 1000;

    private TextView textCity, textMain, textDescription, textTemp, textFeels, textHumidity, textPressure;
    private ImageView imageWeatherIcon;
    private RecyclerView roomRecyclerView;

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;

    private RoomAdapter roomAdapter;
    private final List<RoomWithSensorDto> roomList = new ArrayList<>();

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupWeatherUI();
        weatherUpdater.run();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 200);
        }

        roomRecyclerView = findViewById(R.id.room_recycler_view);
        roomAdapter = new RoomAdapter(roomList, room -> {});
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        loadRoomsFromServer();
    }

    private void sendConfigOverBLE(int roomId, String roomName, String imageName, String ssid, String password) {
        if (writeCharacteristic == null || bluetoothGatt == null) {
            Toast.makeText(this, "BLE не готовий", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "❌ Немає дозволу на Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "default_user");

        String encryptedPassword = encryptPassword(password);

        try {
            JSONObject json = new JSONObject();
            json.put("roomId", roomId);
            json.put("roomName", roomName);
            json.put("imageName", imageName);
            json.put("ssid", ssid);
            json.put("password", encryptedPassword);
            json.put("username", username);

            byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
            writeCharacteristic.setValue(jsonBytes);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);

            Toast.makeText(this, success ? "✅ Надіслано BLE JSON" : "❌ BLE помилка", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String encryptPassword(String password) {
        try {
            String key = "my-secret-key-12";
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return password;
        }
    }

    private void showCreateRoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        builder.setView(dialogView);

        EditText roomNameInput = dialogView.findViewById(R.id.editRoomName);
        EditText ssidInput = dialogView.findViewById(R.id.editSsid);
        EditText passwordInput = dialogView.findViewById(R.id.editPassword);

        ImageView[] imageViews = new ImageView[]{
                dialogView.findViewById(R.id.img1), dialogView.findViewById(R.id.img2),
                dialogView.findViewById(R.id.img3), dialogView.findViewById(R.id.img4)
        };

        final String[] selectedImage = {null};
        for (ImageView img : imageViews) {
            img.setOnClickListener(v -> selectedImage[0] = (String) v.getTag());
        }

        builder.setPositiveButton("Створити", (dialog, which) -> {
            String roomName = roomNameInput.getText().toString().trim();
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (!roomName.isEmpty() && selectedImage[0] != null && !ssid.isEmpty() && !password.isEmpty()) {
                createRoomOnServer(roomName, selectedImage[0], ssid, password);
            } else {
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Скасувати", (dialog, which) -> dialog.dismiss());
        builder.setCancelable(false);
        builder.show();
    }

    private void createRoomOnServer(String roomName, String imageName, String ssid, String password) {
        RoomRequest request = new RoomRequest(roomName, imageName);
        RoomApiService apiService = ApiClient.getClient().create(RoomApiService.class);

        apiService.createRoom(request).enqueue(new Callback<RoomWithSensorDto>() {
            @Override
            public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RoomWithSensorDto newRoom = response.body();
                    sendConfigOverBLE(newRoom.id, newRoom.name, newRoom.imageName, ssid, password);
                } else {
                    Toast.makeText(MainActivity.this, "Помилка створення кімнати", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
                Toast.makeText(MainActivity.this, "❌ Сервер недоступний", Toast.LENGTH_SHORT).show();
            }
        });
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
                    textTemp.setText("\uD83C\uDF21 Температура: " + weather.main.temp + "°C");
                    textFeels.setText("Відчувається як: " + weather.main.feelsLike + "°C");
                    textHumidity.setText("💧 Вологість: " + weather.main.humidity + "%");
                    textPressure.setText("🗭 Тиск: " + weather.main.pressure + " гПа");

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
                Toast.makeText(MainActivity.this, "Помилка при завантаженні кімнат", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        weatherHandler.removeCallbacks(weatherUpdater);
    }
}
