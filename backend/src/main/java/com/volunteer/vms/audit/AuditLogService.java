package com.volunteer.vms.audit;

import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(HttpServletRequest request,
                    User operator,
                    String action,
                    String targetType,
                    Object targetId,
                    String detail) {
        if (operator == null) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setOperatorId(operator.getId());
        log.setOperatorName(operator.getDisplayName());
        log.setOperatorRole(operator.getRole().name());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(String.valueOf(targetId));
        log.setDetail(detail == null ? "" : detail);
        log.setIpAddress(resolveIp(request));
        auditLogRepository.save(log);
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr.trim();
    }
}
