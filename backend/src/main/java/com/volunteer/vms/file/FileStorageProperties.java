package com.volunteer.vms.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vms.files")
public record FileStorageProperties(
        String storageDir,
        long imageMaxBytes,
        long attachmentMaxBytes
) {
}
