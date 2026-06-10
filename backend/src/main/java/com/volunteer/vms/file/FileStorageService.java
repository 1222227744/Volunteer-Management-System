package com.volunteer.vms.file;

import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx", "zip", "rar");
    private static final Map<String, Set<String>> CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")),
            Map.entry("rar", Set.of("application/vnd.rar", "application/x-rar-compressed", "application/octet-stream"))
    );

    private final FileAssetRepository fileAssetRepository;
    private final FileStorageProperties properties;
    private final FileObjectStorage objectStorage;

    public FileStorageService(FileAssetRepository fileAssetRepository,
                              FileStorageProperties properties,
                              FileObjectStorage objectStorage) {
        this.fileAssetRepository = fileAssetRepository;
        this.properties = properties;
        this.objectStorage = objectStorage;
    }

    public FileAsset store(MultipartFile file,
                           FileCategory category,
                           String businessType,
                           Long businessId,
                           User uploader,
                           HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        if (originalName.contains("..")) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件名不合法");
        }
        String extension = resolveExtension(originalName);
        validateCategoryAndSize(file, category, extension);
        validateContentType(file, extension);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        StoredFile storedFile = objectStorage.store(file, storedName);

        FileAsset asset = new FileAsset();
        asset.setOriginalName(originalName);
        asset.setStoredName(storedName);
        asset.setStoragePath(storedFile.path().toString());
        asset.setContentType(resolveContentType(file, extension));
        asset.setFileSize(file.getSize());
        asset.setCategory(category);
        asset.setBusinessType(normalizeBusinessType(businessType));
        asset.setBusinessId(businessId);
        asset.setUploaderId(uploader.getId());
        asset.setUploaderName(uploader.getDisplayName());
        asset.setIpAddress(resolveClientIp(request));
        return fileAssetRepository.save(asset);
    }

    public Path resolvePath(FileAsset asset) {
        return objectStorage.resolve(asset.getStoragePath());
    }

    public FileAsset bindBusiness(Long fileId, String businessType, Long businessId) {
        FileAsset asset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "文件不存在"));
        asset.setBusinessType(normalizeBusinessType(businessType));
        asset.setBusinessId(businessId);
        return fileAssetRepository.save(asset);
    }

    private void validateCategoryAndSize(MultipartFile file, FileCategory category, String extension) {
        if (category == FileCategory.IMAGE) {
            if (!IMAGE_EXTENSIONS.contains(extension)) {
                throw new BizException(HttpStatus.BAD_REQUEST, "图片仅支持 jpg、jpeg、png、gif");
            }
            if (file.getSize() > properties.imageMaxBytes()) {
                throw new BizException(HttpStatus.BAD_REQUEST, "图片大小不能超过5MB");
            }
            return;
        }
        if (!ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "附件仅支持 pdf、docx、xlsx、pptx、zip、rar");
        }
        if (file.getSize() > properties.attachmentMaxBytes()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "附件大小不能超过50MB");
        }
    }

    private void validateContentType(MultipartFile file, String extension) {
        byte[] header = new byte[12];
        int read;
        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.read(header);
        } catch (IOException ex) {
            throw new BizException(HttpStatus.BAD_REQUEST, "无法读取文件内容");
        }
        if (!matchesMagicNumber(extension, header, read)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件真实类型与扩展名不一致");
        }
        String contentType = resolveContentType(file, extension);
        Set<String> allowedContentTypes = CONTENT_TYPES.getOrDefault(extension, Set.of());
        if (!allowedContentTypes.isEmpty() && !allowedContentTypes.contains(contentType)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件 Content-Type 不被允许");
        }
    }

    private boolean matchesMagicNumber(String extension, byte[] header, int read) {
        if (read < 4) {
            return false;
        }
        return switch (extension) {
            case "jpg", "jpeg" -> unsigned(header[0]) == 0xFF && unsigned(header[1]) == 0xD8 && unsigned(header[2]) == 0xFF;
            case "png" -> read >= 8
                    && unsigned(header[0]) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                    && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
            case "gif" -> read >= 6
                    && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38
                    && (header[4] == 0x37 || header[4] == 0x39) && header[5] == 0x61;
            case "pdf" -> header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;
            case "docx", "xlsx", "pptx", "zip" -> header[0] == 0x50 && header[1] == 0x4B;
            case "rar" -> read >= 7
                    && header[0] == 0x52 && header[1] == 0x61 && header[2] == 0x72 && header[3] == 0x21
                    && header[4] == 0x1A && header[5] == 0x07 && (header[6] == 0x00 || header[6] == 0x01);
            default -> false;
        };
    }

    private String resolveExtension(String originalName) {
        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件必须包含扩展名");
        }
        return originalName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return switch (extension) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "pdf" -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "zip" -> "application/zip";
                case "rar" -> "application/vnd.rar";
                default -> "application/octet-stream";
            };
        }
        return contentType;
    }

    private String normalizeBusinessType(String businessType) {
        return businessType == null || businessType.isBlank() ? null : businessType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}
