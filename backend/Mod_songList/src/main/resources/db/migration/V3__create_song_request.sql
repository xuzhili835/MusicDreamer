-- V3 2026-08-26 求歌申请表（听歌识曲一期）
-- 幂等写法（IF NOT EXISTS）：与 init.sql 手工初始化过的库共存
CREATE TABLE IF NOT EXISTS song_request (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  user_id         BIGINT NOT NULL COMMENT '申请人ID',
  title           VARCHAR(200) NOT NULL COMMENT '歌名',
  artist          VARCHAR(200) NULL COMMENT '歌手',
  cover_url       VARCHAR(500) NULL COMMENT '封面图（识曲/外置识别带回）',
  source          TINYINT NOT NULL DEFAULT 0 COMMENT '来源(0手动 1识曲 2外置识别)',
  status          TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0待处理 1已入库 2已拒绝)',
  result_song_id  BIGINT NULL COMMENT '入库后的歌曲ID',
  reject_reason   VARCHAR(200) NULL COMMENT '拒绝理由',
  handled_by      BIGINT NULL COMMENT '处理人ID',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at      DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求歌申请表';
