package com.volunteer.vms.announcement;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.notification.Notification;
import com.volunteer.vms.notification.NotificationRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口层：实现 SRS FR-06 中的公告发布与广播通知。
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    public AnnouncementController(AnnouncementRepository announcementRepository,
                                  NotificationRepository notificationRepository,
                                  AuditLogService auditLogService) {
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<AnnouncementResponse>> list() {
        List<AnnouncementResponse> data = announcementRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AnnouncementResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping
    public ApiResponse<Void> create(HttpServletRequest request,
                                    @Valid @RequestBody CreateAnnouncementRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        Announcement announcement = new Announcement();
        announcement.setTitle(createRequest.title());
        announcement.setContent(createRequest.content());
        announcement.setPublisherId(currentUser.getId());
        Announcement saved = announcementRepository.save(announcement);

        Notification notification = new Notification();
        notification.setUserId(null);
        notification.setTitle("新公告: " + createRequest.title());
        notification.setContent(createRequest.content());
        notificationRepository.save(notification);
        auditLogService.log(
                request,
                currentUser,
                "ANNOUNCEMENT_CREATED",
                "ANNOUNCEMENT",
                saved.getId(),
                "发布公告: " + saved.getTitle()
        );
        return ApiResponse.success();
    }

    public record CreateAnnouncementRequest(
            @NotBlank(message = "公告标题不能为空")
            @Size(max = 120, message = "公告标题最多120字")
            String title,
            @NotBlank(message = "公告内容不能为空")
            @Size(max = 2000, message = "公告内容最多2000字")
            String content
    ) {
    }

    public record AnnouncementResponse(Long id, String title, String content, Long publisherId, LocalDateTime createdAt) {
        static AnnouncementResponse from(Announcement announcement) {
            return new AnnouncementResponse(
                    announcement.getId(),
                    announcement.getTitle(),
                    announcement.getContent(),
                    announcement.getPublisherId(),
                    announcement.getCreatedAt()
            );
        }
    }
}
