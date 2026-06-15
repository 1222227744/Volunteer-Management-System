package com.volunteer.vms.user;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 接口层：实现 SRS FR-01 权限管理与 FR-05 激励排行展示。
 * 其中排行基于累计积分，属于“评价与激励”简化实现。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public UserController(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/ranking")
    public ApiResponse<List<UserRankingResponse>> ranking() {
        List<UserRankingResponse> rankings = userRepository.findTop20ByOrderByPointsDescCreatedAtAsc().stream()
                .filter(user -> user.getRole() == Role.VOLUNTEER)
                .map(user -> new UserRankingResponse(user.getId(), user.getDisplayName(), user.getPoints()))
                .limit(20)
                .toList();
        return ApiResponse.success(rankings);
    }

    @GetMapping
    public ApiResponse<List<UserSummaryResponse>> allUsers(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        List<UserSummaryResponse> users = userRepository.findAll().stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ApiResponse.success(users);
    }

    @PatchMapping("/me/profile")
    public ApiResponse<UserSummaryResponse> updateMyProfile(HttpServletRequest request,
                                                            @Valid @RequestBody UpdateProfileRequest profileRequest) {
        User currentUser = AuthUtils.currentUser(request);
        currentUser.setDisplayName(profileRequest.displayName());
        currentUser.setPhone(normalizeNullable(profileRequest.phone()));
        currentUser.setServiceIntention(normalizeNullable(profileRequest.serviceIntention()));
        User saved = userRepository.save(currentUser);
        auditLogService.log(
                request,
                currentUser,
                "USER_PROFILE_UPDATED",
                "USER",
                saved.getId(),
                "用户更新个人资料"
        );
        return ApiResponse.success(UserSummaryResponse.from(saved));
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<Void> updateUserRole(HttpServletRequest request,
                                            @PathVariable Long userId,
                                            @Valid @RequestBody UpdateRoleRequest roleRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        Role oldRole = user.getRole();
        user.setRole(roleRequest.role());
        userRepository.save(user);
        auditLogService.log(
                request,
                currentUser,
                "USER_ROLE_UPDATED",
                "USER",
                userId,
                "角色从 " + oldRole + " 变更为 " + roleRequest.role()
        );
        return ApiResponse.success();
    }

    @PatchMapping("/{userId}/account-status")
    public ApiResponse<Void> updateAccountStatus(HttpServletRequest request,
                                                 @PathVariable Long userId,
                                                 @Valid @RequestBody UpdateAccountStatusRequest statusRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        if (currentUser.getId().equals(userId) && statusRequest.accountStatus() != AccountStatus.ENABLED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "不能停用或锁定当前登录管理员账号");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        AccountStatus oldStatus = user.getAccountStatus();
        user.setAccountStatus(statusRequest.accountStatus());
        userRepository.save(user);
        auditLogService.log(
                request,
                currentUser,
                "USER_ACCOUNT_STATUS_UPDATED",
                "USER",
                userId,
                "账号状态从 " + oldStatus + " 变更为 " + statusRequest.accountStatus()
        );
        return ApiResponse.success();
    }

    @PatchMapping("/{userId}/verification")
    public ApiResponse<Void> updateVerification(HttpServletRequest request,
                                                @PathVariable Long userId,
                                                @Valid @RequestBody UpdateVerificationRequest verificationRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        VerificationStatus oldStatus = user.getVerificationStatus();
        user.setVerificationStatus(verificationRequest.verificationStatus());
        user.setVerificationComment(normalizeNullable(verificationRequest.comment()));
        userRepository.save(user);
        auditLogService.log(
                request,
                currentUser,
                "USER_VERIFICATION_UPDATED",
                "USER",
                userId,
                "实名状态从 " + oldStatus + " 变更为 " + verificationRequest.verificationStatus()
        );
        return ApiResponse.success();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UserRankingResponse(Long userId, String displayName, Integer points) {
    }

    public record UserSummaryResponse(Long userId,
                                      String username,
                                      String displayName,
                                      Role role,
                                      Integer points,
                                      String phone,
                                      String serviceIntention,
                                      AccountStatus accountStatus,
                                      VerificationStatus verificationStatus,
                                      String verificationComment) {
        static UserSummaryResponse from(User user) {
            return new UserSummaryResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getPoints(),
                    user.getPhone(),
                    user.getServiceIntention(),
                    user.getAccountStatus(),
                    user.getVerificationStatus(),
                    user.getVerificationComment()
            );
        }
    }

    public record UpdateRoleRequest(@NotNull(message = "角色不能为空") Role role) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "昵称不能为空")
            @Size(max = 50, message = "昵称最多50字")
            String displayName,
            @Size(max = 30, message = "联系电话最多30字")
            String phone,
            @Size(max = 500, message = "服务意向最多500字")
            String serviceIntention
    ) {
    }

    public record UpdateAccountStatusRequest(@NotNull(message = "账号状态不能为空") AccountStatus accountStatus) {
    }

    public record UpdateVerificationRequest(
            @NotNull(message = "实名状态不能为空")
            VerificationStatus verificationStatus,
            @Size(max = 500, message = "审核说明最多500字")
            String comment
    ) {
    }
}
