package com.google.android.mobly.snippet.bundled.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.MediaSnippet; // Import MediaSnippet for media control
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import com.google.android.mobly.snippet.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONException;

public class LeAudioClientSnippet implements Snippet {
    private final Context context;
    private final EventCache eventCache;
    private final HashMap<String, HashMap<String, BluetoothGattCharacteristic>> characteristicHashMap;

    private BluetoothGatt bluetoothGattClient;
    private MediaSnippet mediaSnippet; // Instance for media control

    public LeAudioClientSnippet(Context context) {
        this.context = context;
        this.eventCache = EventCache.getInstance();
        this.characteristicHashMap = new HashMap<>();
        this.mediaSnippet = new MediaSnippet();  // Initialize MediaSnippet
    }

    // The bleConnectGatt method from BluetoothGattClientSnippet with pairing handling
    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @AsyncRpc(description = "Start LE Audio client Connect.")
    public void LeAudioConnectGatt(String callbackId, String deviceAddress) throws JSONException {
        BluetoothDevice remoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

        // First connection attempt (might not be secure)
        BluetoothGattCallback gattCallback = new DefaultBluetoothGattCallback(callbackId);
        bluetoothGattClient = remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);

        // After the first connection, initiate a second connection to enforce secure pairing (if required)
        // The second connection attempt forces secure pairing
        bluetoothGattClient = remoteDevice.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
    
        Log.d("Bluetooth" + "Attempting to connect with secure connection...");
    }

    // The bleDisconnect method from BluetoothGattClientSnippet
    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Stop BLE client.")
    public void LeAudioDisconnect() throws Exception {
        if (bluetoothGattClient == null) {
            throw new Exception("BLE client is not initialized.");
        }
        bluetoothGattClient.disconnect();
    }

    // LE Audio control methods(function from MediaSnippet)
    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Play media using MediaSnippet")
    public void LeAudioPlayMedia(String mediaUri) throws IOException {
        mediaSnippet.mediaPlayAudioFile(mediaUri);
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Pause media using MediaSnippet")
    public void LeAudioPauseMedia() {
        mediaSnippet.mediaPause();
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Stop media using MediaSnippet")
    public void LeAudioStopMedia() throws IOException {
        mediaSnippet.mediaStop();
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Start BLE service discovery")
    public long LeAudioDiscoverServices() throws Exception {
        if (bluetoothGattClient == null) {
            throw new Exception("BLE client is not initialized.");
        }
        long discoverServicesStartTime = SystemClock.elapsedRealtimeNanos();
        boolean result = bluetoothGattClient.discoverServices();
        if (!result) {
            throw new Exception("Discover services returned false.");
        }
        return discoverServicesStartTime;
    }

    // Default BluetoothGattCallback to handle events and secure connection prompts
    private class DefaultBluetoothGattCallback extends BluetoothGattCallback {
        private final String callbackId;

        DefaultBluetoothGattCallback(String callbackId) {
            this.callbackId = callbackId;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            SnippetEvent event = new SnippetEvent(callbackId, "onConnectionStateChange");
            event.getData().putString("status", Integer.toString(status));
            event.getData().putString("newState", Integer.toString(newState));
            eventCache.postEvent(event);

            // Handle pairing prompts or secure connection states (if applicable)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("Bluetooth" + "LE Audio device connected, checking for pairing...");
                // Check if secure connection or pairing is required, handle accordingly
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            SnippetEvent event = new SnippetEvent(callbackId, "onServiceDiscovered");
            event.getData().putString("status", Integer.toString(status));

            // Update characteristicHashMap with a HashMap of UUID -> Characteristic for each service
            ArrayList<Bundle> services = new ArrayList<>();
            for (BluetoothGattService service : gatt.getServices()) {
                HashMap<String, BluetoothGattCharacteristic> characteristicsMap = new HashMap<>();
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    characteristicsMap.put(characteristic.getUuid().toString(), characteristic);
                }
                characteristicHashMap.put(service.getUuid().toString(), characteristicsMap);
                services.add(JsonSerializer.serializeBluetoothGattService(service));
            }
            event.getData().putParcelableArrayList("Services", services);
            eventCache.postEvent(event);
        }
    }

    @Override
    public void shutdown() {
        if (bluetoothGattClient != null) {
            bluetoothGattClient.close();
        }
    }
}