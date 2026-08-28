package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 歌手/管理员提交歌曲（入库即审核中 status=1）。 */
@Data
public class SongSubmitDTO {

    @NotBlank(message = "歌曲名称不能为空")
    private String name;

    private String album;

    /** 风格必选（bug12）。 */
    @NotBlank(message = "风格不能为空")
    private String style;

    private String language;

    private Integer duration;

    @NotBlank(message = "音频文件地址不能为空")
    private String fileUrl;

    private String coverUrl;

    private String lyricUrl;

    @NotBlank(message = "音频格式不能为空")
    private String fileFormat;

    /** 下载来源链接，非空时查重。 */
    private String sourceUrl;

    /** 原始歌手名（管理员从外站导入他人作品时填写，展示优先于登录账号昵称）。 */
    private String singerName;
}
