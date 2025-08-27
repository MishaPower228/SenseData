package com.example.sensedata;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipUpdateDto;
import com.example.sensedata.model.SensorPointDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.SensorDataApiService;
import com.example.sensedata.network.UserApiService;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends ImmersiveActivity {

    private WeatherManager weatherManager;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;

    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    private ActivityResultLauncher<Intent> createRoomLauncher;

    // 📊 Графік + кнопки
    private LineChart chart;
    private MaterialButton btnDay, btnWeek;
    private MaterialButton btnTemperature, btnHumidity;

    // Стан графіка
    private boolean isDaySelected = true;      // день чи тиждень
    private boolean showTemperature = true;    // 👈 що показувати: true = температура, false = вологість

    // Чіпи кімнат + стан
    private ChipGroup chipGroupRooms;
    private final List<RoomWithSensorDto> roomsCache = new ArrayList<>();
    private String selectedChipId = null;   // кімната для графіка

    private static final int PERMISSION_REQUEST_CODE = 1001;

    // 🔵 BLE для оновлення Wi-Fi
    private BleManager bleManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        setContentView(R.layout.activity_main);

        // 👇 Insets для cutout (чубчика)
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            Toolbar toolbar = findViewById(R.id.custom_toolbar);
            if (toolbar != null) {
                toolbar.setPadding(
                        toolbar.getPaddingLeft(),
                        cutoutInsets.top,   // відступ під notch
                        toolbar.getPaddingRight(),
                        toolbar.getPaddingBottom()
                );
            }
            return insets;
        });

        requestAllPermissions();

        // SwipeRefreshLayout
        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.main_color)
        );
        swipeRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(this, R.color.bg_weather_card)
        );
        swipeRefresh.setOnRefreshListener(() -> {
            weatherManager.startWeatherUpdates();
            loadRoomsFromServer();
            updateChartForSelection();
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1000);
        });

        // --- Toolbar ---
        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        TextView title = toolbar.findViewById(R.id.toolbar_title);
        String cached = getSavedUsername();
        setHello(title, (cached != null) ? cached : getString(R.string.guest));
        refreshUsernameFromServer(title);
        // --- /Toolbar ---

        // Підписи секцій
        TextView labelWeather = findViewById(R.id.labelWeather);
        TextView labelRooms   = findViewById(R.id.labelRooms);
        TextView labelCharts  = findViewById(R.id.labelCharts);
        labelWeather.setPaintFlags(labelWeather.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        labelRooms.setPaintFlags(labelRooms.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        labelCharts.setPaintFlags(labelCharts.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Погода
        weatherManager = new WeatherManager(this);
        weatherManager.startWeatherUpdates();

        // BLE
        bleManager = new BleManager(this);

        // Список кімнат
        setupRoomsRecycler();

        // FAB -> CreateRoomActivity
        createRoomLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String chipId = result.getData().getStringExtra("chipId");
                        String roomName = result.getData().getStringExtra("roomName");
                        String imageName = result.getData().getStringExtra("imageName");
                    }
                }
        );
        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v ->
                createRoomLauncher.launch(new Intent(this, CreateRoomActivity.class))
        );

        // 📊 Графік
        chart  = findViewById(R.id.chart);
        btnDay = findViewById(R.id.btnDay);
        btnWeek = findViewById(R.id.btnWeek);
        btnTemperature = findViewById(R.id.btnTemperature);
        btnHumidity = findViewById(R.id.btnHumidity);
        setupChart(chart);

        // ChipGroup кімнат
        chipGroupRooms = findViewById(R.id.chipGroupRooms);

        // Кнопки ДЕНЬ/ТИЖДЕНЬ
        btnDay.setOnClickListener(v -> {
            highlightButton(btnDay, btnWeek);
            isDaySelected = true;
            updateChartForSelection();
        });
        btnWeek.setOnClickListener(v -> {
            highlightButton(btnWeek, btnDay);
            isDaySelected = false;
            updateChartForSelection();
        });
        highlightButton(btnDay, btnWeek);

        // Кнопки ТЕМПЕРАТУРА / ВОЛОГІСТЬ
        btnTemperature.setOnClickListener(v -> {
            highlightButton(btnTemperature, btnHumidity);
            showTemperature = true;
            updateChartForSelection();
        });
        btnHumidity.setOnClickListener(v -> {
            highlightButton(btnHumidity, btnTemperature);
            showTemperature = false;
            updateChartForSelection();
        });
        highlightButton(btnTemperature, btnHumidity);

        // Завантаження кімнат
        loadRoomsFromServer();
        startPeriodicRoomRefresh();
    }
    private void setupRoomsRecycler() {
        roomRecyclerView = findViewById(R.id.room_recycler_view);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        roomRecyclerView.setLayoutManager(lm);

        roomAdapter = new RoomAdapter(
                room -> {
                    Intent intent = new Intent(this, SensorDataActivity.class);
                    intent.putExtra(SensorDataActivity.EXTRA_CHIP_ID, room.getChipId());
                    intent.putExtra(SensorDataActivity.EXTRA_ROOM_NAME, room.getRoomName());
                    startActivity(intent);
                },
                // 👉 ЛОНГТАП: показуємо поп-меню
                (anchor, room) -> showRoomPopup(anchor, room)
        );
        roomRecyclerView.setAdapter(roomAdapter);
    }

    // ---------- ПОП-МЕНЮ ТА ПОВ’ЯЗАНЕ ----------

    private void showRoomPopup(View anchor, RoomWithSensorDto room) {
        Context wrapper = new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_App_PopupMenu);
        PopupMenu menu = new PopupMenu(wrapper, anchor, Gravity.END);
        if (android.os.Build.VERSION.SDK_INT >= 23) menu.setGravity(Gravity.END);

        menu.getMenu().add(0, 1, 0, "Оновити кімнату");
        menu.getMenu().add(0, 2, 1, "Оновити Wi-Fi");
        menu.getMenu().add(0, 3, 2, "Видалити кімнату");

        tintPopupMenuText(menu, R.color.weather_card_text);

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showEditRoomDialog(room); return true;
                case 2: showWifiDialog(room);     return true;
                case 3: confirmAndDeleteRoom(room); return true;
            }
            return false;
        });
        menu.show();
    }

    private void tintPopupMenuText(PopupMenu menu, @androidx.annotation.ColorRes int colorRes) {
        int c = ContextCompat.getColor(this, colorRes);
        for (int i = 0; i < menu.getMenu().size(); i++) {
            android.view.MenuItem mi = menu.getMenu().getItem(i);
            SpannableString s = new SpannableString(mi.getTitle());
            s.setSpan(new android.text.style.ForegroundColorSpan(c), 0, s.length(), 0);
            mi.setTitle(s);
        }
    }

    private void showEditRoomDialog(RoomWithSensorDto room) {
        final String chipId = room.getChipId();
        if (chipId == null || chipId.trim().isEmpty()) {
            Toast.makeText(this, "У кімнати немає chipId. Онови список.", Toast.LENGTH_LONG).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_edit_room, null);
        EditText etName = view.findViewById(R.id.etRoomName);

        FrameLayout[] containers = new FrameLayout[] {
                view.findViewById(R.id.container1),
                view.findViewById(R.id.container2),
                view.findViewById(R.id.container3),
                view.findViewById(R.id.container4),
                view.findViewById(R.id.container5),
                view.findViewById(R.id.container6)
        };
        ImageView[] imageViews = new ImageView[] {
                view.findViewById(R.id.img1),
                view.findViewById(R.id.img2),
                view.findViewById(R.id.img3),
                view.findViewById(R.id.img4),
                view.findViewById(R.id.img5),
                view.findViewById(R.id.img6)
        };

        etName.setText(room.getRoomName());
        final int[] selectedIndex = {-1};
        final String[] selectedImage = { room.getImageName() };

        for (int i = 0; i < imageViews.length; i++) {
            final int idx = i;
            imageViews[i].setOnClickListener(v -> {
                for (FrameLayout c : containers) c.setSelected(false);
                containers[idx].setSelected(true);
                for (ImageView iv : imageViews) { iv.setScaleX(1f); iv.setScaleY(1f); }
                v.setScaleX(0.95f); v.setScaleY(0.95f);
                selectedIndex[0] = idx;
                Object tag = v.getTag();
                selectedImage[0] = tag == null ? null : tag.toString();
            });
        }

        // Підсвічуємо поточну картинку
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
                .setPositiveButton("Зберегти", null)
                .create();

        dlg.setOnShowListener(di -> {
            applyDialogBg(dlg);
            styleDialogTextAndButtons(dlg, R.color.weather_card_text);
            android.widget.Button btn = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
                if (newName.isEmpty()) { etName.setError("Введіть назву"); return; }
                if (selectedImage[0] == null) {
                    Toast.makeText(this, "Оберіть зображення", Toast.LENGTH_SHORT).show();
                    return;
                }
                doPutUpdateOwnership(chipId, newName, selectedImage[0]);
                dlg.dismiss();
            });
        });
        dlg.show();
    }

    private void doPutUpdateOwnership(String chipId, String newName, String newImage) {
        RoomApiService api = ApiClientMain.getClient(this).create(RoomApiService.class);
        SensorOwnershipUpdateDto body = new SensorOwnershipUpdateDto(chipId, newName, newImage);
        String ifMatch = getEtagForChip(chipId); // може бути null

        api.updateOwnership(ifMatch, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) {
                    String etag = resp.headers().get("ETag");
                    if (etag != null) saveEtagForChip(chipId, etag);

                    // Локально оновлюємо картку
                    List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                    for (int i = 0; i < updated.size(); i++) {
                        RoomWithSensorDto r = updated.get(i);
                        if (r.getChipId()!=null && r.getChipId().equalsIgnoreCase(chipId)) {
                            updated.set(i, new RoomWithSensorDto(
                                    r.getId(), r.getChipId(), newName, newImage, r.getTemperature(), r.getHumidity()
                            ));
                            break;
                        }
                    }
                    roomAdapter.submitList(updated);
                    Toast.makeText(MainActivity.this, "Оновлено", Toast.LENGTH_SHORT).show();

                    // Дотягуємо з сервера
                    loadRoomsFromServer();
                    return;
                }

                int code = resp.code();
                String serverMsg = null;
                try { if (resp.errorBody()!=null) serverMsg = resp.errorBody().string(); } catch (Exception ignore) {}
                if (code == 412) {
                    Toast.makeText(MainActivity.this, "Дані змінені в іншому місці (412). Оновлюю список…", Toast.LENGTH_LONG).show();
                    loadRoomsFromServer();
                } else if (code == 404) {
                    Toast.makeText(MainActivity.this, "Пристрій не знайдено (404). Оновлюю список…", Toast.LENGTH_LONG).show();
                    loadRoomsFromServer();
                } else if (code == 409) {
                    Toast.makeText(MainActivity.this, "Конфлікт (409): " + (serverMsg!=null?serverMsg:""), Toast.LENGTH_LONG).show();
                    loadRoomsFromServer();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка PUT: " + code + (serverMsg!=null?(" • "+serverMsg):""), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "PUT збій: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWifiDialog(RoomWithSensorDto room) {
        if (room == null || room.getChipId() == null || room.getChipId().trim().isEmpty()) {
            Toast.makeText(this, "У кімнати немає chipId. Онови список.", Toast.LENGTH_LONG).show();
            return;
        }

        final View view = getLayoutInflater().inflate(R.layout.dialog_wifi, null);
        final EditText etSsid = view.findViewById(R.id.etSsid);
        final EditText etPass = view.findViewById(R.id.etPass);
        final TextView tvStatus = view.findViewById(R.id.tvWifiStatus);
        final CircularProgressIndicator prog = view.findViewById(R.id.progressWifi);

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
            btnPos.setEnabled(false); btnNeg.setEnabled(false);
            etSsid.setEnabled(false); etPass.setEnabled(false);
            tvStatus.setText("Сканую ESP32…");
            tvStatus.setVisibility(View.VISIBLE);
            prog.setVisibility(View.VISIBLE);
        };
        final Runnable setBusyFalse = () -> {
            btnPos.setEnabled(true); btnNeg.setEnabled(true);
            etSsid.setEnabled(true); etPass.setEnabled(true);
            tvStatus.setVisibility(View.GONE);
            prog.setVisibility(View.GONE);
        };

        dialog.setOnDismissListener(d -> {
            try { bleManager.stopScan(); } catch (Exception ignore) {}
        });

        btnPos.setOnClickListener(v -> {
            final String ssid = etSsid.getText()==null ? "" : etSsid.getText().toString().trim();
            final String pass = etPass.getText()==null ? "" : etPass.getText().toString();
            if (ssid.isEmpty()) { etSsid.setError("Введіть SSID"); return; }

            if (!bleManager.isBluetoothSupported()) { Toast.makeText(this,"BLE недоступний",Toast.LENGTH_SHORT).show(); return; }
            if (!bleManager.isBluetoothEnabled()) { startActivity(bleManager.getEnableBluetoothIntent()); return; }
            if (!bleManager.hasAllBlePermissions()) { bleManager.requestAllBlePermissions(this, 42); return; }

            final String chipId = room.getChipId().trim().toUpperCase(Locale.ROOT);
            if (chipId.length() < 6) { Toast.makeText(this,"chipId некоректний",Toast.LENGTH_SHORT).show(); return; }
            final String targetName = "ESP32_" + chipId;

            setBusyTrue.run();
            tvStatus.setText("Сканую ESP32 (" + targetName + ")…");

            bleManager.startBleScan(4000, (names, devices) -> runOnUiThread(() -> {
                android.bluetooth.BluetoothDevice target = null;
                for (int i = 0; i < names.size(); i++) {
                    if (targetName.equalsIgnoreCase(names.get(i))) {
                        target = devices.get(i);
                        break;
                    }
                }
                if (target == null) {
                    tvStatus.setText("ESP " + targetName + " не знайдено");
                    setBusyFalse.run();
                    return;
                }
                tvStatus.setText("Надсилаю Wi-Fi на " + targetName + "…");
                try { bleManager.stopScan(); } catch (Exception ignore) {}

                bleManager.sendWifiPatchViaDevice(target, ssid, pass, new BleManager.WifiPatchCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> {
                            setBusyFalse.run();
                            if (dialog.isShowing()) dialog.dismiss();
                            Toast.makeText(MainActivity.this, "Wi-Fi успішно оновлено", Toast.LENGTH_SHORT).show();
                            if (dialog.isShowing()) {
                                dialog.dismiss();   // 👈 переніс після тосту, щоб точно закривалось
                            }
                        });
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            tvStatus.setText(message == null ? "Помилка BLE" : message);
                            setBusyFalse.run();
                        });
                    }
                });
            }));
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

    private void doDeleteRoom(RoomWithSensorDto room) {
        int userId = getSavedUserId();
        if (userId == -1 || room.getChipId() == null) {
            Toast.makeText(this, "Немає userId або chipId", Toast.LENGTH_SHORT).show();
            return;
        }
        RoomApiService api = ApiClientMain.getClient(this).create(RoomApiService.class);
        api.deleteOwnership(room.getChipId(), userId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) {
                    List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                    updated.removeIf(r -> r.getChipId()!=null && r.getChipId().equalsIgnoreCase(room.getChipId()));
                    roomAdapter.submitList(updated);
                    removeEtagForChip(room.getChipId());
                    Toast.makeText(MainActivity.this, "Кімнату видалено", Toast.LENGTH_SHORT).show();
                    loadRoomsFromServer();
                } else {
                    Toast.makeText(MainActivity.this, "Помилка DELETE: " + resp.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "DELETE збій: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- ETag helpers ----------
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

    // ---------- Діалогова стилізація ----------
    private void applyDialogBg(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    AppCompatResources.getDrawable(this, R.drawable.dialog_surface_bg));
        }
    }
    private void styleDialogTextAndButtons(AlertDialog dialog, @androidx.annotation.ColorRes int colorRes) {
        int color = ContextCompat.getColor(this, colorRes);

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

        TextView msg = dialog.findViewById(android.R.id.message);
        if (msg != null) msg.setTextColor(color);

        android.widget.Button bPos = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        android.widget.Button bNeg = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        android.widget.Button bNeu = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (bPos != null) { bPos.setTextColor(color); bPos.setAllCaps(false); }
        if (bNeg != null) { bNeg.setTextColor(color); bNeg.setAllCaps(false); }
        if (bNeu != null) { bNeu.setTextColor(color); bNeu.setAllCaps(false); }
    }

    // ---------- Графік ----------
    private void setupChart(LineChart chart) {
        int txtColor = androidx.core.content.ContextCompat.getColor(this, R.color.weather_card_text);

        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(txtColor);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(txtColor);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        chart.getAxisLeft().setTextColor(txtColor);
        chart.getAxisRight().setEnabled(false);

        chart.setNoDataText("Немає даних");
        chart.setNoDataTextColor(txtColor);
    }

    private void setChartDataFromApi(List<SensorPointDto> points, boolean isDay) {
        int txtColor = ContextCompat.getColor(this, R.color.weather_card_text);

        if (points == null || points.isEmpty()) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<Entry> temp = new ArrayList<>();
        List<Entry> hum  = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        ZoneId kyiv = ZoneId.of("Europe/Kyiv");
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm", new Locale("uk", "UA"));
        DateTimeFormatter ddMM = DateTimeFormatter.ofPattern("d.MM", new Locale("uk", "UA"));

        for (int i = 0; i < points.size(); i++) {
            SensorPointDto p = points.get(i);
            temp.add(new Entry(i, (float) p.temperature));
            hum .add(new Entry(i, (float) p.humidity));

            ZonedDateTime local = Instant.parse(p.timestampUtc).atZone(kyiv);
            labels.add(isDay ? local.toLocalTime().format(hhmm) : local.toLocalDate().format(ddMM));
        }

        // --- DataSet
        LineDataSet dataSet;
        final boolean isTemp = showTemperature;
        if (isTemp) {
            dataSet = new LineDataSet(temp, "Температура °C");
            dataSet.setColor(Color.RED);
            dataSet.setCircleColor(Color.RED);
            dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%.0f°", value);
                }
            });
        } else {
            dataSet = new LineDataSet(hum, "Вологість %");
            dataSet.setColor(Color.BLUE);
            dataSet.setCircleColor(Color.BLUE);
            dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%.0f%%", value); // 👈 тільки ціле число
                }
            });
        }
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(txtColor);
        dataSet.setValueTextSize(10f);

        // --- X-вісь
        XAxis x = chart.getXAxis();
        x.setGranularity(1f);
        x.setLabelCount(Math.min(6, labels.size()), true);
        x.setTextColor(txtColor);
        x.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getAxisLabel(float value, AxisBase axis) {
                int i = (int) value;
                return (i >= 0 && i < labels.size()) ? labels.get(i) : "";
            }
        });

        // --- Y-вісь
        YAxis left = chart.getAxisLeft();
        chart.getAxisRight().setEnabled(false);
        left.setTextColor(txtColor);
        left.setAxisLineColor(txtColor);
        left.setXOffset(12f); // 👈 цифри Y-осі відсунуті лівіше

        // межі по даним
        List<Entry> series = isTemp ? temp : hum;
        float dataMin = Float.MAX_VALUE, dataMax = -Float.MAX_VALUE;
        for (Entry e : series) {
            float y = e.getY();
            if (y < dataMin) dataMin = y;
            if (y > dataMax) dataMax = y;
        }
        float lowerLimit = dataMin - 5f;
        float upperLimit = dataMax + 5f;

        left.setAxisMinimum(lowerLimit);
        left.setAxisMaximum(upperLimit);

        // --- LimitLine (без підписів, тільки пунктирні лінії)
        left.removeAllLimitLines();

        LimitLine up = new LimitLine(upperLimit);
        up.setLineWidth(1f);
        up.enableDashedLine(10f, 10f, 0f);
        up.setLineColor(txtColor);

        LimitLine lo = new LimitLine(lowerLimit);
        lo.setLineWidth(1f);
        lo.enableDashedLine(10f, 10f, 0f);
        lo.setLineColor(txtColor);

        left.addLimitLine(up);
        left.addLimitLine(lo);
        left.setDrawLimitLinesBehindData(true);

        // --- Загальні
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);

        // 👇 додаткові відступи з усіх боків
        chart.setExtraOffsets(3f, 18f, 30f, 12f);
        // порядок: left, top, right, bottom

        chart.setData(new LineData(dataSet));
        chart.invalidate();
    }


    // ---------- Завантаження серій ----------
    private void loadDayDataFor(String chipId) {
        SensorDataApiService api = ApiClientMain.getClient(this).create(SensorDataApiService.class);
        api.getDay(chipId).enqueue(new Callback<List<SensorPointDto>>() {
            @Override public void onResponse(Call<List<SensorPointDto>> call, Response<List<SensorPointDto>> resp) {
                if (resp.isSuccessful() && resp.body() != null && !resp.body().isEmpty()) {
                    setChartDataFromApi(resp.body(), true);
                } else {
                    chart.clear(); chart.setNoDataText("Немає даних за день");
                }
            }
            @Override public void onFailure(Call<List<SensorPointDto>> call, Throwable t) {
                chart.clear(); chart.setNoDataText("Помилка завантаження");
            }
        });
    }

    private void loadWeekDataFor(String chipId) {
        SensorDataApiService api = ApiClientMain.getClient(this).create(SensorDataApiService.class);
        api.getWeek(chipId).enqueue(new Callback<List<SensorPointDto>>() {
            @Override public void onResponse(Call<List<SensorPointDto>> call, Response<List<SensorPointDto>> resp) {
                if (resp.isSuccessful() && resp.body() != null && !resp.body().isEmpty()) {
                    setChartDataFromApi(resp.body(), false);
                } else {
                    chart.clear(); chart.setNoDataText("Немає даних за 7 днів");
                }
            }
            @Override public void onFailure(Call<List<SensorPointDto>> call, Throwable t) {
                chart.clear(); chart.setNoDataText("Помилка завантаження");
            }
        });
    }

    private void updateChartForSelection() {
        if (selectedChipId == null) {
            chart.clear();
            chart.setNoDataText("Виберіть кімнату");
            return;
        }
        if (isDaySelected) loadDayDataFor(selectedChipId);
        else               loadWeekDataFor(selectedChipId);
    }

    // ---------- Робота з кімнатами ----------
    private void startPeriodicRoomRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() {
                loadRoomsFromServer();
                handler.postDelayed(this, 30_000);
            }
        };
        handler.post(refreshRunnable);
    }

    private void loadRoomsFromServer() {
        int userId = getSavedUserId();
        if (userId == -1) return;

        RoomApiService apiService = ApiClientMain.getClient(this).create(RoomApiService.class);
        apiService.getAllRooms(userId).enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RoomWithSensorDto> rooms = new ArrayList<>(response.body());
                    roomAdapter.submitList(rooms);

                    roomsCache.clear();
                    roomsCache.addAll(rooms);
                    renderRoomChips(rooms);

                    if (selectedChipId == null && !roomsCache.isEmpty()) {
                        selectedChipId = roomsCache.get(0).getChipId();
                        checkChipByChipId(selectedChipId);
                    }

                    if (roomsCache.isEmpty()) {
                        selectedChipId = null;
                        chart.clear();
                        chart.setNoDataText("Немає кімнат");
                    } else {
                        // 👇 гарантовано оновлюємо графік після завантаження кімнат
                        updateChartForSelection();
                    }
                }
            }
            @Override public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) { }
        });
    }

    private void renderRoomChips(List<RoomWithSensorDto> rooms) {
        chipGroupRooms.setOnCheckedStateChangeListener(null);
        chipGroupRooms.removeAllViews();

        if (rooms == null || rooms.isEmpty()) {
            selectedChipId = null;
            chart.clear();
            chart.setNoDataText("Немає кімнат");
            return;
        }

        int toCheckId = View.NO_ID;

        for (int i = 0; i < rooms.size(); i++) {
            RoomWithSensorDto r = rooms.get(i);
            if (r == null || r.getChipId() == null) continue;

            Chip chip = new Chip(this, null,
                    com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice);
            chip.setId(View.generateViewId());

            String title = (r.getRoomName() == null || r.getRoomName().trim().isEmpty())
                    ? ("Кімната " + (i + 1))
                    : r.getRoomName();
            chip.setText(title);
            chip.setTag(r.getChipId());

            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxLines(1);

            chip.setChipBackgroundColor(
                    AppCompatResources.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(
                    AppCompatResources.getColorStateList(this, R.color.chip_text_selector));
            chip.setChipStrokeWidth(dp(2f));
            chip.setChipStrokeColor(
                    AppCompatResources.getColorStateList(this, R.color.chip_stroke_selector));
            chip.setRippleColor(
                    AppCompatResources.getColorStateList(this, R.color.chip_ripple_selector));

            chipGroupRooms.addView(chip);

            if (selectedChipId != null && selectedChipId.equals(r.getChipId())) {
                toCheckId = chip.getId();
            }
        }

        if (chipGroupRooms.getChildCount() > 0) {
            if (toCheckId != View.NO_ID) {
                chipGroupRooms.check(toCheckId);
            } else {
                View first = chipGroupRooms.getChildAt(0);
                chipGroupRooms.check(first.getId());
                Object tag = first.getTag();
                selectedChipId = (tag != null) ? tag.toString() : null;
            }
        }

        chipGroupRooms.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            View v = group.findViewById(id);
            if (!(v instanceof Chip)) return;

            String newChipId = String.valueOf(v.getTag());
            if (!newChipId.equals(selectedChipId)) {
                selectedChipId = newChipId;
                updateChartForSelection();
            }
        });
    }

    private void checkChipByChipId(String chipId) {
        for (int i = 0; i < chipGroupRooms.getChildCount(); i++) {
            View v = chipGroupRooms.getChildAt(i);
            if (v instanceof Chip && chipId.equals(v.getTag())) {
                chipGroupRooms.check(v.getId());
                selectedChipId = chipId;
                return;
            }
        }
    }

    // ---------- Допоміжні ----------
    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private String getSavedUsername() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getString("username", null);
    }

    private int getSavedUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getInt("userId", -1);
    }

    private void setHello(TextView title, String name) {
        String text = getString(R.string.hello_username, name);
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
                            .apply();
                }
            }
            @Override public void onFailure(retrofit2.Call<String> call, Throwable t) { }
        });
    }

    private void highlightButton(MaterialButton active, MaterialButton inactive) {
        active.setBackgroundColor(ContextCompat.getColor(this, R.color.main_color));
        active.setTextColor(Color.WHITE);

        inactive.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_weather_card));
        inactive.setTextColor(ContextCompat.getColor(this, R.color.weather_card_text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
        try { bleManager.stopScan(); } catch (Exception ignore) {}
    }

    private void requestAllPermissions() {
        String[] permissions = new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.POST_NOTIFICATIONS
        };

        List<String> toRequest = new ArrayList<>();
        for (String perm : permissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(perm);
            }
        }

        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    toRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            "Дозвіл " + permissions[i] + " не надано. Додаток може працювати некоректно.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
