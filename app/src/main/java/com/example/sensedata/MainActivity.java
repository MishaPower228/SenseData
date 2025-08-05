package com.example.sensedata;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
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
import com.example.sensedata.network.ApiClientWeather;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.BleManager;
import com.example.sensedata.WeatherManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
    private final List<RoomWithSensorDto> roomList = new ArrayList<>();

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

        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v -> showCreateRoomDialog());

        roomRecyclerView = findViewById(R.id.room_recycler_view);
        roomAdapter = new RoomAdapter(roomList, room -> {
        });
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        loadRoomsFromServer();
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
                        roomList.add(new RoomWithSensorDto(roomName));
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

    public void createRoomOnServer(String roomName, String imageName, String ssid, String password) {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        if (username == null) {
            Toast.makeText(this, "Користувач не знайдений", Toast.LENGTH_SHORT).show();
            return;
        }

        RoomRequest request = new RoomRequest(roomName, imageName);
        RoomApiService apiService = ApiClientWeather.getClient().create(RoomApiService.class);

        apiService.createRoom(request).enqueue(new Callback<RoomWithSensorDto>() {
            @Override
            public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RoomWithSensorDto newRoom = response.body();
                    bleManager.sendConfigToEsp32ViaDevice(
                            bleManager.getSelectedDevice(),
                            newRoom.name,
                            newRoom.imageName,
                            ssid,
                            password,
                            username
                    );
                    roomList.add(newRoom);
                    roomAdapter.notifyItemInserted(roomList.size() - 1);
                    Toast.makeText(MainActivity.this, "Кімната створена", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка створення кімнати", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Сервер недоступний", Toast.LENGTH_SHORT).show();
            }
        });
    }

    void showCreateRoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText roomNameInput = dialogView.findViewById(R.id.editRoomName);
        EditText ssidInput = dialogView.findViewById(R.id.editSsid);
        EditText passwordInput = dialogView.findViewById(R.id.editPassword);
        Spinner deviceSpinner = dialogView.findViewById(R.id.spinnerDevices);

        // Ініціалізація картинок
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
        final int[] selectedIndex = {-1}; // -1 означає нічого не вибрано

        for (int i = 0; i < imageViews.length; i++) {
            final int index = i;
            imageViews[i].setOnClickListener(v -> {
                // Повторне натискання — скасувати виділення
                if (selectedIndex[0] == index) {
                    containers[index].setBackgroundResource(R.drawable.bg_image_selector);
                    v.setScaleX(1.0f); // Повернути до нормального розміру
                    v.setScaleY(1.0f);
                    selectedIndex[0] = -1;
                    selectedImage[0] = null;
                    return;
                }

                // Очистити всі рамки та масштаб
                for (int j = 0; j < containers.length; j++) {
                    containers[j].setBackgroundResource(R.drawable.bg_image_selector);
                    imageViews[j].setScaleX(1.0f);
                    imageViews[j].setScaleY(1.0f);
                }

                // Виділити поточне
                containers[index].setBackgroundResource(R.drawable.bg_image_selected);
                v.setScaleX(0.95f); // Зменшити масштаб
                v.setScaleY(0.95f);

                selectedIndex[0] = index;
                selectedImage[0] = (String) v.getTag();
            });
        }


        // Адаптер для BLE-пристроїв
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        deviceSpinner.setAdapter(deviceAdapter);

        // BLE-сканування при натисканні на Spinner
        deviceSpinner.setOnTouchListener((v, event) -> {
            bleManager.startBleScan((deviceNames, devices) -> runOnUiThread(() -> {
                deviceAdapter.clear();
                deviceAdapter.addAll(deviceNames);
                deviceAdapter.notifyDataSetChanged();
            }));
            return false;
        });

        // Вибір пристрою з Spinner
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bleManager.setSelectedDevice(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Обробка кнопок
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnCreate).setOnClickListener(v -> {
            String roomName = roomNameInput.getText().toString().trim();
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (roomName.isEmpty() || ssid.isEmpty() || password.isEmpty() || selectedImage[0] == null) {
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show();
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

            // Створення кімнати та надсилання на ESP32
            RoomRequest request = new RoomRequest(roomName, selectedImage[0]);
            RoomApiService apiService = ApiClientWeather.getClient().create(RoomApiService.class);

            apiService.createRoom(request).enqueue(new Callback<RoomWithSensorDto>() {
                @Override
                public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        RoomWithSensorDto newRoom = response.body();

                        bleManager.sendConfigToEsp32ViaDevice(device,
                                newRoom.name,
                                newRoom.imageName,
                                ssid,
                                password,
                                username
                        );

                        roomList.add(newRoom);
                        roomAdapter.notifyItemInserted(roomList.size() - 1);
                        Toast.makeText(MainActivity.this, "Кімната створена", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(MainActivity.this, "Помилка створення кімнати", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Сервер недоступний", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.setCancelable(false);
        dialog.show();
    }
}