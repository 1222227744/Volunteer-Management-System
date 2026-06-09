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
import com.volunteer.vms.file.FileStorageService;
import com.volunteer.vms.notification.NotificationService;
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

/**
 * 接口层：实现 SRS FR-04 服务记录沉淀，以及 FR-05 积分激励的基础规则。
 * 当前以“登记服务记录后发放积分”的课程版规则替代更复杂的评优算法。
 */
@RestController
@RequestMapping("/api/service-records")
public class ServiceRecordController {
    private final ServiceRecordRepository serviceRecordRepository;
    private final ServiceRecordCorrectionRepository correctionRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    public ServiceRecordController(ServiceRecordRepository serviceRecordRepository,
                                   ServiceRecordCorrectionRepository correctionRepository,
                                   ActivityRepository activityRepository,
                                   ActivityRegistrationRepository registrationRepository,
                                   UserRepository userRepository,
                                   NotificationService notificationService,
                                   AuditLogService auditLogService,
                                   FileStorageService fileStorageService) {
        this.serviceRecordRepository = serviceRecordRepository;
        this.correctionRepository = correctionRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
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
        if (registration.getStatus() != RegistrationStatus.CHECKED_OUT || registration.getCheckOutAt() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该用户尚未完成签退，不能登记服务记录");
        }
        if (serviceRecordRepository.existsByActivityIdAndUserId(createRequest.activityId(), createRequest.userId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该活动的服务记录已登记，请勿重复提交");
        }
        ServiceRecord record = new ServiceRecord();
        record.setActivityId(createRequest.activityId());
        record.setUserId(createRequest.userId());
        record.setHours(createRequest.hours().setScale(2, RoundingMode.HALF_UP));
        record.setAchievement(createRequest.achievement());
        record.setEvidenceUrl(createRequest.evidenceUrl());
        record.setEvidenceFileId(createRequest.evidenceFileId());
        ServiceRecord savedRecord = serviceRecordRepository.save(record);
        bindFileIfPresent(savedRecord.getEvidenceFileId(), "SERVICE_RECORD", savedRecord.getId());

        registration.setStatus(RegistrationStatus.COMPLETED);
        registrationRepository.save(registration);

        int gainedPoints = calculatePoints(createRequest.hours());
        targetUser.setPoints(targetUser.getPoints() + gainedPoints);
        userRepository.save(targetUser);
        notificationService.notifyUser(
                targetUser.getId(),
                "服务记录已登记",
                "活动《" + activity.getTitle() + "》的服务记录已登记，新增 " + createRequest.hours() + " 小时，积分 +" + gainedPoints + "。"
        );
        auditLogService.log(
                request,
                currentUser,
                "SERVICE_RECORD_CREATED",
                "SERVICE_RECORD",
                savedRecord.getId(),
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
    public ApiResponse<Map<String, Object>> userRecords(HttpServletRequest request,
                                                        @PathVariable Long userId,
                                                        @RequestParam(required = false) Long activityId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        return buildUserRecordsResponse(currentUser, userId, activityId);
    }

    @PostMapping("/{recordId}/corrections")
    @Transactional
    public ApiResponse<ServiceRecordCorrectionResponse> createCorrection(HttpServletRequest request,
                                                                         @PathVariable Long recordId,
                                                                         @Valid @RequestBody CreateServiceRecordCorrectionRequest correctionRequest) {
        User currentUser = AuthUtils.currentUser(request);
        ServiceRecord record = serviceRecordRepository.findById(recordId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "服务记录不存在"));
        if (!currentUser.getId().equals(record.getUserId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能为自己的服务记录提交更正申请");
        }
        if (correctionRepository.existsByServiceRecordIdAndStatus(recordId, ServiceRecordCorrectionStatus.PENDING)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该服务记录已有待审核更正申请，请勿重复提交");
        }
        Activity activity = activityRepository.findById(record.getActivityId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));

        ServiceRecordCorrection correction = new ServiceRecordCorrection();
        correction.setServiceRecordId(record.getId());
        correction.setActivityId(record.getActivityId());
        correction.setUserId(record.getUserId());
        correction.setRequesterId(currentUser.getId());
        correction.setRequesterName(currentUser.getDisplayName());
        correction.setStatus(ServiceRecordCorrectionStatus.PENDING);
        correction.setOldHours(record.getHours());
        correction.setNewHours(correctionRequest.hours().setScale(2, RoundingMode.HALF_UP));
        correction.setOldAchievement(record.getAchievement());
        correction.setNewAchievement(correctionRequest.achievement());
        correction.setOldEvidenceUrl(record.getEvidenceUrl());
        correction.setNewEvidenceUrl(correctionRequest.evidenceUrl());
        correction.setOldEvidenceFileId(record.getEvidenceFileId());
        correction.setNewEvidenceFileId(correctionRequest.evidenceFileId());
        correction.setReason(correctionRequest.reason());
        ServiceRecordCorrection saved = correctionRepository.save(correction);
        bindFileIfPresent(saved.getNewEvidenceFileId(), "SERVICE_RECORD_CORRECTION", saved.getId());

        notificationService.notifyUser(
                activity.getOrganizerId(),
                "收到服务记录更正申请",
                currentUser.getDisplayName() + " 对活动《" + activity.getTitle() + "》的服务记录提交了更正申请，请及时处理。"
        );
        auditLogService.log(
                request,
                currentUser,
                "SERVICE_RECORD_CORRECTION_REQUESTED",
                "SERVICE_RECORD_CORRECTION",
                saved.getId(),
                "服务记录ID=" + recordId + ", 原时长=" + record.getHours() + ", 申请时长=" + saved.getNewHours()
        );
        return ApiResponse.success(ServiceRecordCorrectionResponse.from(saved, activity));
    }

    @GetMapping("/corrections/my")
    public ApiResponse<List<ServiceRecordCorrectionResponse>> myCorrections(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        return ApiResponse.success(buildCorrectionResponses(
                correctionRepository.findByUserIdOrderByRequestedAtDesc(currentUser.getId())
        ));
    }

    @GetMapping("/corrections")
    public ApiResponse<List<ServiceRecordCorrectionResponse>> corrections(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        List<ServiceRecordCorrection> corrections;
        if (currentUser.getRole() == Role.ADMIN) {
            corrections = correctionRepository.findAllByOrderByRequestedAtDesc();
        } else {
            List<Long> activityIds = activityRepository.findIdsByOrganizerId(currentUser.getId());
            corrections = activityIds.isEmpty()
                    ? List.of()
                    : correctionRepository.findByActivityIdInOrderByRequestedAtDesc(activityIds);
        }
        return ApiResponse.success(buildCorrectionResponses(corrections));
    }

    @PatchMapping("/corrections/{correctionId}/review")
    @Transactional
    public ApiResponse<ServiceRecordCorrectionResponse> reviewCorrection(HttpServletRequest request,
                                                                         @PathVariable Long correctionId,
                                                                         @Valid @RequestBody ReviewServiceRecordCorrectionRequest reviewRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        if (reviewRequest.status() != ServiceRecordCorrectionStatus.APPROVED
                && reviewRequest.status() != ServiceRecordCorrectionStatus.REJECTED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务记录更正审核只允许设置为 APPROVED 或 REJECTED");
        }
        ServiceRecordCorrection correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "更正申请不存在"));
        if (correction.getStatus() != ServiceRecordCorrectionStatus.PENDING) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该更正申请已处理，不能重复审核");
        }
        Activity activity = activityRepository.findById(correction.getActivityId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);

