package com.volunteer.vms.notification;

public record ExternalNotificationDeliveryResult(boolean delivered, String errorMessage) {
    public static ExternalNotificationDeliveryResult success() {
        return new ExternalNotificationDeliveryResult(true, null);
    }

    public static ExternalNotificationDeliveryResult failed(String errorMessage) {
        return new ExternalNotificationDeliveryResult(false, errorMessage);
    }
}
