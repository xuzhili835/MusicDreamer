-- V1 2026-08-23 专辑模块建表
-- 幂等写法（IF NOT EXISTS）：与 init.sql 手工初始化过的库共存——
-- 已由 init.sql 建过的表此脚本空跑，Flyway 记录版本后不再重复执行。
CREATE TABLE IF NOT EXISTS album (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '发布歌手/管理员ID',
  name        VARCHAR(200) NOT NULL COMMENT '专辑名称',
  description TEXT NULL COMMENT '专辑简介',
  cover_url   VARCHAR(500) NULL COMMENT '封面图片URL',
  is_public   TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开(0未发布 1已发布)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_album_user (user_id),
  KEY idx_album_public (is_public)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专辑表';

CREATE TABLE IF NOT EXISTS album_song (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  album_id    BIGINT NOT NULL COMMENT '专辑ID',
  song_id     BIGINT NOT NULL COMMENT '歌曲ID',
  sort_order  INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_album_song (album_id, song_id),
  KEY idx_as_song (song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专辑歌曲关联表';

CREATE TABLE IF NOT EXISTS user_favorite_album (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  album_id    BIGINT NOT NULL COMMENT '专辑ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_album (user_id, album_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏专辑表';
