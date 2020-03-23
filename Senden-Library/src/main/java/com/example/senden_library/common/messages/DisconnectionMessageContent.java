package com.example.senden_library.common.messages;


import com.example.senden_library.common.SendenDevice;

public class DisconnectionMessageContent {

    private SendenDevice sendenDevice;


    public void setSendenDevice(SendenDevice wroupDevice) {
        this.sendenDevice = sendenDevice;
    }

    public SendenDevice getSendenDevice() {
        return sendenDevice;
    }

}
