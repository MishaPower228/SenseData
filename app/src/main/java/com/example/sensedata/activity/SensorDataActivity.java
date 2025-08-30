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

import androidx.annotation.NonNull;
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
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTemp, tvHumi, tvPressure, tvAltitude,
            tvTempBme, tvHumiBme, tvGas, tvGasAnalog,
            tvLight, tvLightAnalog, tvAdvice;

    private SettingsApiService settingsApi;
    private volatile boolean adviceLoading = false;
    private volatile boolean isLoading = false;

    // автооновлення
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            loadLatest(false);          // без центрального спінера
            loadRecommendations(false); // без збереження в історію
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_data);

        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setPadding(
                        toolbar.getPaddingLeft(),
                        cutout.top,
                        toolbar.getPaddingRight(),
                        toolbar.getPaddingBottom()
                );
                toolbar.getLayoutParams().height =
                        toolbar.getLayoutParams().height + cutout.top;
                toolbar.requestLayout();
            }
            return insets;
        });

        chipId   = getIntent().getStringExtra(EXTRA_CHIP_ID);
        roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);

        // --- Toolbar ---
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarTitle.setText(makeTitle(roomName, chipId));
        // --- /Toolbar ---

        progress = findViewById(R.id.progress);
        swipeRefresh = findViewById(R.id.swipeRefreshSensor);

        // init views
        tvTemp        = findViewById(R.id.tvTemp);
        tvHumi        = findViewById(R.id.tvHumi);
        tvPressure    = findViewById(R.id.tvPressure);
        tvAltitude    = findViewById(R.id.tvAltitude);
        tvTempBme     = findViewById(R.id.tvTempBme);
        tvHumiBme     = findViewById(R.id.tvHumiBme);
        tvGas         = findViewById(R.id.tvGas);
        tvGasAnalog   = findViewById(R.id.tvGasAnalog);
        tvLight       = findViewById(R.id.tvLight);
        tvLightAnalog = findViewById(R.id.tvLightAnalog);
        tvAdvice      = findViewById(R.id.tvAdvice);

        // retrofit API для порад
        settingsApi = ApiClientMain.getClient(getApplicationContext()).create(SettingsApiService.class);

        // довгий тап по блоку порад -> історія
        tvAdvice.setOnLongClickListener(v -> { showAdviceHistoryDialog(); return true; });

        // свайп для оновлення
        swipeRefresh.setOnRefreshListener(() -> {
            loadLatest(false);
            loadRecommendations(false);
        });

        // перше завантаження — з індикатором
        loadLatest(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable); // одразу, а далі кожні 5 хв
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshRunnable);
    }

    // ---------- Дані сенсора ----------
    private void loadLatest(boolean showProgress) {
        if (isLoading) return;
        isLoading = true;

        if (showProgress) progress.setVisibility(View.VISIBLE);

        SensorDataApiService api = ApiClientMain
                .getClient(getApplicationContext())
                .create(SensorDataApiService.class);

        api.getLatest(chipId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<SensorDataDto> call,
                                   @NonNull Response<SensorDataDto> resp) {
                try {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        if (showProgress) {
                            Toast.makeText(SensorDataActivity.this,
                                    getString(R.string.no_data),
                                    Toast.LENGTH_SHORT).show();
                        }
                        loadRecommendations(false);
                        return;
                    }

                    SensorDataDto d = resp.body();

                    // Якщо з беку приїхало ім'я кімнати — оновлюємо
                    if ((roomName == null || roomName.isEmpty()) && !TextUtils.isEmpty(d.roomName)) {
                        roomName = d.roomName;
                    }

                    // Оновити заголовок тулбара
                    TextView toolbarTitle = findViewById(R.id.toolbar_title);
                    if (toolbarTitle != null) {
                        toolbarTitle.setText(makeTitle(roomName, chipId));
                    }

                    // ---- Основні показники ----
                    tvTemp.setText(getString(R.string.sensor_temp_val,      f(d.temperatureDht, "°C")));
                    tvHumi.setText(getString(R.string.sensor_humi_val,      f(d.humidityDht, "%")));
                    tvPressure.setText(getString(R.string.sensor_press_val, f(d.pressure, "hPa")));
                    tvAltitude.setText(getString(R.string.sensor_alt_val,   f(d.altitude, "m")));
                    tvTempBme.setText(getString(R.string.sensor_temp_bme,   f(d.temperatureBme, "°C")));
                    tvHumiBme.setText(getString(R.string.sensor_humi_bme,   f(d.humidityBme, "%")));

                    // Газ
                    tvGas.setText(getString(R.string.sensor_gas_val, b(d.gasDetected)));
                    tvGasAnalog.setText(getString(R.string.sensor_gas_analog_val,
                            i(d.mq2Analog), f(d.mq2AnalogPercent, "%")));

                    // Світло
                    tvLight.setText(getString(R.string.sensor_light_val, b(d.light)));
                    tvLightAnalog.setText(getString(R.string.sensor_light_analog_val,
                            i(d.lightAnalog), f(d.lightAnalogPercent, "%")));

                    // Поради
                    loadRecommendations(true);

                } finally {
                    // гарантовано сховати всі індикатори
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    isLoading = false;
                }
            }

            @Override
            public void onFailure(@NonNull Call<SensorDataDto> call, @NonNull Throwable t) {
                try {
                    if (showProgress) {
                        Toast.makeText(SensorDataActivity.this,
                                getString(R.string.loading_error),
                                Toast.LENGTH_SHORT).show();
                    }
                    loadRecommendations(false);
                } finally {
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    isLoading = false;
                }
            }
        });
    }

    // ---------- Поради ----------
    private void loadRecommendations(boolean saveIfAny) {
        if (adviceLoading) return;
        adviceLoading = true;

        settingsApi.getLatestAdvice(chipId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<RecommendationsDto> call,
                                   @NonNull Response<RecommendationsDto> resp) {
                try {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        tvAdvice.setText(getString(R.string.dash));
                        return;
                    }

                    List<String> adv = resp.body().advice;

                    if (adv == null || adv.isEmpty()) {
                        tvAdvice.setText(getString(R.string.sensor_ok));
                    } else {
                        String bullet = getString(R.string.bullet); // "• "
                        String sep = "\n" + bullet;
                        String adviceText = bullet + TextUtils.join(sep, adv);
                        tvAdvice.setText(adviceText);

                        if (saveIfAny) {
                            settingsApi.saveLatestAdvice(chipId).enqueue(new Callback<>() {
                                @Override public void onResponse(@NonNull Call<SaveLatestRecommendationDto> c,
                                                                 @NonNull Response<SaveLatestRecommendationDto> r) { }
                                @Override public void onFailure(@NonNull Call<SaveLatestRecommendationDto> c,
                                                                @NonNull Throwable t) { }
                            });
                        }
                    }
                } finally {
                    adviceLoading = false;
                }
            }

            @Override
            public void onFailure(@NonNull Call<RecommendationsDto> call, @NonNull Throwable t) {
                try {
                    tvAdvice.setText(getString(R.string.sensor_error));
                } finally {
                    adviceLoading = false;
                }
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

        settingsApi.getAdviceHistory(chipId, 30).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<RecommendationHistoryDto>> call,
                                   @NonNull Response<List<RecommendationHistoryDto>> resp) {
                List<RecommendationHistoryDto> data = resp.isSuccessful() ? resp.body() : null;

                if (data == null || data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.submitList(new ArrayList<>());
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    adapter.submitList(data);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<RecommendationHistoryDto>> call, @NonNull Throwable t) {
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    // ---------- Утиліти форматування ----------
    private String makeTitle(@Nullable String roomName, @Nullable String chipId) {
        if (TextUtils.isEmpty(roomName)) return getString(R.string.title_default);
        if (TextUtils.isEmpty(chipId))   return getString(R.string.title_room, roomName);
        return getString(R.string.title_room_chip, roomName, chipId);
    }

    private String f(@Nullable Float v, @NonNull String unit) {
        return (v == null) ? getString(R.string.dash)
                : String.format(Locale.getDefault(), "%.1f %s", v, unit);
    }
    private String i(@Nullable Integer v) { return v == null ? getString(R.string.dash) : String.valueOf(v); }
    private String b(@Nullable Boolean v) { return v == null ? getString(R.string.dash) : (v ? getString(R.string.yes) : getString(R.string.no)); }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
