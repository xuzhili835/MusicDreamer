package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 提交举报。 */
@Data
public class ReportSubmitDTO {

    /** 1 歌曲 2 评论 3 歌单 4 动态。 */
    @NotNull(message = "targetType 不能为空")
    private Integer targetType;

    @NotNull(message = "targetId 不能为空")
    private Long targetId;

    /** 1 侵权 2 违规内容 3 垃圾信息 4 其他。 */
    @NotNull(message = "reason 不能为空")
    private Integer reason;

    private String description;
}
