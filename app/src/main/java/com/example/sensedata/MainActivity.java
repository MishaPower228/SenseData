package com.example.sensedata;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.UserApiService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private WeatherManager weatherManager;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;

    // з CreateRoomActivity повертаємо ці значення і зберігаємо тут для POST /ownership
    private String lastCreatedRoomName = null;
    private String lastCreatedImageName = null;

    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    // запуск сторінки створення кімнати та прийом результату
    private ActivityResultLauncher<Intent> createRoomLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // якщо немає логіну — на LoginActivity
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        if (prefs.getString("username", null) == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // шапка
        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        TextView title = toolbar.findViewById(R.id.toolbar_title);

        // підписи
        TextView labelWeather = findViewById(R.id.labelWeather);
        TextView labelRooms   = findViewById(R.id.labelRooms);
        labelWeather.setPaintFlags(labelWeather.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        labelRooms.setPaintFlags(labelRooms.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // 1) показуємо з Prefs
        String cached = getSavedUsername();
        setHello(title, (cached != null) ? cached : getString(R.string.guest));
        // 2) за потреби — оновлюємо з API
        refreshUsernameFromServer(title);

        // погода
        weatherManager = new WeatherManager(this);
        weatherManager.startWeatherUpdates();

        // список кімнат
        roomRecyclerView = findViewById(R.id.room_recycler_view);
        roomAdapter = new RoomAdapter(room -> { /* click handler якщо знадобиться */ });
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        // FAB -> відкриваємо CreateRoomActivity
        createRoomLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String chipId   = result.getData().getStringExtra("chipId");
                        lastCreatedRoomName  = result.getData().getStringExtra("roomName");
                        lastCreatedImageName = result.getData().getStringExtra("imageName");
                        onChipIdReceivedFromEsp32(chipId);   // далі — POST /ownership і оновлення списку
                    }
                }
        );

        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v ->
                createRoomLauncher.launch(new Intent(this, CreateRoomActivity.class))
        );

        // первинне завантаження + періодичне оновлення
        loadRoomsFromServer();
        startPeriodicRoomRefresh();
    }

    private void startPeriodicRoomRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() {
                if (!roomAdapter.getCurrentList().isEmpty()) {
                    refreshRoomsData();
                }
                handler.postDelayed(this, 30_000); // кожні 30 с
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
                    roomAdapter.submitList(new ArrayList<>(response.body()));
                }
            }
            @Override public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) { /* тихо */ }
        });
    }

    private int getSavedUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getInt("userId", -1);
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

    /** Викликається після того, як CreateRoomActivity повертає chipId */
    public void onChipIdReceivedFromEsp32(String chipIdRaw) {
        if (chipIdRaw == null) return;

        String chipId = chipIdRaw.trim().toUpperCase(Locale.ROOT);
        Log.d("BLE_NOTIFY", "Отримано з ESP32: " + chipId);

        if (roomAlreadyExists(chipId)) {
            Log.d("BLE_NOTIFY", "chipId вже в списку: " + chipId);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        int userId = prefs.getInt("userId", -1);
        if (userId == -1) {
            Toast.makeText(this, "UserId не знайдено. Увійдіть знову.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastCreatedRoomName == null || lastCreatedImageName == null) {
            Toast.makeText(this, "Немає параметрів кімнати (roomName/imageName). Створіть кімнату ще раз.", Toast.LENGTH_SHORT).show();
            return;
        }

        RoomApiService apiService = ApiClientMain.getClient(MainActivity.this).create(RoomApiService.class);
        com.example.sensedata.model.SensorOwnershipCreateDto request =
                new com.example.sensedata.model.SensorOwnershipCreateDto(
                        userId, chipId, lastCreatedRoomName, lastCreatedImageName
                );

        Log.d("ROOM_CREATE", "POST /ownership: chipId=" + chipId + ", userId=" + userId +
                ", room=" + lastCreatedRoomName + ", image=" + lastCreatedImageName);

        apiService.createRoom(request).enqueue(new Callback<RoomWithSensorDto>() {
            @Override
            public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RoomWithSensorDto room = response.body();

                    List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                    boolean exists = false;
                    for (RoomWithSensorDto r : updated) {
                        if (r.getChipId() != null && r.getChipId().equalsIgnoreCase(room.getChipId())) {
                            exists = true; break;
                        }
                    }
                    if (!exists) {
                        updated.add(room);
                        roomAdapter.submitList(updated);
                        Toast.makeText(MainActivity.this, "Кімната додана: " + room.getRoomName(), Toast.LENGTH_SHORT).show();
                    }
                } else if (response.code() == 409) {
                    Toast.makeText(MainActivity.this, "Пристрій вже зареєстрований. Оновлюю список…", Toast.LENGTH_SHORT).show();
                    refreshRoomsData();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка створення кімнати (POST): " + response.code(), Toast.LENGTH_SHORT).show();
                    Log.e("ROOM_CREATE", "POST помилка: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
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
        String target = chipId.trim().toUpperCase(Locale.ROOT);
        for (RoomWithSensorDto room : roomAdapter.getCurrentList()) {
            if (room.getChipId() != null &&
                    room.getChipId().trim().toUpperCase(Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
    }

    private void setHello(TextView title, String name) {
        String text = getString(R.string.hello_username, name); // "Привіт, %1$s"
        SpannableString s = new SpannableString(text);
        int start = text.indexOf(name);
        if (start >= 0) s.setSpan(new StyleSpan(Typeface.BOLD), start, start + name.length(), 0);
        title.setText(s);
    }

    private void refreshUsernameFromServer(TextView title) {
        int userId = getSavedUserId();
        if (userId <= 0) return;

        UserApiService api = ApiClientMain.getClient(this).create(UserApiService.class);
        api.getUsername(userId).enqueue(new retrofit2.Callback<String>() {
            @Override public void onResponse(retrofit2.Call<String> call, retrofit2.Response<String> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String fresh = resp.body().trim();
                    setHello(title, fresh);
                    getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                            .edit()
                            .putString("username", fresh)
                            .putLong("username_refreshed_at", System.currentTimeMillis())
                            .apply();
                }
            }
            @Override public void onFailure(retrofit2.Call<String> call, Throwable t) { /* ignore */ }
        });
    }
}
