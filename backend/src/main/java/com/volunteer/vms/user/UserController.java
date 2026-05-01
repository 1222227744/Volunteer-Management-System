package com.volunteer.vms.user;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 接口层：实现 SRS FR-01 权限管理与 FR-05 激励排行展示。
 * 其中排行基于累计积分，属于课程版“评价与激励”简化实现。
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
                .map(user -> new UserSummaryResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getPoints()))
                .toList();
        return ApiResponse.success(users);
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

    public record UserRankingResponse(Long userId, String displayName, Integer points) {
    }

    public record UserSummaryResponse(Long userId, String username, String displayName, Role role, Integer points) {
    }

    public record UpdateRoleRequest(@NotNull(message = "角色不能为空") Role role) {
    }
}
