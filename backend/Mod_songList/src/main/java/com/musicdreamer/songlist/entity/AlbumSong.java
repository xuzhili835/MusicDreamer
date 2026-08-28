package com.musicdreamer.songlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 专辑歌曲关联表（uk: album_id + song_id）。 */
@Data
@TableName("album_song")
public class AlbumSong {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long albumId;

    private Long songId;

    /** 排序序号（按 (album_id, sort_order) 有序返回）。 */
    private Integer sortOrder;

    private LocalDateTime createTime;
}
