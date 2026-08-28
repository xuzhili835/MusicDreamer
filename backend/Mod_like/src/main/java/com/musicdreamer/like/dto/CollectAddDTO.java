package com.musicdreamer.like.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 添加收藏请求体。 */
@Data
public class CollectAddDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;
}
