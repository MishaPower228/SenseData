package com.example.sensedata;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class BleManager {
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bleScanner;

    private final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final UUID CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");

    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final List<String> foundDeviceNames = new ArrayList<>();

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothDevice selectedDevice;

    // Pending config for delayed write
    private String roomNamePending, imageNamePending, ssidPending, passwordPending, usernamePending;
    private boolean resetPending;

    private final Set<String> receivedChipIds = new HashSet<>();

    public BleManager(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.bleScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public void startBleScan(BleScanCallback callback) {
        foundDevices.clear();
        foundDeviceNames.clear();

        if (!checkPermissions()) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 102);
            return;
        }

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            try {
                scanner.startScan(scanCallback);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    scanner.stopScan(scanCallback);
                    callback.onDevicesFound(foundDeviceNames, foundDevices);
                }, 3000);
            } catch (SecurityException e) {
                e.printStackTrace();
                Toast.makeText(context, "Немає дозволу на BLE-сканування", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                return;

            if (device != null) {
                String name = device.getName();
                String address = device.getAddress();

                if (name == null || !name.startsWith("ESP32_")) return;

                boolean alreadyAdded = false;
                for (BluetoothDevice d : foundDevices) {
                    if (d.getAddress().equals(address)) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    foundDevices.add(device);
                    foundDeviceNames.add(name);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(context, "Помилка сканування: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    public void sendConfigToEsp32ViaDevice(BluetoothDevice device, String roomName, String imageName, String ssid, String password, String username, boolean reset) {
        if (device == null) {
            Toast.makeText(context, "BLE пристрій не вибрано", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDevice = device;

        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
            writeCharacteristic = null;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
            return;
        }

        roomNamePending = roomName;
        imageNamePending = imageName;
        ssidPending = ssid;
        passwordPending = password;
        usernamePending = username;
        resetPending = reset;

        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic != null) {
                    writeCharacteristic = characteristic;
                    bluetoothGatt = gatt;

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        boolean notifySet = gatt.setCharacteristicNotification(writeCharacteristic, true);
                        if (notifySet) {
                            BluetoothGattDescriptor descriptor = writeCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }
                        }
                    } else {
                        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 104);
                    }

                    sendConfigToEsp32(roomNamePending, imageNamePending, ssidPending, passwordPending, usernamePending, resetPending);
                }
            } else {
                Toast.makeText(context, "Сервіс ESP32 не знайдено", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String value = new String(characteristic.getValue(), StandardCharsets.UTF_8);
            Log.d("BLE", "Отримано з ESP32: " + value);

            try {
                JSONObject json = new JSONObject(value);
                if (json.has("chipId")) {
                    String chipId = json.getString("chipId");
                    Log.d("BLE", "chipId: " + chipId);

                    // ✅ Debounce: якщо chipId вже оброблений — не повторювати
                    if (receivedChipIds.contains(chipId)) {
                        Log.d("BLE", "chipId вже оброблений: " + chipId);
                        return;
                    }
                    receivedChipIds.add(chipId);

                    if (context instanceof MainActivity) {
                        ((MainActivity) context).onChipIdReceivedFromEsp32(chipId);
                    }

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.disconnect();
                            gatt.close();
                            Log.d("BLE", "BLE-з'єднання завершено");
                        }
                    }, 1000);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private void sendConfigToEsp32(String roomName, String imageName, String ssid, String password, String username, boolean reset) {
        if (writeCharacteristic == null || bluetoothGatt == null) {
            Toast.makeText(context, "BLE не готовий", Toast.LENGTH_SHORT).show();
            return;
        }

        String encryptedPassword = encryptPassword(password);

        try {
            JSONObject json = new JSONObject();
            json.put("roomName", roomName);
            json.put("imageName", imageName);
            json.put("ssid", ssid);
            json.put("password", encryptedPassword);
            json.put("username", username);
            json.put("reset", reset);

            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writeCharacteristic.setValue(json.toString().getBytes(StandardCharsets.UTF_8));

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                Toast.makeText(context, success ? "Дані надіслано ESP32" : "Помилка надсилання", Toast.LENGTH_SHORT).show();
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String encryptPassword(String password) {
        try {
            String key = "my-secret-key-12";
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return password;
        }
    }

    public void setSelectedDevice(int index) {
        if (index >= 0 && index < foundDevices.size()) {
            selectedDevice = foundDevices.get(index);
        }
    }

    public BluetoothDevice getSelectedDevice() {
        return selectedDevice;
    }

    public void cleanup() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public interface BleScanCallback {
        void onDevicesFound(List<String> deviceNames, List<BluetoothDevice> devices);
    }

    public void clearChipIdCache(String chipId) {
        // нічого не робить
    }
}
