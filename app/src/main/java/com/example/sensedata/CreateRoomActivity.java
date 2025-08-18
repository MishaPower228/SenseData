package com.example.sensedata;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;

public class CreateRoomActivity extends AppCompatActivity {

    // ====== UI ======
    private EditText roomNameInput, ssidInput, passwordInput;
    private MaterialCheckBox checkboxReset;
    private MaterialAutoCompleteTextView dropdownDevices;
    private CircularProgressIndicator progressScan;
    private MaterialButton btnScan, btnCreate, btnCancel;
    private FrameLayout[] containers;
    private ImageView[] imageViews;

    // ====== Данні вибору картинки ======
    private String selectedImage = null;
    private int selectedIndex = -1;

    // ====== BLE ======
    private BleManager bleManager;
    private ArrayAdapter<String> deviceAdapter;

    // ====== Пермішени/BT ======
    private static final int REQ_BLE_PERMS = 2010;
    private static final int REQ_ENABLE_BT = 2011;
    private boolean pendingScanAfterPermission = false;
    private boolean pendingScanAfterEnableBt = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        // ---- BLE ----
        bleManager = new BleManager(this);

        // ---- BACK (кастомна стрілка) + системний "Назад" ----
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> closeWithoutResult());
        }
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
                R.id.tvDeviceName,            // важливо: id TextView з item_dropdown_device.xml
                new ArrayList<>()
        );
        dropdownDevices.setAdapter(deviceAdapter);
        dropdownDevices.setOnClickListener(v -> dropdownDevices.showDropDown());
        dropdownDevices.setOnItemClickListener((parent, view, position, id) -> {
            bleManager.setSelectedDevice(position);
        });
        // (опційно) однаковий стиль попапу з карткою

        // ---- Прогрес/кнопки ----
        progressScan = findViewById(R.id.progressScan);
        btnScan      = findViewById(R.id.btnScanBle);
        btnCreate    = findViewById(R.id.btnCreate);
        btnCancel    = findViewById(R.id.btnCancel);

        // ---- Вибір картинок ----
        containers = new FrameLayout[] {
                findViewById(R.id.container1),
                findViewById(R.id.container2),
                findViewById(R.id.container3),
                findViewById(R.id.container4)
        };
        imageViews = new ImageView[] {
                findViewById(R.id.img1),
                findViewById(R.id.img2),
                findViewById(R.id.img3),
                findViewById(R.id.img4)
        };
        setupImageSelection();

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
    private void setupImageSelection() {
        for (int i = 0; i < imageViews.length; i++) {
            final int idx = i;
            imageViews[i].setOnClickListener(v -> {
                if (selectedIndex == idx) {
                    containers[idx].setBackgroundResource(R.drawable.bg_image_selector);
                    v.setScaleX(1f); v.setScaleY(1f);
                    selectedIndex = -1; selectedImage = null;
                    return;
                }
                for (int j = 0; j < containers.length; j++) {
                    containers[j].setBackgroundResource(R.drawable.bg_image_selector);
                    imageViews[j].setScaleX(1f); imageViews[j].setScaleY(1f);
                }
                containers[idx].setBackgroundResource(R.drawable.bg_image_selected);
                v.setScaleX(0.95f); v.setScaleY(0.95f);
                selectedIndex = idx;
                selectedImage = (String) v.getTag(); // тег заданий у XML
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

        if (roomName.isEmpty() || ssid.isEmpty() || password.isEmpty() || selectedImage == null) {
            toast("Заповніть всі поля");
            return;
        }
        if (!password.matches("\\A\\p{ASCII}*\\z")) {
            toast("Пароль має бути латиницею");
            return;
        }

        BluetoothDevice device = bleManager.getSelectedDevice();
        if (device == null) {
            toast("Оберіть ESP32-плату");
            dropdownDevices.requestFocus();
            dropdownDevices.showDropDown();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        if (username == null) {
            toast("Користувач не знайдений");
            return;
        }

        // Слухач chipId → повертаємо у MainActivity
        bleManager.setChipIdListener(chipId -> {
            Intent data = new Intent();
            data.putExtra("chipId", chipId);
            data.putExtra("roomName", roomName);
            data.putExtra("imageName", selectedImage);
            setResult(RESULT_OK, data);
            finish();
        });

        // Відправляємо конфіг
        bleManager.sendConfigToEsp32ViaDevice(
                device, roomName, selectedImage, ssid, password, username, reset
        );
    }

    private void ensureBleReadyAndScan() {
        if (!bleManager.isBluetoothSupported()) {
            toast("BLE недоступний на цьому пристрої");
            return;
        }
        if (!bleManager.isBluetoothEnabled()) {
            // Попросимо увімкнути BT
            pendingScanAfterEnableBt = true;
            startActivityForResult(bleManager.getEnableBluetoothIntent(), REQ_ENABLE_BT);
            return;
        }
        if (!bleManager.hasAllBlePermissions()) {
            pendingScanAfterPermission = true;
            bleManager.requestAllBlePermissions(this, REQ_BLE_PERMS);
            return;
        }

        // Готові — скануємо
        setScanning(true);
        bleManager.startBleScan((names, devices) -> runOnUiThread(() -> {
            deviceAdapter.clear();
            deviceAdapter.addAll(names);
            deviceAdapter.notifyDataSetChanged();
            setScanning(false);

            if (!names.isEmpty()) {
                dropdownDevices.setText(names.get(0), false);
                bleManager.setSelectedDevice(0);
                // (опційно) відразу показати список:
                // dropdownDevices.showDropDown();
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
            if (bleManager != null) {
                bleManager.removeChipIdListener();
                bleManager.stopScan();
                bleManager.cleanup();
            }
        } finally {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.removeChipIdListener();
            bleManager.stopScan();
            bleManager.cleanup();
        }
    }

    // ---------------------------------------
    //  Permissions / Activity results
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

    @Override
    @SuppressLint("MissingSuperCall") // ми свідомо не викликаємо super для застарілого API
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK && pendingScanAfterEnableBt) {
                pendingScanAfterEnableBt = false;
                ensureBleReadyAndScan();
            } else {
                pendingScanAfterEnableBt = false;
                toast("Bluetooth не увімкнено");
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
