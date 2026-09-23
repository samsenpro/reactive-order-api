package com.example.reactiveorderapi.notification;

/**
 * No se pudo entregar la notificación tras agotar los intentos (o por un error no reintentable).
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
