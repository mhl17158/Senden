package com.example.senden_library.common.messages;


import com.example.senden_library.common.SendenDevice;

public class MessageWrapper {

    public enum MessageType {
        NORMAL, CONNECTION_MESSAGE, DISCONNECTION_MESSAGE, REGISTERED_DEVICES;
    }

    private String message;
    private MessageType messageType;
    private SendenDevice sendenDevice;


    public void setSendenDevice(SendenDevice sendenDevice) {
        this.sendenDevice = sendenDevice;
    }

    public SendenDevice getSendenDevice() {
        return sendenDevice;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public String toString() {
        return "MessageWrapper{" +
                "message='" + message + '\'' +
                ", messageType=" + messageType +
                ", wroupDevice=" + sendenDevice +
                '}';
    }

}
