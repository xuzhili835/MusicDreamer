package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 发表评论（两级结构，parentId 可空）。 */
@Data
public class CommentAddDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    /** 父评论 ID，顶层评论不传。 */
    private Long parentId;

    @NotBlank(message = "评论内容不能为空")
    private String content;
}
