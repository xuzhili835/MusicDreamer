package com.musicdreamer.songlist.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 专辑列表项。 */
@Data
public class AlbumVO {

    private Long id;

    private Long userId;

    private String name;

    private String description;

    private String coverUrl;

    private Boolean isPublic;

    private LocalDateTime createTime;

    private Long songCount;

    private LocalDateTime favoriteTime;

    /** 歌手（发布者）昵称。 */
    private String singerName;
}
