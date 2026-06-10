package com.volunteer.vms.activity;

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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 接口层：覆盖 SRS 中的 FR-02 志愿活动发布与查询、FR-03 活动报名与审核、
 * FR-04 签到签退流程。
 * 当前控制器仍承担了部分流程编排，和《系统设计说明书》里的说明一致，
 * 后续可继续将流程下沉到应用层服务。
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private static final Set<RegistrationStatus> OCCUPIED_STATUSES = EnumSet.of(
            RegistrationStatus.APPROVED,
            RegistrationStatus.CHECKED_IN,
            RegistrationStatus.CHECKED_OUT,
            RegistrationStatus.COMPLETED
    );
    private static final Set<RegistrationStatus> CANCELLABLE_REGISTRATION_STATUSES = EnumSet.of(
            RegistrationStatus.PENDING,
            RegistrationStatus.APPROVED,
            RegistrationStatus.CHECKED_IN
    );

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final ActivityAttendanceCorrectionRepository attendanceCorrectionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    public ActivityController(ActivityRepository activityRepository,
                              ActivityRegistrationRepository registrationRepository,
                              ActivityAttendanceCorrectionRepository attendanceCorrectionRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              AuditLogService auditLogService,
                              FileStorageService fileStorageService) {
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceCorrectionRepository = attendanceCorrectionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public ApiResponse<List<ActivityResponse>> listActivities(@RequestParam(required = false) ActivityStatus status,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String location,
                                                              @RequestParam(required = false)
                                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                              LocalDateTime startFrom,
                                                              @RequestParam(required = false)
                                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                              LocalDateTime startTo,
                                                              HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<Activity> activities = hasActivityFilters(status, keyword, location, startFrom, startTo)
                ? activityRepository.search(status, normalizeFilter(keyword), normalizeFilter(location), startFrom, startTo)
                : activityRepository.findAllByOrderByStartTimeDesc();
        List<ActivityResponse> response = activities.stream().map(activity -> {
            long registeredCount = registrationRepository.countByActivityIdAndStatusIn(activity.getId(), OCCUPIED_STATUSES);
            return ActivityResponse.from(activity, registeredCount, canViewCheckCode(currentUser, activity));
        }).toList();
        return ApiResponse.success(response);
    }

    @PostMapping
    public ApiResponse<ActivityResponse> createActivity(HttpServletRequest request,
                                                        @Valid @RequestBody CreateActivityRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        if (!createRequest.endTime().isAfter(createRequest.startTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "活动结束时间必须晚于开始时间");
        }
        if (createRequest.registrationDeadline() != null
                && createRequest.registrationDeadline().isAfter(createRequest.startTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "报名截止时间不能晚于活动开始时间");
        }
        Activity activity = new Activity();
        activity.setTitle(createRequest.title());
        activity.setDescription(createRequest.description());
        activity.setLocation(createRequest.location());
        activity.setStartTime(createRequest.startTime());
        activity.setEndTime(createRequest.endTime());
        activity.setRegistrationDeadline(createRequest.registrationDeadline());
        activity.setParticipationRequirement(createRequest.participationRequirement());
        activity.setAttachmentFileId(createRequest.attachmentFileId());
        activity.setMaxParticipants(createRequest.maxParticipants());
        activity.setStatus(createRequest.status() == null ? ActivityStatus.PUBLISHED : createRequest.status());
        activity.setCheckCode(generateCheckCode());
        activity.setOrganizerId(currentUser.getId());
        Activity saved = activityRepository.save(activity);
        bindFileIfPresent(saved.getAttachmentFileId(), "ACTIVITY", saved.getId());
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_CREATED",
                "ACTIVITY",
                saved.getId(),
                "创建活动: " + saved.getTitle()
        );
        return ApiResponse.success(ActivityResponse.from(saved, 0L, true));
    }

    @PutMapping("/{activityId}")
    public ApiResponse<ActivityResponse> updateActivity(HttpServletRequest request,
                                                        @PathVariable Long activityId,
                                                        @Valid @RequestBody UpdateActivityRequest updateRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        if (!updateRequest.endTime().isAfter(updateRequest.startTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "活动结束时间必须晚于开始时间");
        }
        if (updateRequest.registrationDeadline() != null
                && updateRequest.registrationDeadline().isAfter(updateRequest.startTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "报名截止时间不能晚于活动开始时间");
        }
        long occupied = registrationRepository.countByActivityIdAndStatusIn(activityId, OCCUPIED_STATUSES);
        if (updateRequest.maxParticipants() < occupied) {
            throw new BizException(HttpStatus.BAD_REQUEST, "人数上限不能小于当前已占用名额");
        }

        activity.setTitle(updateRequest.title());
        activity.setDescription(updateRequest.description());
        activity.setLocation(updateRequest.location());
        activity.setStartTime(updateRequest.startTime());
        activity.setEndTime(updateRequest.endTime());
        activity.setRegistrationDeadline(updateRequest.registrationDeadline());
        activity.setParticipationRequirement(updateRequest.participationRequirement());
        activity.setAttachmentFileId(updateRequest.attachmentFileId());
        activity.setMaxParticipants(updateRequest.maxParticipants());
        Activity saved = activityRepository.save(activity);
        bindFileIfPresent(saved.getAttachmentFileId(), "ACTIVITY", saved.getId());
        notifyParticipantsForActivityUpdated(saved);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_UPDATED",
                "ACTIVITY",
                saved.getId(),
                "更新活动信息: " + saved.getTitle()
        );
        return ApiResponse.success(ActivityResponse.from(saved, occupied, true));
    }

    @PostMapping("/{activityId}/register")
    @Transactional
    public ApiResponse<Void> registerActivity(HttpServletRequest request, @PathVariable Long activityId) {
        User currentUser = AuthUtils.currentUser(request);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (registrationRepository.existsByActivityIdAndUserId(activityId, currentUser.getId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "你已报名该活动");
        }
        if (!(activity.getStatus() == ActivityStatus.PUBLISHED || activity.getStatus() == ActivityStatus.ONGOING)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "当前活动状态不允许报名");
        }
        if (activity.getRegistrationDeadline() != null && LocalDateTime.now().isAfter(activity.getRegistrationDeadline())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "活动报名已截止");
        }
        long occupied = registrationRepository.countByActivityIdAndStatusIn(activityId, OCCUPIED_STATUSES);
        if (occupied >= activity.getMaxParticipants()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "活动名额已满");
        }
        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setUserId(currentUser.getId());
        registration.setStatus(RegistrationStatus.PENDING);
        registrationRepository.save(registration);
        notificationService.notifyUser(
                activity.getOrganizerId(),
                "收到新的活动报名",
                "活动《" + activity.getTitle() + "》收到来自 " + currentUser.getDisplayName() + " 的报名申请。"
        );
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_REGISTERED",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + " 提交报名申请，等待审核"
        );
        return ApiResponse.success();
    }

    @PostMapping("/{activityId}/cancel-registration")
    @Transactional
    public ApiResponse<Void> cancelMyRegistration(HttpServletRequest request,
                                                  @PathVariable Long activityId,
                                                  @Valid @RequestBody CancelRegistrationRequest cancelRequest) {
        User currentUser = AuthUtils.currentUser(request);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, currentUser.getId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "你尚未报名该活动"));
        if (registration.getStatus() != RegistrationStatus.PENDING && registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "当前报名状态不允许主动取消");
        }
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCheckInAt(null);
        registration.setCheckOutAt(null);
        registration.setReviewComment(normalizeComment(cancelRequest.reason(), "志愿者主动取消报名"));
        registration.setReviewedAt(LocalDateTime.now());
        registrationRepository.save(registration);
        notificationService.notifyUser(
                activity.getOrganizerId(),
                "志愿者取消报名",
                currentUser.getDisplayName() + " 已取消活动《" + activity.getTitle() + "》的报名。"
        );
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_REGISTRATION_CANCELLED_BY_USER",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + " 志愿者主动取消报名，原因=" + registration.getReviewComment()
        );
        return ApiResponse.success();
    }

    @GetMapping("/my-registrations")
    public ApiResponse<List<MyRegistrationResponse>> myRegistrations(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<ActivityRegistration> registrations = registrationRepository.findByUserIdOrderByRegisteredAtDesc(currentUser.getId());
        Map<Long, Activity> activityMap = activityRepository.findAllById(
                registrations.stream().map(ActivityRegistration::getActivityId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Activity::getId, item -> item));
        Map<Long, List<AttendanceCorrectionResponse>> correctionMap = correctionsByRegistration(registrations);
        List<MyRegistrationResponse> response = registrations.stream()
                .map(registration -> MyRegistrationResponse.from(
                        registration,
                        activityMap.get(registration.getActivityId()),
                        correctionMap.getOrDefault(registration.getId(), List.of())
                ))
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/{activityId}/registrations")
    public ApiResponse<List<ActivityRegistrationResponse>> registrations(HttpServletRequest request,
                                                                         @PathVariable Long activityId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        List<ActivityRegistration> registrations = registrationRepository.findByActivityIdOrderByRegisteredAtDesc(activityId);
        Map<Long, String> userNameMap = userRepository.findAllById(
                registrations.stream().map(ActivityRegistration::getUserId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<Long, List<AttendanceCorrectionResponse>> correctionMap = correctionsByRegistration(registrations);

        List<ActivityRegistrationResponse> result = registrations.stream()
                .map(item -> new ActivityRegistrationResponse(
                        item.getId(),
                        item.getUserId(),
                        userNameMap.getOrDefault(item.getUserId(), "未知用户"),
                        item.getStatus(),
                        item.getRegisteredAt(),
                        item.getCheckInAt(),
                        item.getCheckOutAt(),
                        item.getReviewComment(),
                        item.getReviewedAt(),
                        correctionMap.getOrDefault(item.getId(), List.of())
                ))
                .toList();
        return ApiResponse.success(result);
    }

    @PatchMapping("/{activityId}/registrations/{userId}/status")
    @Transactional
    public ApiResponse<Void> reviewRegistration(HttpServletRequest request,
                                                @PathVariable Long activityId,
                                                @PathVariable Long userId,
                                                @Valid @RequestBody UpdateRegistrationStatusRequest statusRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        if (statusRequest.status() != RegistrationStatus.APPROVED
                && statusRequest.status() != RegistrationStatus.REJECTED
                && statusRequest.status() != RegistrationStatus.CANCELLED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "报名审核只允许设置为 APPROVED、REJECTED 或 CANCELLED");
        }
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "该用户未报名活动"));
        if (registration.getStatus() != RegistrationStatus.PENDING
                && registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "当前报名状态不允许再次审核");
        }
        registration.setStatus(statusRequest.status());
        registration.setReviewComment(statusRequest.comment());
        registration.setReviewedAt(LocalDateTime.now());
        if (statusRequest.status() != RegistrationStatus.APPROVED) {
            registration.setCheckInAt(null);
            registration.setCheckOutAt(null);
        }
        registrationRepository.save(registration);
        notificationService.notifyUser(
                registration.getUserId(),
                "活动报名状态更新",
                buildRegistrationStatusMessage(activity, statusRequest.status(), statusRequest.comment())
        );
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_REGISTRATION_REVIEWED",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + ", 用户ID=" + userId + ", 审核结果=" + statusRequest.status()
                        + ", 说明=" + nullToDash(statusRequest.comment())
        );
        return ApiResponse.success();
    }

    @PostMapping("/{activityId}/check-in/{userId}")
    @Transactional
    public ApiResponse<Void> checkIn(HttpServletRequest request,
                                     @PathVariable Long activityId,
                                     @PathVariable Long userId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "该用户未报名活动"));
        applyCheckIn(registration, activity);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_CHECKIN",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + ", 用户ID=" + userId + " 已签到"
        );
        return ApiResponse.success();
    }

    @PostMapping("/{activityId}/self-check-in")
    @Transactional
    public ApiResponse<Void> selfCheckIn(HttpServletRequest request,
                                         @PathVariable Long activityId,
                                         @Valid @RequestBody SelfAttendanceRequest attendanceRequest) {
        User currentUser = AuthUtils.currentUser(request);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCheckCodeMatches(activity, attendanceRequest.checkCode());
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, currentUser.getId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "你尚未报名该活动"));
        applyCheckIn(registration, activity);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_SELF_CHECKIN",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + " 志愿者自助签到"
        );
        return ApiResponse.success();
    }

    @PostMapping("/{activityId}/check-out/{userId}")
    @Transactional
    public ApiResponse<Void> checkOut(HttpServletRequest request,
                                      @PathVariable Long activityId,
                                      @PathVariable Long userId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "该用户未报名活动"));
        applyCheckOut(registration, activity);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_CHECKOUT",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + ", 用户ID=" + userId + " 已签退"
        );
        return ApiResponse.success();
    }

    @PostMapping("/{activityId}/self-check-out")
    @Transactional
    public ApiResponse<Void> selfCheckOut(HttpServletRequest request,
                                          @PathVariable Long activityId,
                                          @Valid @RequestBody SelfAttendanceRequest attendanceRequest) {
        User currentUser = AuthUtils.currentUser(request);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCheckCodeMatches(activity, attendanceRequest.checkCode());
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, currentUser.getId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "你尚未报名该活动"));
        applyCheckOut(registration, activity);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_SELF_CHECKOUT",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + " 志愿者自助签退"
        );
        return ApiResponse.success();
    }

    @PostMapping("/{activityId}/check-code/refresh")
    @Transactional
    public ApiResponse<Map<String, Object>> refreshCheckCode(HttpServletRequest request,
                                                             @PathVariable Long activityId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        activity.setCheckCode(generateCheckCode());
        activityRepository.save(activity);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_CHECK_CODE_REFRESHED",
                "ACTIVITY",
                activityId,
                "刷新活动签到码"
        );
        return ApiResponse.success(Map.of("checkCode", activity.getCheckCode()));
    }

    @PostMapping("/{activityId}/attendance-corrections")
    @Transactional
    public ApiResponse<AttendanceCorrectionResponse> correctAttendance(HttpServletRequest request,
                                                                       @PathVariable Long activityId,
                                                                       @Valid @RequestBody AttendanceCorrectionRequest correctionRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        ActivityRegistration registration = registrationRepository.findByActivityIdAndUserId(activityId, correctionRequest.userId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "该用户未报名活动"));
        ActivityAttendanceCorrection correction = buildCorrectionSnapshot(registration, currentUser, correctionRequest);
        applyAttendanceCorrection(registration, correctionRequest.action());
        registrationRepository.save(registration);
        fillCorrectionAfterSnapshot(correction, registration);
        ActivityAttendanceCorrection saved = attendanceCorrectionRepository.save(correction);
        notificationService.notifyUser(
                registration.getUserId(),
                "活动考勤已更正",
                "活动《" + activity.getTitle() + "》的考勤记录已由工作人员更正，原因：" + saved.getReason()
        );
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_ATTENDANCE_CORRECTED",
                "ACTIVITY_REGISTRATION",
                registration.getId(),
                "活动ID=" + activityId + ", 用户ID=" + registration.getUserId() + ", 更正动作=" + correctionRequest.action()
                        + ", 原因=" + saved.getReason()
        );
        return ApiResponse.success(AttendanceCorrectionResponse.from(saved));
    }

    @GetMapping("/{activityId}/attendance-corrections")
    public ApiResponse<List<AttendanceCorrectionResponse>> attendanceCorrections(HttpServletRequest request,
                                                                                 @PathVariable Long activityId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        return ApiResponse.success(attendanceCorrectionRepository.findByActivityIdOrderByCorrectedAtDesc(activityId)
                .stream()
                .map(AttendanceCorrectionResponse::from)
                .toList());
    }

    @PatchMapping("/{activityId}/status")
    @Transactional
    public ApiResponse<Void> updateStatus(HttpServletRequest request,
                                          @PathVariable Long activityId,
                                          @Valid @RequestBody UpdateStatusRequest statusRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        ensureCanManageActivity(currentUser, activity);
        ActivityStatus oldStatus = activity.getStatus();
        validateStatusTransition(activity, statusRequest.status());
        activity.setStatus(statusRequest.status());
        activityRepository.save(activity);
        handleActivityStatusSideEffects(activity, oldStatus, statusRequest.status());
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_STATUS_UPDATED",
                "ACTIVITY",
                activityId,
                "状态从 " + oldStatus + " 更新为 " + statusRequest.status()
        );
        return ApiResponse.success();
    }

    private void validateStatusTransition(Activity activity, ActivityStatus nextStatus) {
        ActivityStatus currentStatus = activity.getStatus();
        if (currentStatus == nextStatus) {
            return;
        }
        boolean allowed = switch (currentStatus) {
            case DRAFT -> nextStatus == ActivityStatus.PUBLISHED || nextStatus == ActivityStatus.CANCELLED;
            case PUBLISHED -> nextStatus == ActivityStatus.ONGOING
                    || nextStatus == ActivityStatus.OFFLINE
                    || nextStatus == ActivityStatus.CANCELLED;
            case OFFLINE -> nextStatus == ActivityStatus.PUBLISHED || nextStatus == ActivityStatus.CANCELLED;
            case ONGOING -> nextStatus == ActivityStatus.FINISHED || nextStatus == ActivityStatus.CANCELLED;
            case FINISHED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new BizException(HttpStatus.BAD_REQUEST,
                    "活动状态不允许从 " + currentStatus + " 直接变更为 " + nextStatus);
        }
    }

    private void handleActivityStatusSideEffects(Activity activity,
                                                 ActivityStatus oldStatus,
                                                 ActivityStatus newStatus) {
        if (oldStatus == newStatus) {
            return;
        }
        if (newStatus == ActivityStatus.CANCELLED) {
            cancelRelatedRegistrations(activity);
        } else {
            notifyParticipantsForStatusChange(activity, oldStatus, newStatus);
        }
    }

    private void cancelRelatedRegistrations(Activity activity) {
        List<ActivityRegistration> registrations = registrationRepository.findByActivityId(activity.getId());
        List<ActivityRegistration> changed = registrations.stream()
                .filter(item -> CANCELLABLE_REGISTRATION_STATUSES.contains(item.getStatus()))
                .peek(item -> {
                    item.setStatus(RegistrationStatus.CANCELLED);
                    item.setCheckInAt(null);
                    item.setCheckOutAt(null);
                })
                .toList();
        if (!changed.isEmpty()) {
            registrationRepository.saveAll(changed);
            notificationService.notifyUsers(
                    changed.stream().map(ActivityRegistration::getUserId).toList(),
                    "活动已取消",
                    "活动《" + activity.getTitle() + "》已取消，原报名记录已同步关闭。"
            );
        }
    }

    private void notifyParticipantsForStatusChange(Activity activity,
                                                   ActivityStatus oldStatus,
                                                   ActivityStatus newStatus) {
        List<Long> participantIds = registrationRepository.findByActivityId(activity.getId()).stream()
                .filter(item -> OCCUPIED_STATUSES.contains(item.getStatus()) || item.getStatus() == RegistrationStatus.PENDING)
                .map(ActivityRegistration::getUserId)
                .distinct()
                .toList();
        if (participantIds.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(
                participantIds,
                "活动状态更新",
                "活动《" + activity.getTitle() + "》状态已由 " + translateActivityStatus(oldStatus)
                        + " 更新为 " + translateActivityStatus(newStatus) + "。"
        );
    }

    private void notifyParticipantsForActivityUpdated(Activity activity) {
        List<Long> participantIds = registrationRepository.findByActivityId(activity.getId()).stream()
                .filter(item -> OCCUPIED_STATUSES.contains(item.getStatus()) || item.getStatus() == RegistrationStatus.PENDING)
                .map(ActivityRegistration::getUserId)
                .distinct()
                .toList();
        if (participantIds.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(
                participantIds,
                "活动信息已更新",
                "活动《" + activity.getTitle() + "》的信息已更新，请及时查看活动时间、地点和参与要求。"
        );
    }

    private String buildRegistrationStatusMessage(Activity activity, RegistrationStatus status, String comment) {
        String message = switch (status) {
            case APPROVED -> "你报名的活动《" + activity.getTitle() + "》已审核通过，请按时参加。";
            case REJECTED -> "你报名的活动《" + activity.getTitle() + "》未通过审核，请关注后续活动。";
            case CANCELLED -> "你在活动《" + activity.getTitle() + "》中的报名已被取消。";
            default -> "活动《" + activity.getTitle() + "》的报名状态已更新为 " + status + "。";
        };
        if (comment == null || comment.isBlank()) {
            return message;
        }
        return message + "处理说明：" + comment;
    }

    private String translateActivityStatus(ActivityStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case PUBLISHED -> "报名中";
            case ONGOING -> "进行中";
            case OFFLINE -> "已下架";
            case FINISHED -> "已结束";
            case CANCELLED -> "已取消";
        };
    }

    private boolean hasActivityFilters(ActivityStatus status,
                                       String keyword,
                                       String location,
                                       LocalDateTime startFrom,
                                       LocalDateTime startTo) {
        return status != null
                || (keyword != null && !keyword.isBlank())
                || (location != null && !location.isBlank())
                || startFrom != null
                || startTo != null;
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeComment(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void applyCheckIn(ActivityRegistration registration, Activity activity) {
        if (registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有审核通过的报名才能签到");
        }
        registration.setStatus(RegistrationStatus.CHECKED_IN);
        registration.setCheckInAt(LocalDateTime.now());
        registrationRepository.save(registration);
        notificationService.notifyUser(
                registration.getUserId(),
                "活动签到成功",
                "你已在活动《" + activity.getTitle() + "》完成签到。"
        );
    }

    private void applyCheckOut(ActivityRegistration registration, Activity activity) {
        if (registration.getStatus() != RegistrationStatus.CHECKED_IN || registration.getCheckInAt() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有已签到的报名才能签退");
        }
        registration.setStatus(RegistrationStatus.CHECKED_OUT);
        registration.setCheckOutAt(LocalDateTime.now());
        registrationRepository.save(registration);
        notificationService.notifyUser(
                registration.getUserId(),
                "活动签退成功",
                "你已在活动《" + activity.getTitle() + "》完成签退，可由组织方登记服务记录。"
        );
    }

    private String generateCheckCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void ensureCheckCodeMatches(Activity activity, String checkCode) {
        if (activity.getStatus() != ActivityStatus.ONGOING && activity.getStatus() != ActivityStatus.PUBLISHED) {
            throw new BizException(HttpStatus.BAD_REQUEST, "当前活动状态不允许自助签到签退");
        }
        if (checkCode == null || activity.getCheckCode() == null
                || !activity.getCheckCode().equalsIgnoreCase(checkCode.trim())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "签到码不正确");
        }
    }

    private ActivityAttendanceCorrection buildCorrectionSnapshot(ActivityRegistration registration,
                                                                 User currentUser,
                                                                 AttendanceCorrectionRequest correctionRequest) {
        ActivityAttendanceCorrection correction = new ActivityAttendanceCorrection();
        correction.setActivityId(registration.getActivityId());
        correction.setRegistrationId(registration.getId());
        correction.setUserId(registration.getUserId());
        correction.setAction(correctionRequest.action());
        correction.setBeforeStatus(registration.getStatus());
        correction.setBeforeCheckInAt(registration.getCheckInAt());
        correction.setBeforeCheckOutAt(registration.getCheckOutAt());
        correction.setReason(normalizeComment(correctionRequest.reason(), "异常考勤人工更正"));
        correction.setCorrectedBy(currentUser.getId());
        correction.setCorrectedByName(currentUser.getDisplayName());
        return correction;
    }

    private void fillCorrectionAfterSnapshot(ActivityAttendanceCorrection correction,
                                             ActivityRegistration registration) {
        correction.setAfterStatus(registration.getStatus());
        correction.setAfterCheckInAt(registration.getCheckInAt());
        correction.setAfterCheckOutAt(registration.getCheckOutAt());
    }

    private void applyAttendanceCorrection(ActivityRegistration registration,
                                           AttendanceCorrectionAction action) {
        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case SET_APPROVED -> {
                registration.setStatus(RegistrationStatus.APPROVED);
                registration.setCheckInAt(null);
                registration.setCheckOutAt(null);
            }
            case SET_CHECKED_IN -> {
                registration.setStatus(RegistrationStatus.CHECKED_IN);
                registration.setCheckInAt(now);
                registration.setCheckOutAt(null);
            }
            case SET_CHECKED_OUT -> {
                registration.setStatus(RegistrationStatus.CHECKED_OUT);
                if (registration.getCheckInAt() == null) {
                    registration.setCheckInAt(now);
                }
                registration.setCheckOutAt(now);
            }
            case CLEAR_CHECK_IN -> {
                registration.setStatus(RegistrationStatus.APPROVED);
                registration.setCheckInAt(null);
                registration.setCheckOutAt(null);
            }
            case CLEAR_CHECK_OUT -> {
                if (registration.getCheckInAt() == null) {
                    throw new BizException(HttpStatus.BAD_REQUEST, "缺少签到时间，不能仅清除签退");
                }
                registration.setStatus(RegistrationStatus.CHECKED_IN);
                registration.setCheckOutAt(null);
            }
            case SET_CANCELLED -> {
                registration.setStatus(RegistrationStatus.CANCELLED);
                registration.setCheckInAt(null);
                registration.setCheckOutAt(null);
            }
        }
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void ensureCanManageActivity(User currentUser, Activity activity) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (!currentUser.getId().equals(activity.getOrganizerId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能管理自己发布的活动");
        }
    }

    private boolean canViewCheckCode(User currentUser, Activity activity) {
        return currentUser.getRole() == Role.ADMIN || currentUser.getId().equals(activity.getOrganizerId());
    }

    private Map<Long, List<AttendanceCorrectionResponse>> correctionsByRegistration(List<ActivityRegistration> registrations) {
        if (registrations.isEmpty()) {
            return Map.of();
        }
        return attendanceCorrectionRepository
                .findByRegistrationIdInOrderByCorrectedAtDesc(registrations.stream().map(ActivityRegistration::getId).toList())
                .stream()
                .map(AttendanceCorrectionResponse::from)
                .collect(Collectors.groupingBy(AttendanceCorrectionResponse::registrationId));
    }

    private void bindFileIfPresent(Long fileId, String businessType, Long businessId) {
        if (fileId != null) {
            fileStorageService.bindBusiness(fileId, businessType, businessId);
        }
    }

    public record CreateActivityRequest(
            @NotBlank(message = "活动标题不能为空")
            @Size(max = 120, message = "活动标题最多120字")
            String title,
            @NotBlank(message = "活动描述不能为空")
            @Size(max = 1000, message = "活动描述最多1000字")
            String description,
            @NotBlank(message = "活动地点不能为空")
            @Size(max = 200, message = "活动地点最多200字")
            String location,
            @NotNull(message = "开始时间不能为空")
            LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalDateTime endTime,
            LocalDateTime registrationDeadline,
            @Size(max = 1000, message = "参与要求最多1000字")
            String participationRequirement,
            Long attachmentFileId,
            @NotNull(message = "人数上限不能为空")
            @Min(value = 1, message = "人数上限至少1人")
            Integer maxParticipants,
            ActivityStatus status
    ) {
    }

    public record UpdateActivityRequest(
            @NotBlank(message = "活动标题不能为空")
            @Size(max = 120, message = "活动标题最多120字")
            String title,
            @NotBlank(message = "活动描述不能为空")
            @Size(max = 1000, message = "活动描述最多1000字")
            String description,
            @NotBlank(message = "活动地点不能为空")
            @Size(max = 200, message = "活动地点最多200字")
            String location,
            @NotNull(message = "开始时间不能为空")
            LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalDateTime endTime,
            LocalDateTime registrationDeadline,
            @Size(max = 1000, message = "参与要求最多1000字")
            String participationRequirement,
            Long attachmentFileId,
            @NotNull(message = "人数上限不能为空")
            @Min(value = 1, message = "人数上限至少1人")
            Integer maxParticipants
    ) {
    }

    public record UpdateStatusRequest(@NotNull(message = "状态不能为空") ActivityStatus status) {
    }

    public record UpdateRegistrationStatusRequest(
            @NotNull(message = "报名状态不能为空")
            RegistrationStatus status,
            @Size(max = 500, message = "审核说明最多500字")
            String comment
    ) {
    }

    public record CancelRegistrationRequest(
            @Size(max = 500, message = "取消原因最多500字")
            String reason
    ) {
    }

    public record SelfAttendanceRequest(
            @NotBlank(message = "签到码不能为空")
            @Size(max = 32, message = "签到码最多32位")
            String checkCode
    ) {
    }

    public record AttendanceCorrectionRequest(
            @NotNull(message = "用户ID不能为空")
            Long userId,
            @NotNull(message = "更正动作不能为空")
            AttendanceCorrectionAction action,
            @NotBlank(message = "更正原因不能为空")
            @Size(max = 500, message = "更正原因最多500字")
            String reason
    ) {
    }

    public record ActivityResponse(Long id,
                                   String title,
                                   String description,
                                   String location,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime,
                                   LocalDateTime registrationDeadline,
                                   String participationRequirement,
                                   Long attachmentFileId,
                                   Integer maxParticipants,
                                   Long registeredCount,
                                   ActivityStatus status,
                                   String checkCode,
                                   Long organizerId) {
        static ActivityResponse from(Activity activity, Long registeredCount, boolean includeCheckCode) {
            return new ActivityResponse(
                    activity.getId(),
                    activity.getTitle(),
                    activity.getDescription(),
                    activity.getLocation(),
                    activity.getStartTime(),
                    activity.getEndTime(),
                    activity.getRegistrationDeadline(),
                    activity.getParticipationRequirement(),
                    activity.getAttachmentFileId(),
                    activity.getMaxParticipants(),
                    registeredCount,
                    activity.getStatus(),
                    includeCheckCode ? activity.getCheckCode() : null,
                    activity.getOrganizerId()
            );
        }
    }

    public record MyRegistrationResponse(Long registrationId,
                                         Long activityId,
                                         String activityTitle,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime,
                                         RegistrationStatus status,
                                         LocalDateTime registeredAt,
                                         LocalDateTime checkInAt,
                                         LocalDateTime checkOutAt,
                                         String reviewComment,
                                         LocalDateTime reviewedAt,
                                         List<AttendanceCorrectionResponse> corrections) {
        static MyRegistrationResponse from(ActivityRegistration registration,
                                           Activity activity,
                                           List<AttendanceCorrectionResponse> corrections) {
            String title = activity == null ? "未知活动" : activity.getTitle();
            LocalDateTime start = activity == null ? null : activity.getStartTime();
            LocalDateTime end = activity == null ? null : activity.getEndTime();
            return new MyRegistrationResponse(
                    registration.getId(),
                    registration.getActivityId(),
                    title,
                    start,
                    end,
                    registration.getStatus(),
                    registration.getRegisteredAt(),
                    registration.getCheckInAt(),
                    registration.getCheckOutAt(),
                    registration.getReviewComment(),
                    registration.getReviewedAt(),
                    corrections
            );
        }
    }

    public record ActivityRegistrationResponse(Long registrationId,
                                               Long userId,
                                               String userDisplayName,
                                               RegistrationStatus status,
                                               LocalDateTime registeredAt,
                                               LocalDateTime checkInAt,
                                               LocalDateTime checkOutAt,
                                               String reviewComment,
                                               LocalDateTime reviewedAt,
                                               List<AttendanceCorrectionResponse> corrections) {
    }

    public record AttendanceCorrectionResponse(Long id,
                                               Long activityId,
                                               Long registrationId,
                                               Long userId,
                                               AttendanceCorrectionAction action,
                                               RegistrationStatus beforeStatus,
                                               RegistrationStatus afterStatus,
                                               LocalDateTime beforeCheckInAt,
                                               LocalDateTime afterCheckInAt,
                                               LocalDateTime beforeCheckOutAt,
                                               LocalDateTime afterCheckOutAt,
                                               String reason,
                                               Long correctedBy,
                                               String correctedByName,
                                               LocalDateTime correctedAt) {
        static AttendanceCorrectionResponse from(ActivityAttendanceCorrection correction) {
            return new AttendanceCorrectionResponse(
                    correction.getId(),
                    correction.getActivityId(),
                    correction.getRegistrationId(),
                    correction.getUserId(),
                    correction.getAction(),
                    correction.getBeforeStatus(),
                    correction.getAfterStatus(),
                    correction.getBeforeCheckInAt(),
                    correction.getAfterCheckInAt(),
                    correction.getBeforeCheckOutAt(),
                    correction.getAfterCheckOutAt(),
                    correction.getReason(),
                    correction.getCorrectedBy(),
                    correction.getCorrectedByName(),
                    correction.getCorrectedAt()
            );
        }
    }
}
