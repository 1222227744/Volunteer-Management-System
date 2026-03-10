package com.volunteer.vms.audit;

import com.volunteer.vms.common.BizException;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> list(HttpServletRequest request,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String operatorName,
                                                    @RequestParam(required = false) String targetType,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to,
                                                    @RequestParam(defaultValue = "120") Integer limit) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        int safeLimit = Math.max(1, Math.min(limit, 300));
        FilterSpec filter = buildFilter(action, keyword, operatorName, targetType, from, to);

        List<AuditLogResponse> data = auditLogRepository.findTop2000ByOrderByCreatedAtDesc().stream()
                .filter(item -> matches(item, filter))
                .limit(safeLimit)
                .map(AuditLogResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @GetMapping("/paged")
    public ApiResponse<Map<String, Object>> paged(HttpServletRequest request,
                                                  @RequestParam(required = false) String action,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String operatorName,
                                                  @RequestParam(required = false) String targetType,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        FilterSpec filter = buildFilter(action, keyword, operatorName, targetType, from, to);

        List<AuditLog> filtered = auditLogRepository.findTop2000ByOrderByCreatedAtDesc().stream()
                .filter(item -> matches(item, filter))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        List<AuditLogResponse> items = filtered.subList(fromIndex, toIndex).stream()
                .map(AuditLogResponse::from)
                .toList();

        return ApiResponse.success(Map.of(
                "page", safePage,
                "size", safeSize,
                "total", total,
                "items", items
        ));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(HttpServletRequest request,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String operatorName,
                                         @RequestParam(required = false) String targetType,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(defaultValue = "500") Integer limit) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        int safeLimit = Math.max(1, Math.min(limit, 2000));
        FilterSpec filter = buildFilter(action, keyword, operatorName, targetType, from, to);

        List<AuditLog> rows = auditLogRepository.findTop2000ByOrderByCreatedAtDesc().stream()
                .filter(item -> matches(item, filter))
                .limit(safeLimit)
                .toList();

        String csv = buildCsv(rows);
        byte[] payload = csv.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"");
        return new ResponseEntity<>(payload, headers, HttpStatus.OK);
    }

    private FilterSpec buildFilter(String action,
                                   String keyword,
                                   String operatorName,
                                   String targetType,
                                   String from,
                                   String to) {
        String actionFilter = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String keywordFilter = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String operatorFilter = operatorName == null ? "" : operatorName.trim().toLowerCase(Locale.ROOT);
        String targetTypeFilter = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        LocalDateTime fromTime = parseDateTime(from, "from");
        LocalDateTime toTime = parseDateTime(to, "to");
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        return new FilterSpec(actionFilter, keywordFilter, operatorFilter, targetTypeFilter, fromTime, toTime);
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 时间格式错误，应为 yyyy-MM-ddTHH:mm");
        }
    }

    private boolean matches(AuditLog item, FilterSpec filter) {
        if (!filter.action().isBlank() && !item.getAction().equalsIgnoreCase(filter.action())) {
            return false;
        }
        if (!filter.keyword().isBlank() && !containsKeyword(item, filter.keyword())) {
            return false;
        }
        if (!filter.operatorName().isBlank() &&
                !item.getOperatorName().toLowerCase(Locale.ROOT).contains(filter.operatorName())) {
            return false;
        }
        if (!filter.targetType().isBlank() &&
                !item.getTargetType().equalsIgnoreCase(filter.targetType())) {
            return false;
        }
        if (filter.from() != null && item.getCreatedAt().isBefore(filter.from())) {
            return false;
        }
        if (filter.to() != null && item.getCreatedAt().isAfter(filter.to())) {
            return false;
        }
        return true;
    }

    private boolean containsKeyword(AuditLog item, String keyword) {
        return item.getOperatorName().toLowerCase(Locale.ROOT).contains(keyword)
                || item.getAction().toLowerCase(Locale.ROOT).contains(keyword)
                || item.getTargetType().toLowerCase(Locale.ROOT).contains(keyword)
                || item.getTargetId().toLowerCase(Locale.ROOT).contains(keyword)
                || item.getDetail().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String buildCsv(List<AuditLog> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,operatorId,operatorName,operatorRole,action,targetType,targetId,ipAddress,detail\n");
        for (AuditLog item : rows) {
            sb.append(item.getId()).append(',')
                    .append(csvCell(String.valueOf(item.getCreatedAt()))).append(',')
                    .append(item.getOperatorId()).append(',')
                    .append(csvCell(item.getOperatorName())).append(',')
                    .append(csvCell(item.getOperatorRole())).append(',')
                    .append(csvCell(item.getAction())).append(',')
                    .append(csvCell(item.getTargetType())).append(',')
                    .append(csvCell(item.getTargetId())).append(',')
                    .append(csvCell(item.getIpAddress())).append(',')
                    .append(csvCell(item.getDetail())).append('\n');
        }
        return sb.toString();
    }

    private String csvCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record FilterSpec(String action,
                              String keyword,
                              String operatorName,
                              String targetType,
                              LocalDateTime from,
                              LocalDateTime to) {
    }

    public record AuditLogResponse(Long id,
                                   Long operatorId,
                                   String operatorName,
                                   String operatorRole,
                                   String action,
                                   String targetType,
                                   String targetId,
                                   String detail,
                                   String ipAddress,
                                   LocalDateTime createdAt) {
        static AuditLogResponse from(AuditLog item) {
            return new AuditLogResponse(
                    item.getId(),
                    item.getOperatorId(),
                    item.getOperatorName(),
                    item.getOperatorRole(),
                    item.getAction(),
                    item.getTargetType(),
                    item.getTargetId(),
                    item.getDetail(),
                    item.getIpAddress(),
                    item.getCreatedAt()
            );
        }
    }
}
