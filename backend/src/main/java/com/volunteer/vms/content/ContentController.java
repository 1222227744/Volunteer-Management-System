package com.volunteer.vms.content;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.file.FileStorageService;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口层：实现 SRS FR-06 内容发布、审核与展示。
 * 公告和通知能力由公告模块、通知模块分别承接。
 */
@RestController
@RequestMapping("/api/contents")
public class ContentController {
    private final ContentPostRepository contentPostRepository;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    public ContentController(ContentPostRepository contentPostRepository,
                             AuditLogService auditLogService,
                             FileStorageService fileStorageService) {
        this.contentPostRepository = contentPostRepository;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ApiResponse<ContentResponse> submit(HttpServletRequest request,
                                               @Valid @RequestBody SubmitContentRequest submitRequest) {
        User currentUser = AuthUtils.currentUser(request);
        ContentPost contentPost = new ContentPost();
        contentPost.setUserId(currentUser.getId());
        contentPost.setTitle(submitRequest.title());
        contentPost.setContent(submitRequest.content());
        contentPost.setImageFileId(submitRequest.imageFileId());
        contentPost.setStatus(ContentStatus.PENDING);
        ContentPost saved = contentPostRepository.save(contentPost);
        if (saved.getImageFileId() != null) {
            fileStorageService.bindBusiness(saved.getImageFileId(), "CONTENT", saved.getId());
        }
        auditLogService.log(
                request,
                currentUser,
                "CONTENT_SUBMITTED",
                "CONTENT",
                saved.getId(),
                "提交内容: " + submitRequest.title()
        );
        return ApiResponse.success(ContentResponse.from(saved));
    }

    @GetMapping("/my")
    public ApiResponse<List<ContentResponse>> myContents(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<ContentResponse> data = contentPostRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(ContentResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @GetMapping("/approved")
    public ApiResponse<List<ContentResponse>> approvedContents() {
        List<ContentResponse> data = contentPostRepository.findByStatusOrderByCreatedAtDesc(ContentStatus.APPROVED)
                .stream()
                .map(ContentResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @GetMapping("/pending")
    public ApiResponse<List<ContentResponse>> pendingContents(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        List<ContentResponse> data = contentPostRepository.findByStatusOrderByCreatedAtDesc(ContentStatus.PENDING)
                .stream()
                .map(ContentResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @PatchMapping("/{contentId}/review")
    public ApiResponse<Void> review(HttpServletRequest request,
                                    @PathVariable Long contentId,
                                    @Valid @RequestBody ReviewContentRequest reviewRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        if (reviewRequest.status() == ContentStatus.PENDING) {
            throw new BizException(HttpStatus.BAD_REQUEST, "审核状态不能为PENDING");
        }
        ContentPost contentPost = contentPostRepository.findById(contentId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "内容不存在"));
        contentPost.setStatus(reviewRequest.status());
        contentPost.setReviewComment(reviewRequest.reviewComment());
        contentPost.setReviewedAt(LocalDateTime.now());
        contentPostRepository.save(contentPost);
        auditLogService.log(
                request,
                currentUser,
                "CONTENT_REVIEWED",
                "CONTENT",
                contentId,
                "审核结果=" + reviewRequest.status() + ", 审核意见=" + (reviewRequest.reviewComment() == null ? "" : reviewRequest.reviewComment())
        );
        return ApiResponse.success();
    }

    public record SubmitContentRequest(
            @NotBlank(message = "标题不能为空")
            @Size(max = 120, message = "标题最多120字")
            String title,
            @NotBlank(message = "内容不能为空")
            @Size(max = 4000, message = "内容最多4000字")
            String content,
            Long imageFileId
    ) {
    }

    public record ReviewContentRequest(
            @NotNull(message = "审核状态不能为空")
            ContentStatus status,
            @Size(max = 1000, message = "审核意见最多1000字")
            String reviewComment
    ) {
    }

    public record ContentResponse(Long id,
                                  Long userId,
                                  String title,
                                  String content,
                                  Long imageFileId,
                                  ContentStatus status,
                                  String reviewComment,
                                  LocalDateTime createdAt,
                                  LocalDateTime reviewedAt) {
        static ContentResponse from(ContentPost post) {
            return new ContentResponse(
                    post.getId(),
                    post.getUserId(),
                    post.getTitle(),
                    post.getContent(),
                    post.getImageFileId(),
                    post.getStatus(),
                    post.getReviewComment(),
                    post.getCreatedAt(),
                    post.getReviewedAt()
            );
        }
    }
}
