package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** Mod_media 下载完成回调建歌（内部接口，无鉴权）。 */
@Data
public class InternalCreateDTO {

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotNull(message = "singerId 不能为空")
    private Long singerId;

    @NotBlank(message = "fileUrl 不能为空")
    private String fileUrl;

    private String coverUrl;

    private Integer duration;

    @NotBlank(message = "fileFormat 不能为空")
    private String fileFormat;

    private String sourceUrl;

    /** 原始歌手名（下载任务带入，展示优先于账号昵称）。 */
    private String singerName;

    /** 音乐风格（bug12：上传中心必选，随任务透传）。 */
    private String style;
}
