package com.musicdreamer.songlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户收藏专辑表（uk: user_id + album_id）。 */
@Data
@TableName("user_favorite_album")
public class UserFavoriteAlbum {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long albumId;

    private LocalDateTime createTime;
}
