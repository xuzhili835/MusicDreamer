package com.musicdreamer.songlist.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 歌单详情：基本信息 + 按 sort_order 升序的歌曲列表。 */
@Data
public class PlaylistDetailVO {

    private Long id;

    private Long userId;

    private String name;

    private String description;

    private String coverUrl;

    private Boolean isPublic;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 歌曲数。 */
    private Integer songCount;

    /** 当前用户是否已收藏该歌单（未登录为 false）。 */
    private Boolean favored;

    private List<PlaylistSongVO> songs;

    /** 创建者昵称（非主人视角展示）。 */
    private String creatorName;
}
