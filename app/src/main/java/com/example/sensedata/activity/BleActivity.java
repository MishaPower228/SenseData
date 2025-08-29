package com.example.sensedata.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Production-ready BLE менеджер з:
 * - керованими таймаутами/логами через Config
 * - захистом від витоків BroadcastReceiver
 * - синхронізованим закриттям GATT
 * - ретраями та бондингом
 * - опційним AES-шифруванням пароля в JSON
 *
 * Публічний API сумісний із попередньою версією.
 */
public class BleActivity {

    // ---------- Публічні колбеки ----------
    public interface BleScanCallback { void onDevicesFound(List<String> deviceNames, List<BluetoothDevice> devices); }
    public interface ChipIdListener { void onChipId(String chipId); }
    public interface WifiPatchCallback { void onSuccess(); void onError(String message); }

    // ---------- Конфіг ----------
    public static final class Config {
        public String deviceNamePrefix = "ESP32_";
        public UUID serviceUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        public UUID characteristicUuid = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
        public boolean debugLogs = false;

        public int defaultScanTimeoutMs = 3000;
        public int requestMtu = 185;             // payload ≈ MTU-3
        public int writeTimeoutMs = 8000;
        public int notifyTimeoutMs = 10000;

        public boolean encryptJsonPassword = true;
        /** 16 байт для AES-128. Якщо null → використається дефолтний ключ. */
        @Nullable public byte[] aesKey16 = "my-secret-key-12".getBytes(StandardCharsets.UTF_8);
        /** 16 байт IV. Якщо null → нулі. */
        @Nullable public byte[] aesIv16 = null;
    }

    // ---------- Константи/статуси ----------
    private static final String TAG = "BLE";
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int GATT_INSUFFICIENT_AUTHENTICATION = 5;
    private static final int GATT_INSUFFICIENT_ENCRYPTION     = 15;

    // ---------- Системні об’єкти ----------
    private final Context appCtx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter btAdapter;
    private final BluetoothLeScanner btScanner;
    private final Config cfg;

    // ---------- Скан ----------
    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final List<String> foundNames = new ArrayList<>();
    private volatile boolean isScanning = false;

    // ---------- GATT ----------
    @Nullable private BluetoothGatt gatt;
    @Nullable private BluetoothGattCharacteristic ioChar;
    @Nullable private BluetoothDevice selectedDevice;
    private volatile int currentMtu = 23;
    private final Object gattLock = new Object();

    // ---------- Таймаути ----------
    private final Runnable writeTimeout = this::onWriteTimeout;
    private final Runnable notifyTimeout = this::onNotifyTimeout;

    // ---------- Стани Wi-Fi патчу / конфігу ----------
    @Nullable private WifiPatchCallback wifiCallback;
    private volatile boolean writeInFlight = false;
    private volatile boolean writeSucceeded = false;
    private volatile boolean pWifiOnly = false;

    private String pRoomName, pImageName, pSsid, pPassword, pUsername;
    private boolean pReset;

    // ---------- ChipId ----------
    @Nullable private ChipIdListener chipIdListener;
    private final Set<String> receivedChipIds = Collections.synchronizedSet(new HashSet<>());

    // ---------- Бондинг ----------
    private final AtomicBoolean awaitingBond = new AtomicBoolean(false);
    private volatile boolean bondReceiverRegistered = false;
    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
            final BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (dev == null || selectedDevice == null) return;
            if (!dev.getAddress().equals(selectedDevice.getAddress())) return;

