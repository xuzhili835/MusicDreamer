package com.musicdreamer.songlist.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 歌单列表项（含歌曲数）。 */
@Data
public class PlaylistVO {

    private Long id;

    /** 创建用户ID。 */
    private Long userId;

    private String name;

    private String description;

    private String coverUrl;

    private Boolean isPublic;

    private LocalDateTime createTime;

    /** 歌单内歌曲数。 */
    private Long songCount;

    /** 收藏时间（仅“我收藏的”列表有值）。 */
    private LocalDateTime favoriteTime;

    /** 创建者昵称（公开广场/收藏列表展示"by 谁"）。 */
    private String creatorName;
}
