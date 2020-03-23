package com.example.senden_library.common;


import android.net.wifi.p2p.WifiP2pDevice;

import java.util.Map;

public class SendenServiceDevice extends SendenDevice {


    private Map<String, String> txtRecordMap;

    public SendenServiceDevice(WifiP2pDevice wifiP2pDevice) {
        super(wifiP2pDevice);
    }

    public Map<String, String> getTxtRecordMap() {
        return txtRecordMap;
    }

    public void setTxtRecordMap(Map<String, String> txtRecordMap) {
        this.txtRecordMap = txtRecordMap;
    }

}
