package com.volunteer.vms.audit;

import com.volunteer.vms.common.BizException;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 接口层：支撑 SRS FR-01、FR-06、FR-08 中提到的关键操作留痕与审计查询。
 */
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
        Page<AuditLog> page = auditLogRepository.findAllBySpec(
                buildSpecification(filter),
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        );
        List<AuditLogResponse> data = page.getContent().stream()
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
        Page<AuditLog> result = auditLogRepository.findAllBySpec(
                buildSpecification(filter),
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        );
        List<AuditLogResponse> items = result.getContent().stream()
                .map(AuditLogResponse::from)
                .toList();

        return ApiResponse.success(Map.of(
                "page", safePage,
                "size", safeSize,
                "total", result.getTotalElements(),
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
        List<AuditLog> rows = auditLogRepository.findAllBySpec(
                buildSpecification(filter),
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        ).getContent();

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

    private Specification<AuditLog> buildSpecification(FilterSpec filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!filter.action().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("action")), filter.action()));
            }
            if (!filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("operatorName")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("targetType")), pattern),
                        cb.like(cb.lower(root.get("targetId")), pattern),
                        cb.like(cb.lower(root.get("detail")), pattern)
                ));
            }
            if (!filter.operatorName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("operatorName")), "%" + filter.operatorName() + "%"));
            }
            if (!filter.targetType().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("targetType")), filter.targetType()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
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
