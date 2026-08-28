package com.musicdreamer.login.vo;

import lombok.Data;

/** 用户简要信息：供其他服务 Feign 内部调用（/api/v1/user/internal/{id}）。 */
@Data
public class UserBriefVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private Integer role;
}
