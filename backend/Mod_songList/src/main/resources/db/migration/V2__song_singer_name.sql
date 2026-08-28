-- V2：song 表增加 singer_name 自由文本列。
-- 场景：管理员从外站导入他人作品时填写原始歌手名；所有展示口径
-- （卡片/详情/最近播放/榜单/管理列表）优先取该列，为空时回落注册歌手昵称。
ALTER TABLE song ADD COLUMN singer_name VARCHAR(64) NULL COMMENT '导入歌曲的原始歌手名（优先于注册歌手昵称展示）' AFTER singer_id;
