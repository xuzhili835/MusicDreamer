-- 在线词库命中信息（bug8：管理员复盘歌词时间戳，可跳转 LRCLIB 原始记录对照）
ALTER TABLE song
    ADD COLUMN lyric_source_id BIGINT NULL COMMENT '在线词库记录id（LRCLIB）' AFTER lyric_url,
    ADD COLUMN lyric_source_url VARCHAR(255) NULL COMMENT '在线词库原始记录链接' AFTER lyric_source_id;
