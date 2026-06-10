package com.volunteer.vms.file;

import com.volunteer.vms.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalFileObjectStorage implements FileObjectStorage {
    private final FileStorageProperties properties;

    public LocalFileObjectStorage(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFile store(MultipartFile file, String storedName) {
        Path storageRoot = storageRoot();
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件存储路径不合法");
        }
        try {
            Files.createDirectories(storageRoot);
            file.transferTo(target);
            return new StoredFile(target);
        } catch (IOException ex) {
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败");
        }
    }

    @Override
    public Path resolve(String storagePath) {
        Path storageRoot = storageRoot();
        Path target = Path.of(storagePath).toAbsolutePath().normalize();
        if (!target.startsWith(storageRoot) || !Files.exists(target) || !Files.isRegularFile(target)) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件不存在或已被移除");
        }
        return target;
    }

    private Path storageRoot() {
        return Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }
}
