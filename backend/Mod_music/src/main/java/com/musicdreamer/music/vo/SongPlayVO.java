package com.musicdreamer.music.vo;

import lombok.Data;

/** 播放接口下发字段（设计 6.4 节，volumeGain 未分析时为空）。 */
@Data
public class SongPlayVO {

    private Long songId;

    private String name;

    /** 歌手昵称（join user，昵称缺失回退用户名）。 */
    private String singer;

    private Long singerId;

    private String album;

    private String fileUrl;

    private String coverUrl;

    private String lyricUrl;

    private Integer duration;

    private Double volumeGain;

    private String fileFormat;
}
