package com.musicdreamer.songlist.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 歌单详情内的歌曲项：playlist_song 关联信息 + Feign 拼装的歌曲信息。 */
@Data
public class PlaylistSongVO {

    private Long songId;

    /** 歌单内排序。 */
    private Integer sortOrder;

    /** 加入歌单时间。 */
    private LocalDateTime addTime;

    /** 以下为音乐服务返回的歌曲字段（服务异常缺失时可能为 null）。 */
    private String name;

    private Long singerId;

    /** 歌手名（音乐服务返回则带出）。 */
    private String singerName;

    private String coverUrl;

    /** 时长（秒）。 */
    private Integer duration;

    private String style;

    private String album;

    private Integer status;
}
