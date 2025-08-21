package com.example.sensedata;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.sensedata.model.SensorDataDTO;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.SensorDataApiService;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SensorDataActivity extends AppCompatActivity {

    public static final String EXTRA_CHIP_ID   = "extra_chip_id";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private String chipId;
    private String roomName;

    private ProgressBar progress;

    // посилання на TextView
    private TextView tvTemp, tvHumi, tvPressure, tvAltitude,
            tvTempBme, tvHumiBme, tvGas, tvLight,
            tvMq2, tvMq2P, tvLightA, tvLightAP;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_data);

        chipId   = getIntent().getStringExtra(EXTRA_CHIP_ID);
        roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);

        // --- MaterialToolbar (центрований тайтл, стрілка назад) ---
        com.google.android.material.appbar.MaterialToolbar tb = findViewById(R.id.toolbar);
        // setSupportActionBar не обов’язковий, але можна лишити — якщо треба меню
        setSupportActionBar(tb);

        tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // заголовок
        String title = (roomName == null || roomName.isEmpty()) ? "Дані сенсорів" : roomName;
        tb.setTitle(title + " (" + chipId + ")");

        // edge-to-edge інсети зверху (якщо треба)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(tb, (v, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        // --- /MaterialToolbar ---

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

        loadLatest();
    }

    private void loadLatest() {
        progress.setVisibility(View.VISIBLE);

        SensorDataApiService api = ApiClientMain
                .getClient(getApplicationContext())
                .create(SensorDataApiService.class);

        api.getLatest(chipId).enqueue(new Callback<SensorDataDTO>() {
            @Override public void onResponse(Call<SensorDataDTO> call, Response<SensorDataDTO> resp) {
                progress.setVisibility(View.GONE);
                if (!resp.isSuccessful() || resp.body() == null) {
                    Toast.makeText(SensorDataActivity.this, "Немає даних", Toast.LENGTH_SHORT).show();
                    return;
                }
                SensorDataDTO d = resp.body();

                // якщо бек повернув roomName — оновимо заголовок
                if ((roomName == null || roomName.isEmpty()) && d.roomName != null) {
                    roomName = d.roomName;
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(roomName + " (" + chipId + ")");
                    }
                }

                // підставляємо значення (акуратно з null)
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
                progress.setVisibility(View.GONE);
                Toast.makeText(SensorDataActivity.this, "Помилка завантаження", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String f(Float v, String unit) {
        return v == null ? "—" : String.format(Locale.getDefault(), "%.1f %s", v, unit);
    }
    private String i(Integer v) {
        return v == null ? "—" : String.valueOf(v);
    }
    private String b(Boolean v) {
        if (v == null) return "—";
        return v ? "Так" : "Ні"; // або "ON/OFF"
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}

