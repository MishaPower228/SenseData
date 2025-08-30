package com.example.sensedata.dialog_fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.sensedata.activity.BleActivity;
import com.example.sensedata.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

/** Діалог Wi-Fi з кастомними кнопками і BLE-логікою всередині. */
public class WifiDialogFragment extends DialogFragment {

    public static final String TAG = "WifiDialog";
    private static final String ARG_CHIP = "chipId";

    private BleActivity bleActivity;

    public static WifiDialogFragment newInstance(String chipId) {
        Bundle b = new Bundle();
        b.putString(ARG_CHIP, chipId);
        WifiDialogFragment f = new WifiDialogFragment();
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_wifi, null, false);

        String chipId = getArguments() != null ? getArguments().getString(ARG_CHIP) : null;

        EditText etSsid = view.findViewById(R.id.etSsid);
        EditText etPass = view.findViewById(R.id.etPass);
        TextView tvStatus = view.findViewById(R.id.tvWifiStatus);
        CircularProgressIndicator prog = view.findViewById(R.id.progressWifi);

        bleActivity = new BleActivity(requireContext());

        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnSend = view.findViewById(R.id.btnSend);

        Runnable setBusyTrue = () -> {
            btnSend.setEnabled(false);
            btnCancel.setEnabled(false);
            etSsid.setEnabled(false);
            etPass.setEnabled(false);
            tvStatus.setText(getString(R.string.scanning_esp32));
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
            String ssid = etSsid.getText() == null ? "" : etSsid.getText().toString().trim();
            String pass = etPass.getText() == null ? "" : etPass.getText().toString();
            if (ssid.isEmpty()) {
                etSsid.setError(getString(R.string.error_enter_ssid));
                return;
            }
            if (chipId == null || chipId.trim().isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_chipid_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            if (bleActivity.isBluetoothSupported()) {
                Toast.makeText(requireContext(), R.string.error_ble_not_supported, Toast.LENGTH_SHORT).show();
                return;
            }
            if (bleActivity.isBluetoothEnabled()) {
                startActivity(bleActivity.getEnableBluetoothIntent());
                return;
            }
            if (bleActivity.hasAllBlePermissions()) {
                bleActivity.requestAllBlePermissions(requireActivity(), 42);
                return;
            }

            final String shortId = chipId.trim().toUpperCase(Locale.ROOT);
            if (shortId.length() < 6) {
                Toast.makeText(requireContext(), R.string.error_chipid_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            final String targetName = "ESP32_" + shortId;

            setBusyTrue.run();
            tvStatus.setText(getString(R.string.scanning_target, targetName));

            bleActivity.startBleScan(4000, (names, devices) -> {
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
                        tvStatus.setText(getString(R.string.error_target_not_found, targetName));
                        setBusyFalse.run();
                        return;
                    }
                    tvStatus.setText(getString(R.string.sending_wifi_to, targetName));
                    try {
                        bleActivity.stopScan();
                    } catch (Exception ignore) {}

                    bleActivity.sendWifiPatchViaDevice(target, ssid, pass, new BleActivity.WifiPatchCallback() {
                        @Override
                        public void onSuccess() {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                setBusyFalse.run();
                                Toast.makeText(requireContext(), R.string.wifi_updated_success, Toast.LENGTH_SHORT).show();
                                dismiss();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                tvStatus.setText(message == null ? getString(R.string.error_ble) : message);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (bleActivity != null) bleActivity.stopScan();
        } catch (Exception ignore) {}
    }
}
