package com.example.sensedata;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.sensedata.model.SensorDataDTO;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.SensorDataApiService;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

import android.os.Handler;
import android.os.Looper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SensorDataActivity extends ImmersiveActivity {

    public static final String EXTRA_CHIP_ID   = "extra_chip_id";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L; // 5 хв

    private String chipId;
    private String roomName;

    private ProgressBar progress;
    private MaterialToolbar tb;

    // TextView
    private TextView tvTemp, tvHumi, tvPressure, tvAltitude,
            tvTempBme, tvHumiBme, tvGas, tvLight,
            tvMq2, tvMq2P, tvLightA, tvLightAP;

    // автооновлення
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            loadLatest(false); // автооновлення — без прогрес-спінера
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };
    private volatile boolean isLoading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_data);

        chipId   = getIntent().getStringExtra(EXTRA_CHIP_ID);
        roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);

        // --- Toolbar ---
        tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        tb.setTitle(makeTitle(roomName, chipId));
        // --- /Toolbar ---

        progress = findViewById(R.id.progress);

        // init views
        tvTemp     = findViewById(R.id.tvTemp);
        tvHumi     = findViewById(R.id.tvHumi);
        tvPressure = findViewById(R.id.tvPressure);
        tvAltitude = findViewById(R.id.tvAltitude);
        tvTempBme  = findViewById(R.id.tvTempBme);
        tvHumiBme  = findViewById(R.id.tvHumiBme);
        tvGas      = findViewById(R.id.tvGas);
        tvLight    = findViewById(R.id.tvLight);
        tvMq2      = findViewById(R.id.tvMq2);
        tvMq2P     = findViewById(R.id.tvMq2P);
        tvLightA   = findViewById(R.id.tvLightA);
        tvLightAP  = findViewById(R.id.tvLightAP);

        // перше завантаження — з індикатором
        loadLatest(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // запускаємо автооновлення через 5 хв (щоб не дублювати одразу після onCreate)
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // зупиняємо таймер, щойно екран зникає
        handler.removeCallbacks(refreshRunnable);
    }

    private void loadLatest() { loadLatest(true); }

    private void loadLatest(boolean showProgress) {
        if (isLoading) return;
        isLoading = true;

        if (showProgress) progress.setVisibility(android.view.View.VISIBLE);

        SensorDataApiService api = ApiClientMain
                .getClient(getApplicationContext())
                .create(SensorDataApiService.class);

        api.getLatest(chipId).enqueue(new Callback<SensorDataDTO>() {
            @Override public void onResponse(Call<SensorDataDTO> call, Response<SensorDataDTO> resp) {
                isLoading = false;
                progress.setVisibility(android.view.View.GONE);

                if (!resp.isSuccessful() || resp.body() == null) {
                    // не шумимо тостом при автооновленні — лише при першому виклику
                    if (showProgress) {
                        Toast.makeText(SensorDataActivity.this, "Немає даних", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                SensorDataDTO d = resp.body();

                // оновлюємо заголовок, якщо з бекенда приїхав roomName
                if ((roomName == null || roomName.isEmpty()) && d.roomName != null && !d.roomName.isEmpty()) {
                    roomName = d.roomName;
                }
                tb.setTitle(makeTitle(roomName, chipId));

                // підставляємо значення
                tvTemp.setText("🌡 Температура: " + f(d.temperatureDht, "°C"));
                tvHumi.setText("💧 Вологість: " + f(d.humidityDht, "%"));

                tvPressure.setText("🧭 Тиск: " + f(d.pressure, "hPa"));
                tvAltitude.setText("⛰ Висота: " + f(d.altitude, "m"));

                tvTempBme.setText("🌡 T(BME): " + f(d.temperatureBme, "°C"));
                tvHumiBme.setText("💧 H(BME): " + f(d.humidityBme, "%"));

                tvGas.setText("🔥 Газ: " + b(d.gasDetected));
                tvLight.setText("💡 Світло: " + b(d.light));

                tvMq2.setText("🧪 MQ2: " + i(d.mq2Analog));
                tvMq2P.setText("🧪 MQ2, %: " + f(d.mq2AnalogPercent, "%"));

                tvLightA.setText("🔆 Освітленість (A): " + i(d.lightAnalog));
                tvLightAP.setText("🔆 Освітленість, %: " + f(d.lightAnalogPercent, "%"));
            }

            @Override public void onFailure(Call<SensorDataDTO> call, Throwable t) {
                isLoading = false;
                progress.setVisibility(android.view.View.GONE);
                if (showProgress) {
                    Toast.makeText(SensorDataActivity.this, "Помилка завантаження", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String makeTitle(String roomName, String chipId) {
        String rn = (roomName == null || roomName.isEmpty()) ? "Дані сенсорів" : roomName;
        return rn + (chipId == null || chipId.isEmpty() ? "" : " (" + chipId + ")");
    }

    private String f(Float v, String unit) {
        return v == null ? "—" : String.format(Locale.getDefault(), "%.1f %s", v, unit);
    }
    private String i(Integer v) { return v == null ? "—" : String.valueOf(v); }
    private String b(Boolean v) { return v == null ? "—" : (v ? "Так" : "Ні"); }

    // (не обов’язково, але якщо лишаєш підтримку home через меню)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
