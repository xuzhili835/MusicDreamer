package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 歌曲相似度矩阵（协同过滤离线计算结果，在线只读）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("song_similarity")
public class SongSimilarity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    private Long simSongId;

    /** 相似度 0-1，调整余弦。 */
    private Double score;

    private LocalDateTime updateTime;
}
