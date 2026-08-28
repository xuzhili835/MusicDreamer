package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 举报表。target_type：1 歌曲 2 评论 3 歌单 4 动态；status：1 待处理 2 已处理 3 已驳回。 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    private Integer targetType;

    private Long targetId;

    /** 原因：1 侵权 2 违规内容 3 垃圾信息 4 其他。 */
    private Integer reason;

    private String description;

    /** 1 待处理 / 2 已处理 / 3 已驳回。 */
    private Integer status;

    private Long handlerId;

    private String handleResult;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;
}
