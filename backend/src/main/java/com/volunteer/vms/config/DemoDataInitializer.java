package com.volunteer.vms.config;

import com.volunteer.vms.activity.Activity;
import com.volunteer.vms.activity.ActivityRegistration;
import com.volunteer.vms.activity.ActivityRegistrationRepository;
import com.volunteer.vms.activity.ActivityRepository;
import com.volunteer.vms.activity.ActivityStatus;
import com.volunteer.vms.activity.RegistrationStatus;
import com.volunteer.vms.announcement.Announcement;
import com.volunteer.vms.announcement.AnnouncementRepository;
import com.volunteer.vms.audit.AuditLog;
import com.volunteer.vms.audit.AuditLogRepository;
import com.volunteer.vms.content.ContentPost;
import com.volunteer.vms.content.ContentPostRepository;
import com.volunteer.vms.content.ContentStatus;
import com.volunteer.vms.donation.Donation;
import com.volunteer.vms.donation.DonationRepository;
import com.volunteer.vms.feedback.ActivityFeedback;
import com.volunteer.vms.feedback.ActivityFeedbackRepository;
import com.volunteer.vms.feedback.Feedback;
import com.volunteer.vms.feedback.FeedbackRepository;
import com.volunteer.vms.feedback.FeedbackStatus;
import com.volunteer.vms.notification.Notification;
import com.volunteer.vms.notification.NotificationRepository;
import com.volunteer.vms.service.ServiceRecord;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import com.volunteer.vms.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动样例数据：用于课程演示和联调测试。
 * 数据内容按 SRS FR-01 ~ FR-08 组织，便于直接展示完整业务闭环。
 */
