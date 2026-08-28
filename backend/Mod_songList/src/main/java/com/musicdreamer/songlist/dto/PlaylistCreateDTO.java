package com.musicdreamer.songlist.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 创建歌单请求体。 */
@Data
public class PlaylistCreateDTO {

    @NotBlank(message = "歌单名称不能为空")
    @Size(max = 100, message = "歌单名称过长")
    private String name;

    @Size(max = 500, message = "歌单描述过长")
    private String description;

    /** 是否公开，缺省公开。 */
    private Boolean isPublic;
}
