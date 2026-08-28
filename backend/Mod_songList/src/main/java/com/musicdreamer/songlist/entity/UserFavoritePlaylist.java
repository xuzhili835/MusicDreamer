package com.musicdreamer.songlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户收藏歌单表（uk: user_id + playlist_id）。 */
@Data
@TableName("user_favorite_playlist")
public class UserFavoritePlaylist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID。 */
    private Long userId;

    /** 歌单ID。 */
    private Long playlistId;

    private LocalDateTime createTime;
}
