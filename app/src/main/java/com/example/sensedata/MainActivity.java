package com.example.sensedata;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RoomAdapter;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.UserApiService;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView title = toolbar.findViewById(R.id.toolbar_title);

        // Привітання
        String cached = getSavedUsername();
        setHello(title, (cached != null) ? cached : getString(R.string.guest));
        refreshUsernameFromServer(title);

        // Підписи
        TextView labelWeather = findViewById(R.id.labelWeather);
        TextView labelRooms   = findViewById(R.id.labelRooms);
        labelWeather.setPaintFlags(labelWeather.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        labelRooms.setPaintFlags(labelRooms.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Погода
        weatherManager = new WeatherManager(this);
        weatherManager.startWeatherUpdates();

        // RecyclerView кімнат
        setupRoomsRecycler();

        // FAB
        createRoomLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String chipId = result.getData().getStringExtra("chipId");
                        String roomName = result.getData().getStringExtra("roomName");
                        String imageName = result.getData().getStringExtra("imageName");
                        // можна обробити chipId
                    }
                }
        );
        FloatingActionButton fab = findViewById(R.id.fab_add_room);
        fab.setOnClickListener(v ->
                createRoomLauncher.launch(new Intent(this, CreateRoomActivity.class))
        );

        // 📊 Налаштування графіка
        chart = findViewById(R.id.chart);
        btnDay = findViewById(R.id.btnDay);
        btnWeek = findViewById(R.id.btnWeek);

        setupChart(chart);

        btnDay.setOnClickListener(v -> {
            highlightButton(btnDay, btnWeek);
            loadDayData();
        });

        btnWeek.setOnClickListener(v -> {
            highlightButton(btnWeek, btnDay);
            loadWeekData();
        });

        // дефолт = день
        highlightButton(btnDay, btnWeek);
        loadDayData();

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
                (anchor, room) -> { /* popup */ }
        );
        roomRecyclerView.setAdapter(roomAdapter);
    }

    // --------- 📊 Графіки ---------
    private void setupChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(Color.WHITE);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
    }

    private void loadDayData() {
        List<Entry> temp = new ArrayList<>();
        List<Entry> hum = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            temp.add(new Entry(i, 20 + (float)Math.random()*5));
            hum.add(new Entry(i, 40 + (float)Math.random()*10));
        }
        setChartData(temp, hum);
    }

    private void loadWeekData() {
        List<Entry> temp = new ArrayList<>();
        List<Entry> hum = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            temp.add(new Entry(i, 18 + (float)Math.random()*6));
            hum.add(new Entry(i, 35 + (float)Math.random()*15));
        }
        setChartData(temp, hum);
    }

    private void setChartData(List<Entry> temp, List<Entry> hum) {
        LineDataSet tempSet = new LineDataSet(temp, "Температура °C");
        tempSet.setColor(Color.RED);
        tempSet.setCircleColor(Color.RED);
        tempSet.setValueTextColor(Color.WHITE);

        LineDataSet humSet = new LineDataSet(hum, "Вологість %");
        humSet.setColor(Color.BLUE);
        humSet.setCircleColor(Color.BLUE);
        humSet.setValueTextColor(Color.WHITE);

        chart.setData(new LineData(tempSet, humSet));
        chart.invalidate();
    }

    private void highlightButton(MaterialButton active, MaterialButton inactive) {
        active.setBackgroundColor(getResources().getColor(R.color.main_color));
        active.setTextColor(Color.WHITE);

        inactive.setBackgroundColor(getResources().getColor(R.color.bg_weather_card));
        inactive.setTextColor(getResources().getColor(R.color.weather_card_text));
    }

    // --------- 🔧 інше ---------
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
                    roomAdapter.submitList(new ArrayList<>(response.body()));
                }
            }
            @Override public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) { }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
    }
}
