package com.volunteer.vms.service;

import com.volunteer.vms.activity.Activity;
import com.volunteer.vms.activity.ActivityRegistration;
import com.volunteer.vms.activity.ActivityRegistrationRepository;
import com.volunteer.vms.activity.ActivityRepository;
import com.volunteer.vms.activity.RegistrationStatus;
import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/service-records")
public class ServiceRecordController {
    private final ServiceRecordRepository serviceRecordRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ServiceRecordController(ServiceRecordRepository serviceRecordRepository,
                                   ActivityRepository activityRepository,
                                   ActivityRegistrationRepository registrationRepository,
                                   UserRepository userRepository,
                                   AuditLogService auditLogService) {
        this.serviceRecordRepository = serviceRecordRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    @Transactional
    public ApiResponse<Void> createRecord(HttpServletRequest request,
                                          @Valid @RequestBody CreateServiceRecordRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(createRequest.activityId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (currentUser.getRole() == Role.ORGANIZER && !currentUser.getId().equals(activity.getOrganizerId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能登记自己活动的服务记录");
        }
        User targetUser = userRepository.findById(createRequest.userId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "志愿者不存在"));
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(createRequest.activityId(), createRequest.userId())
                .orElseThrow(() -> new BizException(HttpStatus.BAD_REQUEST, "该用户未报名活动，不能登记服务记录"));
        ServiceRecord record = new ServiceRecord();
        record.setActivityId(createRequest.activityId());
        record.setUserId(createRequest.userId());
        record.setHours(createRequest.hours().setScale(2, RoundingMode.HALF_UP));
        record.setAchievement(createRequest.achievement());
        record.setEvidenceUrl(createRequest.evidenceUrl());
        serviceRecordRepository.save(record);

        registration.setStatus(RegistrationStatus.COMPLETED);
        registrationRepository.save(registration);

        int gainedPoints = createRequest.hours().multiply(BigDecimal.TEN).intValue();
        targetUser.setPoints(targetUser.getPoints() + gainedPoints);
        userRepository.save(targetUser);
        auditLogService.log(
                request,
                currentUser,
                "SERVICE_RECORD_CREATED",
                "SERVICE_RECORD",
                record.getId(),
                "活动ID=" + createRequest.activityId() + ", 志愿者ID=" + createRequest.userId() + ", 时长=" + createRequest.hours()
        );
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> myRecords(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        return buildUserRecordsResponse(currentUser.getId());
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<Map<String, Object>> userRecords(HttpServletRequest request, @PathVariable Long userId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        return buildUserRecordsResponse(userId);
    }

    private ApiResponse<Map<String, Object>> buildUserRecordsResponse(Long userId) {
        List<ServiceRecord> records = serviceRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Activity> activityMap = activityRepository.findAllById(
                records.stream().map(ServiceRecord::getActivityId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Activity::getId, item -> item));
        BigDecimal totalHours = serviceRecordRepository.sumHoursByUserId(userId);
        List<ServiceRecordResponse> data = records.stream()
                .map(record -> ServiceRecordResponse.from(record, activityMap.get(record.getActivityId())))
                .toList();
        return ApiResponse.success(Map.of(
                "totalHours", totalHours,
                "records", data
        ));
    }

    public record CreateServiceRecordRequest(
            @NotNull(message = "用户ID不能为空")
            Long userId,
            @NotNull(message = "活动ID不能为空")
            Long activityId,
            @NotNull(message = "服务时长不能为空")
            @DecimalMin(value = "0.5", message = "服务时长至少0.5小时")
            @DecimalMax(value = "24.0", message = "服务时长不能超过24小时")
            BigDecimal hours,
            @NotBlank(message = "服务成果不能为空")
            @Size(max = 1000, message = "服务成果最多1000字")
            String achievement,
            @Size(max = 500, message = "证明链接最多500字")
            String evidenceUrl
    ) {
    }

    public record ServiceRecordResponse(Long id,
                                        Long userId,
                                        Long activityId,
                                        String activityTitle,
                                        BigDecimal hours,
                                        String achievement,
                                        String evidenceUrl,
                                        LocalDateTime createdAt) {
        static ServiceRecordResponse from(ServiceRecord record, Activity activity) {
            return new ServiceRecordResponse(
                    record.getId(),
                    record.getUserId(),
                    record.getActivityId(),
                    activity == null ? "未知活动" : activity.getTitle(),
                    record.getHours(),
                    record.getAchievement(),
                    record.getEvidenceUrl(),
                    record.getCreatedAt()
            );
        }
    }
}
