package com.example.senden_library.service;


import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.example.senden_library.common.SendenDevice;
import com.example.senden_library.common.WiFiP2PError;
import com.example.senden_library.common.WiFiP2PInstance;
import com.example.senden_library.common.direct.WiFiDirectUtils;
import com.example.senden_library.common.listeners.ClientConnectedListener;
import com.example.senden_library.common.listeners.ClientDisconnectedListener;
import com.example.senden_library.common.listeners.DataReceivedListener;
import com.example.senden_library.common.listeners.PeerConnectedListener;
import com.example.senden_library.common.listeners.ServiceRegisteredListener;
import com.example.senden_library.common.messages.DisconnectionMessageContent;
import com.example.senden_library.common.messages.MessageWrapper;
import com.example.senden_library.common.messages.RegisteredDevicesMessageContent;
import com.example.senden_library.common.messages.RegistrationMessageContent;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SendenService implements PeerConnectedListener {


    private static final String TAG = SendenService.class.getSimpleName();

    private static final String SERVICE_TYPE = "_senden._tcp";
    public static final String SERVICE_PORT_PROPERTY = "SERVICE_PORT";
    public static final Integer SERVICE_PORT_VALUE = 9999;
    public static final String SERVICE_NAME_PROPERTY = "SERVICE_NAME";
    public static final String SERVICE_NAME_VALUE = "SENDEN";
    public static final String SERVICE_GROUP_NAME = "GROUP_NAME";

    private static SendenService instance;

    private DataReceivedListener dataReceivedListener;
    private ClientConnectedListener clientConnectedListener;
    private ClientDisconnectedListener clientDisconnectedListener;
    private Map<String, SendenDevice> clientsConnected = new HashMap<String, SendenDevice>();
    private WiFiP2PInstance wiFiP2PInstance;

    private ServerSocket serverSocket;
    private Boolean groupAlreadyCreated = false;

    private SendenService(Context context) {
        wiFiP2PInstance = WiFiP2PInstance.getInstance(context);
        wiFiP2PInstance.setPeerConnectedListener(this);
    }

    public static SendenService getInstance(Context context) {
        if (instance == null) {
            instance = new SendenService(context);
        }
        return instance;
    }

    public void registerService(String groupName, ServiceRegisteredListener serviceRegisteredListener) {
        registerService(groupName, null, serviceRegisteredListener);
    }
    public void registerService(String groupName, Map<String, String> customProperties, final ServiceRegisteredListener serviceRegisteredListener) {

        wiFiP2PInstance.startPeerDiscovering();

        Map<String, String> record = new HashMap<>();
        record.put(SERVICE_PORT_PROPERTY, SERVICE_PORT_VALUE.toString());
        record.put(SERVICE_NAME_PROPERTY, SERVICE_NAME_VALUE);
        record.put(SERVICE_GROUP_NAME, groupName);

        if (customProperties != null) {
            for (Map.Entry<String, String> entry : customProperties.entrySet()) {
                record.put(entry.getKey(), entry.getValue());
            }
        }

        WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(groupName, SERVICE_TYPE, record);

        wiFiP2PInstance.getWifiP2pManager().clearLocalServices(wiFiP2PInstance.getChannel(), new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Success clearing local services");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Error clearing local services: " + reason);
            }
        });

        wiFiP2PInstance.getWifiP2pManager().addLocalService(wiFiP2PInstance.getChannel(), serviceInfo, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Service registered");
                serviceRegisteredListener.onSuccessServiceRegistered();

                // Create the group to the clients can connect to it
                removeAndCreateGroup();

                // Create the socket that will accept request
                createServerSocket();
            }

            @Override
            public void onFailure(int reason) {
                WiFiP2PError wiFiP2PError = WiFiP2PError.fromReason(reason);
                if (wiFiP2PError != null) {
                    Log.e(TAG, "Failure registering the service. Reason: " + wiFiP2PError.name());
                    serviceRegisteredListener.onErrorServiceRegistered(wiFiP2PError);
                }
            }

        });
    }

    /**
     * Remove the group created. Before the disconnection, the server sends a message to all
     * clients connected to notify the disconnection.
     */
    public void disconnect() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                Log.i(TAG, "ServerSocket closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing the serverSocket");
            }
        }

        groupAlreadyCreated = false;
        serverSocket = null;
        clientsConnected.clear();

        WiFiDirectUtils.removeGroup(wiFiP2PInstance);
        WiFiDirectUtils.clearLocalServices(wiFiP2PInstance);
        WiFiDirectUtils.stopPeerDiscovering(wiFiP2PInstance);
    }

    /**
     * Set the listener to know when data is received from the client devices connected to the group.
     *
     * @param dataReceivedListener The <code>DataReceivedListener</code> to notify data entries.
     */
    public void setDataReceivedListener(DataReceivedListener dataReceivedListener) {
        this.dataReceivedListener = dataReceivedListener;
    }

    /**
     * Set the listener to know when a client has been disconnected from the group.
     *
     * @param clientDisconnectedListener The <code>ClientDisconnectedListener</code> to notify
     *                                   client disconnections.
     */
    public void setClientDisconnectedListener(ClientDisconnectedListener clientDisconnectedListener) {
        this.clientDisconnectedListener = clientDisconnectedListener;
    }

    /**
     * Set the listener to know when a new client is registered in the group.
     *
     * @param clientConnectedListener The <code>ClientConnectedListener</code> to notify new
     *                                 connections in the group.
     */
    public void setClientConnectedListener(ClientConnectedListener clientConnectedListener) {
        this.clientConnectedListener = clientConnectedListener;
    }

    @Override
    public void onPeerConnected(WifiP2pInfo wifiP2pInfo) {
        Log.i(TAG, "OnPeerConnected...");

        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
            Log.i(TAG, "I am the group owner");
            Log.i(TAG, "My addess is: " + wifiP2pInfo.groupOwnerAddress.getHostAddress());
        }
    }

    /**
     * Send a message to all the devices connected to the group.
     *
     * @param message The message to be sent.
     */
    public void sendMessageToAllClients(final MessageWrapper message) {
        for (SendenDevice clientDevice : clientsConnected.values()) {
            sendMessage(clientDevice, message);
        }
    }

    /**
     * Send a message to the desired device who it's connected in the group.
     *
     * @param device  The receiver of the message.
     * @param message The message to be sent.
     */
    public void sendMessage(final SendenDevice device, MessageWrapper message) {
        // Set the actual device to the message
        message.setSendenDevice(wiFiP2PInstance.getThisDevice());

        new AsyncTask<MessageWrapper, Void, Void>() {
            @Override
            protected Void doInBackground(MessageWrapper... params) {
                if (device != null && device.getDeviceServerSocketIP() != null) {
                    try {
                        Socket socket = new Socket();
                        socket.bind(null);

                        InetSocketAddress hostAddres = new InetSocketAddress(device.getDeviceServerSocketIP(), device.getDeviceServerSocketPort());
                        socket.connect(hostAddres, 2000);

                        Gson gson = new Gson();
                        String messageJson = gson.toJson(params[0]);

                        OutputStream outputStream = socket.getOutputStream();
                        outputStream.write(messageJson.getBytes(), 0, messageJson.getBytes().length);

                        Log.d(TAG, "Sending data: " + params[0]);

                        socket.close();
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error creating client socket: " + e.getMessage());
                    }
                }

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
    }

    private void createServerSocket() {
        if (serverSocket == null) {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {

                    try {
                        serverSocket = new ServerSocket(SERVICE_PORT_VALUE);
                        Log.i(TAG, "Server socket created. Accepting requests...");

                        while (true) {
                            Socket socket = serverSocket.accept();

                            String dataReceived = IOUtils.toString(socket.getInputStream());
                            Log.i(TAG, "Data received: " + dataReceived);
                            Log.i(TAG, "From IP: " + socket.getInetAddress().getHostAddress());

                            Gson gson = new Gson();
                            MessageWrapper messageWrapper = gson.fromJson(dataReceived, MessageWrapper.class);
                            onMessageReceived(messageWrapper, socket.getInetAddress());
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error creating/closing server socket: " + e.getMessage());
                    }

                    return null;
                }

            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }


    private void removeAndCreateGroup() {
        wiFiP2PInstance.getWifiP2pManager().requestGroupInfo(wiFiP2PInstance.getChannel(), new WifiP2pManager.GroupInfoListener() {

            @Override
            public void onGroupInfoAvailable(final WifiP2pGroup group) {
                if (group != null) {
                    wiFiP2PInstance.getWifiP2pManager().removeGroup(wiFiP2PInstance.getChannel(), new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Group deleted");
                            Log.d(TAG, "\tNetwordk Name: " + group.getNetworkName());
                            Log.d(TAG, "\tInterface: " + group.getInterface());
                            Log.d(TAG, "\tPassword: " + group.getPassphrase());
                            Log.d(TAG, "\tOwner Name: " + group.getOwner().deviceName);
                            Log.d(TAG, "\tOwner Address: " + group.getOwner().deviceAddress);
                            Log.d(TAG, "\tClient list size: " + group.getClientList().size());

                            groupAlreadyCreated = false;

                            // Now we can create the group
                            createGroup();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "Error deleting group");
                        }
                    });
                } else {
                    createGroup();
                }
            }
        });
    }

    private void createGroup() {
        if (!groupAlreadyCreated) {
            wiFiP2PInstance.getWifiP2pManager().createGroup(wiFiP2PInstance.getChannel(), new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    Log.i(TAG, "Group created!");
                    groupAlreadyCreated = true;
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Error creating group. Reason: " + WiFiP2PError.fromReason(reason));
                }
            });
        }
    }

    private void onMessageReceived(MessageWrapper messageWrapper, InetAddress fromAddress) {
        if (messageWrapper.getMessageType().equals(MessageWrapper.MessageType.CONNECTION_MESSAGE)) {
            Gson gson = new Gson();

            String messageContentStr = messageWrapper.getMessage();
            RegistrationMessageContent registrationMessageContent = gson.fromJson(messageContentStr, RegistrationMessageContent.class);
            SendenDevice client = registrationMessageContent.getSendenDevice();
            client.setDeviceServerSocketIP(fromAddress.getHostAddress());
            clientsConnected.put(client.getDeviceMac(), client);

            Log.d(TAG, "New client registered:");
            Log.d(TAG, "\tDevice name: " + client.getDeviceName());
            Log.d(TAG, "\tDecive mac: " + client.getDeviceMac());
            Log.d(TAG, "\tDevice IP: " + client.getDeviceServerSocketIP());
            Log.d(TAG, "\tDevice ServerSocket port: " + client.getDeviceServerSocketPort());

            // Sending to all clients that new client is connected
            for (SendenDevice device : clientsConnected.values()) {
                if (!client.getDeviceMac().equals(device.getDeviceMac())) {
                    sendConnectionMessage(device, client);
                } else {
                    sendRegisteredDevicesMessage(device);
                }
            }

            if (clientConnectedListener != null) {
                clientConnectedListener.onClientConnected(client);
            }
        } else if (messageWrapper.getMessageType().equals(MessageWrapper.MessageType.DISCONNECTION_MESSAGE)) {
            Gson gson = new Gson();

            String messageContentStr = messageWrapper.getMessage();
            DisconnectionMessageContent disconnectionMessageContent = gson.fromJson(messageContentStr, DisconnectionMessageContent.class);
            SendenDevice client = disconnectionMessageContent.getSendenDevice();
            clientsConnected.remove(client.getDeviceMac());

            Log.d(TAG, "Client disconnected:");
            Log.d(TAG, "\tDevice name: " + client.getDeviceName());
            Log.d(TAG, "\tDecive mac: " + client.getDeviceMac());
            Log.d(TAG, "\tDevice IP: " + client.getDeviceServerSocketIP());
            Log.d(TAG, "\tDevice ServerSocket port: " + client.getDeviceServerSocketPort());

            // Sending to all clients that a client is disconnected now
            for (SendenDevice device : clientsConnected.values()) {
                if (!client.getDeviceMac().equals(device.getDeviceMac())) {
                    sendDisconnectionMessage(device, client);
                }
            }

            if (clientDisconnectedListener != null) {
                clientDisconnectedListener.onClientDisconnected(client);
            }
        } else {
            if (dataReceivedListener != null) {
                dataReceivedListener.onDataReceived(messageWrapper);
            }
        }
    }

    private void sendConnectionMessage(SendenDevice deviceToSend, SendenDevice deviceConnected) {
        RegistrationMessageContent content = new RegistrationMessageContent();
        content.setSendenDevice(deviceConnected);

        Gson gson = new Gson();

        MessageWrapper messageWrapper = new MessageWrapper();
        messageWrapper.setMessageType(MessageWrapper.MessageType.CONNECTION_MESSAGE);
        messageWrapper.setMessage(gson.toJson(content));

        sendMessage(deviceToSend, messageWrapper);
    }

    private void sendDisconnectionMessage(SendenDevice deviceToSend, SendenDevice deviceDisconnected) {
        DisconnectionMessageContent content = new DisconnectionMessageContent();
        content.setSendenDevice(deviceDisconnected);

        Gson gson = new Gson();

        MessageWrapper disconnectionMessage = new MessageWrapper();
        disconnectionMessage.setMessageType(MessageWrapper.MessageType.DISCONNECTION_MESSAGE);
        disconnectionMessage.setMessage(gson.toJson(content));

        sendMessage(deviceToSend, disconnectionMessage);
    }

    private void sendRegisteredDevicesMessage(SendenDevice deviceToSend) {
        List<SendenDevice> devicesConnected = new ArrayList<>();
        for (SendenDevice device : clientsConnected.values()) {
            if (!device.getDeviceMac().equals(deviceToSend.getDeviceMac())) {
                devicesConnected.add(device);
            }
        }

        RegisteredDevicesMessageContent content = new RegisteredDevicesMessageContent();
        content.setDevicesRegistered(devicesConnected);

        Gson gson = new Gson();

        MessageWrapper messageWrapper = new MessageWrapper();
        messageWrapper.setMessageType(MessageWrapper.MessageType.REGISTERED_DEVICES);
        messageWrapper.setMessage(gson.toJson(content));

        sendMessage(deviceToSend, messageWrapper);
    }

}