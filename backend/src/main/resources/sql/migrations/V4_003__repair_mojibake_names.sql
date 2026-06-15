-- 修复历史库中由于 UTF-8 中文被 Latin-1 错读后写入的默认账号昵称与名称快照。
-- 使用 CONVERT(CAST(... AS BINARY) USING latin1) 生成历史错误形态，避免在源码中继续保存乱码字面量。

SET @admin_name := '系统管理员';
SET @organizer_name := '组织方账号';
SET @admin_mojibake := CONVERT(CAST(@admin_name AS BINARY) USING latin1);
SET @organizer_mojibake := CONVERT(CAST(@organizer_name AS BINARY) USING latin1);

UPDATE users
SET display_name = @admin_name
WHERE username IN ('admin', 'admin@example.com')
  AND display_name = @admin_mojibake;

UPDATE users
SET display_name = @organizer_name
WHERE username IN ('organizer', 'organizer@example.com')
  AND display_name = @organizer_mojibake;

UPDATE system_configs
SET updated_by_name = CASE updated_by_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE updated_by_name
END
WHERE updated_by_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE system_configs
SET config_name = '样例数据加载控制',
    description = '控制系统是否允许启动期写入预置业务数据。'
WHERE config_key = 'demo.data.enabled';

UPDATE system_configs
SET config_name = '基础账号创建控制',
    description = '控制系统是否允许启动期创建基础管理账号。'
WHERE config_key = 'bootstrap.accounts.enabled';

UPDATE incident_records
SET created_by_name = CASE created_by_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE created_by_name
END
WHERE created_by_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE audit_logs
SET operator_name = CASE operator_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE operator_name
END
WHERE operator_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE activity_attendance_corrections
SET corrected_by_name = CASE corrected_by_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE corrected_by_name
END
WHERE corrected_by_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE service_record_corrections
SET requester_name = CASE requester_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE requester_name
END,
    reviewed_by_name = CASE reviewed_by_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE reviewed_by_name
END
WHERE requester_name IN (@admin_mojibake, @organizer_mojibake)
   OR reviewed_by_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE file_assets
SET uploader_name = CASE uploader_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE uploader_name
END
WHERE uploader_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE donation_orders
SET donor_name = CASE donor_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE donor_name
END
WHERE donor_name IN (@admin_mojibake, @organizer_mojibake);

UPDATE donations
SET donor_name = CASE donor_name
    WHEN @admin_mojibake THEN @admin_name
    WHEN @organizer_mojibake THEN @organizer_name
    ELSE donor_name
END
WHERE donor_name IN (@admin_mojibake, @organizer_mojibake);
