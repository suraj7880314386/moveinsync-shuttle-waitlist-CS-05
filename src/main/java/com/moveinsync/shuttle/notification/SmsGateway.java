package com.moveinsync.shuttle.notification;

public interface SmsGateway {
    void send(String phone, String message);
}
