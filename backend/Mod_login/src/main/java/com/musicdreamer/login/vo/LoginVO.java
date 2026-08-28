package com.musicdreamer.login.vo;

import lombok.Builder;
import lombok.Data;

/** 登录成功返回（契约见设计文档 12.2）。 */
@Data
@Builder
public class LoginVO {

    private String token;

    private Long userId;

    private String nickname;

    private String avatar;

    /** 0 用户 1 歌手 2 管理员。 */
    private Integer role;
}
