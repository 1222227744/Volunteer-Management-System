package com.volunteer.vms.file;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 文件对象存储端口：业务层只保存文件资产元数据，
 * 具体存到本地磁盘、MinIO 或云对象存储由实现类决定。
 */
public interface FileObjectStorage {
    StoredFile store(MultipartFile file, String storedName);

    Path resolve(String storagePath);
}
