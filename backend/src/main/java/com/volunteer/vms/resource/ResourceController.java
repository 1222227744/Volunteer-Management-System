package com.volunteer.vms.resource;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final PublicResourceRepository resourceRepository;
    private final HelpNeedRepository needRepository;
    private final ResourceMatchRepository matchRepository;
    private final AuditLogService auditLogService;

    public ResourceController(PublicResourceRepository resourceRepository,
                              HelpNeedRepository needRepository,
                              ResourceMatchRepository matchRepository,
                              AuditLogService auditLogService) {
        this.resourceRepository = resourceRepository;
        this.needRepository = needRepository;
        this.matchRepository = matchRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<ResourceBoardResponse> board() {
        List<PublicResource> resources = resourceRepository.findAllByOrderByCreatedAtDesc();
        List<HelpNeed> needs = needRepository.findAllByOrderByCreatedAtDesc();
        List<ResourceMatch> matches = matchRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, PublicResource> resourceMap = resources.stream()
                .collect(Collectors.toMap(PublicResource::getId, item -> item));
        Map<Long, HelpNeed> needMap = needs.stream()
                .collect(Collectors.toMap(HelpNeed::getId, item -> item));
        return ApiResponse.success(new ResourceBoardResponse(
                resources.stream().map(ResourceResponse::from).toList(),
                needs.stream().map(NeedResponse::from).toList(),
                matches.stream()
                        .map(item -> MatchResponse.from(item, resourceMap.get(item.getResourceId()), needMap.get(item.getNeedId())))
                        .toList()
        ));
    }

    @PostMapping
    public ApiResponse<ResourceResponse> createResource(HttpServletRequest request,
                                                        @Valid @RequestBody CreateResourceRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        PublicResource resource = new PublicResource();
        resource.setName(createRequest.name());
        resource.setCategory(createRequest.category());
        resource.setSource(createRequest.source());
        resource.setQuantity(createRequest.quantity());
        resource.setUnit(createRequest.unit());
        resource.setAvailableScope(createRequest.availableScope());
        resource.setCreatedBy(currentUser.getId());
        PublicResource saved = resourceRepository.save(resource);
        auditLogService.log(request, currentUser, "PUBLIC_RESOURCE_CREATED", "PUBLIC_RESOURCE",
                saved.getId(), "登记公益资源: " + saved.getName());
        return ApiResponse.success(ResourceResponse.from(saved));
    }

    @PostMapping("/needs")
    public ApiResponse<NeedResponse> createNeed(HttpServletRequest request,
                                                @Valid @RequestBody CreateNeedRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        HelpNeed need = new HelpNeed();
        need.setTitle(createRequest.title());
        need.setRequester(createRequest.requester());
        need.setContent(createRequest.content());
        need.setQuantity(createRequest.quantity());
        need.setUnit(createRequest.unit());
        need.setLocation(createRequest.location());
        need.setRequiredAt(createRequest.requiredAt());
        need.setCreatedBy(currentUser.getId());
        HelpNeed saved = needRepository.save(need);
        auditLogService.log(request, currentUser, "HELP_NEED_CREATED", "HELP_NEED",
                saved.getId(), "登记帮扶需求: " + saved.getTitle());
        return ApiResponse.success(NeedResponse.from(saved));
    }

    @PostMapping("/matches")
    @Transactional
    public ApiResponse<MatchResponse> createMatch(HttpServletRequest request,
                                                  @Valid @RequestBody CreateMatchRequest createRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        PublicResource resource = resourceRepository.findById(createRequest.resourceId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "公益资源不存在"));
        HelpNeed need = needRepository.findById(createRequest.needId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "帮扶需求不存在"));
        if (resource.getStatus() != ResourceStatus.AVAILABLE) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有可用资源才能匹配");
        }
        if (need.getStatus() != NeedStatus.OPEN) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有待匹配需求才能匹配资源");
        }
        if (createRequest.allocatedQuantity() > resource.getQuantity()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "分配数量不能超过资源数量");
        }
        ResourceMatch match = new ResourceMatch();
        match.setResourceId(resource.getId());
        match.setNeedId(need.getId());
        match.setAllocatedQuantity(createRequest.allocatedQuantity());
        match.setProgressNote(createRequest.progressNote());
        match.setCreatedBy(currentUser.getId());
        ResourceMatch saved = matchRepository.save(match);
        resource.setStatus(ResourceStatus.RESERVED);
        need.setStatus(NeedStatus.MATCHED);
        resourceRepository.save(resource);
        needRepository.save(need);
        auditLogService.log(request, currentUser, "RESOURCE_MATCH_CREATED", "RESOURCE_MATCH",
                saved.getId(), "资源ID=" + resource.getId() + " 匹配需求ID=" + need.getId());
        return ApiResponse.success(MatchResponse.from(saved, resource, need));
    }

    @PatchMapping("/matches/{matchId}/status")
    @Transactional
    public ApiResponse<MatchResponse> updateMatchStatus(HttpServletRequest request,
                                                        @PathVariable Long matchId,
                                                        @Valid @RequestBody UpdateMatchStatusRequest statusRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ORGANIZER, Role.ADMIN);
        ResourceMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "匹配记录不存在"));
        PublicResource resource = resourceRepository.findById(match.getResourceId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "公益资源不存在"));
        HelpNeed need = needRepository.findById(match.getNeedId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "帮扶需求不存在"));
        MatchStatus oldStatus = match.getStatus();
        validateMatchTransition(oldStatus, statusRequest.status());
        match.setStatus(statusRequest.status());
        match.setProgressNote(statusRequest.progressNote());
        applyMatchSideEffects(resource, need, statusRequest.status());
        ResourceMatch saved = matchRepository.save(match);
        resourceRepository.save(resource);
        needRepository.save(need);
        auditLogService.log(request, currentUser, "RESOURCE_MATCH_STATUS_UPDATED", "RESOURCE_MATCH",
                matchId, "状态从 " + oldStatus + " 变更为 " + statusRequest.status());
        return ApiResponse.success(MatchResponse.from(saved, resource, need));
    }

    private void validateMatchTransition(MatchStatus oldStatus, MatchStatus nextStatus) {
        if (oldStatus == nextStatus) {
            return;
        }
        boolean allowed = switch (oldStatus) {
            case MATCHED -> nextStatus == MatchStatus.ALLOCATED || nextStatus == MatchStatus.CANCELLED;
            case ALLOCATED -> nextStatus == MatchStatus.COMPLETED || nextStatus == MatchStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new BizException(HttpStatus.BAD_REQUEST, "资源匹配状态不允许从 " + oldStatus + " 变更为 " + nextStatus);
        }
    }

    private void applyMatchSideEffects(PublicResource resource, HelpNeed need, MatchStatus status) {
        switch (status) {
            case MATCHED -> {
                resource.setStatus(ResourceStatus.RESERVED);
                need.setStatus(NeedStatus.MATCHED);
            }
            case ALLOCATED -> {
                resource.setStatus(ResourceStatus.RESERVED);
                need.setStatus(NeedStatus.IN_PROGRESS);
            }
            case COMPLETED -> {
                resource.setStatus(ResourceStatus.USED);
                need.setStatus(NeedStatus.COMPLETED);
            }
            case CANCELLED -> {
                resource.setStatus(ResourceStatus.AVAILABLE);
                need.setStatus(NeedStatus.OPEN);
            }
        }
    }

    public record CreateResourceRequest(
            @NotBlank(message = "资源名称不能为空")
            @Size(max = 120, message = "资源名称最多120字")
            String name,
            @NotBlank(message = "资源类别不能为空")
            @Size(max = 60, message = "资源类别最多60字")
            String category,
            @NotBlank(message = "资源来源不能为空")
            @Size(max = 120, message = "资源来源最多120字")
            String source,
            @NotNull(message = "资源数量不能为空")
            @Min(value = 1, message = "资源数量至少为1")
            Integer quantity,
            @Size(max = 40, message = "单位最多40字")
            String unit,
            @Size(max = 200, message = "可用范围最多200字")
            String availableScope
    ) {
    }

    public record CreateNeedRequest(
            @NotBlank(message = "需求标题不能为空")
            @Size(max = 120, message = "需求标题最多120字")
            String title,
            @NotBlank(message = "需求对象不能为空")
            @Size(max = 120, message = "需求对象最多120字")
            String requester,
            @NotBlank(message = "需求内容不能为空")
            @Size(max = 1000, message = "需求内容最多1000字")
            String content,
            @NotNull(message = "需求数量不能为空")
            @Min(value = 1, message = "需求数量至少为1")
            Integer quantity,
            @Size(max = 40, message = "单位最多40字")
            String unit,
            @NotBlank(message = "需求地点不能为空")
            @Size(max = 200, message = "需求地点最多200字")
            String location,
            LocalDateTime requiredAt
    ) {
    }

    public record CreateMatchRequest(
            @NotNull(message = "资源ID不能为空")
            Long resourceId,
            @NotNull(message = "需求ID不能为空")
            Long needId,
            @NotNull(message = "分配数量不能为空")
            @Min(value = 1, message = "分配数量至少为1")
            Integer allocatedQuantity,
            @Size(max = 500, message = "进度说明最多500字")
            String progressNote
    ) {
    }

    public record UpdateMatchStatusRequest(
            @NotNull(message = "匹配状态不能为空")
            MatchStatus status,
            @Size(max = 500, message = "进度说明最多500字")
            String progressNote
    ) {
    }

    public record ResourceBoardResponse(List<ResourceResponse> resources,
                                        List<NeedResponse> needs,
                                        List<MatchResponse> matches) {
    }

    public record ResourceResponse(Long id,
                                   String name,
                                   String category,
                                   String source,
                                   Integer quantity,
                                   String unit,
                                   String availableScope,
                                   ResourceStatus status,
                                   Long createdBy,
                                   LocalDateTime createdAt) {
        static ResourceResponse from(PublicResource resource) {
            return new ResourceResponse(
                    resource.getId(),
                    resource.getName(),
                    resource.getCategory(),
                    resource.getSource(),
                    resource.getQuantity(),
                    resource.getUnit(),
                    resource.getAvailableScope(),
                    resource.getStatus(),
                    resource.getCreatedBy(),
                    resource.getCreatedAt()
            );
        }
    }

    public record NeedResponse(Long id,
                               String title,
                               String requester,
                               String content,
                               Integer quantity,
                               String unit,
                               String location,
                               LocalDateTime requiredAt,
                               NeedStatus status,
                               Long createdBy,
                               LocalDateTime createdAt) {
        static NeedResponse from(HelpNeed need) {
            return new NeedResponse(
                    need.getId(),
                    need.getTitle(),
                    need.getRequester(),
                    need.getContent(),
                    need.getQuantity(),
                    need.getUnit(),
                    need.getLocation(),
                    need.getRequiredAt(),
                    need.getStatus(),
                    need.getCreatedBy(),
                    need.getCreatedAt()
            );
        }
    }

    public record MatchResponse(Long id,
                                Long resourceId,
                                String resourceName,
                                Long needId,
                                String needTitle,
                                Integer allocatedQuantity,
                                String progressNote,
                                MatchStatus status,
                                Long createdBy,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt) {
        static MatchResponse from(ResourceMatch match, PublicResource resource, HelpNeed need) {
            return new MatchResponse(
                    match.getId(),
                    match.getResourceId(),
                    resource == null ? "未知资源" : resource.getName(),
                    match.getNeedId(),
                    need == null ? "未知需求" : need.getTitle(),
                    match.getAllocatedQuantity(),
                    match.getProgressNote(),
                    match.getStatus(),
                    match.getCreatedBy(),
                    match.getCreatedAt(),
                    match.getUpdatedAt()
            );
        }
    }
}
