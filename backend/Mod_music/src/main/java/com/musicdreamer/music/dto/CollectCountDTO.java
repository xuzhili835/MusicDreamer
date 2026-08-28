package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 收藏计数增减（Mod_like 经 Feign 调用，delta 可为负，下限 0）。 */
@Data
public class CollectCountDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    @NotNull(message = "delta 不能为空")
    private Integer delta;
}
