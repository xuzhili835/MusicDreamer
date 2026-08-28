package com.musicdreamer.songlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌单歌曲关联表（uk: playlist_id + song_id）。 */
@Data
@TableName("playlist_song")
public class PlaylistSong {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌单ID。 */
    private Long playlistId;

    /** 歌曲ID。 */
    private Long songId;

    /** 排序序号（按 (playlist_id, sort_order) 有序返回）。 */
    private Integer sortOrder;

    private LocalDateTime createTime;
}
