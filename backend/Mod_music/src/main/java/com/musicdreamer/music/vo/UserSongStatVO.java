package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户近 90 天播放统计（歌曲 + 最后播放时间，推荐权重用）。 */
@Data
public class UserSongStatVO {

    private Long songId;

    private LocalDateTime lastPlayedAt;
}
