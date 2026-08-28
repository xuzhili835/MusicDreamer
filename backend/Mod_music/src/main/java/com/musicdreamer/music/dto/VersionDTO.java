package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 追加歌曲版本（version=max+1，进入版本审核中 status=2）。 */
@Data
public class VersionDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    @NotBlank(message = "音频文件地址不能为空")
    private String fileUrl;

    @NotBlank(message = "音频格式不能为空")
    private String fileFormat;

    private Long fileSize;

    private Integer duration;
}
