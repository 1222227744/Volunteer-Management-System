package com.volunteer.vms.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {
    List<FileAsset> findByBusinessTypeAndBusinessIdOrderByCreatedAtDesc(String businessType, Long businessId);
}
