package com.volunteer.vms.donation;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接口层：实现 SRS FR-07 捐赠与支持管理。
 * 支付结果回调在课程版本中以直接写入捐赠记录的方式模拟。
 */
@RestController
@RequestMapping("/api/donations")
public class DonationController {
    private final DonationRepository donationRepository;
    private final AuditLogService auditLogService;

    public DonationController(DonationRepository donationRepository,
                              AuditLogService auditLogService) {
        this.donationRepository = donationRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public ApiResponse<Void> donate(HttpServletRequest request,
                                    @Valid @RequestBody CreateDonationRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        Donation donation = new Donation();
        donation.setUserId(currentUser.getId());
        donation.setDonorName(
                createRequest.donorName() == null || createRequest.donorName().isBlank()
                        ? currentUser.getDisplayName()
                        : createRequest.donorName().trim()
        );
        donation.setAmount(createRequest.amount());
        donation.setMessage(createRequest.message());
        Donation saved = donationRepository.save(donation);
        auditLogService.log(
                request,
                currentUser,
                "DONATION_CREATED",
                "DONATION",
                saved.getId(),
                "提交捐赠，金额=" + createRequest.amount()
        );
        return ApiResponse.success();
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
                "items", items
        ));
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

    public record DonationResponse(Long id,
                                   String donorName,
                                   Long userId,
                                   BigDecimal amount,
                                   String message,
                                   LocalDateTime createdAt) {
        static DonationResponse from(Donation donation) {
            return new DonationResponse(
                    donation.getId(),
                    donation.getDonorName(),
                    donation.getUserId(),
                    donation.getAmount(),
                    donation.getMessage(),
                    donation.getCreatedAt()
            );
        }
    }
}