            final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
            final int prev  = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE);
            d("Bond state: " + prev + " -> " + state);

            if (state == BluetoothDevice.BOND_BONDED) {
                awaitingBond.set(false);
                refreshDeviceCache(gatt);
                reconnectGatt();
            } else if (state == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING) {
                awaitingBond.set(false);
                toast("Pairing відхилено");
                closeGattSafe(gatt);
                completeWifiError("Pairing cancelled");
            }
        }
    };

    // ---------- Конструктори ----------
    public BleActivity(Context context) {
        this(context, new Config());
    }

    public BleActivity(Context context, Config config) {
        this.appCtx = context.getApplicationContext();
        this.cfg = (config != null) ? config : new Config();
        BluetoothManager bm = (BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = (bm != null) ? bm.getAdapter() : null;
        this.btScanner = (btAdapter != null) ? btAdapter.getBluetoothLeScanner() : null;
    }

    // ---------- Перевірки/допоміжні ----------
    public boolean isBluetoothSupported() { return btAdapter != null; }
    public boolean isBluetoothEnabled() { return btAdapter != null && btAdapter.isEnabled(); }

    public boolean hasAllBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            return has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION);
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

    public Intent getEnableBluetoothIntent() { return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); }

    private boolean has(String perm) {
        return ContextCompat.checkSelfPermission(appCtx, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String msg) { main.post(() -> Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()); }
    private void d(String msg) { if (cfg.debugLogs) Log.d(TAG, msg); }
    private void w(String msg) { if (cfg.debugLogs) Log.w(TAG, msg); }
    private void e(String msg, Throwable t) { if (cfg.debugLogs) Log.e(TAG, msg, t); }

    // ---------- Скан ----------
    public void startBleScan(BleScanCallback cb) { startBleScan(cfg.defaultScanTimeoutMs, cb); }

    @SuppressLint("MissingPermission")
    public void startBleScan(int timeoutMs, BleScanCallback cb) {
        if (cb == null) return;
        if (!isBluetoothSupported()) { toast("BLE недоступний"); return; }
        if (!isBluetoothEnabled())   { toast("Увімкніть Bluetooth"); return; }
        if (!hasAllBlePermissions()) { toast("Немає дозволів BLE"); return; }
        if (btScanner == null)       { toast("Сканер BLE недоступний"); return; }

        stopScanInternal();
        isScanning = true;
        foundDevices.clear();
        foundNames.clear();

        try { btScanner.startScan(scanCb); }
        catch (SecurityException se) { e("startScan SEC", se); toast("Помилка доступу до BLE-сканера"); isScanning = false; return; }

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
            if (name == null || !name.startsWith(cfg.deviceNamePrefix)) return;

            String address = dev.getAddress();
            for (BluetoothDevice d : foundDevices) if (d.getAddress().equals(address)) return;
            foundDevices.add(dev);
            foundNames.add(name);
        }
        @Override public void onScanFailed(int errorCode) { toast("Помилка сканування: " + errorCode); }
    };

    // ---------- API вищого рівня ----------
    public void setSelectedDevice(int index) { if (index >= 0 && index < foundDevices.size()) selectedDevice = foundDevices.get(index); }
    @Nullable public BluetoothDevice getSelectedDevice() { return selectedDevice; }
    public void setChipIdListener(@Nullable ChipIdListener l) { this.chipIdListener = l; }
    public void removeChipIdListener() { this.chipIdListener = null; }
    public void clearChipIdCache() { receivedChipIds.clear(); }
    public void clearChipIdCache(String chipId) { if (chipId != null) receivedChipIds.remove(chipId); }

    // ---------- Повна конфігурація (створення кімнати) ----------
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

    // ---------- Оновлення лише Wi-Fi ----------
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

        pRoomName = null; pImageName = null; pUsername = null; pReset = false;
        pSsid = ssid; pPassword = password; pWifiOnly = true;
        wifiCallback = cb; writeInFlight = false; writeSucceeded = false;

        connectGattFresh();
    }

    // ---------- GATT lifecycle ----------
    @SuppressLint("MissingPermission")
    private void connectGattFresh() {
        if (selectedDevice == null) { toast("Пристрій не вибрано"); return; }
        try {
            synchronized (gattLock) {
                gatt = selectedDevice.connectGatt(appCtx, false, gattCb);
            }
        } catch (SecurityException se) { e("connectGatt SEC", se); toast("Помилка підключення"); completeWifiError("Помилка підключення (SEC)"); }
    }

    @SuppressLint("MissingPermission")
    private void reconnectGatt() {
        d("reconnectGatt()");
        closeGattSafe(gatt);
        try {
            synchronized (gattLock) {
                if (selectedDevice != null)
                    gatt = selectedDevice.connectGatt(appCtx, false, gattCb);
            }
        } catch (SecurityException se) { e("reconnectGatt SEC", se); completeWifiError("Помилка перепідключення"); }
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            d("onConnectionStateChange: status=" + status + " newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= 21) {
                    boolean ok = g.requestMtu(cfg.requestMtu);
                    d("requestMtu(" + cfg.requestMtu + ")=" + ok);
                    if (Build.VERSION.SDK_INT >= 21) g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                } else {
                    g.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cancelAllTimeouts();
                if (pWifiOnly && !writeSucceeded) completeWifiError(status == BluetoothGatt.GATT_SUCCESS ? "Роз'єднано до запису" : "Роз'єднано, статус=" + status);
                main.postDelayed(() -> { refreshDeviceCache(g); closeGattSafe(g); }, 150);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            currentMtu = mtu;
            d("MTU changed to " + mtu + ", status=" + status);
            if (has(Manifest.permission.BLUETOOTH_CONNECT)) {
                try { g.discoverServices(); } catch (SecurityException e) { e("discoverServices SEC", e); }
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { if (pWifiOnly) completeWifiError("onServicesDiscovered=" + status); return; }

            BluetoothGattService svc = g.getService(cfg.serviceUuid);
            if (svc == null) { if (pWifiOnly) completeWifiError("Сервіс не знайдено"); return; }
            ioChar = svc.getCharacteristic(cfg.characteristicUuid);
            if (ioChar == null) { if (pWifiOnly) completeWifiError("Характеристика не знайдена"); return; }

            int props = ioChar.getProperties();
            boolean canWrite = (props & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
            boolean canNotify = (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
            if (!canWrite) { if (pWifiOnly) completeWifiError("Характеристика не підтримує WRITE"); return; }

            if (pWifiOnly) {
                sendConfigNow(g);
            } else {
                boolean ok = g.setCharacteristicNotification(ioChar, true);
                d("setCharacteristicNotification=" + ok);
                BluetoothGattDescriptor cccd = ioChar.getDescriptor(CCCD_UUID);
                if (cccd != null) {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    boolean queued = g.writeDescriptor(cccd);
                    if (!queued) {
                        main.postDelayed(() -> {
                            boolean again = g.writeDescriptor(cccd);
                            d("writeDescriptor retry=" + again);
                            if (!again) sendConfigNow(g);
                        }, 200);
                    }
                } else {
                    sendConfigNow(g);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            if (!CCCD_UUID.equals(d.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendConfigNow(g);
            } else if (status == GATT_INSUFFICIENT_AUTHENTICATION || status == GATT_INSUFFICIENT_ENCRYPTION) {
                ensureBondThenRetry();
            } else {
                w("onDescriptorWrite status=" + status + ", продовжимо без notify");
                sendConfigNow(g);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (ioChar == null || c.getUuid() == null || !c.getUuid().equals(ioChar.getUuid())) return;

            writeInFlight = false;
            main.removeCallbacks(writeTimeout);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeSucceeded = true;
                if (pWifiOnly) {
                    completeWifiSuccess();
                    main.postDelayed(() -> closeGattSafe(g), 500);
                } else {
                    main.postDelayed(notifyTimeout, cfg.notifyTimeoutMs);
                }
            } else if (status == GATT_INSUFFICIENT_AUTHENTICATION || status == GATT_INSUFFICIENT_ENCRYPTION) {
                ensureBondThenRetry();
            } else {
                if (pWifiOnly) { completeWifiError("onCharacteristicWrite=" + status); main.postDelayed(() -> closeGattSafe(g), 500); }
                else { e("WRITE FAIL status=" + status, null); closeGattSafe(g); }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (c.getUuid() == null || !c.getUuid().equals(cfg.characteristicUuid)) return;
            String value = new String(c.getValue(), StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) return;
            if (!pWifiOnly) {
                if (!receivedChipIds.add(value)) return;
                main.removeCallbacks(notifyTimeout);
                if (chipIdListener != null) chipIdListener.onChipId(value);
                main.postDelayed(() -> closeGattSafe(g), 800);
            }
        }
    };

    // ---------- Надсилання конфігу ----------
    @SuppressLint("MissingPermission")
    private void sendConfigNow(@Nullable BluetoothGatt g) {
        if (g == null || ioChar == null) { if (pWifiOnly) completeWifiError("BLE не готовий"); return; }

        final String encPwd = cfg.encryptJsonPassword ? encryptPassword(pPassword) : pPassword;

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
                w("Payload " + payload.length + " > " + maxPayload + " (MTU-3). Скороти поля або підтягни MTU.");
            }

            ioChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            ioChar.setValue(payload);
            boolean queued = g.writeCharacteristic(ioChar);
            writeInFlight = queued;
            d("writeCharacteristic queued=" + queued + ", bytes=" + payload.length);

            if (!queued) { if (pWifiOnly) completeWifiError("writeCharacteristic() повернув false"); else closeGattSafe(g); return; }
            if (pWifiOnly) main.postDelayed(writeTimeout, cfg.writeTimeoutMs);

        } catch (JSONException e) {
            e("JSON error", e);
            if (pWifiOnly) completeWifiError("JSON помилка"); else closeGattSafe(g);
        }
    }

    private void onWriteTimeout() {
        if (pWifiOnly && !writeSucceeded) {
            completeWifiError("write timeout");
            closeGattSafe(gatt);
        }
    }

    private void onNotifyTimeout() {
        if (!pWifiOnly) {
            w("notify timeout waiting for chipId");
            closeGattSafe(gatt);
        }
    }

    // ---------- Безпека/бондинг/кеш ----------
    @SuppressLint("MissingPermission")
    private void ensureBondThenRetry() {
        if (selectedDevice == null) return;
        if (!has(Manifest.permission.BLUETOOTH_CONNECT)) { completeWifiError("Немає BLUETOOTH_CONNECT"); return; }

        int bs = selectedDevice.getBondState();
        d("ensureBondThenRetry, bondState=" + bs);

        if (bs == BluetoothDevice.BOND_BONDED) {
            main.postDelayed(this::reconnectGatt, 200);
            return;
        }
        if (awaitingBond.compareAndSet(false, true)) {
            registerBondReceiverIfNeeded();
            try {
                boolean ok = selectedDevice.createBond();
                d("createBond() -> " + ok);
                if (!ok) {
                    awaitingBond.set(false);
                    completeWifiError("createBond() failed");
                }
            } catch (SecurityException se) {
                awaitingBond.set(false);
                completeWifiError("SEC при createBond()");
            }
        }
    }

    private void registerBondReceiverIfNeeded() {
        if (bondReceiverRegistered) return;
        try {
            IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            appCtx.registerReceiver(bondReceiver, f);
            bondReceiverRegistered = true;
        } catch (Exception ex) {
            e("registerReceiver failed", ex);
        }
    }

    private void unregisterBondReceiverIfNeeded() {
        if (!bondReceiverRegistered) return;
        try {
            appCtx.unregisterReceiver(bondReceiver);
        } catch (Exception ignored) {}
        bondReceiverRegistered = false;
    }

    /** Прихована очистка GATT-кешу (для перепрошивок/зміни атрибутів) */
    private boolean refreshDeviceCache(@Nullable BluetoothGatt g) {
        if (g == null) return false;
        try {
            java.lang.reflect.Method m = g.getClass().getMethod("refresh");
            boolean result = (Boolean) m.invoke(g);
            d("refreshDeviceCache=" + result);
            return result;
        } catch (Exception e) {
            w("refreshDeviceCache failed: " + e.getMessage());
            return false;
        }
    }

    // ---------- Сервісні ----------
    /** Викликайте у onDestroy() активності/фрагмента */
    @SuppressLint("MissingPermission")
    public void cleanup() {
        stopScanInternal();
        cleanupGattOnly();
        cancelAllTimeouts();
        unregisterBondReceiverIfNeeded();
        chipIdListener = null;
        wifiCallback = null;
    }

    @SuppressLint("MissingPermission")
    private void cleanupGattOnly() {
        closeGattSafe(gatt);
        ioChar = null;
    }

    private void cancelAllTimeouts() {
        main.removeCallbacks(writeTimeout);
        main.removeCallbacks(notifyTimeout);
    }

    /** Безпечне закриття GATT з уникненням гонок/подвійних close */
    @SuppressLint("MissingPermission")
    private void closeGattSafe(@Nullable BluetoothGatt g) {
        if (g == null) return;
        synchronized (gattLock) {
            BluetoothGatt local = gatt;
            // обнуляємо посилання ПЕРЕД закриттям, щоб інші гілки бачили, що GATT вже “пішов”
            if (g == local) gatt = null;
            try { if (has(Manifest.permission.BLUETOOTH_CONNECT)) g.disconnect(); } catch (Throwable ignored) {}
            try { g.close(); } catch (Throwable ignored) {}
        }
        cancelAllTimeouts();
    }

    // ---------- AES для пароля в JSON ----------
    private String encryptPassword(String password) {
        if (password == null) return null;
        if (!cfg.encryptJsonPassword) return password;
        try {
            byte[] key = (cfg.aesKey16 != null && cfg.aesKey16.length == 16) ? cfg.aesKey16 : "my-secret-key-12".getBytes(StandardCharsets.UTF_8);
            byte[] iv  = (cfg.aesIv16  != null && cfg.aesIv16.length  == 16) ? cfg.aesIv16  : new byte[16];
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] enc = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(enc, Base64.NO_WRAP);
        } catch (Exception e) {
            e("encryptPassword error", e);
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
            d("WifiPatchCallback is null, success ignored");
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
            w("WifiPatchCallback is null, error ignored: " + message);
        }
    }


    // ---------- Utility ----------
    private boolean hasBleConnect() { return has(Manifest.permission.BLUETOOTH_CONNECT); }
}
