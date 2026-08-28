package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** Mod_media 转写歌词地址回写。 */
@Data
public class LyricDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    @NotBlank(message = "lyricUrl 不能为空")
    private String lyricUrl;
}
