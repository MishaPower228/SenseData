package com.example.sensedata;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomRequest;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipRequestDTO;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.ApiClientWeather;
import com.example.sensedata.network.RoomApiService;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private WeatherManager weatherManager;
    private BleManager bleManager;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private AlertDialog createRoomDialog; // Додаємо поле для доступу з інших методів

    private Handler handler = new Handler();
    private Runnable refreshRunnable = new Runnable() {
        public void run() {
            if (!roomAdapter.getCurrentList().isEmpty()) {
                refreshRoomsData();
            }
            handler.postDelayed(this, 30_000);
        }
    };
    private String lastCreatedRoomName = null;
    private String lastCreatedImageName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        if (prefs.getString("username", null) == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);

        bleManager = new BleManager(this);
        weatherManager = new WeatherManager(this);
        weatherManager.startWeatherUpdates();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, 1001);
        }

        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v -> showCreateRoomDialog());

        roomRecyclerView = findViewById(R.id.room_recycler_view);
        roomAdapter = new RoomAdapter(room -> {
        });
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        loadRoomsFromServer();
        startPeriodicRoomRefresh();
    }

    private void startPeriodicRoomRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!roomAdapter.getCurrentList().isEmpty()) {
                    refreshRoomsData();
                }
                handler.postDelayed(this, 30_000); // кожні 30 секунд
            }
        };
        handler.post(refreshRunnable);
    }

    private void refreshRoomsData() {
        int userId = getSavedUserId();
        if (userId == -1) return;

        RoomApiService apiService = ApiClientMain.getClient(MainActivity.this).create(RoomApiService.class);

        apiService.getAllRooms(userId).enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RoomWithSensorDto> newList = response.body();
                    roomAdapter.submitList(new ArrayList<>(newList)); // використовуємо DiffUtil
                }
            }

            @Override
            public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) {
                // не показуємо Toast — щоб не спамити при кожному циклі
            }
        });
    }


    private int getSavedUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getInt("userId", -1); // -1 якщо не знайдено
    }

    private void loadRoomsFromServer() {
        int userId = getSavedUserId();
        if (userId == -1) {
            Toast.makeText(this, "Користувач не знайдений", Toast.LENGTH_SHORT).show();
            return;
        }

        RoomApiService apiService = ApiClientMain.getClient(MainActivity.this).create(RoomApiService.class);

        apiService.getAllRooms(userId).enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    roomAdapter.submitList(new ArrayList<>(response.body()));
                } else {
                    Toast.makeText(MainActivity.this, "Не вдалося отримати кімнати", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка при завантаженні кімнат", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onChipIdReceivedFromEsp32(String chipId) {
        Log.d("BLE_NOTIFY", "Отримано з ESP32: " + chipId);

        if (roomAlreadyExists(chipId)) {
            Log.d("BLE_NOTIFY", "chipId вже оброблений: " + chipId);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        int userId = prefs.getInt("userId", -1);
        String username = prefs.getString("username", null);

        if (userId == -1 || username == null) {
            Toast.makeText(this, "UserId або Username не знайдені", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ 1. Надсилаємо POST-запит на /ownership
        RoomApiService apiService = ApiClientMain.getClient(MainActivity.this).create(RoomApiService.class);

        SensorOwnershipRequestDTO request = new SensorOwnershipRequestDTO(
                lastCreatedRoomName,
                lastCreatedImageName,
                chipId,
                username
        );

        Log.d("ROOM_CREATE", "POST на /ownership: chipId=" + chipId + ", username=" + username + ", room=" + lastCreatedRoomName + ", image=" + lastCreatedImageName);

        Call<Void> postCall = apiService.createRoom(request);
        postCall.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("ROOM_CREATE", "Кімната успішно створена на сервері");

                    // ✅ 2. Після POST виконуємо GET по chipId + userId
                    Call<RoomWithSensorDto> getCall = apiService.getRoomByChipId(chipId, userId);
                    getCall.enqueue(new Callback<RoomWithSensorDto>() {
                        @Override
                        public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                RoomWithSensorDto room = response.body();
                                List<RoomWithSensorDto> updatedList = new ArrayList<>(roomAdapter.getCurrentList());
                                updatedList.add(room);
                                roomAdapter.submitList(updatedList);
                                Toast.makeText(MainActivity.this, "Кімната додана: " + room.getRoomName(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Помилка при отриманні кімнати", Toast.LENGTH_SHORT).show();
                                Log.e("ROOM_GET", "Помилка GET: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
                            Toast.makeText(MainActivity.this, "Помилка GET-запиту: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e("ROOM_GET", "GET помилка", t);
                        }
                    });

                } else {
                    Toast.makeText(MainActivity.this, "Помилка створення кімнати (POST): " + response.code(), Toast.LENGTH_SHORT).show();
                    Log.e("ROOM_CREATE", "POST помилка: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка POST-запиту: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("ROOM_CREATE", "POST виключення", t);
            }
        });
    }

    private String getSavedUsername() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getString("username", null);
    }

    private boolean roomAlreadyExists(String chipId) {
        if (chipId == null) return false;
        for (RoomWithSensorDto room : roomAdapter.getCurrentList()) {
            if (chipId.equals(room.getChipId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }

    private void showCreateRoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        builder.setView(dialogView);
        createRoomDialog = builder.create();

        // 🔹 Поля вводу
        EditText roomNameInput = dialogView.findViewById(R.id.editRoomName);
        EditText ssidInput = dialogView.findViewById(R.id.editSsid);
        EditText passwordInput = dialogView.findViewById(R.id.editPassword);
        MaterialCheckBox checkboxReset = dialogView.findViewById(R.id.checkboxReset);

        // 🔹 Сканування BLE
        Spinner deviceSpinner = dialogView.findViewById(R.id.spinnerDevices);
        ProgressBar progressScan = dialogView.findViewById(R.id.progressScan);
        Button btnScan = dialogView.findViewById(R.id.btnScanBle);

        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        deviceSpinner.setAdapter(deviceAdapter);

        btnScan.setOnClickListener(v -> {
            progressScan.setVisibility(View.VISIBLE);
            bleManager.startBleScan((deviceNames, devices) -> runOnUiThread(() -> {
                deviceAdapter.clear();
                deviceAdapter.addAll(deviceNames);
                deviceAdapter.notifyDataSetChanged();
                progressScan.setVisibility(View.GONE);

                if (!deviceNames.isEmpty()) {
                    deviceSpinner.setSelection(0);
                    bleManager.setSelectedDevice(0);
                }
            }));
        });

        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bleManager.setSelectedDevice(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 🔹 Вибір зображення
        FrameLayout[] containers = {
                dialogView.findViewById(R.id.container1),
                dialogView.findViewById(R.id.container2),
                dialogView.findViewById(R.id.container3),
                dialogView.findViewById(R.id.container4)
        };

        ImageView[] imageViews = {
                dialogView.findViewById(R.id.img1),
                dialogView.findViewById(R.id.img2),
                dialogView.findViewById(R.id.img3),
                dialogView.findViewById(R.id.img4)
        };

        final String[] selectedImage = {null};
        final int[] selectedIndex = {-1};

        for (int i = 0; i < imageViews.length; i++) {
            final int index = i;
            imageViews[i].setOnClickListener(v -> {
                if (selectedIndex[0] == index) {
                    containers[index].setBackgroundResource(R.drawable.bg_image_selector);
                    v.setScaleX(1.0f);
                    v.setScaleY(1.0f);
                    selectedIndex[0] = -1;
                    selectedImage[0] = null;
                    return;
                }

                for (int j = 0; j < containers.length; j++) {
                    containers[j].setBackgroundResource(R.drawable.bg_image_selector);
                    imageViews[j].setScaleX(1.0f);
                    imageViews[j].setScaleY(1.0f);
                }

                containers[index].setBackgroundResource(R.drawable.bg_image_selected);
                v.setScaleX(0.95f);
                v.setScaleY(0.95f);
                selectedIndex[0] = index;
                selectedImage[0] = (String) v.getTag(); // ⚠️ має бути встановлено в XML
            });
        }

        // 🔹 Кнопки
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> createRoomDialog.dismiss());

        dialogView.findViewById(R.id.btnCreate).setOnClickListener(v -> {
            String roomName = roomNameInput.getText().toString().trim();
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            boolean reset = checkboxReset.isChecked();

            // 🔸 Валідація
            if (roomName.isEmpty() || ssid.isEmpty() || password.isEmpty() || selectedImage[0] == null) {
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isAsciiOnly(password)) {
                Toast.makeText(this, "Пароль повинен містити лише англійські символи", Toast.LENGTH_SHORT).show();
                return;
            }

            BluetoothDevice device = bleManager.getSelectedDevice();
            if (device == null) {
                Toast.makeText(this, "Оберіть ESP32-плату", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
            String username = prefs.getString("username", null);
            if (username == null) {
                Toast.makeText(this, "Користувач не знайдений", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Збереження для POST-запиту після BLE
            lastCreatedRoomName = roomName;
            lastCreatedImageName = selectedImage[0];

            Log.d("BLE_SEND", "roomName: " + roomName + ", image: " + selectedImage[0]);

            // 🟦 Відправлення BLE-конфігурації
            bleManager.sendConfigToEsp32ViaDevice(device,
                    roomName,
                    selectedImage[0],
                    ssid,
                    password,
                    username,
                    reset);

            createRoomDialog.dismiss();
        });

        createRoomDialog.setCancelable(false);
        createRoomDialog.show();
    }


    private boolean isAsciiOnly(String input) {
        return input.matches("\\A\\p{ASCII}*\\z");
    }
}

