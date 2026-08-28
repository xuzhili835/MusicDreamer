-- ============================================================
-- Music Dreamer 悦享音乐系统 数据库初始化脚本
-- MySQL 8.0 / utf8mb4 / InnoDB，共 19 张表
-- 首次部署由 MySQL 容器自动执行（挂载 ./init -> /docker-entrypoint-initdb.d）
-- ============================================================
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS music DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE music;

-- ---------- 用户域 ----------
CREATE TABLE user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username      VARCHAR(50)  NOT NULL COMMENT '用户名',
  password      VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
  email         VARCHAR(100) NOT NULL COMMENT '邮箱',
  nickname      VARCHAR(50)  NULL COMMENT '昵称',
  avatar        VARCHAR(255) NULL COMMENT '头像URL',
  role          TINYINT      NOT NULL DEFAULT 0 COMMENT '角色(0普通用户 1认证歌手 2管理员)',
  singer_status TINYINT      NOT NULL DEFAULT 0 COMMENT '歌手认证(0未申请 1审核中 2通过 3驳回)',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态(0禁用 1正常)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_role (role),
  KEY idx_user_singer_status (singer_status),
  KEY idx_user_status (status),
  KEY idx_user_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE singer_profile (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  user_id          BIGINT NOT NULL COMMENT '用户ID(唯一)',
  stage_name       VARCHAR(50) NULL COMMENT '艺名',
  bio              TEXT NULL COMMENT '简介',
  background_image VARCHAR(255) NULL COMMENT '背景图URL',
  verified_date    DATETIME NULL COMMENT '认证通过时间',
  fans_count       INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  total_plays      BIGINT NOT NULL DEFAULT 0 COMMENT '累计播放量',
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sprofile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌手资料表';

CREATE TABLE singer_application (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  user_id          BIGINT NOT NULL COMMENT '申请用户ID',
  real_name        VARCHAR(50) NOT NULL COMMENT '真实姓名',
  id_card          VARCHAR(255) NOT NULL COMMENT '身份证号(加密存储)',
  id_card_front    VARCHAR(255) NOT NULL COMMENT '证件正面照URL',
  id_card_back     VARCHAR(255) NOT NULL COMMENT '证件反面照URL',
  artist_statement TEXT NULL COMMENT '艺人声明',
  status           TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态(1审核中 2通过 3驳回)',
  reject_reason    VARCHAR(500) NULL COMMENT '驳回原因',
  auditor_id       BIGINT NULL COMMENT '审核人ID',
  audit_time       DATETIME NULL COMMENT '审核时间',
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sapp_user (user_id),
  KEY idx_sapp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌手认证申请表';

-- ---------- 音乐域 ----------
CREATE TABLE song (
  id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '歌曲ID',
  name                VARCHAR(100) NOT NULL COMMENT '歌曲名称',
  singer_id           BIGINT NOT NULL COMMENT '歌手用户ID',
  singer_name         VARCHAR(64) NULL COMMENT '导入歌曲的原始歌手名（优先于注册歌手昵称展示）',
  album               VARCHAR(100) NULL COMMENT '专辑名称',
  style               VARCHAR(50) NULL COMMENT '风格(流行/摇滚/民谣等)',
  language            VARCHAR(20) NULL COMMENT '语言(国语/英语/日语等)',
  duration            INT NOT NULL DEFAULT 0 COMMENT '时长(秒)',
  file_url            VARCHAR(255) NOT NULL COMMENT '音频文件URL',
  cover_url           VARCHAR(255) NULL COMMENT '封面图片URL',
  lyric_url           VARCHAR(255) NULL COMMENT '歌词文件URL',
  file_format         VARCHAR(10) NOT NULL COMMENT '格式(MP3/FLAC/AAC)',
  play_count          BIGINT NOT NULL DEFAULT 0 COMMENT '播放次数(冗余热计数)',
  collect_count       INT NOT NULL DEFAULT 0 COMMENT '收藏次数',
  status              TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0已下架 1审核中 2已发布,审核制)',
  reject_reason       TEXT NULL COMMENT '审核驳回原因',
  auditor_id          BIGINT NULL COMMENT '审核人ID',
  audit_time          DATETIME NULL COMMENT '审核时间',
  source_url          VARCHAR(512) NULL COMMENT '下载来源URL(查重)',
  volume_gain         DOUBLE NULL COMMENT '响度补偿增益(dB)',
  integrated_loudness DOUBLE NULL COMMENT '原始综合响度(LUFS)',
  create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_song_name (name),
  KEY idx_song_singer (singer_id),
  KEY idx_song_style (style),
  KEY idx_song_language (language),
  KEY idx_song_status (status),
  KEY idx_song_source (source_url),
  KEY idx_song_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲表';

CREATE TABLE song_version (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  song_id     BIGINT NOT NULL COMMENT '歌曲ID',
  version     INT NOT NULL DEFAULT 1 COMMENT '版本号',
  file_url    VARCHAR(255) NOT NULL COMMENT '音频文件URL',
  file_format VARCHAR(10) NOT NULL COMMENT '音频格式',
  file_size   BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  duration    INT NOT NULL DEFAULT 0 COMMENT '时长(秒)',
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '版本状态(0已废弃 1生效中 2审核中)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  audit_time  DATETIME NULL COMMENT '审核时间',
  auditor_id  BIGINT NULL COMMENT '审核人ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sver (song_id, version),
  KEY idx_sver_status (song_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲版本表';

CREATE TABLE play_history (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  user_id       BIGINT NOT NULL COMMENT '用户ID',
  song_id       BIGINT NOT NULL COMMENT '歌曲ID',
  play_duration INT NOT NULL DEFAULT 0 COMMENT '播放时长(秒)',
  play_complete TINYINT NOT NULL DEFAULT 0 COMMENT '是否播完(0否 1是)',
  device_type   VARCHAR(20) NULL COMMENT '设备类型(web/mobile/app)',
  ip_address    VARCHAR(50) NULL COMMENT '播放IP',
  played_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '播放时间',
  PRIMARY KEY (id),
  KEY idx_ph_user (user_id, played_at),
  KEY idx_ph_song (song_id),
  KEY idx_ph_time (played_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放历史表';

CREATE TABLE media_task (
  id          INT NOT NULL AUTO_INCREMENT,
  task_type   VARCHAR(20) NOT NULL COMMENT 'DOWNLOAD/TRANSCRIBE/LOUDNESS/SUBTITLE/MODEL_DOWNLOAD/TOOL_UPDATE',
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  progress    INT NOT NULL DEFAULT 0 COMMENT '进度(0-100)',
  stage       VARCHAR(200) NULL COMMENT '当前阶段描述(中文,直接展示)',
  source_url  VARCHAR(512) NULL COMMENT '来源URL',
  song_id     BIGINT NULL COMMENT '关联歌曲ID',
  operator    BIGINT NULL COMMENT '发起用户ID',
  error       VARCHAR(1000) NULL COMMENT '转译后的用户可读错误',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL COMMENT '完成时间',
  PRIMARY KEY (id),
  KEY idx_task_status (status),
  KEY idx_task_song (song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体任务表';

CREATE TABLE song_similarity (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  song_id      BIGINT NOT NULL COMMENT '歌曲ID',
  sim_song_id  BIGINT NOT NULL COMMENT '相似歌曲ID',
  score        DOUBLE NOT NULL COMMENT '相似度(0-1,调整余弦)',
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '重算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sim (song_id, sim_song_id),
  KEY idx_sim_score (song_id, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲相似度矩阵(协同过滤离线计算)';

-- ---------- 歌单域 ----------
CREATE TABLE playlist (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '创建用户ID',
  name        VARCHAR(100) NOT NULL COMMENT '歌单名称',
  description VARCHAR(500) NULL COMMENT '歌单描述',
  cover_url   VARCHAR(255) NULL COMMENT '封面图片URL',
  is_public   TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开(0私有 1公开)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pl_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单表';

CREATE TABLE playlist_song (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  playlist_id BIGINT NOT NULL COMMENT '歌单ID',
  song_id     BIGINT NOT NULL COMMENT '歌曲ID',
  sort_order  INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ps (playlist_id, song_id),
  KEY idx_ps_order (playlist_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单歌曲关联表';

CREATE TABLE user_favorite_playlist (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  playlist_id BIGINT NOT NULL COMMENT '歌单ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ufp (user_id, playlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏歌单表';

CREATE TABLE collect (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  song_id     BIGINT NOT NULL COMMENT '歌曲ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collect (user_id, song_id),
  KEY idx_collect_song (song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲收藏表';

-- ---------- 社交域 ----------
CREATE TABLE comment (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  song_id     BIGINT NOT NULL COMMENT '歌曲ID',
  user_id     BIGINT NOT NULL COMMENT '评论用户ID',
  parent_id   BIGINT NULL COMMENT '父评论ID(顶层评论为NULL)',
  content     TEXT NOT NULL COMMENT '评论内容',
  like_count  INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0删除 1正常 2审核中)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cmt_song (song_id, create_time),
  KEY idx_cmt_user (user_id, create_time),
  KEY idx_cmt_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE dynamic (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  user_id       BIGINT NOT NULL COMMENT '发布用户ID(歌手)',
  content       TEXT NULL COMMENT '文字内容',
  media_type    TINYINT NOT NULL DEFAULT 0 COMMENT '媒体类型(0纯文字 1图片 2视频 3混合)',
  media_urls    JSON NULL COMMENT '媒体文件URL数组',
  like_count    INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数',
  share_count   INT NOT NULL DEFAULT 0 COMMENT '分享数',
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0删除 1正常 2审核中)',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dyn_user (user_id, create_time),
  KEY idx_dyn_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌手动态表';

CREATE TABLE user_follow (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  follower_id BIGINT NOT NULL COMMENT '关注者ID',
  followee_id BIGINT NOT NULL COMMENT '被关注者ID(歌手)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_follow (follower_id, followee_id),
  KEY idx_follow_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注表';

-- ---------- 运营域 ----------
CREATE TABLE report (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  reporter_id   BIGINT NOT NULL COMMENT '举报人ID',
  target_type   TINYINT NOT NULL COMMENT '对象类型(1歌曲 2评论 3歌单 4动态)',
  target_id     BIGINT NOT NULL COMMENT '对象ID',
  reason        TINYINT NOT NULL COMMENT '原因(1侵权 2违规内容 3垃圾信息 4其他)',
  description   TEXT NULL COMMENT '详细描述',
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '处理状态(1待处理 2已处理 3已驳回)',
  handler_id    BIGINT NULL COMMENT '处理人ID',
  handle_result TEXT NULL COMMENT '处理结果说明',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handle_time   DATETIME NULL COMMENT '处理时间',
  PRIMARY KEY (id),
  KEY idx_rpt_reporter (reporter_id, create_time),
  KEY idx_rpt_target (target_type, target_id),
  KEY idx_rpt_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

CREATE TABLE operation_log (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NULL COMMENT '操作用户ID',
  username    VARCHAR(50) NULL COMMENT '操作用户名',
  operation   VARCHAR(50) NOT NULL COMMENT '操作类型(LOGIN/UPLOAD/DELETE/EDIT等)',
  method      VARCHAR(10) NULL COMMENT '请求方法',
  params      TEXT NULL COMMENT '请求参数',
  ip          VARCHAR(50) NULL COMMENT '操作IP',
  location    VARCHAR(100) NULL COMMENT '操作地点',
  browser     VARCHAR(100) NULL COMMENT '浏览器类型',
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态(0失败 1成功)',
  error_msg   VARCHAR(1000) NULL COMMENT '错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oplog_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

CREATE TABLE album (
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

CREATE TABLE album_song (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  album_id    BIGINT NOT NULL COMMENT '专辑ID',
  song_id     BIGINT NOT NULL COMMENT '歌曲ID',
  sort_order  INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_album_song (album_id, song_id),
  KEY idx_as_song (song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专辑歌曲关联表';

CREATE TABLE user_favorite_album (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  album_id    BIGINT NOT NULL COMMENT '专辑ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_album (user_id, album_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏专辑表';

CREATE TABLE sys_setting (
  cfg_key    VARCHAR(100) NOT NULL COMMENT '配置键',
  cfg_value  TEXT NOT NULL COMMENT '配置值(JSON)',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (cfg_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 默认管理员：admin / admin123（BCrypt，$2b 前缀 Spring Security 兼容）。
-- 不再种示例账号/示例歌曲/示例歌单：演示数据一律由使用者自行创建
INSERT INTO user (username, password, email, nickname, role, singer_status, status) VALUES
('admin', '$2b$10$DsE1LUw7C9G3AMVcnNSP.OrZvYg0sTYZAme8h1v0/1SuzzmaxWdYG',
 'admin@musicdream.com', '系统管理员', 2, 0, 1);

INSERT INTO sys_setting (cfg_key, cfg_value) VALUES
('tools_path', '""'),
('download_proxy', '""'),
('cookies_path', '""'),
('volume_target_lufs', '"-16"'),
('whisper_model', '"small"'),
-- storage_root 必须留空：空值回退各服务 yml 默认（本机 backend/data、
-- Docker 用 STORAGE_ROOT 环境变量）。曾种 "/data" 导致 Windows 上写进
-- 盘符根 D:\data 而静态服务读 backend/data，音频/封面全部 404
('storage_root', '""'),
('alert_email_to', '""');
