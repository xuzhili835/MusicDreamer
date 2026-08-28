package com.musicdreamer.songlist.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 创建专辑入参。 */
@Data
public class AlbumCreateDTO {

    @NotBlank(message = "专辑名称不能为空")
    private String name;

    private String description;

    /** 是否发布（默认发布）。 */
    private Boolean isPublic;
}
