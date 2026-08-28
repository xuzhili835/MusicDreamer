package com.musicdreamer.login.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌手认证申请表（init.sql：singer_application）。 */
@Data
@TableName("singer_application")
public class SingerApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String idCard;

    private String idCardFront;

    private String idCardBack;

    private String artistStatement;

    /** 1 审核中 2 通过 3 驳回。 */
    private Integer status;

    private String rejectReason;

    private Long auditorId;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;
}
