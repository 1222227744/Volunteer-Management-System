package com.volunteer.vms.donation;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationControllerTest {
    @Mock
    private DonationRepository donationRepository;

    @Mock
    private DonationOrderRepository orderRepository;

    private RecordingAuditLogService auditLogService;
    private DonationController controller;
    private User donor;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        auditLogService = new RecordingAuditLogService();
        controller = new DonationController(
                donationRepository,
                orderRepository,
                auditLogService,
                new FixedDonationPaymentGateway()
        );
        donor = new User();
        donor.setId(5L);
        donor.setRole(Role.VOLUNTEER);
        donor.setUsername("donor");
        donor.setDisplayName("捐赠人");
        donor.setPoints(0);
        request = new MockHttpServletRequest();
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, donor);
    }

    @Test
    void simulatePaymentShouldRejectInvalidCallbackToken() {
        DonationOrder order = pendingOrder();
        order.setCallbackToken("token-ok");
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> controller.simulatePayment(
                request,
                9L,
                new DonationController.SimulatePaymentRequest(DonationOrderStatus.PAID, "bad-token", "伪造回调")
        ))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bizException.getMessage()).isEqualTo("支付回调校验失败");
                });

        verify(donationRepository, never()).save(any(Donation.class));
        verify(orderRepository, never()).save(any(DonationOrder.class));
        assertThat(auditLogService.callCount).isZero();
    }

    @Test
    void simulatePaymentShouldCreateDonationWhenPaid() {
        DonationOrder order = pendingOrder();
        order.setCallbackToken("token-ok");
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(DonationOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.simulatePayment(
                request,
                9L,
                new DonationController.SimulatePaymentRequest(DonationOrderStatus.PAID, "token-ok", "演示支付成功")
        );

        assertThat(order.getStatus()).isEqualTo(DonationOrderStatus.PAID);
        assertThat(order.getPaymentNote()).isEqualTo("演示支付成功");
        assertThat(order.getPaidAt()).isNotNull();

        ArgumentCaptor<Donation> donationCaptor = ArgumentCaptor.forClass(Donation.class);
        verify(donationRepository).save(donationCaptor.capture());
        Donation donation = donationCaptor.getValue();
        assertThat(donation.getUserId()).isEqualTo(5L);
        assertThat(donation.getDonorName()).isEqualTo("捐赠人");
        assertThat(donation.getAmount()).isEqualByComparingTo("66.00");
        assertThat(donation.getMessage()).isEqualTo("支持志愿服务");

        assertThat(auditLogService.callCount).isEqualTo(1);
        assertThat(auditLogService.lastCall.action()).isEqualTo("DONATION_PAYMENT_SIMULATED");
        assertThat(auditLogService.lastCall.targetType()).isEqualTo("DONATION_ORDER");
    }

    private DonationOrder pendingOrder() {
        DonationOrder order = new DonationOrder();
        order.setUserId(5L);
        order.setDonorName("捐赠人");
        order.setAmount(new BigDecimal("66.00"));
        order.setMessage("支持志愿服务");
        order.setStatus(DonationOrderStatus.PENDING);
        return order;
    }

    private static class FixedDonationPaymentGateway implements DonationPaymentGateway {
        @Override
        public String createCallbackToken() {
            return "token-ok";
        }

        @Override
        public boolean verifyCallback(DonationOrder order, String callbackToken) {
            return order != null && "token-ok".equals(callbackToken == null ? null : callbackToken.trim());
        }

        @Override
        public String gatewayName() {
            return "TEST";
        }
    }

    private static class RecordingAuditLogService extends AuditLogService {
        private int callCount;
        private AuditCall lastCall;

        RecordingAuditLogService() {
            super(null);
        }

        @Override
        public void log(HttpServletRequest request,
                        User operator,
                        String action,
                        String targetType,
                        Object targetId,
                        String detail) {
            callCount++;
            lastCall = new AuditCall(request, operator, action, targetType, targetId, detail);
        }
    }

    private record AuditCall(HttpServletRequest request,
                             User operator,
                             String action,
                             String targetType,
                             Object targetId,
                             String detail) {
    }
}
