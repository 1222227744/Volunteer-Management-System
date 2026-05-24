package com.volunteer.vms.file;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.service.ServiceRecord;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final ServiceRecordRepository serviceRecordRepository;

    public FileController(FileAssetRepository fileAssetRepository,
                          FileStorageService fileStorageService,
                          AuditLogService auditLogService,
                          ServiceRecordRepository serviceRecordRepository) {
        this.fileAssetRepository = fileAssetRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.serviceRecordRepository = serviceRecordRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileAssetResponse> upload(HttpServletRequest request,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam FileCategory category,
                                                 @RequestParam(required = false) String businessType,
                                                 @RequestParam(required = false) Long businessId) {
        User currentUser = AuthUtils.currentUser(request);
        FileAsset asset = fileStorageService.store(file, category, businessType, businessId, currentUser, request);
        auditLogService.log(
                request,
                currentUser,
                "FILE_UPLOADED",
                "FILE",
                asset.getId(),
                "上传文件: " + asset.getOriginalName() + ", category=" + asset.getCategory()
        );
        return ApiResponse.success(FileAssetResponse.from(asset));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<InputStreamResource> download(HttpServletRequest request, @PathVariable Long fileId) throws IOException {
        User currentUser = AuthUtils.currentUser(request);
        FileAsset asset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "文件不存在"));
        ensureCanAccess(currentUser, asset);
        Path path = fileStorageService.resolvePath(asset);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(asset.getContentType()));
        headers.setContentLength(asset.getFileSize());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(asset.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    @GetMapping
    public ApiResponse<List<FileAssetResponse>> listByBusiness(HttpServletRequest request,
                                                               @RequestParam String businessType,
                                                               @RequestParam Long businessId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        return ApiResponse.success(fileAssetRepository.findByBusinessTypeAndBusinessIdOrderByCreatedAtDesc(
                        businessType.toUpperCase(), businessId)
                .stream()
                .map(FileAssetResponse::from)
                .toList());
    }

    private void ensureCanAccess(User currentUser, FileAsset asset) {
        if (currentUser.getRole() == Role.ADMIN || currentUser.getId().equals(asset.getUploaderId())) {
            return;
        }
        if (asset.getBusinessType() == null) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权访问该文件");
        }
        if ("CONTENT".equals(asset.getBusinessType()) || "ACTIVITY".equals(asset.getBusinessType())) {
            return;
        }
        if ("SERVICE_RECORD".equals(asset.getBusinessType()) && asset.getBusinessId() != null) {
            ServiceRecord record = serviceRecordRepository.findById(asset.getBusinessId())
                    .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "服务记录不存在"));
            if (currentUser.getId().equals(record.getUserId())) {
                return;
            }
        }
        throw new BizException(HttpStatus.FORBIDDEN, "无权访问该文件");
    }

    public record FileAssetResponse(Long id,
                                    String originalName,
                                    String contentType,
                                    Long fileSize,
                                    FileCategory category,
                                    String businessType,
                                    Long businessId,
                                    Long uploaderId,
                                    String uploaderName,
                                    LocalDateTime createdAt,
                                    String url) {
        static FileAssetResponse from(FileAsset asset) {
            return new FileAssetResponse(
                    asset.getId(),
                    asset.getOriginalName(),
                    asset.getContentType(),
                    asset.getFileSize(),
                    asset.getCategory(),
                    asset.getBusinessType(),
                    asset.getBusinessId(),
                    asset.getUploaderId(),
                    asset.getUploaderName(),
                    asset.getCreatedAt(),
                    "/api/files/" + asset.getId()
            );
        }
    }
}
