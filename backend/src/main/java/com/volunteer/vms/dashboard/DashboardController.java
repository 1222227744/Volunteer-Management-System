package com.volunteer.vms.dashboard;

import com.volunteer.vms.activity.ActivityRepository;
import com.volunteer.vms.activity.ActivityRegistrationRepository;
import com.volunteer.vms.activity.ActivityStatus;
import com.volunteer.vms.activity.RegistrationStatus;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.content.ContentPostRepository;
import com.volunteer.vms.content.ContentStatus;
import com.volunteer.vms.donation.DonationRepository;
import com.volunteer.vms.feedback.FeedbackRepository;
import com.volunteer.vms.feedback.FeedbackStatus;
import com.volunteer.vms.ops.IncidentRecordRepository;
import com.volunteer.vms.ops.IncidentStatus;
import com.volunteer.vms.resource.HelpNeedRepository;
import com.volunteer.vms.resource.NeedStatus;
import com.volunteer.vms.resource.PublicResourceRepository;
import com.volunteer.vms.resource.ResourceMatchRepository;
import com.volunteer.vms.resource.ResourceStatus;
import com.volunteer.vms.resource.MatchStatus;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 接口层：实现 SRS FR-08 统计分析与后台治理视图的数据聚合出口。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final ContentPostRepository contentPostRepository;
    private final DonationRepository donationRepository;
    private final FeedbackRepository feedbackRepository;
    private final PublicResourceRepository publicResourceRepository;
    private final HelpNeedRepository helpNeedRepository;
    private final ResourceMatchRepository resourceMatchRepository;
    private final IncidentRecordRepository incidentRecordRepository;

    public DashboardController(UserRepository userRepository,
                               ActivityRepository activityRepository,
                               ActivityRegistrationRepository activityRegistrationRepository,
                               ServiceRecordRepository serviceRecordRepository,
                               ContentPostRepository contentPostRepository,
                               DonationRepository donationRepository,
                               FeedbackRepository feedbackRepository,
                               PublicResourceRepository publicResourceRepository,
                               HelpNeedRepository helpNeedRepository,
                               ResourceMatchRepository resourceMatchRepository,
                               IncidentRecordRepository incidentRecordRepository) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.activityRegistrationRepository = activityRegistrationRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.contentPostRepository = contentPostRepository;
        this.donationRepository = donationRepository;
        this.feedbackRepository = feedbackRepository;
        this.publicResourceRepository = publicResourceRepository;
        this.helpNeedRepository = helpNeedRepository;
        this.resourceMatchRepository = resourceMatchRepository;
        this.incidentRecordRepository = incidentRecordRepository;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        if (currentUser.getRole() == Role.ORGANIZER) {
            return ApiResponse.success(buildOrganizerStats(currentUser));
        }
        return ApiResponse.success(buildGlobalStats());
    }

    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> exportStats(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        Map<String, Object> stats = currentUser.getRole() == Role.ORGANIZER
                ? buildOrganizerStats(currentUser)
                : buildGlobalStats();
        byte[] body = buildStatsCsv(stats).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("dashboard-stats.csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private Map<String, Object> buildOrganizerStats(User currentUser) {
        List<Long> activityIds = activityRepository.findIdsByOrganizerId(currentUser.getId());
        if (activityIds.isEmpty()) {
            return buildStatsPayload(
                    "ORGANIZER",
                    "我管理的活动",
                    false,
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    contentPostRepository.countByStatus(ContentStatus.PENDING),
                    0L,
                    BigDecimal.ZERO,
                    activityIds
            );
        }
        return buildStatsPayload(
                "ORGANIZER",
                "我管理的活动",
                false,
                false,
                activityRegistrationRepository.countDistinctUserIdByActivityIdIn(activityIds),
                activityRepository.countByOrganizerId(currentUser.getId()),
                activityRegistrationRepository.countByActivityIdIn(activityIds),
                activityRegistrationRepository.countByActivityIdInAndStatus(activityIds, RegistrationStatus.COMPLETED),
                serviceRecordRepository.sumHoursByActivityIds(activityIds),
                contentPostRepository.countByStatus(ContentStatus.PENDING),
                0L,
                BigDecimal.ZERO,
                activityIds
        );
    }

    private Map<String, Object> buildGlobalStats() {
        return buildStatsPayload(
                "GLOBAL",
                "平台全局",
                true,
                true,
                userRepository.count(),
                activityRepository.count(),
                activityRegistrationRepository.count(),
                activityRegistrationRepository.countByStatus(RegistrationStatus.COMPLETED),
                serviceRecordRepository.sumHoursAll(),
                contentPostRepository.countByStatus(ContentStatus.PENDING),
                feedbackRepository.countByStatus(FeedbackStatus.OPEN),
                donationRepository.totalAmount(),
                null
        );
    }

    private Map<String, Object> buildStatsPayload(String scope,
                                                  String scopeLabel,
                                                  boolean canViewFeedbackMetrics,
                                                  boolean canViewDonationMetrics,
                                                  long userCount,
                                                  long activityCount,
                                                  long registrationCount,
                                                  long completedRegistrationCount,
                                                  BigDecimal totalServiceHours,
                                                  long pendingContentCount,
                                                  long feedbackOpenCount,
                                                  BigDecimal donationTotalAmount,
                                                  Collection<Long> scopedActivityIds) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("scope", scope);
        stats.put("scopeLabel", scopeLabel);
        stats.put("canViewFeedbackMetrics", canViewFeedbackMetrics);
        stats.put("canViewDonationMetrics", canViewDonationMetrics);
        stats.put("userCount", userCount);
        stats.put("activityCount", activityCount);
        stats.put("registrationCount", registrationCount);
        stats.put("completedRegistrationCount", completedRegistrationCount);
        stats.put("totalServiceHours", totalServiceHours);
        stats.put("pendingContentCount", pendingContentCount);
        stats.put("feedbackOpenCount", feedbackOpenCount);
        stats.put("donationTotalAmount", donationTotalAmount);
        stats.put("activityStatusStats", activityStatusStats(scopedActivityIds));
        stats.put("registrationStatusStats", registrationStatusStats(scopedActivityIds));
        stats.put("activityTrendStats", activityTrendStats(scopedActivityIds));
        stats.put("resourceStats", resourceStats());
        stats.put("incidentOpenCount", incidentRecordRepository.countByStatus(IncidentStatus.OPEN)
                + incidentRecordRepository.countByStatus(IncidentStatus.PROCESSING));
        return stats;
    }

    private Map<String, Long> activityStatusStats(Collection<Long> scopedActivityIds) {
        Set<Long> scopedIds = scopedActivityIds == null ? null : Set.copyOf(scopedActivityIds);
        Map<ActivityStatus, Long> grouped = activityRepository.findAll().stream()
                .filter(activity -> scopedIds == null || scopedIds.contains(activity.getId()))
                .collect(Collectors.groupingBy(activity -> activity.getStatus(), () -> new EnumMap<>(ActivityStatus.class), Collectors.counting()));
        return enumCounts(ActivityStatus.values(), grouped::get);
    }

    private Map<String, Long> registrationStatusStats(Collection<Long> scopedActivityIds) {
        Set<Long> scopedIds = scopedActivityIds == null ? null : Set.copyOf(scopedActivityIds);
        Map<RegistrationStatus, Long> grouped = activityRegistrationRepository.findAll().stream()
                .filter(registration -> scopedIds == null || scopedIds.contains(registration.getActivityId()))
                .collect(Collectors.groupingBy(registration -> registration.getStatus(), () -> new EnumMap<>(RegistrationStatus.class), Collectors.counting()));
        return enumCounts(RegistrationStatus.values(), grouped::get);
    }

    private List<Map<String, Object>> activityTrendStats(Collection<Long> scopedActivityIds) {
        Set<Long> scopedIds = scopedActivityIds == null ? null : Set.copyOf(scopedActivityIds);
        List<YearMonth> months = IntStream.rangeClosed(0, 5)
                .mapToObj(index -> YearMonth.now().minusMonths(5L - index))
                .toList();
        Map<YearMonth, Long> activityCountByMonth = activityRepository.findAll().stream()
                .filter(activity -> scopedIds == null || scopedIds.contains(activity.getId()))
                .collect(Collectors.groupingBy(activity -> YearMonth.from(activity.getStartTime()), Collectors.counting()));
        Map<YearMonth, Long> registrationCountByMonth = activityRegistrationRepository.findAll().stream()
                .filter(registration -> scopedIds == null || scopedIds.contains(registration.getActivityId()))
                .collect(Collectors.groupingBy(registration -> YearMonth.from(registration.getRegisteredAt()), Collectors.counting()));
        Map<YearMonth, Long> completedCountByMonth = activityRegistrationRepository.findAll().stream()
                .filter(registration -> scopedIds == null || scopedIds.contains(registration.getActivityId()))
                .filter(registration -> registration.getStatus() == RegistrationStatus.COMPLETED)
                .collect(Collectors.groupingBy(registration -> YearMonth.from(registration.getRegisteredAt()), Collectors.counting()));
        return months.stream()
                .map(month -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("month", month.toString());
                    item.put("activityCount", activityCountByMonth.getOrDefault(month, 0L));
                    item.put("registrationCount", registrationCountByMonth.getOrDefault(month, 0L));
                    item.put("completedRegistrationCount", completedCountByMonth.getOrDefault(month, 0L));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> resourceStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("resourceCount", publicResourceRepository.count());
        stats.put("needCount", helpNeedRepository.count());
        stats.put("matchCount", resourceMatchRepository.count());
        stats.put("availableResourceCount", publicResourceRepository.countByStatus(ResourceStatus.AVAILABLE));
        stats.put("openNeedCount", helpNeedRepository.countByStatus(NeedStatus.OPEN));
        stats.put("completedMatchCount", resourceMatchRepository.countByStatus(MatchStatus.COMPLETED));
        return stats;
    }

    private <E extends Enum<E>> Map<String, Long> enumCounts(E[] values, Function<E, Long> countResolver) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (E value : values) {
            result.put(value.name(), countResolver.apply(value) == null ? 0L : countResolver.apply(value));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String buildStatsCsv(Map<String, Object> stats) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("统计项,数值\n");
        appendCsvRow(builder, "统计范围", stats.get("scopeLabel"));
        appendCsvRow(builder, "用户总数", stats.get("userCount"));
        appendCsvRow(builder, "活动总数", stats.get("activityCount"));
        appendCsvRow(builder, "报名总数", stats.get("registrationCount"));
        appendCsvRow(builder, "完成报名", stats.get("completedRegistrationCount"));
        appendCsvRow(builder, "累计服务时长", stats.get("totalServiceHours"));
        appendCsvRow(builder, "待审核内容", stats.get("pendingContentCount"));
        appendCsvRow(builder, "待处理反馈", stats.get("feedbackOpenCount"));
        appendCsvRow(builder, "捐赠总金额", stats.get("donationTotalAmount"));
        appendCsvRow(builder, "待处理故障", stats.get("incidentOpenCount"));
        Map<String, Long> activityStatusStats = (Map<String, Long>) stats.get("activityStatusStats");
        activityStatusStats.forEach((key, value) -> appendCsvRow(builder, "活动状态-" + key, value));
        Map<String, Long> registrationStatusStats = (Map<String, Long>) stats.get("registrationStatusStats");
        registrationStatusStats.forEach((key, value) -> appendCsvRow(builder, "报名状态-" + key, value));
        Map<String, Object> resourceStats = (Map<String, Object>) stats.get("resourceStats");
        resourceStats.forEach((key, value) -> appendCsvRow(builder, "资源统计-" + key, value));
        builder.append("\n月份,活动数量,报名数量,完成数量\n");
        List<Map<String, Object>> trends = (List<Map<String, Object>>) stats.get("activityTrendStats");
        for (Map<String, Object> trend : trends) {
            builder.append(escapeCsv(trend.get("month"))).append(',')
                    .append(escapeCsv(trend.get("activityCount"))).append(',')
                    .append(escapeCsv(trend.get("registrationCount"))).append(',')
                    .append(escapeCsv(trend.get("completedRegistrationCount"))).append('\n');
        }
        return builder.toString();
    }

    private void appendCsvRow(StringBuilder builder, String label, Object value) {
        builder.append(escapeCsv(label)).append(',').append(escapeCsv(value)).append('\n');
    }

    private String escapeCsv(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }
}
