package com.example.sensedata;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.model.AdjustmentAbsoluteItemDto;
import com.example.sensedata.model.AdjustmentAbsoluteRequestDto;
import com.example.sensedata.model.AdjustmentAbsoluteResponseDto;
import com.example.sensedata.model.EffectiveSettingDto;
import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.RoomApiService;
import com.example.sensedata.network.SettingsApiService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ThresholdDialogFragment extends DialogFragment {

    public static final String TAG = "ThresholdDialog";

    private String chipId;
    private ChipGroup chipGroupRooms;
    private RangeSlider sliderTemp, sliderHum;
    private TextView valueTemp, valueHum;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_thresholds, null, false);

        chipId = getArguments() != null ? getArguments().getString("chipId") : null;

        valueTemp   = v.findViewById(R.id.valueTemp);
        valueHum    = v.findViewById(R.id.valueHum);
        sliderTemp  = v.findViewById(R.id.sliderTemp);
        sliderHum   = v.findViewById(R.id.sliderHum);
        chipGroupRooms = v.findViewById(R.id.chipGroupThresholdRooms);

        if (chipId == null || chipId.trim().isEmpty()) {
            // 🔹 Виклик без chipId → показуємо список кімнат
            chipGroupRooms.setVisibility(View.VISIBLE);
            loadRoomsIntoChips();
        } else {
            // 🔹 Виклик з chipId → ховаємо список кімнат
            chipGroupRooms.setVisibility(View.GONE);
            loadThresholdsFromServer(chipId);
        }

        sliderTemp.addOnChangeListener((slider, value, fromUser) ->
                valueTemp.setText(formatTemp(slider.getValues())));
        sliderHum.addOnChangeListener((slider, value, fromUser) ->
                valueHum.setText(formatHum(slider.getValues())));

        MaterialButton btnCancel = v.findViewById(R.id.btnCancel);
        MaterialButton btnSave   = v.findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(view -> dismiss());
        btnSave.setOnClickListener(view -> {
            if (chipId == null || chipId.trim().isEmpty()) {
                int checkedId = chipGroupRooms.getCheckedChipId();
                if (checkedId == View.NO_ID) {
                    Toast.makeText(requireContext(), "Оберіть кімнату", Toast.LENGTH_SHORT).show();
                    return;
                }
                Chip selectedChip = chipGroupRooms.findViewById(checkedId);
                chipId = (String) selectedChip.getTag();
            }

            if (validateThresholds()) {
                sendToServer();
                dismiss();
            }
        });

        return new MaterialAlertDialogBuilder(requireContext(), R.style.Overlay_SenseData_AlertDialog)
                .setView(v)
                .setCancelable(false)
                .create();
    }

    /** Завантажує кімнати у ChipGroup */
    private void loadRoomsIntoChips() {
        int userId = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                .getInt("userId", -1);
        if (userId == -1) return;

        RoomApiService api = ApiClientMain.getClient(requireContext()).create(RoomApiService.class);
        api.getAllRooms(userId).enqueue(new Callback<List<RoomWithSensorDto>>() {
            @Override
            public void onResponse(Call<List<RoomWithSensorDto>> call, Response<List<RoomWithSensorDto>> resp) {
                if (!isAdded() || resp.body() == null) return;
                List<RoomWithSensorDto> rooms = new ArrayList<>(resp.body());
                chipGroupRooms.removeAllViews();

                for (RoomWithSensorDto r : rooms) {
                    if (r.getChipId() == null) continue;

                    Chip chip = new Chip(requireContext(), null,
                            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice);

                    chip.setText(r.getRoomName());
                    chip.setTag(r.getChipId());
                    chip.setId(View.generateViewId());
                    chip.setCheckable(true);
                    chip.setCheckedIconVisible(false);
                    chip.setEnsureMinTouchTargetSize(true);
                    chip.setEllipsize(TextUtils.TruncateAt.END);
                    chip.setMaxLines(1);

                    // 🔹 Стиль як у MainActivity
                    chip.setChipBackgroundColor(
                            AppCompatResources.getColorStateList(requireContext(), R.color.chip_bg_selector));
                    chip.setTextColor(
                            AppCompatResources.getColorStateList(requireContext(), R.color.chip_text_selector));
                    chip.setChipStrokeWidth(dp(2f));
                    chip.setChipStrokeColor(
                            AppCompatResources.getColorStateList(requireContext(), R.color.chip_stroke_selector));
                    chip.setRippleColor(
                            AppCompatResources.getColorStateList(requireContext(), R.color.chip_ripple_selector));

                    chipGroupRooms.addView(chip);

                    chip.setOnClickListener(view -> {
                        chipId = (String) chip.getTag();
                        loadThresholdsFromServer(chipId);
                    });
                }
            }
            @Override public void onFailure(Call<List<RoomWithSensorDto>> call, Throwable t) { }
        });
    }

    /** Підтягує ефективні пороги із сервера */
    private void loadThresholdsFromServer(String chipId) {
        SettingsApiService api = ApiClientMain.getClient(requireContext()).create(SettingsApiService.class);

        api.getEffectiveByChip(chipId).enqueue(new Callback<List<EffectiveSettingDto>>() {
            @Override
            public void onResponse(Call<List<EffectiveSettingDto>> call, Response<List<EffectiveSettingDto>> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    for (EffectiveSettingDto dto : resp.body()) {
                        if ("temperature".equalsIgnoreCase(dto.parameterName)) {
                            applySliderValues(sliderTemp, dto.lowValue, dto.highValue);
                            valueTemp.setText(formatTemp(sliderTemp.getValues()));
                        }
                        if ("humidity".equalsIgnoreCase(dto.parameterName)) {
                            applySliderValues(sliderHum, dto.lowValue, dto.highValue);
                            valueHum.setText(formatHum(sliderHum.getValues()));
                        }
                    }
                }
            }
            @Override public void onFailure(Call<List<EffectiveSettingDto>> call, Throwable t) { }
        });
    }

    private void applySliderValues(RangeSlider slider, Float low, Float high) {
        if (low == null || high == null) return;
        float min = slider.getValueFrom();
        float max = slider.getValueTo();

        float safeLow = Math.max(min, Math.min(low, max));
        float safeHigh = Math.max(min, Math.min(high, max));

        slider.setValues(safeLow, safeHigh);
    }

    private boolean validateThresholds() {
        List<Float> t = sliderTemp.getValues();
        List<Float> h = sliderHum.getValues();

        float tmin = t.get(0), tmax = t.get(1);
        int   hmin = Math.round(h.get(0)), hmax = Math.round(h.get(1));

        if (tmin >= tmax) {
            Toast.makeText(requireContext(), "Температура: мін має бути < макс", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (hmin >= hmax) {
            Toast.makeText(requireContext(), "Вологість: мін має бути < макс", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void sendToServer() {
        List<Float> t = sliderTemp.getValues();
        List<Float> h = sliderHum.getValues();

        AdjustmentAbsoluteRequestDto body = new AdjustmentAbsoluteRequestDto(
                Arrays.asList(
                        new AdjustmentAbsoluteItemDto("temperature", t.get(0), t.get(1)),
                        new AdjustmentAbsoluteItemDto("humidity", h.get(0), h.get(1))
                )
        );

        SettingsApiService api = ApiClientMain.getClient(requireContext()).create(SettingsApiService.class);
        api.postAdjustmentsForChip(chipId, body).enqueue(new Callback<AdjustmentAbsoluteResponseDto>() {
            @Override public void onResponse(Call<AdjustmentAbsoluteResponseDto> call, Response<AdjustmentAbsoluteResponseDto> response) {
                if (!isAdded()) return;
                Context ctx = getContext();
                if (ctx == null) return;
                Toast.makeText(ctx,
                        response.isSuccessful() ? "Пороги збережено" : "Помилка: " + response.code(),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<AdjustmentAbsoluteResponseDto> call, Throwable t) {
                if (!isAdded()) return;
                Context ctx = getContext();
                if (ctx == null) return;
                Toast.makeText(ctx, "Помилка мережі: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatTemp(List<Float> values) {
        return String.format("%.1f – %.1f °C", values.get(0), values.get(1));
    }
    private String formatHum(List<Float> values) {
        return String.format("%d – %d %%", Math.round(values.get(0)), Math.round(values.get(1)));
    }

    public static ThresholdDialogFragment newInstance(@Nullable String chipId) {
        ThresholdDialogFragment f = new ThresholdDialogFragment();
        Bundle args = new Bundle();
        if (chipId != null) args.putString("chipId", chipId);
        f.setArguments(args);
        return f;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                requireContext().getResources().getDisplayMetrics()
        );
    }
}
