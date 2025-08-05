package com.example.sensedata;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.example.sensedata.model.RoomWithSensorDto;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class BleManager {
    private BluetoothDevice selectedDevice;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> spinnerAdapter;
    private final BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;

    private final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final UUID CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");

    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final List<String> foundDeviceNames = new ArrayList<>();
    private boolean scanning = false;

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            BluetoothDevice device = result.getDevice();
            if (device != null && !foundDevices.contains(device)) {
                foundDevices.add(device);
                String name = (device.getName() != null) ? device.getName() : "ESP32";
                foundDeviceNames.add(name);

                if (spinnerAdapter != null) {
                    ((Activity) context).runOnUiThread(() -> spinnerAdapter.notifyDataSetChanged());
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(context, "Помилка сканування: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    private final ScanCallback scanCallback = bleScanCallback;

    public BleManager(Context context) {
        this.context = context;

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public void startBleScanForSelection() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 101);
            return;
        }

        foundDevices.clear();
        foundDeviceNames.clear();

        scanning = true;

        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bleScanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        }

        new Handler().postDelayed(() -> {
            if (scanning) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner.stopScan(scanCallback);
                }
                scanning = false;
                showBleDeviceDialog();
            }
        }, 4000);
    }

    public void setSpinnerAdapter(ArrayAdapter<String> adapter) {
        this.spinnerAdapter = adapter;
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void connectToDeviceAndSendConfig(BluetoothDevice device, String roomName, String imageName, String ssid, String password, String username) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
            return;
        }

        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
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
                        sendConfigToEsp32(roomName, imageName, ssid, password, username);
                    }
                }
            }
        });
    }

    public void sendConfigToEsp32(String roomName, String imageName, String ssid, String password, String username) {
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

            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writeCharacteristic.setValue(json.toString().getBytes(StandardCharsets.UTF_8));

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                Toast.makeText(context, success ? "Дані надіслано ESP32" : "Помилка надсилання", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public interface BleScanCallback {
        void onDevicesFound(List<String> deviceNames, List<BluetoothDevice> devices);
    }

    public void startBleScan(BleScanCallback callback) {
        foundDevices.clear();
        foundDeviceNames.clear();
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null && checkPermissions()) {
            scanner.startScan(scanCallback);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                scanner.stopScan(scanCallback);
                callback.onDevicesFound(foundDeviceNames, foundDevices);
            }, 3000);
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

    private void showBleDeviceDialog() {
        if (foundDevices.isEmpty()) {
            Toast.makeText(context, "Жодного ESP32 не знайдено", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] deviceNames = foundDeviceNames.toArray(new String[0]);
        new AlertDialog.Builder(context)
                .setTitle("Оберіть ESP32")
                .setItems(deviceNames, (dialog, which) -> connectToDevice(foundDevices.get(which)))
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
            return;
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        Toast.makeText(context, "🔗 Підключення до " + device.getName(), Toast.LENGTH_SHORT).show();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                return;

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                bluetoothGatt = gatt;
                ((MainActivity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Підключено до ESP32", Toast.LENGTH_SHORT).show();
                    ((MainActivity) context).showCreateRoomDialog();
                });
            }
        }
    };

    public void sendConfigToEsp32ViaDevice(BluetoothDevice device, String roomName, String imageName, String ssid, String password, String username) {
        if (device == null) {
            Toast.makeText(context, "BLE пристрій не вибрано", Toast.LENGTH_SHORT).show();
            return;
        }

        this.selectedDevice = device;
        connectToDeviceAndSendConfig(device, roomName, imageName, ssid, password, username);
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

    public void handlePermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Дозвіл на Bluetooth надано", Toast.LENGTH_SHORT).show();
        }
    }

    public void cleanup() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }
}