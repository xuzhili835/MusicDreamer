package com.musicdreamer.songlist.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/** 更新歌单请求体：字段为 null 表示不修改；name 一旦提供必须非空。 */
@Data
public class PlaylistUpdateDTO {

    @Size(max = 100, message = "歌单名称过长")
    private String name;

    @Size(max = 500, message = "歌单描述过长")
    private String description;

    private Boolean isPublic;

    @Size(max = 255)
    private String coverUrl;
}
