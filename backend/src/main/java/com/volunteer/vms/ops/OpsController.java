package com.volunteer.vms.ops;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
public class OpsController {
    private final SystemConfigRepository systemConfigRepository;
    private final IncidentRecordRepository incidentRecordRepository;
    private final AuditLogService auditLogService;

    public OpsController(SystemConfigRepository systemConfigRepository,
                         IncidentRecordRepository incidentRecordRepository,
                         AuditLogService auditLogService) {
        this.systemConfigRepository = systemConfigRepository;
        this.incidentRecordRepository = incidentRecordRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/configs")
    public ApiResponse<List<SystemConfigResponse>> configs(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        ensureDefaultConfigs(currentUser);
        return ApiResponse.success(systemConfigRepository.findAllByOrderByConfigKeyAsc().stream()
                .map(SystemConfigResponse::from)
                .toList());
    }

    @PatchMapping("/configs/{configKey}")
    @Transactional
    public ApiResponse<SystemConfigResponse> updateConfig(HttpServletRequest request,
                                                          @PathVariable String configKey,
                                                          @Valid @RequestBody UpdateConfigRequest updateRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        ensureDefaultConfigs(currentUser);
        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "系统配置不存在"));
        if (!Boolean.TRUE.equals(config.getEditable())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该配置不允许在线修改");
        }
        config.setConfigValue(updateRequest.configValue().trim());
        config.setUpdatedBy(currentUser.getId());
        config.setUpdatedByName(currentUser.getDisplayName());
        SystemConfig saved = systemConfigRepository.save(config);
        auditLogService.log(
                request,
                currentUser,
                "SYSTEM_CONFIG_UPDATED",
                "SYSTEM_CONFIG",
                saved.getConfigKey(),
                "更新系统配置: " + saved.getConfigName() + "=" + saved.getConfigValue()
        );
        return ApiResponse.success(SystemConfigResponse.from(saved));
    }

    @GetMapping("/incidents")
    public ApiResponse<Map<String, Object>> incidents(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        List<IncidentRecordResponse> items = incidentRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(IncidentRecordResponse::from)
                .toList();
        return ApiResponse.success(Map.of(
                "openCount", incidentRecordRepository.countByStatus(IncidentStatus.OPEN)
                        + incidentRecordRepository.countByStatus(IncidentStatus.PROCESSING),
                "items", items
        ));
    }

    @PostMapping("/incidents")
    @Transactional
    public ApiResponse<IncidentRecordResponse> createIncident(HttpServletRequest request,
                                                              @Valid @RequestBody CreateIncidentRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        IncidentRecord incident = new IncidentRecord();
        incident.setTitle(createRequest.title());
        incident.setDescription(createRequest.description());
        incident.setSeverity(createRequest.severity());
        incident.setStatus(createRequest.status() == null ? IncidentStatus.OPEN : createRequest.status());
        incident.setHandlingMeasure(createRequest.handlingMeasure());
        incident.setResult(createRequest.result());
        incident.setCreatedBy(currentUser.getId());
        incident.setCreatedByName(currentUser.getDisplayName());
        if (incident.getStatus() == IncidentStatus.RESOLVED || incident.getStatus() == IncidentStatus.CLOSED) {
            incident.setResolvedAt(LocalDateTime.now());
        }
        IncidentRecord saved = incidentRecordRepository.save(incident);
        auditLogService.log(
                request,
                currentUser,
                "INCIDENT_CREATED",
                "INCIDENT",
                saved.getId(),
                "登记故障处理记录: " + saved.getTitle()
        );
        return ApiResponse.success(IncidentRecordResponse.from(saved));
    }

    @PatchMapping("/incidents/{incidentId}")
    @Transactional
    public ApiResponse<IncidentRecordResponse> updateIncident(HttpServletRequest request,
                                                              @PathVariable Long incidentId,
                                                              @Valid @RequestBody UpdateIncidentRequest updateRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        IncidentRecord incident = incidentRecordRepository.findById(incidentId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "故障记录不存在"));
        incident.setStatus(updateRequest.status());
        incident.setHandlingMeasure(updateRequest.handlingMeasure());
        incident.setResult(updateRequest.result());
        if (updateRequest.status() == IncidentStatus.RESOLVED || updateRequest.status() == IncidentStatus.CLOSED) {
            incident.setResolvedAt(LocalDateTime.now());
        } else {
            incident.setResolvedAt(null);
        }
        IncidentRecord saved = incidentRecordRepository.save(incident);
        auditLogService.log(
                request,
                currentUser,
                "INCIDENT_UPDATED",
                "INCIDENT",
                saved.getId(),
                "更新故障处理记录状态为: " + saved.getStatus()
        );
        return ApiResponse.success(IncidentRecordResponse.from(saved));
    }

    private void ensureDefaultConfigs(User currentUser) {
        ensureConfig("demo.data.enabled", "演示数据开关", "false", "生产环境应关闭演示数据初始化。", false, currentUser);
        ensureConfig("bootstrap.accounts.enabled", "默认账号初始化", "false", "生产环境应关闭默认管理员和组织方账号创建。", false, currentUser);
        ensureConfig("registration.require.verification", "报名实名校验", "false", "开启后可作为报名资格校验规则。", true, currentUser);
        ensureConfig("notification.external.enabled", "外部通知开关", "true", "控制邮件/短信任务是否作为外部通知通道处理。", true, currentUser);
        ensureConfig("attendance.self.enabled", "自助签到签退开关", "true", "控制志愿者是否可通过签到码自助签到签退。", true, currentUser);
    }

    private void ensureConfig(String key,
                              String name,
                              String value,
                              String description,
                              boolean editable,
                              User currentUser) {
        if (systemConfigRepository.findByConfigKey(key).isPresent()) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigName(name);
        config.setConfigValue(value);
        config.setDescription(description);
        config.setEditable(editable);
        config.setUpdatedBy(currentUser.getId());
        config.setUpdatedByName(currentUser.getDisplayName());
        systemConfigRepository.save(config);
    }

    public record UpdateConfigRequest(
            @NotBlank(message = "配置值不能为空")
            @Size(max = 500, message = "配置值最多500字")
            String configValue
    ) {
    }

    public record CreateIncidentRequest(
            @NotBlank(message = "故障标题不能为空")
            @Size(max = 120, message = "故障标题最多120字")
            String title,
            @NotBlank(message = "故障描述不能为空")
            @Size(max = 1000, message = "故障描述最多1000字")
            String description,
            @NotNull(message = "严重程度不能为空")
            IncidentSeverity severity,
            IncidentStatus status,
            @Size(max = 1000, message = "处理措施最多1000字")
            String handlingMeasure,
            @Size(max = 1000, message = "处理结果最多1000字")
            String result
    ) {
    }

    public record UpdateIncidentRequest(
            @NotNull(message = "处理状态不能为空")
            IncidentStatus status,
            @Size(max = 1000, message = "处理措施最多1000字")
            String handlingMeasure,
            @Size(max = 1000, message = "处理结果最多1000字")
            String result
    ) {
    }

    public record SystemConfigResponse(Long id,
                                       String configKey,
                                       String configValue,
                                       String configName,
                                       String description,
                                       Boolean editable,
                                       LocalDateTime updatedAt,
                                       Long updatedBy,
                                       String updatedByName) {
        static SystemConfigResponse from(SystemConfig config) {
            return new SystemConfigResponse(
                    config.getId(),
                    config.getConfigKey(),
                    config.getConfigValue(),
                    config.getConfigName(),
                    config.getDescription(),
                    config.getEditable(),
                    config.getUpdatedAt(),
                    config.getUpdatedBy(),
                    config.getUpdatedByName()
            );
        }
    }

    public record IncidentRecordResponse(Long id,
                                         String title,
                                         String description,
                                         IncidentSeverity severity,
                                         IncidentStatus status,
                                         String handlingMeasure,
                                         String result,
                                         Long createdBy,
                                         String createdByName,
                                         LocalDateTime createdAt,
                                         LocalDateTime resolvedAt) {
        static IncidentRecordResponse from(IncidentRecord incident) {
            return new IncidentRecordResponse(
                    incident.getId(),
                    incident.getTitle(),
                    incident.getDescription(),
                    incident.getSeverity(),
                    incident.getStatus(),
                    incident.getHandlingMeasure(),
                    incident.getResult(),
                    incident.getCreatedBy(),
                    incident.getCreatedByName(),
                    incident.getCreatedAt(),
                    incident.getResolvedAt()
            );
        }
    }
}