        if (reviewRequest.status() == ServiceRecordCorrectionStatus.APPROVED) {
            applyCorrection(correction);
        }
        correction.setStatus(reviewRequest.status());
        correction.setReviewComment(reviewRequest.comment());
        correction.setReviewedBy(currentUser.getId());
        correction.setReviewedByName(currentUser.getDisplayName());
        correction.setReviewedAt(LocalDateTime.now());
        ServiceRecordCorrection saved = correctionRepository.save(correction);

        notificationService.notifyUser(
                correction.getUserId(),
                "服务记录更正审核完成",
                buildCorrectionReviewMessage(activity, saved)
        );
        auditLogService.log(
                request,
                currentUser,
                "SERVICE_RECORD_CORRECTION_REVIEWED",
                "SERVICE_RECORD_CORRECTION",
                saved.getId(),
                "服务记录ID=" + saved.getServiceRecordId() + ", 审核结果=" + saved.getStatus()
                        + ", 说明=" + nullToDash(saved.getReviewComment())
        );
        return ApiResponse.success(ServiceRecordCorrectionResponse.from(saved, activity));
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

    private ApiResponse<Map<String, Object>> buildUserRecordsResponse(User currentUser, Long userId, Long activityId) {
        if (currentUser.getRole() == Role.ADMIN) {
            if (activityId == null) {
                return buildUserRecordsResponse(userId);
            }
            return buildScopedUserRecordsResponse(userId, activityId);
        }
        if (activityId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "组织方查询志愿者服务记录时必须指定活动");
        }
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (!currentUser.getId().equals(activity.getOrganizerId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能查看自己活动范围内的服务记录");
        }
        return buildScopedUserRecordsResponse(userId, activityId);
    }

