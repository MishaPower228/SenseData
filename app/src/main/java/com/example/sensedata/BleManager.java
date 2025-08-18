package com.example.sensedata;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class BleManager {

    // ======== Публічні колбеки ========
    public interface BleScanCallback {
        void onDevicesFound(List<String> deviceNames, List<BluetoothDevice> devices);
    }
    public interface ChipIdListener { void onChipId(String chipId); }

    // ======== Константи ========
    private static final String TAG = "BLE";
    private static final String NAME_PREFIX = "ESP32_";
    private static final int DEFAULT_SCAN_TIMEOUT_MS = 3000;

    private static final UUID SERVICE_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // ======== Системні об’єкти ========
    private final Context appCtx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter btAdapter;
    private final BluetoothLeScanner btScanner;

    // ======== Скан ========
    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final List<String> foundNames = new ArrayList<>();
    private boolean isScanning = false;

    // ======== GATT ========
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic ioChar;
    private BluetoothDevice selectedDevice;

    // ======== Pending-конфіг ========
    private String pRoomName, pImageName, pSsid, pPassword, pUsername;
    private boolean pReset;

    // ======== Колбеки ========
    private ChipIdListener chipIdListener;

    // ======== Debounce chipId ========
    private final Set<String> receivedChipIds = new HashSet<>();

    public BleManager(Context context) {
        this.appCtx = context.getApplicationContext();
        BluetoothManager bm = (BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = (bm != null) ? bm.getAdapter() : null;
        this.btScanner = (btAdapter != null) ? btAdapter.getBluetoothLeScanner() : null;
    }

    // ---------- Перевірки/допоміжні ----------
    public boolean isBluetoothSupported() { return btAdapter != null; }

    public boolean isBluetoothEnabled() { return btAdapter != null && btAdapter.isEnabled(); }

    public boolean hasAllBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return has(Manifest.permission.BLUETOOTH_SCAN) &&
                    has(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            return has(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    has(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
    }

    public void requestAllBlePermissions(Activity activity, int reqCode) {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, reqCode);
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, reqCode);
        }
    }

    public Intent getEnableBluetoothIntent() {
        return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    }

    private boolean has(String perm) {
        return ActivityCompat.checkSelfPermission(appCtx, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String msg) {
        main.post(() -> Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show());
    }

    // ---------- Скан ----------
    public void startBleScan(BleScanCallback cb) { startBleScan(DEFAULT_SCAN_TIMEOUT_MS, cb); }

    @SuppressLint("MissingPermission") // усі виклики нижче виконуються лише після рантайм-перевірок
    public void startBleScan(int timeoutMs, BleScanCallback cb) {
        if (!isBluetoothSupported()) { toast("BLE недоступний"); return; }
        if (!isBluetoothEnabled())   { toast("Увімкніть Bluetooth"); return; }
        if (!hasAllBlePermissions()) { toast("Немає дозволів BLE"); return; }
        if (btScanner == null)       { toast("Сканер BLE недоступний"); return; }

        if (isScanning) stopScanInternal();
        isScanning = true;

        foundDevices.clear();
        foundNames.clear();

        try {
            btScanner.startScan(scanCb); // lint приглушено анотацією
        } catch (SecurityException se) {
            Log.e(TAG, "startScan SecurityException", se);
            toast("Помилка доступу до BLE-сканера");
            isScanning = false;
            return;
        }

        main.postDelayed(() -> {
            stopScanInternal();
            cb.onDevicesFound(new ArrayList<>(foundNames), new ArrayList<>(foundDevices));
        }, Math.max(1000, timeoutMs));
    }

    @SuppressLint("MissingPermission")
    public void stopScan() { stopScanInternal(); }

    @SuppressLint("MissingPermission")
    private void stopScanInternal() {
        if (!isScanning) return;
        try {
            if (btScanner != null && has(Manifest.permission.BLUETOOTH_SCAN)) {
                btScanner.stopScan(scanCb);
            }
        } catch (SecurityException ignored) {}
        isScanning = false;
    }

    private final ScanCallback scanCb = new ScanCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice dev = result.getDevice();
            if (dev == null) return;
            if (!has(Manifest.permission.BLUETOOTH_CONNECT)) return;

            // ці виклики вимагають BLUETOOTH_CONNECT — ми перевірили вище
            String name = dev.getName();
            if (name == null || !name.startsWith(NAME_PREFIX)) return;

            String address = dev.getAddress();
            for (BluetoothDevice d : foundDevices) {
                if (d.getAddress().equals(address)) return;
            }
            foundDevices.add(dev);
            foundNames.add(name);
        }

        @Override
        public void onScanFailed(int errorCode) { toast("Помилка сканування: " + errorCode); }
    };

    // ---------- Підключення / надсилання конфіг ----------
    @SuppressLint("MissingPermission")
    public void sendConfigToEsp32ViaDevice(BluetoothDevice device,
                                           String roomName, String imageName,
                                           String ssid, String password,
                                           String username, boolean reset) {
        if (device == null) { toast("BLE пристрій не вибрано"); return; }
        if (!isBluetoothEnabled()) { toast("Увімкніть Bluetooth"); return; }
        if (!hasAllBlePermissions()) { toast("Немає дозволів BLE"); return; }

        selectedDevice = device;
        cleanupGattOnly(); // закриємо попередній конект

        pRoomName = roomName;
        pImageName = imageName;
        pSsid = ssid;
        pPassword = password;
        pUsername = username;
        pReset = reset;

        try {
            gatt = selectedDevice.connectGatt(appCtx, false, gattCb); // вимагає CONNECT — перевірили
        } catch (SecurityException se) {
            Log.e(TAG, "connectGatt SecurityException", se);
            toast("Помилка підключення");
        }
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {

        @Override @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices(); // CONNECT перевірено в sendConfigToEsp32ViaDevice
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt(g);
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService svc = g.getService(SERVICE_UUID);
            if (svc == null) { toast("Сервіс ESP32 не знайдено"); return; }
            ioChar = svc.getCharacteristic(CHARACTERISTIC_UUID);
            if (ioChar == null) { toast("Характеристику ESP32 не знайдено"); return; }

            boolean ok = g.setCharacteristicNotification(ioChar, true);
            Log.d(TAG, "setCharacteristicNotification=" + ok);
            BluetoothGattDescriptor cccd = ioChar.getDescriptor(CCCD_UUID);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(cccd); // ➜ onDescriptorWrite
            } else {
                sendConfigNow(g);
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            if (CCCD_UUID.equals(d.getUuid())) sendConfigNow(g);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) toast("Помилка надсилання даних");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            String value = new String(c.getValue(), StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) return;

            if (!receivedChipIds.add(value)) return; // debounce

            if (chipIdListener != null) chipIdListener.onChipId(value);

            main.postDelayed(() -> {
                closeGatt(g);   // без прямого g.disconnect() тут
            }, 1000);
        }
    };

    @SuppressLint("MissingPermission")
    private void sendConfigNow(@Nullable BluetoothGatt g) {
        if (g == null || ioChar == null) { toast("BLE не готовий"); return; }

        String encPwd = encryptPassword(pPassword);
        try {
            JSONObject json = new JSONObject();
            json.put("roomName", pRoomName);
            json.put("imageName", pImageName);
            json.put("ssid", pSsid);
            json.put("password", encPwd);
            json.put("username", pUsername);
            json.put("reset", pReset);

            ioChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            ioChar.setValue(json.toString().getBytes(StandardCharsets.UTF_8));

            boolean ok = g.writeCharacteristic(ioChar); // CONNECT — перевірено раніше
            Log.d(TAG, "writeCharacteristic=" + ok);
            toast(ok ? "Дані надіслано ESP32" : "Помилка надсилання");
        } catch (JSONException e) {
            Log.e(TAG, "JSON error", e);
        }
    }

    // ---------- Сервісні ----------
    public void setSelectedDevice(int index) {
        if (index >= 0 && index < foundDevices.size()) selectedDevice = foundDevices.get(index);
    }

    public BluetoothDevice getSelectedDevice() { return selectedDevice; }

    public void setChipIdListener(@Nullable ChipIdListener l) { this.chipIdListener = l; }

    public void removeChipIdListener() { this.chipIdListener = null; }

    public void clearChipIdCache() { receivedChipIds.clear(); }

    public void clearChipIdCache(String chipId) { receivedChipIds.remove(chipId); }

    @SuppressLint("MissingPermission")
    public void cleanup() {
        stopScanInternal();
        cleanupGattOnly();
    }

    @SuppressLint("MissingPermission")
    private void cleanupGattOnly() {
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); }      catch (Exception ignored) {}
            gatt = null;
            ioChar = null;
        }
    }

    @SuppressLint("MissingPermission")   // ми самі перевіряємо дозвіл всередині
    private void closeGatt(@Nullable BluetoothGatt g) {
        if (g == null) return;

        // Перевірка BLUETOOTH_CONNECT перед небезпечними викликами
        if (ActivityCompat.checkSelfPermission(appCtx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            try { g.disconnect(); } catch (SecurityException ignored) {}
            try { g.close(); }      catch (SecurityException ignored) {}
        }

        // Обнулимо посилання навіть якщо прав немає — щоб не тримати стан
        if (g == this.gatt) {
            this.gatt = null;
            this.ioChar = null;
        }
    }

    // ---------- Шифрування ----------
    private String encryptPassword(String password) {
        try {
            String key = "my-secret-key-12"; // 16 байт = AES-128
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] enc = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(enc, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "encryptPassword error", e);
            return password; // fallback
        }
    }
}
