package com.example.reactiveorderapi.support;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de notificaciones falso para los tests de integración. Por defecto responde 202;
 * los tests pueden cambiar el código de respuesta para simular caídas.
 */
public final class NotificationServerStub {

    private final MockWebServer server = new MockWebServer();
    private volatile int responseCode = 202;

    public NotificationServerStub() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(responseCode);
            }
        });
        try {
            server.start();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String baseUrl() {
        String url = server.url("/").toString();
        return url.substring(0, url.length() - 1);
    }

    public void respondWith(int code) {
        this.responseCode = code;
    }

    public void reset() {
        responseCode = 202;
        RecordedRequest pending;
        do {
            pending = poll(0);
        } while (pending != null);
    }

    public RecordedRequest poll(long timeoutMillis) {
        try {
            return server.takeRequest(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
