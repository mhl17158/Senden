package com.example.senden_library.common.listeners;


import com.example.senden_library.common.WiFiP2PError;

public interface ServiceRegisteredListener {

    void onSuccessServiceRegistered();

    void onErrorServiceRegistered(WiFiP2PError wiFiP2PError);

}
