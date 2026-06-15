package com.volunteer.vms.donation;

/**
 * 支付适配端口：当前默认实现负责本地生成和校验支付确认令牌。
 * 后续接入真实支付平台时，应替换该接口实现而不是改写捐赠业务流程。
 */
public interface DonationPaymentGateway {
    String createCallbackToken();

    boolean verifyCallback(DonationOrder order, String callbackToken);
}
