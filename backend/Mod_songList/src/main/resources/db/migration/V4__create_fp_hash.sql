-- V4 2026-08-26 听歌识曲 landmark 指纹倒排索引（设计文档二期）
-- 幂等写法（IF NOT EXISTS）。hash = (f1<<10|f2)<<6|Δt（30 位），
-- pos 为锚点帧号（hop 512/11025Hz ≈ 46.4ms/帧，SMALLINT 上限 32767 帧约 25 分钟）
CREATE TABLE IF NOT EXISTS fp_hash (
  hash    INT UNSIGNED NOT NULL,
  song_id BIGINT       NOT NULL,
  pos     SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (hash, song_id, pos)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='landmark 指纹倒排（主键即聚簇索引，按 hash 范围扫描）';
