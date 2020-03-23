package com.example.senden_library.common.listeners;


import com.example.senden_library.common.SendenServiceDevice;
import com.example.senden_library.common.WiFiP2PError;

import java.util.List;

public interface ServiceDiscoveredListener {

    void onNewServiceDeviceDiscovered(SendenServiceDevice serviceDevice);

    void onFinishServiceDeviceDiscovered(List<SendenServiceDevice> serviceDevices);

    void onError(WiFiP2PError wiFiP2PError);

}
