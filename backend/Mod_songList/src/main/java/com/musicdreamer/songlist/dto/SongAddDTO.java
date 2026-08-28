package com.musicdreamer.songlist.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 歌单添加歌曲请求体。 */
@Data
public class SongAddDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;
}
