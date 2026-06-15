package com.volunteer.vms.donation;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalDonationPaymentGateway implements DonationPaymentGateway {
    @Override
    public String createCallbackToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public boolean verifyCallback(DonationOrder order, String callbackToken) {
        return order != null
                && order.getCallbackToken() != null
                && callbackToken != null
                && order.getCallbackToken().equals(callbackToken.trim());
    }
}
