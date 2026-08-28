package com.musicdreamer.login.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户信息视图（不含密码）。 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String email;

    private String nickname;

    private String avatar;

    private Integer role;

    private Integer singerStatus;

    private Integer status;

    private LocalDateTime createTime;
}
