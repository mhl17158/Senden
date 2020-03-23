package com.example.senden_library.common.messages;


import com.example.senden_library.common.SendenDevice;

public class RegistrationMessageContent {

    private SendenDevice sendenDevice;

    public SendenDevice getSendenDevice() {
        return sendenDevice;
    }

    public void setSendenDevice(SendenDevice sendenDevice) {
        this.sendenDevice = sendenDevice;
    }

}
