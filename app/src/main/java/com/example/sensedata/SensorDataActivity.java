package com.example.sensedata;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensedata.adapter.RecommendationHistoryAdapter;
import com.example.sensedata.model.RecommendationHistoryDto;
import com.example.sensedata.model.RecommendationsDto;
import com.example.sensedata.model.SaveLatestRecommendationDto;
import com.example.sensedata.model.SensorDataDTO;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.SensorDataApiService;
import com.example.sensedata.network.SettingsApiService;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    // Показники
    private TextView tvTemp, tvHumi, tvPressure, tvAltitude,
            tvTempBme, tvHumiBme, tvGas, tvLight,
            tvMq2, tvMq2P, tvLightA, tvLightAP;

    // Поради
    private TextView tvAdvice;
    private SettingsApiService settingsApi;
    private volatile boolean adviceLoading = false;

    // автооновлення
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            loadLatest(false);          // без спінера
            loadRecommendations(false); // без запису в історію
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

        // поради (має бути додано у layout нижче списку показників)
        tvAdvice   = findViewById(R.id.tvAdvice);

        // retrofit API для порад
        settingsApi = ApiClientMain
                .getClient(getApplicationContext())
                .create(SettingsApiService.class);

        // довгий тап по блоку порад -> історія
        tvAdvice.setOnLongClickListener(v -> {
            showAdviceHistoryDialog();
            return true;
        });

        // перше завантаження — з індикатором
        loadLatest(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshRunnable);
    }

    private void loadLatest() { loadLatest(true); }

    private void loadLatest(boolean showProgress) {
        if (isLoading) return;
        isLoading = true;

        if (showProgress) progress.setVisibility(View.VISIBLE);

        SensorDataApiService api = ApiClientMain
                .getClient(getApplicationContext())
                .create(SensorDataApiService.class);

        api.getLatest(chipId).enqueue(new Callback<SensorDataDTO>() {
            @Override public void onResponse(Call<SensorDataDTO> call, Response<SensorDataDTO> resp) {
                isLoading = false;
                progress.setVisibility(View.GONE);

                if (!resp.isSuccessful() || resp.body() == null) {
                    if (showProgress) {
                        Toast.makeText(SensorDataActivity.this, "Немає даних", Toast.LENGTH_SHORT).show();
                    }
                    // навіть якщо даних немає — підвантажимо поради, вони рахуються окремо
                    loadRecommendations(false);
                    return;
                }
                SensorDataDTO d = resp.body();

                // оновлюємо заголовок, якщо з бекенда приїхав roomName
                if ((roomName == null || roomName.isEmpty()) && d.roomName != null && !d.roomName.isEmpty()) {
                    roomName = d.roomName;
                }
                tb.setTitle(makeTitle(roomName, chipId));

                // показники
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

                // поради (і одразу збережемо у історію, якщо вони є)
                loadRecommendations(true);
            }

            @Override public void onFailure(Call<SensorDataDTO> call, Throwable t) {
                isLoading = false;
                progress.setVisibility(View.GONE);
                if (showProgress) {
                    Toast.makeText(SensorDataActivity.this, "Помилка завантаження", Toast.LENGTH_SHORT).show();
                }
                loadRecommendations(false);
            }
        });
    }

    // ----------- ПОРАДИ -----------

    private void loadRecommendations(boolean saveIfAny) {
        if (adviceLoading) return;
        adviceLoading = true;

        settingsApi.getLatestAdvice(chipId).enqueue(new Callback<RecommendationsDto>() {
            @Override public void onResponse(Call<RecommendationsDto> c, Response<RecommendationsDto> r) {
                adviceLoading = false;
                if (!r.isSuccessful() || r.body()==null) {
                    tvAdvice.setText("—");
                    return;
                }
                List<String> adv = r.body().advice;
                if (adv == null || adv.isEmpty()) {
                    tvAdvice.setText("Все в нормі");
                    return;
                }
                tvAdvice.setText("• " + TextUtils.join("\n• ", adv));

                if (saveIfAny) {
                    // 204 No Content означає: нічого не збережено (порад немає) — це ок
                    settingsApi.saveLatestAdvice(chipId).enqueue(new Callback<SaveLatestRecommendationDto>() {
                        @Override public void onResponse(Call<SaveLatestRecommendationDto> c2, Response<SaveLatestRecommendationDto> r2) { /* no-op */ }
                        @Override public void onFailure(Call<SaveLatestRecommendationDto> c2, Throwable t) { /* no-op */ }
                    });
                }
            }
            @Override public void onFailure(Call<RecommendationsDto> c, Throwable t) {
                adviceLoading = false;
                // тихо ігноруємо
            }
        });
    }

    private void showAdviceHistoryDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_recommendations_history);

        // повністю прибрати будь-яке затемнення/бекграунд системного вікна
        if (dialog.getWindow() != null) {
            Window w = dialog.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0f);
        }

        // view з лейаута
        RecyclerView rv = dialog.findViewById(R.id.rvAdviceHistory);
        TextView tvEmpty = dialog.findViewById(R.id.tvEmpty);
        Button btnClose = dialog.findViewById(R.id.btnClose);

        // список
        rv.setLayoutManager(new LinearLayoutManager(this));
        RecommendationHistoryAdapter adapter = new RecommendationHistoryAdapter();
        rv.setAdapter(adapter);

        // кнопка "Закрити"
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // задати ширину ~90% екрана (щоб не було вузької колонки)
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // завантаження історії
        settingsApi.getAdviceHistory(chipId, 30).enqueue(new Callback<List<RecommendationHistoryDto>>() {
            @Override
            public void onResponse(Call<List<RecommendationHistoryDto>> call,
                                   Response<List<RecommendationHistoryDto>> resp) {
                List<RecommendationHistoryDto> data =
                        resp.isSuccessful() ? resp.body() : null;

                if (data == null || data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.submit(new ArrayList<>());
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    adapter.submit(data);
                }
            }

            @Override
            public void onFailure(Call<List<RecommendationHistoryDto>> call, Throwable t) {
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    // ----------- Утиліти -----------

    private String makeTitle(String roomName, String chipId) {
        String rn = (roomName == null || roomName.isEmpty()) ? "Дані сенсорів" : roomName;
        return rn + (chipId == null || chipId.isEmpty() ? "" : " (" + chipId + ")");
    }

    private String f(Float v, String unit) {
        return v == null ? "—" : String.format(Locale.getDefault(), "%.1f %s", v, unit);
    }
    private String i(Integer v) { return v == null ? "—" : String.valueOf(v); }
    private String b(Boolean v) { return v == null ? "—" : (v ? "Так" : "Ні"); }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
