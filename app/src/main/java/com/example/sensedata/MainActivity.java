package com.example.sensedata;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.content.res.AppCompatResources;
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
import com.squareup.picasso.BuildConfig;

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

        if (!BuildConfig.DEBUG) {
            ApiClientMain.setHttpLoggingEnabled(false); // вимкнути логи в релізі
        }

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
                room -> {
                    // 📌 перехід у SensorDataActivity
                    Intent intent = new Intent(this, SensorDataActivity.class);
                    intent.putExtra(SensorDataActivity.EXTRA_CHIP_ID, room.getChipId());
                    intent.putExtra(SensorDataActivity.EXTRA_ROOM_NAME, room.getRoomName());
                    startActivity(intent);
                },
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
        Context wrapper = new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_App_PopupMenu);
        PopupMenu menu = new PopupMenu(wrapper, anchor, Gravity.END);
        if (android.os.Build.VERSION.SDK_INT >= 23) menu.setGravity(Gravity.END);

        menu.getMenu().add(0, 1, 0, "Оновити кімнату");
        menu.getMenu().add(0, 2, 1, "Оновити Wi-Fi");
        menu.getMenu().add(0, 3, 2, "Видалити кімнату");

        // страховка, якщо OEM ігнорує текстові стилі
        tintPopupMenuText(menu, R.color.weather_card_text);

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showEditRoomDialog(room); return true;
                case 2: showWifiDialog(room);    return true;
                case 3: confirmAndDeleteRoom(room); return true;
            }
            return false;
        });
        menu.show();
    }

    private void tintPopupMenuText(PopupMenu menu, @androidx.annotation.ColorRes int colorRes) {
        int c = androidx.core.content.ContextCompat.getColor(this, colorRes);
        for (int i = 0; i < menu.getMenu().size(); i++) {
            android.view.MenuItem mi = menu.getMenu().getItem(i);
            android.text.SpannableString s = new android.text.SpannableString(mi.getTitle());
            s.setSpan(new android.text.style.ForegroundColorSpan(c), 0, s.length(), 0);
            mi.setTitle(s);
        }
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
        final String chipId = room.getChipId();  // ← беремо звідси
        if (chipId == null || chipId.trim().isEmpty()) {
            android.util.Log.e("EDIT_ROOM", "Вибрана кімната без chipId. room=" + room.getRoomName());
            android.widget.Toast.makeText(this, "У кімнати немає chipId. Онови список (pull-to-refresh).", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_room, null);
        EditText etName = view.findViewById(R.id.etRoomName);

        FrameLayout[] containers = new FrameLayout[] {
                view.findViewById(R.id.container1),
                view.findViewById(R.id.container2),
                view.findViewById(R.id.container3),
                view.findViewById(R.id.container4)
        };
        ImageView[] imageViews = new ImageView[] {
                view.findViewById(R.id.img1),
                view.findViewById(R.id.img2),
                view.findViewById(R.id.img3),
                view.findViewById(R.id.img4)
        };

        etName.setText(room.getRoomName());

        final int[] selectedIndex = {-1};
        final String[] selectedImage = {room.getImageName()}; // попереднє значення

        // виділення зображення
        for (int i = 0; i < imageViews.length; i++) {
            final int idx = i;
            imageViews[i].setOnClickListener(v -> {
                for (FrameLayout c : containers) c.setSelected(false);
                containers[idx].setSelected(true); // селектор сам намалює рамку
                for (ImageView iv : imageViews) { iv.setScaleX(1f); iv.setScaleY(1f); }
                v.setScaleX(0.95f); v.setScaleY(0.95f);
                selectedIndex[0] = idx;
                Object tag = v.getTag();
                selectedImage[0] = tag == null ? null : tag.toString();
            });
        }
        // попередньо підсвітити поточне зображення
        if (room.getImageName() != null) {
            for (int i = 0; i < imageViews.length; i++) {
                Object tag = imageViews[i].getTag();
                if (tag != null && tag.toString().equals(room.getImageName())) {
                    imageViews[i].performClick();
                    break;
                }
            }
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("Оновити кімнату")
                .setView(view)
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Зберегти", null)   // щоб не закривати одразу
                .create();

        dlg.setOnShowListener(di -> {
            applyDialogBg(dlg);
            styleDialogTextAndButtons(dlg, R.color.weather_card_text);

            android.widget.Button btn = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String newName = etName.getText()==null ? "" : etName.getText().toString().trim();
                if (newName.isEmpty()) { etName.setError("Введіть назву"); return; }
                if (selectedImage[0] == null) { Toast.makeText(this,"Оберіть зображення",Toast.LENGTH_SHORT).show(); return; }
                doPutUpdateOwnership(room.getChipId(), newName, selectedImage[0]);
                dlg.dismiss();
            });
        });
        dlg.show();

    }

    private void doPutUpdateOwnership(String chipId, String newName, String newImage) {
        RoomApiService api = ApiClientMain.getClient(this).create(RoomApiService.class);
        SensorOwnershipUpdateDto body = new SensorOwnershipUpdateDto(chipId, newName, newImage);

        String ifMatch = getEtagForChip(chipId); // може бути null — бек прийме без передумови
        api.updateOwnership(ifMatch, body).enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> resp) {
                if (resp.isSuccessful()) {
                    // 204 No Content, збережемо новий ETag, якщо прийшов
                    String etag = resp.headers().get("ETag");
                    if (etag != null) saveEtagForChip(chipId, etag);

                    // локально оновлюємо картку
                    List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                    for (int i = 0; i < updated.size(); i++) {
                        RoomWithSensorDto r = updated.get(i);
                        if (r.getChipId()!=null && r.getChipId().equalsIgnoreCase(chipId)) {
                            updated.set(i, new RoomWithSensorDto(
                                    r.getId(), r.getChipId(), newName, newImage,
                                    r.getTemperature(), r.getHumidity()
                            ));
                            break;
                        }
                    }
                    roomAdapter.submitList(updated);
                    Toast.makeText(MainActivity.this, "Оновлено", Toast.LENGTH_SHORT).show();

                    // 🔁 додатково підтягнемо з сервера (щоб вирівняти стани/версії)
                    refreshRoomsData();
                    return;
                }

                // помилки
                int code = resp.code();
                String serverMsg = null;
                try { if (resp.errorBody()!=null) serverMsg = resp.errorBody().string(); } catch (Exception ignore) {}

                if (code == 412) {
                    Toast.makeText(MainActivity.this, "Дані змінені в іншому місці (412). Оновлюю список…", Toast.LENGTH_LONG).show();
                    refreshRoomsData();
                } else if (code == 404) {
                    Toast.makeText(MainActivity.this, "Пристрій не знайдено (404). Оновлюю список…", Toast.LENGTH_LONG).show();
                    refreshRoomsData();
                } else if (code == 409) {
                    Toast.makeText(MainActivity.this, "Конфлікт (409): " + (serverMsg!=null?serverMsg:""), Toast.LENGTH_LONG).show();
                    refreshRoomsData();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка PUT: " + code + (serverMsg!=null?(" • "+serverMsg):""), Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "PUT збій: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWifiDialog(RoomWithSensorDto room) {
        if (room == null || room.getChipId() == null || room.getChipId().trim().isEmpty()) {
            Toast.makeText(this, "У кімнати немає chipId. Онови список (pull-to-refresh).", Toast.LENGTH_LONG).show();
            return;
        }

        final View view = getLayoutInflater().inflate(R.layout.dialog_wifi, null);
        final EditText etSsid = view.findViewById(R.id.etSsid);
        final EditText etPass = view.findViewById(R.id.etPass);
        final TextView tvStatus = view.findViewById(R.id.tvWifiStatus);
        final com.google.android.material.progressindicator.CircularProgressIndicator prog =
                view.findViewById(R.id.progressWifi);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Оновити Wi-Fi")
                .setView(view)
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Надіслати", null)
                .create();

        dialog.setOnShowListener(d -> {
            applyDialogBg(dialog);
            styleDialogTextAndButtons(dialog, R.color.weather_card_text);
        });
        dialog.show();

        final android.widget.Button btnPos = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        final android.widget.Button btnNeg = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

        final Runnable setBusyTrue = () -> {
            btnPos.setEnabled(false);
            btnNeg.setEnabled(false);
            etSsid.setEnabled(false);
            etPass.setEnabled(false);
            tvStatus.setText("Сканую ESP32…");
            tvStatus.setVisibility(View.VISIBLE);
            prog.setVisibility(View.VISIBLE);
        };
        final Runnable setBusyFalse = () -> {
            btnPos.setEnabled(true);
            btnNeg.setEnabled(true);
            etSsid.setEnabled(true);
            etPass.setEnabled(true);
            tvStatus.setVisibility(View.GONE);
            prog.setVisibility(View.GONE);
        };

        dialog.setOnDismissListener(d -> { try { bleManager.stopScan(); } catch (Exception ignore) {} });

        btnPos.setOnClickListener(v -> {
            final String ssid = etSsid.getText() == null ? "" : etSsid.getText().toString().trim();
            final String pass = etPass.getText() == null ? "" : etPass.getText().toString();
            if (ssid.isEmpty()) { etSsid.setError("Введіть SSID"); return; }

            if (!bleManager.isBluetoothSupported()) { Toast.makeText(this,"BLE недоступний",Toast.LENGTH_SHORT).show(); return; }
            if (!bleManager.isBluetoothEnabled())   { startActivity(bleManager.getEnableBluetoothIntent()); return; }
            if (!bleManager.hasAllBlePermissions()) { bleManager.requestAllBlePermissions(this, 42); return; }

            final String chipId = room.getChipId().trim().toUpperCase(java.util.Locale.ROOT);
            if (chipId.length() < 6) { Toast.makeText(this,"chipId некоректний",Toast.LENGTH_SHORT).show(); return; }
            final String targetName = "ESP32_" + chipId;

            setBusyTrue.run();
            tvStatus.setText("Сканую ESP32 (" + targetName + ")…");

            bleManager.startBleScan(4000, (names, devices) -> runOnUiThread(() -> {
                android.bluetooth.BluetoothDevice target = null;
                for (int i = 0; i < names.size(); i++) {
                    if (targetName.equalsIgnoreCase(names.get(i))) { target = devices.get(i); break; }
                }
                if (target == null) { tvStatus.setText("ESP " + targetName + " не знайдено"); setBusyFalse.run(); return; }

                tvStatus.setText("Надсилаю Wi-Fi на " + targetName + "…");
                try { bleManager.stopScan(); } catch (Exception ignore) {}

                bleManager.sendWifiPatchViaDevice(target, ssid, pass, new BleManager.WifiPatchCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> {
                            setBusyFalse.run();
                            if (dialog.isShowing()) dialog.dismiss(); // один dismiss
                            Toast.makeText(MainActivity.this, "Wi-Fi успішно оновлено", Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            tvStatus.setText(message == null ? "Помилка BLE" : message);
                            setBusyFalse.run(); // лишаємо діалог відкритим
                        });
                    }
                });
            }));
        });
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
        String targetName = "ESP32_" + chipId;

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

    private void confirmAndDeleteRoom(RoomWithSensorDto room) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Видалити кімнату?")
                .setMessage("Це відв'яже пристрій від вашого акаунта.")
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Видалити", (d, w) -> doDeleteRoom(room))
                .create();

        dialog.setOnShowListener(d -> {
            applyDialogBg(dialog);
            styleDialogTextAndButtons(dialog, R.color.weather_card_text);
        });
        dialog.show();
    }

    private String getEtagForChip(String chipId) {
        return getSharedPreferences("etag_store", MODE_PRIVATE)
                .getString("etag_" + chipId, null);
    }

    private void saveEtagForChip(String chipId, String etag) {
        if (etag == null) return;
        getSharedPreferences("etag_store", MODE_PRIVATE)
                .edit().putString("etag_" + chipId, etag).apply();
    }

    private void removeEtagForChip(String chipId) {
        getSharedPreferences("etag_store", MODE_PRIVATE)
                .edit().remove("etag_" + chipId).apply();
    }

    private void doDeleteRoom(RoomWithSensorDto room) {
        int userId = getSavedUserId();
        if (userId == -1 || room.getChipId() == null) {
            Toast.makeText(this, "Немає userId або chipId", Toast.LENGTH_SHORT).show(); return;
        }
        RoomApiService api = ApiClientMain.getClient(this).create(RoomApiService.class);
        api.deleteOwnership(room.getChipId(), userId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) {
                    // локально
                    List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                    updated.removeIf(r -> r.getChipId()!=null &&
                            r.getChipId().equalsIgnoreCase(room.getChipId()));
                    roomAdapter.submitList(updated);

                    // прибираємо ETag для цього chipId
                    removeEtagForChip(room.getChipId());

                    Toast.makeText(MainActivity.this, "Кімнату видалено", Toast.LENGTH_SHORT).show();

                    // 🔁 підтягнути список з сервера
                    refreshRoomsData();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка DELETE: " + resp.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "DELETE збій: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyDialogBg(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    AppCompatResources.getDrawable(this, R.drawable.dialog_surface_bg));
        }
    }

    private void styleDialogTextAndButtons(AlertDialog dialog, @androidx.annotation.ColorRes int colorRes) {
        int color = androidx.core.content.ContextCompat.getColor(this, colorRes);

        // Заголовок
        TextView titleView = null;
        int id = getResources().getIdentifier("alertTitle", "id", getPackageName());
        if (id != 0) titleView = dialog.findViewById(id);
        if (titleView == null) {
            try { titleView = dialog.findViewById(com.google.android.material.R.id.alertTitle); } catch (Exception ignore) {}
        }
        if (titleView == null) {
            id = getResources().getIdentifier("alertTitle", "id", "android");
            if (id != 0) titleView = dialog.findViewById(id);
        }
        if (titleView != null) {
            titleView.setTextColor(color);
            titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        }

        // Повідомлення
        TextView msg = dialog.findViewById(android.R.id.message);
        if (msg != null) msg.setTextColor(color);

        // Кнопки
        android.widget.Button bPos = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        android.widget.Button bNeg = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        android.widget.Button bNeu = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (bPos != null) { bPos.setTextColor(color); bPos.setAllCaps(false); }
        if (bNeg != null) { bNeg.setTextColor(color); bNeg.setAllCaps(false); }
        if (bNeu != null) { bNeu.setTextColor(color); bNeu.setAllCaps(false); }
    }
}
