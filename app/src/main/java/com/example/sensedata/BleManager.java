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
import android.content.BroadcastReceiver;    // ★ NEW
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;         // ★ NEW
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
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BleManager {

    // -------- Публічні колбеки --------
    public interface BleScanCallback {
        void onDevicesFound(List<String> deviceNames, List<BluetoothDevice> devices);
    }
    public interface ChipIdListener {
        void onChipId(String chipId);
    }
    public interface WifiPatchCallback {
        void onSuccess();
        void onError(String message);
    }

    // -------- Константи --------
    private static final String TAG = "BLE";
    private static final String NAME_PREFIX = "ESP32_";
    private static final int DEFAULT_SCAN_TIMEOUT_MS = 3000;

    private static final UUID SERVICE_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int REQUESTED_MTU = 185;            // payload ≈ 182 байти
    private static final int WRITE_TIMEOUT_MS = 8000;
    private static final int NOTIFY_TIMEOUT_MS = 10000;

    // ★ NEW: типові коди помилок безпеки
    private static final int GATT_INSUFFICIENT_AUTHENTICATION = 5;
    private static final int GATT_INSUFFICIENT_ENCRYPTION     = 15;

    // ★ NEW: увімк/вимк AES у JSON (поверх шифрованого BLE)
    private static final boolean ENCRYPT_JSON_PASSWORD = true;

    // -------- Системні об’єкти --------
    private final Context appCtx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter btAdapter;
    private final BluetoothLeScanner btScanner;

    // -------- Скан --------
    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final List<String> foundNames = new ArrayList<>();
    private boolean isScanning = false;

    // -------- GATT --------
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic ioChar;
    private BluetoothDevice selectedDevice;
    private int currentMtu = 23;

    // Таймаути
    private final Runnable writeTimeout;
    private final Runnable notifyTimeout;

    // -------- Стани Wi-Fi патчу --------
    @Nullable private WifiPatchCallback wifiCallback;
    private boolean writeInFlight = false;
    private boolean writeSucceeded = false;
    private boolean pWifiOnly = false;

    // -------- Pending-конфіг --------
    private String pRoomName, pImageName, pSsid, pPassword, pUsername;
    private boolean pReset;

    // -------- ChipId --------
    private ChipIdListener chipIdListener;
    private final Set<String> receivedChipIds = new HashSet<>();

    // ★ NEW: підтримка бондингу
    private boolean awaitingBond = false;                     // ★ NEW
    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() { // ★ NEW
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
            BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (dev == null || selectedDevice == null) return;
            if (!dev.getAddress().equals(selectedDevice.getAddress())) return;

            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
            int prev  = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE);
            Log.d(TAG, "Bond state: " + prev + " -> " + state);
            if (state == BluetoothDevice.BOND_BONDED) {
                awaitingBond = false;
                // Після бондингу освіжимо кеш і перепідключимось
                refreshDeviceCache(gatt);
                reconnectGatt();
            } else if (state == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING) {
                awaitingBond = false;
                toast("Pairing відхилено");
                closeGatt(gatt);
                completeWifiError("Pairing cancelled");
            }
        }
    };

    public BleManager(Context context) {
        this.appCtx = context.getApplicationContext();
        BluetoothManager bm = (BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = (bm != null) ? bm.getAdapter() : null;
        this.btScanner = (btAdapter != null) ? btAdapter.getBluetoothLeScanner() : null;

        // Таймаути
        this.writeTimeout = () -> {
            if (pWifiOnly && !writeSucceeded) {
                completeWifiError("write timeout");
                closeGatt(gatt);
            }
        };
        this.notifyTimeout = () -> {
            if (!pWifiOnly) {
                Log.w(TAG, "notify timeout waiting for chipId");
                closeGatt(gatt);
            }
        };

        // ★ NEW: підписка на бонд-івенти
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        appCtx.registerReceiver(bondReceiver, f);
    }

    // ======== Перевірки/допоміжні ========
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

    private void toast(String msg) { main.post(() -> Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()); }

    // ======== Скан ========
    public void startBleScan(BleScanCallback cb) { startBleScan(DEFAULT_SCAN_TIMEOUT_MS, cb); }

    @SuppressLint("MissingPermission")
    public void startBleScan(int timeoutMs, BleScanCallback cb) {
        if (!isBluetoothSupported()) { toast("BLE недоступний"); return; }
        if (!isBluetoothEnabled())   { toast("Увімкніть Bluetooth"); return; }
        if (!hasAllBlePermissions()) { toast("Немає дозволів BLE"); return; }
        if (btScanner == null)       { toast("Сканер BLE недоступний"); return; }

        if (isScanning) stopScanInternal();
        isScanning = true;

        foundDevices.clear();
        foundNames.clear();

        try { btScanner.startScan(scanCb); }
        catch (SecurityException se) {
            Log.e(TAG, "startScan SecurityException", se);
            toast("Помилка доступу до BLE-сканера");
            isScanning = false; return;
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
        try { if (btScanner != null && has(Manifest.permission.BLUETOOTH_SCAN)) btScanner.stopScan(scanCb); }
        catch (SecurityException ignored) {}
        isScanning = false;
    }

    private final ScanCallback scanCb = new ScanCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice dev = result.getDevice();
            if (dev == null) return;
            if (!has(Manifest.permission.BLUETOOTH_CONNECT)) return;

            String name = dev.getName();
            if (name == null || !name.startsWith(NAME_PREFIX)) return;

            String address = dev.getAddress();
            for (BluetoothDevice d : foundDevices) if (d.getAddress().equals(address)) return;
            foundDevices.add(dev); foundNames.add(name);
        }
        @Override public void onScanFailed(int errorCode) { toast("Помилка сканування: " + errorCode); }
    };

    // ======== API вищого рівня ========
    public void setSelectedDevice(int index) { if (index >= 0 && index < foundDevices.size()) selectedDevice = foundDevices.get(index); }
    public BluetoothDevice getSelectedDevice() { return selectedDevice; }
    public void setChipIdListener(@Nullable ChipIdListener l) { this.chipIdListener = l; }
    public void removeChipIdListener() { this.chipIdListener = null; }
    public void clearChipIdCache() { receivedChipIds.clear(); }
    public void clearChipIdCache(String chipId) { receivedChipIds.remove(chipId); }

    // ======== Повна конфігурація (створення кімнати) ========
    @SuppressLint("MissingPermission")
    public void sendConfigToEsp32ViaDevice(BluetoothDevice device,
                                           String roomName, String imageName,
                                           String ssid, String password,
                                           String username, boolean reset) {
        if (device == null) { toast("BLE пристрій не вибрано"); return; }
        if (!isBluetoothEnabled()) { toast("Увімкніть Bluetooth"); return; }
        if (!hasAllBlePermissions()) { toast("Немає дозволів BLE"); return; }

        selectedDevice = device;
        cleanupGattOnly();

        pRoomName = roomName; pImageName = imageName; pSsid = ssid; pPassword = password; pUsername = username; pReset = reset;
        pWifiOnly = false; writeInFlight = false; writeSucceeded = false;

        connectGattFresh();
    }

    // ======== Оновлення лише Wi-Fi ========
    @SuppressLint("MissingPermission")
    public void sendWifiPatchViaDevice(BluetoothDevice device, String ssid, String password) {
        sendWifiPatchViaDevice(device, ssid, password, null);
    }

    @SuppressLint("MissingPermission")
    public void sendWifiPatchViaDevice(BluetoothDevice device, String ssid, String password, @Nullable WifiPatchCallback cb) {
        if (device == null) { toast("BLE пристрій не вибрано"); return; }
        if (!isBluetoothEnabled()) { toast("Увімкніть Bluetooth"); return; }
        if (!hasAllBlePermissions()) { toast("Немає дозволів BLE"); return; }

        selectedDevice = device;
        cleanupGattOnly();

        pRoomName = null; pImageName = null; pUsername = null; pReset = false; pSsid = ssid; pPassword = password; pWifiOnly = true;
        wifiCallback = cb; writeInFlight = false; writeSucceeded = false;

        connectGattFresh();
    }

    // ======== GATT lifecycle ========
    @SuppressLint("MissingPermission")
    private void connectGattFresh() {
        if (selectedDevice == null) { toast("Пристрій не вибрано"); return; }
        // ★ NEW: якщо вже є бонд — добре; якщо ні, система запитає під час операції
        try {
            gatt = selectedDevice.connectGatt(appCtx, false, gattCb); // ★ CHANGED: централізовано
        } catch (SecurityException se) {
            Log.e(TAG, "connectGatt SecurityException", se);
            toast("Помилка підключення");
            completeWifiError("Помилка підключення (SEC)");
        }
    }

    @SuppressLint("MissingPermission")
    private void reconnectGatt() { // ★ NEW
        closeGatt(gatt);
        try {
            gatt = selectedDevice.connectGatt(appCtx, false, gattCb);
        } catch (SecurityException se) {
            Log.e(TAG, "reconnectGatt SEC", se);
            completeWifiError("Помилка перепідключення");
        }
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: status=" + status + " newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= 21) {
                    boolean ok = g.requestMtu(REQUESTED_MTU);
                    Log.d(TAG, "requestMtu(" + REQUESTED_MTU + ")=" + ok);
                    if (Build.VERSION.SDK_INT >= 21) g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                } else {
                    g.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cancelAllTimeouts();
                if (pWifiOnly && !writeSucceeded) completeWifiError(status == BluetoothGatt.GATT_SUCCESS ? "Роз'єднано до запису" : "Роз'єднано, статус=" + status);
                main.postDelayed(() -> {
                    refreshDeviceCache(g);  // ★ NEW: освіжити кеш при виході
                    closeGatt(g);
                }, 150);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            currentMtu = mtu;
            Log.d(TAG, "MTU changed to " + mtu + ", status=" + status);
            if (has(Manifest.permission.BLUETOOTH_CONNECT)) {
                try { g.discoverServices(); } catch (SecurityException e) { Log.e(TAG, "discoverServices SEC", e); }
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (pWifiOnly) completeWifiError("onServicesDiscovered=" + status);
                return;
            }
            BluetoothGattService svc = g.getService(SERVICE_UUID);
            if (svc == null) { if (pWifiOnly) completeWifiError("Сервіс не знайдено"); return; }
            ioChar = svc.getCharacteristic(CHARACTERISTIC_UUID);
            if (ioChar == null) { if (pWifiOnly) completeWifiError("Характеристика не знайдена"); return; }

            int props = ioChar.getProperties();
            boolean canWrite = (props & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
            boolean canNotify = (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
            if (!canWrite) { if (pWifiOnly) completeWifiError("Характеристика не підтримує WRITE"); return; }

            if (pWifiOnly) {
                sendConfigNow(g);
            } else {
                boolean ok = g.setCharacteristicNotification(ioChar, true);
                Log.d(TAG, "setCharacteristicNotification=" + ok);
                BluetoothGattDescriptor cccd = ioChar.getDescriptor(CCCD_UUID);
                if (cccd != null) {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    boolean queued = g.writeDescriptor(cccd); // → onDescriptorWrite
                    if (!queued) {
                        // ★ NEW: ретрай через 200 мс
                        main.postDelayed(() -> {
                            boolean again = g.writeDescriptor(cccd);
                            Log.d(TAG, "writeDescriptor retry=" + again);
                            if (!again) sendConfigNow(g);
                        }, 200);
                    }
                } else {
                    sendConfigNow(g);
                }
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            if (!CCCD_UUID.equals(d.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendConfigNow(g);
            } else if (status == GATT_INSUFFICIENT_AUTHENTICATION || status == GATT_INSUFFICIENT_ENCRYPTION) {
                // ★ NEW: потрібен бонд → ініціюємо pairing і чекаємо
                ensureBondThenRetry();
            } else {
                Log.w(TAG, "onDescriptorWrite status=" + status + ", продовжимо без notify");
                sendConfigNow(g);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (c != ioChar) return;
            writeInFlight = false;
            main.removeCallbacks(writeTimeout);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeSucceeded = true;
                if (pWifiOnly) { completeWifiSuccess(); main.postDelayed(() -> closeGatt(g), 500); }
                else { main.postDelayed(notifyTimeout, NOTIFY_TIMEOUT_MS); }
            } else if (status == GATT_INSUFFICIENT_AUTHENTICATION || status == GATT_INSUFFICIENT_ENCRYPTION) {
                // ★ NEW: тригеримо бонд і повторимо
                ensureBondThenRetry();
            } else {
                if (pWifiOnly) { completeWifiError("onCharacteristicWrite=" + status); main.postDelayed(() -> closeGatt(g), 500); }
                else { Log.e(TAG, "WRITE FAIL status=" + status); closeGatt(g); }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            String value = new String(c.getValue(), StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) return;
            if (!pWifiOnly) {
                if (!receivedChipIds.add(value)) return;
                main.removeCallbacks(notifyTimeout);
                if (chipIdListener != null) chipIdListener.onChipId(value);
                main.postDelayed(() -> closeGatt(g), 800);
            }
        }
    };

    // ======== Надсилання конфігу ========
    @SuppressLint("MissingPermission")
    private void sendConfigNow(@Nullable BluetoothGatt g) {
        if (g == null || ioChar == null) { if (pWifiOnly) completeWifiError("BLE не готовий"); return; }

        final String encPwd = ENCRYPT_JSON_PASSWORD ? encryptPassword(pPassword) : pPassword; // ★ CHANGED
        try {
            JSONObject json = new JSONObject();
            if (pWifiOnly) {
                json.put("ssid", pSsid);
                json.put("password", encPwd);
            } else {
                if (pRoomName != null)  json.put("roomName", pRoomName);
                if (pImageName != null) json.put("imageName", pImageName);
                if (pSsid != null)      json.put("ssid", pSsid);
                if (pPassword != null)  json.put("password", encPwd);
                if (pUsername != null)  json.put("username", pUsername);
                json.put("reset", pReset);
            }

            byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
            int maxPayload = Math.max(20, currentMtu - 3);
            if (payload.length > maxPayload) {
                Log.w(TAG, "Payload " + payload.length + "> MTU payload " + maxPayload + ". Скороти поля або підтягни MTU.");
            }

            ioChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            ioChar.setValue(payload);
            boolean queued = g.writeCharacteristic(ioChar);
            writeInFlight = queued;
            Log.d(TAG, "writeCharacteristic queued=" + queued + ", bytes=" + payload.length);

            if (!queued) { if (pWifiOnly) completeWifiError("writeCharacteristic() повернув false"); else closeGatt(g); return; }
            if (pWifiOnly) main.postDelayed(writeTimeout, WRITE_TIMEOUT_MS);

        } catch (JSONException e) {
            Log.e(TAG, "JSON error", e);
            if (pWifiOnly) completeWifiError("JSON помилка"); else closeGatt(g);
        }
    }

    // ======== Безпека/бондинг/кеш ========

    // ★ NEW: ініціює бонд та очікує подію через BroadcastReceiver
    @SuppressLint("MissingPermission")
    private void ensureBondThenRetry() {
        if (selectedDevice == null) return;
        if (!has(Manifest.permission.BLUETOOTH_CONNECT)) { completeWifiError("Немає BLUETOOTH_CONNECT"); return; }
        int bs = selectedDevice.getBondState();
        Log.d(TAG, "ensureBondThenRetry, bondState=" + bs);
        if (bs == BluetoothDevice.BOND_BONDED) {
            // вже забондились — просто повторимо логіку
            main.postDelayed(this::reconnectGatt, 200);
            return;
        }
        if (!awaitingBond) {
            awaitingBond = true;
            try {
                boolean ok = selectedDevice.createBond();
                Log.d(TAG, "createBond() -> " + ok);
                if (!ok) {
                    awaitingBond = false;
                    completeWifiError("createBond() failed");
                }
            } catch (SecurityException se) {
                awaitingBond = false;
                completeWifiError("SEC при createBond()");
            }
        }
    }

    // ★ NEW: прихована очистка GATT-кешу (для перепрошивок/зміни атрибутів)
    private boolean refreshDeviceCache(@Nullable BluetoothGatt g) {
        if (g == null) return false;
        try {
            java.lang.reflect.Method m = g.getClass().getMethod("refresh");
            boolean result = (Boolean) m.invoke(g);
            Log.d(TAG, "refreshDeviceCache=" + result);
            return result;
        } catch (Exception e) {
            Log.w(TAG, "refreshDeviceCache failed", e);
            return false;
        }
    }

    // ======== Сервісні ========
    @SuppressLint("MissingPermission")
    public void cleanup() {
        stopScanInternal();
        cleanupGattOnly();
        cancelAllTimeouts();
        try { appCtx.unregisterReceiver(bondReceiver); } catch (Exception ignored) {} // ★ NEW
    }

    @SuppressLint("MissingPermission")
    private void cleanupGattOnly() {
        try { if (gatt != null) gatt.disconnect(); } catch (Exception ignored) {}
        try { if (gatt != null) gatt.close(); } catch (Exception ignored) {}
        gatt = null; ioChar = null; cancelAllTimeouts(); awaitingBond = false;
    }

    private void cancelAllTimeouts() { main.removeCallbacks(writeTimeout); main.removeCallbacks(notifyTimeout); }

    @SuppressLint("MissingPermission")
    private void closeGatt(@Nullable BluetoothGatt g) {
        if (g == null) return;
        if (has(Manifest.permission.BLUETOOTH_CONNECT)) {
            try { g.disconnect(); } catch (SecurityException ignored) {}
            try { g.close(); } catch (SecurityException ignored) {}
        } else { try { g.close(); } catch (Exception ignored) {} }
        if (g == this.gatt) { this.gatt = null; this.ioChar = null; }
        cancelAllTimeouts();
    }

    // ======== AES для пароля в JSON ========
    private String encryptPassword(String password) {
        if (password == null) return null;
        if (!ENCRYPT_JSON_PASSWORD) return password;
        try {
            String key = "my-secret-key-12"; // 16 байт = AES-128
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec iv = new IvParameterSpec(new byte[16]); // 16 нулів
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            byte[] enc = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(enc, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "encryptPassword error", e);
            return password; // fallback
        }
    }

    private void completeWifiSuccess() {
        final WifiPatchCallback cb = wifiCallback;
        wifiCallback = null;
        pWifiOnly = false;
        writeInFlight = false;
        writeSucceeded = false;
        cancelAllTimeouts();

        if (cb != null) {
            main.post(cb::onSuccess);
        } else {
            Log.w(TAG, "WifiPatchCallback is null, success ignored");
        }
    }

    private void completeWifiError(@Nullable String message) {
        final WifiPatchCallback cb = wifiCallback;
        wifiCallback = null;
        pWifiOnly = false;
        writeInFlight = false;
        writeSucceeded = false;
        cancelAllTimeouts();

        if (cb != null) {
            final String msg = (message != null) ? message : "BLE помилка";
            main.post(() -> cb.onError(msg));
        } else {
            Log.w(TAG, "WifiPatchCallback is null, error ignored: " + message);
        }
    }
}
