package com.example.sensedata;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
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
import com.example.sensedata.network.ApiClientWeather;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.WeatherApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;


import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothLeScanner bleScanner;
    private boolean scanning = false;

    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private final List<RoomWithSensorDto> roomList = new ArrayList<>();

    private TextView textCity, textMain, textDescription, textTemp, textFeels, textHumidity, textPressure;
    private ImageView imageWeatherIcon;

    private FusedLocationProviderClient fusedLocationClient;
    private Handler weatherHandler;
    private Runnable weatherUpdater;
    private static final int UPDATE_INTERVAL = 10 * 60 * 1000;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔐 Перевірка авторизації
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        if (username == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish(); // Закриває поточну активність
            return;
        }

        // 🌐 Прозорий Edge-to-Edge
        EdgeToEdge.enable(this);

        // 📄 Завантаження layout
        setContentView(R.layout.activity_main);

        // ➕ FAB: додати кімнату
        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v -> showCreateRoomDialog());

        // 🌤️ Погода
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupWeatherUI();
        weatherHandler = new Handler();
        weatherUpdater = () -> {
            fetchLocationAndWeather();
            weatherHandler.postDelayed(weatherUpdater, UPDATE_INTERVAL);
        };
        weatherHandler.post(weatherUpdater);

        // 🔵 BLE
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        // 🏠 Список кімнат
        roomRecyclerView = findViewById(R.id.room_recycler_view);
        roomAdapter = new RoomAdapter(roomList, room -> {
            // Обробка кліку по кімнаті
        });
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        // 🔄 Завантажити кімнати
        loadRoomsFromServer();

        // 🔍 BLE сканування
        scanAndConnectToESP32();
    }


    private void scanAndConnectToESP32() {
        if (scanning || bleScanner == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 101);
            return;
        }

        scanning = true;
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bleScanner.startScan(Collections.singletonList(filter), settings, scanCallback);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
                return;
            }

            if (device != null) {
                bleScanner.stopScan(this);
                bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
                scanning = false;
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
                }
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
                return;
            }

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ Підключено до ESP32", Toast.LENGTH_SHORT).show());
            }
        }
    };


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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
                    textCity.setText("Місто: " + weather.cityName);
                    textMain.setText("Погода: " + weather.weather.get(0).main);
                    textDescription.setText("Опис: " + weather.weather.get(0).description);
                    textTemp.setText("Температура: " + weather.main.temp + "°C");
                    textFeels.setText("Відчувається як: " + weather.main.feelsLike + "°C");
                    textHumidity.setText("Вологість: " + weather.main.humidity + "%");
                    textPressure.setText("Тиск: " + weather.main.pressure + " гПа");
                    String iconUrl = "https://openweathermap.org/img/wn/" + weather.weather.get(0).icon + "@2x.png";
                    Picasso.get().load(iconUrl).into(imageWeatherIcon);
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка погоди: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRoomsFromServer() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        if (username == null) {
            Toast.makeText(this, "Користувач не знайдений", Toast.LENGTH_SHORT).show();
            return;
        }

        RoomApiService apiService = ApiClientWeather.getClient().create(RoomApiService.class);
        apiService.getAllRooms(username).enqueue(new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    roomList.clear();
                    for (String roomName : response.body()) {
                        roomList.add(new RoomWithSensorDto(roomName)); // або створюй RoomInfo
                    }
                    roomAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка при завантаженні кімнат", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateRoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText roomNameInput = dialogView.findViewById(R.id.editRoomName);
        EditText ssidInput = dialogView.findViewById(R.id.editSsid);
        EditText passwordInput = dialogView.findViewById(R.id.editPassword);

        ImageView[] imageViews = new ImageView[]{
                dialogView.findViewById(R.id.img1), dialogView.findViewById(R.id.img2),
                dialogView.findViewById(R.id.img3), dialogView.findViewById(R.id.img4)
        };

        final String[] selectedImage = {null};
        Animation clickAnim = AnimationUtils.loadAnimation(this, R.anim.image_click);

        for (ImageView img : imageViews) {
            img.setOnClickListener(v -> {
                v.startAnimation(clickAnim);
                for (ImageView iv : imageViews) {
                    iv.setBackgroundResource(R.drawable.bg_image_selector);
                }
                img.setBackgroundResource(R.drawable.bg_image_selected);
                selectedImage[0] = (String) v.getTag();
            });
        }

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnCreate).setOnClickListener(v -> {
            String roomName = roomNameInput.getText().toString().trim();
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (!roomName.isEmpty() && selectedImage[0] != null && !ssid.isEmpty() && !password.isEmpty()) {
                createRoomOnServer(roomName, selectedImage[0], ssid, password);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setCancelable(false);
        dialog.show();
    }

    private void createRoomOnServer(String roomName, String imageName, String ssid, String password) {
        RoomRequest request = new RoomRequest(roomName, imageName);
        RoomApiService apiService = ApiClientWeather.getClient().create(RoomApiService.class);

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

    private void sendConfigOverBLE(int roomId, String roomName, String imageName, String ssid, String password) {
        if (writeCharacteristic == null || bluetoothGatt == null) {
            Toast.makeText(this, "BLE не готовий", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "default_user");
        String encryptedPassword = encryptPassword(password);

        try {
            JSONObject json = new JSONObject();
            json.put("roomName", roomName);
            json.put("imageName", imageName);
            json.put("ssid", ssid);
            json.put("password", encryptedPassword);
            json.put("username", username);

            writeCharacteristic.setValue(json.toString().getBytes(StandardCharsets.UTF_8));

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                Toast.makeText(this, success ? "✅ Надіслано BLE JSON" : "❌ BLE помилка", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
            }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (weatherHandler != null && weatherUpdater != null) {
            weatherHandler.removeCallbacks(weatherUpdater);
        }

        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Дозвіл на Bluetooth надано", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Дозвіл на Bluetooth відхилено", Toast.LENGTH_SHORT).show();
        }
    }
}
