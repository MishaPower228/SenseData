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

    // ===== Контекст та BLE-компоненти =====
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bleScanner;

    // ===== UUID сервісу та характеристики ESP32 =====
    private final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final UUID CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");

    // ===== Списки знайдених пристроїв =====
    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final List<String> foundDeviceNames = new ArrayList<>();

    // ===== Поточне з'єднання =====
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothDevice selectedDevice;

    public BleManager(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.bleScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    /**
     * Запуск BLE-сканування. Через 3 секунди повертає список знайдених пристроїв у колбек.
     */
    public void startBleScan(BleScanCallback callback) {
        foundDevices.clear();
        foundDeviceNames.clear();

        // Перевірка дозволів
        if (!checkPermissions()) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN
            }, 102);
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

    /**
     * Колбек при знаходженні пристроїв під час сканування.
     */
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                return;

            if (device != null) {
                String name = device.getName();
                String address = device.getAddress();

                // Ігноруємо, якщо ім’я не починається з ESP32_
                if (name == null || !name.startsWith("ESP32_")) return;

                // Перевірка, щоб MAC не дублювався
                boolean alreadyAdded = false;
                for (BluetoothDevice d : foundDevices) {
                    if (d.getAddress().equals(address)) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    foundDevices.add(device);
                    foundDeviceNames.add(name); // Додай назву як є (ESP32_1234ABCD)
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(context, "Помилка сканування: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * Надсилання конфігурації на ESP32.
     */
    public void sendConfigToEsp32ViaDevice(BluetoothDevice device, String roomName, String imageName, String ssid, String password, String username, boolean reset) {
        if (device == null) {
            Toast.makeText(context, "BLE пристрій не вибрано", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDevice = device;

        // ✅ Очистити попереднє з'єднання
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
            writeCharacteristic = null;
        }

        // Перевірка дозволу
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT
            }, 101);
            return;
        }

        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
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
                        sendConfigToEsp32(roomName, imageName, ssid, password, username, reset);
                    }
                } else {
                    Toast.makeText(context, "Сервіс ESP32 не знайдено", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


    /**
     * Встановлення вибраного пристрою.
     */
    public void setSelectedDevice(int index) {
        if (index >= 0 && index < foundDevices.size()) {
            selectedDevice = foundDevices.get(index);
        }
    }

    public BluetoothDevice getSelectedDevice() {
        return selectedDevice;
    }

    /**
     * Відправлення JSON-конфігурації на ESP32.
     */
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

                // ✅ Відключення через 500 мс
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt.disconnect();
                        bluetoothGatt.close();
                        bluetoothGatt = null;
                        writeCharacteristic = null;
                    }
                }, 500);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * Шифрування пароля (AES-128 ECB).
     */
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

    public void sendRawJsonToEsp32(BluetoothDevice device, String jsonString) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BLE_RESET", "Немає дозволу BLUETOOTH_CONNECT");
            return;
        }

        if (bluetoothGatt == null || writeCharacteristic == null) {
            Log.e("BLE_RESET", "BLE не готовий для запису");
            return;
        }

        writeCharacteristic.setValue(jsonString.getBytes(StandardCharsets.UTF_8));
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);

        Log.d("BLE_RESET", "Write success: " + success + ", Data: " + jsonString);
    }


    /**
     * Перевірка необхідних BLE-дозволів.
     */
    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Колбек для повернення списку знайдених пристроїв у Spinner.
     */
    public interface BleScanCallback {
        void onDevicesFound(List<String> deviceNames, List<BluetoothDevice> devices);
    }

    /**
     * Очистка після роботи з BLE.
     */
    public void cleanup() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }
}
