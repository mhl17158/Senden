package com.example.senden_library.common.messages;



import com.example.senden_library.common.SendenDevice;

import java.util.List;

public class RegisteredDevicesMessageContent {

    private List<SendenDevice> devicesRegistered;

    public List<SendenDevice> getDevicesRegistered() {
        return devicesRegistered;
    }

    public void setDevicesRegistered(List<SendenDevice> devicesRegistered) {
        this.devicesRegistered = devicesRegistered;
    }

}
