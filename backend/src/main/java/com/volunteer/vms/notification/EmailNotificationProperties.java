package com.volunteer.vms.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vms.external.email")
public record EmailNotificationProperties(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String from,
        String fromName,
        boolean auth,
        boolean sslEnabled,
        boolean startTlsEnabled,
        long connectionTimeoutMillis,
        long timeoutMillis,
        long writeTimeoutMillis
) {
    public int resolvedPort() {
        if (port > 0) {
            return port;
        }
        return sslEnabled ? 465 : 587;
    }

    public long resolvedConnectionTimeoutMillis() {
        return connectionTimeoutMillis > 0 ? connectionTimeoutMillis : 10000;
    }

    public long resolvedTimeoutMillis() {
        return timeoutMillis > 0 ? timeoutMillis : 10000;
    }

    public long resolvedWriteTimeoutMillis() {
        return writeTimeoutMillis > 0 ? writeTimeoutMillis : 10000;
    }
}
