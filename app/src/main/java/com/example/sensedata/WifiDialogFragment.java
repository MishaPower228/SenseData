package com.example.sensedata;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.BleManager;
import com.example.sensedata.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

/** Діалог Wi-Fi з кастомними кнопками і BLE-логікою всередині. */
public class WifiDialogFragment extends DialogFragment {

    public static final String TAG = "WifiDialog";
    private static final String ARG_CHIP = "chipId";

    private BleManager bleManager;

    public static WifiDialogFragment newInstance(String chipId) {
        Bundle b = new Bundle();
        b.putString(ARG_CHIP, chipId);
        WifiDialogFragment f = new WifiDialogFragment();
        f.setArguments(b);
        return f;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_wifi, null, false);

        String chipId = getArguments() != null ? getArguments().getString(ARG_CHIP) : null;

        EditText etSsid = view.findViewById(R.id.etSsid);
        EditText etPass = view.findViewById(R.id.etPass);
        TextView tvStatus = view.findViewById(R.id.tvWifiStatus);
        CircularProgressIndicator prog = view.findViewById(R.id.progressWifi);

        bleManager = new BleManager(requireContext());

        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnSend   = view.findViewById(R.id.btnSend);

        Runnable setBusyTrue = () -> {
            btnSend.setEnabled(false);
            btnCancel.setEnabled(false);
            etSsid.setEnabled(false);
            etPass.setEnabled(false);
            tvStatus.setText("Сканую ESP32…");
            tvStatus.setVisibility(View.VISIBLE);
            prog.setVisibility(View.VISIBLE);
        };
        Runnable setBusyFalse = () -> {
            btnSend.setEnabled(true);
            btnCancel.setEnabled(true);
            etSsid.setEnabled(true);
            etPass.setEnabled(true);
            tvStatus.setVisibility(View.GONE);
            prog.setVisibility(View.GONE);
        };

        btnCancel.setOnClickListener(v -> dismiss());
        btnSend.setOnClickListener(v -> {
            String ssid = etSsid.getText()==null ? "" : etSsid.getText().toString().trim();
            String pass = etPass.getText()==null ? "" : etPass.getText().toString();
            if (ssid.isEmpty()) { etSsid.setError("Введіть SSID"); return; }
            if (chipId == null || chipId.trim().isEmpty()) { Toast.makeText(requireContext(),"chipId порожній",Toast.LENGTH_SHORT).show(); return; }

            if (!bleManager.isBluetoothSupported()) { Toast.makeText(requireContext(),"BLE недоступний",Toast.LENGTH_SHORT).show(); return; }
            if (!bleManager.isBluetoothEnabled()) { startActivity(bleManager.getEnableBluetoothIntent()); return; }
            if (!bleManager.hasAllBlePermissions()) { bleManager.requestAllBlePermissions(requireActivity(), 42); return; }

            final String shortId = chipId.trim().toUpperCase(Locale.ROOT);
            if (shortId.length() < 6) { Toast.makeText(requireContext(),"chipId некоректний",Toast.LENGTH_SHORT).show(); return; }
            final String targetName = "ESP32_" + shortId;

            setBusyTrue.run();
            tvStatus.setText("Сканую ESP32 (" + targetName + ")…");

            bleManager.startBleScan(4000, (names, devices) -> {
                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> {
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
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                setBusyFalse.run();
                                Toast.makeText(requireContext(), "Wi-Fi успішно оновлено", Toast.LENGTH_SHORT).show();
                                dismiss();
                            });
                        }
                        @Override public void onError(String message) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                tvStatus.setText(message == null ? "Помилка BLE" : message);
                                setBusyFalse.run();
                            });
                        }
                    });
                });
            });
        });

        return new MaterialAlertDialogBuilder(requireContext(), R.style.Overlay_SenseData_AlertDialog)
                .setView(view)
                .setCancelable(true)
                .create();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        try { if (bleManager != null) bleManager.stopScan(); } catch (Exception ignore) {}
    }
}
