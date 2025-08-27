package com.example.sensedata;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.model.AdjustmentAbsoluteItemDto;
import com.example.sensedata.model.AdjustmentAbsoluteRequestDto;
import com.example.sensedata.model.AdjustmentAbsoluteResponseDto;
import com.example.sensedata.network.ApiClientMain;
import com.example.sensedata.network.SettingsApiService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ThresholdDialogFragment extends DialogFragment {

    public static final String TAG = "ThresholdDialog";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_thresholds, null, false);

        TextView valueTemp = v.findViewById(R.id.valueTemp);
        TextView valueHum  = v.findViewById(R.id.valueHum);
        RangeSlider sliderTemp = v.findViewById(R.id.sliderTemp);
        RangeSlider sliderHum  = v.findViewById(R.id.sliderHum);

        // Ініціалізація з Prefs
        float tMin = ThresholdPrefs.getTempMin(requireContext());
        float tMax = ThresholdPrefs.getTempMax(requireContext());
        int   hMin = ThresholdPrefs.getHumMin(requireContext());
        int   hMax = ThresholdPrefs.getHumMax(requireContext());

        sliderTemp.setValues(tMin, tMax);
        sliderHum.setValues((float) hMin, (float) hMax);

        valueTemp.setText(formatTemp(sliderTemp.getValues()));
        valueHum.setText(formatHum(sliderHum.getValues()));

        sliderTemp.addOnChangeListener((slider, value, fromUser) ->
                valueTemp.setText(formatTemp(slider.getValues())));
        sliderHum.addOnChangeListener((slider, value, fromUser) ->
                valueHum.setText(formatHum(slider.getValues())));

        // Кнопки
        MaterialButton btnCancel = v.findViewById(R.id.btnCancel);
        MaterialButton btnSave   = v.findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(view -> dismiss());
        btnSave.setOnClickListener(view -> {
            if (saveThresholds(sliderTemp, sliderHum)) {
                sendToServer(sliderTemp, sliderHum); // 👈 відправка на сервер
                dismiss();
            }
        });

        return new MaterialAlertDialogBuilder(requireContext(), R.style.Overlay_SenseData_AlertDialog)
                .setView(v)
                .setCancelable(false)
                .create();
    }

    /** Зберігає локально */
    private boolean saveThresholds(RangeSlider sliderTemp, RangeSlider sliderHum) {
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

        ThresholdPrefs.save(requireContext(), tmin, tmax, hmin, hmax);
        return true;
    }

    /** Відправляє на сервер */
    private void sendToServer(RangeSlider sliderTemp, RangeSlider sliderHum) {
        List<Float> t = sliderTemp.getValues();
        List<Float> h = sliderHum.getValues();

        AdjustmentAbsoluteRequestDto body = new AdjustmentAbsoluteRequestDto(Arrays.asList(
                new AdjustmentAbsoluteItemDto("temperature", t.get(0), t.get(1)),
                new AdjustmentAbsoluteItemDto("humidity", h.get(0), h.get(1))
        ));

        SettingsApiService api = ApiClientMain.getClient(requireContext()).create(SettingsApiService.class);
        api.postAdjustments(body).enqueue(new Callback<AdjustmentAbsoluteResponseDto>() {
            @Override
            public void onResponse(Call<AdjustmentAbsoluteResponseDto> call, Response<AdjustmentAbsoluteResponseDto> response) {
                if (!isAdded()) return; // фрагмент вже закрито
                Context ctx = getContext();
                if (ctx == null) return;

                if (response.isSuccessful()) {
                    Toast.makeText(ctx, "Пороги збережено на сервері", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, "Помилка сервера: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AdjustmentAbsoluteResponseDto> call, Throwable t) {
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
}
