package com.musicdreamer.music.dto;

import lombok.Data;

/** 歌曲元数据编辑（本人或管理员，仅元数据不动文件与状态）。 */
@Data
public class SongEditDTO {

    private String name;

    private String album;

    private String style;

    private String language;

    private String coverUrl;

    /** 歌词文件地址（OCR 识别结果存为 lrc 后回填）。 */
    private String lyricUrl;

    /** 原始歌手名（导入他人作品时可改；传空串清除回落账号昵称）。 */
    private String singerName;
}