@Component
@Order(2)
@EnableConfigurationProperties(DemoDataInitializer.DemoDataProperties.class)
public class DemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final ContentPostRepository contentPostRepository;
    private final AnnouncementRepository announcementRepository;
    private final DonationRepository donationRepository;
    private final ActivityFeedbackRepository activityFeedbackRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataProperties demoDataProperties;

    public DemoDataInitializer(UserRepository userRepository,
                               ActivityRepository activityRepository,
                               ActivityRegistrationRepository registrationRepository,
                               ServiceRecordRepository serviceRecordRepository,
                               ContentPostRepository contentPostRepository,
                               AnnouncementRepository announcementRepository,
                               DonationRepository donationRepository,
                               ActivityFeedbackRepository activityFeedbackRepository,
                               FeedbackRepository feedbackRepository,
                               NotificationRepository notificationRepository,
                               AuditLogRepository auditLogRepository,
                               PasswordEncoder passwordEncoder,
                               DemoDataProperties demoDataProperties) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.contentPostRepository = contentPostRepository;
        this.announcementRepository = announcementRepository;
        this.donationRepository = donationRepository;
        this.activityFeedbackRepository = activityFeedbackRepository;
        this.feedbackRepository = feedbackRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoDataProperties = demoDataProperties;
    }

    @Override
    public void run(String... args) {
        if (!demoDataProperties.enabled()) {
            log.info("跳过演示数据初始化，vms.demo-data.enabled=false");
            return;
        }
        if (activityRepository.count() > 0 || registrationRepository.count() > 0 || serviceRecordRepository.count() > 0) {
            log.info("检测到业务数据已存在，跳过演示数据初始化");
            return;
        }

        Map<String, User> users = seedUsers();
        Map<String, Activity> activities = seedActivities(users);
        seedRegistrations(activities, users);
        seedServiceRecords(activities, users);
        seedActivityFeedbacks(activities, users);
        seedContents(users);
        seedAnnouncements(users);
        seedDonations(users);
        seedFeedbacks(users);
        seedNotifications(users);
        seedAuditLogs(users, activities);
        log.info("演示数据初始化完成");
    }

    private Map<String, User> seedUsers() {
        Map<String, User> users = new HashMap<>();
        users.put("admin", ensureUser("admin", "admin123", "系统管理员", Role.ADMIN, 320, "13800000001", "平台运营与审计管理", VerificationStatus.VERIFIED));
        users.put("organizer", ensureUser("organizer", "organizer123", "组织方账号", Role.ORGANIZER, 180, "13800000002", "社区公益活动组织", VerificationStatus.VERIFIED));
        users.put("liu", ensureUser("liuqi", "volunteer123", "刘琪", Role.VOLUNTEER, 96, "13800000003", "校园迎新、秩序维护、活动宣传", VerificationStatus.PENDING));
        users.put("chen", ensureUser("chenmo", "volunteer123", "陈墨", Role.VOLUNTEER, 132, "13800000004", "环保清洁、社区陪伴", VerificationStatus.VERIFIED));
        users.put("lin", ensureUser("linan", "volunteer123", "林安", Role.VOLUNTEER, 68, "13800000005", "儿童阅读陪伴、图书整理", VerificationStatus.VERIFIED));
        users.put("zhao", ensureUser("zhaowei", "volunteer123", "赵薇", Role.VOLUNTEER, 110, "13800000006", "环保宣传、现场引导", VerificationStatus.VERIFIED));
        users.put("sun", ensureUser("sunhao", "volunteer123", "孙昊", Role.VOLUNTEER, 42, "13800000007", "物资搬运、后勤支持", VerificationStatus.REJECTED));
        return users;
    }

    private User ensureUser(String username,
                            String rawPassword,
                            String displayName,
                            Role role,
                            int points,
                            String phone,
                            String serviceIntention,
                            VerificationStatus verificationStatus) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            boolean changed = false;
            if (existing.getPhone() == null) {
                existing.setPhone(phone);
                changed = true;
            }
            if (existing.getServiceIntention() == null) {
                existing.setServiceIntention(serviceIntention);
                changed = true;
            }
            if (existing.getVerificationStatus() == null || existing.getVerificationStatus() == VerificationStatus.UNVERIFIED) {
                existing.setVerificationStatus(verificationStatus);
                existing.setVerificationComment(verificationStatus == VerificationStatus.REJECTED ? "演示数据：资料信息不完整。" : null);
                changed = true;
            }
            if (changed) {
                return userRepository.save(existing);
            }
            return existing;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setPoints(points);
        user.setPhone(phone);
        user.setServiceIntention(serviceIntention);
        user.setVerificationStatus(verificationStatus);
        user.setVerificationComment(verificationStatus == VerificationStatus.REJECTED ? "演示数据：资料信息不完整。" : null);
        return userRepository.save(user);
    }

    private Map<String, Activity> seedActivities(Map<String, User> users) {
        Map<String, Activity> activities = new HashMap<>();
        User organizer = users.get("organizer");

        activities.put("park", saveActivity(
                "城市公园环保清洁行动",
                "组织志愿者分组清理步道、草坪和水域周边垃圾，并向游客宣传垃圾分类知识。",
                "需自备防晒用品，现场统一发放手套和垃圾袋。",
                "城南市民公园",
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(10).plusHours(4),
                LocalDateTime.now().minusDays(12),
                20,
                ActivityStatus.FINISHED,
                organizer.getId()
        ));
        activities.put("library", saveActivity(
                "社区图书馆阅读陪伴",
                "为社区儿童开展阅读陪伴、绘本整理和借阅秩序维护。",
                "需有耐心，能够完成基础图书分类和儿童陪伴阅读。",
                "青禾社区图书馆",
                LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusDays(3).plusHours(3),
                LocalDateTime.now().minusDays(4),
                12,
                ActivityStatus.FINISHED,
                organizer.getId()
        ));
        activities.put("campus", saveActivity(
                "校园迎新志愿服务",
                "在报到点协助路线指引、物资发放和新生咨询接待。",
                "需熟悉校园主要路线，能够连续服务不少于 3 小时。",
                "大学生活动中心",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(6),
                LocalDateTime.now().plusDays(1),
                30,
                ActivityStatus.PUBLISHED,
                organizer.getId()
        ));
        activities.put("elder", saveActivity(
                "敬老院陪伴慰问",
                "陪伴老人聊天、协助整理房间并进行简单文娱活动组织。",
                "需具备基本沟通能力，活动当天服从现场分组安排。",
                "康乐敬老院",
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(5).plusHours(4),
                LocalDateTime.now().plusDays(4),
                15,
                ActivityStatus.PUBLISHED,
                organizer.getId()
        ));
        return activities;
    }

    private Activity saveActivity(String title,
                                  String description,
                                  String participationRequirement,
                                  String location,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  LocalDateTime registrationDeadline,
                                  int maxParticipants,
                                  ActivityStatus status,
                                  Long organizerId) {
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setDescription(description);
        activity.setParticipationRequirement(participationRequirement);
        activity.setLocation(location);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setRegistrationDeadline(registrationDeadline);
        activity.setMaxParticipants(maxParticipants);
        activity.setStatus(status);
        activity.setOrganizerId(organizerId);
        return activityRepository.save(activity);
    }

    private void seedRegistrations(Map<String, Activity> activities, Map<String, User> users) {
        saveRegistration(activities.get("park"), users.get("chen"), RegistrationStatus.COMPLETED,
                LocalDateTime.now().minusDays(12), LocalDateTime.now().minusDays(10).plusHours(0), LocalDateTime.now().minusDays(10).plusHours(4));
        saveRegistration(activities.get("park"), users.get("zhao"), RegistrationStatus.COMPLETED,
                LocalDateTime.now().minusDays(12), LocalDateTime.now().minusDays(10).plusMinutes(20), LocalDateTime.now().minusDays(10).plusHours(4).minusMinutes(10));
        saveRegistration(activities.get("park"), users.get("liu"), RegistrationStatus.CANCELLED,
                LocalDateTime.now().minusDays(11), null, null);

        saveRegistration(activities.get("library"), users.get("lin"), RegistrationStatus.COMPLETED,
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3).plusHours(3));
        saveRegistration(activities.get("library"), users.get("sun"), RegistrationStatus.REJECTED,
                LocalDateTime.now().minusDays(5), null, null);

        saveRegistration(activities.get("campus"), users.get("liu"), RegistrationStatus.PENDING,
                LocalDateTime.now().minusHours(20), null, null);
        saveRegistration(activities.get("campus"), users.get("chen"), RegistrationStatus.APPROVED,
                LocalDateTime.now().minusHours(18), null, null);
        saveRegistration(activities.get("campus"), users.get("zhao"), RegistrationStatus.APPROVED,
                LocalDateTime.now().minusHours(10), null, null);

        saveRegistration(activities.get("elder"), users.get("lin"), RegistrationStatus.PENDING,
                LocalDateTime.now().minusHours(4), null, null);

        registrationRepository.findByActivityIdAndUserId(activities.get("park").getId(), users.get("liu").getId())
                .ifPresent(item -> {
                    item.setReviewComment("志愿者因时间冲突主动取消报名。");
                    item.setReviewedAt(LocalDateTime.now().minusDays(10));
                    registrationRepository.save(item);
                });
        registrationRepository.findByActivityIdAndUserId(activities.get("library").getId(), users.get("sun").getId())
                .ifPresent(item -> {
                    item.setReviewComment("本场阅读陪伴名额有限，优先安排已有阅读活动经验的志愿者。");
                    item.setReviewedAt(LocalDateTime.now().minusDays(4));
                    registrationRepository.save(item);
                });
    }

    private void saveRegistration(Activity activity,
                                  User user,
                                  RegistrationStatus status,
                                  LocalDateTime registeredAt,
                                  LocalDateTime checkInAt,
                                  LocalDateTime checkOutAt) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activity.getId());
        registration.setUserId(user.getId());
        registration.setStatus(status);
        registration.setRegisteredAt(registeredAt);
        registration.setCheckInAt(checkInAt);
        registration.setCheckOutAt(checkOutAt);
        registrationRepository.save(registration);
    }

    private void seedServiceRecords(Map<String, Activity> activities, Map<String, User> users) {
        saveServiceRecord(users.get("chen"), activities.get("park"), new BigDecimal("4.00"),
                "负责公园东侧步道清洁与垃圾分类宣传，共完成 8 袋可回收垃圾整理。", "https://example.com/evidence/park-chen");
        saveServiceRecord(users.get("zhao"), activities.get("park"), new BigDecimal("3.50"),
                "完成水域沿线清洁和游客引导，协助现场秩序维护。", "https://example.com/evidence/park-zhao");
        saveServiceRecord(users.get("lin"), activities.get("library"), new BigDecimal("3.00"),
                "组织儿童阅读陪伴活动并完成图书归类与借阅登记辅助。", "https://example.com/evidence/library-lin");
    }

    private void saveServiceRecord(User user,
                                   Activity activity,
                                   BigDecimal hours,
                                   String achievement,
                                   String evidenceUrl) {
        ServiceRecord record = new ServiceRecord();
        record.setUserId(user.getId());
        record.setActivityId(activity.getId());
        record.setHours(hours);
        record.setAchievement(achievement);
        record.setEvidenceUrl(evidenceUrl);
        serviceRecordRepository.save(record);
    }

    private void seedContents(Map<String, User> users) {
        saveContent(users.get("chen"), "公园清洁活动成果纪实",
                "我们分为四个小组完成了步道、草坪和湖边区域的清洁，还向游客发放了垃圾分类宣传卡片。",
                ContentStatus.APPROVED, "内容详实，可用于成果展示。", LocalDateTime.now().minusDays(9));
        saveContent(users.get("lin"), "社区阅读陪伴心得",
                "本次活动中孩子们参与度很高，志愿者还补充整理了借阅区书架标签。",
                ContentStatus.PENDING, null, null);
        saveContent(users.get("liu"), "迎新志愿者招募倡议",
                "建议迎新活动增加行李搬运路线图和咨询台值班说明。",
                ContentStatus.REJECTED, "请区分活动倡议与个人心得后重新投稿。", LocalDateTime.now().minusHours(20));
    }

    private void seedActivityFeedbacks(Map<String, Activity> activities, Map<String, User> users) {
        saveActivityFeedback(users.get("chen"), activities.get("park"), 5,
                "活动组织清晰，分工明确，现场物资和路线说明都很到位。");
        saveActivityFeedback(users.get("zhao"), activities.get("park"), 4,
                "环保宣传和清洁任务衔接顺畅，建议后续增加中途补水点。");
        saveActivityFeedback(users.get("lin"), activities.get("library"), 5,
                "活动节奏舒适，儿童阅读陪伴和书架整理都安排得很合理。");
    }

    private void saveActivityFeedback(User user, Activity activity, int rating, String comment) {
        ActivityFeedback feedback = new ActivityFeedback();
        feedback.setUserId(user.getId());
        feedback.setActivityId(activity.getId());
        feedback.setRating(rating);
        feedback.setComment(comment);
        activityFeedbackRepository.save(feedback);
    }

    private void saveContent(User user,
                             String title,
                             String content,
                             ContentStatus status,
                             String reviewComment,
                             LocalDateTime reviewedAt) {
        ContentPost post = new ContentPost();
        post.setUserId(user.getId());
        post.setTitle(title);
        post.setContent(content);
        post.setStatus(status);
        post.setReviewComment(reviewComment);
        post.setReviewedAt(reviewedAt);
        contentPostRepository.save(post);
    }

    private void seedAnnouncements(Map<String, User> users) {
        saveAnnouncement(users.get("organizer"), "五一期间活动安排说明",
                "节日期间平台仍开放报名，但线下服务活动将按照组织方实际通知执行。");
        saveAnnouncement(users.get("admin"), "服务记录公示规则更新",
                "服务记录登记需以签到签退留痕为前提，异常情况由管理员复核。");
    }

    private void saveAnnouncement(User publisher, String title, String content) {
        Announcement announcement = new Announcement();
        announcement.setPublisherId(publisher.getId());
        announcement.setTitle(title);
        announcement.setContent(content);
        announcementRepository.save(announcement);
    }

    private void seedDonations(Map<String, User> users) {
        saveDonation(users.get("liu"), "刘琪", new BigDecimal("50.00"), "支持迎新志愿者饮水补给");
        saveDonation(users.get("chen"), "陈墨", new BigDecimal("88.00"), "用于环保活动物资采购");
        saveDonation(users.get("organizer"), "组织方账号", new BigDecimal("200.00"), "补充社区活动宣传展板费用");
    }

    private void saveDonation(User user, String donorName, BigDecimal amount, String message) {
        Donation donation = new Donation();
        donation.setUserId(user.getId());
        donation.setDonorName(donorName);
        donation.setAmount(amount);
        donation.setMessage(message);
        donationRepository.save(donation);
    }

    private void seedFeedbacks(Map<String, User> users) {
        saveFeedback(users.get("liu"), "希望活动报名页面增加报名状态说明，便于区分待审核和已通过。", FeedbackStatus.RESOLVED,
                "已在活动执行页补充审核流程，后续会继续优化报名页说明。");
        saveFeedback(users.get("sun"), "建议捐赠记录支持导出回执，方便后续汇总。", FeedbackStatus.OPEN, null);
    }

    private void saveFeedback(User user, String content, FeedbackStatus status, String reply) {
        Feedback feedback = new Feedback();
        feedback.setUserId(user.getId());
        feedback.setContent(content);
        feedback.setStatus(status);
        if (status == FeedbackStatus.RESOLVED) {
            feedback.setReply(reply);
            feedback.setResolvedAt(LocalDateTime.now().minusHours(8));
        }
        feedbackRepository.save(feedback);
    }

    private void seedNotifications(Map<String, User> users) {
        for (User user : users.values()) {
            saveNotification(user.getId(), "系统演示数据已加载",
                    "当前环境已预置活动、报名、服务记录、反馈和审计日志数据，可直接用于课程展示。", false);
        }
        saveNotification(users.get("liu").getId(), "反馈已处理",
                "你提交的页面优化建议已完成处理，请前往反馈页面查看回复。", false);
        saveNotification(users.get("chen").getId(), "活动报名审核通过",
                "你报名的“校园迎新志愿服务”已审核通过，请按时参加。", true);
    }

    private void saveNotification(Long userId, String title, String content, boolean readFlag) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setReadFlag(readFlag);
        notificationRepository.save(notification);
    }

    private void seedAuditLogs(Map<String, User> users, Map<String, Activity> activities) {
        saveAuditLog(users.get("organizer"), "ACTIVITY_CREATED", "ACTIVITY", activities.get("campus").getId(),
                "创建活动: 校园迎新志愿服务");
        saveAuditLog(users.get("organizer"), "ACTIVITY_REGISTRATION_REVIEWED", "ACTIVITY_REGISTRATION", 1L,
                "审核通过陈墨的迎新活动报名");
        saveAuditLog(users.get("admin"), "ANNOUNCEMENT_CREATED", "ANNOUNCEMENT", 1L,
                "发布公告: 服务记录公示规则更新");
        saveAuditLog(users.get("organizer"), "CONTENT_REVIEWED", "CONTENT", 2L,
                "审核结果=APPROVED, 审核意见=内容详实，可用于成果展示。");
        saveAuditLog(users.get("admin"), "FEEDBACK_RESOLVED", "FEEDBACK", 1L,
                "处理回复=已在活动执行页补充审核流程，后续会继续优化报名页说明。");
    }

    private void saveAuditLog(User operator, String action, String targetType, Object targetId, String detail) {
        AuditLog logItem = new AuditLog();
        logItem.setOperatorId(operator.getId());
        logItem.setOperatorName(operator.getDisplayName());
        logItem.setOperatorRole(operator.getRole().name());
        logItem.setAction(action);
        logItem.setTargetType(targetType);
        logItem.setTargetId(String.valueOf(targetId));
        logItem.setDetail(detail);
        logItem.setIpAddress("127.0.0.1");
        auditLogRepository.save(logItem);
    }

    @ConfigurationProperties(prefix = "vms.demo-data")
    public record DemoDataProperties(boolean enabled) {
    }
}
