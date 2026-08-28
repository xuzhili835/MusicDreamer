package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 举报管理列表项（含举报人昵称）。 */
@Data
public class ReportItemVO {

    private Long id;

    private Long reporterId;

    private String reporterName;

    private Integer targetType;

    private Long targetId;

    private Integer reason;

    private String description;

    private Integer status;

    private Long handlerId;

    private String handleResult;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;
}
