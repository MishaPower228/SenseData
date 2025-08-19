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
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipUpdateDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.UserApiService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends ImmersiveActivity {

    private WeatherManager weatherManager;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;

    private String lastCreatedRoomName = null;
    private String lastCreatedImageName = null;

    private BleManager bleManager;
    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    private ActivityResultLauncher<Intent> createRoomLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bleManager = new BleManager(this);

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        if (prefs.getString("username", null) == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        TextView title = toolbar.findViewById(R.id.toolbar_title);

        // Підписи
        TextView labelWeather = findViewById(R.id.labelWeather);
        TextView labelRooms   = findViewById(R.id.labelRooms);
        labelWeather.setPaintFlags(labelWeather.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        labelRooms.setPaintFlags(labelRooms.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Привітання
        String cached = getSavedUsername();
        setHello(title, (cached != null) ? cached : getString(R.string.guest));
        refreshUsernameFromServer(title);

        // Погода
        weatherManager = new WeatherManager(this);
        weatherManager.startWeatherUpdates();

        // RecyclerView
        setupRoomsRecycler();

        // FAB -> CreateRoomActivity
        createRoomLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String chipId = result.getData().getStringExtra("chipId");
                        lastCreatedRoomName  = result.getData().getStringExtra("roomName");
                        lastCreatedImageName = result.getData().getStringExtra("imageName");
                        onChipIdReceivedFromEsp32(chipId);
                    }
                }
        );

        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v ->
                createRoomLauncher.launch(new Intent(this, CreateRoomActivity.class))
        );

        // Завантаження з сервера + періодичне оновлення
        loadRoomsFromServer();
        startPeriodicRoomRefresh();
    }

    private void setupRoomsRecycler() {
        roomRecyclerView = findViewById(R.id.room_recycler_view);

        // Горизонтальний LayoutManager
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        roomRecyclerView.setLayoutManager(lm);

        // Адаптер
        roomAdapter = new RoomAdapter(
                room -> { /* onClick за потреби */ },
                (anchor, room) -> showRoomPopup(anchor, room)
        );
        roomRecyclerView.setAdapter(roomAdapter);

        // Розміри/анімації
        roomRecyclerView.setHasFixedSize(false);
        roomRecyclerView.setItemAnimator(null);

        // Відступи між картками лише по горизонталі (12dp)
        final int space = (int) (12 * getResources().getDisplayMetrics().density);
        roomRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view,
                                       RecyclerView parent, RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                outRect.top = 0; outRect.bottom = 0;
                outRect.left = (pos == 0) ? space : 0;
                outRect.right = space;
            }
        });

        // За потреби:
        // new LinearSnapHelper().attachToRecyclerView(roomRecyclerView);
    }

    private void startPeriodicRoomRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() {
                refreshRoomsData(); // лише серверні
                handler.postDelayed(this, 30_000);
            }
        };
        handler.post(refreshRunnable);
    }

    private void showRoomPopup(View anchor, RoomWithSensorDto room) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (android.os.Build.VERSION.SDK_INT >= 23) menu.setGravity(Gravity.END);

        menu.getMenu().add(0, 1, 0, "Оновити кімнату");
        menu.getMenu().add(0, 2, 1, "Оновити вайфай");

        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                showEditRoomDialog(room);
                return true;
            } else if (id == 2) {
                showWifiDialog(room);
                return true;
            }
            return false;
        });

        menu.show();
    }

    private void refreshRoomsData() {
        int userId = getSavedUserId();
        if (userId == -1) return;

        RoomApiService apiService = ApiClientMain.getClient(MainActivity.this).create(RoomApiService.class);
        apiService.getAllRooms(userId).enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    roomAdapter.submitList(new ArrayList<>(response.body())); // тільки сервер
                }
            }
            @Override public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) { /* ignore */ }
        });
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
            public void onResponse(Call<List<RoomWithSensorDto>> call,
                                   Response<List<RoomWithSensorDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this, "Не вдалося отримати кімнати", Toast.LENGTH_SHORT).show();
                    return;
                }
                roomAdapter.submitList(new ArrayList<>(response.body())); // тільки сервер
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

    private int getSavedUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getInt("userId", -1);
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

    private void showEditRoomDialog(RoomWithSensorDto room) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_room, null);
        EditText etName  = view.findViewById(R.id.etRoomName);
        EditText etImage = view.findViewById(R.id.etImageName);

        etName.setText(room.getRoomName());
        etName.setSelection(etName.getText().length());
        etImage.setText(room.getImageName());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Змінити атрибути")
                .setView(view)
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Зберегти", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newImg  = etImage.getText().toString().trim();
                    if (newName.isEmpty()) {
                        etName.setError("Введіть назву");
                        return;
                    }
                    doPutUpdateOwnership(room.getChipId(), newName, newImg);
                })
                .show();
    }

    private void doPutUpdateOwnership(String chipId, String newName, String newImage) {
        RoomApiService api = ApiClientMain.getClient(this).create(RoomApiService.class);
        SensorOwnershipUpdateDto body = new SensorOwnershipUpdateDto(chipId, newName, newImage);

        api.updateOwnership(body).enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> resp) {
                if (resp.isSuccessful()) {
                    List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                    for (int i = 0; i < updated.size(); i++) {
                        RoomWithSensorDto r = updated.get(i);
                        if (r.getChipId() != null && r.getChipId().equalsIgnoreCase(chipId)) {
                            RoomWithSensorDto copy = new RoomWithSensorDto(
                                    r.getId(), r.getChipId(),
                                    newName, newImage,
                                    r.getTemperature(), r.getHumidity()
                            );
                            updated.set(i, copy);
                            break;
                        }
                    }
                    roomAdapter.submitList(updated);
                    Toast.makeText(MainActivity.this, "Оновлено", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка PUT: " + resp.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "PUT збій: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWifiDialog(RoomWithSensorDto room) {
        View view = getLayoutInflater().inflate(R.layout.dialog_wifi, null);
        EditText etSsid = view.findViewById(R.id.etSsid);
        EditText etPass = view.findViewById(R.id.etPass);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Оновити Wi-Fi")
                .setView(view)
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("ОК", (d, w) -> {
                    String ssid = etSsid.getText().toString().trim();
                    String pass = etPass.getText().toString();
                    if (ssid.isEmpty()) { etSsid.setError("Введіть SSID"); return; }
                    sendWifiForRoom(room, ssid, pass);
                })
                .show();
    }

    private void sendWifiForRoom(RoomWithSensorDto room, String ssid, String pass) {
        if (!bleManager.isBluetoothSupported()) {
            Toast.makeText(this, "BLE недоступний", Toast.LENGTH_SHORT).show(); return;
        }
        if (!bleManager.isBluetoothEnabled()) {
            startActivity(bleManager.getEnableBluetoothIntent()); return;
        }
        if (!bleManager.hasAllBlePermissions()) {
            bleManager.requestAllBlePermissions(this, 42);
            return;
        }

        String chipId = room.getChipId() == null ? "" : room.getChipId().trim().toUpperCase(Locale.ROOT);
        if (chipId.length() < 6) {
            Toast.makeText(this, "chipId некоректний", Toast.LENGTH_SHORT).show();
            return;
        }
        String targetName = "ESP32_" + chipId.substring(chipId.length() - 6);

        bleManager.startBleScan(4000, (names, devices) -> {
            android.bluetooth.BluetoothDevice target = null;
            for (int i = 0; i < names.size(); i++) {
                if (targetName.equalsIgnoreCase(names.get(i))) {
                    target = devices.get(i);
                    break;
                }
            }
            if (target == null) {
                Toast.makeText(this, "ESP " + targetName + " не знайдено", Toast.LENGTH_SHORT).show();
                return;
            }
            bleManager.sendWifiPatchViaDevice(target, ssid, pass);
        });
    }
}
