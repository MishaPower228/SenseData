package com.example.sensedata.activity;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.sensedata.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;

public class CreateRoomActivity extends ImmersiveActivity {

    // ====== UI ======
    private EditText roomNameInput, ssidInput, passwordInput;
    private MaterialCheckBox checkboxReset;
    private MaterialAutoCompleteTextView dropdownDevices;
    private CircularProgressIndicator progressScan;
    private MaterialButton btnScan;

    // ====== Дані вибору картинки ======
    private String selectedImage = null;

    // ====== BLE ======
    private BleActivity bleActivity;
    private ArrayAdapter<String> deviceAdapter;

    // ====== Пермішени/BT ======
    private static final int REQ_BLE_PERMS = 2010;
    private boolean pendingScanAfterPermission = false;
    private boolean pendingScanAfterEnableBt = false;

    // Activity Result API замість startActivityForResult
    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && pendingScanAfterEnableBt) {
                    pendingScanAfterEnableBt = false;
                    ensureBleReadyAndScan();
                } else {
                    pendingScanAfterEnableBt = false;
                    toast("Bluetooth не увімкнено");
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        // ---- BLE ----
        bleActivity = new BleActivity(this);

        // ---- BACK (кастомна стрілка) + системний "Назад" ----
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> closeWithoutResult());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { closeWithoutResult(); }
        });

        // ---- Поля вводу ----
        roomNameInput = findViewById(R.id.editRoomName);
        ssidInput     = findViewById(R.id.editSsid);
        passwordInput = findViewById(R.id.editPassword);
        checkboxReset = findViewById(R.id.checkboxReset);

        // ---- Dropdown пристроїв ----
        dropdownDevices = findViewById(R.id.dropdownDevices);
        deviceAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_dropdown_device,
                R.id.tvDeviceName,
                new ArrayList<>()
        );
        dropdownDevices.setAdapter(deviceAdapter);
        dropdownDevices.setOnClickListener(v -> dropdownDevices.showDropDown());
        dropdownDevices.setOnItemClickListener((parent, view, position, id) -> bleActivity.setSelectedDevice(position));

        // ---- Прогрес/кнопки ----
        progressScan = findViewById(R.id.progressScan);
        btnScan      = findViewById(R.id.btnScanBle);
        MaterialButton btnCreate    = findViewById(R.id.btnCreate);
        MaterialButton btnCancel    = findViewById(R.id.btnCancel);

        // ---- Вибір картинок (локальні final масиви) ----
        final FrameLayout[] containers = new FrameLayout[] {
                findViewById(R.id.container1),
                findViewById(R.id.container2),
                findViewById(R.id.container3),
                findViewById(R.id.container4),
                findViewById(R.id.container5),
                findViewById(R.id.container6)
        };
        final ImageView[] imageViews = new ImageView[] {
                findViewById(R.id.img1),
                findViewById(R.id.img2),
                findViewById(R.id.img3),
                findViewById(R.id.img4),
                findViewById(R.id.img5),
                findViewById(R.id.img6)
        };
        setupImageSelection(containers, imageViews);

        // ---- Скан ----
        btnScan.setOnClickListener(v -> ensureBleReadyAndScan());

        // ---- Скасувати ----
        btnCancel.setOnClickListener(v -> closeWithoutResult());

        // ---- Створити ----
        btnCreate.setOnClickListener(v -> onCreateClick());
    }

    // ---------------------------------------
    //  UI helpers
    // ---------------------------------------
    private void setupImageSelection(FrameLayout[] containers, ImageView[] imageViews) {
        for (int i = 0; i < imageViews.length; i++) {
            final int idx = i;
            imageViews[i].setOnClickListener(v -> {
                for (FrameLayout c : containers) c.setSelected(false);
                for (ImageView iv : imageViews) { iv.setScaleX(1f); iv.setScaleY(1f); }

                containers[idx].setSelected(true);
                v.setScaleX(0.95f); v.setScaleY(0.95f);

                selectedImage = (String) v.getTag();
            });
        }
    }

    private void setScanning(boolean scanning) {
        btnScan.setEnabled(!scanning);
        progressScan.setVisibility(scanning ? View.VISIBLE : View.GONE);
        btnScan.setText(scanning ? "Сканую..." : "Сканувати ESP32");
    }

    // ---------------------------------------
    //  Логіка кнопок
    // ---------------------------------------
    private void onCreateClick() {
        String roomName = safeText(roomNameInput);
        String ssid     = safeText(ssidInput);
        String password = safeText(passwordInput);
        boolean reset   = checkboxReset.isChecked();

        if (roomName.isEmpty()) { roomNameInput.setError("Введіть назву"); return; }
        if (ssid.isEmpty())     { ssidInput.setError("Введіть SSID"); return; }
        if (password.isEmpty()) { passwordInput.setError("Введіть пароль"); return; }
        if (selectedImage == null) { toast("Оберіть зображення кімнати"); return; }
        if (!password.matches("\\A\\p{ASCII}*\\z")) {
            passwordInput.setError("Пароль має бути латиницею");
            toast("Пароль має бути латиницею");
            return;
        }

        BluetoothDevice device = bleActivity.getSelectedDevice();
        if (device == null) {
            toast("Оберіть ESP32-плату");
            dropdownDevices.requestFocus();
            dropdownDevices.showDropDown();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        if (username == null) {
            toast("Сесія втрачена. Авторизуйтесь знову.");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        toast("Надсилаємо конфігурацію на ESP32...");

        bleActivity.setChipIdListener(chipId -> {
            Intent data = new Intent();
            data.putExtra("chipId", chipId);
            data.putExtra("roomName", roomName);
            data.putExtra("imageName", selectedImage);
            setResult(RESULT_OK, data);
            finish();
        });

        bleActivity.sendConfigToEsp32ViaDevice(
                device, roomName, selectedImage, ssid, password, username, reset
        );
    }

    private void ensureBleReadyAndScan() {
        if (bleActivity.isBluetoothSupported()) { toast("BLE недоступний на цьому пристрої"); return; }
        if (bleActivity.isBluetoothEnabled()) {
            pendingScanAfterEnableBt = true;
            enableBtLauncher.launch(bleActivity.getEnableBluetoothIntent()); // <-- новий API
            return;
        }
        if (bleActivity.hasAllBlePermissions()) {
            pendingScanAfterPermission = true;
            bleActivity.requestAllBlePermissions(this, REQ_BLE_PERMS);
            return;
        }

        setScanning(true);
        bleActivity.startBleScan((names, devices) -> runOnUiThread(() -> {
            deviceAdapter.clear();
            deviceAdapter.addAll(names);
            deviceAdapter.notifyDataSetChanged();
            setScanning(false);

            if (!names.isEmpty()) {
                dropdownDevices.setText(names.get(0), false);
                bleActivity.setSelectedDevice(0);
            } else {
                dropdownDevices.setText("", false);
                toast("Пристрої не знайдені");
            }
        }));
    }

    // ---------------------------------------
    //  Закриття/життєвий цикл
    // ---------------------------------------
    private void closeWithoutResult() {
        try {
            if (bleActivity != null) {
                bleActivity.removeChipIdListener();
                bleActivity.stopScan();
                bleActivity.cleanup();
            }
        } finally {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleActivity != null) {
            bleActivity.removeChipIdListener();
            bleActivity.stopScan();
            bleActivity.cleanup();
        }
    }

    // ---------------------------------------
    //  Permissions
    // ---------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE_PERMS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted && pendingScanAfterPermission) {
                pendingScanAfterPermission = false;
                ensureBleReadyAndScan();
            } else {
                pendingScanAfterPermission = false;
                toast("Дозволи BLE не надані");
            }
        }
    }

    // ---------------------------------------
    //  Utils
    // ---------------------------------------
    private String safeText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
