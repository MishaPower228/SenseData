package com.example.sensedata.activity;

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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.sensedata.R;
import com.example.sensedata.adapter.RecommendationHistoryAdapter;
import com.example.sensedata.model.recommendations.RecommendationHistoryDto;
import com.example.sensedata.model.recommendations.RecommendationsDto;
import com.example.sensedata.model.recommendations.SaveLatestRecommendationDto;
import com.example.sensedata.model.sensordata.SensorDataDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.SensorDataApiService;
import com.example.sensedata.network.SettingsApiService;
import com.google.android.material.appbar.MaterialToolbar;

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
    private SwipeRefreshLayout swipeRefresh;

    // Показники
    private TextView tvTemp, tvHumi, tvPressure, tvAltitude,
            tvTempBme, tvHumiBme, tvGas, tvGasAnalog,
            tvLight, tvLightAnalog;

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

        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setPadding(
                        toolbar.getPaddingLeft(),
                        cutoutInsets.top,
                        toolbar.getPaddingRight(),
                        toolbar.getPaddingBottom()
                );
                toolbar.getLayoutParams().height =
                        toolbar.getLayoutParams().height + cutoutInsets.top;
                toolbar.requestLayout();
            }
            return insets;
        });

        chipId   = getIntent().getStringExtra(EXTRA_CHIP_ID);
        roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);

        // --- Toolbar ---
        tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarTitle.setText(makeTitle(roomName, chipId));
        // --- /Toolbar ---

        progress = findViewById(R.id.progress);
        swipeRefresh = findViewById(R.id.swipeRefreshSensor);

        // 🎨 Кольори індикатора
        swipeRefresh.setColorSchemeColors(getColor(R.color.main_color));
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.bg_weather_card));

        // 🔄 Свайп для оновлення
        swipeRefresh.setOnRefreshListener(() -> {
            loadLatest(false);
            loadRecommendations(false);
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1000);
        });

        // init views
        tvTemp     = findViewById(R.id.tvTemp);
        tvHumi     = findViewById(R.id.tvHumi);
        tvPressure = findViewById(R.id.tvPressure);
        tvAltitude = findViewById(R.id.tvAltitude);
        tvTempBme  = findViewById(R.id.tvTempBme);
        tvHumiBme  = findViewById(R.id.tvHumiBme);
        tvGas      = findViewById(R.id.tvGas);
        tvGasAnalog= findViewById(R.id.tvGasAnalog);
        tvLight    = findViewById(R.id.tvLight);
        tvLightAnalog = findViewById(R.id.tvLightAnalog);
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

    private void loadLatest(boolean showProgress) {
        if (isLoading) return;
        isLoading = true;

        if (showProgress) progress.setVisibility(View.VISIBLE);

        SensorDataApiService api = ApiClientMain
                .getClient(getApplicationContext())
                .create(SensorDataApiService.class);

        api.getLatest(chipId).enqueue(new Callback<SensorDataDto>() {
            @Override
            public void onResponse(Call<SensorDataDto> call, Response<SensorDataDto> resp) {
                isLoading = false;
                progress.setVisibility(View.GONE);

                if (!resp.isSuccessful() || resp.body() == null) {
                    if (showProgress) {
                        Toast.makeText(SensorDataActivity.this, "Немає даних", Toast.LENGTH_SHORT).show();
                    }
                    loadRecommendations(false);
                    return;
                }

                SensorDataDto d = resp.body();

                // Якщо з беку приїхало ім'я кімнати — оновлюємо
                if ((roomName == null || roomName.isEmpty()) && d.roomName != null && !d.roomName.isEmpty()) {
                    roomName = d.roomName;
                }

                // 🔹 Встановлюємо заголовок у TextView тулбара
                TextView toolbarTitle = findViewById(R.id.toolbar_title);
                if (toolbarTitle != null) {
                    toolbarTitle.setText(makeTitle(roomName, chipId));
                }

                // 🔹 Основні показники
                tvTemp.setText("🌡 Температура: " + f(d.temperatureDht, "°C"));
                tvHumi.setText("💧 Вологість: " + f(d.humidityDht, "%"));
                tvPressure.setText("🧭 Тиск: " + f(d.pressure, "hPa"));
                tvAltitude.setText("⛰ Висота: " + f(d.altitude, "m"));
                tvTempBme.setText("🌡 T(BME): " + f(d.temperatureBme, "°C"));
                tvHumiBme.setText("💧 H(BME): " + f(d.humidityBme, "%"));

                // 🔹 Газ
                tvGas.setText("🔥 Газ: " + b(d.gasDetected));
                tvGasAnalog.setText("🔥 Газ: " + i(d.mq2Analog) + ", " + f(d.mq2AnalogPercent, "%"));

                // 🔹 Світло
                tvLight.setText("💡 Світло: " + b(d.light));
                tvLightAnalog.setText("💡 Світло: " + i(d.lightAnalog) + ", " + f(d.lightAnalogPercent, "%"));

                // 🔹 Поради
                loadRecommendations(true);
            }

            @Override
            public void onFailure(Call<SensorDataDto> call, Throwable t) {
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
            @Override
            public void onResponse(Call<RecommendationsDto> call, Response<RecommendationsDto> resp) {
                adviceLoading = false;

                if (!resp.isSuccessful() || resp.body() == null) {
                    tvAdvice.setText("—");
                    return;
                }

                List<String> adv = resp.body().advice;

                if (adv == null || adv.isEmpty()) {
                    tvAdvice.setText("Все в нормі ✅");
                } else {
                    tvAdvice.setText("• " + TextUtils.join("\n• ", adv));

                    // Якщо хочеш, зберігаємо останню пораду в історію
                    if (saveIfAny) {
                        settingsApi.saveLatestAdvice(chipId).enqueue(new Callback<SaveLatestRecommendationDto>() {
                            @Override public void onResponse(Call<SaveLatestRecommendationDto> c, Response<SaveLatestRecommendationDto> r) { }
                            @Override public void onFailure(Call<SaveLatestRecommendationDto> c, Throwable t) { }
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<RecommendationsDto> call, Throwable t) {
                adviceLoading = false;
                tvAdvice.setText("Помилка при отриманні порад ❌");
            }
        });
    }

    private void showAdviceHistoryDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_recommendations_history);

        if (dialog.getWindow() != null) {
            Window w = dialog.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0f);
        }

        RecyclerView rv = dialog.findViewById(R.id.rvAdviceHistory);
        TextView tvEmpty = dialog.findViewById(R.id.tvEmpty);
        Button btnClose = dialog.findViewById(R.id.btnClose);

        rv.setLayoutManager(new LinearLayoutManager(this));
        RecommendationHistoryAdapter adapter = new RecommendationHistoryAdapter();
        rv.setAdapter(adapter);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

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
