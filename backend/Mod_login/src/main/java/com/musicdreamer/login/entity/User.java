package com.musicdreamer.login.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户表（init.sql：user）。 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文，任何接口不回显。 */
    @JsonIgnore
    private String password;

    private String email;

    private String nickname;

    private String avatar;

    /** 微信小程序 openid（uniapp 端登录/绑定；null=未绑定）。 */
    private String wxOpenid;

    /** 0 普通用户 1 认证歌手 2 管理员。 */
    private Integer role;

    /** 歌手认证：0 未申请 1 审核中 2 通过 3 驳回。 */
    private Integer singerStatus;

    /** 账号状态：0 禁用 1 正常。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
