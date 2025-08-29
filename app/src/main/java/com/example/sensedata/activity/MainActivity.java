package com.example.sensedata.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.sensedata.R;
import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.dialog_fragment.DeleteRoomDialogFragment;
import com.example.sensedata.dialog_fragment.EditRoomDialogFragment;
import com.example.sensedata.dialog_fragment.LogoutDialogFragment;
import com.example.sensedata.dialog_fragment.ProfileDialogFragment;
import com.example.sensedata.dialog_fragment.ThresholdDialogFragment;
import com.example.sensedata.dialog_fragment.WifiDialogFragment;
import com.example.sensedata.model.historychart.SensorPointDto;
import com.example.sensedata.model.room.RoomWithSensorDto;
import com.example.sensedata.model.sensorownership.SensorOwnershipRequestDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.SensorDataApiService;
import com.example.sensedata.network.UserApiService;
import com.example.sensedata.user.LoginActivity;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

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

public class MainActivity extends ImmersiveActivity
        implements EditRoomDialogFragment.OnRoomsChangedListener,
        DeleteRoomDialogFragment.OnRoomsChangedListener {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private static final long ROOMS_REFRESH_INTERVAL_MS = 30_000L;

    private String pendingSelectChipId = null;
    private WeatherActivity weatherActivity;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    private ActivityResultLauncher<Intent> createRoomLauncher;

    private LineChart chart;
    private MaterialButton btnDay, btnWeek;
    private MaterialButton btnTemperature, btnHumidity;
    private boolean isDaySelected = true;
    private boolean showTemperature = true;

    private ChipGroup chipGroupRooms;
    private final List<RoomWithSensorDto> roomsCache = new ArrayList<>();
    private String selectedChipId = null;

    private DrawerLayout drawerLayout;
    private NavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        if (prefs.getString("accessToken", null) == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.custom_toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets topInsets = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()
            );
            int top = topInsets.top;

            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());

            int actionBarSizePx = 0;
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(com.google.android.material.R.attr.actionBarSize, tv, true)) {
                actionBarSizePx = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = actionBarSizePx + top;
            v.setLayoutParams(lp);
            return insets;
        });

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        navView.setNavigationItemSelectedListener(item -> {
            item.setChecked(true);
            drawerLayout.closeDrawers();

            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                new ProfileDialogFragment().show(getSupportFragmentManager(), ProfileDialogFragment.TAG);
            }
            else if (id == R.id.nav_thresholds) {
                ThresholdDialogFragment.newInstance(null)
                        .show(getSupportFragmentManager(), ThresholdDialogFragment.TAG);
            }
            else if (id == R.id.nav_logout) {
                new LogoutDialogFragment().show(getSupportFragmentManager(), LogoutDialogFragment.TAG);
            }
            return true;
        });

        TextView title = toolbar.findViewById(R.id.toolbar_title);
        setHello(title, getSavedUsername() != null ? getSavedUsername() : getString(R.string.guest));
        refreshUsernameFromServer(title);
        ImageView profileIcon = toolbar.findViewById(R.id.toolbar_profile_icon);
        if (profileIcon != null) {
            profileIcon.setOnClickListener(v ->
                    new ProfileDialogFragment().show(getSupportFragmentManager(), ProfileDialogFragment.TAG));
        }

        requestAllPermissions();

        weatherActivity = new WeatherActivity(this);
        weatherActivity.startWeatherUpdates();

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.main_color));
        swipeRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(this, R.color.bg_weather_card)
        );
        swipeRefresh.setOnRefreshListener(() -> {
            weatherActivity.startWeatherUpdates();
            loadRoomsFromServer();
            updateChartForSelection();
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1000);
        });

        setupRoomsRecycler();

        setupCreateRoomLauncher();
        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v -> createRoomLauncher.launch(new Intent(this, CreateRoomActivity.class)));

        chart          = findViewById(R.id.chart);
        btnDay         = findViewById(R.id.btnDay);
        btnWeek        = findViewById(R.id.btnWeek);
        btnTemperature = findViewById(R.id.btnTemperature);
        btnHumidity    = findViewById(R.id.btnHumidity);
        chipGroupRooms = findViewById(R.id.chipGroupRooms);
        setupChart(chart);

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

        loadRoomsFromServer();
        startPeriodicRoomRefresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
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
                (anchor, room) -> showRoomPopup(anchor, room)
        );
        roomRecyclerView.setAdapter(roomAdapter);
    }

    private void setupCreateRoomLauncher() {
        createRoomLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

                    String chipId    = result.getData().getStringExtra("chipId");
                    String roomName  = result.getData().getStringExtra("roomName");
                    String imageName = result.getData().getStringExtra("imageName");
                    String username = result.getData().getStringExtra("username");
                    int userId       = getSavedUserId();

                    if (chipId == null || chipId.trim().isEmpty() || userId <= 0) {
                        Toast.makeText(this, "Невірні дані кімнати (chipId/userId)", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SensorOwnershipRequestDto dto = new SensorOwnershipRequestDto(
                            roomName,   // ✅ перший аргумент
                            imageName,  // ✅ другий
                            chipId,     // ✅ третій
                            username    // ✅ четвертий
                    );

                    RoomApiService api = ApiClientMain.getClient(this).create(RoomApiService.class);
                    api.createOwnership(dto).enqueue(new Callback<RoomWithSensorDto>() {
                        @Override
                        public void onResponse(Call<RoomWithSensorDto> call, Response<RoomWithSensorDto> resp) {
                            if (!resp.isSuccessful() || resp.body() == null) {
                                Toast.makeText(MainActivity.this, "Помилка створення: " + resp.code(), Toast.LENGTH_LONG).show();
                                return;
                            }

                            RoomWithSensorDto created = resp.body();
                            Toast.makeText(MainActivity.this, "Кімнату створено", Toast.LENGTH_SHORT).show();

                            List<RoomWithSensorDto> updated = new ArrayList<>(roomAdapter.getCurrentList());
                            updated.add(created);
                            roomAdapter.submitList(updated);

                            selectedChipId = created.getChipId();
                            loadRoomsFromServer();
                            checkChipByChipId(selectedChipId);
                            updateChartForSelection();

                            showThresholdDialog(created.getChipId());
                        }

                        @Override
                        public void onFailure(Call<RoomWithSensorDto> call, Throwable t) {
                            Toast.makeText(MainActivity.this, "Збій створення: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
        );
    }
private void showRoomPopup(View anchor, RoomWithSensorDto room) {
        Context wrapper = new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_App_PopupMenu);
        PopupMenu menu = new PopupMenu(wrapper, anchor, Gravity.END);
        if (android.os.Build.VERSION.SDK_INT >= 23) menu.setGravity(Gravity.END);

        menu.getMenu().add(0, 1, 0, "Оновити кімнату");
        menu.getMenu().add(0, 2, 1, "Оновити Wi-Fi");
        menu.getMenu().add(0, 3, 2, "Видалити кімнату");
        menu.getMenu().add(0, 4, 3, "Пороги");

        tintPopupMenuText(menu, R.color.weather_card_text);

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> EditRoomDialogFragment.newInstance(room.getChipId(), room.getRoomName(), room.getImageName())
                        .show(getSupportFragmentManager(), EditRoomDialogFragment.TAG);
                case 2 -> WifiDialogFragment.newInstance(room.getChipId())
                        .show(getSupportFragmentManager(), WifiDialogFragment.TAG);
                case 3 -> DeleteRoomDialogFragment.newInstance(room.getChipId(), room.getRoomName())
                        .show(getSupportFragmentManager(), DeleteRoomDialogFragment.TAG);
                case 4 -> {
                    if (TextUtils.isEmpty(room.getChipId())) {
                        Toast.makeText(this, "У кімнати немає chipId", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    ThresholdDialogFragment.newInstance(room.getChipId())
                            .show(getSupportFragmentManager(), ThresholdDialogFragment.TAG);
                }
            }
            return true;
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

    private void showThresholdDialog(String chipId) {
        ThresholdDialogFragment dialog = ThresholdDialogFragment.newInstance(chipId);
        dialog.show(getSupportFragmentManager(), ThresholdDialogFragment.TAG);
    }

    // ----------------------------- Реакція на діалоги -----------------------------
    @Override
    public void onRoomsChanged() {
        loadRoomsFromServer();
    }

    // ----------------------------- Завантаження кімнат -----------------------------
    private void startPeriodicRoomRefresh() {
        stopPeriodicRoomRefresh();
        refreshRunnable = new Runnable() {
            @Override public void run() {
                loadRoomsFromServer();
                handler.postDelayed(this, ROOMS_REFRESH_INTERVAL_MS);
            }
        };
        handler.post(refreshRunnable);
    }

    private void stopPeriodicRoomRefresh() {
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
    }

    private void loadRoomsFromServer() { loadRoomsFromServer(null); }

    private void loadRoomsFromServer(@androidx.annotation.Nullable Runnable after) {
        int userId = getSavedUserId();
        if (userId == -1) return;

        RoomApiService apiService = ApiClientMain.getClient(this).create(RoomApiService.class);
        apiService.getAllRooms(userId).enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> response) {
                if (!response.isSuccessful()) {
                    if (response.code() == 401) handleUnauthorized();
                    return;
                }
                List<RoomWithSensorDto> rooms = response.body() != null ? response.body() : new ArrayList<>();
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
                    updateChartForSelection();
                }

                if (after != null) after.run();
            }

            @Override public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) { /* ignore */ }
        });
    }

    // ----------------------------- Чіпи кімнат -----------------------------
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

            chip.setChipBackgroundColor(AppCompatResources.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(AppCompatResources.getColorStateList(this, R.color.chip_text_selector));
            chip.setChipStrokeWidth(dp(2f));
            chip.setChipStrokeColor(AppCompatResources.getColorStateList(this, R.color.chip_stroke_selector));
            chip.setRippleColor(AppCompatResources.getColorStateList(this, R.color.chip_ripple_selector));

            chipGroupRooms.addView(chip);

            if (pendingSelectChipId != null && pendingSelectChipId.equals(r.getChipId())) {
                toCheckId = chip.getId();
            } else if (selectedChipId != null && selectedChipId.equals(r.getChipId())) {
                toCheckId = chip.getId();
            }
        }

        if (chipGroupRooms.getChildCount() > 0) {
            if (toCheckId != View.NO_ID) {
                chipGroupRooms.check(toCheckId);
                View v = chipGroupRooms.findViewById(toCheckId);
                if (v != null && v.getTag() != null) selectedChipId = v.getTag().toString();
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

    // ----------------------------- Графіки -----------------------------
    private void setupChart(LineChart chart) {
        int txtColor = ContextCompat.getColor(this, R.color.weather_card_text);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(txtColor);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(txtColor);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(1f);

        chart.getAxisLeft().setTextColor(txtColor);
        chart.getAxisRight().setEnabled(false);

        chart.setNoDataText("Немає даних");
        chart.setNoDataTextColor(txtColor);
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

        LineDataSet dataSet;
        if (showTemperature) {
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
                    return String.format(Locale.getDefault(), "%.0f%%", value);
                }
            });
        }
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(txtColor);
        dataSet.setValueTextSize(10f);

        XAxis x = chart.getXAxis();
        x.setLabelCount(Math.min(6, labels.size()), true);
        x.setTextColor(txtColor);
        x.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getAxisLabel(float value, AxisBase axis) {
                int i = (int) value;
                return (i >= 0 && i < labels.size()) ? labels.get(i) : "";
            }
        });

        YAxis left = chart.getAxisLeft();
        chart.getAxisRight().setEnabled(false);
        left.setTextColor(txtColor);
        left.setAxisLineColor(txtColor);
        left.setXOffset(12f);

        List<Entry> series = showTemperature ? temp : hum;
        float dataMin = Float.MAX_VALUE, dataMax = -Float.MAX_VALUE;
        for (Entry e : series) {
            float y = e.getY();
            dataMin = Math.min(dataMin, y);
            dataMax = Math.max(dataMax, y);
        }
        float lowerLimit = dataMin - 5f;
        float upperLimit = dataMax + 5f;

        left.setAxisMinimum(lowerLimit);
        left.setAxisMaximum(upperLimit);

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

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(3f, 18f, 30f, 12f);

        chart.setData(new LineData(dataSet));
        chart.invalidate();
    }

    // ----------------------------- Дозволи -----------------------------
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
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
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

    // ----------------------------- Хелпери -----------------------------
    private void refreshAll() {
        weatherActivity.startWeatherUpdates();
        loadRoomsFromServer();
        updateChartForSelection();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
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
        String text = getString(R.string.username_house, name);
        if (title == null) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(text);
            return;
        }
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
                } else if (resp.code() == 401) {
                    handleUnauthorized();
                }
            }
            @Override public void onFailure(retrofit2.Call<String> call, Throwable t) { /* ignore */ }
        });
    }

    private void highlightButton(MaterialButton active, MaterialButton inactive) {
        active.setBackgroundColor(ContextCompat.getColor(this, R.color.main_color));
        active.setTextColor(Color.WHITE);

        inactive.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_weather_card));
        inactive.setTextColor(ContextCompat.getColor(this, R.color.weather_card_text));
    }

    private void handleUnauthorized() {
        Toast.makeText(this, "Сесія завершилась. Увійдіть знову.", Toast.LENGTH_LONG).show();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ETag local store (узгоджено зі сховищем у EditRoomDialogFragment)
    private static void saveEtagForChip(Context ctx, String chipId, String etag) {
        if (TextUtils.isEmpty(chipId) || TextUtils.isEmpty(etag)) return;
        ctx.getSharedPreferences("etag_store", Context.MODE_PRIVATE)
                .edit().putString("etag_" + chipId, etag).apply();
    }
}