    private ApiResponse<Map<String, Object>> buildScopedUserRecordsResponse(Long userId, Long activityId) {
        List<ServiceRecord> records = serviceRecordRepository.findByUserIdAndActivityIdOrderByCreatedAtDesc(userId, activityId);
        Activity activity = activityRepository.findById(activityId).orElse(null);
        BigDecimal totalHours = serviceRecordRepository.sumHoursByUserIdAndActivityId(userId, activityId);
        List<ServiceRecordResponse> data = records.stream()
                .map(record -> ServiceRecordResponse.from(record, activity))
                .toList();
        return ApiResponse.success(Map.of(
                "totalHours", totalHours,
                "records", data
        ));
    }

    private void applyCorrection(ServiceRecordCorrection correction) {
        ServiceRecord record = serviceRecordRepository.findById(correction.getServiceRecordId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "服务记录不存在"));
        User targetUser = userRepository.findById(correction.getUserId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "志愿者不存在"));
        int oldPoints = calculatePoints(record.getHours());
        int newPoints = calculatePoints(correction.getNewHours());

        record.setHours(correction.getNewHours());
        record.setAchievement(correction.getNewAchievement());
        record.setEvidenceUrl(correction.getNewEvidenceUrl());
        record.setEvidenceFileId(correction.getNewEvidenceFileId());
        ServiceRecord savedRecord = serviceRecordRepository.save(record);
        bindFileIfPresent(savedRecord.getEvidenceFileId(), "SERVICE_RECORD", savedRecord.getId());

        int adjustedPoints = targetUser.getPoints() + newPoints - oldPoints;
        targetUser.setPoints(Math.max(0, adjustedPoints));
        userRepository.save(targetUser);
    }

    private List<ServiceRecordCorrectionResponse> buildCorrectionResponses(List<ServiceRecordCorrection> corrections) {
        Map<Long, Activity> activityMap = activityRepository.findAllById(
                corrections.stream().map(ServiceRecordCorrection::getActivityId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Activity::getId, item -> item));
        return corrections.stream()
                .map(correction -> ServiceRecordCorrectionResponse.from(correction, activityMap.get(correction.getActivityId())))
                .toList();
    }

    private int calculatePoints(BigDecimal hours) {
        return hours.multiply(BigDecimal.TEN).intValue();
    }

    private void ensureCanManageActivity(User currentUser, Activity activity) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (!currentUser.getId().equals(activity.getOrganizerId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能处理自己活动范围内的服务记录");
        }
    }

    private String buildCorrectionReviewMessage(Activity activity, ServiceRecordCorrection correction) {
        String action = correction.getStatus() == ServiceRecordCorrectionStatus.APPROVED ? "已通过" : "已驳回";
        String message = "你在活动《" + activity.getTitle() + "》中的服务记录更正申请" + action + "。";
        if (correction.getReviewComment() == null || correction.getReviewComment().isBlank()) {
            return message;
        }
        return message + "处理说明：" + correction.getReviewComment();
    }

    private void bindFileIfPresent(Long fileId, String businessType, Long businessId) {
        if (fileId != null && fileStorageService != null) {
            fileStorageService.bindBusiness(fileId, businessType, businessId);
        }
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
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
            String evidenceUrl,
            Long evidenceFileId
    ) {
    }

    public record CreateServiceRecordCorrectionRequest(
            @NotNull(message = "服务时长不能为空")
            @DecimalMin(value = "0.5", message = "服务时长至少0.5小时")
            @DecimalMax(value = "24.0", message = "服务时长不能超过24小时")
            BigDecimal hours,
            @NotBlank(message = "服务成果不能为空")
            @Size(max = 1000, message = "服务成果最多1000字")
            String achievement,
            @Size(max = 500, message = "证明链接最多500字")
            String evidenceUrl,
            Long evidenceFileId,
            @NotBlank(message = "更正原因不能为空")
            @Size(max = 500, message = "更正原因最多500字")
            String reason
    ) {
    }

    public record ReviewServiceRecordCorrectionRequest(
            @NotNull(message = "审核状态不能为空")
            ServiceRecordCorrectionStatus status,
            @Size(max = 500, message = "审核说明最多500字")
            String comment
    ) {
    }

    public record ServiceRecordResponse(Long id,
                                        Long userId,
                                        Long activityId,
                                        String activityTitle,
                                        BigDecimal hours,
                                        String achievement,
                                        String evidenceUrl,
                                        Long evidenceFileId,
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
                    record.getEvidenceFileId(),
                    record.getCreatedAt()
            );
        }
    }

    public record ServiceRecordCorrectionResponse(Long id,
                                                  Long serviceRecordId,
                                                  Long activityId,
                                                  String activityTitle,
                                                  Long userId,
                                                  Long requesterId,
                                                  String requesterName,
                                                  ServiceRecordCorrectionStatus status,
                                                  BigDecimal oldHours,
                                                  BigDecimal newHours,
                                                  String oldAchievement,
                                                  String newAchievement,
                                                  String oldEvidenceUrl,
                                                  String newEvidenceUrl,
                                                  Long oldEvidenceFileId,
                                                  Long newEvidenceFileId,
                                                  String reason,
                                                  String reviewComment,
                                                  Long reviewedBy,
                                                  String reviewedByName,
                                                  LocalDateTime requestedAt,
                                                  LocalDateTime reviewedAt) {
        static ServiceRecordCorrectionResponse from(ServiceRecordCorrection correction, Activity activity) {
            return new ServiceRecordCorrectionResponse(
                    correction.getId(),
                    correction.getServiceRecordId(),
                    correction.getActivityId(),
                    activity == null ? "未知活动" : activity.getTitle(),
                    correction.getUserId(),
                    correction.getRequesterId(),
                    correction.getRequesterName(),
                    correction.getStatus(),
                    correction.getOldHours(),
                    correction.getNewHours(),
                    correction.getOldAchievement(),
                    correction.getNewAchievement(),
                    correction.getOldEvidenceUrl(),
                    correction.getNewEvidenceUrl(),
                    correction.getOldEvidenceFileId(),
                    correction.getNewEvidenceFileId(),
                    correction.getReason(),
                    correction.getReviewComment(),
                    correction.getReviewedBy(),
                    correction.getReviewedByName(),
                    correction.getRequestedAt(),
                    correction.getReviewedAt()
            );
        }
    }
}
