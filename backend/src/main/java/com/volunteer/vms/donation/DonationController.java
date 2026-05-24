package com.volunteer.vms.donation;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 接口层：实现 SRS FR-07 捐赠与支持管理。
 * v3 按接口文档 4.3 将捐赠流程拆分为“创建订单、模拟支付回调、成功后生成捐赠记录”。
 */
@RestController
@RequestMapping("/api/donations")
public class DonationController {
    private final DonationRepository donationRepository;
    private final DonationOrderRepository orderRepository;
    private final AuditLogService auditLogService;

    public DonationController(DonationRepository donationRepository,
                              DonationOrderRepository orderRepository,
                              AuditLogService auditLogService) {
        this.donationRepository = donationRepository;
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    @Transactional
    public ApiResponse<Void> donate(HttpServletRequest request,
                                    @Valid @RequestBody CreateDonationRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        DonationOrder order = new DonationOrder();
        order.setUserId(currentUser.getId());
        order.setDonorName(resolveDonorName(createRequest.donorName(), currentUser));
        order.setAmount(createRequest.amount());
        order.setMessage(createRequest.message());
        order.setStatus(DonationOrderStatus.PAID);
        order.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
        order.setPaymentNote("兼容接口提交，视为已完成支付");
        order.setPaidAt(LocalDateTime.now());
        DonationOrder savedOrder = orderRepository.save(order);
        Donation saved = createDonationFromOrder(savedOrder);
        auditLogService.log(
                request,
                currentUser,
                "DONATION_CREATED",
                "DONATION",
                saved.getId(),
                "提交捐赠，金额=" + createRequest.amount() + "，关联订单=" + savedOrder.getId()
        );
        return ApiResponse.success();
    }

    @PostMapping("/orders")
    public ApiResponse<DonationOrderResponse> createOrder(HttpServletRequest request,
                                                          @Valid @RequestBody CreateDonationRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        DonationOrder order = new DonationOrder();
        order.setUserId(currentUser.getId());
        order.setDonorName(resolveDonorName(createRequest.donorName(), currentUser));
        order.setAmount(createRequest.amount());
        order.setMessage(createRequest.message());
        order.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
        DonationOrder saved = orderRepository.save(order);
        auditLogService.log(
                request,
                currentUser,
                "DONATION_ORDER_CREATED",
                "DONATION_ORDER",
                saved.getId(),
                "创建捐赠订单，金额=" + createRequest.amount()
        );
        return ApiResponse.success(DonationOrderResponse.from(saved));
    }

    @PostMapping("/orders/{orderId}/simulate-payment")
    @Transactional
    public ApiResponse<DonationOrderResponse> simulatePayment(HttpServletRequest request,
                                                              @PathVariable Long orderId,
                                                              @Valid @RequestBody SimulatePaymentRequest paymentRequest) {
        User currentUser = AuthUtils.currentUser(request);
        DonationOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "捐赠订单不存在"));
        if (!order.getUserId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能处理自己的捐赠订单");
        }
        if (order.getStatus() != DonationOrderStatus.PENDING) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有待支付订单才能模拟支付");
        }
        if (paymentRequest.status() == DonationOrderStatus.PENDING || paymentRequest.status() == DonationOrderStatus.CLOSED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模拟支付状态只能为 PAID、FAILED 或 CANCELLED");
        }
        if (!order.getCallbackToken().equals(paymentRequest.callbackToken().trim())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "支付回调校验失败");
        }
        order.setStatus(paymentRequest.status());
        order.setPaymentNote(paymentRequest.note());
        if (paymentRequest.status() == DonationOrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
            createDonationFromOrder(order);
        }
        DonationOrder saved = orderRepository.save(order);
        auditLogService.log(
                request,
                currentUser,
                "DONATION_PAYMENT_SIMULATED",
                "DONATION_ORDER",
                orderId,
                "模拟支付状态=" + paymentRequest.status()
        );
        return ApiResponse.success(DonationOrderResponse.from(saved));
    }

    @GetMapping("/my")
    public ApiResponse<List<DonationResponse>> myDonations(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<DonationResponse> data = donationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(DonationResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @GetMapping("/orders/my")
    public ApiResponse<List<DonationOrderResponse>> myOrders(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        return ApiResponse.success(orderRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(DonationOrderResponse::from)
                .toList());
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> allDonations(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        List<DonationResponse> items = donationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(DonationResponse::from)
                .toList();
        return ApiResponse.success(Map.of(
                "totalAmount", donationRepository.totalAmount(),
                "items", items,
                "orders", orderRepository.findAllByOrderByCreatedAtDesc().stream()
                        .map(DonationOrderResponse::from)
                        .toList()
        ));
    }

    private String resolveDonorName(String donorName, User currentUser) {
        return donorName == null || donorName.isBlank() ? currentUser.getDisplayName() : donorName.trim();
    }

    private Donation createDonationFromOrder(DonationOrder order) {
        Donation donation = new Donation();
        donation.setOrderId(order.getId());
        donation.setUserId(order.getUserId());
        donation.setDonorName(order.getDonorName());
        donation.setAmount(order.getAmount());
        donation.setMessage(order.getMessage());
        return donationRepository.save(donation);
    }

    public record CreateDonationRequest(
            String donorName,
            @NotNull(message = "捐赠金额不能为空")
            @DecimalMin(value = "0.01", message = "捐赠金额必须大于0")
            BigDecimal amount,
            @Size(max = 500, message = "留言最多500字")
            String message
    ) {
    }

    public record SimulatePaymentRequest(
            @NotNull(message = "支付状态不能为空")
            DonationOrderStatus status,
            @NotBlank(message = "回调令牌不能为空")
            String callbackToken,
            @Size(max = 500, message = "支付说明最多500字")
            String note
    ) {
    }

    public record DonationOrderResponse(Long id,
                                        Long userId,
                                        String donorName,
                                        BigDecimal amount,
                                        String message,
                                        DonationOrderStatus status,
                                        String callbackToken,
                                        String paymentNote,
                                        LocalDateTime createdAt,
                                        LocalDateTime paidAt) {
        static DonationOrderResponse from(DonationOrder order) {
            return new DonationOrderResponse(
                    order.getId(),
                    order.getUserId(),
                    order.getDonorName(),
                    order.getAmount(),
                    order.getMessage(),
                    order.getStatus(),
                    order.getCallbackToken(),
                    order.getPaymentNote(),
                    order.getCreatedAt(),
                    order.getPaidAt()
            );
        }
    }

    public record DonationResponse(Long id,
                                   String donorName,
                                   Long userId,
                                   Long orderId,
                                   BigDecimal amount,
                                   String message,
                                   LocalDateTime createdAt) {
        static DonationResponse from(Donation donation) {
            return new DonationResponse(
                    donation.getId(),
                    donation.getDonorName(),
                    donation.getUserId(),
                    donation.getOrderId(),
                    donation.getAmount(),
                    donation.getMessage(),
                    donation.getCreatedAt()
            );
        }
    }
}
