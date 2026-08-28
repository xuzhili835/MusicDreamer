-- 微信小程序登录（uniapp 端）：用户绑定 openid。
-- code 由前端 wx.login 获取，后端 jscode2session 换 openid；首次登录自动建号。
-- 占位邮箱 wx_{openid}@wx.placeholder 满足 email NOT NULL + 唯一约束，openid 唯一故不冲突。
ALTER TABLE user
    ADD COLUMN wx_openid VARCHAR(64) NULL COMMENT '微信小程序openid' AFTER avatar,
    ADD UNIQUE INDEX uk_user_wx_openid (wx_openid);
