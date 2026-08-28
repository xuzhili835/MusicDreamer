package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 歌曲详情（匿名可见，含歌手昵称与热计数）。 */
@Data
public class SongDetailVO {

    private Long songId;

    private String name;

    private String singer;

    /** 歌手展示名的自定义部分（song.singer_name，编辑弹窗回填用；空表示未自定义）。 */
    private String singerName;

    private Long singerId;

    private String album;

    private String style;

    private String language;

    private Integer duration;

    private String fileUrl;

    private String coverUrl;

    private String lyricUrl;

    private String fileFormat;

    private Long playCount;

    private Integer collectCount;

    private Double volumeGain;

    /** 在线词库来源记录（bug8 管理端复盘）：LRCLIB 命中时记录其 id 与回查链接。 */
    private Long lyricSourceId;

    private String lyricSourceUrl;

    private Integer status;

    private LocalDateTime createTime;
}
