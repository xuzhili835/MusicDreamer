package com.musicdreamer.login.dto;

import lombok.Data;

/**
 * 修改用户基础信息（F016）：只允许改 user 表已有字段。
 * user 表无性别等扩展列，故仅支持昵称与头像（邮箱涉及激活体系，不开放此处修改）。
 */
@Data
public class UpdateUserInfoDTO {

    private String nickname;

    private String avatar;
}
